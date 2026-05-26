package edu.uestc.eams.helper.data.parser

import edu.uestc.eams.helper.domain.model.UestcCourse

/** 合并同一课程在相邻或重叠节次上的记录，用于树维导入与课表展示。 */
object AdjacentCourseMerge {

    fun merge(courses: List<UestcCourse>): List<UestcCourse> {
        if (courses.isEmpty()) return courses
        val sorted =
            courses.sortedWith(
                compareBy<UestcCourse>({ it.weekday })
                    .thenBy { it.period }
                    .thenBy { it.courseName }
                    .thenBy { it.courseId },
            )
        val out = mutableListOf<UestcCourse>()
        var cur: UestcCourse? = null
        for (c in sorted) {
            val prev = cur
            if (prev != null && canMerge(prev, c)) {
                cur =
                    prev.copy(
                        period = minOf(prev.period, c.period),
                        endPeriod = maxOf(prev.endPeriod, c.endPeriod),
                        endTime = c.endTime.ifBlank { prev.endTime },
                        teacher = joinDistinct(prev.teacher, c.teacher),
                        room = joinDistinct(prev.room, c.room),
                    )
            } else {
                prev?.let { out += it }
                cur = c
            }
        }
        cur?.let { out += it }
        return out
    }

    private fun canMerge(a: UestcCourse, b: UestcCourse): Boolean =
        a.weekday == b.weekday &&
            sameCourse(a, b) &&
            a.weeks == b.weeks &&
            b.period <= a.endPeriod + 1

    private fun sameCourse(a: UestcCourse, b: UestcCourse): Boolean {
        if (a.courseId.isNotBlank() && b.courseId.isNotBlank()) {
            return a.courseId == b.courseId
        }
        return a.courseName == b.courseName
    }

    private fun joinDistinct(a: String, b: String): String {
        val left = a.trim()
        val right = b.trim()
        if (left.isEmpty()) return right
        if (right.isEmpty() || left == right) return left
        if (left.contains(right) || right.contains(left)) return left
        return "$left / $right"
    }
}
