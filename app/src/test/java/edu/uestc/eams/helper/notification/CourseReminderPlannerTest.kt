package edu.uestc.eams.helper.notification

import edu.uestc.eams.helper.data.parser.CourseWeekFilter
import edu.uestc.eams.helper.domain.model.TimetableMeta
import edu.uestc.eams.helper.domain.model.UestcCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CourseReminderPlannerTest {

    @Test
    fun resolveTeachingWeek_uses_anchor_when_present() {
        val meta =
            TimetableMeta(
                semesterCode = "25262",
                currentWeek = 14,
                displayWeek = 14,
                weekOneMonday = "2026-02-23",
            )
        val wed = LocalDate.of(2026, 6, 3)
        val week = CourseReminderPlanner.resolveTeachingWeek(meta, wed)
        assertEquals(14, meta.currentWeek)
        assertTrue(week in 13..15)
    }

    @Test
    fun filter_excludes_course_only_active_in_other_week() {
        val wed =
            UestcCourse(
                courseName = "旧周周三课",
                teacher = "",
                room = "A101",
                weekday = 3,
                period = 3,
                endPeriod = 4,
                weeks = "13",
            )
        val active =
            UestcCourse(
                courseName = "音乐鉴赏",
                teacher = "",
                room = "B401",
                weekday = 3,
                period = 1,
                endPeriod = 2,
                weeks = "14",
            )
        val list = CourseWeekFilter.filterForWeek(listOf(wed, active), 14)
        assertEquals(1, list.size)
        assertEquals("音乐鉴赏", list.single().courseName)
    }
}
