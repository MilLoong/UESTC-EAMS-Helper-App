package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.uestc.eams.helper.data.mapper.TimetableWeekCalendar
import edu.uestc.eams.helper.data.mapper.UestcPeriodTime
import edu.uestc.eams.helper.data.parser.AdjacentCourseMerge
import edu.uestc.eams.helper.data.parser.CourseWeekFilter
import edu.uestc.eams.helper.data.parser.WeekSpec
import edu.uestc.eams.helper.domain.model.TimetableMeta
import edu.uestc.eams.helper.domain.model.UestcCourse
import java.time.LocalDate

private val coursePalette =
    longArrayOf(
        0xFF5B8DEF,
        0xFF6BCB9A,
        0xFFE8A84A,
        0xFFB388EB,
        0xFF4ECDC4,
        0xFFFF8A80,
        0xFF81C784,
        0xFF64B5F6,
    )

private val timeColumnWidth = 38.dp
private val rowHeight = 50.dp
private val dayHeaderHeight = 36.dp
private const val TIMETABLE_PAGE_COUNT = 30

@Composable
fun ScheduleTab(
    courses: List<UestcCourse>,
    timetableMeta: TimetableMeta?,
    modifier: Modifier = Modifier,
    onPrevWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onWeekSelected: (Int) -> Unit = {},
    onGoCurrentWeek: () -> Unit = {},
) {
    val today = LocalDate.now()
    val currentWeek = timetableMeta?.currentWeek ?: 1
    val displayWeek = timetableMeta?.displayWeek ?: currentWeek
    val weekOneMonday = timetableMeta?.weekOneMondayDate()

    if (courses.isEmpty() && timetableMeta == null) {
        EmptyHint(
            "暂无课表\n登录或 Web [导入会话] 后点 [刷新]\n或顶栏 [导入] WakeUp 树维导出的 HTML",
            modifier,
        )
        return
    }

    val pageCount =
        remember(courses) {
            courses
                .maxOfOrNull { WeekSpec.maxWeekNumber(it.weeks) }
                ?.coerceIn(1, TIMETABLE_PAGE_COUNT)
                ?: TIMETABLE_PAGE_COUNT
        }
    val initialPage = (displayWeek - 1).coerceIn(0, pageCount - 1)
    val pagerState =
        rememberPagerState(
            initialPage = initialPage,
            pageCount = { pageCount },
        )
    val headerWeek by remember(pageCount) {
        derivedStateOf {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction + 1f)
                .roundToInt()
                .coerceIn(1, pageCount)
        }
    }
    val weekMonday =
        remember(headerWeek, currentWeek) {
            TimetableWeekCalendar.mondayOfDisplayedWeek(
                headerWeek,
                currentWeek,
                today,
                weekOneMonday,
            )
        }
    val isCurrentWeek = headerWeek == currentWeek
    val weekRangeSubtitle =
        TimetableWeekCalendar.formatHeaderDate(weekMonday) +
            " - " +
            TimetableWeekCalendar.formatHeaderDate(weekMonday.plusDays(6))
    var syncingPagerFromModel by remember { mutableStateOf(false) }

    LaunchedEffect(displayWeek) {
        val target = (displayWeek - 1).coerceIn(0, pageCount - 1)
        if (pagerState.currentPage == target) return@LaunchedEffect
        syncingPagerFromModel = true
        try {
            pagerState.animateScrollToPage(target)
        } finally {
            syncingPagerFromModel = false
        }
    }

    LaunchedEffect(pagerState, displayWeek) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val week = page + 1
                if (!syncingPagerFromModel && week != displayWeek) {
                    onWeekSelected(week)
                }
            }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevWeek, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "上一周",
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "第${headerWeek}周",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                    )
                    if (isCurrentWeek) {
                        Text(
                            "本周",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    } else {
                        Text(
                            "回本周",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier
                                    .padding(start = 6.dp)
                                    .clickable(onClick = onGoCurrentWeek),
                        )
                    }
                }
                Text(
                    weekRangeSubtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                )
            }
            IconButton(onClick = onNextWeek, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下一周",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        HorizontalDivider()
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1,
        ) { page ->
            ScheduleWeekPage(
                weekNumber = page + 1,
                courses = courses,
                currentWeek = currentWeek,
                today = today,
                weekOneMonday = weekOneMonday,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ScheduleWeekPage(
    weekNumber: Int,
    courses: List<UestcCourse>,
    currentWeek: Int,
    today: LocalDate,
    weekOneMonday: LocalDate?,
    modifier: Modifier = Modifier,
) {
    val weekMonday =
        remember(weekNumber, currentWeek, weekOneMonday) {
            TimetableWeekCalendar.mondayOfDisplayedWeek(
                weekNumber,
                currentWeek,
                today,
                weekOneMonday,
            )
        }
    val visible =
        remember(courses, weekNumber) {
            AdjacentCourseMerge.merge(CourseWeekFilter.filterForWeek(courses, weekNumber))
        }
    val byDay = remember(visible) { visible.groupBy { it.weekday } }
    val colorByKey =
        remember(visible) {
            val map = mutableMapOf<String, Long>()
            visible.forEachIndexed { i, c ->
                val key = c.courseId.ifBlank { c.courseName }
                map.putIfAbsent(key, coursePalette[i % coursePalette.size])
            }
            map
        }
    val vScroll = remember(weekNumber) { ScrollState(0) }
    val gridHeight = rowHeight * UestcPeriodTime.maxPeriod

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(dayHeaderHeight)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(timeColumnWidth))
            for (d in 1..7) {
                val date = TimetableWeekCalendar.dateForWeekday(weekMonday, d)
                DayHeaderCell(
                    dayLabel = TimetableWeekCalendar.shortDayLabel(d),
                    dateLabel = "${date.dayOfMonth}",
                    isToday = date == today,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider()
        Row(
            Modifier
                .weight(1f)
                .verticalScroll(vScroll),
        ) {
            Column(
                Modifier
                    .width(timeColumnWidth)
                    .height(gridHeight)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
            ) {
                UestcPeriodTime.slots.forEach { slot ->
                    PeriodLabelCell(slot.index, slot.start, slot.end, rowHeight)
                }
            }
            Row(Modifier.height(gridHeight).weight(1f)) {
                for (d in 1..7) {
                    DayColumn(
                        courses = byDay[d].orEmpty(),
                        rowHeight = rowHeight,
                        gridHeight = gridHeight,
                        colorByKey = colorByKey,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodLabelCell(
    periodIndex: Int,
    start: String,
    end: String,
    rowHeight: Dp,
) {
    Column(
        Modifier
            .height(rowHeight)
            .fillMaxWidth()
            .padding(horizontal = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$periodIndex",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        Text(
            start,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Text(
            end,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun DayHeaderCell(
    dayLabel: String,
    dateLabel: String,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            dayLabel,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        if (isToday) {
            Box(
                Modifier
                    .padding(top = 1.dp)
                    .size(width = 22.dp, height = 18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    dateLabel,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                )
            }
        } else {
            Text(
                dateLabel,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun DayColumn(
    courses: List<UestcCourse>,
    rowHeight: Dp,
    gridHeight: Dp,
    colorByKey: Map<String, Long>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Box(
        modifier
            .height(gridHeight)
            .padding(horizontal = 1.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            repeat(UestcPeriodTime.maxPeriod) {
                Box(
                    Modifier
                        .height(rowHeight)
                        .fillMaxWidth(),
                ) {
                    HorizontalDivider(
                        Modifier.align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    )
                }
            }
        }
        courses.forEach { course ->
            val span = (course.endPeriod - course.period + 1).coerceAtLeast(1)
            val topPx = with(density) { rowHeight.toPx() * (course.period - 1) }
            val heightPx = with(density) { rowHeight.toPx() * span }
            val colorKey = course.courseId.ifBlank { course.courseName }
            val bg = Color(colorByKey[colorKey] ?: coursePalette[0]).copy(alpha = 0.9f)
            CourseCard(
                course = course,
                periodSpan = span,
                background = bg,
                modifier =
                    Modifier
                        .offset(y = with(density) { topPx.toDp() })
                        .height(with(density) { heightPx.toDp() })
                        .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CourseCard(
    course: UestcCourse,
    periodSpan: Int,
    background: Color,
    modifier: Modifier = Modifier,
) {
    val startTime = UestcPeriodTime.resolvedStartTime(course)
    val endTime = UestcPeriodTime.resolvedEndTime(course)
    val showLabeledTime = periodSpan >= 2
    Column(
        modifier =
            modifier
                .padding(horizontal = 1.dp, vertical = 1.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(background)
                .padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
        Text(
            course.courseName,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = if (periodSpan <= 1) 2 else 3,
            overflow = TextOverflow.Ellipsis,
            color = Color.White,
            lineHeight = 9.sp,
        )
        if (startTime.isNotEmpty() || endTime.isNotEmpty()) {
            CourseTimeBlock(
                startTime = startTime,
                endTime = endTime,
                labeled = showLabeledTime,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        val loc = course.room.trim()
        if (loc.isNotEmpty()) {
            Text(
                loc,
                fontSize = 7.sp,
                lineHeight = 8.sp,
                maxLines = if (periodSpan <= 1) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun CourseTimeBlock(
    startTime: String,
    endTime: String,
    labeled: Boolean,
    modifier: Modifier = Modifier,
) {
    val labelColor = Color.White.copy(alpha = 0.82f)
    val valueColor = Color.White.copy(alpha = 0.95f)
    Column(modifier) {
        if (labeled) {
            if (startTime.isNotEmpty()) {
                Text("开始", fontSize = 6.sp, lineHeight = 7.sp, color = labelColor)
                Text(
                    startTime,
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    fontWeight = FontWeight.Medium,
                    color = valueColor,
                    maxLines = 1,
                )
            }
            if (endTime.isNotEmpty()) {
                Text(
                    "结束",
                    fontSize = 6.sp,
                    lineHeight = 7.sp,
                    color = labelColor,
                    modifier = Modifier.padding(top = 1.dp),
                )
                Text(
                    endTime,
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    fontWeight = FontWeight.Medium,
                    color = valueColor,
                    maxLines = 1,
                )
            }
        } else {
            if (startTime.isNotEmpty()) {
                Text(startTime, fontSize = 7.sp, lineHeight = 8.sp, color = valueColor, maxLines = 1)
            }
            if (endTime.isNotEmpty() && endTime != startTime) {
                Text(endTime, fontSize = 7.sp, lineHeight = 8.sp, color = valueColor, maxLines = 1)
            }
        }
    }
}
