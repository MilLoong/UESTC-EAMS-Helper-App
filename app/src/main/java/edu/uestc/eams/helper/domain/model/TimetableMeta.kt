package edu.uestc.eams.helper.domain.model

/** 课表周次与学期（与接口 getCurWeek / week 参数对齐）。 */
data class TimetableMeta(
    val semesterCode: String,
    /** 服务端当前教学周。 */
    val currentWeek: Int,
    /** 正在查看的周（可左右切换）。 */
    val displayWeek: Int,
)
