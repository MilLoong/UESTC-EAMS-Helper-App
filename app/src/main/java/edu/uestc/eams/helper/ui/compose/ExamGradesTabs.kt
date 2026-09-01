package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import edu.uestc.eams.helper.domain.model.ExamItem
import edu.uestc.eams.helper.BuildConfig
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Composable
fun ExamTab(
    exams: List<ExamItem>,
    modifier: Modifier = Modifier,
    semesterOptions: List<String> = emptyList(),
    activeSemesterCode: String? = null,
    currentSemesterCode: String? = null,
    onSemesterSelect: (String?) -> Unit = {},
) {
    if (exams.isEmpty() && semesterOptions.isEmpty()) {
        EmptyHint("暂无考试安排\n请到课表页点刷新获取", modifier)
        return
    }
    val headerSemesterLabel =
        when {
            !activeSemesterCode.isNullOrBlank() -> semesterLabel(activeSemesterCode)
            !currentSemesterCode.isNullOrBlank() -> "当前学期"
            else -> null
        }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (semesterOptions.isNotEmpty()) {
            item {
                SemesterSelectBar(
                    semesterOptions = semesterOptions,
                    selectedSemester = activeSemesterCode,
                    onSelect = onSemesterSelect,
                    showAllOption = false,
                    currentSemesterCode = currentSemesterCode,
                    showCurrentOption = true,
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "考试安排",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                headerSemesterLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (exams.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "暂无该学期考试安排",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        } else {
            items(exams, key = { it.courseName + it.examTimeText + it.room }) { exam ->
                GlassCard {
                    Text(
                        exam.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (exam.examType.isNotBlank()) {
                        Text(
                            exam.examType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    exam.examTimeText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    exam.countdownMillis()?.let { ms ->
                        // 调试期允许显示负数（已过/未来），便于核对；正式包默认只显示未来正数。
                        val show = ms >= 0 || BuildConfig.DEBUG
                        if (show) {
                            val absMs = abs(ms)
                            val days = TimeUnit.MILLISECONDS.toDays(absMs)
                            val hours = TimeUnit.MILLISECONDS.toHours(absMs) % 24
                            val minutes = TimeUnit.MILLISECONDS.toMinutes(absMs) % 60
                            val label =
                                buildString {
                                    if (days > 0) append("${days}天")
                                    if (hours > 0) append("${hours}小时")
                                    append("${minutes}分钟")
                                }
                            val prefix = if (ms < 0) "已过" else "倒计时"
                            Text(
                                "$prefix：$label",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp),
                                color =
                                    if (ms < 0) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                            )
                        }
                    }
                    val placeText =
                        if (exam.room.isBlank() || exam.room == "待定") "待定" else exam.room
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "地点：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(placeText, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (exam.seat.isNotBlank() && exam.seat != "-") {
                        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "座位：",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(exam.seat, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

/** 无数据时的居中占位。 */
@Composable
internal fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}
