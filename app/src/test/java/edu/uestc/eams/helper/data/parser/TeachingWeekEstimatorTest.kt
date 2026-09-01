package edu.uestc.eams.helper.data.parser

import edu.uestc.eams.helper.data.parser.TeachingWeekEstimator
import edu.uestc.eams.helper.domain.model.TimetableMeta
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TeachingWeekEstimatorTest {

    @Test
    fun weekOneMonday_first_week_of_spring_still_reverses() {
        val today = LocalDate.of(2026, 2, 20)
        val weekOne = TeachingWeekEstimator.weekOneMondayForCurrentWeek(1, today)
        assertEquals(LocalDate.of(2026, 2, 16), weekOne)
    }

    @Test
    fun weekOneMonday_for_current_week_reverses_anchor() {
        val today = LocalDate.of(2026, 5, 25)
        val weekOne = TeachingWeekEstimator.weekOneMondayForCurrentWeek(14, today)
        assertEquals(LocalDate.of(2026, 2, 23), weekOne)
    }

    @Test
    fun weekOneMonday_before_fall_does_not_treat_today_as_week_one() {
        val today = LocalDate.of(2026, 8, 21)
        val weekOne = TeachingWeekEstimator.weekOneMondayForCurrentWeek(1, today)
        assertEquals(LocalDate.of(2026, 8, 31), weekOne)
    }

    @Test
    fun weekOneMonday_before_spring_uses_february_week() {
        val today = LocalDate.of(2026, 1, 10)
        val weekOne = TeachingWeekEstimator.weekOneMondayForCurrentWeek(1, today)
        assertEquals(LocalDate.of(2026, 2, 16), weekOne)
    }

    @Test
    fun defaultImportWeekOneMonday_before_fall_uses_september_week() {
        val today = LocalDate.of(2026, 8, 21)
        val weekOne = TeachingWeekEstimator.defaultImportWeekOneMonday(null, null, today)
        assertEquals(LocalDate.of(2026, 8, 31), weekOne)
    }

    @Test
    fun resolvePersistedWeekOneMonday_replaces_today_anchor_before_semester() {
        val today = LocalDate.of(2026, 8, 21)
        val storedWrong = LocalDate.of(2026, 8, 17)
        val resolved =
            TeachingWeekEstimator.resolvePersistedWeekOneMonday(
                stored = storedWrong,
                sameSemester = true,
                apiWeek = 1,
                today = today,
            )
        assertEquals(LocalDate.of(2026, 8, 31), resolved)
    }

    @Test
    fun resolvePersistedWeekOneMonday_keeps_user_locked_anchor() {
        val today = LocalDate.of(2026, 8, 21)
        val chosen = LocalDate.of(2026, 9, 7)
        val resolved =
            TeachingWeekEstimator.resolvePersistedWeekOneMonday(
                stored = chosen,
                sameSemester = true,
                apiWeek = 1,
                today = today,
                userLocked = true,
            )
        assertEquals(chosen, resolved)
    }

    @Test
    fun alignTimetableMeta_snaps_to_monday_and_locks() {
        val today = LocalDate.of(2026, 8, 21)
        val meta =
            TimetableMeta(
                semesterCode = "25262",
                currentWeek = 1,
                displayWeek = 2,
                weekOneMonday = "2026-08-17",
            )
        val aligned =
            TeachingWeekEstimator.alignTimetableMeta(
                meta,
                LocalDate.of(2026, 9, 2),
                today,
            )
        assertEquals("2026-08-31", aligned.weekOneMonday)
        assertEquals(1, aligned.currentWeek)
        assertEquals(2, aligned.displayWeek)
        assertEquals(true, aligned.weekOneLocked)
    }

    @Test
    fun previewWeekRanges_lists_mondays_from_anchor() {
        val ranges =
            TeachingWeekEstimator.previewWeekRanges(LocalDate.of(2026, 8, 31), 1..2)
        assertEquals(LocalDate.of(2026, 8, 31), ranges[0].monday)
        assertEquals(LocalDate.of(2026, 9, 6), ranges[0].sunday)
        assertEquals(2, ranges[1].week)
        assertEquals(LocalDate.of(2026, 9, 7), ranges[1].monday)
    }
}
