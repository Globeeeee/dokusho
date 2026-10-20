package eu.kanade.tachiyomi.data.tts

/**
 * A speech synthesizer the TTS playback service can drive. [AndroidTtsEngine] wraps the system
 * TextToSpeech. [SupertonicTtsEngine] runs the downloaded Supertonic voice on this device.
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

    fun isSupertonic(id: String) = id == SUPERTONIC

    /** Package of a specific system engine, or null for the device default. */
    fun systemPackage(id: String): String? = id.takeUnless { it.isBlank() || isSupertonic(it) }
}
