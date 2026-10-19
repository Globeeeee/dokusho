package eu.kanade.tachiyomi.source.model

/**
 * The text of one chapter.
 *
 * @property html sanitized chapter body. Allowed tags: p, h1-h4, em, i, strong, b, br, hr,
 * blockquote, img (absolute src). The app sanitizes again before caching.
 * @property title the chapter heading as shown on the site, if any.
 * @property locked true when the user's account cannot read this chapter; [html] is then empty.
 */
class ChapterContent(
    val html: String,
    val title: String? = null,
    val locked: Boolean = false,
)
