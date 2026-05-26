package edu.uestc.eams.helper.data.parser

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableJsonParserTest {

    @Test
    fun parse_eamsapp_week_table_uses_startUnit_and_weekDay() {
        val json =
            """
            [
              {
                "courseName": "音乐鉴赏",
                "weekDay": 3,
                "startUnit": 1,
                "endUnit": 2,
                "startTime": "08:30",
                "endTime": "10:05",
                "roomName": "第二教学楼401",
                "teacherName": "唐珞",
                "week": "1-16",
                "courseCode": "A9904920",
                "no": "A9904920.08"
              },
              {
                "courseName": "网球D",
                "weekDay": 1,
                "startUnit": 5,
                "endUnit": 6,
                "roomName": "",
                "teacherName": "熊妮"
              }
            ]
            """.trimIndent()
        val list = TimetableJsonParser.parse(JsonParser.parseString(json))
        val music = list.first { it.courseName == "音乐鉴赏" }
        assertEquals(3, music.weekday)
        assertEquals(1, music.period)
        assertEquals(2, music.endPeriod)
        assertEquals("08:30", music.startTime)
        assertEquals("第二教学楼401", music.room)
        assertEquals("唐珞", music.teacher)

        val tennis = list.first { it.courseName == "网球D" }
        assertEquals(5, tennis.period)
        assertEquals(6, tennis.endPeriod)
        assertTrue(tennis.room.isEmpty())
    }
}
