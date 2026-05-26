package edu.uestc.eams.helper.data.repository

import edu.uestc.eams.helper.data.auth.CasLoginRepository
import edu.uestc.eams.helper.data.eamsapp.EamsAppApi
import edu.uestc.eams.helper.data.eamsapp.EamsAppCookie
import edu.uestc.eams.helper.data.eamsapp.EamsAppTicketConsumer
import edu.uestc.eams.helper.data.local.AcademicCache
import edu.uestc.eams.helper.data.network.InMemoryCookieJar
import edu.uestc.eams.helper.data.parser.ExamJsonParser
import edu.uestc.eams.helper.data.parser.GradesJsonParser
import edu.uestc.eams.helper.data.parser.CourseWeekFilter
import edu.uestc.eams.helper.data.parser.TeachingWeekEstimator
import edu.uestc.eams.helper.data.parser.TimetableJsonParser
import edu.uestc.eams.helper.data.parser.WakeUpShuweiHtmlParser
import edu.uestc.eams.helper.data.session.SessionCookieStorage
import edu.uestc.eams.helper.data.auth.ReauthSmsSendOutcome
import edu.uestc.eams.helper.domain.model.ExamItem
import edu.uestc.eams.helper.domain.model.GradeItem
import edu.uestc.eams.helper.domain.model.GradesSummary
import edu.uestc.eams.helper.domain.model.TimetableMeta
import edu.uestc.eams.helper.domain.model.UestcCourse
import edu.uestc.eams.helper.domain.model.UserProfile
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** 课表、成绩、考试与登录会话的统一数据入口。 */
class UestcRepository(
    private val client: OkHttpClient,
    private val jar: InMemoryCookieJar,
    private val casRepository: CasLoginRepository,
    private val sessionStorage: SessionCookieStorage,
    private val cache: AcademicCache,
) {
    private val api = EamsAppApi(client)
    private val ticketConsumer = EamsAppTicketConsumer(client, jar)

    suspend fun probeSession(): Boolean =
        withContext(Dispatchers.IO) {
            cookieHeaderOrNull()?.let { api.probeSession(it) } == true
        }

    /** 重新发送登录短信验证码。 */
    suspend fun resendLoginSms(): Result<ReauthSmsSendOutcome> =
        withContext(Dispatchers.IO) {
            casRepository.resendReauthDynamicCode()
        }

    suspend fun login(
        username: String,
        password: String,
        smsCallback: suspend (prompt: String) -> String?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            casRepository.login(username, password, smsCallback)
                .mapCatching {
                    val hdr = ensureMobileCookieHeader()
                    sessionStorage.persistFromJar(jar)
                    refreshUserProfile(hdr).getOrThrow()
                    Unit
                }
        }

    /** 加载指定教学周课表；[forceNetwork] 为 false 时优先读本地缓存。 */
    suspend fun refreshTimetable(
        week: Int? = null,
        forceNetwork: Boolean = false,
    ): Result<List<UestcCourse>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!forceNetwork && cache.isOfflineImported()) {
                    val meta =
                        cache.loadTimetableMeta()
                            ?: throw IllegalStateException("暂无导入课表")
                    val displayWeek = week?.coerceAtLeast(1) ?: meta.displayWeek
                    cache.saveTimetableMeta(meta.copy(displayWeek = displayWeek))
                    return@runCatching cache.loadCourses()
                }
                val ck = ensureMobileCookieHeader()
                val semester = api.fetchCurSemesterCode(ck) ?: "25262"
                val currentWeek = api.fetchCurWeek(ck, semester) ?: 1
                val displayWeek = week?.coerceAtLeast(1) ?: currentWeek
                val meta =
                    TimetableMeta(
                        semesterCode = semester,
                        currentWeek = currentWeek,
                        displayWeek = displayWeek,
                    )

                if (!forceNetwork) {
                    cache.loadWeekCourses(semester, displayWeek)?.let { cached ->
                        cache.saveCourses(cached)
                        cache.saveTimetableMeta(meta)
                        return@runCatching cached
                    }
                }

                val code = api.resolveStudentCode(ck)
                val json =
                    api.fetchWeekTimetableJson(ck, semester, code, displayWeek.toString())
                        ?: throw IllegalStateException("课表接口无数据")
                val courses = TimetableJsonParser.parse(json)
                cache.saveWeekCourses(semester, displayWeek, courses)
                cache.saveCourses(courses)
                cache.saveTimetableMeta(meta)
                cache.setOfflineImported(false)
                courses
            }
        }

    fun hasCachedTimetableWeek(semesterCode: String, week: Int): Boolean =
        cache.hasWeekCourses(semesterCode, week)

    /** 后台每日同步当前教学周课表，当日已同步则跳过。 */
    suspend fun syncCurrentWeekTimetableIfNeeded(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!probeSession()) return@runCatching Unit
                val ck = ensureMobileCookieHeader()
                val semester = api.fetchCurSemesterCode(ck) ?: return@runCatching Unit
                val currentWeek = api.fetchCurWeek(ck, semester) ?: return@runCatching Unit
                if (
                    cache.wasWeekTimetableSyncedToday(semester, currentWeek) &&
                    cache.hasWeekCourses(semester, currentWeek)
                ) {
                    return@runCatching Unit
                }
                refreshTimetable(week = currentWeek, forceNetwork = true).getOrThrow()
                Unit
            }
        }

    suspend fun refreshGrades(): Result<GradesSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val ck = ensureMobileCookieHeader()
                val code = api.resolveStudentCode(ck)
                val json =
                    api.fetchGradesJson(ck, code)
                        ?: throw IllegalStateException("成绩接口无数据")
                GradesJsonParser.parse(json).also { cache.saveGrades(it.items) }
            }
        }

    suspend fun refreshExams(): Result<List<ExamItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val ck = ensureMobileCookieHeader()
                val semester = api.fetchCurSemesterCode(ck) ?: "25262"
                val json =
                    api.fetchExamQueryJson(ck, semester)
                        ?: throw IllegalStateException("考试接口无数据")
                ExamJsonParser.parse(json).also { cache.saveExams(it) }
            }
        }

    /** 登录成功后拉取资料、课表、成绩与考试。 */
    suspend fun refreshAllAfterLogin(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val hdr = ensureMobileCookieHeader()
                refreshUserProfile(hdr).getOrThrow()
                refreshTimetable(forceNetwork = true).getOrThrow()
                refreshGrades().getOrThrow()
                refreshExams().getOrThrow()
                Unit
            }
        }

    /** 按当前 Tab 只刷新对应数据。 */
    suspend fun refreshForTab(tab: Int, timetableDisplayWeek: Int?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (tab) {
                    0 ->
                        refreshTimetable(
                            week = timetableDisplayWeek,
                            forceNetwork = true,
                        ).getOrThrow()
                    1 -> refreshExams().getOrThrow()
                    2 -> refreshGrades().getOrThrow()
                    else -> refreshUserProfile().getOrThrow()
                }
                Unit
            }
        }

    suspend fun refreshUserProfile(cookieHeader: String? = null): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val hdr = cookieHeader ?: ensureMobileCookieHeader()
                val jwtProfile =
                    api.profileFromJwt(hdr)
                        ?: throw IllegalStateException("无法解析登录用户。")
                val bladeId = jwtProfile.bladeUserId
                val merged =
                    if (!bladeId.isNullOrBlank()) {
                        api.fetchAppProfile(hdr, bladeId) ?: jwtProfile
                    } else {
                        jwtProfile
                    }
                cache.saveUserProfile(merged)
                merged
            }
        }

    fun cachedUserProfile(): UserProfile? = cache.loadUserProfile()

    fun cachedCourses(): List<UestcCourse> = cache.loadCourses()
    fun cachedTimetableMeta(): TimetableMeta? = cache.loadTimetableMeta()
    fun isOfflineImported(): Boolean = cache.isOfflineImported()

    /** 从 WakeUp 导出的 HTML 导入课表并覆盖本地缓存；[weekOneMonday] 为第 1 教学周周一。 */
    suspend fun importWakeUpTimetableFile(
        fileText: String,
        weekOneMonday: LocalDate,
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsed = WakeUpShuweiHtmlParser.parse(fileText)
                val currentWeek =
                    TeachingWeekEstimator.estimateFromWeekOneMonday(
                        weekOneMonday,
                        parsed.maxWeek,
                    )
                val meta =
                    TimetableMeta(
                        semesterCode = AcademicCache.IMPORT_SEMESTER,
                        currentWeek = currentWeek,
                        displayWeek = currentWeek.coerceIn(1, parsed.maxWeek),
                        weekOneMonday = weekOneMonday.toString(),
                    )
                cache.saveCourses(parsed.courses)
                cache.saveTimetableMeta(meta)
                cache.setOfflineImported(true)
                for (w in 1..parsed.maxWeek) {
                    val weekCourses = CourseWeekFilter.filterForWeek(parsed.courses, w)
                    cache.saveWeekCourses(AcademicCache.IMPORT_SEMESTER, w, weekCourses)
                }
                parsed.courses.size
            }
        }

    /** 导入课表模式下仅切换显示周次。 */
    fun switchTimetableWeekLocal(week: Int): Boolean {
        if (!cache.isOfflineImported()) return false
        val meta = cache.loadTimetableMeta() ?: return false
        val w = week.coerceAtLeast(1)
        cache.saveTimetableMeta(meta.copy(displayWeek = w))
        return true
    }
    fun cachedGrades(): List<GradeItem> = cache.loadGrades()
    fun cachedExams(): List<ExamItem> = cache.loadExams()

    private fun cookieHeaderOrNull(): String? {
        EamsAppCookie.pickJwtFromJar(jar)?.let { return EamsAppCookie.composeFromJar(jar, it) }
        return null
    }

    private fun ensureMobileCookieHeader(): String {
        cookieHeaderOrNull()?.let { hdr ->
            if (api.probeSession(hdr)) return hdr
        }
        val jwt = ticketConsumer.ensureJwtAfterCas()
            ?: throw IllegalStateException("未建立移动教务会话，请先登录。")
        val hdr = EamsAppCookie.composeFromJar(jar, jwt)
        if (!api.probeSession(hdr)) {
            throw IllegalStateException("移动教务会话无效或已过期，请重新登录。")
        }
        return hdr
    }
}
