package edu.uestc.eams.helper.data.mapper

import edu.uestc.eams.helper.domain.model.UestcCourse
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 本校常见上课节次与起止时间（清水河校区默认）。
 * 下标 0 对应第 1 节，依此类推至第 12 节。
 */
object UestcPeriodTime {

    data class PeriodSlot(val index: Int, val start: String, val end: String)

    val slots: List<PeriodSlot> =
        listOf(
            PeriodSlot(1, "08:30", "09:15"),
            PeriodSlot(2, "09:20", "10:05"),
            PeriodSlot(3, "10:20", "11:05"),
            PeriodSlot(4, "11:10", "11:55"),
            PeriodSlot(5, "14:00", "14:45"),
            PeriodSlot(6, "14:50", "15:35"),
            PeriodSlot(7, "16:00", "16:45"),
            PeriodSlot(8, "16:50", "17:35"),
            PeriodSlot(9, "19:00", "19:45"),
            PeriodSlot(10, "19:50", "20:35"),
            PeriodSlot(11, "20:40", "21:25"),
            PeriodSlot(12, "21:30", "22:15"),
        )

    val maxPeriod: Int get() = slots.size

    private val timeFmt = DateTimeFormatter.ofPattern("H:mm")

    fun weekAnchor(today: LocalDate = LocalDate.now()): LocalDate = today.with(DayOfWeek.MONDAY)

    fun startEpochSec(course: UestcCourse, weekAnchor: LocalDate = weekAnchor()): Long {
        val day = weekAnchor.plusDays((course.weekday - 1).coerceIn(0, 6).toLong())
        val time =
            parseClock(course.startTime)
                ?: slots.getOrNull(course.period - 1)?.start?.let { parseClock(it) }
                ?: LocalTime.of(8, 30)
        return day.atTime(time).atZone(ZoneId.systemDefault()).toEpochSecond()
    }

    fun endEpochSec(course: UestcCourse, weekAnchor: LocalDate = weekAnchor()): Long {
        val day = weekAnchor.plusDays((course.weekday - 1).coerceIn(0, 6).toLong())
        val time =
            parseClock(course.endTime)
                ?: slots.getOrNull(course.endPeriod - 1)?.end?.let { parseClock(it) }
                ?: parseClock(course.startTime)?.plusMinutes(
                    ((course.endPeriod - course.period + 1) * 50L).coerceAtLeast(45),
                )
                ?: LocalTime.of(9, 15)
        return day.atTime(time).atZone(ZoneId.systemDefault()).toEpochSecond()
    }

    fun durationSec(course: UestcCourse, weekAnchor: LocalDate = weekAnchor()): Int {
        val end = endEpochSec(course, weekAnchor)
        val start = startEpochSec(course, weekAnchor)
        return (end - start).toInt().coerceAtLeast(45 * 60)
    }

    fun periodLabel(period: Int): String {
        val slot = slots.getOrNull(period - 1) ?: return "$period"
        return "${slot.index}\n${slot.start}-${slot.end}"
    }

    fun resolvedStartTime(course: UestcCourse): String {
        val fromApi = course.startTime.trim()
        if (fromApi.isNotEmpty()) return fromApi
        return slots.getOrNull(course.period - 1)?.start.orEmpty()
    }

    fun resolvedEndTime(course: UestcCourse): String {
        val fromApi = course.endTime.trim()
        if (fromApi.isNotEmpty()) return fromApi
        return slots.getOrNull(course.endPeriod - 1)?.end.orEmpty()
    }

    fun timeRangeLabel(course: UestcCourse): String {
        val start = course.startTime.trim()
        val end = course.endTime.trim()
        if (start.isNotEmpty() && end.isNotEmpty()) return "$start-$end"
        if (start.isNotEmpty()) return start
        val first = slots.getOrNull(course.period - 1)
        val last = slots.getOrNull(course.endPeriod - 1)
        if (first != null && last != null) {
            return if (course.period == course.endPeriod) {
                "${first.start}-${first.end}"
            } else {
                "${first.start}-${last.end}"
            }
        }
        return ""
    }

    private fun parseClock(text: String): LocalTime? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return runCatching { LocalTime.parse(t, timeFmt) }.getOrNull()
            ?: runCatching { LocalTime.parse(t, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
    }
}
