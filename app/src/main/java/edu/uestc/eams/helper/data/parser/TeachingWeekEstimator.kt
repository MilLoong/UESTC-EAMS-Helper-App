package edu.uestc.eams.helper.data.parser

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 按学年与开学日估算当前教学周。 */
object TeachingWeekEstimator {

    /**
     * 用于周次与日期对齐的「本周周一」。
     * 周日按移动教务惯例计入下一教学周（与 getCurWeek 在新周首日一致）。
     */
    fun teachingWeekMonday(today: LocalDate): LocalDate =
        when (today.dayOfWeek) {
            DayOfWeek.SUNDAY -> today.plusDays(1)
            else -> today.with(DayOfWeek.MONDAY)
        }

    fun weekOneMondayForCurrentWeek(currentWeek: Int, today: LocalDate = LocalDate.now()): LocalDate =
        teachingWeekMonday(today).minusWeeks((currentWeek - 1).coerceAtLeast(0).toLong())

    fun estimate(
        academicYear: Int?,
        maxWeek: Int,
        today: LocalDate = LocalDate.now(),
    ): Int {
        val cap = maxWeek.coerceAtLeast(1)
        val year = academicYear ?: today.year
        val startMonday = semesterStartMonday(year, today)
        return weekIndexFromMonday(startMonday, today, cap)
    }

    fun estimateFromWeekOneMonday(
        weekOneMonday: LocalDate,
        maxWeek: Int,
        today: LocalDate = LocalDate.now(),
    ): Int = weekIndexFromMonday(weekOneMonday, today, maxWeek.coerceAtLeast(1))

    private fun weekIndexFromMonday(
        weekOneMonday: LocalDate,
        today: LocalDate,
        maxWeek: Int,
    ): Int {
        if (today.isBefore(weekOneMonday)) return 1
        val anchorMonday = teachingWeekMonday(today)
        val weeks = ChronoUnit.WEEKS.between(weekOneMonday, anchorMonday) + 1
        return weeks.toInt().coerceIn(1, maxWeek)
    }

    private fun semesterStartMonday(year: Int, today: LocalDate): LocalDate {
        val month = today.monthValue
        val anchor =
            when {
                month >= 9 -> LocalDate.of(year, 9, 1)
                month >= 2 -> LocalDate.of(year, 2, 17)
                else -> LocalDate.of(year - 1, 9, 1)
            }
        var d = anchor
        while (d.dayOfWeek != DayOfWeek.MONDAY) {
            d = d.plusDays(1)
        }
        return d
    }
}
