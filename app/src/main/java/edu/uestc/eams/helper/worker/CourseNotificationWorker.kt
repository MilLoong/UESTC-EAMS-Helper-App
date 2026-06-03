package edu.uestc.eams.helper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import edu.uestc.eams.helper.data.local.AcademicCache
import edu.uestc.eams.helper.data.mapper.UestcPeriodTime
import edu.uestc.eams.helper.data.prefs.CourseReminderPreferences
import edu.uestc.eams.helper.notification.CourseNotificationHelper
import edu.uestc.eams.helper.notification.CourseReminderPlanner
import java.time.LocalDate

/** 定时检查课表并发送上课提醒。 */
class CourseNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cache = AcademicCache(applicationContext)
        val meta = cache.loadTimetableMeta()
        val leadSec = CourseReminderPreferences(applicationContext).leadSeconds
        val nowSec = System.currentTimeMillis() / 1000
        val upcoming =
            CourseReminderPlanner.findUpcoming(
                cache = cache,
                meta = meta,
                today = LocalDate.now(),
                leadSeconds = leadSec.toLong(),
                nowEpochSec = nowSec,
            ) ?: return Result.success()

        val minutes = ((upcoming.secondsUntilStart + 59) / 60).toInt().coerceAtLeast(1)
        CourseNotificationHelper.showClassReminder(
            context = applicationContext,
            courseName = upcoming.course.courseName,
            room = upcoming.course.room,
            minutesUntil = minutes,
            startTime = UestcPeriodTime.resolvedStartTime(upcoming.course),
            debug = false,
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "course_notification_worker"
        const val EXTRA_OPEN_TAB = "open_tab"
    }
}
