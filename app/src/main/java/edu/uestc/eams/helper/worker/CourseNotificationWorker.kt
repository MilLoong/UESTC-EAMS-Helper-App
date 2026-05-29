package edu.uestc.eams.helper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import edu.uestc.eams.helper.data.local.AcademicCache
import edu.uestc.eams.helper.data.mapper.UestcPeriodTime
import edu.uestc.eams.helper.data.prefs.CourseReminderPreferences
import edu.uestc.eams.helper.domain.model.UestcCourse
import edu.uestc.eams.helper.notification.CourseNotificationHelper
import java.time.LocalDate

/** 定时检查课表并发送上课提醒。 */
class CourseNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val courses = AcademicCache(applicationContext).loadTimetableCoursesForUi()
        if (courses.isEmpty()) return Result.success()

        val leadSec = CourseReminderPreferences(applicationContext).leadSeconds
        val nowSec = System.currentTimeMillis() / 1000
        val today = LocalDate.now()
        val scanDates = listOf(today, today.plusDays(1))

        var bestCourse: UestcCourse? = null
        var bestDelta = Long.MAX_VALUE
        for (date in scanDates) {
            for (c in courses) {
                val start = UestcPeriodTime.startEpochSecOnDate(c, date) ?: continue
                val delta = start - nowSec
                if (delta in 1..leadSec && delta < bestDelta) {
                    bestDelta = delta
                    bestCourse = c
                }
            }
        }

        val course = bestCourse ?: return Result.success()
        val minutes = ((bestDelta + 59) / 60).toInt().coerceAtLeast(1)
        CourseNotificationHelper.showClassReminder(
            context = applicationContext,
            courseName = course.courseName,
            room = course.room,
            minutesUntil = minutes,
            startTime = UestcPeriodTime.resolvedStartTime(course),
            debug = false,
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "course_notification_worker"
        const val EXTRA_OPEN_TAB = "open_tab"
    }
}
