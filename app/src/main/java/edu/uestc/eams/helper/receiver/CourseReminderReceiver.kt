package edu.uestc.eams.helper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import edu.uestc.eams.helper.data.mapper.UestcPeriodTime
import edu.uestc.eams.helper.domain.model.UestcCourse
import edu.uestc.eams.helper.notification.CourseNotificationHelper
import edu.uestc.eams.helper.notification.CourseReminderAlarmScheduler
import java.time.LocalDate

/** 精确闹钟到点后发上课通知，并重新挂载后续提醒。 */
class CourseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return
        val app = context.applicationContext
        val dateRaw = intent.getStringExtra(EXTRA_DATE).orEmpty()
        val date = runCatching { LocalDate.parse(dateRaw) }.getOrNull() ?: LocalDate.now()
        val startEpoch = intent.getLongExtra(EXTRA_START_EPOCH_SEC, 0L)
        val course =
            UestcCourse(
                courseName = intent.getStringExtra(EXTRA_COURSE_NAME).orEmpty(),
                teacher = intent.getStringExtra(EXTRA_TEACHER).orEmpty(),
                room = intent.getStringExtra(EXTRA_ROOM).orEmpty(),
                weekday = intent.getIntExtra(EXTRA_WEEKDAY, date.dayOfWeek.value),
                period = intent.getIntExtra(EXTRA_PERIOD, 1),
                endPeriod = intent.getIntExtra(EXTRA_END_PERIOD, 1),
                weeks = intent.getStringExtra(EXTRA_WEEKS).orEmpty(),
                lessonNo = intent.getStringExtra(EXTRA_LESSON_NO).orEmpty(),
                startTime = intent.getStringExtra(EXTRA_START_TIME).orEmpty(),
                endTime = intent.getStringExtra(EXTRA_END_TIME).orEmpty(),
            )
        if (course.courseName.isBlank()) {
            CourseReminderAlarmScheduler.reschedule(app)
            return
        }
        val nowSec = System.currentTimeMillis() / 1000L
        val secondsUntil =
            if (startEpoch > 0L) {
                (startEpoch - nowSec).coerceAtLeast(0L)
            } else {
                0L
            }
        val minutesUntil = ((secondsUntil + 59) / 60).toInt().coerceAtLeast(0)
        CourseNotificationHelper.showClassReminder(
            context = app,
            course = course,
            date = date,
            minutesUntil = minutesUntil,
            startTime = UestcPeriodTime.resolvedStartTime(course),
            debug = false,
        )
        // 发完本条后继续挂后续课的闹钟（进程被杀也不依赖 WorkManager）
        CourseReminderAlarmScheduler.reschedule(app)
    }

    companion object {
        const val ACTION_FIRE = "edu.uestc.eams.helper.action.COURSE_REMINDER_FIRE"
        const val EXTRA_REMINDER_KEY = "reminder_key"
        const val EXTRA_DATE = "date"
        const val EXTRA_COURSE_NAME = "course_name"
        const val EXTRA_ROOM = "room"
        const val EXTRA_TEACHER = "teacher"
        const val EXTRA_WEEKDAY = "weekday"
        const val EXTRA_PERIOD = "period"
        const val EXTRA_END_PERIOD = "end_period"
        const val EXTRA_WEEKS = "weeks"
        const val EXTRA_LESSON_NO = "lesson_no"
        const val EXTRA_START_TIME = "start_time"
        const val EXTRA_END_TIME = "end_time"
        const val EXTRA_START_EPOCH_SEC = "start_epoch_sec"
    }
}
