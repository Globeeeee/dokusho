package eu.kanade.tachiyomi.data.tts

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.SystemClock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Where a [ClipTtsEngine] gets the audio of one sentence: Supertonic on this device, or a system engine
 * rendering to a file when "Save spoken audio" is on.
 */
interface ClipSource {
    suspend fun prepare(): Boolean

    /** The audio for [text] with these settings, or null when it can't be voiced. */
    suspend fun clip(text: String, voice: String?, speed: Float, pitch: Float): TtsClip?

    /** Sentences that follow [clip]'s, so they can be rendered while earlier ones play. */
    fun ahead(texts: List<String>, voice: String?, speed: Float, pitch: Float) {}

    /** True when [clip] already carries the pitch; otherwise the player shifts it. */
    val bakesPitch: Boolean

    fun release() {}
}

/** The downloaded Supertonic voice. The models stay loaded in [SupertonicRuntime], which also reads ahead. */
class SupertonicClipSource(private val context: Context) : ClipSource {
    override val bakesPitch = false

    override suspend fun prepare() = SupertonicRuntime.prepare(context)

    override suspend fun clip(text: String, voice: String?, speed: Float, pitch: Float): TtsClip? =
        SupertonicRuntime.clip(context, SupertonicVoices.resolve(voice), speed, text)
            ?.let { TtsClip(it, SupertonicRuntime.sampleRate()) }
}

/**
 * Plays sentence clips from a [ClipSource]. Each sentence plays as soon as its audio is ready while
 * later sentences render ahead.
 *
 * One [AudioTrack] stays open across sentences and paragraphs and is fed back to back, so clips
 * follow each other without a gap. Only [stop] (pause, seek, speed or voice change) closes it.
 */
class ClipTtsEngine(private val source: ClipSource) : TtsEngine {

    private var sampleRate = 44_100
    private var speed = 1f
    private var pitch = 1f
    private var voiceId: String? = null
    private val output = AtomicReference<Output?>(null)

    /** Bumped by every [speak] and [stop]; a call whose number is stale has been stopped. */
    private val calls = AtomicInteger()

    override suspend fun init(): Boolean = source.prepare()

