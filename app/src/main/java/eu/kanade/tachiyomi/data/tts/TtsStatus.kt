package eu.kanade.tachiyomi.data.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the TTS service is reading right now; null when no session is active. */
data class TtsState(
    val mangaId: Long,
    val chapterId: Long,
    /** Paragraph being spoken, as an index into the chapter's blocks (= reader page index). */
    val blockIndex: Int,
    val playing: Boolean,
    /** Speech rate, 1.0 is normal. */
    val speed: Float,
    /** Epoch millis when the sleep timer pauses playback; null when no timer runs. */
    val sleepAtMillis: Long? = null,
    /**
     * Character range of the sentence being spoken, inside the current paragraph.
     * Both are -1 when nothing is highlighted yet.
     */
    val spokenStart: Int = -1,
    val spokenEnd: Int = -1,
    /** Character range of the word being spoken, inside the current paragraph; -1 when unknown. */
    val wordStart: Int = -1,
    val wordEnd: Int = -1,
)

/**
 * The TTS playback service publishes its state here and the reader observes it to highlight the
 * paragraph being read. Both live in the same process, so a shared flow is all that's needed.
 */
object TtsStatus {
    private val mutableState = MutableStateFlow<TtsState?>(null)
    val state: StateFlow<TtsState?> = mutableState.asStateFlow()

    internal fun update(state: TtsState?) {
        mutableState.value = state
    }
}
