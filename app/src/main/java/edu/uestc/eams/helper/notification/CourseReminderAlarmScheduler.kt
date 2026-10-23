package edu.uestc.eams.helper.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import edu.uestc.eams.helper.MainActivity
import edu.uestc.eams.helper.data.local.AcademicCache
import edu.uestc.eams.helper.data.prefs.CourseReminderPreferences
import edu.uestc.eams.helper.data.prefs.CourseReminderSentPreferences
import edu.uestc.eams.helper.receiver.CourseReminderReceiver
import java.time.LocalDate

/**
 * 用 [AlarmManager.setAlarmClock] 按开课时间精确调度提醒。
 * 比 WorkManager 周期任务更接近 QQ 这类「到点就响」；状态栏可能短暂出现闹钟图标。
 */
object CourseReminderAlarmScheduler {

    private const val PREF = "course_reminder_alarms"
    private const val KEY_REQUEST_CODES = "request_codes"
    private const val MAX_ALARMS = 16

    fun reschedule(context: Context) {
        val app = context.applicationContext
        cancelAll(app)
        if (!edu.uestc.eams.helper.server.ServerNotifications.local(app)) return
        CourseNotificationHelper.ensureChannel(app)

        val cache = AcademicCache(app)
        val meta = cache.loadTimetableMeta()
        val leadSec = CourseReminderPreferences(app).leadSeconds.toLong()
        val nowSec = System.currentTimeMillis() / 1000L
        val sent = CourseReminderSentPreferences(app)
        val triggers =
            CourseReminderPlanner.listTriggers(
                cache = cache,
                meta = meta,
                today = LocalDate.now(),
                leadSeconds = leadSec,
                nowEpochSec = nowSec,
                horizonDays = 2,
            ).filter {
                !sent.wasSentToday(CourseNotificationHelper.reminderKey(it.course, it.date))
            }.take(MAX_ALARMS)
        if (triggers.isEmpty()) return

        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val codes = ArrayList<Int>(triggers.size)
        val nowMs = System.currentTimeMillis()
        for (t in triggers) {
            val key = CourseNotificationHelper.reminderKey(t.course, t.date)
            val requestCode = requestCodeFor(key)
            codes += requestCode
            var triggerAtMs = t.triggerEpochSec * 1000L
            if (triggerAtMs < nowMs + 1_500L) {
                triggerAtMs = nowMs + 1_500L
            }
            val firePi = firePendingIntent(app, requestCode, t, key)
            val showPi =
                PendingIntent.getActivity(
                    app,
                    requestCode xor 0x5A5A5A5A,
                    Intent(app, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, showPi), firePi)
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, firePi)
            }
        }
        app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_REQUEST_CODES, codes.map { it.toString() }.toSet())
            .apply()
    }

    fun cancelAll(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val codes = prefs.getStringSet(KEY_REQUEST_CODES, emptySet()).orEmpty()
        for (raw in codes) {
            val code = raw.toIntOrNull() ?: continue
            am.cancel(firePendingIntent(app, code, trigger = null, reminderKey = "cancel-$code"))
        }
        prefs.edit().remove(KEY_REQUEST_CODES).apply()
    }

    private fun firePendingIntent(
        context: Context,
        requestCode: Int,
        trigger: CourseReminderPlanner.ScheduledTrigger?,
        reminderKey: String,
    ): PendingIntent {
        val intent =
            Intent(context, CourseReminderReceiver::class.java).apply {
                action = CourseReminderReceiver.ACTION_FIRE
                putExtra(CourseReminderReceiver.EXTRA_REMINDER_KEY, reminderKey)
                if (trigger != null) {
                    putExtra(CourseReminderReceiver.EXTRA_DATE, trigger.date.toString())
                    putExtra(CourseReminderReceiver.EXTRA_COURSE_NAME, trigger.course.courseName)
                    putExtra(CourseReminderReceiver.EXTRA_ROOM, trigger.course.room)
                    putExtra(CourseReminderReceiver.EXTRA_TEACHER, trigger.course.teacher)
                    putExtra(CourseReminderReceiver.EXTRA_WEEKDAY, trigger.course.weekday)
                    putExtra(CourseReminderReceiver.EXTRA_PERIOD, trigger.course.period)
                    putExtra(CourseReminderReceiver.EXTRA_END_PERIOD, trigger.course.endPeriod)
                    putExtra(CourseReminderReceiver.EXTRA_WEEKS, trigger.course.weeks)
                    putExtra(CourseReminderReceiver.EXTRA_LESSON_NO, trigger.course.lessonNo)
                    putExtra(CourseReminderReceiver.EXTRA_START_TIME, trigger.course.startTime)
                    putExtra(CourseReminderReceiver.EXTRA_END_TIME, trigger.course.endTime)
                    putExtra(CourseReminderReceiver.EXTRA_START_EPOCH_SEC, trigger.startEpochSec)
                }
            }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCodeFor(reminderKey: String): Int {
        var h = reminderKey.hashCode()
        if (h == Int.MIN_VALUE) h = 0
        return 0x71000000 or (kotlin.math.abs(h) % 0x00FFFFFF)
    }
}
