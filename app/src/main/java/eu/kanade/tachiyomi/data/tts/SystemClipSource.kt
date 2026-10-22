package eu.kanade.tachiyomi.data.tts

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * A system engine rendering each sentence to a file instead of speaking it ("Save spoken audio").
 * Sentences are kept in [TtsClipCache], so a chapter played again needs no engine at all, and Dokusho
 * plays the audio itself, so earbud buttons and ducking work like Supertonic.
 */
class SystemClipSource(
    private val context: Context,
    private val enginePackage: String?,
    onFallback: () -> Unit = {},
) : ClipSource {

    private val engine = AndroidTtsEngine(context, enginePackage, onFallback)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The engine renders one sentence at a time; settings are applied per sentence under this lock. */
    private val renderLock = Mutex()
    private val pending = ConcurrentHashMap<String, Deferred<TtsClip?>>()

    override val bakesPitch = true

    override suspend fun prepare() = engine.init()

    override suspend fun clip(text: String, voice: String?, speed: Float, pitch: Float): TtsClip? {
        val key = key(text, voice, speed, pitch)
        return try {
            request(key, text, voice, speed, pitch).await()
        } finally {
            pending.remove(key)
        }
    }

    override fun ahead(texts: List<String>, voice: String?, speed: Float, pitch: Float) {
        // ponytail: the rest of the paragraph; the next paragraph renders when it starts
        texts.drop(1).forEach { request(key(it, voice, speed, pitch), it, voice, speed, pitch) }
    }

    override fun release() {
        scope.cancel()
        pending.clear()
        engine.release()
    }

    private fun request(key: String, text: String, voice: String?, speed: Float, pitch: Float): Deferred<TtsClip?> =
        pending.getOrPut(key) {
            scope.async {
                TtsClipCache.read(context, key) ?: renderLock.withLock {
                    engine.setVoice(voice)
                    engine.setSpeed(speed)
                    engine.setPitch(pitch)
                    engine.synthesize(text)
                }?.also { TtsClipCache.write(context, key, it) }
            }.also { job ->
                // Finished audio is on disk; holding it here too would grow with every skipped sentence.
                job.invokeOnCompletion { pending.remove(key, job) }
            }
        }

    // After a fallback the default engine speaks, so its audio is filed under it, not the chosen one.
    private fun key(text: String, voice: String?, speed: Float, pitch: Float) =
        "system|${engine.packageInUse}|${voice.orEmpty()}|$speed|$pitch|$text"
}
