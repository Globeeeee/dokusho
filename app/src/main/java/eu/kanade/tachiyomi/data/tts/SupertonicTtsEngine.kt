package eu.kanade.tachiyomi.data.tts

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Speaks with the downloaded Supertonic voice. The models stay loaded in [SupertonicRuntime];
 * each sentence plays as soon as its audio is ready while later sentences synthesize ahead.
 */
class SupertonicTtsEngine(private val context: Context) : TtsEngine {

    private var sampleRate = 44_100
    private var speed = 1f
    private var pitch = 1f
    private var voiceId = SupertonicVoices.DEFAULT
    private val track = AtomicReference<AudioTrack?>(null)

    /** Bumped by every [speak] and [stop]; a call whose number is stale has been stopped. */
    private val calls = AtomicInteger()

    override suspend fun init(): Boolean {
        if (!SupertonicRuntime.prepare(context)) return false
        sampleRate = SupertonicRuntime.sampleRate()
        return true
    }

    override suspend fun speak(text: String, onRange: (start: Int, end: Int) -> Unit): Boolean {
        val call = calls.incrementAndGet()
        val stopped = { calls.get() != call }
        var searchFrom = 0
        var played = 0
        var failed = 0
        for (chunk in supertonicChunks(text)) {
            currentCoroutineContext().ensureActive()
            if (stopped()) return true
            val at = text.indexOf(chunk, searchFrom).takeIf { it >= 0 } ?: searchFrom
            searchFrom = at + chunk.length
            val audio = SupertonicRuntime.clip(context, voiceId, speed, chunk)
            if (stopped()) return true
            if (audio == null) {
                // One sentence the model can't voice shouldn't end the session; a dead model fails every one.
                logcat(LogPriority.WARN) { "Supertonic skipped a sentence it could not synthesize" }
                failed++
                continue
            }
            if (audio.isEmpty()) continue
            if (!play(audio, chunk, at, stopped, onRange)) return false
            played++
        }
        return played > 0 || failed == 0
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.5f, 3f)
    }

    override fun setPitch(pitch: Float) {
        this.pitch = pitch.coerceIn(0.5f, 2f)
    }

    override fun setVoice(voiceName: String?) {
        voiceId = SupertonicVoices.resolve(voiceName)
    }

    override fun stop() {
        calls.incrementAndGet()
        track.get()?.let { active ->
            runCatching {
                active.pause()
                active.flush()
            }
        }
    }

    override fun release() {
        stop()
    }

    private suspend fun play(
        audio: FloatArray,
        chunk: String,
        chunkStart: Int,
        stopped: () -> Boolean,
        onRange: (start: Int, end: Int) -> Unit,
    ): Boolean {
        val pcm = ShortArray(audio.size) { index ->
            (audio[index].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false
        val player = AudioTrack.Builder()
            .setAudioAttributes(ttsAudioAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer, sampleRate / 5))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.set(player)
        try {
            if (stopped()) return true
            val words = SpokenWords(chunk)
            var shown = -1
            player.play()
            if (pitch != 1f) {
                runCatching {
                    player.playbackParams = PlaybackParams().setPitch(pitch).setSpeed(1f)
                }
            }
            // Non-blocking writes, so a track that never starts can't hang the chapter; the deadline ends it.
            val deadline = SystemClock.elapsedRealtime() + pcm.size * 1000L / sampleRate + 2000L
            var offset = 0
            while (SystemClock.elapsedRealtime() < deadline) {
                currentCoroutineContext().ensureActive()
                if (stopped()) return true
                if (offset < pcm.size) {
                    val wrote = player.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                    if (wrote < 0) return stopped()
                    offset += wrote
                }
                val head = player.playbackHeadPosition
                if (offset >= pcm.size && (head >= pcm.size || player.playState != AudioTrack.PLAYSTATE_PLAYING)) break
                val word = words.at(head.toFloat() / pcm.size)
                if (word != shown && word >= 0) {
                    shown = word
                    onRange(chunkStart + words.start(word), chunkStart + words.end(word))
                }
                delay(30)
            }
            return true
        } finally {
            // Release this call's track even when a newer call already replaced it.
            track.compareAndSet(player, null)
            runCatching {
                player.pause()
                player.flush()
                player.release()
            }
        }
    }
}

/**
 * Supertonic reports no word timings, so the spoken word is estimated from playback progress:
 * each word weighs its letters, plus a pause after commas and sentence ends.
 */
internal class SpokenWords(text: String) {
    private val ranges = WORD.findAll(text).map { it.range }.toList()
    private val ends: FloatArray

    init {
        var total = 0f
        ends = FloatArray(ranges.size) { index ->
            val word = text.substring(ranges[index].first, ranges[index].last + 1)
            total += word.count { it.isLetterOrDigit() }.coerceAtLeast(1) + when (word.last()) {
                ',', ';', ':', '-', '\u2014' -> 2.5f
                '.', '!', '?', '\u2026', '"', '\u201D' -> 4f
                else -> 1f
            }
            total
        }
        if (total > 0f) for (index in ends.indices) ends[index] /= total
    }

    /** The word playing at [fraction] (0 to 1) of the clip, or -1 for an empty clip. */
    fun at(fraction: Float): Int {
        if (ranges.isEmpty()) return -1
        val index = ends.indexOfFirst { fraction < it }
        return if (index < 0) ranges.lastIndex else index
    }

    fun start(word: Int) = ranges[word].first

    fun end(word: Int) = ranges[word].last + 1

    private companion object {
        val WORD = Regex("\\S+")
    }
}
