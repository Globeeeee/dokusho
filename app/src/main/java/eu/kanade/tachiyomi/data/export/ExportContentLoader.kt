package eu.kanade.tachiyomi.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil3.imageLoader
import coil3.request.Options
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Inject
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.coil.MangaKeyer
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.Downloader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.io.ByteArrayOutputStream

class ExportImage(val bytes: ByteArray, val mediaType: String)

sealed interface ExportText {
    /** The chapter's HTML; [dir] is its download folder, where relative image names point. */
    class Ready(val html: String, val dir: UniFile?) : ExportText

    data object Locked : ExportText

    data object NotDownloaded : ExportText

    data object Empty : ExportText
}

/**
 * Finds what an export needs without reaching past the user's own access: downloaded files first, then
 * the caches, and the source only when the user turned on "download missing".
 */
@Inject
class ExportContentLoader(
    private val context: Context,
    private val chapterCache: ChapterCache,
    private val coverCache: CoverCache,
    private val downloadProvider: DownloadProvider,
) {

    suspend fun mangaDir(manga: Manga, source: Source): UniFile? = downloadProvider.findMangaDir(manga.title, source)

    /** Downloaded chapter.html, then the chapter cache, then the source when [fetchMissing] is on. */
    suspend fun chapterText(
        chapter: Chapter,
        source: Source,
        mangaDir: UniFile?,
        fetchMissing: Boolean,
    ): ExportText = withContext(Dispatchers.IO) {
        val dir = mangaDir?.let { parent ->
            downloadProvider.getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.url)
                .firstNotNullOfOrNull { parent.findFile(it) }
        }
        dir?.findFile(Downloader.NOVEL_CHAPTER_FILE)
            ?.openInputStream()
            ?.use { it.bufferedReader().readText() }
            ?.takeIf { it.isNotBlank() }
            ?.let { return@withContext ExportText.Ready(it, dir) }

        chapterCache.getChapterText(chapter)?.takeIf { it.isNotBlank() }
            ?.let { return@withContext ExportText.Ready(it, null) }

        val novelSource = source as? NovelSource
        if (!fetchMissing || novelSource == null) return@withContext ExportText.NotDownloaded
        // The extension's own client carries its rate limit and the user's session.
        val content = novelSource.getChapterContent(chapter.toSChapter())
        when {
            content.locked -> ExportText.Locked
            content.html.isBlank() -> ExportText.Empty
            else -> {
                chapterCache.putChapterText(chapter, content.html)
                ExportText.Ready(content.html, null)
            }
        }
    }

    /**
     * An image of a chapter: a file saved next to a downloaded chapter, or a web image already in a cache.
     * New network requests only when [fetchMissing] is on, with the source's headers.
     */
    suspend fun image(url: String, dir: UniFile?, source: Source, fetchMissing: Boolean): ExportImage? =
        withContext(Dispatchers.IO) {
            try {
                val bytes = when {
                    !url.contains("://") -> dir?.findFile(url)?.openInputStream()?.use { it.readBytes() }
                    url.startsWith("http") -> cachedImage(url) ?: if (fetchMissing) fetchImage(url, source) else null
                    else -> null
                }
                bytes?.let(::normalize)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN, e) { "Export left out an image" }
                null
            }
        }

    /** The cover the app already shows: custom cover, library cover cache, or Coil's disk cache. */
    suspend fun cover(manga: Manga): ExportImage? = withContext(Dispatchers.IO) {
        try {
            val file = if (manga.hasCustomCover(coverCache)) {
                coverCache.getCustomCoverFile(manga.id)
            } else {
                coverCache.getCoverFile(manga.thumbnailUrl)
            }
            val bytes = file?.takeIf { it.exists() }?.readBytes()
                ?: coilCache(MangaKeyer().key(manga, Options(context)))
            bytes?.let(::normalize)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Export has no cover" }
            null
        }
    }

    private fun cachedImage(url: String): ByteArray? {
        coilCache(url)?.let { return it }
        return chapterCache.getImageFile(url).takeIf { it.exists() }?.readBytes()
    }

    private fun coilCache(key: String): ByteArray? {
        val diskCache = context.imageLoader.diskCache ?: return null
        return diskCache.openSnapshot(key)?.use { snapshot ->
            diskCache.fileSystem.read(snapshot.data) { readByteArray() }
        }
    }

    private suspend fun fetchImage(url: String, source: Source): ByteArray? {
        val httpSource = source as? HttpSource ?: return null
        return httpSource.client.newCall(GET(url, httpSource.headers)).awaitSuccess().use { it.body.bytes() }
    }

    /** Keeps the formats every EPUB reader shows and turns anything else into JPEG. */
    private fun normalize(bytes: ByteArray): ExportImage? {
        val type = ImageUtil.findImageType(bytes.inputStream()) ?: return null
        if (type.mime in EpubWriter.CORE_IMAGE_TYPES) return ExportImage(bytes, type.mime)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bitmap.recycle()
        return ExportImage(out.toByteArray(), "image/jpeg")
    }

    private companion object {
        const val JPEG_QUALITY = 90
    }
}
