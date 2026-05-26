package edu.uestc.eams.helper.data.parser

import edu.uestc.eams.helper.domain.model.UestcCourse

/** 合并同一课程在相邻或重叠节次上的记录，用于树维导入与课表展示。 */
object AdjacentCourseMerge {

    fun merge(courses: List<UestcCourse>): List<UestcCourse> {
        if (courses.isEmpty()) return courses
        return courses
            .groupBy { groupKey(it) }
            .flatMap { (_, group) -> mergeChain(group.sortedBy { it.period }) }
            .sortedWith(compareBy({ it.weekday }, { it.period }))
    }

    private fun groupKey(c: UestcCourse): String =
        "${c.weekday}|${normalizeCourseName(c.courseName)}|${c.weeks.trim()}"

    private fun normalizeCourseName(name: String): String = name.trim().replace(Regex("\\s+"), "")

    private fun mergeChain(sorted: List<UestcCourse>): List<UestcCourse> {
        if (sorted.isEmpty()) return emptyList()
        val out = mutableListOf<UestcCourse>()
        var cur = sorted.first()
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (canMerge(cur, next)) {
                cur = combine(cur, next)
            } else {
                out += cur
                cur = next
            }
        }
        out += cur
        return out
    }

    private fun canMerge(a: UestcCourse, b: UestcCourse): Boolean =
        a.weekday == b.weekday &&
            sameCourse(a, b) &&
            compatibleWeeks(a.weeks, b.weeks) &&
            b.period <= a.endPeriod + 1

    private fun sameCourse(a: UestcCourse, b: UestcCourse): Boolean {
        val na = normalizeCourseName(a.courseName)
        val nb = normalizeCourseName(b.courseName)
        if (na.isNotEmpty() && na == nb) return true
        val idA = a.courseId.trim()
        val idB = b.courseId.trim()
        return idA.isNotEmpty() && idA == idB
    }

    private fun compatibleWeeks(a: String, b: String): Boolean {
        val left = a.trim()
        val right = b.trim()
        if (left == right) return true
        if (left.isEmpty() || right.isEmpty()) return true
        return false
    }

    private fun combine(a: UestcCourse, b: UestcCourse): UestcCourse =
        a.copy(
            period = minOf(a.period, b.period),
            endPeriod = maxOf(a.endPeriod, b.endPeriod),
            endTime = b.endTime.ifBlank { a.endTime },
            startTime = a.startTime.ifBlank { b.startTime },
            teacher = joinDistinct(a.teacher, b.teacher),
            room = joinDistinct(a.room, b.room),
            courseId = a.courseId.ifBlank { b.courseId },
        )

    private fun joinDistinct(a: String, b: String): String {
        val left = a.trim()
        val right = b.trim()
        if (left.isEmpty()) return right
        if (right.isEmpty() || left == right) return left
        if (left.contains(right) || right.contains(left)) return left
        return "$left / $right"
    }
}
