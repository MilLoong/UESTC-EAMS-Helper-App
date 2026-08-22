package edu.uestc.eams.helper.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TeachingWeekEstimatorTest {

    @Test
    fun weekOneMonday_for_current_week_reverses_anchor() {
        val today = LocalDate.of(2026, 5, 25)
        val weekOne = TeachingWeekEstimator.weekOneMondayForCurrentWeek(14, today)
        assertEquals(LocalDate.of(2026, 2, 23), weekOne)
    }

    @Test
    fun weekOneMonday_from_startOn_aligns_to_monday() {
        // 2026-03-02 已是周一
        assertEquals(
            LocalDate.of(2026, 3, 2),
            TeachingWeekEstimator.weekOneMondayFromStartOn(LocalDate.of(2026, 3, 2)),
        )
        // 若 startOn 为周二，对齐到该周周一
        assertEquals(
            LocalDate.of(2026, 8, 31),
            TeachingWeekEstimator.weekOneMondayFromStartOn(LocalDate.of(2026, 9, 1)),
        )
    }

    @Test
    fun teaching_week_before_start_is_week_one() {
        val weekOne = LocalDate.of(2026, 8, 31)
        assertEquals(
            1,
            TeachingWeekEstimator.teachingWeekForDate(weekOne, LocalDate.of(2026, 8, 22)),
        )
    }
}
