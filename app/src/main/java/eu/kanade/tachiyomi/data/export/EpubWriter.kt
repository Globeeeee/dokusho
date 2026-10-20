package eu.kanade.tachiyomi.data.export

import java.io.Closeable
import java.io.OutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class EpubMetadata(
    val identifier: String,
    val title: String,
    val author: String?,
    val language: String,
    val modified: Instant,
)

/**
 * Streams an EPUB 3 book: chapters and images go straight into the archive, the package files are
 * written by [finish].
 *
 * ponytail: java.util.zip instead of core:archive's ZipWriter. libarchive adds extra fields and a data
 * descriptor to every entry, which moves the `mimetype` bytes off the fixed offset strict readers check.
 */
class EpubWriter(output: OutputStream, private val metadata: EpubMetadata) : Closeable {

    private class Item(val id: String, val href: String, val mediaType: String, val properties: String? = null)

    private class TocEntry(val href: String, val title: String)

    private val zip = ZipOutputStream(output.buffered())
    private val manifest = mutableListOf<Item>()
    private val spine = mutableListOf<String>()
    private val toc = mutableListOf<TocEntry>()
    private var imageCount = 0
    private var finished = false

    init {
        writeStored(MIMETYPE_PATH, MIMETYPE.toByteArray(Charsets.US_ASCII))
        write("META-INF/container.xml", CONTAINER_XML)
    }

    /** The cover image, shown first. Call before the first chapter. */
    fun addCover(bytes: ByteArray, mediaType: String) {
        val href = "images/cover.${extensionOf(mediaType)}"
        write("$OEBPS/$href", bytes)
        manifest += Item("cover-image", href, mediaType, properties = "cover-image")
        val page = xhtmlPage(
            metadata.title,
            "<div class=\"cover\"><img src=\"$href\" alt=\"${NovelXhtml.escape(metadata.title)}\"/></div>",
        )
        write("$OEBPS/cover.xhtml", page)
        manifest += Item("cover", "cover.xhtml", XHTML)
        spine += "cover"
    }

    /** Stores an image and returns the path chapters use to point at it. */
    fun addImage(bytes: ByteArray, mediaType: String): String {
        imageCount++
        val id = "img%04d".format(imageCount)
        val href = "images/$id.${extensionOf(mediaType)}"
        write("$OEBPS/$href", bytes)
        manifest += Item(id, href, mediaType)
        return href
    }

    /** One chapter page, `chNNNN.xhtml` by its [position] in the novel. [body] comes from [NovelXhtml.body]. */
    fun addChapter(position: Int, title: String, body: String) {
        val id = "ch%04d".format(position)
        val href = "$id.xhtml"
        write("$OEBPS/$href", xhtmlPage(title, "<section epub:type=\"chapter\">\n$body</section>"))
        manifest += Item(id, href, XHTML)
        spine += id
        toc += TocEntry(href, title)
    }

    val chapterCount: Int
        get() = toc.size

    /** Writes the stylesheet, navigation and package document and closes the archive. */
    fun finish() {
        if (finished) return
        finished = true
        write("$OEBPS/style.css", STYLE_CSS)
        write("$OEBPS/nav.xhtml", navXhtml())
        write("$OEBPS/toc.ncx", tocNcx())
        write("$OEBPS/content.opf", contentOpf())
        zip.close()
    }

    override fun close() {
        if (!finished) {
            finished = true
            zip.close()
        }
    }

