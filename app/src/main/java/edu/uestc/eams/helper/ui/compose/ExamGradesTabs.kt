package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.uestc.eams.helper.domain.model.ExamItem
import java.util.concurrent.TimeUnit

@Composable
fun ExamTab(exams: List<ExamItem>, modifier: Modifier = Modifier) {
    if (exams.isEmpty()) {
        EmptyHint("暂无考试安排\n点顶栏刷新获取", modifier)
        return
    }
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(exams, key = { it.courseName + it.examTimeText + it.room }) { exam ->
            GlassCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(exam.courseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (exam.examType.isNotBlank()) {
                    Text(exam.examType, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
                Text(exam.examTimeText, modifier = Modifier.padding(top = 4.dp))
                val placeLine =
                    buildString {
                        append("地点：")
                        append(if (exam.room.isBlank() || exam.room == "待定") "待定" else exam.room)
                        if (exam.seat.isNotBlank() && exam.seat != "-") {
                            append("  座位：")
                            append(exam.seat)
                        }
                    }
                Text(placeLine, style = MaterialTheme.typography.bodySmall)
                exam.countdownMillis()?.let { ms ->
                    if (ms > 0) {
                        val days = TimeUnit.MILLISECONDS.toDays(ms)
                        val hours = TimeUnit.MILLISECONDS.toHours(ms) % 24
                        Text(
                            "倒计时：${days}天${hours}小时",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
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
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
