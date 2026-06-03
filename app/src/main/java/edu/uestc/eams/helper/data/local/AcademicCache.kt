package edu.uestc.eams.helper.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.uestc.eams.helper.domain.model.ExamItem
import edu.uestc.eams.helper.domain.model.GradeItem
import edu.uestc.eams.helper.domain.model.UestcCourse
import edu.uestc.eams.helper.domain.model.TimetableMeta
import edu.uestc.eams.helper.domain.model.UserProfile
import java.time.LocalDate

/** 课表、成绩、考试的本地缓存。 */
class AcademicCache(context: Context) {

    private val prefs = context.getSharedPreferences("academic_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveCourses(courses: List<UestcCourse>) {
        prefs.edit().putString(KEY_COURSES, gson.toJson(courses)).apply()
    }

    fun saveTimetableMeta(meta: TimetableMeta) {
        prefs.edit().putString(KEY_TIMETABLE_META, gson.toJson(meta)).apply()
    }

    fun loadTimetableMeta(): TimetableMeta? =
        prefs.getString(KEY_TIMETABLE_META, null)?.let {
            gson.fromJson(it, TimetableMeta::class.java)
        }

    fun loadCourses(): List<UestcCourse> =
        prefs.getString(KEY_COURSES, null)?.let {
            gson.fromJson(it, object : TypeToken<List<UestcCourse>>() {}.type)
        } ?: emptyList()

    /**
     * 课表 UI 用课程列表：树维导入为整表；在线模式为[已拉取过的各教学周]合并，
     * 与树维一样供 Pager 按周过滤，而不是只保留「当前周」一份。
     */
    fun loadTimetableCoursesForUi(): List<UestcCourse> {
        if (isOfflineImported()) return loadCourses()
        return rebuildMergedWeekCourses()
    }

    fun hasWeekCourses(semesterCode: String, week: Int): Boolean =
        loadWeekCourses(semesterCode, week) != null

    fun loadWeekCourses(semesterCode: String, week: Int): List<UestcCourse>? {
        ensureWeekCacheSemester(semesterCode)
        return loadWeekCourseMap()[weekKey(week)]
    }

    fun saveWeekCourses(semesterCode: String, week: Int, courses: List<UestcCourse>) {
        ensureWeekCacheSemester(semesterCode)
        val map = loadWeekCourseMap().toMutableMap()
        // 接口常带 1-16 等全学期周次，合并后按周过滤会致相邻周显示相同；按请求周强制打标
        map[weekKey(week)] = courses.map { it.copy(weeks = week.toString()) }
        prefs.edit().putString(KEY_WEEK_COURSES, gson.toJson(map)).apply()
        markWeekTimetableSyncedToday(semesterCode, week)
        if (!isOfflineImported()) {
            saveCourses(rebuildMergedWeekCourses())
        }
    }

    private fun rebuildMergedWeekCourses(): List<UestcCourse> =
        loadWeekCourseMap().values.flatten()

    /** 该教学周今天是否已同步过。 */
    fun wasWeekTimetableSyncedToday(semesterCode: String, week: Int): Boolean {
        val key = syncKey(semesterCode, week)
        return prefs.getString(KEY_WEEK_SYNC_KEY, null) == key &&
            prefs.getLong(KEY_WEEK_SYNC_DAY, -1L) == LocalDate.now().toEpochDay()
    }

    private fun markWeekTimetableSyncedToday(semesterCode: String, week: Int) {
        prefs.edit()
            .putString(KEY_WEEK_SYNC_KEY, syncKey(semesterCode, week))
            .putLong(KEY_WEEK_SYNC_DAY, LocalDate.now().toEpochDay())
            .apply()
    }

    private fun syncKey(semesterCode: String, week: Int): String = "$semesterCode|$week"

    /**
     * 学期代码变化时清空在线课表周缓存与合并课表，避免新学期仍显示旧数据。
     * 树维导入学期为 [IMPORT_SEMESTER]，与教务学期互切时同样会清空。
     */
    fun ensureWeekCacheSemester(semesterCode: String) {
        val stored = prefs.getString(KEY_WEEK_CACHE_SEMESTER, null)
        if (stored != semesterCode) {
            prefs.edit()
                .putString(KEY_WEEK_CACHE_SEMESTER, semesterCode)
                .remove(KEY_WEEK_COURSES)
                .remove(KEY_COURSES)
                .remove(KEY_WEEK_SYNC_KEY)
                .remove(KEY_WEEK_SYNC_DAY)
                .apply()
        }
    }

    private fun weekKey(week: Int): String = week.toString()

    private fun loadWeekCourseMap(): Map<String, List<UestcCourse>> =
        prefs.getString(KEY_WEEK_COURSES, null)?.let {
            gson.fromJson(it, object : TypeToken<Map<String, List<UestcCourse>>>() {}.type)
        } ?: emptyMap()

    fun saveGrades(items: List<GradeItem>) {
        prefs.edit().putString(KEY_GRADES, gson.toJson(items)).apply()
    }

    fun loadGrades(): List<GradeItem> =
        prefs.getString(KEY_GRADES, null)?.let {
            gson.fromJson(it, object : TypeToken<List<GradeItem>>() {}.type)
        } ?: emptyList()

    fun saveExams(items: List<ExamItem>) {
        prefs.edit().putString(KEY_EXAMS, gson.toJson(items)).apply()
    }

    fun loadExams(): List<ExamItem> =
        prefs.getString(KEY_EXAMS, null)?.let {
            gson.fromJson(it, object : TypeToken<List<ExamItem>>() {}.type)
        } ?: emptyList()

    fun saveUserProfile(profile: UserProfile) {
        prefs.edit().putString(KEY_PROFILE, gson.toJson(profile)).apply()
    }

    fun loadUserProfile(): UserProfile? =
        prefs.getString(KEY_PROFILE, null)?.let {
            gson.fromJson(it, UserProfile::class.java)
        }

    fun clearUserProfile() {
        prefs.edit().remove(KEY_PROFILE).apply()
    }

    fun setOfflineImported(imported: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_IMPORTED, imported).apply()
    }

    fun isOfflineImported(): Boolean = prefs.getBoolean(KEY_OFFLINE_IMPORTED, false)

    companion object {
        const val IMPORT_SEMESTER = "wakeup-import"
        private const val KEY_COURSES = "courses"
        private const val KEY_WEEK_COURSES = "week_courses_by_week"
        private const val KEY_WEEK_CACHE_SEMESTER = "week_courses_semester"
        private const val KEY_WEEK_SYNC_KEY = "week_timetable_sync_key"
        private const val KEY_WEEK_SYNC_DAY = "week_timetable_sync_day"
        private const val KEY_TIMETABLE_META = "timetable_meta"
        private const val KEY_GRADES = "grades"
        private const val KEY_EXAMS = "exams"
        private const val KEY_PROFILE = "user_profile"
        private const val KEY_OFFLINE_IMPORTED = "offline_wakeup_import"
    }
}
