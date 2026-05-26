package edu.uestc.eams.helper.data.parser

import edu.uestc.eams.helper.domain.model.UestcCourse

/** 判断课程在指定教学周是否上课。 */
object CourseWeekFilter {

    fun isActiveInWeek(weekSpec: String, week: Int): Boolean {
        val spec = weekSpec.trim()
        if (spec.isEmpty()) return true
        return spec.split(",").any { segment -> segment.trim().let { matchesSegment(it, week) } }
    }

    private fun matchesSegment(segment: String, week: Int): Boolean {
        if (segment.contains("-")) {
            val parts = segment.split("-", limit = 2)
            val start = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return false
            val end = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return false
            return week in start..end
        }
        return segment.toIntOrNull() == week
    }

    fun filterForWeek(courses: List<UestcCourse>, week: Int): List<UestcCourse> =
        courses.filter { isActiveInWeek(it.weeks, week) }
}