    override suspend fun speak(text: String, onRange: (start: Int, end: Int) -> Unit): Boolean {
        val call = calls.incrementAndGet()
        val stopped = { calls.get() != call }
        val timeline = ClipTimeline()
        val report: (Output) -> Unit = { track ->
            timeline.wordAt(track.heard())?.let { onRange(it.first, it.last + 1) }
        }
        var out: Output? = null
        var searchFrom = 0
        var played = 0
        var failed = 0
        val chunks = supertonicChunks(text)
        source.ahead(chunks, voiceId, speed, pitch)
        for (chunk in chunks) {
            currentCoroutineContext().ensureActive()
            if (stopped()) return true
            val at = text.indexOf(chunk, searchFrom).takeIf { it >= 0 } ?: searchFrom
            searchFrom = at + chunk.length
            // The previous sentence keeps playing (and highlighting) while this one is fetched.
            val clip = awaitClip(chunk) { out?.let(report) }
            if (stopped()) return true
            if (clip == null) {
                // One sentence the model can't voice shouldn't end the session; a dead model fails every one.
                logcat(LogPriority.WARN) { "Read aloud skipped a sentence it could not synthesize" }
                failed++
                continue
            }
            val audio = clip.audio
            if (audio.isEmpty()) continue
            sampleRate = clip.sampleRate
            // A clip at another rate (a different engine's cache) needs a track at that rate.
            if (out != null && out?.sampleRate != sampleRate) {
                out = null
                timeline.clear()
            }
            val track = out ?: openOutput() ?: return stopped()
            out = track
            if (stopped()) return true
            timeline.add(track.written, audio.size, chunk, at)
            when (write(track, toPcm(audio), stopped, report)) {
                Write.DONE -> Unit
                Write.STOPPED -> return true
                Write.FAILED -> {
                    closeOutput(track)
                    return stopped()
                }
                Write.STALLED -> {
                    // A track that stopped moving can't hang the chapter: drop it, the next sentence opens a new one.
                    logcat(LogPriority.WARN) { "Read aloud track stalled, reopening it" }
                    closeOutput(track)
                    timeline.clear()
                    out = null
                }
            }
            played++
        }
        val track = out ?: return played > 0 || failed == 0
        // A short silence after the paragraph pushes its last frames through the mixer and covers
        // the hop to the next paragraph, which is written while this silence plays.
        val padded = write(track, ShortArray(padFrames(track.sampleRate)), stopped, report)
        val result = if (padded == Write.DONE) drain(track, timeline.end, stopped, report) else padded
        when (result) {
            Write.DONE -> Unit
            Write.STOPPED -> return true
            Write.FAILED, Write.STALLED -> closeOutput(track)
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
        voiceId = voiceName
    }

    override fun stop() {
        calls.incrementAndGet()
        output.getAndSet(null)?.close()
    }

    override fun release() {
        stop()
        source.release()
    }

    /** The clip for [chunk]; [whileWaiting] runs every poll while it is still being synthesized. */
    private suspend fun awaitClip(chunk: String, whileWaiting: () -> Unit): TtsClip? = coroutineScope {
        val pending = async { source.clip(chunk, voiceId, speed, pitch) }
        while (!pending.isCompleted) {
            withTimeoutOrNull(POLL_MS) { pending.join() }
            if (!pending.isCompleted) whileWaiting()
        }
        pending.await()
    }

    /** The open track, or a new one. A track at another sample rate or near its frame limit is replaced. */
    @Volatile private var ducked = false

    override fun duck(ducked: Boolean) {
        this.ducked = ducked
        output.get()?.applyVolume(ducked)
    }

    private val trackPitch get() = if (source.bakesPitch) 1f else pitch

    private fun openOutput(): Output? {
        while (true) {
            val current = output.get()
            if (current != null && current.sampleRate == sampleRate && current.written < RECYCLE_FRAMES) {
                current.applyPitch(trackPitch)
                return current
            }
            val fresh = Output.open(sampleRate) ?: return null
            if (output.compareAndSet(current, fresh)) {
                current?.close()
                fresh.applyPitch(trackPitch)
                fresh.applyVolume(ducked)
                return fresh
            }
            // A concurrent call swapped the track first; use that one.
            fresh.close()
        }
    }

    private fun closeOutput(track: Output) {
        output.compareAndSet(track, null)
        track.close()
    }

    /** Queues all of [pcm] without blocking, so a track that never plays can't hang the call. */
    private suspend fun write(track: Output, pcm: ShortArray, stopped: () -> Boolean, report: (Output) -> Unit): Write {
        var offset = 0
        val watch = StallWatch(STALL_MS)
        while (offset < pcm.size) {
            currentCoroutineContext().ensureActive()
            if (stopped()) return Write.STOPPED
            val wrote = track.track.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_NON_BLOCKING)
            if (wrote < 0) return if (stopped()) Write.STOPPED else Write.FAILED
            offset += wrote
            track.written += wrote
            if (offset >= pcm.size) break
            report(track)
            if (watch.stalled(track.head(), SystemClock.elapsedRealtime())) return Write.STALLED
            delay(POLL_MS)
        }
        return Write.DONE
    }

    /** Waits until the track has played up to [frame]. */
    private suspend fun drain(track: Output, frame: Long, stopped: () -> Boolean, report: (Output) -> Unit): Write {
        val watch = StallWatch(STALL_MS)
        while (true) {
            currentCoroutineContext().ensureActive()
            if (stopped()) return Write.STOPPED
            report(track)
            val head = track.head()
            if (head >= frame || track.track.playState != AudioTrack.PLAYSTATE_PLAYING) return Write.DONE
            if (watch.stalled(head, SystemClock.elapsedRealtime())) return Write.STALLED
            delay(POLL_MS)
        }
    }

    private enum class Write { DONE, STOPPED, FAILED, STALLED }

    /** A playing stream track and the frames written into it since it opened. */
    private class Output(val track: AudioTrack, val sampleRate: Int) {
        @Volatile var written = 0L
        private var pitch = 1f

        fun applyVolume(ducked: Boolean) {
            runCatching { track.setVolume(if (ducked) DUCKED_VOLUME else AudioTrack.getMaxVolume()) }
        }

        @Volatile private var lastHead = 0L

        /**
         * Frames played since the track opened; the platform reports it as an unsigned 32-bit count.
         * [stop] releases the track from the main thread while a write may still poll it, and a released
         * track throws here, so it keeps the last position instead.
         */
        fun head(): Long = runCatching { track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL }
            .onSuccess { lastHead = it }
            .getOrDefault(lastHead)

        private val stamp = AudioTimestamp()

