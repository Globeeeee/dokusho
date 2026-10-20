package eu.kanade.tachiyomi.data.tts

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SupertonicChunksTest {

    @Test
    fun `an ellipsis does not end the sentence`() {
        val text = "Wait... I think he is lying to everyone in this room right now, and I can prove it."
        supertonicChunks(text) shouldContainExactly listOf(text)
    }

    @Test
    fun `short sentences join their neighbour`() {
        val text = "Yes. He nodded. Then the long march through the silent forest began again at dawn."
        supertonicChunks(text) shouldContainExactly listOf(text)
    }

    @Test
    fun `long sentences stay separate`() {
        val first = "The storm rolled over the mountains for three whole days without a pause."
        val second = "When it finally cleared, the valley below had changed beyond all recognition."
        supertonicChunks("$first $second") shouldContainExactly listOf(first, second)
    }

    @Test
    fun `every clip is an exact slice of the paragraph`() {
        val text = "\"Run!\"  she shouted.\nThey ran... and ran. " +
            "Nobody looked back, not even once, as the gates closed behind them."
        supertonicChunks(text).forEach { (it in text) shouldBe true }
    }

    @Test
    fun `a wordless paragraph has no clips once preprocessed`() {
        supertonicChunks("...") shouldContainExactly listOf("...")
    }
}
