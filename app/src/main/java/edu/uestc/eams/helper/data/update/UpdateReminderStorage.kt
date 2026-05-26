package edu.uestc.eams.helper.data.update

import android.content.Context
import java.util.concurrent.TimeUnit

/** 更新检查间隔与「稍后」记录。 */
class UpdateReminderStorage(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun shouldCheckNow(): Boolean {
        val last = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        return System.currentTimeMillis() - last >= CHECK_INTERVAL_MS
    }

    fun markCheckedNow() {
        prefs.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply()
    }

    fun isDismissed(releaseTag: String): Boolean =
        prefs.getString(KEY_DISMISSED_TAG, null) == releaseTag

    fun dismiss(releaseTag: String) {
        prefs.edit().putString(KEY_DISMISSED_TAG, releaseTag).apply()
    }

    companion object {
        private const val PREF_NAME = "app_update_reminder"
        private const val KEY_LAST_CHECK_MS = "last_check_ms"
        private const val KEY_DISMISSED_TAG = "dismissed_release_tag"
        private val CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(12)
    }
}
