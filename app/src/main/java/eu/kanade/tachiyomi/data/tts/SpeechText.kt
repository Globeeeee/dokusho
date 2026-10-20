package eu.kanade.tachiyomi.data.tts

import eu.kanade.tachiyomi.ui.reader.viewer.novel.NovelBlock

/**
 * A paragraph as the voice should hear it: soft hyphens and zero-width characters dropped, and a
 * word that the source broke across a line ("des\ntination", "des-\ntination") joined again.
 * [displayStart] and [displayEnd] map ranges in [spoken] back onto the displayed paragraph.
 */
internal class SpeechText private constructor(
    /** The paragraph as the reader shows it. */
    val display: String,
    val spoken: String,
    private val toDisplay: IntArray,
) {

    fun displayStart(spokenIndex: Int): Int =
        if (toDisplay.isEmpty()) 0 else toDisplay[spokenIndex.coerceIn(0, toDisplay.lastIndex)]

    /** Display index just after the character before the exclusive [spokenEnd]. */
    fun displayEnd(spokenEnd: Int): Int =
        if (toDisplay.isEmpty()) 0 else toDisplay[(spokenEnd - 1).coerceIn(0, toDisplay.lastIndex)] + 1

    companion object {
        private val INVISIBLE = charArrayOf('­', '​', '‌', '‍', '⁠', '﻿')

        fun of(display: String): SpeechText {
            val out = StringBuilder(display.length)
            val map = IntArray(display.length)
            for ((index, char) in display.withIndex()) {
                if (char in INVISIBLE) continue
                if (char == '\n') {
                    val next = display.drop(index + 1).firstOrNull { it !in INVISIBLE }
                    val last = out.lastOrNull()
                    val hyphen = last == '-' && out.getOrNull(out.length - 2)?.isLetter() == true
                    // Only a break tight on both sides splits a word; "the \nsea" stays two words.
                    if (next != null && next.isLowerCase() && (last?.isLetter() == true || hyphen)) {
                        if (hyphen) out.setLength(out.length - 1)
                        continue
                    }
                    map[out.length] = index
                    out.append(' ')
                    continue
                }
                map[out.length] = index
                out.append(char)
            }
            return SpeechText(display, out.toString(), map.copyOf(out.length))
        }
    }
}

/**
 * The blocks the voice reads, as (block index, speech). Images and dividers have nothing to say and
 * are skipped; the indexes stay those of the reader's paragraph list.
 */
internal fun speakableBlocks(blocks: List<NovelBlock>): List<Pair<Int, SpeechText>> =
    blocks.mapIndexedNotNull { index, block ->
        when (block) {
            is NovelBlock.Paragraph, is NovelBlock.Heading, is NovelBlock.Quote ->
                SpeechText.of(block.text.text).takeIf { it.spoken.isNotBlank() }?.let { index to it }
            else -> null
        }
    }
