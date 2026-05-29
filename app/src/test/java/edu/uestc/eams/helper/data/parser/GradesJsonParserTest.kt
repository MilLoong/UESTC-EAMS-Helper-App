package edu.uestc.eams.helper.data.parser

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradesJsonParserTest {

    @Test
    fun parses_component_scores_from_student_grade_json() {
        val json =
            """
            {
              "courseName": "软件工程与实践",
              "courseCode": "G0901030",
              "semester": "25261",
              "scoreText": "73",
              "credits": 3.0,
              "gp": 2.8,
              "psScore": "90",
              "qmScore": "62",
              "zpScore": "73"
            }
            """.trimIndent()
        val item = GradesJsonParser.parse(JsonParser.parseString(json)).items.single()
        assertEquals("73", item.score)
        assertTrue(item.scoreParts.any { it.label == "平时" && it.value == "90" })
        assertTrue(item.scoreParts.any { it.label == "期末" && it.value == "62" })
        assertTrue(item.scoreParts.any { it.label == "总评" && it.value == "73" })
    }
}
