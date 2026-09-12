package edu.uestc.eams.helper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import edu.uestc.eams.helper.notification.CourseReminderAlarmScheduler

/** 开机或覆盖安装后重新挂上课提醒闹钟。 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> CourseReminderAlarmScheduler.reschedule(context.applicationContext)
        }
    }
}
