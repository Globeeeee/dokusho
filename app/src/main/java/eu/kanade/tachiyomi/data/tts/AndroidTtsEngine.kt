package eu.kanade.tachiyomi.data.tts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * [TtsEngine] on the system [TextToSpeech]. The voice is the one chosen in read-aloud settings,
 * or the engine's own default. When the chosen engine can't start or fails mid-chapter, it carries
 * on with the device's default engine and calls [onFallback].
 */
class AndroidTtsEngine(
    private val context: Context,
    private val enginePackage: String? = null,
    private val onFallback: () -> Unit = {},
) : TtsEngine {

    private var tts: TextToSpeech? = null
    private var activePackage = enginePackage
    private var speed = 1f
    private var pitch = 1f
    private var voiceName: String? = null
    private val utteranceIds = AtomicInteger()
    private val highlightLag = Handler(Looper.getMainLooper())
    private val initLock = Mutex()
    private val keepAlive = SilentTrack()

    /** The engine actually speaking: the chosen one, or the device default after a fallback. */
    val packageInUse: String? get() = activePackage ?: tts?.defaultEngine

    // One engine instance at a time: settings changes restart playback, and with it init, many times a second.
    override suspend fun init(): Boolean = initLock.withLock {
        if (tts != null) return@withLock true
        tts = open(activePackage)
        if (tts == null && activePackage != null) {
            activePackage = null
            onFallback()
            tts = open(null)
        }
        tts != null
    }

    private suspend fun open(enginePackage: String?): TextToSpeech? = suspendCancellableCoroutine { continuation ->
        var engine: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            if (!continuation.isActive) {
                // Cancelled while binding: this instance is already shut down and must not become [tts].
                engine?.shutdown()
            } else if (status == TextToSpeech.SUCCESS && engine != null) {
                val prepared = engine!!
                prepared.setAudioAttributes(ttsAudioAttributes())
                prepared.setSpeechRate(speed)
                prepared.setPitch(pitch)
                applyVoice(prepared)
                continuation.resume(prepared)
            } else {
                logcat(LogPriority.WARN) { "TTS engine ${enginePackage ?: "default"} failed to start ($status)" }
                engine?.shutdown()
                continuation.resume(null)
            }
        }
        engine = if (enginePackage.isNullOrBlank()) {
            TextToSpeech(context, listener)
        } else {
            TextToSpeech(context, listener, enginePackage)
        }
        continuation.invokeOnCancellation { engine?.shutdown() }
    }

    /** Drops a chosen engine that failed for the device default. False when already on the default. */
    private suspend fun fallBack(): Boolean {
        if (activePackage == null) return false
        initLock.withLock {
            tts?.shutdown()
            tts = null
            activePackage = null
        }
        onFallback()
        return init()
    }

    override suspend fun speak(text: String, onRange: (start: Int, end: Int) -> Unit): Boolean {
        if (tts == null) return false
        keepAlive.play()
        // The system engine rejects long input; read a long paragraph in sentence-boundary chunks.
        var searchFrom = 0
        for (chunk in split(text, TextToSpeech.getMaxSpeechInputLength() - 1)) {
            val at = text.indexOf(chunk, searchFrom).takeIf { it >= 0 } ?: searchFrom
            val report = { start: Int, end: Int -> onRange(at + start, at + end) }
            val spoken = speakChunk(tts ?: return false, chunk, report) ||
                (fallBack() && speakChunk(tts ?: return false, chunk, report))
            if (!spoken) return false
            searchFrom = at + chunk.length
        }
        return true
    }

    /**
     * Renders [text] to audio instead of playing it, for the sentence cache. Falls back like [speak];
     * null when the engine can't voice it.
     */
    suspend fun synthesize(text: String): TtsClip? {
        if (tts == null && !init()) return null
        return synthesizeChunk(tts ?: return null, text)
            ?: if (fallBack()) synthesizeChunk(tts ?: return null, text) else null
    }

    private suspend fun synthesizeChunk(tts: TextToSpeech, text: String): TtsClip? {
        val id = "dokusho-file-${utteranceIds.incrementAndGet()}"
        val file = File(context.cacheDir, "tts/$id.wav").apply { parentFile?.mkdirs() }
        try {
            val done = suspendCancellableCoroutine { continuation ->
                tts.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == id && continuation.isActive) continuation.resume(true)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == id && continuation.isActive) continuation.resume(false)
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (utteranceId != id) return
                            logcat(LogPriority.WARN) {
                                "TTS engine ${activePackage ?: "default"} file error $errorCode"
                            }
                            if (continuation.isActive) continuation.resume(false)
                        }
                    },
                )
                val queued = tts.synthesizeToFile(text, null, file, id)
                if (queued != TextToSpeech.SUCCESS && continuation.isActive) continuation.resume(false)
                continuation.invokeOnCancellation { tts.stop() }
            }
            return if (done) readWav(file) else null
        } finally {
            file.delete()
        }
    }

    private suspend fun speakChunk(
        tts: TextToSpeech,
        text: String,
        onRange: (start: Int, end: Int) -> Unit,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val id = "dokusho-tts-${utteranceIds.incrementAndGet()}"
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    // Reported as the word is handed to audio output, a moment before it is heard.
                    if (utteranceId == id) highlightLag.postDelayed({ onRange(start, end) }, HIGHLIGHT_LAG_MS)
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(true)
                }

                // stop() lands here on some engines; treat it as an abort, not a failure.
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId != id) return
                    logcat(LogPriority.WARN) { "TTS engine ${activePackage ?: "default"} error $errorCode" }
                    if (continuation.isActive) continuation.resume(false)
                }
            },
        )
        val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (queued != TextToSpeech.SUCCESS && continuation.isActive) continuation.resume(false)
        continuation.invokeOnCancellation { tts.stop() }
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
        tts?.setSpeechRate(speed)
    }

    override fun setPitch(pitch: Float) {
        this.pitch = pitch
        tts?.setPitch(pitch)
    }

    override fun setVoice(voiceName: String?) {
        this.voiceName = voiceName
        tts?.let(::applyVoice)
    }

    override fun stop() {
        keepAlive.pause()
        tts?.stop()
    }

    override fun release() {
        keepAlive.release()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun applyVoice(tts: TextToSpeech) {
        val name = voiceName
        if (name.isNullOrBlank()) return
        val voice = tts.voices?.firstOrNull { it.name == name } ?: return
        tts.voice = voice
    }

    /** Splits [text] into pieces of at most [limit] characters, preferring sentence ends. */
    private fun split(text: String, limit: Int): List<String> {
        if (text.length <= limit) return listOf(text)
        val parts = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            if (text.length - start <= limit) {
                parts += text.substring(start)
                break
            }
            val window = text.substring(start, start + limit)
            val cut = window.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ';'))
                .takeIf { it > 0 }
                ?: window.lastIndexOf(' ').takeIf { it > 0 }
                ?: (limit - 1)
            parts += text.substring(start, start + cut + 1)
            start += cut + 1
        }
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/** Mono audio from a PCM WAV a system engine wrote (16-bit, or 8-bit from old engines); null for anything else. */
private fun readWav(file: File): TtsClip? {
    if (!file.isFile) return null
    val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
    if (buffer.remaining() < 12 || buffer.getInt(0) != RIFF || buffer.getInt(8) != WAVE) return null
    buffer.position(12)
    var channels = 0
    var sampleRate = 0
    var bits = 0
    while (buffer.remaining() >= 8) {
        val id = buffer.getInt()
        val size = buffer.getInt()
        val body = buffer.position()
        when (id) {
            FMT -> {
                if (buffer.getShort().toInt() != 1) return null // PCM only
                channels = buffer.getShort().toInt()
                sampleRate = buffer.getInt()
                buffer.position(body + 14)
                bits = buffer.getShort().toInt()
            }
            DATA -> {
                if (channels <= 0 || sampleRate <= 0) return null
                val bytesPerSample = bits / 8
                if (bytesPerSample != 1 && bytesPerSample != 2) return null
                // A header left unfinished reports 0 (or garbage) for the data size; read to the end then.
                val length = if (size <= 0) buffer.remaining() else minOf(size, buffer.remaining())
                val frames = length / (bytesPerSample * channels)
                val audio = FloatArray(frames) { frame ->
                    var sum = 0f
                    repeat(channels) {
                        sum += if (bytesPerSample == 2) {
                            buffer.getShort() / 32768f
                        } else {
                            ((buffer.get().toInt() and 0xFF) - 128) / 128f
                        }
                    }
                    sum / channels
                }
                return TtsClip(audio, sampleRate)
            }
        }
        // Chunks are padded to an even size.
        buffer.position((body + size + (size and 1)).coerceAtMost(buffer.limit()))
    }
    return null
}

