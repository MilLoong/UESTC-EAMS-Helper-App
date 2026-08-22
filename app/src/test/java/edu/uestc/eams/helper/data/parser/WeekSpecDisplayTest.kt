package edu.uestc.eams.helper.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekSpecDisplayTest {

    @Test
    fun format_single_and_ranges() {
        assertEquals("第1-5周，第7-16周", WeekSpecDisplay.formatForUi("1-5,7-16"))
        assertEquals("第3周", WeekSpecDisplay.formatForUi("3"))
        assertEquals("未标注周次", WeekSpecDisplay.formatForUi(""))
    }

    @Test
    fun format_compact() {
        assertEquals("1-16周", WeekSpecDisplay.formatCompact("1-16"))
        assertEquals("1-5,7-16周", WeekSpecDisplay.formatCompact("1-5,7-16"))
        assertEquals("", WeekSpecDisplay.formatCompact(""))
    }
}
