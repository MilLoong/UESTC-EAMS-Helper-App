package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.uestc.eams.helper.domain.model.SemesterCodes

/** 把教务学期编码格式化为可读标签。 */
internal fun semesterLabel(code: String): String = SemesterCodes.label(code)

internal fun canonicalSemesterKey(code: String): String = SemesterCodes.canonicalKey(code)

internal fun sameSemester(a: String?, b: String?): Boolean = SemesterCodes.same(a, b)

/** 学期选择栏选中态：全部 / 当前学期 / 指定学期编码。 */
internal sealed interface SemesterBarSelection {
    data object All : SemesterBarSelection
    data object Current : SemesterBarSelection
    data class Code(val value: String) : SemesterBarSelection
}

/** 学期选择栏。 */
@Composable
internal fun SemesterSelectBar(
    semesterOptions: List<String>,
    selection: SemesterBarSelection,
    onSelect: (SemesterBarSelection) -> Unit,
    modifier: Modifier = Modifier,
    showAllOption: Boolean = true,
    currentSemesterCode: String? = null,
    showCurrentOption: Boolean = false,
) {
    val chips =
        if (showCurrentOption && !currentSemesterCode.isNullOrBlank()) {
            semesterOptions.filter { !sameSemester(it, currentSemesterCode) }
        } else {
            semesterOptions
        }
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAllOption) {
            FilterChip(
                selected = selection is SemesterBarSelection.All,
                onClick = { onSelect(SemesterBarSelection.All) },
                shape = MaterialTheme.shapes.small,
                label = { Text("全部", style = MaterialTheme.typography.labelMedium) },
            )
        }
        if (showCurrentOption) {
            FilterChip(
                selected = selection is SemesterBarSelection.Current,
                onClick = { onSelect(SemesterBarSelection.Current) },
                shape = MaterialTheme.shapes.small,
                label = { Text("当前学期", style = MaterialTheme.typography.labelMedium) },
            )
        }
        chips.forEach { code ->
            FilterChip(
                selected = selection is SemesterBarSelection.Code && selection.value == code,
                onClick = { onSelect(SemesterBarSelection.Code(code)) },
                shape = MaterialTheme.shapes.small,
                label = { Text(semesterLabel(code), style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

/** 以三横线按钮触发的学期选择下拉。 */
@Composable
internal fun SemesterMenuButton(
    semesterOptions: List<String>,
    activeSemesterCode: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    currentSemesterCode: String? = null,
) {
    if (semesterOptions.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = "选择学期",
                modifier = Modifier.size(24.dp),
            )
        }
        SemesterDropdownMenu(
            expanded = expanded,
            semesterOptions = semesterOptions,
            activeSemesterCode = activeSemesterCode,
            onSelect = onSelect,
            onDismiss = { expanded = false },
            currentSemesterCode = currentSemesterCode,
        )
    }
}

@Composable
private fun SemesterDropdownMenu(
    expanded: Boolean,
    semesterOptions: List<String>,
    activeSemesterCode: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    currentSemesterCode: String? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val selectable =
            semesterOptions.filter { !sameSemester(it, currentSemesterCode) }
        DropdownMenuItem(
            text = { Text("当前学期") },
            leadingIcon =
                if (activeSemesterCode == null || sameSemester(activeSemesterCode, currentSemesterCode)) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else {
                    null
                },
            onClick = {
                onDismiss()
                onSelect(null)
            },
        )
        selectable.forEach { code ->
            DropdownMenuItem(
                text = { Text(semesterLabel(code)) },
                leadingIcon =
                    if (code == activeSemesterCode) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                onClick = {
                    onDismiss()
                    onSelect(code)
                },
            )
        }
    }
}
