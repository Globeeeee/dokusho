package eu.kanade.tachiyomi.data.export

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.reader.viewer.novel.NovelHtml
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.export.service.ExportFormat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import java.time.Instant

/** What the export sheet asks for; applied to every novel of one run. */
data class NovelExportOptions(
    val format: ExportFormat,
    val range: ExportRange,
    val from: Int,
    val to: Int,
    val chaptersPerFile: Int,
    val downloadMissing: Boolean,
)

/**
 * Exports novels to EPUB or PDF under `<storage root>/exports/<Title>/`, one novel after another.
 * Locked chapters are never exported; chapters without text are skipped and listed when it ends.
 */
class ExportWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var getManga: GetManga

    @Inject private lateinit var getChaptersByMangaId: GetChaptersByMangaId

    @Inject private lateinit var sourceManager: SourceManager

    @Inject private lateinit var storageManager: StorageManager

    @Inject private lateinit var downloadManager: DownloadManager

    @Inject private lateinit var loader: ExportContentLoader

    @Inject private lateinit var notifier: ExportNotifier

    private var progressTitle = ""

    override suspend fun getForegroundInfo(): ForegroundInfo {
        if (!::notifier.isInitialized) graph.inject(this)
        return ForegroundInfo(
            Notifications.ID_EXPORT_PROGRESS,
            notifier.progress(id, progressTitle, 1, 1, 0, 0).build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    override suspend fun doWork(): Result {
        graph.inject(this)
        val mangaIds = inputData.getLongArray(KEY_MANGA_IDS)?.distinct().orEmpty()
        val options = inputData.toOptions() ?: return Result.failure()
        if (mangaIds.isEmpty()) return Result.failure()

        setForegroundSafely()

        val files = mutableListOf<UniFile>()
        val skipped = mutableListOf<String>()
        val folders = mutableListOf<String>()
        return try {
            val root = withContext(Dispatchers.IO) { storageManager.getExportsDirectory() }
                ?: throw Exception(context.stringResource(MR.strings.export_no_storage))
            mangaIds.forEachIndexed { index, mangaId ->
                val manga = getManga.await(mangaId) ?: return@forEachIndexed
                val source = sourceManager.getOrStub(manga.source)
                val dir = withContext(Dispatchers.IO) { root.createDirectory(ExportPlan.safeTitle(manga.title)) }
                    ?: throw Exception(context.stringResource(MR.strings.export_no_storage))
                folders += dir.displayablePath
                exportNovel(manga, source, dir, options, index + 1, mangaIds.size, files, skipped)
            }
            notifier.showComplete(files, folders.singleOrNull() ?: root.displayablePath, skipped)
            Result.success()
        } catch (e: CancellationException) {
            notifier.cancelProgress()
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Export failed" }
            notifier.showError(e.message)
            Result.failure()
        }
    }

    private suspend fun exportNovel(
        manga: Manga,
        source: Source,
        dir: UniFile,
        options: NovelExportOptions,
        novel: Int,
        novels: Int,
        files: MutableList<UniFile>,
        skipped: MutableList<String>,
    ) {
        // Reading order: the source lists newest first.
        val chapters = getChaptersByMangaId.await(manga.id).sortedByDescending { it.sourceOrder }
        val mangaDir = loader.mangaDir(manga, source)
        val positions = ExportPlan.select(chapters.size, options.range, options.from, options.to) {
            val chapter = chapters[it - 1]
            downloadManager.isChapterDownloaded(chapter.name, chapter.scanlator, chapter.url, manga.title, manga.source)
        }
        val parts = ExportPlan.split(positions, options.chaptersPerFile)
        progressTitle = manga.title
        var done = 0
        fun report() = notifier.showProgress(notifier.progress(id, manga.title, novel, novels, done, positions.size))
        report()

        val cover = if (options.format == ExportFormat.EPUB) loader.cover(manga) else null
        val novelSkipped = mutableListOf<String>()
        for (part in parts) {
            val name = ExportPlan.fileName(manga.title, part, options.format.extension)
            val file = withContext(Dispatchers.IO) {
                dir.findFile(name)?.delete()
                dir.createFile(name)
            } ?: throw Exception(context.stringResource(MR.strings.export_no_storage))
            var written = 0
            try {
                withContext(Dispatchers.IO) {
                    file.openOutputStream().use { out ->
                        val sink = when (options.format) {
                            ExportFormat.EPUB -> EpubSink(
                                EpubWriter(out, metadata(manga, source, part, parts.size)),
                                cover,
                            )
                            ExportFormat.PDF -> PdfSink(PdfWriter(out))
                        }
                        sink.use {
                            for (position in part.positions) {
                                val chapter = chapters[position - 1]
                                if (addChapter(
                                        sink,
                                        chapter,
                                        position,
                                        source,
                                        mangaDir,
                                        options.downloadMissing,
                                        novelSkipped,
                                    )
                                ) {
                                    written++
                                }
                                done++
                                report()
                            }
                            if (written > 0) sink.finish()
                        }
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.IO + NonCancellable) { file.delete() }
                throw e
            }
            if (written > 0) files += file else withContext(Dispatchers.IO) { file.delete() }
        }
        skipped += if (novels > 1) novelSkipped.map { "${manga.title}: $it" } else novelSkipped
    }

    /** Adds one chapter; false when it was skipped (and listed in [skipped]). */
    private suspend fun addChapter(
        sink: ExportSink,
        chapter: Chapter,
        position: Int,
        source: Source,
        mangaDir: UniFile?,
        downloadMissing: Boolean,
        skipped: MutableList<String>,
    ): Boolean {
        val text = try {
            loader.chapterText(chapter, source, mangaDir, downloadMissing)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Export could not load a chapter" }
            null
        }
        val reason = when (text) {
            is ExportText.Ready -> null
            ExportText.Locked -> MR.strings.export_skipped_locked
            ExportText.NotDownloaded -> MR.strings.export_skipped_not_downloaded
            ExportText.Empty -> MR.strings.novel_chapter_empty
            null -> MR.strings.export_skipped_failed
        }
        if (text !is ExportText.Ready) {
            skipped +=
                context.stringResource(MR.strings.export_skipped_line, chapter.name, context.stringResource(reason!!))
            return false
        }
        val blocks = NovelHtml.parse(text.html)
        if (blocks.isEmpty()) {
            skipped += context.stringResource(
                MR.strings.export_skipped_line,
                chapter.name,
                context.stringResource(MR.strings.novel_chapter_empty),
            )
            return false
        }
        sink.add(position, chapter.name, blocks) { url -> loader.image(url, text.dir, source, downloadMissing) }
        return true
    }

    private fun metadata(manga: Manga, source: Source, part: ExportPart, partCount: Int): EpubMetadata {
        val base = "urn:dokusho:${source.id}:${Hash.md5(manga.url)}"
        val range = "ch%04d-%04d".format(part.first, part.last)
        return EpubMetadata(
            identifier = if (partCount > 1) "$base:$range" else base,
            title = if (partCount > 1) "${manga.title} ($range)" else manga.title,
            author = manga.author,
            language = source.lang.takeIf { it.length in 2..8 && it != "all" && it != "other" } ?: "und",
            modified = Instant.now(),
        )
    }

    companion object {
        private const val TAG = "NovelExport"
        private const val KEY_MANGA_IDS = "manga_ids"
        private const val KEY_FORMAT = "format"
        private const val KEY_RANGE = "range"
        private const val KEY_FROM = "from"
        private const val KEY_TO = "to"
        private const val KEY_SPLIT = "split"
        private const val KEY_DOWNLOAD_MISSING = "download_missing"

        /** Queues an export; runs after any export already in progress. */
        fun start(context: Context, mangaIds: List<Long>, options: NovelExportOptions) {
            val data = workDataOf(
                KEY_MANGA_IDS to mangaIds.toLongArray(),
                KEY_FORMAT to options.format.name,
                KEY_RANGE to options.range.name,
                KEY_FROM to options.from,
                KEY_TO to options.to,
                KEY_SPLIT to options.chaptersPerFile,
                KEY_DOWNLOAD_MISSING to options.downloadMissing,
            )
            val request = OneTimeWorkRequestBuilder<ExportWorker>()
                .addTag(TAG)
                .setInputData(data)
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        private fun Data.toOptions(): NovelExportOptions? {
            val format = getString(KEY_FORMAT)?.let { runCatching { ExportFormat.valueOf(it) }.getOrNull() }
            val range = getString(KEY_RANGE)?.let { runCatching { ExportRange.valueOf(it) }.getOrNull() }
            if (format == null || range == null) return null
            return NovelExportOptions(
                format = format,
                range = range,
                from = getInt(KEY_FROM, 1),
                to = getInt(KEY_TO, Int.MAX_VALUE),
                chaptersPerFile = getInt(KEY_SPLIT, 0),
                downloadMissing = getBoolean(KEY_DOWNLOAD_MISSING, false),
            )
        }
    }
}
