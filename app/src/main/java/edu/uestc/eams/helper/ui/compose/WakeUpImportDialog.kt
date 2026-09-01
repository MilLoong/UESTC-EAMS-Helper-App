package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.uestc.eams.helper.R
import edu.uestc.eams.helper.data.parser.TeachingWeekEstimator
import edu.uestc.eams.helper.ui.viewmodel.WakeUpImportPrompt
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateLabelFmt = DateTimeFormatter.ofPattern("yyyy/M/d")

@Composable
fun WakeUpImportDialog(
    prompt: WakeUpImportPrompt,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var selectedDate by remember(prompt.initialDate) { mutableStateOf(prompt.initialDate) }
    val semesterStart = remember { TeachingWeekEstimator.upcomingSemesterStartMonday() }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.wakeup_import_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedDate == semesterStart,
                        onClick = { selectedDate = semesterStart },
                        shape = MaterialTheme.shapes.small,
                        label = {
                            Text(
                                stringResource(
                                    R.string.wakeup_import_preset_semester,
                                    semesterStart.format(dateLabelFmt),
                                ),
                            )
                        },
                        enabled = !loading,
                    )
                    prompt.firstClassDay?.let { firstDay ->
                        FilterChip(
                            selected = selectedDate == firstDay,
                            onClick = { selectedDate = firstDay },
                            shape = MaterialTheme.shapes.small,
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
                AppMonthCalendar(
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedDate) },
                enabled = !loading,
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
