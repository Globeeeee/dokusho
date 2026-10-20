package eu.kanade.tachiyomi.ui.export

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.export.NovelExportDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.SelectAll
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/** Settings, Data and storage, Export novels: pick library novels, then the export options. */
class LibraryExportScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = metroViewModel<LibraryExportViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        var showOptions by rememberSaveable { mutableStateOf(false) }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    searchQuery = state.query,
                    onChangeSearchQuery = viewModel::search,
                    titleContent = {
                        AppBarTitle(
                            title = stringResource(MR.strings.export_novels),
                            subtitle = state.selected.size.takeIf { it > 0 }
                                ?.let { stringResource(MR.strings.export_selected, it) },
                        )
                    },
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_select_all),
                                    icon = MaterialSymbols.Rounded.SelectAll,
                                    onClick = viewModel::toggleAll,
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                if (state.selected.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        text = { Text(text = stringResource(MR.strings.action_export)) },
                        icon = { Icon(imageVector = MaterialSymbols.Rounded.Download, contentDescription = null) },
                        onClick = { showOptions = true },
                    )
                }
            },
        ) { contentPadding ->
            when {
                state.loading -> LoadingScreen(Modifier.padding(contentPadding))
                state.novels.isEmpty() -> EmptyScreen(
                    stringRes = MR.strings.export_no_novels,
                    modifier = Modifier.padding(contentPadding),
                )
                else -> FastScrollLazyColumn(contentPadding = contentPadding) {
                    items(state.visible, key = { it.id }) { manga ->
                        val checked = manga.id in state.selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(role = Role.Checkbox) { viewModel.toggle(manga.id) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (showOptions) {
            val count = state.selected.size
            NovelExportDialog(
                title = if (count == 1) {
                    stringResource(MR.strings.export_title)
                } else {
                    stringResource(MR.strings.export_title_many, count)
                },
                defaults = viewModel.defaults,
                splitFor = viewModel::splitFor,
                chapterCount = null,
                downloadedCount = null,
                onDismissRequest = { showOptions = false },
                onConfirm = { options ->
                    viewModel.export(options)
                    showOptions = false
                    context.toast(MR.strings.export_started)
                },
            )
        }
    }
}
