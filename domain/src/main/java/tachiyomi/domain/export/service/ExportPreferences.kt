package tachiyomi.domain.export.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

enum class ExportFormat(val extension: String) {
    EPUB("epub"),
    PDF("pdf"),
}

/** Defaults for the novel export sheet. A split of 0 puts every chapter in one file. */
@Inject
@SingleIn(AppScope::class)
class ExportPreferences(
    preferenceStore: PreferenceStore,
) {

    val format: Preference<ExportFormat> = preferenceStore.getEnum("export_format", ExportFormat.EPUB)

    val epubSplit: Preference<Int> = preferenceStore.getInt("export_epub_split", 0)

    val pdfSplit: Preference<Int> = preferenceStore.getInt("export_pdf_split", 100)

    val downloadMissing: Preference<Boolean> = preferenceStore.getBoolean("export_download_missing", false)

    fun splitFor(format: ExportFormat): Preference<Int> = when (format) {
        ExportFormat.EPUB -> epubSplit
        ExportFormat.PDF -> pdfSplit
    }
}
