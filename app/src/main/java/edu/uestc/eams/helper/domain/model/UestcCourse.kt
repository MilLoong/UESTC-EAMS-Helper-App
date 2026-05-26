package edu.uestc.eams.helper.domain.model

/**
 * 从移动教务 / 教务 HTML 解析出的单条课表记录。
 * [weekday] 1=周一 … 7=周日；[period]、[endPeriod] 为节次（含端点）。
 */
data class UestcCourse(
    val courseName: String,
    val teacher: String,
    val room: String,
    val weekday: Int,
    val period: Int,
    val endPeriod: Int,
    val weeks: String,
    val courseId: String = "",
    val lessonNo: String = "",
    val courseType: String = "",
    /** 如 `08:30`，来自接口 `startTime`。 */
    val startTime: String = "",
    /** 如 `10:05`，来自接口 `endTime`。 */
    val endTime: String = "",
)

data class ExamItem(
    val courseName: String,
    val examTimeText: String,
    val room: String,
    val seat: String,
    val examType: String = "",
    /** 考试开始时刻（毫秒）；无法解析时为 null。 */
    val startEpochMillis: Long? = null,
) {
    fun countdownMillis(now: Long = System.currentTimeMillis()): Long? {
        val start = startEpochMillis ?: return null
        return start - now
    }
}

data class GradeItem(
    val courseName: String,
    val score: String,
    val credit: String,
    val gradePoint: String,
    val courseType: String = "",
    val semester: String = "",
    val examMode: String = "",
    val necessary: String = "",
    val courseCode: String = "",
    val passed: Boolean? = null,
)

data class GradesSummary(
    val items: List<GradeItem>,
    val gpa: String? = null,
    val totalCredits: String? = null,
)
