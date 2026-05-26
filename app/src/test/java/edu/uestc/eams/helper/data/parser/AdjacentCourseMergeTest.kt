package edu.uestc.eams.helper.data.parser

import edu.uestc.eams.helper.domain.model.UestcCourse
import org.junit.Assert.assertEquals
import org.junit.Test

class AdjacentCourseMergeTest {

    @Test
    fun merges_adjacent_periods_with_different_course_ids_same_name() {
        val courses =
            listOf(
                course(id = "a", period = 9, end = 9),
                course(id = "b", period = 10, end = 10),
                course(id = "c", period = 11, end = 11),
            )
        val merged = AdjacentCourseMerge.merge(courses)
        assertEquals(1, merged.size)
        assertEquals(9, merged[0].period)
        assertEquals(11, merged[0].endPeriod)
    }

    @Test
    fun does_not_merge_same_name_when_periods_not_adjacent() {
        val courses =
            listOf(
                course(id = "a", period = 9, end = 9),
                course(id = "x", name = "其它课", period = 10, end = 10),
                course(id = "b", period = 11, end = 11),
            )
        val merged = AdjacentCourseMerge.merge(courses)
        val os = merged.filter { it.courseName == "操作系统" }
        assertEquals(2, os.size)
    }

    private fun course(
        id: String,
        period: Int,
        end: Int = period,
        name: String = "操作系统",
    ): UestcCourse =
        UestcCourse(
            courseName = name,
            teacher = "甲",
            room = "A101",
            weekday = 1,
            period = period,
            endPeriod = end,
            weeks = "1-13",
            courseId = id,
            startTime = "19:00",
            endTime = "22:15",
        )
}
