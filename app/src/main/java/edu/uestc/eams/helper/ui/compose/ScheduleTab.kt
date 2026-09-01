package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import edu.uestc.eams.helper.data.mapper.TimetableWeekCalendar
import edu.uestc.eams.helper.data.mapper.UestcPeriodTime
import edu.uestc.eams.helper.data.parser.AdjacentCourseMerge
import edu.uestc.eams.helper.data.parser.CourseWeekFilter
import edu.uestc.eams.helper.data.parser.PeriodOverlapResolver
import edu.uestc.eams.helper.data.parser.WeekSpec
import edu.uestc.eams.helper.data.prefs.TimetableCourseNameMode
import edu.uestc.eams.helper.data.prefs.TimetableLayoutSettings
import edu.uestc.eams.helper.domain.model.TimetableMeta
import edu.uestc.eams.helper.domain.model.UestcCourse
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 课表块深浅两色，每两节一组交替。 */
@Composable
private fun courseBlockColors(startPeriod: Int): Pair<Color, Color> {
    val useLightShade = ((startPeriod - 1) / 2) % 2 == 1
    return if (useLightShade) {
        // 淡色块取自 primary 与 primaryContainer 的中间调，避免和页面背景融为一体。
        val lightBg =
            lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, 0.5f)
        lightBg to MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }
}

private val dayHeaderHeight = 36.dp
private val weekStateLabelWidth = 48.dp

/** 单次双指手势允许的整体缩放范围，配合 TimetableLayoutSettings 的上下限共同生效。 */
private const val MIN_GRID_ZOOM = 0.6f
private const val MAX_GRID_ZOOM = 1.7f

/** 精简版课程名最多 4 个字；允许换行，保证在窄卡片里也能完整显示。 */
private const val COMPACT_NAME_MAX_LINES = 2

/** 周选择列表至少提供的范围（常见学期长度）。 */
private const val WEEK_PICKER_MIN_RANGE = 20

/** 固定可滑动周次下限；不按课程周次字段上限截断，避免排到第 16 周就无法往后浏览。 */
private const val TIMETABLE_PAGE_COUNT = 30

private fun scaledSp(base: Float, fontScale: Float) = (base * fontScale).sp

/** 网纹背景：按节次行高/列宽叠加淡灰色横竖线，不改变配色。 */
@Composable
private fun Modifier.timetableGridMesh(
    show: Boolean,
    rowHeight: Dp,
    columnWidth: Dp,
): Modifier {
    if (!show) return this
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    return drawBehind {
        val rowPx = rowHeight.toPx()
        val colPx = columnWidth.toPx()
        var y = rowPx
        while (y < size.height) {
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            y += rowPx
        }
        var x = colPx
        while (x < size.width) {
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            x += colPx
        }
    }
}

