package eu.kanade.tachiyomi.ui.reader.viewer.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VoiceLabelTest {

    @Test
    fun `numbered online voice is split into language, number and badge`() {
        assertEquals(
            VoiceLabel("English (India)", 4, online = true, hd = false),
            VoiceLabel.of("English (India) 4 · online"),
        )
    }

    @Test
    fun `single hd voice keeps its language`() {
        assertEquals(
            VoiceLabel("Deutsch (Deutschland)", null, online = false, hd = true),
            VoiceLabel.of("Deutsch (Deutschland) · hd"),
        )
    }

    @Test
    fun `plain label`() {
        assertEquals(
            VoiceLabel("English (United States)", 2, online = false, hd = false),
            VoiceLabel.of("English (United States) 2"),
        )
    }
}
