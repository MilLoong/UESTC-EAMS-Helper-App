package edu.uestc.eams.helper.data.prefs

import android.content.Context

/** 上课提醒提前分钟数。 */
class CourseReminderPreferences(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var leadMinutes: Int
        get() = prefs.getInt(KEY_LEAD_MINUTES, DEFAULT_LEAD_MINUTES).coerceIn(MIN_LEAD, MAX_LEAD)
        set(value) {
            prefs.edit().putInt(KEY_LEAD_MINUTES, value.coerceIn(MIN_LEAD, MAX_LEAD)).apply()
        }

    val leadSeconds: Int get() = leadMinutes * 60

    companion object {
        const val DEFAULT_LEAD_MINUTES = 20
        const val MIN_LEAD = 5
        const val MAX_LEAD = 60
        val presetMinutes: List<Int> = listOf(10, 15, 20, 30, 45, 60)

        private const val PREF_NAME = "course_reminder_prefs"
        private const val KEY_LEAD_MINUTES = "lead_minutes"
    }
}
