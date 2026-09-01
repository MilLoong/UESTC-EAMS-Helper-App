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

    @Test
    fun does_not_merge_across_noon_break() {
        val courses =
            listOf(
                course(id = "a", name = "中国哲学经典著作选读", period = 3, end = 4),
                course(id = "b", name = "中国哲学经典著作选读", period = 5, end = 6),
            )
        val merged = AdjacentCourseMerge.merge(courses)
        assertEquals(2, merged.size)
        assertEquals(3, merged[0].period)
        assertEquals(4, merged[0].endPeriod)
        assertEquals(5, merged[1].period)
        assertEquals(6, merged[1].endPeriod)
    }

    @Test
    fun overlap_resolver_clips_taller_block_under_later_course() {
        val courses =
            listOf(
                course(id = "a", name = "中国哲学经典著作选读", period = 1, end = 4, weeks = "1-16"),
                course(
                    id = "b",
                    name = "中国哲学经典著作选读",
                    period = 3,
                    end = 4,
                    weeks = "",
                ),
            )
        // weeks 不同 → 合并不成，仍是两块重叠
        val merged = AdjacentCourseMerge.merge(courses)
        assertEquals(2, merged.size)
        val resolved = PeriodOverlapResolver.resolve(merged)
        assertEquals(2, resolved.size)
        assertEquals(1, resolved[0].period)
        assertEquals(2, resolved[0].endPeriod)
        assertEquals(3, resolved[1].period)
        assertEquals(4, resolved[1].endPeriod)
    }

    private fun course(
        id: String,
        period: Int,
        end: Int = period,
        name: String = "操作系统",
        weeks: String = "1-13",
    ): UestcCourse =
        UestcCourse(
            courseName = name,
            teacher = "甲",
            room = "A101",
            weekday = 1,
            period = period,
            endPeriod = end,
            weeks = weeks,
            courseId = id,
            startTime = "19:00",
            endTime = "22:15",
        )
}
