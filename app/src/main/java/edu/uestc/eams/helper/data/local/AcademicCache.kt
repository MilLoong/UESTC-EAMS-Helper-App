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
     * 课表 UI 用课程列表：树维导入为整表；在线模式为[该学期已拉取过的各教学周]合并。
     * 周课表按「学期|周」存储，多个学期可并存，切换学期不清空其它学期。
     */
    fun loadTimetableCoursesForUi(semesterCode: String): List<UestcCourse> {
        if (isOfflineImported()) return loadCourses()
        if (semesterCode.isBlank()) return emptyList()
        return rebuildMergedWeekCourses(semesterCode)
    }

    fun hasWeekCourses(semesterCode: String, week: Int): Boolean =
        loadWeekCourses(semesterCode, week) != null

    fun loadWeekCourses(semesterCode: String, week: Int): List<UestcCourse>? =
        loadWeekCourseMap()[weekCacheKey(semesterCode, week)]

    fun saveWeekCourses(semesterCode: String, week: Int, courses: List<UestcCourse>) {
        val map = loadWeekCourseMap().toMutableMap()
        // 接口常带 1-16 等全学期周次，合并后按周过滤会致相邻周显示相同；按请求周强制打标
        map[weekCacheKey(semesterCode, week)] = courses.map { it.copy(weeks = week.toString()) }
        prefs.edit().putString(KEY_WEEK_COURSES, gson.toJson(map)).apply()
        markWeekTimetableSyncedToday(semesterCode, week)
    }

    private fun rebuildMergedWeekCourses(semesterCode: String): List<UestcCourse> =
        loadWeekCourseMap()
            .filterKeys { it.startsWith("$semesterCode|") }
            .values
            .flatten()

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

    private fun weekCacheKey(semesterCode: String, week: Int): String = "$semesterCode|$week"

    /**
     * 读取周课表映射，并把旧版「仅周号」的键迁移为「学期|周」，避免升级后丢缓存。
     */
    private fun loadWeekCourseMap(): Map<String, List<UestcCourse>> {
        val raw = prefs.getString(KEY_WEEK_COURSES, null) ?: return emptyMap()
        val map: Map<String, List<UestcCourse>> =
            gson.fromJson(raw, object : TypeToken<Map<String, List<UestcCourse>>>() {}.type)
                ?: return emptyMap()
        if (map.isEmpty()) return map
        val legacySemester = prefs.getString(KEY_WEEK_CACHE_SEMESTER, null)
        val needsMigrate = legacySemester != null && map.keys.any { !it.contains("|") }
        if (!needsMigrate) return map
        val migrated: LinkedHashMap<String, List<UestcCourse>> = LinkedHashMap()
        for ((k, v) in map) {
            val newKey = if (k.contains("|")) k else "$legacySemester|$k"
            migrated[newKey] = v
        }
        prefs.edit()
            .putString(KEY_WEEK_COURSES, gson.toJson(migrated))
            .remove(KEY_WEEK_CACHE_SEMESTER)
            .apply()
        return migrated
    }

    fun saveGrades(items: List<GradeItem>) {
        prefs.edit().putString(KEY_GRADES, gson.toJson(items)).apply()
    }

    fun loadGrades(): List<GradeItem> =
        prefs.getString(KEY_GRADES, null)?.let {
            gson.fromJson(it, object : TypeToken<List<GradeItem>>() {}.type)
        } ?: emptyList()

    /** 按学期保存考试列表；key 为学期编码。 */
    fun saveExams(semester: String, items: List<ExamItem>) {
        val map = examsBySemester().toMutableMap()
        map[semester] = items
        prefs.edit().putString(KEY_EXAMS_BY_SEMESTER, gson.toJson(map)).apply()
    }

    /** 读取某学期考试；若还没有按学期存过，则回退到旧版整表缓存。 */
    fun loadExams(semester: String): List<ExamItem> {
        val map = examsBySemester()
        if (map.isEmpty()) {
            // 旧版整表缓存：视为最近一次使用的（当前）学期数据
            return legacyFlatExams()
        }
        return map[semester] ?: emptyList()
    }

    fun examSemesters(): List<String> = examsBySemester().keys.sortedDescending()

    private fun examsBySemester(): Map<String, List<ExamItem>> =
        prefs.getString(KEY_EXAMS_BY_SEMESTER, null)?.let {
            gson.fromJson(it, object : TypeToken<Map<String, List<ExamItem>>>() {}.type)
        } ?: emptyMap()

    private fun legacyFlatExams(): List<ExamItem> =
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
        private const val KEY_EXAMS_BY_SEMESTER = "exams_by_semester"
        private const val KEY_PROFILE = "user_profile"
        private const val KEY_OFFLINE_IMPORTED = "offline_wakeup_import"
    }
}
