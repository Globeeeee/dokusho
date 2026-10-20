package eu.kanade.tachiyomi.data.tts

import eu.kanade.tachiyomi.ui.reader.viewer.novel.NovelBlock
import eu.kanade.tachiyomi.ui.reader.viewer.novel.NovelHtml
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class SpeakableBlocksTest {

    @Test
    fun `an image between paragraphs becomes its own block`() {
        val blocks = NovelHtml.parse(
            """<p>First.</p><img src="https://example.org/a.png" alt="A map"><p>Second.</p>""",
        )
        blocks.size shouldBe 3
        blocks[1].shouldBeInstanceOf<NovelBlock.Image>().url shouldBe "https://example.org/a.png"
    }

    @Test
    fun `lazy and inline-wrapped images are kept`() {
        val blocks = NovelHtml.parse(
            """<p>Text <em><img src="https://example.org/blank.gif" data-src="https://example.org/b.png"></em></p>""" +
                """<em><img src="https://example.org/c.png"></em>""",
        )
        blocks.filterIsInstance<NovelBlock.Image>().map { it.url } shouldBe
            listOf("https://example.org/b.png", "https://example.org/c.png")
    }

    @Test
    fun `the voice skips images and keeps the reader's indexes`() {
        val blocks = NovelHtml.parse(
            """<p>First.</p><img src="https://example.org/a.png" alt="A map"><p>Second.</p>""",
        )
        val speakable = speakableBlocks(blocks)
        speakable.map { it.first } shouldBe listOf(0, 2)
        speakable.map { it.second.spoken } shouldBe listOf("First.", "Second.")
    }
}
