package edu.uestc.eams.helper.ui.compose

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class AppMonthCalendarTest {
    @Test
    fun weekday_labels_are_single_chinese_chars_monday_first() {
        val expected =
            listOf(
                DayOfWeek.MONDAY to "一",
                DayOfWeek.TUESDAY to "二",
                DayOfWeek.WEDNESDAY to "三",
                DayOfWeek.THURSDAY to "四",
                DayOfWeek.FRIDAY to "五",
                DayOfWeek.SATURDAY to "六",
                DayOfWeek.SUNDAY to "日",
            )
        expected.forEach { (day, label) ->
            assertEquals(label, chineseWeekdayLabel(day))
        }
    }
}
