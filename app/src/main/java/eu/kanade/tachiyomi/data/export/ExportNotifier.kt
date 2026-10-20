package eu.kanade.tachiyomi.data.export

import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.util.UUID
import mihon.icons.materialsymbols.R as MaterialSymbolsR

/** Progress, result and error notifications of [ExportWorker]. Never shows chapter text. */
@Inject
class ExportNotifier(
    private val context: Context,
    private val securityPreferences: SecurityPreferences,
) {

    private val progressBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_EXPORT) {
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            setSmallIcon(R.drawable.ic_dokusho)
            setAutoCancel(false)
            setOngoing(true)
            setOnlyAlertOnce(true)
        }
    }

    /** [novel] is 1-based among [novels]; [done] of [total] chapters of the current novel. */
    fun progress(
        workId: UUID,
        title: String,
        novel: Int,
        novels: Int,
        done: Int,
        total: Int,
    ): NotificationCompat.Builder = progressBuilder.apply {
        val hide = securityPreferences.hideNotificationContent.get()
        setContentTitle(
            if (novels > 1) {
                context.stringResource(MR.strings.export_progress_title_many, novel, novels)
            } else {
                context.stringResource(MR.strings.export_progress_title)
            },
        )
        val count = context.stringResource(MR.strings.export_progress_chapters, done, total)
        setContentText(if (hide || title.isBlank()) count else "$title · $count")
        setProgress(total, done, total == 0)
        clearActions()
        addAction(
            MaterialSymbolsR.drawable.rounded_close,
            context.stringResource(MR.strings.action_cancel),
            WorkManager.getInstance(context).createCancelPendingIntent(workId),
        )
    }

    fun showProgress(builder: NotificationCompat.Builder) {
        context.notify(Notifications.ID_EXPORT_PROGRESS, builder.build())
    }

    /** [files] are every file written; [skipped] the chapter names left out, with why. */
    fun showComplete(files: List<UniFile>, folder: String, skipped: List<String>) {
        context.cancelNotification(Notifications.ID_EXPORT_PROGRESS)
        val hide = securityPreferences.hideNotificationContent.get()
        context.notify(Notifications.ID_EXPORT_COMPLETE, Notifications.CHANNEL_EXPORT) {
            setSmallIcon(R.drawable.ic_dokusho)
            setAutoCancel(true)
            if (files.isEmpty()) {
                setContentTitle(context.stringResource(MR.strings.export_nothing))
            } else {
                setContentTitle(context.pluralStringResource(MR.plurals.export_complete, files.size, files.size))
            }
            val summary = buildString {
                if (!hide) append(folder)
                if (skipped.isNotEmpty()) {
                    if (isNotEmpty()) append('\n')
                    append(context.pluralStringResource(MR.plurals.export_skipped, skipped.size, skipped.size))
                    if (!hide) {
                        skipped.take(MAX_SKIPPED_LINES).forEach { append('\n').append(it) }
                        if (skipped.size > MAX_SKIPPED_LINES) append("\n…")
                    }
                }
            }
            setContentText(summary.substringBefore('\n'))
            setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            files.firstOrNull()?.let { first ->
                val open = openIntent(first)
                setContentIntent(open)
                addAction(
                    MaterialSymbolsR.drawable.rounded_filled_folder,
                    context.stringResource(MR.strings.export_open),
                    open,
                )
                addAction(
                    MaterialSymbolsR.drawable.rounded_share,
                    context.stringResource(MR.strings.action_share),
                    shareIntent(files),
                )
            }
        }
    }

    fun showError(message: String?) {
        context.cancelNotification(Notifications.ID_EXPORT_PROGRESS)
        context.notify(Notifications.ID_EXPORT_COMPLETE, Notifications.CHANNEL_EXPORT) {
            setSmallIcon(R.drawable.ic_dokusho)
            setAutoCancel(true)
            setContentTitle(context.stringResource(MR.strings.export_failed))
            setContentText(message)
        }
    }

    fun cancelProgress() {
        context.cancelNotification(Notifications.ID_EXPORT_PROGRESS)
    }

    private fun openIntent(file: UniFile): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, mimeOf(file))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val chooser = Intent.createChooser(intent, context.stringResource(MR.strings.export_open)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            context,
            1,
            chooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun shareIntent(files: List<UniFile>): PendingIntent {
        val uris = files.map { it.uri }
        val type = files.map(::mimeOf).distinct().singleOrNull() ?: "*/*"
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
        }.apply {
            setType(type)
            clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val chooser = Intent.createChooser(send, context.stringResource(MR.strings.action_share)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            context,
            2,
            chooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun mimeOf(file: UniFile): String =
        if (file.name.orEmpty().endsWith(".pdf")) "application/pdf" else EpubWriter.MIMETYPE

    private companion object {
        const val MAX_SKIPPED_LINES = 15
    }
}
