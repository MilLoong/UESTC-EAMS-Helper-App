package edu.uestc.eams.helper.domain.model

import java.time.LocalDate

/** 课表学期与周次状态。 */
data class TimetableMeta(
    val semesterCode: String,
    val currentWeek: Int,
    val displayWeek: Int,
    /** 第 1 教学周周一，ISO 日期 yyyy-MM-dd；树维导入时写入。 */
    val weekOneMonday: String? = null,
) {
    fun weekOneMondayDate(): LocalDate? =
        weekOneMonday?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

/** 指定日期落在哪一教学周（优先用第 1 周周一锚点，与顶栏日期一致）。 */
fun TimetableMeta.teachingWeekOn(date: LocalDate): Int {
    weekOneMondayDate()?.let { anchor ->
        val days = java.time.temporal.ChronoUnit.DAYS.between(anchor, date.with(java.time.DayOfWeek.MONDAY))
        if (days < 0) return 1
        return (days / 7 + 1).toInt().coerceAtLeast(1)
    }
    return currentWeek
}
