package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.OutDateStyle
import com.kizitonwose.calendar.core.daysOfWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

private val dayCellHeight = 36.dp
private const val WEEK_ROWS = 6

internal fun chineseWeekdayLabel(dayOfWeek: DayOfWeek): String =
    when (dayOfWeek) {
        DayOfWeek.MONDAY -> "一"
        DayOfWeek.TUESDAY -> "二"
        DayOfWeek.WEDNESDAY -> "三"
        DayOfWeek.THURSDAY -> "四"
        DayOfWeek.FRIDAY -> "五"
        DayOfWeek.SATURDAY -> "六"
        DayOfWeek.SUNDAY -> "日"
    }

@Composable
internal fun AppMonthCalendar(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val daysOfWeek = remember { daysOfWeek(firstDayOfWeek = DayOfWeek.MONDAY) }
    val today = remember { LocalDate.now() }
    val startMonth = remember { YearMonth.now().minusMonths(36) }
    val endMonth = remember { YearMonth.now().plusMonths(36) }
    val state =
        rememberCalendarState(
            startMonth = startMonth,
            endMonth = endMonth,
            firstVisibleMonth = YearMonth.from(selectedDate),
            firstDayOfWeek = daysOfWeek.first(),
            outDateStyle = OutDateStyle.EndOfGrid,
        )
    val scope = rememberCoroutineScope()
    val visibleMonth = state.firstVisibleMonth.yearMonth

    LaunchedEffect(selectedDate) {
        val month = YearMonth.from(selectedDate)
        if (state.firstVisibleMonth.yearMonth != month) {
            state.scrollToMonth(month)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        state.animateScrollToMonth(visibleMonth.minusMonths(1))
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "上个月",
                )
            }
            Text(
                "${visibleMonth.year}年${visibleMonth.monthValue}月",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(
                onClick = {
                    scope.launch {
                        state.animateScrollToMonth(visibleMonth.plusMonths(1))
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下个月",
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            daysOfWeek.forEach { day ->
                Text(
                    chineseWeekdayLabel(day),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
        HorizontalCalendar(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(dayCellHeight * WEEK_ROWS),
            state = state,
            calendarScrollPaged = true,
            dayContent = { day ->
                CalendarDayCell(
                    day = day,
                    selected = day.date == selectedDate,
                    today = day.date == today,
                    onClick = onDateSelected,
                )
            },
        )
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    selected: Boolean,
    today: Boolean,
    onClick: (LocalDate) -> Unit,
) {
    val inMonth = day.position == DayPosition.MonthDate
    val textColor =
        when {
            selected -> MaterialTheme.colorScheme.onPrimary
            !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            today -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(dayCellHeight)
                .padding(2.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                )
                .then(
                    if (today && !selected) {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.shapes.extraSmall,
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable { onClick(day.date) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.date.dayOfMonth.toString(),
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected || today) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
