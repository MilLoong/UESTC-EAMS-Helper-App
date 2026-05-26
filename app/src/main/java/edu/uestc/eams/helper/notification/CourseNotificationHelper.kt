package edu.uestc.eams.helper.notification

import android.Manifest
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
import edu.uestc.eams.helper.domain.model.UestcCourse
import edu.uestc.eams.helper.worker.CourseNotificationWorker

object CourseNotificationHelper {

    const val CHANNEL_ID = "class_reminder"
    private const val NOTIFY_ID = 42001
    private const val DEBUG_NOTIFY_ID = 42002

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
        val ch =
            NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "开课前约 20 分钟提醒"
            }
        nm.createNotificationChannel(ch)
    }

    /**
     * @param minutesUntil 距开课分钟数（正式提醒由 Worker 按实际时间差计算）。
     * @param startTime 开课时刻，如 `10:20`；有则写入正文。
     */
    fun showClassReminder(
        context: Context,
        courseName: String,
        room: String,
        minutesUntil: Int? = null,
        startTime: String = "",
        debug: Boolean = false,
    ): Boolean {
        if (!hasPostNotificationPermission(context)) return false
        ensureChannel(context)

        val title = buildTitle(courseName, debug)
        val body = buildBody(room, minutesUntil, startTime, debug)

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(CourseNotificationWorker.EXTRA_OPEN_TAB, 0)
            }
        val pi =
            PendingIntent.getActivity(
                context,
                if (debug) 1 else 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        NotificationManagerCompat.from(context).notify(
            if (debug) DEBUG_NOTIFY_ID else NOTIFY_ID,
            notification,
        )
        return true
    }

    /** 调试：从本地课表随机抽一门课；无课表数据时不发通知。 */
    fun showPreview(context: Context, courses: List<UestcCourse>): PreviewResult {
        val pool = courses.filter { it.courseName.isNotBlank() }
        if (pool.isEmpty()) return PreviewResult.NoCourses
        val sample = pool.random()
        val room = sample.room.ifBlank { "（教室待定）" }
        val sent =
            showClassReminder(
                context = context,
                courseName = sample.courseName,
                room = room,
                minutesUntil = 15,
                startTime = UestcPeriodTime.resolvedStartTime(sample),
                debug = true,
            )
        return if (sent) PreviewResult.Sent else PreviewResult.NoPermission
    }

    private fun buildTitle(courseName: String, debug: Boolean): String {
        val prefix = if (debug) "（调试）" else ""
        return "${prefix}📌 上课提醒：$courseName 即将开始！"
    }

    private fun buildBody(
        room: String,
        minutesUntil: Int?,
        startTime: String,
        debug: Boolean,
    ): String {
        val loc = room.trim().ifEmpty { "（教室待定）" }
        val clock = startTime.trim()
        val timing =
            when {
                minutesUntil != null && minutesUntil > 0 -> {
                    val countdown = "${minutesUntil} 分钟后开始"
                    if (clock.isNotEmpty()) "$countdown（$clock）" else countdown
                }
                minutesUntil == 0 -> if (clock.isNotEmpty()) "$clock 马上开始" else "马上开始"
                debug ->
                    if (clock.isNotEmpty()) {
                        "15 分钟后开始（$clock，调试预览）"
                    } else {
                        "15 分钟后开始（调试预览）"
                    }
                clock.isNotEmpty() -> "$clock 即将开始"
                else -> "即将开始"
            }
        return "$timing\n教室：$loc"
    }
}
