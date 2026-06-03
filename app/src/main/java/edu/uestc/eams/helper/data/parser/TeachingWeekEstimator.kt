package edu.uestc.eams.helper.data.parser

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 按学年与开学日估算当前教学周。 */
object TeachingWeekEstimator {

    /** 教学周按周一至周日计，取今天所在自然周的周一。 */
    fun teachingWeekMonday(today: LocalDate): LocalDate = today.with(DayOfWeek.MONDAY)

    fun weekOneMondayForCurrentWeek(currentWeek: Int, today: LocalDate = LocalDate.now()): LocalDate =
        teachingWeekMonday(today).minusWeeks((currentWeek - 1).coerceAtLeast(0).toLong())

    fun teachingWeekForDate(
        weekOneMonday: LocalDate,
        date: LocalDate,
        maxWeek: Int = 30,
    ): Int = weekIndexFromMonday(weekOneMonday, date, maxWeek.coerceAtLeast(1))

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
