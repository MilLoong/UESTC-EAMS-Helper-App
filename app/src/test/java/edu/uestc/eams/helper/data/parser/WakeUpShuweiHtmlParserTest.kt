package edu.uestc.eams.helper.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeUpShuweiHtmlParserTest {

    @Test
    fun parses_activity_grid_and_week_bits() {
        val json =
            """
            {
              "unitCount": 2,
              "year": 2026,
              "activities": [
                [], [{"courseId":"1","courseName":"测试课","teacherName":"张老师","roomName":"A101","vaildWeeks":"110"}],
                [], [], [], [], [],
                [], [], [], [], [], [], []
              ]
            }
            """.trimIndent()
        val result = WakeUpShuweiHtmlParser.parse(json)
        assertEquals(1, result.courses.size)
        val c = result.courses.first()
        assertEquals("测试课", c.courseName)
        assertEquals("张老师", c.teacher)
        assertEquals("A101", c.room)
        assertEquals(1, c.weekday)
        assertEquals(2, c.period)
        assertEquals("1-2", c.weeks)
        assertTrue(CourseWeekFilter.isActiveInWeek(c.weeks, 1))
        assertTrue(CourseWeekFilter.isActiveInWeek(c.weeks, 2))
    }

    @Test
    fun parses_vaildWeeks_with_leading_padding_zero() {
        val bits = "0" + "1".repeat(13)
        val json =
            """
            {
              "unitCount": 1,
              "activities": [
                [{"courseId":"1","courseName":"课A","vaildWeeks":"$bits"}],
                [], [], [], [], [], [], []
              ]
            }
            """.trimIndent()
        val result = WakeUpShuweiHtmlParser.parse(json)
        assertEquals(13, result.maxWeek)
        val c = result.courses.single()
        assertEquals("1-13", c.weeks)
        assertTrue(CourseWeekFilter.isActiveInWeek(c.weeks, 1))
    }

    @Test
    fun merges_same_course_across_periods_with_different_teachers() {
        val json =
            """
            {
              "unitCount": 3,
              "activities": [
                [], [], [],
                [], [], [],
                [], [{"courseId":"57815","courseName":"操作系统","teacherName":"甲","roomName":"A101","vaildWeeks":"111111111111111111111111111111111111111111111111111111111111"}], [{"courseId":"57815","courseName":"操作系统","teacherName":"乙","roomName":"A101","vaildWeeks":"111111111111111111111111111111111111111111111111111111111111"}],
                [], [], [],
                [], [], [],
                [], [], []
              ]
            }
            """.trimIndent()
        val result = WakeUpShuweiHtmlParser.parse(json)
        val wed = result.courses.filter { it.weekday == 3 && it.courseName == "操作系统" }
        assertEquals(1, wed.size)
        assertEquals(2, wed.first().period)
        assertEquals(3, wed.first().endPeriod)
    }
}
