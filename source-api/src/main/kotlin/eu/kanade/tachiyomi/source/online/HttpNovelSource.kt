package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.model.ChapterContent
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Request
import okhttp3.Response

/**
 * [HttpSource] for websites that publish chapters as text.
 */
abstract class HttpNovelSource :
    HttpSource(),
    NovelSource {

    override suspend fun getChapterContent(chapter: SChapter): ChapterContent =
        chapterContentParse(client.newCall(chapterContentRequest(chapter)).awaitSuccess())

    /**
     * Returns the request for a chapter's text. Override when the text comes from an API.
     */
    protected open fun chapterContentRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    /**
     * Parses the chapter text out of [response].
     */
    protected abstract fun chapterContentParse(response: Response): ChapterContent

    /**
     * Novel chapters have no page images.
     */
    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
}
