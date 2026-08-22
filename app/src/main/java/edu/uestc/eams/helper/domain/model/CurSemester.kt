package edu.uestc.eams.helper.domain.model

import java.time.LocalDate

/** 移动教务 getCurSemester 解析结果。 */
data class CurSemester(
    val code: String,
    val year: String? = null,
    val name: String? = null,
    /** 学期开始日（接口 startOn）；第 1 教学周按该日所在周的周一对齐。 */
    val startOn: LocalDate? = null,
    val endOn: LocalDate? = null,
    val firstWeek: Int? = null,
    val weeks: Int? = null,
)
