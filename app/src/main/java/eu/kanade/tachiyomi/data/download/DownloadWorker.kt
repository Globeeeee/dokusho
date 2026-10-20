package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.asFlow
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.connectivityManager
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.R
import kotlin.time.Duration.Companion.seconds

/**
 * This worker owns the lifecycle of the downloader: it starts the downloader and stops it when the
 * worker is stopped by the system or there's no suitable network available.
 */
class DownloadWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var downloader: Downloader

    @Inject private lateinit var downloadPreferences: DownloadPreferences

    init {
        graph.inject(this)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle(applicationContext.getString(R.string.download_notifier_downloader_title))
            setSmallIcon(android.R.drawable.stat_sys_download)
        }.build()
        return ForegroundInfo(
            Notifications.ID_DOWNLOAD_CHAPTER_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        var networkIssue = networkIssue()
        if (networkIssue != null) {
            downloader.stop(networkIssue)
            // Runs again once the network allows it, instead of leaving the queue waiting for a manual resume
            return Result.retry()
        }

        if (!downloader.start()) return Result.failure()

        try {
            setForegroundSafely()

            while (networkIssue == null && downloader.isRunning) {
                delay(1.seconds)
                networkIssue = networkIssue()
            }
        } finally {
            // Stopped by WorkManager means already canceled, and taking the lock would then throw instead of stopping
            if (downloader.isRunning && (networkIssue != null || isStopped)) {
                withContext(NonCancellable) { downloader.stop(networkIssue) }
            }
        }

        return if (networkIssue != null) Result.retry() else Result.success()
    }

    private fun networkIssue(): String? {
        return when (networkIssue(applicationContext, downloadPreferences.downloadOnlyOverWifi.get())) {
            NetworkIssue.Offline -> applicationContext.getString(R.string.download_notifier_no_network)
            NetworkIssue.NoWifi -> applicationContext.getString(R.string.download_notifier_text_only_wifi)
            null -> null
        }
    }

    enum class NetworkIssue { Offline, NoWifi }

    companion object {
        private const val TAG = "Downloader"

        /**
         * Queues the worker. It waits for a network that [wifiOnly] allows, the same rule the worker checks, so
         * chapters queued offline or off Wi-Fi start on their own once one is back.
         */
        fun start(context: Context, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .addTag(TAG)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType(wifiOnly)).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        /** Drops a worker still waiting for the network, so a paused or cleared queue doesn't start on its own. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }

        fun networkType(wifiOnly: Boolean): NetworkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        /** Why downloads can't run on the current network, if they can't. Only on Wi-Fi means not metered. */
        fun networkIssue(context: Context, wifiOnly: Boolean): NetworkIssue? {
            return when {
                !context.activeNetworkState().isOnline -> NetworkIssue.Offline
                wifiOnly && context.connectivityManager.isActiveNetworkMetered -> NetworkIssue.NoWifi
                else -> null
            }
        }

        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(TAG)
                .asFlow()
                .map { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }
    }
}
