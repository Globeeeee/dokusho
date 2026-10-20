package eu.kanade.tachiyomi.ui.reader.viewer.novel

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Safelist

/**
 * One unit of a novel chapter. Blocks are what the reader shows, what progress counts and what
 * text-to-speech and export walk through.
 */
sealed interface NovelBlock {
    val text: AnnotatedString

    data class Paragraph(override val text: AnnotatedString) : NovelBlock
    data class Heading(override val text: AnnotatedString, val level: Int) : NovelBlock
    data class Quote(override val text: AnnotatedString) : NovelBlock
    data object Divider : NovelBlock {
        override val text = AnnotatedString("")
    }
    data class Image(val url: String) : NovelBlock {
        override val text = AnnotatedString("")
    }
}

object NovelHtml {

    // Second pass after the extension's own cleanup; nothing outside this list reaches the reader.
    private val safelist = Safelist()
        .addTags("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "hr", "br")
        .addTags("em", "i", "strong", "b", "u")
        .addTags("img")
        .addAttributes("img", "src")
        .addProtocols("img", "src", "http", "https", "content", "file")

    private val blockTags = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "hr")

    /** Points images saved next to a downloaded chapter at their files; [resolve] maps a file name to a URI. */
    fun resolveLocalImages(html: String, resolve: (String) -> String?): String {
        if (!html.contains("<img")) return html
        val document = Jsoup.parseBodyFragment(html)
        document.select("img[src]").forEach { image ->
            val src = image.attr("src")
            if (!src.contains("://")) resolve(src)?.let { image.attr("src", it) }
        }
        return document.body().html()
    }

    fun parse(html: String): List<NovelBlock> {
        val body = Jsoup.parseBodyFragment(Jsoup.clean(withLazyImages(html), safelist)).body()
        val blocks = mutableListOf<NovelBlock>()
        collect(body, blocks)
        return blocks
    }

    private fun collect(parent: Element, out: MutableList<NovelBlock>) {
        val loose = mutableListOf<Node>()
        fun flushLoose() {
            if (loose.isEmpty()) return
            inline(loose).takeIf { it.isNotBlank() }?.let { out += NovelBlock.Paragraph(it) }
            // Images wrapped in inline tags (<em><img></em>) still get their own blocks.
            loose.filterIsInstance<Element>().flatMap { it.select("img[src]") }
                .forEach { out += NovelBlock.Image(it.attr("src")) }
            loose.clear()
        }
        for (node in parent.childNodes()) {
            val element = node as? Element
            if (element != null && element.tagName() == "img") {
                flushLoose()
                element.attr("src").takeIf { it.isNotBlank() }?.let { out += NovelBlock.Image(it) }
                continue
            }
            if (element == null || element.tagName() !in blockTags) {
                loose += node
                continue
            }
            flushLoose()
            when (val tag = element.tagName()) {
                "hr" -> out += NovelBlock.Divider
                "div" -> collect(element, out)
                else -> {
                    val text = inline(element.childNodes())
                    if (text.isNotBlank()) {
                        out += when {
                            tag == "blockquote" -> NovelBlock.Quote(text)
                            tag.startsWith("h") -> NovelBlock.Heading(text, tag[1].digitToInt())
                            else -> NovelBlock.Paragraph(text)
                        }
                    }
                    // Images inside a paragraph become their own blocks after its text.
                    element.select("img[src]").forEach { out += NovelBlock.Image(it.attr("src")) }
                }
            }
        }
        flushLoose()
    }

    private fun inline(nodes: List<Node>): AnnotatedString = buildAnnotatedString {
        fun walk(node: Node) {
            when (node) {
                is TextNode -> append(node.text().replace(WHITESPACE, " "))
                is Element -> when (node.tagName()) {
                    "br" -> append('\n')
                    "em", "i" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        node.childNodes().forEach(::walk)
                    }
                    "strong", "b" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        node.childNodes().forEach(::walk)
                    }
                    else -> node.childNodes().forEach(::walk)
                }
            }
        }
        nodes.forEach(::walk)
    }.trimmed()

    private fun AnnotatedString.trimmed(): AnnotatedString {
        val start = text.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: return AnnotatedString("")
        val end = text.indexOfLast { !it.isWhitespace() } + 1
        return subSequence(start, end)
    }

    private fun AnnotatedString.isNotBlank() = text.isNotBlank()

    /** Sites that lazy-load keep the real image in a data attribute and a placeholder in src. */
    private fun withLazyImages(html: String): String {
        if (!html.contains("<img") || LAZY_SRC.none { html.contains(it) }) return html
        val document = Jsoup.parseBodyFragment(html)
        document.select("img").forEach { image ->
            LAZY_SRC.firstNotNullOfOrNull { name -> image.absUrl(name).ifBlank { image.attr(name) }.ifBlank { null } }
                ?.let { image.attr("src", it) }
        }
        return document.body().html()
    }

    private val LAZY_SRC = listOf("data-src", "data-original", "data-lazy-src")

    private val WHITESPACE = Regex("\\s+")
}
