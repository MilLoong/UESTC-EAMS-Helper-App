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

    data class Upcoming(
        val course: UestcCourse,
        val date: LocalDate,
        val secondsUntilStart: Long,
    )

    /** 计划中的一次提醒：在 [triggerEpochSec] 唤醒，对应 [startEpochSec] 开课。 */
    data class ScheduledTrigger(
        val course: UestcCourse,
        val date: LocalDate,
        val startEpochSec: Long,
        val triggerEpochSec: Long,
    )

    fun findUpcoming(
        cache: AcademicCache,
        meta: TimetableMeta?,
        today: LocalDate,
        leadSeconds: Long,
        nowEpochSec: Long,
    ): Upcoming? {
        return listTriggers(
            cache = cache,
            meta = meta,
            today = today,
            leadSeconds = leadSeconds,
            nowEpochSec = nowEpochSec,
            horizonDays = 1,
        ).firstOrNull { it.startEpochSec - nowEpochSec in 1..leadSeconds }
            ?.let {
                Upcoming(
                    course = it.course,
                    date = it.date,
                    secondsUntilStart = it.startEpochSec - nowEpochSec,
                )
            }
    }

    /**
     * 列出未来若干天内应调度的提醒点（按触发时间升序）。
     * 若已进入提前提醒窗口但仍未开课，触发时间会夹到「现在」，便于立刻补发。
     */
    fun listTriggers(
        cache: AcademicCache,
        meta: TimetableMeta?,
        today: LocalDate,
        leadSeconds: Long,
        nowEpochSec: Long,
        horizonDays: Int = 2,
    ): List<ScheduledTrigger> {
        val lead = leadSeconds.coerceAtLeast(60)
        val out = ArrayList<ScheduledTrigger>()
        for (offset in 0..horizonDays.coerceAtLeast(0)) {
            val date = today.plusDays(offset.toLong())
            val week = resolveTeachingWeek(meta, date)
            val courses = coursesForTeachingWeek(cache, meta, week)
            for (course in courses) {
                val start = UestcPeriodTime.startEpochSecOnDate(course, date) ?: continue
                if (start <= nowEpochSec) continue
                val rawTrigger = start - lead
                val trigger = if (rawTrigger <= nowEpochSec) nowEpochSec else rawTrigger
                out +=
                    ScheduledTrigger(
                        course = course,
                        date = date,
                        startEpochSec = start,
                        triggerEpochSec = trigger,
                    )
            }
        }
        return out.sortedWith(
            compareBy<ScheduledTrigger> { it.triggerEpochSec }
                .thenBy { it.startEpochSec }
                .thenBy { it.course.courseName },
        )
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
        return CourseWeekFilter.filterForWeek(
            cache.loadTimetableCoursesForUi(meta?.semesterCode.orEmpty()),
            w,
        )
    }
}