    internal fun contentOpf(): String = buildString {
        val modified = metadata.modified.truncatedTo(ChronoUnit.SECONDS).toString()
        append("""<?xml version="1.0" encoding="utf-8"?>""").append('\n')
        append("""<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" """)
        append("""xml:lang="${esc(metadata.language)}">""").append('\n')
        append("""<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""").append('\n')
        append("""<dc:identifier id="book-id">${esc(metadata.identifier)}</dc:identifier>""").append('\n')
        append("""<dc:title>${esc(metadata.title)}</dc:title>""").append('\n')
        metadata.author?.takeIf { it.isNotBlank() }?.let {
            append("""<dc:creator>${esc(it)}</dc:creator>""").append('\n')
        }
        append("""<dc:language>${esc(metadata.language)}</dc:language>""").append('\n')
        append("""<meta property="dcterms:modified">$modified</meta>""").append('\n')
        if (manifest.any {
                it.id == "cover-image"
            }
        ) {
            append("""<meta name="cover" content="cover-image"/>""").append('\n')
        }
        append("</metadata>\n<manifest>\n")
        append("""<item id="nav" href="nav.xhtml" media-type="$XHTML" properties="nav"/>""").append('\n')
        append("""<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""").append('\n')
        append("""<item id="css" href="style.css" media-type="text/css"/>""").append('\n')
        manifest.forEach { item ->
            append("""<item id="${item.id}" href="${esc(item.href)}" media-type="${item.mediaType}"""")
            item.properties?.let { append(""" properties="$it"""") }
            append("/>\n")
        }
        append("</manifest>\n<spine toc=\"ncx\">\n")
        spine.forEach { append("""<itemref idref="$it"/>""").append('\n') }
        append("</spine>\n</package>\n")
    }

    internal fun navXhtml(): String {
        val items = toc.joinToString("\n") { """<li><a href="${esc(it.href)}">${esc(it.title)}</a></li>""" }
        return xhtmlPage(
            metadata.title,
            "<nav epub:type=\"toc\" id=\"toc\">\n<h1>${esc(metadata.title)}</h1>\n<ol>\n$items\n</ol>\n</nav>",
        )
    }

    private fun tocNcx(): String = buildString {
        append("""<?xml version="1.0" encoding="utf-8"?>""").append('\n')
        append("""<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">""").append('\n')
        append("""<head><meta name="dtb:uid" content="${esc(metadata.identifier)}"/></head>""").append('\n')
        append("""<docTitle><text>${esc(metadata.title)}</text></docTitle>""").append('\n')
        append("<navMap>\n")
        toc.forEachIndexed { index, entry ->
            append("""<navPoint id="np${index + 1}" playOrder="${index + 1}">""")
            append("""<navLabel><text>${esc(entry.title)}</text></navLabel>""")
            append("""<content src="${esc(entry.href)}"/></navPoint>""").append('\n')
        }
        append("</navMap>\n</ncx>\n")
    }

    private fun xhtmlPage(title: String, body: String): String = buildString {
        val lang = esc(metadata.language)
        append("""<?xml version="1.0" encoding="utf-8"?>""").append('\n')
        append("<!DOCTYPE html>\n")
        append("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" """)
        append("""xml:lang="$lang" lang="$lang">""").append('\n')
        append("""<head><meta charset="utf-8"/><title>${esc(title)}</title>""")
        append("""<link rel="stylesheet" type="text/css" href="style.css"/></head>""").append('\n')
        append("<body>\n").append(body).append("\n</body>\n</html>\n")
    }

    private fun write(path: String, text: String) = write(path, text.toByteArray(Charsets.UTF_8))

    private fun write(path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeStored(path: String, bytes: ByteArray) {
        val entry = ZipEntry(path).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun esc(text: String) = NovelXhtml.escape(text)

    companion object {
        const val MIMETYPE = "application/epub+zip"
        const val MIMETYPE_PATH = "mimetype"
        private const val OEBPS = "OEBPS"
        private const val XHTML = "application/xhtml+xml"

        /** Image types every EPUB 3 reader must show. */
        val CORE_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

        private fun extensionOf(mediaType: String) = when (mediaType) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "jpg"
        }

        private val CONTAINER_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
            <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
            </rootfiles>
            </container>
        """.trimIndent() + "\n"

        private val STYLE_CSS = """
            body { margin: 0 5%; line-height: 1.5; }
            h1 { font-size: 1.5em; margin: 1.5em 0 1em; text-align: center; }
            h2, h3, h4, h5, h6 { margin: 1.2em 0 0.6em; }
            p { margin: 0 0 0.8em; text-indent: 0; }
            blockquote { margin: 1em 1.5em; font-style: italic; }
            hr { border: none; border-top: 1px solid #888; width: 30%; margin: 1.5em auto; }
            div.image { text-align: center; margin: 1em 0; }
            div.image img { max-width: 100%; height: auto; }
            div.cover { text-align: center; }
            div.cover img { max-width: 100%; max-height: 100vh; }
        """.trimIndent() + "\n"
    }
}
