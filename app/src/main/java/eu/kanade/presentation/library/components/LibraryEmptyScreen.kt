package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Explore
import mihon.icons.materialsymbols.rounded.LocalLibrary
import mihon.icons.materialsymbols.roundedfilled.Extension
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Shown when the library has no novels: what to do next, with direct ways to do it. */
@Composable
fun LibraryEmptyScreen(
    onBrowse: () -> Unit,
    onExtensions: () -> Unit,
    onGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.LocalLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(20.dp)
                    .size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(MR.strings.information_empty_library),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(MR.strings.library_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                Icon(MaterialSymbols.Rounded.Explore, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(MR.strings.library_empty_browse))
            }
            OutlinedButton(onClick = onExtensions, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    MaterialSymbols.RoundedFilled.Extension,
                    contentDescription = null,
                    Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(MR.strings.library_empty_extensions))
            }
            TextButton(onClick = onGuide, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(MR.strings.getting_started_guide))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun LibraryEmptyScreenPreview() {
    TachiyomiPreviewTheme {
        Surface {
            LibraryEmptyScreen(onBrowse = {}, onExtensions = {}, onGuide = {})
        }
    }
}
