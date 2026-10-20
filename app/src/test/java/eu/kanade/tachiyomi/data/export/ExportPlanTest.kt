package eu.kanade.tachiyomi.data.export

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class ExportPlanTest {

    @Test
    fun `all, downloaded and from-to pick reading positions`() {
        ExportPlan.select(5, ExportRange.ALL) shouldBe listOf(1, 2, 3, 4, 5)
        ExportPlan.select(5, ExportRange.DOWNLOADED) { it % 2 == 0 } shouldBe listOf(2, 4)
        ExportPlan.select(10, ExportRange.FROM_TO, from = 3, to = 6) shouldBe listOf(3, 4, 5, 6)
    }

    @Test
    fun `from-to is clamped to the novel and accepts a reversed range`() {
        ExportPlan.select(10, ExportRange.FROM_TO, from = 8, to = 400) shouldBe listOf(8, 9, 10)
        ExportPlan.select(10, ExportRange.FROM_TO, from = 4, to = 2) shouldBe listOf(2, 3, 4)
        ExportPlan.select(0, ExportRange.ALL) shouldBe emptyList()
    }

    @Test
    fun `300 chapters split by 100 make three parts and 0 keeps one`() {
        val positions = ExportPlan.select(300, ExportRange.ALL)
        ExportPlan.split(positions, 100).map { it.first to it.last } shouldBe
            listOf(1 to 100, 101 to 200, 201 to 300)
        ExportPlan.split(positions, 0).single().let { (it.first to it.last) shouldBe (1 to 300) }
        ExportPlan.split(listOf(1, 2, 3, 4, 5), 2).map { it.positions } shouldBe
            listOf(listOf(1, 2), listOf(3, 4), listOf(5))
        ExportPlan.split(emptyList(), 100) shouldBe emptyList()
    }

    @Test
    fun `file names follow Title - chNNNN-NNNN`() {
        val part = ExportPart(1, 100, (1..100).toList())
        ExportPlan.fileName("Mother of Learning", part, "epub") shouldBe "Mother of Learning - ch0001-0100.epub"
        ExportPlan.fileName("A", ExportPart(101, 200, emptyList()), "pdf") shouldBe "A - ch0101-0200.pdf"
    }

    @Test
    fun `titles lose slashes, reserved characters and long dashes`() {
        val emDash = Char(0x2014)
        val enDash = Char(0x2013)
        val name = ExportPlan.safeTitle("Re:Zero / Book 1 $emDash Start${enDash}Again?  ")
        name shouldBe "Re_Zero _ Book 1 - Start-Again_"
        name shouldNotContain "/"
        name shouldNotContain emDash.toString()
        ExportPlan.safeTitle("   ") shouldBe "(invalid)"
        ExportPlan.safeTitle("x".repeat(400)).toByteArray().size shouldBe 200
    }
}
