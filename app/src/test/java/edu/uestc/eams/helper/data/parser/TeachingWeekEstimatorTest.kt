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
}
