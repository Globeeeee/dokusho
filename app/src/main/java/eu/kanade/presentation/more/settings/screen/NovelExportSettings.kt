package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.export.LibraryExportScreen
import mihon.app.di.appGraph
import tachiyomi.domain.export.service.ExportFormat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Settings, Data and storage: "Export novels" and the defaults of the export sheet. */
@Composable
internal fun getNovelExportGroup(): Preference.PreferenceGroup {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val preferences = remember { context.appGraph.exportPreferences }

    val splitEntries = SPLIT_CHOICES.associateWith { size ->
        if (size == 0) {
            stringResource(MR.strings.export_split_whole)
        } else {
            stringResource(MR.strings.export_split_count, size)
        }
    }

    return Preference.PreferenceGroup(
        title = stringResource(MR.strings.export_settings),
        preferenceItems = listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.export_novels),
                subtitle = stringResource(MR.strings.export_novels_summary),
                onClick = { navigator.push(LibraryExportScreen()) },
            ),
            Preference.PreferenceItem.ListPreference(
                preference = preferences.format,
                entries = mapOf(
                    ExportFormat.EPUB to stringResource(MR.strings.export_format_epub),
                    ExportFormat.PDF to stringResource(MR.strings.export_format_pdf),
                ),
                title = stringResource(MR.strings.export_default_format),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = preferences.epubSplit,
                entries = splitEntries,
                title = stringResource(MR.strings.export_epub_split),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = preferences.pdfSplit,
                entries = splitEntries,
                title = stringResource(MR.strings.export_pdf_split),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.downloadMissing,
                title = stringResource(MR.strings.export_download_missing),
                subtitle = stringResource(MR.strings.export_download_missing_summary),
            ),
        ),
    )
}

private val SPLIT_CHOICES = listOf(0, 25, 50, 100, 200, 500)
