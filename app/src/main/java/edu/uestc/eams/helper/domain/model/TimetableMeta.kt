package edu.uestc.eams.helper.domain.model

/** 课表学期与周次状态。 */
data class TimetableMeta(
    val semesterCode: String,
    val currentWeek: Int,
    val displayWeek: Int,
)
