package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.uestc.eams.helper.domain.grade.GradeStatsCalculator
import edu.uestc.eams.helper.domain.model.GradeItem
import java.util.Locale

@Composable
fun GradesTab(
    grades: List<GradeItem>,
    averageKeys: Set<String>,
    gpaKeys: Set<String>,
    onToggleAverage: (String) -> Unit,
    onToggleGpa: (String) -> Unit,
    onSelectAverageScope: (Collection<String>, Boolean) -> Unit,
    onSelectGpaScope: (Collection<String>, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    currentSemesterCode: String? = null,
) {
    if (grades.isEmpty()) {
        EmptyHint("暂无成绩数据\n请到课表页点刷新获取", modifier)
        return
    }
    var detailGrade by remember { mutableStateOf<GradeItem?>(null) }
    var semesterScope by remember { mutableStateOf<SemesterBarSelection>(SemesterBarSelection.All) }
    val semesterOptions = remember(grades) { semesterOptionsOf(grades) }
    val scopedGrades =
        remember(grades, semesterScope, currentSemesterCode) {
            when (val scope = semesterScope) {
                SemesterBarSelection.All -> grades
                SemesterBarSelection.Current -> {
                    val current = currentSemesterCode?.takeIf { it.isNotBlank() }
                    if (current == null) grades else grades.filter { it.semester == current }
                }
                is SemesterBarSelection.Code -> grades.filter { it.semester == scope.value }
            }
        }
    val scopedKeys = remember(scopedGrades) { scopedGrades.map { GradeStatsCalculator.stableKey(it) } }
    val avgStat =
        remember(scopedGrades, averageKeys) {
            GradeStatsCalculator.averageScore(scopedGrades, averageKeys)
        }
    val gpaStat =
        remember(scopedGrades, gpaKeys) {
            GradeStatsCalculator.averageGpa(scopedGrades, gpaKeys)
        }
    val allAvgSelected = scopedKeys.isNotEmpty() && scopedKeys.all { it in averageKeys }
    val allGpaSelected = scopedKeys.isNotEmpty() && scopedKeys.all { it in gpaKeys }
    val scopeLabel =
        when (val scope = semesterScope) {
            SemesterBarSelection.All -> null
            SemesterBarSelection.Current ->
                currentSemesterCode?.takeIf { it.isNotBlank() }?.let { semesterLabel(it) }
                    ?: "当前学期"
            is SemesterBarSelection.Code -> semesterLabel(scope.value)
        }
    val listTitle =
        when (semesterScope) {
            SemesterBarSelection.All -> "全部课程"
            SemesterBarSelection.Current -> "当前学期"
            is SemesterBarSelection.Code -> scopeLabel ?: "课程"
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            SemesterSelectBar(
                semesterOptions = semesterOptions,
                selection = semesterScope,
                onSelect = { semesterScope = it },
                showAllOption = true,
                currentSemesterCode = currentSemesterCode,
                showCurrentOption = !currentSemesterCode.isNullOrBlank(),
            )
        }
        item {
            GradeSummaryCard(
                title = scopeLabel,
                average = formatStat(avgStat.value, 1),
                gpa = formatStat(gpaStat.value, 2),
                selectedCount = avgStat.courseCount,
                totalCount = scopedKeys.size,
                allAverageSelected = allAvgSelected,
                allGpaSelected = allGpaSelected,
                onSelectAllAverage = {
                    onSelectAverageScope(scopedKeys, it)
                },
                onSelectAllGpa = {
                    onSelectGpaScope(scopedKeys, it)
                },
            )
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    listTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${scopedKeys.size} 门",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(scopedGrades, key = { GradeStatsCalculator.stableKey(it) }) { grade ->
            val key = GradeStatsCalculator.stableKey(grade)
            GradeCardRow(
                grade = grade,
                averageSelected = key in averageKeys,
                gpaSelected = key in gpaKeys,
                onToggleAverage = { onToggleAverage(key) },
                onToggleGpa = { onToggleGpa(key) },
                onClick = { detailGrade = grade },
            )
        }
    }

    detailGrade?.let { grade ->
        GradeDetailBottomSheet(grade = grade, onDismiss = { detailGrade = null })
    }
}

