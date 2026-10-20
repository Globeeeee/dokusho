package eu.kanade.tachiyomi.data.tts

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SpokenWordsTest {

    @Test
    fun `steps through the words as the clip plays`() {
        val text = "I walked home, slowly."
        val words = SpokenWords(text)
        text.substring(words.start(words.at(0f)), words.end(words.at(0f))) shouldBe "I"
        text.substring(words.start(words.at(0.99f)), words.end(words.at(0.99f))) shouldBe "slowly."
        words.at(1f) shouldBe 3
    }

    @Test
    fun `an empty clip has no word`() {
        SpokenWords("   ").at(0.5f) shouldBe -1
    }
}
