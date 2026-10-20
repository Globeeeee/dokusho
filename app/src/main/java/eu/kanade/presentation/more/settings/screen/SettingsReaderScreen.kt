package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.tts.SupertonicDownload
import eu.kanade.tachiyomi.data.tts.SupertonicDownloadState
import eu.kanade.tachiyomi.data.tts.SupertonicModel
import eu.kanade.tachiyomi.data.tts.SupertonicVoices
import eu.kanade.tachiyomi.data.tts.TtsCatalog
import eu.kanade.tachiyomi.data.tts.TtsEngines
import eu.kanade.tachiyomi.data.tts.TtsPlaybackService
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.text.NumberFormat

object SettingsReaderScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_reader

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val readerPref = remember { context.appGraph.readerPreferences }

        return listOf(
            Preference.PreferenceItem.ListPreference(
                preference = readerPref.defaultReadingMode,
                entries = ReadingMode.entries.drop(1)
                    .associate { it.flagValue to stringResource(it.stringRes) },
                title = stringResource(MR.strings.pref_viewer_type),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = readerPref.doubleTapAnimSpeed,
                entries = mapOf(
                    1 to stringResource(MR.strings.double_tap_anim_speed_0),
                    500 to stringResource(MR.strings.double_tap_anim_speed_normal),
                    250 to stringResource(MR.strings.double_tap_anim_speed_fast),
                ),
                title = stringResource(MR.strings.pref_double_tap_anim_speed),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.showReadingMode,
                title = stringResource(MR.strings.pref_show_reading_mode),
                subtitle = stringResource(MR.strings.pref_show_reading_mode_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.showNavigationOverlayOnStart,
                title = stringResource(MR.strings.pref_show_navigation_mode),
                subtitle = stringResource(MR.strings.pref_show_navigation_mode_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.pageTransitions,
                title = stringResource(MR.strings.pref_page_transitions),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.loadAheadOnlyOverWifi,
                title = stringResource(MR.strings.pref_load_ahead_only_over_wifi),
                subtitle = stringResource(MR.strings.pref_load_ahead_only_over_wifi_summary),
            ),
            getNovelGroup(readerPreferences = readerPref),
            getTtsGroup(readerPreferences = readerPref),
            getDisplayGroup(readerPreferences = readerPref),
            getEInkGroup(readerPreferences = readerPref),
            getReadingGroup(readerPreferences = readerPref),
            getPagedGroup(readerPreferences = readerPref),
            getWebtoonGroup(readerPreferences = readerPref),
            getNavigationGroup(readerPreferences = readerPref),
            getActionsGroup(readerPreferences = readerPref),
        )
    }

    // Dokusho: the text reader. The same options are in the reader's settings dialog for novel sources.
    @Composable
    private fun getNovelGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.novel_reader),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.novelReadingMode,
                    entries = mapOf(
                        ReaderPreferences.NovelReadingMode.SCROLL to stringResource(MR.strings.novel_mode_scroll),
                        ReaderPreferences.NovelReadingMode.PAGED_LTR to stringResource(MR.strings.novel_mode_paged_ltr),
                        ReaderPreferences.NovelReadingMode.PAGED_RTL to stringResource(MR.strings.novel_mode_paged_rtl),
                        ReaderPreferences.NovelReadingMode.PAGED_VERTICAL to
                            stringResource(MR.strings.novel_mode_paged_vertical),
                    ),
                    title = stringResource(MR.strings.novel_reading_mode),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.novelTheme,
                    entries = mapOf(
                        ReaderPreferences.NovelTheme.AUTO to stringResource(MR.strings.novel_theme_auto),
                        ReaderPreferences.NovelTheme.PAPER to stringResource(MR.strings.novel_theme_paper),
                        ReaderPreferences.NovelTheme.SEPIA to stringResource(MR.strings.novel_theme_sepia),
                        ReaderPreferences.NovelTheme.DARK to stringResource(MR.strings.novel_theme_dark),
                        ReaderPreferences.NovelTheme.BLACK to stringResource(MR.strings.novel_theme_black),
                    ),
                    title = stringResource(MR.strings.novel_theme),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.novelFont,
                    entries = mapOf(
                        ReaderPreferences.NovelFont.SERIF to stringResource(MR.strings.novel_font_serif),
                        ReaderPreferences.NovelFont.SANS to stringResource(MR.strings.novel_font_sans),
                        ReaderPreferences.NovelFont.MONO to stringResource(MR.strings.novel_font_mono),
                        ReaderPreferences.NovelFont.SYSTEM to stringResource(MR.strings.novel_font_system),
                    ),
                    title = stringResource(MR.strings.novel_font),
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.novelFontSize,
                    valueRange = 12..32,
                    title = stringResource(MR.strings.novel_font_size),
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.novelHorizontalMargin,
                    valueRange = 8..48 step 4,
                    title = stringResource(MR.strings.novel_margins),
                ),
            ),
        )
    }

    @Composable
    private fun getTtsGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val engine by readerPreferences.ttsEngine.collectAsState()
        val systemDefault = stringResource(MR.strings.tts_system_default)
        val voiceDefault = stringResource(MR.strings.tts_voice_default)
        val supertonic = stringResource(MR.strings.tts_supertonic)
        val download by SupertonicDownload.state.collectAsState()
        val supertonicLabel = when (val status = download) {
            is SupertonicDownloadState.Running -> "$supertonic ${status.percent}%"
            else -> supertonic
        }
        var engineEntries by remember {
            mutableStateOf(mapOf(TtsEngines.SYSTEM to systemDefault, TtsEngines.SUPERTONIC to supertonicLabel))
        }
        var voiceEntries by remember { mutableStateOf(mapOf("" to voiceDefault)) }

        LaunchedEffect(Unit) {
            SupertonicDownload.bind(context)
        }
        LaunchedEffect(systemDefault, supertonicLabel) {
            val installed = TtsCatalog(context).installedEngines()
            engineEntries = buildMap {
                put(TtsEngines.SYSTEM, systemDefault)
                installed.forEach { (id, label) -> put(id, label) }
                put(TtsEngines.SUPERTONIC, supertonicLabel)
            }
        }
        LaunchedEffect(engine, voiceDefault) {
            voiceEntries = if (TtsEngines.isSupertonic(engine)) {
                SupertonicVoices.all.toMap()
            } else {
                buildMap {
                    put("", voiceDefault)
                    TtsCatalog(context).voices(TtsEngines.systemPackage(engine)).forEach { (id, label) ->
                        put(id, label)
                    }
                }
            }
        }

        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
        val pitches = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.tts_settings),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.ttsEngine,
                    entries = engineEntries,
                    title = stringResource(MR.strings.tts_engine),
                    subtitleProvider = { value, entries ->
                        val name = entries[value] ?: value
                        val detail = when (val status = download) {
                            is SupertonicDownloadState.Running ->
                                stringResource(MR.strings.tts_supertonic_progress, status.percent)
                            SupertonicDownloadState.Queued -> stringResource(MR.strings.tts_supertonic_queued)
                            SupertonicDownloadState.Ready -> stringResource(MR.strings.tts_supertonic_downloaded)
                            SupertonicDownloadState.Failed -> stringResource(MR.strings.tts_supertonic_failed)
                            SupertonicDownloadState.Idle -> null
                        }
                        if (detail == null) name else "$name · $detail"
                    },
                    onValueChanged = { value ->
                        if (TtsEngines.isSupertonic(value) && !SupertonicModel.isReady(context)) {
                            SupertonicDownload.onSelected(context)
                            false
                        } else {
                            val previous = readerPreferences.ttsEngine.get()
                            if (previous != value) {
                                readerPreferences.ttsVoiceMemory(previous).set(readerPreferences.ttsVoice.get())
                            }
                            readerPreferences.ttsEngine.set(value)
                            val remembered = readerPreferences.ttsVoiceMemory(value).get().ifEmpty {
                                if (TtsEngines.isSupertonic(value)) SupertonicVoices.DEFAULT else ""
                            }
                            readerPreferences.ttsVoice.set(remembered)
                            readerPreferences.ttsVoiceMemory(value).set(remembered)
                            TtsPlaybackService.applySettings(context)
                            true
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.ttsVoice,
                    entries = voiceEntries,
                    title = stringResource(MR.strings.tts_voice),
                    onValueChanged = { value ->
                        readerPreferences.ttsVoiceMemory(readerPreferences.ttsEngine.get()).set(value)
                        TtsPlaybackService.applySettings(context)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.ttsSpeed,
                    entries = speeds.associateWith { it.asRate() },
                    title = stringResource(MR.strings.tts_speed_label),
                    subtitleProvider = { value, entries -> entries[value] ?: value.asRate() },
                    onValueChanged = {
                        TtsPlaybackService.applySettings(context)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.ttsPitch,
                    entries = pitches.associateWith { it.asRate() },
                    title = stringResource(MR.strings.tts_pitch),
                    subtitleProvider = { value, entries -> entries[value] ?: value.asRate() },
                    onValueChanged = {
                        TtsPlaybackService.applySettings(context)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.ttsShowCover,
                    title = stringResource(MR.strings.tts_show_cover),
                    subtitle = stringResource(MR.strings.tts_show_cover_summary),
                ),
            ),
        )
    }

    @Composable
    private fun getDisplayGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val fullscreen by readerPreferences.fullscreen.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.defaultOrientationType,
                    entries = ReaderOrientation.entries.drop(1)
                        .associate { it.flagValue to stringResource(it.stringRes) },
                    title = stringResource(MR.strings.pref_rotation_type),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.readerTheme,
                    entries = mapOf(
                        1 to stringResource(MR.strings.black_background),
                        2 to stringResource(MR.strings.gray_background),
                        0 to stringResource(MR.strings.white_background),
                        3 to stringResource(MR.strings.automatic_background),
                    ),
                    title = stringResource(MR.strings.pref_reader_theme),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.fullscreen,
                    title = stringResource(MR.strings.pref_fullscreen),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.drawUnderCutout,
                    title = stringResource(MR.strings.pref_cutout_short),
                    visible = LocalView.current.hasDisplayCutout() && fullscreen,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.keepScreenOn,
                    title = stringResource(MR.strings.pref_keep_screen_on),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.showPageNumber,
                    title = stringResource(MR.strings.pref_show_page_number),
                ),
            ),
        )
    }

    @Composable
    private fun getEInkGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val flashPageState by readerPreferences.flashOnPageChange.collectAsState()

        val flashColorPref = readerPreferences.flashColor

        return Preference.PreferenceGroup(
            title = "E-Ink",
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.flashOnPageChange,
                    title = stringResource(MR.strings.pref_flash_page),
                    subtitle = stringResource(MR.strings.pref_flash_page_summ),
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.flashDurationMillis,
                    valueRange = ReaderPreferences.MILLI_CONVERSION.let { it..it * 15 step it },
                    title = stringResource(MR.strings.pref_flash_duration),
                    valueText = { stringResource(MR.strings.pref_flash_duration_summary, it) },
                    visible = flashPageState,
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.flashPageInterval,
                    valueRange = 1..10,
                    title = stringResource(MR.strings.pref_flash_page_interval),
                    valueText = { pluralStringResource(MR.plurals.pref_pages, it, it) },
                    visible = flashPageState,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = flashColorPref,
                    entries = mapOf(
                        ReaderPreferences.FlashColor.BLACK to stringResource(MR.strings.pref_flash_style_black),
                        ReaderPreferences.FlashColor.WHITE to stringResource(MR.strings.pref_flash_style_white),
                        ReaderPreferences.FlashColor.WHITE_BLACK
                            to stringResource(MR.strings.pref_flash_style_white_black),
                    ),
                    title = stringResource(MR.strings.pref_flash_with),
                    visible = flashPageState,
                ),
            ),
        )
    }

    @Composable
    private fun getReadingGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_reading),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipRead,
                    title = stringResource(MR.strings.pref_skip_read_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipFiltered,
                    title = stringResource(MR.strings.pref_skip_filtered_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipDupe,
                    title = stringResource(MR.strings.pref_skip_dupe_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.alwaysShowChapterTransition,
                    title = stringResource(MR.strings.pref_always_show_chapter_transition),
                ),
            ),
        )
    }

    @Composable
    private fun getPagedGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val navModePref = readerPreferences.navigationModePager
        val imageScaleTypePref = readerPreferences.imageScaleType
        val dualPageSplitPref = readerPreferences.dualPageSplitPaged
        val rotateToFitPref = readerPreferences.dualPageRotateToFit

        val navMode by navModePref.collectAsState()
        val imageScaleType by imageScaleTypePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()
        val rotateToFit by rotateToFitPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pager_viewer),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = navModePref,
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap(),
                    title = stringResource(MR.strings.pref_viewer_nav),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.pagerNavInverted,
                    entries = listOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) },
                    title = stringResource(MR.strings.pref_read_with_tapping_inverted),
                    visible = navMode != 5,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = imageScaleTypePref,
                    entries = ReaderPreferences.ImageScaleType
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap(),
                    title = stringResource(MR.strings.pref_image_scale_type),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.zoomStart,
                    entries = ReaderPreferences.ZoomStart
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap(),
                    title = stringResource(MR.strings.pref_zoom_start),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.cropBorders,
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.landscapeZoom,
                    title = stringResource(MR.strings.pref_landscape_zoom),
                    visible = imageScaleType == 1,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.navigateToPan,
                    title = stringResource(MR.strings.pref_navigate_pan),
                    visible = navMode != 5,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = dualPageSplitPref,
                    title = stringResource(MR.strings.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageInvertPaged,
                    title = stringResource(MR.strings.pref_dual_page_invert),
                    subtitle = stringResource(MR.strings.pref_dual_page_invert_summary),
                    visible = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rotateToFitPref,
                    title = stringResource(MR.strings.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageRotateToFitInvert,
                    title = stringResource(MR.strings.pref_page_rotate_invert),
                    visible = rotateToFit,
                ),
            ),
        )
    }

    @Composable
    private fun getWebtoonGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val numberFormat = remember { NumberFormat.getPercentInstance() }

        val navModePref = readerPreferences.navigationModeWebtoon
        val dualPageSplitPref = readerPreferences.dualPageSplitWebtoon
        val rotateToFitPref = readerPreferences.dualPageRotateToFitWebtoon

        val navMode by navModePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()
        val rotateToFit by rotateToFitPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.webtoon_viewer),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = navModePref,
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap(),
                    title = stringResource(MR.strings.pref_viewer_nav),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.webtoonNavInverted,
                    entries = listOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) },
                    title = stringResource(MR.strings.pref_read_with_tapping_inverted),
                    visible = navMode != 5,
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.webtoonSidePadding,
                    valueRange = ReaderPreferences.let {
                        it.WEBTOON_PADDING_MIN..it.WEBTOON_PADDING_MAX
                    },
                    title = stringResource(MR.strings.pref_webtoon_side_padding),
                    valueText = { numberFormat.format(it / 100f) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.readerHideThreshold,
                    entries = mapOf(
                        ReaderPreferences.ReaderHideThreshold.HIGHEST to stringResource(MR.strings.pref_highest),
                        ReaderPreferences.ReaderHideThreshold.HIGH to stringResource(MR.strings.pref_high),
                        ReaderPreferences.ReaderHideThreshold.LOW to stringResource(MR.strings.pref_low),
                        ReaderPreferences.ReaderHideThreshold.LOWEST to stringResource(MR.strings.pref_lowest),
                    ),
                    title = stringResource(MR.strings.pref_hide_threshold),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.cropBordersWebtoon,
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = dualPageSplitPref,
                    title = stringResource(MR.strings.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageInvertWebtoon,
                    title = stringResource(MR.strings.pref_dual_page_invert),
                    subtitle = stringResource(MR.strings.pref_dual_page_invert_summary),
                    visible = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rotateToFitPref,
                    title = stringResource(MR.strings.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageRotateToFitInvertWebtoon,
                    title = stringResource(MR.strings.pref_page_rotate_invert),
                    visible = rotateToFit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.webtoonDoubleTapZoomEnabled,
                    title = stringResource(MR.strings.pref_double_tap_zoom),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.webtoonDisableZoomOut,
                    title = stringResource(MR.strings.pref_webtoon_disable_zoom_out),
                ),
            ),
        )
    }

    @Composable
    private fun getNavigationGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val readWithVolumeKeysPref = readerPreferences.readWithVolumeKeys
        val readWithVolumeKeys by readWithVolumeKeysPref.collectAsState()

        val verticalNavigator by readerPreferences.verticalNavigator.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_reader_navigation),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readWithVolumeKeysPref,
                    title = stringResource(MR.strings.pref_read_with_volume_keys),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.readWithVolumeKeysInverted,
                    title = stringResource(MR.strings.pref_read_with_volume_keys_inverted),
                    visible = readWithVolumeKeys,
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = readerPreferences.verticalNavigator,
                    entries = ReadingMode.entries.filter { it != ReadingMode.DEFAULT }
                        .associate { it to stringResource(it.stringRes) },
                    title = stringResource(MR.strings.pref_vertical_navigator),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.verticalNavigatorOnLeft,
                    title = stringResource(MR.strings.pref_webtoon_vertical_navigator_on_left),
                    visible = verticalNavigator.isNotEmpty(),
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.verticalNavigatorHeight,
                    valueRange = 65..100,
                    steps = 6,
                    title = stringResource(MR.strings.pref_vertical_navigator_height),
                    visible = verticalNavigator.isNotEmpty(),
                ),
            ),
        )
    }

    @Composable
    private fun getActionsGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_reader_actions),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.readWithLongTap,
                    title = stringResource(MR.strings.pref_read_with_long_tap),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.folderPerManga,
                    title = stringResource(MR.strings.pref_create_folder_per_manga),
                    subtitle = stringResource(MR.strings.pref_create_folder_per_manga_summary),
                ),
            ),
        )
    }

    private fun Float.asRate(): String = "%.2f".format(this).trimEnd('0').trimEnd('.', ',') + "×"
}
