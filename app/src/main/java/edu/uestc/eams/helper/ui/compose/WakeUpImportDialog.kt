package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.uestc.eams.helper.R
import edu.uestc.eams.helper.ui.viewmodel.WakeUpImportPrompt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateLabelFmt = DateTimeFormatter.ofPattern("yyyy/M/d")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WakeUpImportDialog(
    prompt: WakeUpImportPrompt,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val initialMillis =
        remember(prompt.initialDate) {
            prompt.initialDate.atStartOfDay(zone).toInstant().toEpochMilli()
        }
    val dateState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )

    fun millisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.wakeup_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.wakeup_import_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (prompt.fromFile) {
                    Text(
                        stringResource(R.string.wakeup_import_from_file_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            dateState.selectedDateMillis =
                                prompt.semesterOpenDay.atStartOfDay(zone).toInstant().toEpochMilli()
                        },
                        label = {
                            Text(
                                stringResource(
                                    R.string.wakeup_import_preset_semester,
                                    prompt.semesterOpenDay.format(dateLabelFmt),
                                ),
                            )
                        },
                        enabled = !loading,
                    )
                    prompt.firstClassDay?.let { firstDay ->
                        if (firstDay != prompt.semesterOpenDay) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    dateState.selectedDateMillis =
                                        firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.wakeup_import_preset_first_class,
                                            firstDay.format(dateLabelFmt),
                                        ),
                                    )
                                },
                                enabled = !loading,
                            )
                        }
                    }
                }
                DatePicker(state = dateState, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = dateState.selectedDateMillis ?: return@TextButton
                    onConfirm(millisToLocalDate(millis))
                },
                enabled = !loading && dateState.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.wakeup_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.wakeup_import_cancel))
            }
        },
    )
}
