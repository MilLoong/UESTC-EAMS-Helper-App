package edu.uestc.eams.helper.data.prefs

import android.content.Context
import edu.uestc.eams.helper.domain.model.GradeItem
import edu.uestc.eams.helper.domain.grade.GradeStatsCalculator

/** 成绩均分 / 均绩各自勾选的课程，刷新后保留仍存在的项。 */
class GradeSelectionPreferences(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun syncWithCurrentGrades(
        currentKeys: Set<String>,
        items: List<GradeItem>,
    ): GradeSelectionState {
        val allKeys = currentKeys.ifEmpty { items.map { GradeStatsCalculator.stableKey(it) }.toSet() }
        val avg = normalize(prefs.getStringSet(KEY_AVG, null), allKeys)
        val gpa = normalize(prefs.getStringSet(KEY_GPA, null), allKeys)
        prefs.edit()
            .putStringSet(KEY_AVG, avg)
            .putStringSet(KEY_GPA, gpa)
            .apply()
        return GradeSelectionState(averageKeys = avg, gpaKeys = gpa)
    }

    fun saveAverageKeys(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_AVG, keys).apply()
    }

    fun saveGpaKeys(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_GPA, keys).apply()
    }

    private fun normalize(saved: Set<String>?, allKeys: Set<String>): Set<String> =
        when {
            allKeys.isEmpty() -> emptySet()
            saved == null -> allKeys
            else -> saved.intersect(allKeys)
        }

    data class GradeSelectionState(
        val averageKeys: Set<String>,
        val gpaKeys: Set<String>,
    )

    companion object {
        private const val PREF_NAME = "grade_selection_prefs"
        private const val KEY_AVG = "avg_keys"
        private const val KEY_GPA = "gpa_keys"
    }
}
