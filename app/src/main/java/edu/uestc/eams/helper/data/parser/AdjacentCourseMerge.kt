package edu.uestc.eams.helper.data.parser

import edu.uestc.eams.helper.data.mapper.UestcPeriodTime
import edu.uestc.eams.helper.domain.model.UestcCourse

/** 合并同一课程在相邻或重叠节次上的记录，用于树维导入与课表展示。 */
object AdjacentCourseMerge {

    /**
     * @param groupByWeeks 为 true 时按周次分组（导入用，避免单双周被拼成一块）；
     * 课表按周过滤后再合并时应传 false，否则 weeks 文案不一致会导致重叠块叠底。
     */
    fun merge(
        courses: List<UestcCourse>,
        groupByWeeks: Boolean = true,
    ): List<UestcCourse> {
        if (courses.isEmpty()) return courses
        return courses
            .groupBy { groupKey(it, groupByWeeks) }
            .flatMap { (_, group) -> mergeChain(group.sortedBy { it.period }) }
            .sortedWith(compareBy({ it.weekday }, { it.period }))
    }

    private fun groupKey(c: UestcCourse, groupByWeeks: Boolean): String {
        val name = normalizeCourseName(c.courseName)
        return if (groupByWeeks) {
            "${c.weekday}|$name|${c.weeks.trim()}"
        } else {
            "${c.weekday}|$name"
        }
    }

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
            b.period <= a.endPeriod + 1 &&
            !crossesSessionBreak(a.endPeriod, b.period)

    /**
     * 跨越午休（4→5）或晚饭后（8→9）的节次不算「连在一起」，不应合并背景。
     */
    private fun crossesSessionBreak(endPeriod: Int, nextStart: Int): Boolean {
        if (nextStart <= endPeriod) return false
        for (p in endPeriod until nextStart) {
            if (p == UestcPeriodTime.NOON_DIVIDER_AFTER_PERIOD) return true
            if (p == 8) return true
        }
        return false
    }

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
            weeks = joinWeeks(a.weeks, b.weeks),
        )

    private fun joinWeeks(a: String, b: String): String {
        val left = a.trim()
        val right = b.trim()
        if (left.isEmpty()) return right
        if (right.isEmpty() || left == right) return left
        return left
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
