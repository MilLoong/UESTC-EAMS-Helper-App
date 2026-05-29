package edu.uestc.eams.helper.data.prefs

import android.content.Context

/** 上课提醒提前分钟数。 */
class CourseReminderPreferences(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var leadMinutes: Int
        get() = prefs.getInt(KEY_LEAD_MINUTES, DEFAULT_LEAD_MINUTES)
            .coerceIn(MIN_LEAD_MINUTES, MAX_LEAD_MINUTES)
        set(value) {
            prefs.edit()
                .putInt(KEY_LEAD_MINUTES, value.coerceIn(MIN_LEAD_MINUTES, MAX_LEAD_MINUTES))
                .apply()
        }

    val leadSeconds: Int get() = leadMinutes * 60

    companion object {
        const val DEFAULT_LEAD_MINUTES = 20
        const val MIN_LEAD_MINUTES = 10
        const val MINUTES_PER_DAY = 24 * 60
        const val MAX_LEAD_MINUTES = MINUTES_PER_DAY

        val presetMinutes: List<Int> =
            listOf(10, 15, 20, 30, 60, 120, 360, 720, MINUTES_PER_DAY)
                .filter { it in MIN_LEAD_MINUTES..MAX_LEAD_MINUTES }

        fun formatLeadLabel(minutes: Int): String =
            when {
                minutes >= MINUTES_PER_DAY && minutes % MINUTES_PER_DAY == 0 -> {
                    val days = minutes / MINUTES_PER_DAY
                    if (days == 1) "1 天" else "$days 天"
                }
                minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60} 小时"
                else -> "$minutes 分钟"
            }

        private const val PREF_NAME = "course_reminder_prefs"
        private const val KEY_LEAD_MINUTES = "lead_minutes"
    }
}
