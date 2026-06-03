package edu.uestc.eams.helper.data.prefs

import android.content.Context
import java.time.LocalDate

/** 记录已发送的上课提醒，避免定时任务反复覆盖同一条通知。 */
class CourseReminderSentPreferences(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun wasSentToday(key: String): Boolean {
        pruneStale()
        return prefs.getString(sentKey(key), null) == todayKey()
    }

    fun markSentToday(key: String) {
        pruneStale()
        prefs.edit().putString(sentKey(key), todayKey()).apply()
    }

    private fun sentKey(reminderKey: String): String = "sent|$reminderKey"

    private fun todayKey(): String = LocalDate.now().toString()

    private fun pruneStale() {
        val today = todayKey()
        val stale =
            prefs.all.keys.filter { key ->
                key.startsWith("sent|") && prefs.getString(key, null) != today
            }
        if (stale.isEmpty()) return
        prefs.edit().apply {
            stale.forEach { remove(it) }
            apply()
        }
    }

    companion object {
        private const val PREFS = "course_reminder_sent"
    }
}
