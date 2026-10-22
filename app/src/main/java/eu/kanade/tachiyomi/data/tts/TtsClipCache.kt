package eu.kanade.tachiyomi.data.tts

import android.content.Context
import eu.kanade.tachiyomi.util.lang.Hash
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One sentence of audio and the rate it plays at. */
class TtsClip(val audio: FloatArray, val sampleRate: Int)

/**
 * Spoken sentences on disk, so a chapter listened to again, or after the app was closed, plays
 * without synthesizing it a second time. Supertonic always uses it; system engines when "Save spoken
 * audio" is on. 16-bit mono PCM per sentence in the app cache after a 4-byte sample rate, keyed by
 * engine, voice, speed, pitch and text; the least recently played sentences go first past [MAX_BYTES].
 */
object TtsClipCache {

    // ponytail: about 55 minutes of audio at 44.1 kHz, three to five chapters
    private const val MAX_BYTES = 300L * 1024 * 1024

    /** Bump when a model or the file layout changes, so old audio is never played for new settings. */
    private const val FORMAT = "2"

    private const val DIRECTORY = "tts/clips"

    private val lock = Any()
    private var dir: File? = null
    private var totalBytes = -1L

    fun read(context: Context, key: String): TtsClip? {
        val file = file(context, key)
        if (!file.isFile) return null
        return try {
            val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            file.setLastModified(System.currentTimeMillis())
            val sampleRate = buffer.getInt()
            val pcm = buffer.asShortBuffer()
            TtsClip(FloatArray(pcm.remaining()) { pcm.get(it) / 32767f }, sampleRate)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Supertonic clip cache could not read a sentence" }
            file.delete()
            null
        }
    }

    fun write(context: Context, key: String, clip: TtsClip) {
        val audio = clip.audio
        if (audio.isEmpty()) return
        val file = file(context, key)
        val bytes = ByteBuffer.allocate(4 + audio.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.putInt(clip.sampleRate)
        audio.forEach { bytes.putShort((it.coerceIn(-1f, 1f) * 32767f).toInt().toShort()) }
        try {
            // Written beside and renamed, so a killed process never leaves half a sentence to play.
            val partial = File(file.parentFile, file.name + ".tmp")
            partial.writeBytes(bytes.array())
            if (!partial.renameTo(file)) {
                partial.delete()
                return
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Supertonic clip cache could not save a sentence" }
            return
        }
        synchronized(lock) {
            totalBytes += file.length()
            if (totalBytes > MAX_BYTES) trimLocked(context)
        }
    }

    private fun file(context: Context, key: String): File =
        File(directory(context), Hash.sha256("$FORMAT|$key") + ".pcm")

    private fun directory(context: Context): File = synchronized(lock) {
        dir ?: File(context.cacheDir, DIRECTORY).also { created ->
            created.mkdirs()
            dir = created
            totalBytes = created.listFiles().orEmpty().sumOf { it.length() }
            if (totalBytes > MAX_BYTES) trimLocked(context)
        }
    }

    /** Deletes the least recently played sentences down to three quarters of the limit. */
    private fun trimLocked(context: Context) {
        val files = File(context.cacheDir, DIRECTORY).listFiles().orEmpty().sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= MAX_BYTES * 3 / 4) break
            val size = file.length()
            if (file.delete()) total -= size
        }
        totalBytes = total
    }
}
