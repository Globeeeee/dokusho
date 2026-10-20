package eu.kanade.tachiyomi.data.tts

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SupertonicPlaybackTest {

    private val text = "Hello there. I walked home."

    /** Both sentences queued back to back in one track, as the engine writes them. */
    private fun timeline() = ClipTimeline().apply {
        add(start = 0, frames = 1000, chunk = "Hello there.", offset = 0)
        add(start = 1000, frames = 1400, chunk = "I walked home.", offset = 13)
    }

    private fun ClipTimeline.spoken(head: Long) = wordAt(head)?.let { text.substring(it.first, it.last + 1) }

    @Test
    fun `follows the head across sentences that are already queued`() {
        val timeline = timeline()
        timeline.spoken(0) shouldBe "Hello"
        timeline.spoken(999) shouldBe "there."
        timeline.spoken(1000) shouldBe "I"
        timeline.spoken(2399) shouldBe "home."
    }

    @Test
    fun `reports a word only when it changes`() {
        val timeline = timeline()
        timeline.spoken(0) shouldBe "Hello"
        timeline.spoken(10) shouldBe null
    }

    @Test
    fun `keeps the last word through the silence after the paragraph`() {
        val timeline = timeline()
        timeline.spoken(2399) shouldBe "home."
        timeline.spoken(2600) shouldBe null
        timeline.end shouldBe 2400L
    }

    @Test
    fun `a head that jumped past a whole sentence lands in the next one`() {
        timeline().spoken(1500) shouldBe "walked"
    }

    @Test
    fun `nothing plays before the first sentence starts`() {
        val timeline = ClipTimeline().apply { add(start = 500, frames = 1000, chunk = "Hello there.", offset = 0) }
        timeline.spoken(100) shouldBe null
        timeline.spoken(500) shouldBe "Hello"
    }

    @Test
    fun `a cleared timeline is empty`() {
        val timeline = timeline()
        timeline.clear()
        timeline.wordAt(0) shouldBe null
        timeline.end shouldBe 0L
    }

    @Test
    fun `a head that keeps moving never stalls`() {
        val watch = StallWatch(limitMs = 2500)
        watch.stalled(head = 0, nowMs = 0) shouldBe false
        watch.stalled(head = 100, nowMs = 2000) shouldBe false
        watch.stalled(head = 200, nowMs = 4000) shouldBe false
    }

    @Test
    fun `a head stuck past the limit stalls`() {
        val watch = StallWatch(limitMs = 2500)
        watch.stalled(head = 0, nowMs = 0) shouldBe false
        watch.stalled(head = 0, nowMs = 2499) shouldBe false
        watch.stalled(head = 0, nowMs = 2500) shouldBe true
        watch.stalled(head = 10, nowMs = 2600) shouldBe false
        watch.stalled(head = 10, nowMs = 5100) shouldBe true
    }
}