private const val RIFF = 0x46464952 // "RIFF" read little-endian
private const val WAVE = 0x45564157
private const val FMT = 0x20746D66
private const val DATA = 0x61746164

/**
 * Silence played from Dokusho's own process while a system engine speaks. The engine's audio belongs to the
 * engine app, so without it Android routes earbud play and pause to whichever media app played last.
 */
private class SilentTrack {
    private var track: AudioTrack? = null

    @Synchronized
    fun play() {
        val current = track ?: open()?.also { track = it } ?: return
        if (current.playState != AudioTrack.PLAYSTATE_PLAYING) runCatching { current.play() }
    }

    @Synchronized
    fun pause() {
        track?.let { runCatching { it.pause() } }
    }

    @Synchronized
    fun release() {
        track?.let { runCatching { it.release() } }
        track = null
    }

    private fun open(): AudioTrack? = try {
        val frames = SILENCE_RATE / 2
        AudioTrack.Builder()
            .setAudioAttributes(ttsAudioAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SILENCE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(frames * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .let { built ->
                val looping = built.write(ShortArray(frames), 0, frames) == frames &&
                    built.setLoopPoints(0, frames, -1) == AudioTrack.SUCCESS
                if (!looping) built.release()
                built.takeIf { looping }
            }
    } catch (e: Exception) {
        logcat(LogPriority.WARN, e) { "TTS could not open the keep-alive track" }
        null
    }

    private companion object {
        const val SILENCE_RATE = 16_000
    }
}

internal fun ttsAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/** What a system engine can do for the reader right now, shown under the engine choice. */
enum class TtsEngineStatus { READY, NO_VOICE, ONLINE_ONLY, UNAVAILABLE }

/** The line shown under the engine choice for this status. */
fun TtsEngineStatus.caption(): StringResource = when (this) {
    TtsEngineStatus.READY -> MR.strings.tts_status_ready
    TtsEngineStatus.NO_VOICE -> MR.strings.tts_status_no_voice
    TtsEngineStatus.ONLINE_ONLY -> MR.strings.tts_status_online
    TtsEngineStatus.UNAVAILABLE -> MR.strings.tts_status_unavailable
}

/** Installed system engines and their voices, for the read-aloud settings. */
class TtsCatalog(private val context: Context) {

    suspend fun installedEngines(): List<Pair<String, String>> = query(null) { tts ->
        tts.engines.filterNot { TtsEngines.isPaid(it.name) }.map { it.name to it.label.toString() }
    }.orEmpty()

    /**
     * Whether [enginePackage] (null for the default) can read aloud in the language of [voiceName],
     * or the device language when no voice is chosen.
     */
    suspend fun status(enginePackage: String?, voiceName: String?): TtsEngineStatus = query(enginePackage) { tts ->
        val voices = tts.voices.orEmpty()
        val locale = voices.firstOrNull { it.name == voiceName }?.locale ?: Locale.getDefault()
        val usable = voices.filter {
            it.locale.language == locale.language &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
        }
        val installed = usable.isNotEmpty() || tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
        when {
            !installed -> TtsEngineStatus.NO_VOICE
            usable.isNotEmpty() && usable.all { it.isNetworkConnectionRequired } -> TtsEngineStatus.ONLINE_ONLY
            else -> TtsEngineStatus.READY
        }
    } ?: TtsEngineStatus.UNAVAILABLE

    /** Opens the engine's own voice download screen, or Android's text-to-speech settings when it has none. */
    fun openVoiceInstall(enginePackage: String?) {
        val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .apply { enginePackage?.let(::setPackage) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(install)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(Intent(TTS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    suspend fun voices(enginePackage: String?): List<Pair<String, String>> = query(enginePackage) { tts ->
        val preferred = Locale.getDefault()
        val available = tts.voices.orEmpty()
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .distinctBy { it.name }
            .sortedWith(
                compareBy(
                    { it.locale.language != preferred.language },
                    { it.locale.country.isNotEmpty() && it.locale.country != preferred.country },
                    { it.locale.displayName.lowercase() },
                    { it.isNetworkConnectionRequired },
                    { it.name },
                ),
            )
        labelVoices(available)
    }.orEmpty()

    /** One readable name per voice. Several voices of the same language are numbered, not repeated. */
    private fun labelVoices(voices: List<Voice>): List<Pair<String, String>> {
        val groups = voices.groupBy { voiceGroup(it) }
        return voices.map { voice ->
            val siblings = groups.getValue(voiceGroup(voice))
            val label = buildString {
                append(voice.locale.displayName)
                if (siblings.size > 1) append(" ").append(siblings.indexOfFirst { it.name == voice.name } + 1)
                if (voice.isNetworkConnectionRequired) append(" · online")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && voice.quality >= Voice.QUALITY_VERY_HIGH) {
                    append(" · hd")
                }
            }
            voice.name to label
        }
    }

    private fun voiceGroup(voice: Voice): String = buildString {
        append(voice.locale.toLanguageTag())
        append(if (voice.isNetworkConnectionRequired) "|online" else "|local")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && voice.quality >= Voice.QUALITY_VERY_HIGH) {
            append("|hd")
        }
    }

    private suspend fun <T> query(enginePackage: String?, read: (TextToSpeech) -> T): T? =
        suspendCancellableCoroutine { continuation ->
            var engine: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                // Some third-party engines throw from their voice list; a broken engine must not crash settings.
                val value = if (status == TextToSpeech.SUCCESS) {
                    engine?.let {
                        runCatching { read(it) }
                            .onFailure { e -> logcat(LogPriority.WARN, e) { "TTS engine list or voices failed" } }
                            .getOrNull()
                    }
                } else {
                    null
                }
                engine?.shutdown()
                if (continuation.isActive) continuation.resume(value)
            }
            engine = if (enginePackage.isNullOrBlank()) {
                TextToSpeech(context, listener)
            } else {
                TextToSpeech(context, listener, enginePackage)
            }
            continuation.invokeOnCancellation { engine?.shutdown() }
        }
}

private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"

/** Typical output latency between the engine's word callback and the word being heard. */
private const val HIGHLIGHT_LAG_MS = 120L
