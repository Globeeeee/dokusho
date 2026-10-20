package eu.kanade.tachiyomi.ui.reader.viewer.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Headphones
import mihon.icons.materialsymbols.rounded.KeyboardArrowRight
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.i18n.stringResource

/**
 * A system voice label from [eu.kanade.tachiyomi.data.tts.TtsCatalog], such as
 * "English (India) 4 · online · hd", split into language, number and badges.
 */
@Immutable
internal data class VoiceLabel(val language: String, val number: Int?, val online: Boolean, val hd: Boolean) {
    companion object {
        private val NUMBER = Regex("""\s(\d+)$""")

        fun of(label: String): VoiceLabel {
            val parts = label.split(" · ")
            val base = parts.first()
            val number = NUMBER.find(base)?.groupValues?.get(1)?.toIntOrNull()
            return VoiceLabel(
                language = if (number != null) base.replace(NUMBER, "") else base,
                number = number,
                online = "online" in parts,
                hd = "hd" in parts,
            )
        }
    }
}

@Composable
private fun VoiceLabel.title(): String =
    number?.let { "$language · ${stringResource(MR.strings.tts_voice_numbered, it)}" } ?: language

/** The chosen system voice as a tappable card, matching the theme's chips. */
@Composable
internal fun VoiceCard(current: VoiceLabel?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsItemsPaddings.Horizontal),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    current?.title() ?: stringResource(MR.strings.tts_voice_default),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current != null && (current.online || current.hd)) {
                    VoiceBadges(current)
                } else {
                    Text(
                        stringResource(MR.strings.tts_voice_change),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = MaterialSymbols.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VoiceBadges(label: VoiceLabel) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.online) VoiceBadge(stringResource(MR.strings.tts_voice_online))
        if (label.hd) VoiceBadge(stringResource(MR.strings.tts_voice_hd))
    }
}

@Composable
private fun VoiceBadge(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private sealed interface VoiceRow {
    data class Header(val language: String) : VoiceRow
    data class Item(val id: String, val label: VoiceLabel?) : VoiceRow
}

/** Voices grouped by language, opened at the voice in use. An empty [selected] is the engine default. */
@Composable
internal fun VoicePickerDialog(
    voices: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = remember(voices) {
        buildList {
            add(VoiceRow.Item("", null))
            voices.map { (id, text) -> id to VoiceLabel.of(text) }
                .groupBy { it.second.language }
                .forEach { (language, group) ->
                    add(VoiceRow.Header(language))
                    group.forEach { (id, label) -> add(VoiceRow.Item(id, label)) }
                }
        }
    }
    val start = remember(rows, selected) {
        val index = rows.indexOfFirst { it is VoiceRow.Item && it.id == selected }
        // Show the language header above the chosen voice too
        (index - 1).coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.tts_voice)) },
        text = {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(400.dp).fillMaxWidth(),
            ) {
                items(rows) { row ->
                    when (row) {
                        is VoiceRow.Header -> Text(
                            row.language,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                        )
                        is VoiceRow.Item -> VoiceOption(row, row.id == selected, onClick = { onSelect(row.id) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun VoiceOption(row: VoiceRow.Item, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val label = row.label
                Text(
                    when {
                        label == null -> stringResource(MR.strings.tts_voice_default)
                        label.number != null -> stringResource(MR.strings.tts_voice_numbered, label.number)
                        else -> label.language
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (label != null && (label.online || label.hd)) VoiceBadges(label)
            }
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (isSelected) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