        /**
         * The frame being heard now. [head] is what the mixer has taken, which runs ahead of the speaker by
         * the device's output latency, so a highlight driven by it lights words up early.
         */
        fun heard(): Long {
            val head = head()
            val stamped = runCatching { track.getTimestamp(stamp) }.getOrDefault(false)
            if (!stamped) return (head - sampleRate / FALLBACK_LATENCY_DIVISOR).coerceAtLeast(0)
            val since = (System.nanoTime() - stamp.nanoTime).coerceAtLeast(0)
            return (stamp.framePosition + since * sampleRate / 1_000_000_000L).coerceIn(0, head)
        }

        fun applyPitch(value: Float) {
            if (value == pitch) return
            runCatching { track.playbackParams = PlaybackParams().setPitch(value).setSpeed(1f) }
                .onSuccess { pitch = value }
        }

        fun close() {
            runCatching {
                track.pause()
                track.flush()
            }
            runCatching { track.release() }
        }

        companion object {
            fun open(sampleRate: Int): Output? {
                val minBuffer = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                if (minBuffer <= 0) return null
                val player = try {
                    AudioTrack.Builder()
                        .setAudioAttributes(ttsAudioAttributes())
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build(),
                        )
                        // About a second of 16-bit mono, so the hop between sentences never runs it dry.
                        .setBufferSizeInBytes(maxOf(minBuffer, sampleRate * 2))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Read aloud could not open an audio track" }
                    return null
                }
                if (player.state != AudioTrack.STATE_INITIALIZED) {
                    player.release()
                    return null
                }
                return try {
                    player.play()
                    Output(player, sampleRate)
                } catch (e: IllegalStateException) {
                    logcat(LogPriority.ERROR, e) { "Read aloud could not start the audio track" }
                    player.release()
                    null
                }
            }
        }
    }

    private companion object {
        const val POLL_MS = 20L

        /** A track whose head has not moved for this long while it holds audio is considered dead. */
        const val STALL_MS = 2_500L

        /** Volume under a notification sound: low enough to hear the sound, high enough to follow the text. */
        const val DUCKED_VOLUME = 0.3f

        /** Without a timestamp, assume about 100 ms of output latency (sample rate / 10). */
        const val FALLBACK_LATENCY_DIVISOR = 10

        /** Reopen the track well before its 32-bit frame counter wraps (about 12 hours at 44.1 kHz). */
        const val RECYCLE_FRAMES = 2_000_000_000L

        fun padFrames(sampleRate: Int) = sampleRate / 5

        fun toPcm(audio: FloatArray) = ShortArray(audio.size) { index ->
            (audio[index].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
    }
}

/**
 * Where each sentence of one [ClipTtsEngine.speak] call sits in the track's frame count, so
 * the playback head maps back to the word being spoken while later sentences are already queued.
 */
internal class ClipTimeline {
    private class Clip(val start: Long, val end: Long, val words: SpokenWords, val offset: Int)

    private val clips = ArrayDeque<Clip>()
    private var shownClip: Clip? = null
    private var shownWord = -1

    /** Frame just after the last sentence added, or 0 when there is none. */
    val end: Long get() = clips.lastOrNull()?.end ?: 0L

    /** A sentence of [frames] frames starting at track frame [start]; [offset] is its index in the spoken text. */
    fun add(start: Long, frames: Int, chunk: String, offset: Int) {
        clips.addLast(Clip(start, start + frames, SpokenWords(chunk), offset))
    }

    fun clear() {
        clips.clear()
        shownClip = null
        shownWord = -1
    }

    /** The text range of the word playing at track frame [head], or null when it is unchanged or nothing plays yet. */
    fun wordAt(head: Long): IntRange? {
        while (clips.size > 1 && head >= clips.first().end) clips.removeFirst()
        val clip = clips.firstOrNull() ?: return null
        if (head < clip.start) return null
        val fraction = (head - clip.start).toFloat() / (clip.end - clip.start).coerceAtLeast(1L)
        val word = clip.words.at(fraction.coerceAtMost(1f))
        if (word < 0 || (clip === shownClip && word == shownWord)) return null
        shownClip = clip
        shownWord = word
        return (clip.offset + clip.words.start(word)) until (clip.offset + clip.words.end(word))
    }
}

/** Reports a playback head that has not moved for [limitMs]. */
internal class StallWatch(private val limitMs: Long) {
    private var head = Long.MIN_VALUE
    private var since = 0L

    fun stalled(head: Long, nowMs: Long): Boolean {
        if (head != this.head) {
            this.head = head
            since = nowMs
            return false
        }
        return nowMs - since >= limitMs
    }
}

/**
 * Clips carry no word timings, so the spoken word is estimated from playback progress:
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
