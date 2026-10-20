package eu.kanade.tachiyomi.ui.export

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.export.ExportRange
import eu.kanade.tachiyomi.data.export.ExportWorker
import eu.kanade.tachiyomi.data.export.NovelExportOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.export.service.ExportFormat
import tachiyomi.domain.export.service.ExportPreferences
import tachiyomi.domain.manga.interactor.GetManga

/** Backs the export sheet of one novel: chapter counts, defaults from settings, and the export itself. */
@AssistedInject
class NovelExportViewModel(
    @Assisted private val mangaId: Long,
    private val context: Context,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val downloadManager: DownloadManager,
    private val preferences: ExportPreferences,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): NovelExportViewModel
    }

    data class Counts(val chapters: Int, val downloaded: Int)

    private val _counts = MutableStateFlow<Counts?>(null)
    val counts: StateFlow<Counts?> = _counts.asStateFlow()

    val defaults: NovelExportOptions
        get() = defaultOptions(preferences)

    /** Counts again each time the sheet opens, since downloads may have finished in between. */
    fun refresh() {
        _counts.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val manga = getManga.await(mangaId) ?: return@launch
            val chapters = getChaptersByMangaId.await(mangaId)
            val downloaded = chapters.count {
                downloadManager.isChapterDownloaded(it.name, it.scanlator, it.url, manga.title, manga.source)
            }
            _counts.value = Counts(chapters.size, downloaded)
        }
    }

    fun splitFor(format: ExportFormat): Int = preferences.splitFor(format).get()

    fun export(options: NovelExportOptions) {
        ExportWorker.start(context, listOf(mangaId), options)
    }
}

internal fun defaultOptions(preferences: ExportPreferences): NovelExportOptions {
    val format = preferences.format.get()
    return NovelExportOptions(
        format = format,
        range = ExportRange.ALL,
        from = 1,
        to = Int.MAX_VALUE,
        chaptersPerFile = preferences.splitFor(format).get(),
        downloadMissing = preferences.downloadMissing.get(),
    )
}
