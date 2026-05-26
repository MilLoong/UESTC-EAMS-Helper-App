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
