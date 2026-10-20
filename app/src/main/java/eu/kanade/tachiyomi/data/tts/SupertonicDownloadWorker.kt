package eu.kanade.tachiyomi.data.tts

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Foreground download of the pinned Supertonic 3 files. Finished files are renamed into place so
 * a partial download is never treated as complete. Speech is not switched over to these files.
 */
class SupertonicDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    private var shownPercent = 0
    private var shownText = ""
    private var lastNotifyAt = 0L
    private var lastPublished = -1L

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_SUPERTONIC_DOWNLOAD,
            progressNotification(shownPercent, shownText.ifEmpty { downloadingTitle() }, ongoing = true),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        if (SupertonicModel.isReady(applicationContext)) {
            SupertonicDownload.publish(SupertonicDownloadState.Ready)
            return Result.success()
        }
        val stored = SupertonicModel.storedBytes(applicationContext)
        report(stored, force = true)
        return try {
            setForegroundSafely()
            withContext(Dispatchers.IO) { downloadAll() }
            SupertonicDownload.publish(SupertonicDownloadState.Ready)
            applicationContext.notify(
                Notifications.ID_SUPERTONIC_DOWNLOAD_DONE,
                Notifications.CHANNEL_DOWNLOADER_PROGRESS,
            ) {
                setContentTitle(applicationContext.stringResource(MR.strings.tts_supertonic_downloaded))
                setSmallIcon(android.R.drawable.stat_sys_download_done)
                setAutoCancel(true)
                setOngoing(false)
            }
            applicationContext.cancelNotification(Notifications.ID_SUPERTONIC_DOWNLOAD)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: DownloadStopped) {
            Result.retry()
        } catch (e: PermanentFailure) {
            fail(e)
        } catch (e: Exception) {
            if (runAttemptCount >= 4) {
                fail(e)
            } else {
                logcat(LogPriority.WARN, e) { "Supertonic download will retry" }
                Result.retry()
            }
        }
    }

    private fun downloadAll() {
        var completed = 0L
        for (model in SupertonicModel.files) {
            if (isStopped) throw DownloadStopped()
            val dest = SupertonicModel.file(applicationContext, model.path)
            if (dest.length() == model.bytes) {
                completed += model.bytes
                report(completed, force = false)
                continue
            }
            downloadOne(model, completed)
            completed += model.bytes
            report(completed, force = true)
        }
    }

    private fun downloadOne(model: ModelFile, completed: Long) {
        val dest = SupertonicModel.file(applicationContext, model.path)
        if (dest.exists()) dest.delete()
        dest.parentFile?.mkdirs()
        val partial = SupertonicModel.partial(applicationContext, model.path)
        if (partial.length() > model.bytes) partial.delete()
        if (partial.length() == model.bytes) {
            moveIntoPlace(partial, dest)
            return
        }
        var resume = partial.length() > 0L
        var finished = false
        repeat(2) {
            if (finished) return@repeat
            val offset = if (resume) partial.length() else 0L
            if (!resume && partial.exists()) partial.delete()
            when (transfer(model, partial, offset, completed)) {
                Transfer.RangeRejected -> {
                    resume = false
                    partial.delete()
                }
                Transfer.Done -> finished = true
            }
        }
        if (partial.length() != model.bytes) {
            throw IOException("Incomplete ${model.path}")
        }
        moveIntoPlace(partial, dest)
    }

    private fun transfer(model: ModelFile, partial: File, offset: Long, completed: Long): Transfer {
        val request = Request.Builder()
            .url(SupertonicModel.url(model.path))
            .header("User-Agent", "Dokusho")
        if (offset > 0L) request.header("Range", "bytes=$offset-")
        client.newCall(request.build()).execute().use { response ->
            if (response.code == 416 && offset > 0L) return Transfer.RangeRejected
            if (response.code !in SUCCESS) {
                if (response.code in 400..499) {
                    throw PermanentFailure("HTTP ${response.code} for ${model.path}")
                }
                throw IOException("HTTP ${response.code} for ${model.path}")
            }
            val append = offset > 0L && response.code == 206
            val start = if (append) offset else 0L
            val body = response.body
            FileOutputStream(partial, append).use { stream ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER)
                    var written = start
                    while (true) {
                        if (isStopped) throw DownloadStopped()
                        val read = input.read(buffer)
                        if (read < 0) break
                        stream.write(buffer, 0, read)
                        written += read
                        report(completed + written, force = false)
                    }
                    stream.flush()
                }
            }
        }
        return Transfer.Done
    }

    private fun report(bytes: Long, force: Boolean) {
        val total = SupertonicModel.totalBytes
        val clamped = bytes.coerceIn(0L, total)
        val step = (total / 100).coerceAtLeast(256L * 1024)
        if (!force && lastPublished >= 0L && clamped - lastPublished < step && clamped < total) return
        lastPublished = clamped
        SupertonicDownload.publish(SupertonicDownloadState.Running(clamped, total))
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastNotifyAt < NOTIFY_INTERVAL_MS && clamped < total) return
        lastNotifyAt = now
        shownPercent = if (total == 0L) 0 else ((clamped * 100) / total).toInt().coerceIn(0, 100)
        shownText = "${clamped.asMegabytes()} / ${total.asMegabytes()}"
        applicationContext.notify(
            Notifications.ID_SUPERTONIC_DOWNLOAD,
            progressNotification(shownPercent, shownText, true),
        )
        runCatching { setProgressAsync(SupertonicDownload.progressData(clamped, total)) }
    }

    private fun progressNotification(percent: Int, text: String, ongoing: Boolean) =
        applicationContext.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle(downloadingTitle())
            setContentText(text)
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(ongoing)
            setOnlyAlertOnce(true)
            setProgress(100, percent, percent == 0)
            setCategory(NotificationCompat.CATEGORY_PROGRESS)
        }.build()

    private fun downloadingTitle(): String =
        applicationContext.stringResource(MR.strings.tts_supertonic_notification)

    private fun fail(error: Exception): Result {
        logcat(LogPriority.ERROR, error) { "Supertonic download failed" }
        SupertonicDownload.publish(SupertonicDownloadState.Failed)
        applicationContext.notify(
            Notifications.ID_SUPERTONIC_DOWNLOAD_DONE,
            Notifications.CHANNEL_DOWNLOADER_ERROR,
        ) {
            setContentTitle(applicationContext.stringResource(MR.strings.tts_supertonic_failed))
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setAutoCancel(true)
        }
        applicationContext.cancelNotification(Notifications.ID_SUPERTONIC_DOWNLOAD)
        return Result.failure()
    }

    private enum class Transfer { Done, RangeRejected }

    private class PermanentFailure(message: String) : IOException(message)

    private class DownloadStopped : Exception()
}

private fun moveIntoPlace(partial: File, dest: File) {
    if (dest.exists()) dest.delete()
    if (!partial.renameTo(dest)) {
        partial.copyTo(dest, overwrite = true)
        partial.delete()
    }
}

private fun Long.asMegabytes(): String {
    val mb = this / (1024.0 * 1024.0)
    return if (mb >= 10) "${mb.roundToInt()} MB" else "%.1f MB".format(mb)
}

private const val BUFFER = 64 * 1024
private const val NOTIFY_INTERVAL_MS = 500L
private val SUCCESS = listOf(200, 206)
