package eu.kanade.tachiyomi.ui.reader.viewer.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.tts.SupertonicDownload
import eu.kanade.tachiyomi.data.tts.SupertonicDownloadState
import eu.kanade.tachiyomi.data.tts.SupertonicModel
import eu.kanade.tachiyomi.data.tts.SupertonicVoices
import eu.kanade.tachiyomi.data.tts.TtsCatalog
import eu.kanade.tachiyomi.data.tts.TtsEngines
import eu.kanade.tachiyomi.data.tts.TtsPlaybackService
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.RadioItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TtsSettingsSheet(preferences: ReaderPreferences, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(MR.strings.tts_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = SettingsItemsPaddings.Horizontal),
            )
            TtsSettingsContent(preferences)
        }
    }
}

/** Engine, voice, speed and pitch. Shared by the listen bar and the novel text settings. */
@Composable
internal fun TtsSettingsContent(preferences: ReaderPreferences) {
    val context = LocalContext.current
    val engine by preferences.ttsEngine.collectAsState()
    val voice by preferences.ttsVoice.collectAsState()
    val speed by preferences.ttsSpeed.collectAsState()
    val pitch by preferences.ttsPitch.collectAsState()
    var installed by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var voices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var pickingVoice by remember { mutableStateOf(false) }
    val download by SupertonicDownload.state.collectAsState()
    val supertonic = TtsEngines.isSupertonic(engine)

    LaunchedEffect(Unit) {
        SupertonicDownload.bind(context)
        installed = TtsCatalog(context).installedEngines()
    }
    LaunchedEffect(engine) {
        voices = if (TtsEngines.isSupertonic(engine)) {
            SupertonicVoices.all
        } else {
            TtsCatalog(context).voices(TtsEngines.systemPackage(engine))
        }
    }

    SettingsChipRow(MR.strings.tts_engine) {
        FilterChip(
            selected = engine.isEmpty(),
            onClick = { selectEngine(preferences, context, TtsEngines.SYSTEM) },
            label = { Text(stringResource(MR.strings.tts_system_default)) },
        )
        installed.forEach { (id, label) ->
            FilterChip(
                selected = engine == id,
                onClick = { selectEngine(preferences, context, id) },
                label = { Text(label) },
            )
        }
        FilterChip(
            selected = engine == TtsEngines.SUPERTONIC,
            onClick = { selectEngine(preferences, context, TtsEngines.SUPERTONIC) },
            label = {
                val status = download
                Text(
                    if (status is SupertonicDownloadState.Running) {
                        "${stringResource(MR.strings.tts_supertonic)} ${status.percent}%"
                    } else {
                        stringResource(MR.strings.tts_supertonic)
                    },
                )
            },
        )
    }
    if (download !is SupertonicDownloadState.Ready) {
        Text(
            supertonicCaption(download),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SettingsItemsPaddings.Horizontal),
        )
    }
    when (val status = download) {
        is SupertonicDownloadState.Running -> LinearProgressIndicator(
            progress = { status.bytes / status.total.coerceAtLeast(1L).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsItemsPaddings.Horizontal),
        )
        SupertonicDownloadState.Queued -> LinearProgressIndicator(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsItemsPaddings.Horizontal),
        )
        else -> Unit
    }

    if (supertonic) {
        SettingsChipRow(MR.strings.tts_voice) {
            voices.forEach { (id, label) ->
                FilterChip(
                    selected = voice == id || (voice.isEmpty() && id == SupertonicVoices.DEFAULT),
                    onClick = {
                        preferences.ttsVoice.set(id)
                        preferences.ttsVoiceMemory(engine).set(id)
                        TtsPlaybackService.applySettings(context)
                    },
                    label = { Text(label) },
                )
            }
        }
    } else {
        HeadingItem(MR.strings.tts_voice)
        val voiceLabel = voices.firstOrNull { it.first == voice }?.second
            ?: stringResource(MR.strings.tts_voice_default)
        TextButton(
            onClick = { pickingVoice = true },
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(voiceLabel)
        }
    }

    if (pickingVoice && !supertonic) {
        AlertDialog(
            onDismissRequest = { pickingVoice = false },
            title = { Text(stringResource(MR.strings.tts_voice)) },
            text = {
                LazyColumn(Modifier.height(360.dp).fillMaxWidth()) {
                    item {
                        RadioItem(
                            label = stringResource(MR.strings.tts_voice_default),
                            selected = voice.isEmpty(),
                            onClick = {
                                preferences.ttsVoice.set("")
                                preferences.ttsVoiceMemory(engine).set("")
                                TtsPlaybackService.applySettings(context)
                                pickingVoice = false
                            },
                        )
                    }
                    items(voices, key = { it.first }) { (id, label) ->
                        RadioItem(
                            label = label,
                            selected = voice == id,
                            onClick = {
                                preferences.ttsVoice.set(id)
                                preferences.ttsVoiceMemory(engine).set(id)
                                TtsPlaybackService.applySettings(context)
                                pickingVoice = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingVoice = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    SliderItem(
        value = (speed * 100).roundToInt().coerceIn(50, 300),
        valueRange = 50..300,
        label = stringResource(MR.strings.tts_speed_label),
        valueString = speed.asRateLabel(),
        onChange = {
            preferences.ttsSpeed.set(it / 100f)
            TtsPlaybackService.applySettings(context)
        },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    SliderItem(
        value = (pitch * 100).roundToInt().coerceIn(50, 200),
        valueRange = 50..200,
        label = stringResource(MR.strings.tts_pitch),
        valueString = pitch.asRateLabel(),
        onChange = {
            preferences.ttsPitch.set(it / 100f)
            TtsPlaybackService.applySettings(context)
        },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

@Composable
private fun supertonicCaption(download: SupertonicDownloadState): String = when (download) {
    is SupertonicDownloadState.Running -> stringResource(MR.strings.tts_supertonic_progress, download.percent)
    SupertonicDownloadState.Queued -> stringResource(MR.strings.tts_supertonic_queued)
    SupertonicDownloadState.Ready -> stringResource(MR.strings.tts_supertonic_downloaded)
    SupertonicDownloadState.Failed -> stringResource(MR.strings.tts_supertonic_failed)
    SupertonicDownloadState.Idle -> stringResource(MR.strings.tts_supertonic_unavailable)
}

private fun selectEngine(preferences: ReaderPreferences, context: android.content.Context, id: String) {
    if (TtsEngines.isSupertonic(id) && !SupertonicModel.isReady(context)) {
        SupertonicDownload.onSelected(context)
        return
    }
    val changed = preferences.ttsEngine.get() != id
    if (changed) {
        preferences.ttsVoiceMemory(preferences.ttsEngine.get()).set(preferences.ttsVoice.get())
    }
    preferences.ttsEngine.set(id)
    val remembered = preferences.ttsVoiceMemory(id).get().ifEmpty {
        if (TtsEngines.isSupertonic(id)) SupertonicVoices.DEFAULT else ""
    }
    preferences.ttsVoice.set(remembered)
    preferences.ttsVoiceMemory(id).set(remembered)
    TtsPlaybackService.applySettings(context)
}

private fun Float.asRateLabel(): String = "%.2f".format(this).trimEnd('0').trimEnd('.', ',') + "×"
