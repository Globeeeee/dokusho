package eu.kanade.tachiyomi.ui.reader.loader

import androidx.compose.ui.text.AnnotatedString
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.novel.NovelBlock
import eu.kanade.tachiyomi.ui.reader.viewer.novel.NovelHtml

/**
 * Loads a text chapter from a [NovelSource] and exposes each paragraph as one [ReaderPage], so the
 * existing progress code stores the paragraph index as the last page read.
 */
internal class NovelPageLoader(
    private val chapter: ReaderChapter,
    private val source: NovelSource,
    private val chapterCache: ChapterCache,
    private val lockedMessage: String,
    private val emptyMessage: String,
    /** Reads a downloaded chapter.html; null when the chapter is not downloaded. */
    private val downloadedText: (suspend () -> String?)? = null,
) : PageLoader() {

    /** True once the text came from a download, so download-ahead treats it like a downloaded chapter. */
    override var isLocal: Boolean = false

    override suspend fun getPages(): List<ReaderPage> {
        val dbChapter = chapter.chapter
        val downloaded = downloadedText?.invoke()
        if (downloaded != null) isLocal = true
        val html = downloaded ?: chapterCache.getChapterText(dbChapter) ?: run {
            val content = source.getChapterContent(dbChapter.toSChapter())
            if (content.locked) {
                return listOf(NovelPage(0, NovelBlock.Paragraph(AnnotatedString(lockedMessage))))
            }
            // Locked chapters are never cached, so unlocking one on the site shows it next time.
            content.html.also { if (it.isNotBlank()) chapterCache.putChapterText(dbChapter, it) }
        }
        val blocks = NovelHtml.parse(html).ifEmpty {
            listOf(NovelBlock.Paragraph(AnnotatedString(emptyMessage)))
        }
        return blocks.mapIndexed { index, block -> NovelPage(index, block) }
    }
}

/** A paragraph of a novel chapter. Always ready; there is nothing to download per page. */
class NovelPage(index: Int, val block: NovelBlock) : ReaderPage(index) {
    init {
        status = Page.State.Ready
    }
}
