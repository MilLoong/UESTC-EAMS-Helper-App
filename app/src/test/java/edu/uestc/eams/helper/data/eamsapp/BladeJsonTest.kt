package edu.uestc.eams.helper.data.eamsapp

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class BladeJsonTest {

    @Test
    fun parseCurWeek_prefers_curWeek_over_generic_week() {
        val json =
            """
            {
              "week": 1,
              "curWeek": 14,
              "currentWeek": 13
            }
            """.trimIndent()
        assertEquals(14, BladeJson.parseCurWeek(JsonParser.parseString(json)))
    }

    @Test
    fun parseCurWeek_ignores_week_one_placeholder() {
        val json =
            """
            {
              "week": 1,
              "curWeek": 14
            }
            """.trimIndent()
        assertEquals(14, BladeJson.parseCurWeek(JsonParser.parseString(json)))
    }

    @Test
    fun parseCurWeek_reads_nested_curWeek_before_week() {
        val json =
            """
            {
              "data": {
                "week": 1,
                "curWeek": 14
              }
            }
            """.trimIndent()
        assertEquals(14, BladeJson.parseCurWeek(JsonParser.parseString(json)))
    }
}
