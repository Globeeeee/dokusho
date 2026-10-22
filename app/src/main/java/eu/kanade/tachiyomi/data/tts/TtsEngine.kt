package eu.kanade.tachiyomi.data.tts

import android.content.Context
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences

/**
 * A speech synthesizer the TTS playback service can drive. [AndroidTtsEngine] wraps the system
 * TextToSpeech. [ClipTtsEngine] plays sentence audio from Supertonic, or from a system engine when
 * "Save spoken audio" is on.
 */
interface TtsEngine {

    /** Prepares the engine. Returns false when no engine is available on the device. */
    suspend fun init(): Boolean

    /**
     * Speaks [text] and suspends until it has been fully spoken. [onRange] reports the word of
     * [text] currently being spoken, so the reader can highlight it. Returns false when the
     * engine failed; a cancelled coroutine or a call to [stop] aborts the utterance without an error.
     */
    suspend fun speak(text: String, onRange: (start: Int, end: Int) -> Unit = { _, _ -> }): Boolean

    /** Speech rate, 1.0 is normal. Applies from the next utterance. */
    fun setSpeed(speed: Float)

    /** Voice pitch, 1.0 is normal. Applies from the next utterance. */
    fun setPitch(pitch: Float)

    /**
     * Selects a voice by the engine's own name, or the engine default when [voiceName] is blank.
     * Applies from the next utterance.
     */
    fun setVoice(voiceName: String?)

    /**
     * Lowers the voice while another app plays a short sound (a notification), and restores it, without
     * interrupting the utterance. Engines that can't change volume mid-utterance keep speaking as they are.
     */
    fun duck(ducked: Boolean) {}

    /** Stops the current utterance; [speak] returns as aborted, not failed. */
    fun stop()

    /** Releases the engine. The instance cannot be used afterwards. */
    fun release()
}

/** Ids stored in the read-aloud engine preference. */
object TtsEngines {
    /** The device's default text-to-speech engine. */
    const val SYSTEM = ""

    /** On-device neural voice. Used once its model files are on disk. */
    const val SUPERTONIC = "supertonic"

    /**
     * Engines that only speak behind a subscription. Hidden from the engine list; one chosen
     * earlier reads with the device default instead.
     */
    private val PAID = setOf(
        // eReader Prestigio: its cloud speech service needs a paid subscription in that app.
        "com.prestigio.ereader",
    )

    fun isSupertonic(id: String) = id == SUPERTONIC

    fun isPaid(enginePackage: String) = enginePackage in PAID

    /** Package of a specific system engine, or null for the device default. */
    fun systemPackage(id: String): String? = id.takeUnless { it.isBlank() || isSupertonic(it) || isPaid(it) }

    /**
     * The engine the read-aloud preferences ask for, configured with their voice, speed and pitch.
     * [systemOnly] reads with the device default instead of Supertonic, for a language it can't read.
     */
    fun create(
        context: Context,
        preferences: ReaderPreferences,
        systemOnly: Boolean = false,
        onFallback: () -> Unit = {},
    ): TtsEngine {
        val id = preferences.ttsEngine.get()
        return when {
            isSupertonic(id) && !systemOnly -> ClipTtsEngine(SupertonicClipSource(context))
            isSupertonic(id) -> AndroidTtsEngine(context, null, onFallback)
            preferences.ttsSaveAudio.get() -> ClipTtsEngine(SystemClipSource(context, systemPackage(id), onFallback))
            else -> AndroidTtsEngine(context, systemPackage(id), onFallback)
        }.apply {
            setSpeed(preferences.ttsSpeed.get())
            setPitch(preferences.ttsPitch.get())
            setVoice(preferences.ttsVoice.get().ifBlank { null })
        }
    }
}
