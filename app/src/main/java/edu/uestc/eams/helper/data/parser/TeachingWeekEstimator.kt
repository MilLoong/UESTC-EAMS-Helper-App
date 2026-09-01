package edu.uestc.eams.helper.data.parser

import edu.uestc.eams.helper.domain.model.TimetableMeta
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 按学年与开学日估算当前教学周。 */
object TeachingWeekEstimator {

    /** 教学周按周一至周日计，取今天所在自然周的周一。 */
    fun teachingWeekMonday(today: LocalDate): LocalDate = today.with(DayOfWeek.MONDAY)

    fun weekOneMondayForCurrentWeek(currentWeek: Int, today: LocalDate = LocalDate.now()): LocalDate {
        val reversed =
            teachingWeekMonday(today).minusWeeks((currentWeek - 1).coerceAtLeast(0).toLong())
        if (currentWeek <= 1 && isBetweenSemesters(today)) {
            return upcomingSemesterStartMonday(today)
        }
        return reversed
    }

    /** 文件无开学日时，默认对齐到即将开始或当前学期的第 1 周周一，而不是今天。 */
    fun defaultImportWeekOneMonday(
        fromFile: LocalDate?,
        firstClassDay: LocalDate?,
        today: LocalDate = LocalDate.now(),
    ): LocalDate = fromFile ?: firstClassDay ?: upcomingSemesterStartMonday(today)

    /**
     * 已缓存的第 1 周周一：同学期沿用；开学前若锚点落在假期里（把今天当成第 1 周），则重算。
     */
    fun resolvePersistedWeekOneMonday(
        stored: LocalDate?,
        sameSemester: Boolean,
        apiWeek: Int,
        today: LocalDate = LocalDate.now(),
        userLocked: Boolean = false,
    ): LocalDate {
        val computed = weekOneMondayForCurrentWeek(apiWeek, today)
        if (stored == null || !sameSemester) return computed
        if (userLocked) return stored
        val upcoming = upcomingSemesterStartMonday(today)
        if (apiWeek <= 1 && isBetweenSemesters(today) && stored.isBefore(upcoming)) {
            return computed
        }
        return stored
    }

    data class WeekRangePreview(
        val week: Int,
        val monday: LocalDate,
        val sunday: LocalDate,
    )

    fun previewWeekRanges(
        weekOneMonday: LocalDate,
        weeks: IntRange = 1..4,
    ): List<WeekRangePreview> =
        weeks.map { week ->
            val monday = weekOneMonday.plusWeeks((week - 1).toLong())
            WeekRangePreview(week, monday, monday.plusDays(6))
        }

    fun alignTimetableMeta(
        meta: TimetableMeta,
        selectedDay: LocalDate,
        today: LocalDate = LocalDate.now(),
        maxWeek: Int = 30,
    ): TimetableMeta {
        val monday = selectedDay.with(DayOfWeek.MONDAY)
        val current = estimateFromWeekOneMonday(monday, maxWeek, today)
        return meta.copy(
            weekOneMonday = monday.toString(),
            currentWeek = current,
            weekOneLocked = true,
        )
    }

    fun upcomingSemesterStartMonday(today: LocalDate = LocalDate.now()): LocalDate {
        val spring = LocalDate.of(today.year, 2, 17).with(DayOfWeek.MONDAY)
        val fall = LocalDate.of(today.year, 9, 1).with(DayOfWeek.MONDAY)
        return when {
            today.isBefore(spring) -> spring
            today.monthValue in 7..8 -> fall
            today.monthValue >= 9 -> fall
            else -> spring
        }
    }

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

    private fun isBetweenSemesters(today: LocalDate): Boolean {
        val spring = LocalDate.of(today.year, 2, 17).with(DayOfWeek.MONDAY)
        val fall = LocalDate.of(today.year, 9, 1).with(DayOfWeek.MONDAY)
        if (today.isBefore(spring)) return true
        return !today.isBefore(LocalDate.of(today.year, 7, 1)) && today.isBefore(fall)
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
