package eu.kanade.tachiyomi.data.tts

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SpeechTextTest {

    @Test
    fun `joins a word broken by a line`() {
        SpeechText.of("the des\ntination").spoken shouldBe "the destination"
    }

    @Test
    fun `joins a word hyphenated at a line end`() {
        SpeechText.of("the des-\ntination").spoken shouldBe "the destination"
    }

    @Test
    fun `drops soft hyphens and zero-width characters`() {
        SpeechText.of("des­tination").spoken shouldBe "destination"
        SpeechText.of("des​tination").spoken shouldBe "destination"
        SpeechText.of("des﻿ti⁠nation").spoken shouldBe "destination"
    }

    @Test
    fun `keeps compounds and real line breaks`() {
        SpeechText.of("a well-known place").spoken shouldBe "a well-known place"
        SpeechText.of("He left.\nThen it rained.").spoken shouldBe "He left. Then it rained."
        SpeechText.of("the \nsea").spoken shouldBe "the  sea"
    }

    @Test
    fun `maps spoken ranges back onto the displayed text`() {
        val display = "go to des-\ntination now"
        val speech = SpeechText.of(display)
        val spoken = speech.spoken
        val start = spoken.indexOf("destination")
        val end = start + "destination".length
        display.substring(speech.displayStart(start), speech.displayEnd(end)) shouldBe "des-\ntination"
        val now = spoken.indexOf("now")
        display.substring(speech.displayStart(now), speech.displayEnd(now + 3)) shouldBe "now"
    }
}
