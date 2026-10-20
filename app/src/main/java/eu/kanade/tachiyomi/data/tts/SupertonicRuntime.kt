package eu.kanade.tachiyomi.data.tts

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * One Supertonic voice for the whole process. Opening a chapter loads the models; Listen then
 * plays clips that were already synthesized, and the next sentences keep generating during playback.
 */
object SupertonicRuntime {

    /** About three minutes of audio kept ready, so playback never waits on the next sentence. */
    private const val MAX_AHEAD_SAMPLES = 44_100 * 180

    /** Hard ceiling so a long chapter cannot pin the process on PCM. */
    private const val MAX_CACHED_SAMPLES = 44_100 * 240

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = Any()
    private val signal = Channel<Unit>(Channel.CONFLATED)

    private var loading: Deferred<SupertonicSynthesizer?>? = null
    private var synthesizer: SupertonicSynthesizer? = null
    private var worker: Job? = null

    private var voice = SupertonicVoices.DEFAULT
    private var speed = 1f
    private var generation = 0

    private val order = ArrayDeque<String>()
    private val running = HashSet<String>()
    private val cache = LinkedHashMap<String, FloatArray>()
    private val waiters = HashMap<String, CompletableDeferred<FloatArray?>>()
    private val handedOut = HashSet<String>()
    private var aheadSamples = 0

    /** Starts a model load if the files are on disk. Safe to call from the reader when a chapter opens. */
    fun warm(context: Context) {
        if (!SupertonicModel.isReady(context)) return
        scope.launch { prepare(context.applicationContext) }
    }

    suspend fun prepare(context: Context): Boolean {
        synchronized(gate) {
            if (synthesizer != null) return true
        }
        val task = synchronized(gate) {
            if (synthesizer != null) return true
            loading ?: scope.async(Dispatchers.IO) {
                load(context.applicationContext)
            }.also { loading = it }
        }
        val loaded = task.await()
        if (loaded == null) {
            synchronized(gate) { if (loading === task) loading = null }
        }
        return loaded != null
    }

    fun sampleRate(): Int = synchronized(gate) { synthesizer?.sampleRate ?: 44_100 }

    /**
     * [current] is synthesized first. [upcoming] (the next chapter) follows, so it is ready
     * while the current chapter is still playing.
     */
    fun plan(context: Context, voiceId: String, rate: Float, current: List<String>, upcoming: List<String>) {
        if (!SupertonicModel.isReady(context)) return
        val currentChunks = current.flatMap { supertonicChunks(it) }
        val upcomingChunks = upcoming.flatMap { supertonicChunks(it) }
        synchronized(gate) {
            applySettingsLocked(voiceId, rate)
            val keep = (currentChunks + upcomingChunks).mapTo(HashSet()) { cacheKey(it) }
            aheadSamples = 0
            for ((key, audio) in cache) {
                if (key in keep && key !in handedOut) {
                    aheadSamples += audio.size
                } else {
                    handedOut.add(key)
                }
            }
            // Sentences playback is already waiting for stay first, even when this plan leaves them out.
            val awaited = order.filter { cacheKey(it) in waiters }
            order.clear()
            evictLocked()
            for (chunk in currentChunks) offerLocked(chunk, atFront = false)
            for (chunk in upcomingChunks) offerLocked(chunk, atFront = false)
            for (chunk in awaited.asReversed()) offerLocked(chunk, atFront = true)
        }
        warm(context)
        ensureWorker(context)
        signal.trySend(Unit)
    }

    /** The PCM for [text], synthesizing it next if it is not ready yet. */
    suspend fun clip(context: Context, voiceId: String, rate: Float, text: String): FloatArray? {
        var key = ""
        val deferred = synchronized(gate) {
            applySettingsLocked(voiceId, rate)
            key = cacheKey(text)
            cache[key]?.let { audio ->
                markHandedLocked(key, audio)
                signal.trySend(Unit)
                return audio
            }
            offerLocked(text, atFront = true)
            waiters.getOrPut(key) { CompletableDeferred() }
        }
        warm(context)
        ensureWorker(context)
        signal.trySend(Unit)
        val audio = deferred.await() ?: return null
        synchronized(gate) {
            markHandedLocked(key, audio)
            signal.trySend(Unit)
        }
        return audio
    }

