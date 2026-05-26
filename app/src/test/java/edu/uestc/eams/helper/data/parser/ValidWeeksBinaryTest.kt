package edu.uestc.eams.helper.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidWeeksBinaryTest {

    @Test
    fun leading_zero_reanchors_to_week_one() {
        val bits = "0" + "1".repeat(13)
        val spec = ValidWeeksBinary.toWeekSpec(bits)
        assertEquals("1-13", spec)
        assertEquals(13, ValidWeeksBinary.maxWeek(bits))
        assertTrue(CourseWeekFilter.isActiveInWeek(spec, 1))
        assertTrue(CourseWeekFilter.isActiveInWeek(spec, 13))
        assertFalse(CourseWeekFilter.isActiveInWeek(spec, 14))
    }

    @Test
    fun compact_bits_unchanged() {
        assertEquals("1-2", ValidWeeksBinary.toWeekSpec("110"))
        assertEquals(2, ValidWeeksBinary.maxWeek("110"))
    }
}
