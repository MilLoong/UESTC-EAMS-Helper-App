package edu.uestc.eams.helper.domain.model

/** 单条课表记录。 */
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
    val startTime: String = "",
    val endTime: String = "",
)

data class ExamItem(
    val courseName: String,
    val examTimeText: String,
    val room: String,
    val seat: String,
    val examType: String = "",
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
