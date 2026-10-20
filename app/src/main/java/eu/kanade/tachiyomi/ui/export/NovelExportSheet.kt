package eu.kanade.tachiyomi.ui.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.export.NovelExportDialog
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** The novel details "Export" action: options for this novel, then the export runs in the background. */
@Composable
fun NovelExportSheet(mangaId: Long, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val viewModel = assistedMetroViewModel<NovelExportViewModel, NovelExportViewModel.Factory> {
        create(mangaId = mangaId)
    }
    LaunchedEffect(viewModel) { viewModel.refresh() }
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val loaded = counts ?: return
    NovelExportDialog(
        title = stringResource(MR.strings.export_title),
        defaults = viewModel.defaults,
        splitFor = viewModel::splitFor,
        chapterCount = loaded.chapters,
        downloadedCount = loaded.downloaded,
        onDismissRequest = onDismissRequest,
        onConfirm = { options ->
            viewModel.export(options)
            context.toast(MR.strings.export_started)
            onDismissRequest()
        },
    )
}
