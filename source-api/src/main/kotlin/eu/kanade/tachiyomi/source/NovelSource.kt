package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.ChapterContent
import eu.kanade.tachiyomi.source.model.SChapter

/**
 * A source whose chapters are text rather than page images.
 *
 * Implemented by [eu.kanade.tachiyomi.source.online.HttpSource] subclasses next to
 * [CatalogueSource]; it only adds the text call, so it does not redeclare the source members.
 *
 * The app asks for [getChapterContent] instead of the page list when it reads, downloads,
 * exports or speaks a chapter from this source.
 */
interface NovelSource {

    /**
     * Get the text of a chapter.
     *
     * @param chapter the chapter, as returned by the chapter list.
     * @return the chapter content. A chapter the user's account cannot read must come back
     * with [ChapterContent.locked] set and empty [ChapterContent.html], never fetched from
     * somewhere else.
     */
    suspend fun getChapterContent(chapter: SChapter): ChapterContent
}