@Composable
private fun GradeSummaryCard(
    title: String?,
    average: String,
    gpa: String,
    selectedCount: Int,
    totalCount: Int,
    allAverageSelected: Boolean,
    allGpaSelected: Boolean,
    onSelectAllAverage: (Boolean) -> Unit,
    onSelectAllGpa: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatBlock(
                    label = if (title == null) "总均分" else "${title} 均分",
                    value = average,
                    modifier = Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .height(38.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                )
                StatBlock(
                    label = if (title == null) "总均绩" else "${title} 均绩",
                    value = gpa,
                    modifier = Modifier.weight(1f),
                    alignEnd = true,
                    valueColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            HorizontalDivider(
                Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "共 $totalCount 门 · 已计入 $selectedCount 门",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onSelectAllAverage(!allAverageSelected) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        if (allAverageSelected) "均分取消全选" else "均分全选",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                TextButton(
                    onClick = { onSelectAllGpa(!allGpaSelected) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        if (allGpaSelected) "均绩取消全选" else "均绩全选",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    valueColor: Color,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

@Composable
private fun GradeCardRow(
    grade: GradeItem,
    averageSelected: Boolean,
    gpaSelected: Boolean,
    onToggleAverage: () -> Unit,
    onToggleGpa: () -> Unit,
    onClick: () -> Unit,
) {
    val scoreText = grade.totalScoreDisplay()
    val (container, content) = scoreBadgeColors(scoreText, grade.passed)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(44.dp)
                    .height(30.dp)
                    .background(container, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    scoreText,
                    color = content,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    grade.courseName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "学分 ${grade.creditDisplay()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(4.dp))
            SelectionChip(label = "均分", selected = averageSelected, onToggle = onToggleAverage)
            Spacer(Modifier.width(4.dp))
            SelectionChip(label = "均绩", selected = gpaSelected, onToggle = onToggleGpa)
        }
    }
}

@Composable
private fun SelectionChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        shape = MaterialTheme.shapes.small,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    )
}

@Composable
private fun scoreBadgeColors(scoreText: String, passed: Boolean?): Pair<Color, Color> {
    val numeric = GradeStatsCalculator.parseScoreValue(scoreText)
    val v =
        numeric
            ?: when (scoreText.trim().lowercase()) {
                "p" -> 85.0
                "优秀", "优" -> 90.0
                "良好", "良" -> 85.0
                "中等", "中" -> 75.0
                "及格" -> 60.0
                "不及格", "挂科" -> 50.0
                else -> null
            }
    val failed = passed == false || (v != null && v < 60.0)
    val dark = isSystemInDarkTheme()
    // 相对鲜艳但不刺眼；分数徽章颜色独立于应用主题，避免换主题后成绩区间配色错乱。
    val (content, container) =
        when {
            failed || (v != null && v < 60.0) ->
                Color(0xFFD32F2F) to if (dark) Color(0xFF5A2020) else Color(0xFFFADEDE)
            v == null ->
                (if (dark) Color(0xFFB0BEC5) else Color(0xFF5B6B76)) to
                    (if (dark) Color(0xFF2C3A44) else Color(0xFFE4EBEE))
            v >= 90.0 ->
                Color(0xFF219653) to if (dark) Color(0xFF1E3B2B) else Color(0xFFCFF2DC)
            v >= 80.0 ->
                Color(0xFF1E88E5) to if (dark) Color(0xFF163252) else Color(0xFFD6E9FB)
            v >= 70.0 ->
                Color(0xFF00897B) to if (dark) Color(0xFF123B38) else Color(0xFFCFF0EE)
            v >= 60.0 ->
                Color(0xFFE8890C) to if (dark) Color(0xFF4A3410) else Color(0xFFFBE9C8)
            else ->
                Color(0xFFD32F2F) to if (dark) Color(0xFF5A2020) else Color(0xFFFADEDE)
        }
    return container to content
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeDetailBottomSheet(
    grade: GradeItem,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                grade.courseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (grade.semester.isNotBlank()) {
                Text(
                    semesterLabel(grade.semester),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (grade.scoreParts.isNotEmpty()) {
                grade.scoreParts.forEach { part ->
                    GradeDetailLine("${part.label}：${part.value}")
                }
            } else {
                GradeDetailLine("总评：${grade.totalScoreDisplay()}")
            }
            val totalDisplay = grade.totalScoreDisplay()
            val scoreText = grade.score.trim()
            if (
                scoreText.isNotEmpty() &&
                scoreText != "-" &&
                scoreText != totalDisplay &&
                grade.scoreParts.none { it.label == "总评" && it.value.trim() == scoreText }
            ) {
                GradeDetailLine("成绩：$scoreText")
            }
            GradeDetailLine("学分：${grade.credit}")
            GradeDetailLine("绩点：${grade.gradePoint}")
            if (grade.examMode.isNotBlank()) {
                GradeDetailLine("考核：${grade.examMode}")
            }
            if (grade.courseCode.isNotBlank()) {
                GradeDetailLine("课程号：${grade.courseCode}")
            }
            grade.passed?.let { ok ->
                GradeDetailLine(
                    text = if (ok) "已通过" else "未通过",
                    emphasized = ok,
                    error = !ok,
                )
            }
        }
    }
}

@Composable
private fun GradeDetailLine(
    text: String,
    emphasized: Boolean = false,
    error: Boolean = false,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color =
            when {
                error -> MaterialTheme.colorScheme.error
                emphasized -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        modifier = Modifier.padding(vertical = 3.dp),
    )
}

private fun semesterOptionsOf(grades: List<GradeItem>): List<String> =
    grades.map { it.semester }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedDescending()

private fun GradeItem.totalScoreDisplay(): String {
    val totalPart = scoreParts.firstOrNull { it.label == "总评" }?.value?.trim().orEmpty()
    if (totalPart.isNotEmpty()) return totalPart
    val scoreText = score.trim()
    if (scoreText.isNotEmpty() && scoreText != "-") return scoreText
    return "—"
}

private fun GradeItem.creditDisplay(): String {
    val creditText = credit.trim()
    if (creditText.isNotEmpty() && creditText != "-") return creditText
    return "—"
}

private fun formatStat(value: Double?, digits: Int): String =
    value?.let { String.format(Locale.CHINA, "%.${digits}f", it) } ?: "—"
