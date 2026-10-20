package eu.kanade.tachiyomi.data.export

import eu.kanade.tachiyomi.ui.reader.viewer.novel.NovelHtml
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeBytes

class EpubWriterTest {

    private val metadata = EpubMetadata(
        identifier = "urn:dokusho:1:abc",
        title = "Tom & Jerry <Book>",
        author = "Someone",
        language = "en",
        modified = Instant.parse("2026-10-21T10:15:30.123Z"),
    )

    private fun book(): ByteArray {
        val out = ByteArrayOutputStream()
        EpubWriter(out, metadata).use { writer ->
            writer.addCover(byteArrayOf(1, 2, 3), "image/png")
            val image = writer.addImage(byteArrayOf(4, 5), "image/jpeg")
            val blocks = NovelHtml.parse(
                """<p>Fish &amp; <em>chips</em> &lt;3</p><img src="https://example.org/a.jpg"><hr><p>Line<br>two</p>""",
            )
            writer.addChapter(1, "Chapter 1: \"Start\"", NovelXhtml.body("Chapter 1: \"Start\"", blocks) { image })
            writer.addChapter(2, "Chapter 2", NovelXhtml.body("Chapter 2", NovelHtml.parse("<p>End.</p>")) { null })
            writer.finish()
        }
        return out.toByteArray()
    }

    private fun entries(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { result[it.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        return result
    }

    private fun parseXml(text: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(text.toByteArray()))

    @Test
    fun `mimetype is the first entry, stored, with no extra field`() {
        val bytes = book()
        String(bytes, 0, 4, Charsets.ISO_8859_1) shouldBe "PK\u0003\u0004"
        // Compression method 0, name length 8, extra length 0, then the name and the content.
        (bytes[8].toInt() or (bytes[9].toInt() shl 8)) shouldBe 0
        (bytes[26].toInt() or (bytes[27].toInt() shl 8)) shouldBe 8
        (bytes[28].toInt() or (bytes[29].toInt() shl 8)) shouldBe 0
        String(bytes, 30, 8, Charsets.US_ASCII) shouldBe "mimetype"
        String(bytes, 38, 20, Charsets.US_ASCII) shouldBe "application/epub+zip"

        val file = createTempFile(suffix = ".epub").apply { writeBytes(bytes) }.toFile()
        ZipFile(file).use { zip ->
            val first = zip.entries().nextElement()
            first.name shouldBe "mimetype"
            first.method shouldBe ZipEntry.STORED
        }
        file.delete()
    }

    @Test
    fun `package lists every file and reads chapters in order`() {
        val files = entries(book())
        files.keys.toList() shouldContainInOrder listOf("mimetype", "META-INF/container.xml")
        files["META-INF/container.xml"]!! shouldContain "full-path=\"OEBPS/content.opf\""
        listOf(
            "OEBPS/ch0001.xhtml",
            "OEBPS/ch0002.xhtml",
            "OEBPS/nav.xhtml",
            "OEBPS/toc.ncx",
            "OEBPS/style.css",
            "OEBPS/cover.xhtml",
            "OEBPS/images/cover.png",
            "OEBPS/images/img0001.jpg",
        ).forEach { files.keys.contains(it) shouldBe true }

        val opf = files.getValue("OEBPS/content.opf")
        parseXml(opf)
        opf shouldContain "<dc:identifier id=\"book-id\">urn:dokusho:1:abc</dc:identifier>"
        opf shouldContain "<dc:title>Tom &amp; Jerry &lt;Book&gt;</dc:title>"
        opf shouldContain "<dc:language>en</dc:language>"
        opf shouldContain "<meta property=\"dcterms:modified\">2026-10-21T10:15:30Z</meta>"
        opf shouldContain "properties=\"nav\""
        opf shouldContain
            "<item id=\"cover-image\" href=\"images/cover.png\" media-type=\"image/png\" properties=\"cover-image\"/>"
        opf shouldContain "<item id=\"img0001\" href=\"images/img0001.jpg\" media-type=\"image/jpeg\"/>"
        Regex("<itemref idref=\"([^\"]+)\"/>").findAll(opf).map { it.groupValues[1] }.toList() shouldBe
            listOf("cover", "ch0001", "ch0002")

        val nav = files.getValue("OEBPS/nav.xhtml")
        parseXml(nav)
        nav shouldContain "<a href=\"ch0001.xhtml\">Chapter 1: &quot;Start&quot;</a>"
        nav shouldContain "<a href=\"ch0002.xhtml\">Chapter 2</a>"
        parseXml(files.getValue("OEBPS/toc.ncx"))
    }

    @Test
    fun `chapter pages are valid XHTML with escaped text and self-closed void tags`() {
        val chapter = entries(book()).getValue("OEBPS/ch0001.xhtml")
        parseXml(chapter)
        chapter shouldContain "<p>Fish &amp; <em>chips</em> &lt;3</p>"
        chapter shouldContain "<img src=\"images/img0001.jpg\" alt=\"\"/>"
        chapter shouldContain "<hr/>"
        chapter shouldContain "<br/>"
        chapter shouldNotContain "<br>"
    }

    @Test
    fun `dropped images leave no tag and control characters are removed`() {
        val body = NovelXhtml.body("T", NovelHtml.parse("""<p>a</p><img src="https://example.org/x.png">""")) { null }
        body shouldNotContain "<img"
        NovelXhtml.escape("a\u0001b'c\"") shouldBe "ab&#39;c&quot;"
    }

    @Test
    fun `bold and italic nest into strong and em`() {
        val blocks = NovelHtml.parse("<p><strong>Bold <em>both</em></strong> plain</p>")
        val text = (blocks.single() as eu.kanade.tachiyomi.ui.reader.viewer.novel.NovelBlock.Paragraph).text
        NovelXhtml.inline(text) shouldBe "<strong>Bold </strong><strong><em>both</em></strong> plain"
    }
}
