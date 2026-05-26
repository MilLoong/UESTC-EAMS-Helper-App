package edu.uestc.eams.helper.data.parser

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 按学年与开学日估算当前教学周。 */
object TeachingWeekEstimator {

    fun estimate(
        academicYear: Int?,
        maxWeek: Int,
        today: LocalDate = LocalDate.now(),
    ): Int {
        val cap = maxWeek.coerceAtLeast(1)
        val year = academicYear ?: today.year
        val startMonday = semesterStartMonday(year, today)
        val thisMonday = today.with(DayOfWeek.MONDAY)
        if (thisMonday.isBefore(startMonday)) return 1
        val weeks = ChronoUnit.WEEKS.between(startMonday, thisMonday) + 1
        return weeks.toInt().coerceIn(1, cap)
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
