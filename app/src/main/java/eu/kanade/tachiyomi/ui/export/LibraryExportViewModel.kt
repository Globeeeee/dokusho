package eu.kanade.tachiyomi.ui.export

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.export.ExportWorker
import eu.kanade.tachiyomi.data.export.NovelExportOptions
import eu.kanade.tachiyomi.source.NovelSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tachiyomi.domain.export.service.ExportFormat
import tachiyomi.domain.export.service.ExportPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

/** The library's novels for "Export novels" in settings: search, selection and the export itself. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class LibraryExportViewModel(
    private val context: Context,
    getLibraryManga: GetLibraryManga,
    sourceManager: SourceManager,
    private val preferences: ExportPreferences,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val novels: List<Manga> = emptyList(),
        val query: String? = null,
        val selected: Set<Long> = emptySet(),
    ) {
        val visible: List<Manga>
            get() = query?.takeIf { it.isNotBlank() }
                ?.let { q -> novels.filter { it.title.contains(q, ignoreCase = true) } }
                ?: novels
    }

    private val query = MutableStateFlow<String?>(null)
    private val selected = MutableStateFlow<Set<Long>>(emptySet())

    private val novels = getLibraryManga.subscribe()
        .map { library ->
            library.map { it.manga }
                .distinctBy { it.id }
                .filter { sourceManager.get(it.source) is NovelSource }
                .sortedBy { it.title.lowercase() }
        }

    val state: StateFlow<State> = combine(novels, query, selected) { novels, query, selected ->
        State(loading = false, novels = novels, query = query, selected = selected)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State())

    val defaults: NovelExportOptions
        get() = defaultOptions(preferences)

    fun splitFor(format: ExportFormat): Int = preferences.splitFor(format).get()

    fun search(text: String?) {
        query.value = text
    }

    fun toggle(id: Long) {
        selected.update { if (id in it) it - id else it + id }
    }

    /** Selects every novel the search shows, or clears them when they are all selected already. */
    fun toggleAll() {
        val ids = state.value.visible.map { it.id }.toSet()
        selected.update { if (it.containsAll(ids)) it - ids else it + ids }
    }

    fun export(options: NovelExportOptions) {
        val ids = state.value.novels.filter { it.id in selected.value }.map { it.id }
        if (ids.isNotEmpty()) ExportWorker.start(context, ids, options)
        selected.value = emptySet()
    }
}
