package eu.kanade.tachiyomi.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * [TtsEngine] on the system [TextToSpeech]. The voice is the one chosen in read-aloud settings,
 * or the engine's own default.
 */
class AndroidTtsEngine(
    private val context: Context,
    private val enginePackage: String? = null,
) : TtsEngine {

    private var tts: TextToSpeech? = null
    private var speed = 1f
    private var pitch = 1f
    private var voiceName: String? = null
    private val utteranceIds = AtomicInteger()

    override suspend fun init(): Boolean {
        if (tts != null) return true
        return suspendCancellableCoroutine { continuation ->
            var engine: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts = engine?.also { prepared ->
                        prepared.setAudioAttributes(ttsAudioAttributes())
                        prepared.setSpeechRate(speed)
                        prepared.setPitch(pitch)
                        applyVoice(prepared)
                    }
                    continuation.resume(true)
                } else {
                    engine?.shutdown()
                    continuation.resume(false)
                }
            }
            engine = if (enginePackage.isNullOrBlank()) {
                TextToSpeech(context, listener)
            } else {
                TextToSpeech(context, listener, enginePackage)
            }
            continuation.invokeOnCancellation { engine?.shutdown() }
        }
    }

    override suspend fun speak(text: String, onRange: (start: Int, end: Int) -> Unit): Boolean {
        val tts = tts ?: return false
        // The system engine rejects long input; read a long paragraph in sentence-boundary chunks.
        var searchFrom = 0
        for (chunk in split(text, TextToSpeech.getMaxSpeechInputLength() - 1)) {
            val at = text.indexOf(chunk, searchFrom).takeIf { it >= 0 } ?: searchFrom
            val spoken = speakChunk(tts, chunk) { start, end -> onRange(at + start, at + end) }
            if (!spoken) return false
            searchFrom = at + chunk.length
        }
        return true
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
                    if (utteranceId == id) onRange(start, end)
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
                    if (utteranceId == id && continuation.isActive) continuation.resume(false)
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
        tts?.stop()
    }

    override fun release() {
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

internal fun ttsAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/** Installed system engines and their voices, for the read-aloud settings. */
class TtsCatalog(private val context: Context) {

    suspend fun installedEngines(): List<Pair<String, String>> = query(null) { tts ->
        tts.engines.map { it.name to it.label.toString() }
    }.orEmpty()

    suspend fun voices(enginePackage: String?): List<Pair<String, String>> = query(enginePackage) { tts ->
        val preferred = Locale.getDefault()
        val available = tts.voices.orEmpty()
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
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
                val value = if (status == TextToSpeech.SUCCESS) engine?.let(read) else null
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
