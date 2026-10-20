package eu.kanade.tachiyomi.data.export

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import eu.kanade.tachiyomi.ui.reader.viewer.novel.NovelBlock

/** Writes the reader's blocks back out as XHTML for EPUB: escaped text, self-closed void tags. */
object NovelXhtml {

    /**
     * The chapter body. [image] maps an image block's address to its path inside the book, or null to
     * leave the image out.
     */
    fun body(title: String, blocks: List<NovelBlock>, image: (String) -> String?): String = buildString {
        append("<h1>").append(escape(title)).append("</h1>\n")
        for (block in blocks) {
            when (block) {
                is NovelBlock.Paragraph -> append("<p>").append(inline(block.text)).append("</p>\n")
                is NovelBlock.Heading -> {
                    val level = (block.level + 1).coerceIn(2, 6)
                    append("<h$level>").append(inline(block.text)).append("</h$level>\n")
                }
                is NovelBlock.Quote -> append(
                    "<blockquote><p>",
                ).append(inline(block.text)).append("</p></blockquote>\n")
                NovelBlock.Divider -> append("<hr/>\n")
                is NovelBlock.Image -> image(block.url)?.let {
                    append("<div class=\"image\"><img src=\"").append(escape(it)).append("\" alt=\"\"/></div>\n")
                }
            }
        }
    }

    /** Italic and bold spans become em and strong; line breaks become br. */
    fun inline(text: AnnotatedString): String {
        val raw = text.text
        if (raw.isEmpty()) return ""
        val italic = BooleanArray(raw.length)
        val bold = BooleanArray(raw.length)
        text.spanStyles.forEach { range ->
            val start = range.start.coerceIn(0, raw.length)
            val end = range.end.coerceIn(0, raw.length)
            if (range.item.fontStyle == FontStyle.Italic) italic.fill(true, start, end)
            if ((range.item.fontWeight?.weight ?: 0) >= FontWeight.Bold.weight) bold.fill(true, start, end)
        }
        return buildString {
            var openItalic = false
            var openBold = false
            fun close() {
                if (openItalic) append("</em>")
                if (openBold) append("</strong>")
                openItalic = false
                openBold = false
            }
            raw.forEachIndexed { i, c ->
                if (italic[i] != openItalic || bold[i] != openBold) {
                    close()
                    if (bold[i]) append("<strong>")
                    if (italic[i]) append("<em>")
                    openBold = bold[i]
                    openItalic = italic[i]
                }
                if (c == '\n') append("<br/>") else appendEscaped(c)
            }
            close()
        }
    }

    fun escape(text: String): String = buildString(text.length) { text.forEach { appendEscaped(it) } }

    private fun StringBuilder.appendEscaped(c: Char) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            // Characters XML 1.0 forbids would make the whole file unreadable.
            else -> if (isXmlChar(c)) append(c)
        }
    }

    private fun isXmlChar(c: Char): Boolean = when {
        c == '\t' || c == '\n' || c == '\r' -> true
        c < ' ' -> false
        else -> c.code != NON_CHAR_FFFE && c.code != NON_CHAR_FFFF
    }

    private const val NON_CHAR_FFFE = 0xFFFE
    private const val NON_CHAR_FFFF = 0xFFFF
}
