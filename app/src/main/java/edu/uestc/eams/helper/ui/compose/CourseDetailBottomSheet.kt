package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.uestc.eams.helper.data.mapper.TimetableWeekCalendar
import edu.uestc.eams.helper.data.mapper.UestcPeriodTime
import edu.uestc.eams.helper.data.parser.WeekSpecDisplay
import edu.uestc.eams.helper.domain.model.UestcCourse
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailBottomSheet(
    course: UestcCourse,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                course.courseName.ifBlank { "未命名课程" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            CourseDetailRow(
                icon = Icons.Default.CalendarMonth,
                tint = Color(0xFFE8A84A),
                text = WeekSpecDisplay.formatForUi(course.weeks),
            )
            CourseDetailRow(
                icon = Icons.Default.Schedule,
                tint = Color(0xFFF06292),
                text = buildPeriodDetailLine(course),
            )
            val teacher = course.teacher.trim()
            if (teacher.isNotEmpty()) {
                CourseDetailRow(
                    icon = Icons.Default.Person,
                    tint = Color(0xFF5B8DEF),
                    text = teacher,
                )
            }
            val room = course.room.trim()
            if (room.isNotEmpty()) {
                CourseDetailRow(
                    icon = Icons.Default.LocationOn,
                    tint = Color(0xFF6BCB9A),
                    text = if (room.startsWith("@")) room else "@$room",
                )
            }
        }
    }
}

private fun buildPeriodDetailLine(course: UestcCourse): String {
    val day =
        TimetableWeekCalendar.dayOfWeekLabel(
            DayOfWeek.of(course.weekday.coerceIn(1, 7)),
        )
    val periodPart =
        if (course.period == course.endPeriod) {
            "第${course.period}节"
        } else {
            "第${course.period}-${course.endPeriod}节"
        }
    val time = UestcPeriodTime.timeRangeLabel(course)
    return buildString {
        append(day)
        append(" · ")
        append(periodPart)
        if (time.isNotEmpty()) {
            append(" ")
            append(time)
        }
    }
}

@Composable
private fun CourseDetailRow(
    icon: ImageVector,
    tint: Color,
    text: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text,
            modifier = Modifier.padding(start = 14.dp),
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
