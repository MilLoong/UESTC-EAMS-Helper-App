package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onSelectAllAverage: (Boolean) -> Unit,
    onSelectAllGpa: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (grades.isEmpty()) {
        EmptyHint("暂无成绩数据\n点顶栏刷新获取", modifier)
        return
    }
    val keys = remember(grades) { grades.map { GradeStatsCalculator.stableKey(it) } }
    val avgStat = remember(grades, averageKeys) { GradeStatsCalculator.averageScore(grades, averageKeys) }
    val gpaStat = remember(grades, gpaKeys) { GradeStatsCalculator.averageGpa(grades, gpaKeys) }
    val allAvgSelected = keys.isNotEmpty() && keys.all { it in averageKeys }
    val allGpaSelected = keys.isNotEmpty() && keys.all { it in gpaKeys }

    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            GlassCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "均分 ${formatStat(avgStat.value, 1)}（${avgStat.courseCount} 门，${formatStat(avgStat.creditSum, 1)} 学分）",
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "均绩 ${formatStat(gpaStat.value, 2)}（${gpaStat.courseCount} 门，${formatStat(gpaStat.creditSum, 1)} 学分）",
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "按学分加权；非数字成绩或绩点不计入。均分与均绩可分别勾选课程。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onSelectAllAverage(!allAvgSelected) }) {
                        Text(if (allAvgSelected) "均分：取消全选" else "均分：全选")
                    }
                    TextButton(onClick = { onSelectAllGpa(!allGpaSelected) }) {
                        Text(if (allGpaSelected) "均绩：取消全选" else "均绩：全选")
                    }
                }
            }
        }
        items(grades, key = { GradeStatsCalculator.stableKey(it) }) { g ->
            val key = GradeStatsCalculator.stableKey(g)
            GlassCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(g.courseName, fontWeight = FontWeight.SemiBold)
                val meta =
                    listOfNotNull(
                        g.semester.takeIf { it.isNotBlank() },
                        g.courseType.takeIf { it.isNotBlank() },
                        g.necessary.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
                Text(
                    "成绩 ${g.score}  学分 ${g.credit}  绩点 ${g.gradePoint}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (g.examMode.isNotBlank()) {
                    Text("考核：${g.examMode}", style = MaterialTheme.typography.bodySmall)
                }
                g.passed?.let { ok ->
                    Text(
                        if (ok) "已通过" else "未通过",
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (ok) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = key in averageKeys,
                            onCheckedChange = { onToggleAverage(key) },
                        )
                        Text("均分", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = key in gpaKeys,
                            onCheckedChange = { onToggleGpa(key) },
                        )
                        Text("均绩", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun formatStat(value: Double?, digits: Int): String =
    value?.let { String.format(Locale.CHINA, "%.${digits}f", it) } ?: "—"
