package eu.kanade.tachiyomi.data.tts

import android.content.Context
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

/**
 * "Play sample" in the read-aloud settings: one sentence with the chosen engine, voice, speed and
 * pitch, so a voice can be judged before reading. Not used while a read-aloud session is open.
 */
object TtsSample {

    private val scope = MainScope()
    private var job: Job? = null

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    fun play(context: Context, preferences: ReaderPreferences) {
        val app = context.applicationContext
        job?.cancel()
        job = scope.launch {
            _playing.value = true
            val engine = TtsEngines.create(app, preferences, onFallback = {
                scope.launch { app.toast(MR.strings.tts_engine_fallback) }
            })
            try {
                val text = app.stringResource(MR.strings.tts_sample_text)
                val spoken = engine.init() && withContext(Dispatchers.IO) { engine.speak(text) }
                if (!spoken) app.toast(MR.strings.tts_engine_unavailable)
            } finally {
                withContext(NonCancellable) { engine.release() }
                _playing.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
