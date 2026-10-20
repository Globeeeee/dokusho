package eu.kanade.presentation.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.export.ExportRange
import eu.kanade.tachiyomi.data.export.NovelExportOptions
import tachiyomi.domain.export.service.ExportFormat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The export options: format, chapter range, chapters per file and "download missing". [chapterCount]
 * and [downloadedCount] are shown when one novel is exported, null for several.
 */
@Composable
fun NovelExportDialog(
    title: String,
    defaults: NovelExportOptions,
    splitFor: (ExportFormat) -> Int,
    chapterCount: Int?,
    downloadedCount: Int?,
    onDismissRequest: () -> Unit,
    onConfirm: (NovelExportOptions) -> Unit,
) {
    var format by rememberSaveable { mutableStateOf(defaults.format) }
    var range by rememberSaveable { mutableStateOf(defaults.range) }
    var from by rememberSaveable { mutableStateOf(defaults.from.toString()) }
    var to by rememberSaveable { mutableStateOf(chapterCount?.toString().orEmpty()) }
    var split by rememberSaveable { mutableStateOf(defaults.chaptersPerFile.toString()) }
    var downloadMissing by rememberSaveable { mutableStateOf(defaults.downloadMissing) }

    val fromValue = from.toIntOrNull()
    val toValue = to.toIntOrNull()
    val splitValue = split.toIntOrNull()
    val rangeValid = range != ExportRange.FROM_TO ||
        (fromValue != null && toValue != null && fromValue >= 1 && toValue >= fromValue)
    val valid = rangeValid && splitValue != null && splitValue >= 0

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionLabel(stringResource(MR.strings.export_format))
                Column(Modifier.selectableGroup()) {
                    ExportFormat.entries.forEach { option ->
                        RadioRow(
                            label = stringResource(
                                when (option) {
                                    ExportFormat.EPUB -> MR.strings.export_format_epub
                                    ExportFormat.PDF -> MR.strings.export_format_pdf
                                },
                            ),
                            selected = format == option,
                            onClick = {
                                if (format != option) {
                                    format = option
                                    split = splitFor(option).toString()
                                }
                            },
                        )
                    }
                }

                SectionLabel(stringResource(MR.strings.export_range))
                Column(Modifier.selectableGroup()) {
                    RadioRow(
                        label = chapterCount?.let { stringResource(MR.strings.export_range_all_count, it) }
                            ?: stringResource(MR.strings.export_range_all),
                        selected = range == ExportRange.ALL,
                        onClick = { range = ExportRange.ALL },
                    )
                    RadioRow(
                        label = downloadedCount?.let { stringResource(MR.strings.export_range_downloaded_count, it) }
                            ?: stringResource(MR.strings.export_range_downloaded),
                        selected = range == ExportRange.DOWNLOADED,
                        onClick = { range = ExportRange.DOWNLOADED },
                    )
                    RadioRow(
                        label = stringResource(MR.strings.export_range_from_to),
                        selected = range == ExportRange.FROM_TO,
                        onClick = { range = ExportRange.FROM_TO },
                    )
                }
                if (range == ExportRange.FROM_TO) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            value = from,
                            onValueChange = { from = it },
                            label = stringResource(MR.strings.export_from),
                            isError = !rangeValid,
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = to,
                            onValueChange = { to = it },
                            label = stringResource(MR.strings.export_to),
                            isError = !rangeValid,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = stringResource(MR.strings.export_range_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                NumberField(
                    value = split,
                    onValueChange = { split = it },
                    label = stringResource(MR.strings.export_split),
                    supportingText = stringResource(MR.strings.export_split_hint),
                    isError = splitValue == null,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )

                LabeledCheckbox(
                    label = stringResource(MR.strings.export_download_missing),
                    checked = downloadMissing,
                    onCheckedChange = { downloadMissing = it },
                )
                Text(
                    text = stringResource(MR.strings.export_download_missing_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        NovelExportOptions(
                            format = format,
                            range = range,
                            from = fromValue ?: 1,
                            to = toValue ?: Int.MAX_VALUE,
                            chaptersPerFile = splitValue ?: 0,
                            downloadMissing = downloadMissing,
                        ),
                    )
                },
            ) {
                Text(text = stringResource(MR.strings.action_export))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit).take(6)) },
        label = { Text(text = label) },
        supportingText = supportingText?.let { { Text(text = it) } },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
