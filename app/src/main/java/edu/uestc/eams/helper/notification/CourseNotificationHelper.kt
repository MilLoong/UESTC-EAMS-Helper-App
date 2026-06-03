package edu.uestc.eams.helper.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import edu.uestc.eams.helper.MainActivity
import edu.uestc.eams.helper.R
import edu.uestc.eams.helper.data.mapper.UestcPeriodTime
import edu.uestc.eams.helper.data.prefs.CourseReminderPreferences
import edu.uestc.eams.helper.data.prefs.CourseReminderSentPreferences
import edu.uestc.eams.helper.domain.model.UestcCourse
import edu.uestc.eams.helper.worker.CourseNotificationWorker
import java.time.LocalDate

object CourseNotificationHelper {

    /** v2：提高重要性并默认震动，避免旧渠道在系统里被设为「仅静默通知」。 */
    const val CHANNEL_ID = "class_reminder_v2"
    private const val NOTIFY_ID_BASE = 42_001
    private const val DEBUG_NOTIFY_ID = 42_002

    enum class PreviewResult {
        Sent,
        NoPermission,
        NoCourses,
    }

    fun hasPostNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val lead = CourseReminderPreferences(context).leadMinutes
        val ch =
            NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "开课前 ${CourseReminderPreferences.formatLeadLabel(lead)} 内提醒"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 280, 120, 280)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        nm.createNotificationChannel(ch)
    }

    fun reminderKey(course: UestcCourse, date: LocalDate): String =
        listOf(
            date.toString(),
            course.weekday.toString(),
            course.period.toString(),
            course.endPeriod.toString(),
            course.courseName,
            course.lessonNo,
            course.room,
        ).joinToString("|")

    fun notifyIdForKey(reminderKey: String): Int =
        (NOTIFY_ID_BASE + reminderKey.hashCode() % 8000).let { id ->
            if (id == DEBUG_NOTIFY_ID) id + 1 else id
        }

    /**
     * @return true 已展示；false 无权限或今日已发过同一条
     */
    fun showClassReminder(
        context: Context,
        course: UestcCourse,
        date: LocalDate,
        minutesUntil: Int? = null,
        startTime: String = "",
        debug: Boolean = false,
    ): Boolean {
        if (!hasPostNotificationPermission(context)) return false
        ensureChannel(context)

        val reminderKey = reminderKey(course, date)
        if (!debug) {
            val sent = CourseReminderSentPreferences(context)
            if (sent.wasSentToday(reminderKey)) return false
        }

        val courseName = course.courseName.ifBlank { "（课程）" }
        val room = course.room.ifBlank { "[教室待定]" }
        val timing = buildTimingLine(minutesUntil, startTime, debug)
        val title = if (debug) "[调试] 上课提醒" else "上课提醒"
        val collapsedText = "$timing\n$courseName"
        val expandedText = "$timing\n$courseName\n教室：$room"

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(CourseNotificationWorker.EXTRA_OPEN_TAB, 0)
            }
        val notifyId = if (debug) DEBUG_NOTIFY_ID else notifyIdForKey(reminderKey)
        val pi =
            PendingIntent.getActivity(
                context,
                notifyId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(collapsedText)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(expandedText)
                        .setBigContentTitle(courseName),
                )
                .setContentIntent(pi)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

        NotificationManagerCompat.from(context).notify(notifyId, notification)
        if (!debug) {
            CourseReminderSentPreferences(context).markSentToday(reminderKey)
        }
        return true
    }

    fun showPreview(context: Context, courses: List<UestcCourse>): PreviewResult {
        val pool = courses.filter { it.courseName.isNotBlank() }
        if (pool.isEmpty()) return PreviewResult.NoCourses
        val sample = pool.random()
        val leadMinutes = CourseReminderPreferences(context).leadMinutes
        val sent =
            showClassReminder(
                context = context,
                course = sample,
                date = LocalDate.now(),
                minutesUntil = leadMinutes,
                startTime = UestcPeriodTime.resolvedStartTime(sample),
                debug = true,
            )
        return if (sent) PreviewResult.Sent else PreviewResult.NoPermission
    }

    private fun buildTimingLine(
        minutesUntil: Int?,
        startTime: String,
        debug: Boolean,
    ): String {
        val clock = startTime.trim()
        return when {
            debug && minutesUntil != null && minutesUntil > 0 -> {
                val lead = "提前 $minutesUntil 分钟"
                if (clock.isNotEmpty()) "$lead · $clock" else lead
            }
            minutesUntil != null && minutesUntil > 0 -> {
                val countdown = "$minutesUntil 分钟后开始"
                if (clock.isNotEmpty()) "$countdown · $clock" else countdown
            }
            minutesUntil == 0 -> if (clock.isNotEmpty()) "$clock 马上开始" else "马上开始"
            clock.isNotEmpty() -> "$clock 即将开始"
            else -> "即将开始"
        }
    }
}
