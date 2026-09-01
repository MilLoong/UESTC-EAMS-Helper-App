package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateLabelFmt = DateTimeFormatter.ofPattern("yyyy/M/d")

@Composable
fun AdjustWeekOneDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate) }
    val previewMonday = selectedDate.with(DayOfWeek.MONDAY)
    val ranges = TeachingWeekEstimator.previewWeekRanges(previewMonday, 1..4)
    val today = remember { LocalDate.now() }
    val todayWeek = TeachingWeekEstimator.teachingWeekForDate(previewMonday, today)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.adjust_week_one_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.adjust_week_one_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppMonthCalendar(
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it },
                )
                Text(
                    stringResource(
                        R.string.adjust_week_one_preview_monday,
                        previewMonday.format(dateLabelFmt),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                ranges.forEach { range ->
                    Text(
                        stringResource(
                            R.string.adjust_week_one_preview_week,
                            range.week,
                            range.monday.format(dateLabelFmt),
                            range.sunday.format(dateLabelFmt),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (today.isBefore(previewMonday)) {
                    Text(
                        stringResource(R.string.adjust_week_one_preview_before),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.adjust_week_one_preview_today, todayWeek),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedDate) }) {
                Text(stringResource(R.string.adjust_week_one_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.adjust_week_one_cancel))
            }
        },
    )
}
