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

/** 把教务学期编码格式化为可读标签。 */
internal fun semesterLabel(code: String): String {
    val t = code.trim()
    if (t.length == 5 && t.all { it.isDigit() }) {
        val y1 = t.substring(0, 2).toIntOrNull()
        val y2 = t.substring(2, 4).toIntOrNull()
        val s = t.substring(4).toIntOrNull()
        if (y1 != null && y2 != null && s != null) {
            val start = if (y1 < 100) 2000 + y1 else y1
            val end = if (y2 < 100) 2000 + y2 else y2
            return "${start}-${end} 第${s}学期"
        }
    }
    return t
}

/** 学期选择栏；[selectedSemester] 为 null 且 [showAllOption] 为 true 时表示「全部」。 */
@Composable
internal fun SemesterSelectBar(
    semesterOptions: List<String>,
    selectedSemester: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    showAllOption: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAllOption) {
            FilterChip(
                selected = selectedSemester == null,
                onClick = { onSelect(null) },
                shape = MaterialTheme.shapes.small,
                label = { Text("全部", style = MaterialTheme.typography.labelMedium) },
            )
        }
        semesterOptions.forEach { code ->
            FilterChip(
                selected = selectedSemester == code,
                onClick = { onSelect(code) },
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
            semesterOptions.filter { it != currentSemesterCode }
        DropdownMenuItem(
            text = { Text("当前学期") },
            leadingIcon =
                if (activeSemesterCode == null || activeSemesterCode == currentSemesterCode) {
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
