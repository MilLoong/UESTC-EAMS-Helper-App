package edu.uestc.eams.helper.domain.grade

import edu.uestc.eams.helper.domain.model.GradeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeStatsCalculatorTest {

    @Test
    fun weighted_average_score_and_gpa() {
        val a = grade("高等数学", "4", "85", "3.7")
        val b = grade("大学英语", "2", "90", "4.0")
        val keys = setOf(GradeStatsCalculator.stableKey(a), GradeStatsCalculator.stableKey(b))

        val avg = GradeStatsCalculator.averageScore(listOf(a, b), keys)
        assertEquals(86.67, avg.value!!, 0.01)
        assertEquals(2, avg.courseCount)

        val gpa = GradeStatsCalculator.averageGpa(listOf(a, b), keys)
        assertEquals(3.8, gpa.value!!, 0.01)
    }

    @Test
    fun skips_non_numeric_score() {
        val item = grade("体育", "1", "良好", "3.0")
        val keys = setOf(GradeStatsCalculator.stableKey(item))
        assertNull(GradeStatsCalculator.averageScore(listOf(item), keys).value)
        assertEquals(3.0, GradeStatsCalculator.averageGpa(listOf(item), keys).value!!, 0.01)
    }

    private fun grade(name: String, credit: String, score: String, gp: String): GradeItem =
        GradeItem(
            courseName = name,
            score = score,
            credit = credit,
            gradePoint = gp,
            courseCode = name,
            semester = "25261",
        )
}
