package edu.uestc.eams.helper.data.parser

import edu.uestc.eams.helper.domain.model.UestcCourse

/**
 * 同一天内若两门课节次范围重叠（常见于 weeks 文案不一致未能合并），
 * 裁短先开始的那一段，避免深色背景拖到后一段课下面。
 */
object PeriodOverlapResolver {

    fun resolve(courses: List<UestcCourse>): List<UestcCourse> {
        if (courses.size <= 1) return courses
        return courses
            .groupBy { it.weekday }
            .flatMap { (_, dayCourses) -> resolveDay(dayCourses) }
            .sortedWith(compareBy({ it.weekday }, { it.period }))
    }

    private fun resolveDay(dayCourses: List<UestcCourse>): List<UestcCourse> {
        val sorted =
            dayCourses.sortedWith(
                compareBy(
                    { it.period },
                    { it.endPeriod },
                    { it.courseName },
                ),
            )
        val out = mutableListOf<UestcCourse>()
        for (i in sorted.indices) {
            val course = sorted[i]
            var end = course.endPeriod
            for (j in (i + 1) until sorted.size) {
                val next = sorted[j]
                if (next.period > end) break
                end = minOf(end, next.period - 1)
            }
            if (end >= course.period) {
                out +=
                    if (end == course.endPeriod) {
                        course
                    } else {
                        course.copy(endPeriod = end)
                    }
            }
        }
        return out
    }
}
