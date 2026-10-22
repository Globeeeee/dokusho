package tachiyomi.source.local.io

import android.net.Uri
import com.hippo.unifile.UniFile
import mihon.core.archive.EpubReader
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import tachiyomi.core.common.storage.extension
import java.io.File
import java.security.MessageDigest

/**
 * Text chapters in the local folder, for the novel reader: TXT and HTML files as one chapter each, and
 * a text EPUB as one chapter per book section. Image EPUBs, archives and folders stay image chapters.
 */
internal object LocalText {

    private val TEXT_EXTENSIONS = setOf("txt", "html", "htm", "xhtml")

    /** Marks a chapter that is one section of a text EPUB: `book.epub#3`. */
    const val SECTION_MARK = '#'

    /** Below this many letters across the whole book, an EPUB is pictures (a comic), not text. */
    private const val MIN_BOOK_TEXT = 500

    fun isTextFile(file: UniFile) = file.extension?.lowercase() in TEXT_EXTENSIONS

    /** From the chapter's url alone, so it can be asked on the main thread. */
    fun isTextChapter(url: String): Boolean {
        val name = url.substringAfterLast('/')
        return SECTION_MARK in name || name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS
    }

    /** The file of a chapter url, without the section mark. */
    fun fileName(chapterName: String) = chapterName.substringBefore(SECTION_MARK)

    fun section(url: String): Int? = url.substringAfterLast('/').substringAfter(SECTION_MARK, "").toIntOrNull()

    /** A TXT or HTML file as chapter HTML. */
    fun fileHtml(file: UniFile): String {
        val text = file.openInputStream().use { it.bufferedReader().readText() }
        return if (file.extension.equals("txt", true)) plainToHtml(text) else Jsoup.parse(text).body().html()
    }

    /** Paragraphs are split on blank lines, or on every line when the file has no blank lines. */
    fun plainToHtml(text: String): String {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        val paragraphs = if ("\n\n" in normalized) normalized.split(Regex("\n\\s*\n")) else normalized.lines()
        return paragraphs
            .map { it.trim().replace(Regex("\\s*\n\\s*"), " ") }
            .filter { it.isNotEmpty() }
            .joinToString("") { "<p>${Entities.escape(it)}</p>" }
    }

    class Section(val index: Int, val title: String?)

    /** The text sections of a text EPUB in reading order, or empty when the EPUB is a picture book. */
    fun sections(epub: EpubReader): List<Section> {
        val pages = spine(epub)
        var letters = 0
        val sections = pages.mapIndexedNotNull { index, path ->
            val page = document(epub, path) ?: return@mapIndexedNotNull null
            val text = page.body().text()
            if (text.isBlank()) return@mapIndexedNotNull null
            letters += text.count { it.isLetterOrDigit() }
            val title = page.body().selectFirst("h1, h2, h3")?.text()?.takeIf { it.isNotBlank() }
                ?: page.title().takeIf { it.isNotBlank() }
            Section(index, title)
        }
        return if (letters >= MIN_BOOK_TEXT) sections else emptyList()
    }

    /**
     * One section of a text EPUB as chapter HTML. Its images are unpacked into [imageCache], a folder of
     * this book's own, and pointed at with file urls, so they show in the reader.
     */
    fun sectionHtml(epub: EpubReader, index: Int, imageCache: File): String {
        val path = spine(epub).getOrNull(index) ?: return ""
        val body = document(epub, path)?.body() ?: return ""
        val folder = path.substringBeforeLast('/', "")
        body.select("img[src]").forEach { image ->
            val entry = resolve(folder, image.attr("src"))
            val target = File(imageCache, hash(entry) + "." + entry.substringAfterLast('.', "img"))
            if (!target.exists()) {
                imageCache.mkdirs()
                epub.getInputStream(entry)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            }
            if (target.exists()) image.attr("src", Uri.fromFile(target).toString()) else image.remove()
        }
        return body.html()
    }

    /** Zip paths of the spine's XHTML documents, in reading order. */
    private fun spine(epub: EpubReader): List<String> {
        val packageHref = epub.getPackageHref()
        val document = epub.getPackageDocument(packageHref)
        val folder = packageHref.substringBeforeLast('/', "")
        val items = document.select("manifest > item")
            .filter { it.attr("media-type") == "application/xhtml+xml" }
            .associateBy { it.attr("id") }
        return document.select("spine > itemref")
            .mapNotNull { items[it.attr("idref")]?.attr("href") }
            .map { resolve(folder, it) }
    }

    private fun document(epub: EpubReader, path: String): Document? =
        epub.getInputStream(path)?.use { Jsoup.parse(it, null, "") }

    /** [href] relative to [folder] inside the zip, with `..` and percent escapes resolved. */
    private fun resolve(folder: String, href: String): String {
        val parts = ArrayDeque<String>()
        if (folder.isNotEmpty()) folder.split('/').forEach(parts::addLast)
        Uri.decode(href.substringBefore('#')).split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
