package eu.kanade.tachiyomi.data.export

import eu.kanade.tachiyomi.ui.reader.viewer.novel.NovelBlock
import java.io.Closeable

/** One output file being written, EPUB or PDF. */
internal interface ExportSink : Closeable {
    suspend fun add(position: Int, title: String, blocks: List<NovelBlock>, image: suspend (String) -> ExportImage?)

    fun finish()
}

internal class EpubSink(private val writer: EpubWriter, cover: ExportImage?) : ExportSink {

    // The same picture used twice in a book is stored once.
    private val images = mutableMapOf<String, String?>()

    init {
        cover?.let { writer.addCover(it.bytes, it.mediaType) }
    }

    override suspend fun add(
        position: Int,
        title: String,
        blocks: List<NovelBlock>,
        image: suspend (String) -> ExportImage?,
    ) {
        blocks.filterIsInstance<NovelBlock.Image>().forEach { block ->
            if (block.url !in images) {
                images[block.url] = image(block.url)?.let { writer.addImage(it.bytes, it.mediaType) }
            }
        }
        writer.addChapter(position, title, NovelXhtml.body(title, blocks) { images[it] })
    }

    override fun finish() = writer.finish()

    override fun close() = writer.close()
}

internal class PdfSink(private val writer: PdfWriter) : ExportSink {

    override suspend fun add(
        position: Int,
        title: String,
        blocks: List<NovelBlock>,
        image: suspend (String) -> ExportImage?,
    ) {
        writer.addChapter(title, blocks) { image(it)?.bytes }
    }

    override fun finish() = writer.finish()

    override fun close() = writer.close()
}
