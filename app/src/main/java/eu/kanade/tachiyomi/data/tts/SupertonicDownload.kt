package eu.kanade.tachiyomi.data.tts

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

internal sealed interface SupertonicDownloadState {
    data object Idle : SupertonicDownloadState

    data object Queued : SupertonicDownloadState

    data class Running(val bytes: Long, val total: Long) : SupertonicDownloadState {
        val percent: Int
            get() = if (total <= 0L) 0 else ((bytes * 100) / total).toInt().coerceIn(0, 100)
    }

    data object Ready : SupertonicDownloadState

    data object Failed : SupertonicDownloadState
}

/**
 * Downloads the Supertonic weights in the background. Playback stays on the system voice; this
 * only fetches the files and exposes progress to the settings UI.
 */
internal object SupertonicDownload {
    const val WORK_NAME = "supertonic_download"
    const val KEY_BYTES = "bytes"
    const val KEY_TOTAL = "total"

    private val mutable = MutableStateFlow<SupertonicDownloadState>(SupertonicDownloadState.Idle)
    val state: StateFlow<SupertonicDownloadState> = mutable.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bound = AtomicBoolean(false)

    fun bind(context: Context) {
        val app = context.applicationContext
        if (SupertonicModel.isReady(app)) mutable.value = SupertonicDownloadState.Ready
        if (!bound.compareAndSet(false, true)) return
        scope.launch {
            WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(WORK_NAME).collect { infos ->
                apply(app, infos)
            }
        }
    }

    fun publish(next: SupertonicDownloadState) {
        mutable.value = next
    }

    fun progressData(bytes: Long, total: Long) = workDataOf(KEY_BYTES to bytes, KEY_TOTAL to total)

    /** Starts the download unless it is already stored or already queued. */
    fun start(context: Context) {
        val app = context.applicationContext
        bind(app)
        if (SupertonicModel.isReady(app)) {
            mutable.value = SupertonicDownloadState.Ready
            return
        }
        val wifiOnly = app.appGraph.downloadPreferences.downloadOnlyOverWifi.get()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SupertonicDownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        if (mutable.value !is SupertonicDownloadState.Running && mutable.value !is SupertonicDownloadState.Queued) {
            mutable.value = SupertonicDownloadState.Queued
        }
    }

    /** Tap handler for the Supertonic engine row. Never switches the speaking engine. */
    fun onSelected(context: Context) {
        when (val status = state.value) {
            SupertonicDownloadState.Ready -> context.toast(MR.strings.tts_supertonic_downloaded)
            is SupertonicDownloadState.Running -> context.toast(
                context.stringResource(MR.strings.tts_supertonic_progress, status.percent),
            )
            else -> {
                start(context)
                val network = context.activeNetworkState()
                val wifiOnly = context.appGraph.downloadPreferences.downloadOnlyOverWifi.get()
                val waiting = !network.isOnline || (wifiOnly && !network.isWifi)
                context.toast(
                    if (waiting) MR.strings.tts_supertonic_queued else MR.strings.tts_supertonic_downloading,
                )
            }
        }
    }

    private fun apply(context: Context, infos: List<WorkInfo>) {
        if (SupertonicModel.isReady(context)) {
            mutable.value = SupertonicDownloadState.Ready
            return
        }
        val info = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
            ?: infos.firstOrNull { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
            ?: infos.firstOrNull()
            ?: return
        when (info.state) {
            WorkInfo.State.RUNNING -> {
                val total = info.progress.getLong(KEY_TOTAL, SupertonicModel.totalBytes)
                    .takeIf { it > 0L } ?: SupertonicModel.totalBytes
                val reported = info.progress.getLong(KEY_BYTES, -1L)
                val current = (mutable.value as? SupertonicDownloadState.Running)?.bytes ?: 0L
                val bytes = when {
                    reported >= 0L -> max(reported, current)
                    current > 0L -> current
                    else -> SupertonicModel.storedBytes(context)
                }
                mutable.value = SupertonicDownloadState.Running(bytes.coerceAtMost(total), total)
            }
            WorkInfo.State.ENQUEUED -> {
                if (mutable.value !is SupertonicDownloadState.Running) {
                    mutable.value = SupertonicDownloadState.Queued
                }
            }
            WorkInfo.State.BLOCKED -> mutable.value = SupertonicDownloadState.Queued
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                if (mutable.value !is SupertonicDownloadState.Running) {
                    mutable.value = SupertonicDownloadState.Failed
                }
            }
            WorkInfo.State.SUCCEEDED -> mutable.value = SupertonicDownloadState.Failed
        }
    }
}