@Composable
private fun Modifier.timetableNoonDivider(show: Boolean, rowHeight: Dp): Modifier {
    if (!show) return this
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    return drawBehind {
        val y = rowHeight.toPx() * UestcPeriodTime.NOON_DIVIDER_AFTER_PERIOD
        drawLine(
            color = dividerColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

/** 中午分隔条：绘制在课程块之上，且上下各留一段背景色空隙，避免被卡片盖住而看不清。 */
@Composable
private fun Modifier.timetableNoonSeparator(show: Boolean, rowHeight: Dp): Modifier {
    if (!show) return this
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f)
    val bandColor = MaterialTheme.colorScheme.background
    return drawWithContent {
        drawContent()
        val y = rowHeight.toPx() * UestcPeriodTime.NOON_DIVIDER_AFTER_PERIOD
        val band = 4.dp.toPx()
        val line = 2.dp.toPx()
        drawRect(bandColor, topLeft = Offset(0f, y - band / 2f), size = Size(size.width, band))
        drawLine(dividerColor, Offset(0f, y), Offset(size.width, y), strokeWidth = line)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTab(
    courses: List<UestcCourse>,
    timetableMeta: TimetableMeta?,
    layout: TimetableLayoutSettings,
    contentLoading: Boolean = false,
    semesterOptions: List<String> = emptyList(),
    activeSemesterCode: String? = null,
    currentSemesterCode: String? = null,
    isCurrentSemester: Boolean = true,
    onSemesterSelect: (String?) -> Unit = {},
    pagerScrollWeek: Int? = null,
    modifier: Modifier = Modifier,
    onPrevWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onGoCurrentWeek: () -> Unit = {},
    onSelectWeek: (Int) -> Unit = {},
    onPagerScrollConsumed: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onLayoutChange: (TimetableLayoutSettings) -> Unit = {},
) {
    val today = LocalDate.now()
    val shellMeta =
        timetableMeta
            ?: TimetableMeta(
                semesterCode = "",
                currentWeek = 1,
                displayWeek = 1,
            )
    val currentWeek = shellMeta.currentWeek
    val displayWeek = shellMeta.displayWeek.coerceAtLeast(1)
    val weekOneMonday = shellMeta.weekOneMondayDate()
    val showSetupHint = timetableMeta == null && courses.isEmpty()
    var detailCourse by remember { mutableStateOf<UestcCourse?>(null) }
    var gridPinching by remember { mutableStateOf(false) }
    var weekMenuExpanded by remember { mutableStateOf(false) }
    val showResetGridFab = !layout.isDefaultGridSize()
    val maxWeek =
        remember(courses, currentWeek, displayWeek) {
            val fromCourses =
                courses.mapNotNull { runCatching { WeekSpec.maxWeekNumber(it.weeks) }.getOrNull() }
                    .maxOrNull()
                    ?: 1
            maxOf(fromCourses, currentWeek, displayWeek).coerceAtLeast(WEEK_PICKER_MIN_RANGE)
        }
    val pageCount = maxOf(maxWeek, TIMETABLE_PAGE_COUNT)
    val initialPage = (displayWeek - 1).coerceIn(0, pageCount - 1)
    val pagerState =
        rememberPagerState(
            initialPage = initialPage,
            pageCount = { pageCount },
        )
    val pagerScope = rememberCoroutineScope()
    // 滑动过程中周次跟随偏移，恢复 1.2.x 丝滑切周的标题反馈。
    val headerWeek by remember(pageCount) {
        derivedStateOf {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction + 1f)
                .roundToInt()
                .coerceIn(1, pageCount)
        }
    }
    val weekMonday =
        remember(headerWeek, currentWeek, weekOneMonday) {
            TimetableWeekCalendar.mondayOfDisplayedWeek(
                headerWeek,
                currentWeek,
                today,
                weekOneMonday,
            )
        }
    val isCurrentWeek =
        remember(weekMonday, today) {
            TimetableWeekCalendar.containsDate(weekMonday, today)
        }
    val weekRangeSubtitle =
        TimetableWeekCalendar.formatHeaderDate(weekMonday) +
            " - " +
            TimetableWeekCalendar.formatHeaderDate(weekMonday.plusDays(6))

    LaunchedEffect(pagerScrollWeek, pageCount) {
        val week = pagerScrollWeek ?: return@LaunchedEffect
        val target = (week - 1).coerceIn(0, pageCount - 1)
        if (pagerState.currentPage != target || pagerState.targetPage != target) {
            pagerState.animateScrollToPage(target)
        }
        onPagerScrollConsumed()
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                onSelectWeek(page + 1)
            }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (semesterOptions.isNotEmpty()) {
                SemesterMenuButton(
                    semesterOptions = semesterOptions,
                    activeSemesterCode = activeSemesterCode,
                    onSelect = onSemesterSelect,
                    currentSemesterCode = currentSemesterCode,
                )
            }
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
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    val showWeekState = isCurrentSemester && !isCurrentWeek
                    if (showWeekState) {
                        Spacer(Modifier.width(weekStateLabelWidth))
                    }
                    Box {
                        Text(
                            "第${headerWeek}周",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier =
                                Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .clickable { weekMenuExpanded = true }
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                        DropdownMenu(
                            expanded = weekMenuExpanded,
                            onDismissRequest = { weekMenuExpanded = false },
                        ) {
                            val maxMenuHeightDp =
                                (LocalConfiguration.current.screenHeightDp / 2).coerceAtLeast(240)
                            Column(
                                Modifier
                                    .heightIn(max = maxMenuHeightDp.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                for (week in 1..maxWeek) {
                                    DropdownMenuItem(
                                        text = { Text("第${week}周") },
                                        leadingIcon =
                                            if (week == headerWeek) {
                                                { Icon(Icons.Filled.Check, contentDescription = null) }
                                            } else {
                                                null
                                            },
                                        onClick = {
                                            weekMenuExpanded = false
                                            val target = (week - 1).coerceIn(0, pageCount - 1)
                                            pagerScope.launch {
                                                pagerState.animateScrollToPage(target)
                                            }
                                            onSelectWeek(week)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (showWeekState) {
                        WeekStateLabel(onGoCurrentWeek = onGoCurrentWeek)
                    }
                }
                if (isCurrentSemester) {
                    Text(
                        weekRangeSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onNextWeek, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下一周",
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(
                onClick = onRefresh,
                enabled = !contentLoading,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "刷新",
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            // HorizontalPager 提供丝滑切周；页内放大后横滑到边缘，剩余位移交给 Pager。
            // 双指缩放时关闭 Pager 用户滑动，避免与 pinch 冲突。
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !gridPinching,
            ) { page ->
                ScheduleWeekPage(
                    weekNumber = page + 1,
                    courses = courses,
                    currentWeek = currentWeek,
                    today = today,
                    weekOneMonday = weekOneMonday,
                    gridLayout = layout,
                    isCurrentSemester = isCurrentSemester,
                    courseClicksEnabled = !gridPinching,
                    onCourseClick = { detailCourse = it },
                    onGridPinchCommit = { factor ->
                        if (abs(factor - 1f) > 0.01f) {
                            onLayoutChange(layout.scaledGridBy(factor))
                        }
                    },
                    onGridPinchingChange = { gridPinching = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (showSetupHint) {
                EmptyHint(
                    "暂无课表\n请到「我的」页登录、网页登录或导入课表文件",
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
            if (showResetGridFab) {
                FloatingActionButton(
                    onClick = {
                        gridPinching = false
                        onLayoutChange(layout.resetGridSizeFrom(layout))
                    },
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = "恢复原始大小",
                    )
                }
            }
        }
        detailCourse?.let { course ->
            CourseDetailBottomSheet(
                course = course,
                onDismiss = { detailCourse = null },
            )
        }
    }
}

@Composable
private fun WeekStateLabel(onGoCurrentWeek: () -> Unit) {
    Box(
        Modifier.width(weekStateLabelWidth),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "回本周",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(onClick = onGoCurrentWeek),
        )
    }
}

@Composable
private fun ScheduleWeekPage(
    weekNumber: Int,
    courses: List<UestcCourse>,
    currentWeek: Int,
    today: LocalDate,
    weekOneMonday: LocalDate?,
    gridLayout: TimetableLayoutSettings,
    isCurrentSemester: Boolean = true,
    courseClicksEnabled: Boolean = true,
    onCourseClick: (UestcCourse) -> Unit,
    onGridPinchCommit: (Float) -> Unit,
    onGridPinchingChange: (Boolean) -> Unit,
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
            val filtered = CourseWeekFilter.filterForWeek(courses, weekNumber)
            // 先合并真正相邻的同名课；再裁掉叠在一起的节次，避免深色底拖到下一截课下面。
            PeriodOverlapResolver.resolve(AdjacentCourseMerge.merge(filtered))
        }
    val byDay = remember(visible) { visible.groupBy { it.weekday } }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    // 双指缩放改行高/列宽，卡片随格子拉长缩短；松手后写入持久化设置。
    // 缩小时不低于可视区域铺满，避免网格旁边空出一大块。
    var gestureScale by remember { mutableFloatStateOf(1f) }
    val displayLayout =
        remember(gridLayout, gestureScale) {
            if (gestureScale == 1f) gridLayout else gridLayout.scaledGridBy(gestureScale)
        }
    val latestOnGridPinchCommit by rememberUpdatedState(onGridPinchCommit)
    val latestOnGridPinchingChange by rememberUpdatedState(onGridPinchingChange)

    BoxWithConstraints(modifier) {
        val timeColumnWidth = gridLayout.timeColumnWidthDp.dp
        val daysAvailWidth = (maxWidth - timeColumnWidth).coerceAtLeast(0.dp)
        val bodyAvailHeight = (maxHeight - dayHeaderHeight).coerceAtLeast(0.dp)
        val fillDayWidth = daysAvailWidth / 7
        val fillRowHeight = bodyAvailHeight / UestcPeriodTime.maxPeriod
        val dayColumnWidth = max(displayLayout.dayColumnWidthDp.dp, fillDayWidth)
        val rowHeight = max(displayLayout.rowHeightDp.dp, fillRowHeight)
        val fontScale = displayLayout.fontScale
        val gridHeight = rowHeight * UestcPeriodTime.maxPeriod
        val dayGridWidth = dayColumnWidth * 7
        // 未放大（铺满宽度）时不挂横滑，手势直接给外层 Pager，手感与 1.2.x 一致；
        // 放大后先页内横滑，到边缘再由 nested scroll 交给 Pager。
        val canScrollHorizontally = dayGridWidth > daysAvailWidth + 0.5.dp

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(dayHeaderHeight)
                    .background(MaterialTheme.colorScheme.surface),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(timeColumnWidth))
                Row(
                    Modifier
                        .weight(1f)
                        .then(
                            if (canScrollHorizontally) {
                                Modifier.horizontalScroll(hScroll)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Row(Modifier.width(dayGridWidth)) {
                        for (d in 1..7) {
                            val date = TimetableWeekCalendar.dateForWeekday(weekMonday, d)
                            DayHeaderCell(
                                dayLabel = TimetableWeekCalendar.shortDayLabel(d),
                                dateLabel = "${date.dayOfMonth}",
                                isToday = date == today,
                                showDate = isCurrentSemester,
                                fontScale = fontScale,
                                modifier = Modifier.width(dayColumnWidth),
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            Box(Modifier.weight(1f)) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(timeColumnWidth)
                            .verticalScroll(vScroll),
                    ) {
                        Box(
                            Modifier
                                .height(gridHeight)
                                .background(MaterialTheme.colorScheme.surface)
                                .timetableNoonDivider(gridLayout.showNoonDivider, rowHeight),
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                UestcPeriodTime.slots.forEach { slot ->
                                    PeriodLabelCell(
                                        slot.index,
                                        slot.start,
                                        slot.end,
                                        rowHeight,
                                        fontScale,
                                    )
                                }
                            }
                        }
                    }
                    // 页内横滑；到边缘后剩余位移交给外层 HorizontalPager 丝滑切周。
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (canScrollHorizontally) {
                                    Modifier.horizontalScroll(hScroll)
                                } else {
                                    Modifier
                                },
                            )
                            .verticalScroll(vScroll),
                    ) {
                        Box(
                            Modifier
                                .width(dayGridWidth)
                                .height(gridHeight)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        var lastDistance = 0f
                                        var pinching = false
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            if (pressed.size >= 2) {
                                                if (!pinching) {
                                                    pinching = true
                                                    latestOnGridPinchingChange(true)
                                                }
                                                val a = pressed[0].position
                                                val b = pressed[1].position
                                                val dx = a.x - b.x
                                                val dy = a.y - b.y
                                                val distance = sqrt(dx * dx + dy * dy)
                                                if (lastDistance > 0f && distance > 0f) {
                                                    val change = distance / lastDistance
                                                    if (change.isFinite()) {
                                                        gestureScale =
                                                            (gestureScale * change).coerceIn(
                                                                MIN_GRID_ZOOM,
                                                                MAX_GRID_ZOOM,
                                                            )
                                                    }
                                                }
                                                lastDistance = distance
                                                pressed.forEach { it.consume() }
                                            } else {
                                                lastDistance = 0f
                                            }
                                            if (event.changes.none { it.pressed }) break
                                        }
                                        if (pinching) {
                                            latestOnGridPinchingChange(false)
                                            val factor = gestureScale
                                            gestureScale = 1f
                                            latestOnGridPinchCommit(factor)
                                        }
                                    }
                                }
                                .timetableGridMesh(gridLayout.gridMesh, rowHeight, dayColumnWidth)
                                .timetableNoonSeparator(gridLayout.showNoonDivider, rowHeight),
                        ) {
                            Row(Modifier.fillMaxSize()) {
                                for (d in 1..7) {
                                    DayColumn(
                                        courses = byDay[d].orEmpty(),
                                        rowHeight = rowHeight,
                                        gridHeight = gridHeight,
                                        fontScale = fontScale,
                                        courseNameMode = gridLayout.courseNameMode,
                                        courseCardBorder = gridLayout.courseCardBorder,
                                        courseClicksEnabled = courseClicksEnabled,
                                        onCourseClick = onCourseClick,
                                        modifier = Modifier.width(dayColumnWidth),
                                    )
                                }
                            }
                        }
                    }
                }
                if (visible.isEmpty()) {
                    Text(
                        "本周无课程",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.align(Alignment.Center),
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
    fontScale: Float,
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
            fontSize = scaledSp(14f, fontScale),
            fontWeight = FontWeight.Bold,
            lineHeight = scaledSp(15f, fontScale),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        Text(
            start,
            fontSize = scaledSp(9f, fontScale),
            lineHeight = scaledSp(10f, fontScale),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Text(
            end,
            fontSize = scaledSp(9f, fontScale),
            lineHeight = scaledSp(10f, fontScale),
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
    showDate: Boolean,
    fontScale: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            dayLabel,
            fontSize = scaledSp(10f, fontScale),
            lineHeight = scaledSp(11f, fontScale),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        if (showDate) {
            if (isToday) {
                Box(
                    Modifier
                        .padding(top = 1.dp)
                        .size(width = 22.dp, height = 18.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        dateLabel,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = scaledSp(10f, fontScale),
                        lineHeight = scaledSp(11f, fontScale),
                    )
                }
            } else {
                Text(
                    dateLabel,
                    fontSize = scaledSp(10f, fontScale),
                    lineHeight = scaledSp(11f, fontScale),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    courses: List<UestcCourse>,
    rowHeight: Dp,
    gridHeight: Dp,
    fontScale: Float,
    courseNameMode: TimetableCourseNameMode,
    courseCardBorder: Boolean,
    courseClicksEnabled: Boolean,
    onCourseClick: (UestcCourse) -> Unit,
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
            val (background, contentColor) = courseBlockColors(course.period)
            CourseCard(
                course = course,
                periodSpan = span,
                background = background,
                contentColor = contentColor,
                fontScale = fontScale,
                courseNameMode = courseNameMode,
                courseCardBorder = courseCardBorder,
                clicksEnabled = courseClicksEnabled,
                onClick = { onCourseClick(course) },
                modifier =
                    Modifier
                        .offset(y = with(density) { topPx.toDp() })
                        .height(with(density) { heightPx.toDp() })
                        .fillMaxWidth(),
            )
        }
    }
}

private fun courseCardNameMaxLines(periodSpan: Int): Int =
    (periodSpan * 2 + 1).coerceIn(2, 10)

private fun courseCardRoomMaxLines(periodSpan: Int): Int =
    (periodSpan + 2).coerceIn(2, 8)

private fun courseCardTeacherMaxLines(periodSpan: Int): Int =
    periodSpan.coerceIn(1, 4)

@Composable
private fun CourseCard(
    course: UestcCourse,
    periodSpan: Int,
    background: Color,
    contentColor: Color,
    fontScale: Float,
    courseNameMode: TimetableCourseNameMode,
    courseCardBorder: Boolean,
    clicksEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionModifier =
        if (clicksEnabled) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }
    val borderModifier =
        if (courseCardBorder) {
            Modifier.border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                MaterialTheme.shapes.extraSmall,
            )
        } else {
            Modifier
        }
    Column(
        modifier =
            modifier
                .padding(horizontal = 1.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(background)
                .then(borderModifier)
                .then(interactionModifier)
                .padding(horizontal = 3.dp, vertical = 3.dp),
    ) {
        val displayName = courseNameMode.displayOnCard(course.courseName)
        if (courseNameMode == TimetableCourseNameMode.COMPACT) {
            // 精简版固定取 COMPACT_CHAR_COUNT 个字。之前用 maxLines = 1 + Clip，
            // 在窄列里会把最后一个字裁掉（看起来只显示前三个字）。
            // 与完整版一样允许换行，确保 4 个字始终完整呈现。
            Text(
                displayName,
                fontSize = scaledSp(10f, fontScale),
                fontWeight = FontWeight.SemiBold,
                maxLines = COMPACT_NAME_MAX_LINES,
                overflow = TextOverflow.Clip,
                color = contentColor,
                lineHeight = scaledSp(11f, fontScale),
            )
        } else {
            Text(
                displayName,
                fontSize = scaledSp(10f, fontScale),
                fontWeight = FontWeight.SemiBold,
                maxLines = courseCardNameMaxLines(periodSpan),
                overflow = TextOverflow.Clip,
                color = contentColor,
                lineHeight = scaledSp(11f, fontScale),
            )
        }
        val loc = course.room.trim()
        if (loc.isNotEmpty()) {
            val locText = if (loc.startsWith("@")) loc else "@$loc"
            Text(
                locText,
                fontSize = scaledSp(8.5f, fontScale),
                lineHeight = scaledSp(9.5f, fontScale),
                maxLines = courseCardRoomMaxLines(periodSpan),
                overflow = TextOverflow.Clip,
                color = contentColor.copy(alpha = 0.88f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        val teacher = course.teacher.trim()
        if (teacher.isNotEmpty()) {
            Text(
                teacher,
                fontSize = scaledSp(8f, fontScale),
                lineHeight = scaledSp(9f, fontScale),
                maxLines = courseCardTeacherMaxLines(periodSpan),
                overflow = TextOverflow.Clip,
                color = contentColor.copy(alpha = 0.82f),
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}
