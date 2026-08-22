package edu.uestc.eams.helper.data.eamsapp

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

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

    @Test
    fun parseCurSemester_reads_startOn_and_weeks() {
        val json =
            """
            {
              "code": "25262",
              "year": "2025-2026",
              "name": "2",
              "startOn": "2026-03-02 00:00:00",
              "endOn": "2026-07-19 00:00:00",
              "first": 1,
              "weeks": 21,
              "calendar": "默认校历"
            }
            """.trimIndent()
        val sem = BladeJson.parseCurSemester(JsonParser.parseString(json))
        assertNotNull(sem)
        assertEquals("25262", sem!!.code)
        assertEquals(LocalDate.of(2026, 3, 2), sem.startOn)
        assertEquals(LocalDate.of(2026, 7, 19), sem.endOn)
        assertEquals(21, sem.weeks)
        assertEquals(1, sem.firstWeek)
    }

    @Test
    fun parseCurSemester_from_wrapped_data() {
        val json =
            """
            {
              "success": true,
              "code": 200,
              "data": {
                "code": "25261",
                "startOn": "2025-09-01 00:00:00",
                "weeks": 20
              }
            }
            """.trimIndent()
        val root = JsonParser.parseString(json)
        val sem = BladeJson.parseCurSemester(BladeJson.unwrapRoot(root))
        assertNotNull(sem)
        assertEquals("25261", sem!!.code)
        assertEquals(LocalDate.of(2025, 9, 1), sem.startOn)
    }
}
