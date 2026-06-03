package edu.uestc.eams.helper.notification

import edu.uestc.eams.helper.data.local.AcademicCache
import edu.uestc.eams.helper.data.mapper.UestcPeriodTime
import edu.uestc.eams.helper.data.parser.CourseWeekFilter
import edu.uestc.eams.helper.data.parser.TeachingWeekEstimator
import edu.uestc.eams.helper.domain.model.TimetableMeta
import edu.uestc.eams.helper.domain.model.UestcCourse
import java.time.LocalDate

/** 从本地课表缓存中挑选即将开课、且属于指定教学周的一条课程。 */
object CourseReminderPlanner {

    data class Upcoming(val course: UestcCourse, val secondsUntilStart: Long)

    fun findUpcoming(
        cache: AcademicCache,
        meta: TimetableMeta?,
        today: LocalDate,
        leadSeconds: Long,
        nowEpochSec: Long,
    ): Upcoming? {
        val scanDates = listOf(today, today.plusDays(1))
        var best: Upcoming? = null
        for (date in scanDates) {
            val week = resolveTeachingWeek(meta, date)
            val courses = coursesForTeachingWeek(cache, meta, week)
            for (course in courses) {
                val start = UestcPeriodTime.startEpochSecOnDate(course, date) ?: continue
                val delta = start - nowEpochSec
                if (delta in 1..leadSeconds) {
                    if (best == null || delta < best.secondsUntilStart) {
                        best = Upcoming(course, delta)
                    }
                }
            }
        }
        return best
    }

    internal fun resolveTeachingWeek(meta: TimetableMeta?, date: LocalDate): Int {
        meta?.weekOneMondayDate()?.let { anchor ->
            return TeachingWeekEstimator.teachingWeekForDate(anchor, date)
        }
        return meta?.currentWeek?.coerceAtLeast(1) ?: 1
    }

    internal fun coursesForTeachingWeek(
        cache: AcademicCache,
        meta: TimetableMeta?,
        week: Int,
    ): List<UestcCourse> {
        val w = week.coerceAtLeast(1)
        if (!cache.isOfflineImported()) {
            val semester = meta?.semesterCode
            if (!semester.isNullOrBlank()) {
                cache.loadWeekCourses(semester, w)?.let { return it }
            }
        }
        return CourseWeekFilter.filterForWeek(cache.loadTimetableCoursesForUi(), w)
    }
}
