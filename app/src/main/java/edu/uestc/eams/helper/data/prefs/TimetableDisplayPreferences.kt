package edu.uestc.eams.helper.data.prefs

import android.content.Context

enum class TimetableCourseNameMode {
    FULL,
    COMPACT,
    ;

    fun displayOnCard(courseName: String): String {
        val name = courseName.trim()
        return when (this) {
            FULL -> name
            COMPACT -> name.take(COMPACT_CHAR_COUNT)
        }
    }

    companion object {
        const val COMPACT_CHAR_COUNT = 4

        fun fromStored(value: String?): TimetableCourseNameMode =
            entries.firstOrNull { it.name == value } ?: FULL
    }
}

data class TimetableLayoutSettings(
    val fontScale: Float = 1f,
    val rowHeightDp: Float = 50f,
    val dayColumnWidthDp: Float = 52f,
    val timeColumnWidthDp: Float = 38f,
    val showNoonDivider: Boolean = true,
    val courseNameMode: TimetableCourseNameMode = TimetableCourseNameMode.FULL,
    val gridMesh: Boolean = false,
    val courseCardBorder: Boolean = true,
) {
    fun coerce(): TimetableLayoutSettings =
        copy(
            fontScale = fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE),
            rowHeightDp = rowHeightDp.coerceIn(MIN_ROW_HEIGHT_DP, MAX_ROW_HEIGHT_DP),
            dayColumnWidthDp = dayColumnWidthDp.coerceIn(MIN_DAY_COLUMN_WIDTH_DP, MAX_DAY_COLUMN_WIDTH_DP),
            timeColumnWidthDp = timeColumnWidthDp.coerceIn(MIN_TIME_COLUMN_WIDTH_DP, MAX_TIME_COLUMN_WIDTH_DP),
        )

    fun scaledBy(factor: Float): TimetableLayoutSettings = scaledGridBy(factor)

    /** 双指缩放仅影响课程网格，不改变左侧节次列宽度。 */
    fun scaledGridBy(factor: Float): TimetableLayoutSettings = coerce().let { base ->
        if (factor == 1f) base
        else {
            copy(
                fontScale = base.fontScale * factor,
                rowHeightDp = base.rowHeightDp * factor,
                dayColumnWidthDp = base.dayColumnWidthDp * factor,
            ).coerce()
        }
    }

    fun isDefaultGridSize(): Boolean {
        val d = TimetableLayoutSettings().coerce()
        return fontScale == d.fontScale &&
            rowHeightDp == d.rowHeightDp &&
            dayColumnWidthDp == d.dayColumnWidthDp
    }

    fun resetGridSizeFrom(other: TimetableLayoutSettings): TimetableLayoutSettings {
        val d = TimetableLayoutSettings().coerce()
        return other.copy(
            fontScale = d.fontScale,
            rowHeightDp = d.rowHeightDp,
            dayColumnWidthDp = d.dayColumnWidthDp,
        ).coerce()
    }

    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.8f
        const val MIN_ROW_HEIGHT_DP = 36f
        const val MAX_ROW_HEIGHT_DP = 96f
        const val MIN_DAY_COLUMN_WIDTH_DP = 40f
        const val MAX_DAY_COLUMN_WIDTH_DP = 100f
        const val MIN_TIME_COLUMN_WIDTH_DP = 28f
        const val MAX_TIME_COLUMN_WIDTH_DP = 56f
    }
}

/** 课表首页排版：字号、行高、列宽。 */
class TimetableDisplayPreferences(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(): TimetableLayoutSettings =
        TimetableLayoutSettings(
            fontScale = prefs.getFloat(KEY_FONT_SCALE, 1f),
            rowHeightDp = prefs.getFloat(KEY_ROW_HEIGHT_DP, 50f),
            dayColumnWidthDp = prefs.getFloat(KEY_DAY_COLUMN_WIDTH_DP, 52f),
            timeColumnWidthDp = prefs.getFloat(KEY_TIME_COLUMN_WIDTH_DP, 38f),
            showNoonDivider = prefs.getBoolean(KEY_SHOW_NOON_DIVIDER, true),
            courseNameMode =
                TimetableCourseNameMode.fromStored(
                    prefs.getString(KEY_COURSE_NAME_MODE, TimetableCourseNameMode.FULL.name),
                ),
            gridMesh = prefs.getBoolean(KEY_GRID_MESH, false),
            courseCardBorder = prefs.getBoolean(KEY_COURSE_CARD_BORDER, true),
        ).coerce()

    fun save(settings: TimetableLayoutSettings) {
        val s = settings.coerce()
        prefs.edit()
            .putFloat(KEY_FONT_SCALE, s.fontScale)
            .putFloat(KEY_ROW_HEIGHT_DP, s.rowHeightDp)
            .putFloat(KEY_DAY_COLUMN_WIDTH_DP, s.dayColumnWidthDp)
            .putFloat(KEY_TIME_COLUMN_WIDTH_DP, s.timeColumnWidthDp)
            .putBoolean(KEY_SHOW_NOON_DIVIDER, s.showNoonDivider)
            .putString(KEY_COURSE_NAME_MODE, s.courseNameMode.name)
            .putBoolean(KEY_GRID_MESH, s.gridMesh)
            .putBoolean(KEY_COURSE_CARD_BORDER, s.courseCardBorder)
            .apply()
    }

    companion object {
        private const val PREF_NAME = "timetable_display_prefs"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_ROW_HEIGHT_DP = "row_height_dp"
        private const val KEY_DAY_COLUMN_WIDTH_DP = "day_column_width_dp"
        private const val KEY_TIME_COLUMN_WIDTH_DP = "time_column_width_dp"
        private const val KEY_SHOW_NOON_DIVIDER = "show_noon_divider"
        private const val KEY_COURSE_NAME_MODE = "course_name_mode"
        private const val KEY_GRID_MESH = "grid_mesh"
        private const val KEY_COURSE_CARD_BORDER = "course_card_border"
    }
}
