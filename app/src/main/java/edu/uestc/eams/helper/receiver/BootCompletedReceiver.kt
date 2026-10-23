package edu.uestc.eams.helper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import edu.uestc.eams.helper.notification.CourseReminderAlarmScheduler
import edu.uestc.eams.helper.server.ServerNotifications

/** 开机或覆盖安装后：本地模式重挂闹钟；服务器模式恢复前台接收。 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                val app = context.applicationContext
                CourseReminderAlarmScheduler.reschedule(app)
                runCatching { ServerNotifications.startReceiving(app) }
            }
        }
    }
}