    private fun applySettingsLocked(voiceId: String, rate: Float) {
        val resolved = SupertonicVoices.resolve(voiceId)
        val safeRate = rate.coerceIn(0.5f, 3f)
        if (resolved == voice && safeRate == speed) return
        voice = resolved
        speed = safeRate
        generation++
        order.clear()
        running.clear()
        cache.clear()
        handedOut.clear()
        aheadSamples = 0
        val pending = waiters.values.toList()
        waiters.clear()
        pending.forEach { it.complete(null) }
    }

    private fun offerLocked(text: String, atFront: Boolean) {
        if (text.isBlank()) return
        val key = cacheKey(text)
        if (key in running || cache.containsKey(key)) {
            if (atFront) order.remove(text)
            return
        }
        if (text in order) {
            if (!atFront) return
            order.remove(text)
        }
        if (atFront) order.addFirst(text) else order.addLast(text)
    }

    private fun cacheKey(text: String) = "$voice|$speed|$text"

    private fun markHandedLocked(key: String, audio: FloatArray) {
        if (handedOut.add(key)) aheadSamples = (aheadSamples - audio.size).coerceAtLeast(0)
        evictLocked()
    }

    private fun evictLocked() {
        var total = cache.values.sumOf { it.size }
        if (total <= MAX_CACHED_SAMPLES) return
        val iterator = cache.entries.iterator()
        while (total > MAX_CACHED_SAMPLES && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in handedOut) continue
            total -= entry.value.size
            handedOut.remove(entry.key)
            iterator.remove()
        }
    }

    private suspend fun load(context: Context): SupertonicSynthesizer? {
        if (!SupertonicModel.isReady(context)) return null
        return try {
            val started = System.nanoTime()
            SupertonicSynthesizer.load(context).also { loaded ->
                synchronized(gate) { synthesizer = loaded }
                val millis = (System.nanoTime() - started) / 1_000_000
                logcat { "Supertonic ready in ${millis}ms" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Supertonic failed to load" }
            null
        }
    }

    private fun ensureWorker(context: Context) {
        val app = context.applicationContext
        synchronized(gate) {
            if (worker?.isActive == true) return
            worker = scope.launch { runWorker(app) }
        }
    }

    private suspend fun runWorker(context: Context) {
        if (!prepare(context)) return
        while (currentCoroutineContext().isActive) {
            val job = synchronized(gate) { nextJobLocked() }
            if (job == null) {
                signal.receive()
                continue
            }
            val synth = synchronized(gate) { synthesizer }
            val audio = try {
                if (synth == null) {
                    null
                } else {
                    synth.synthesize(job.text, supertonicLanguage(job.text), job.voice, job.speed)
                }
            } catch (e: CancellationException) {
                synchronized(gate) { running.remove(job.key) }
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Supertonic failed to speak ahead" }
                null
            }
            synchronized(gate) {
                running.remove(job.key)
                if (job.generation != generation) return@synchronized
                if (audio != null && !cache.containsKey(job.key)) {
                    cache[job.key] = audio
                    if (job.key !in handedOut) aheadSamples += audio.size
                    evictLocked()
                }
                waiters.remove(job.key)?.complete(audio ?: cache[job.key])
            }
        }
    }

    private fun nextJobLocked(): Pending? {
        while (order.isNotEmpty()) {
            val text = order.removeFirst()
            val key = cacheKey(text)
            if (cache.containsKey(key) || key in running) continue
            // The read-ahead cap never holds back a sentence playback is waiting for.
            if (aheadSamples >= MAX_AHEAD_SAMPLES && key !in waiters) {
                order.addFirst(text)
                return null
            }
            running.add(key)
            return Pending(text, key, voice, speed, generation)
        }
        return null
    }

    private class Pending(
        val text: String,
        val key: String,
        val voice: String,
        val speed: Float,
        val generation: Int,
    )
}
