package eu.kanade.tachiyomi.ui.reader.loader

import androidx.compose.ui.text.AnnotatedString
import eu.kanade.domain.chapter.model.toSChapter
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
    private val lockedMessage: String,
    private val emptyMessage: String,
) : PageLoader() {

    override var isLocal: Boolean = false

    override suspend fun getPages(): List<ReaderPage> {
        val content = source.getChapterContent(chapter.chapter.toSChapter())
        val blocks = when {
            content.locked -> listOf(NovelBlock.Paragraph(AnnotatedString(lockedMessage)))
            else -> NovelHtml.parse(content.html).ifEmpty {
                listOf(NovelBlock.Paragraph(AnnotatedString(emptyMessage)))
            }
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
