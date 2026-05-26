package edu.uestc.eams.helper.data.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseWeekFilterTest {

    @Test
    fun week_ranges() {
        assertTrue(CourseWeekFilter.isActiveInWeek("1-16", 13))
        assertTrue(CourseWeekFilter.isActiveInWeek("11-13", 12))
        assertFalse(CourseWeekFilter.isActiveInWeek("11-13", 10))
        assertTrue(CourseWeekFilter.isActiveInWeek("1-5,7-16", 3))
        assertFalse(CourseWeekFilter.isActiveInWeek("1-5,7-16", 6))
        assertTrue(CourseWeekFilter.isActiveInWeek("13", 13))
    }
}
