package edu.uestc.eams.helper.data.repository

import edu.uestc.eams.helper.data.auth.CasLoginRepository
import edu.uestc.eams.helper.data.eamsapp.EamsAppApi
import edu.uestc.eams.helper.data.eamsapp.EamsAppCookie
import edu.uestc.eams.helper.data.eamsapp.EamsAppTicketConsumer
import edu.uestc.eams.helper.data.local.AcademicCache
import edu.uestc.eams.helper.data.network.EamsFetchException
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

    /** 本地是否已有移动教务 JWT，不发起网络请求。 */
    fun hasLocalSession(): Boolean = cookieHeaderOrNull() != null

    /** 清除本地 Cookie 与登录资料，不清课表/成绩缓存。 */
    fun clearLoginSession() {
        sessionStorage.clearJarAndPersistence(jar)
        cache.clearUserProfile()
    }

    /** 联网探测会话是否仍有效；一般不再用于前台判断。 */
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
        semesterCode: String? = null,
    ): Result<List<UestcCourse>> =
        runUserDataFetch {
            withContext(Dispatchers.IO) {
                if (!forceNetwork && cache.isOfflineImported()) {
                    val meta =
                        cache.loadTimetableMeta()
                            ?: throw IllegalStateException("暂无导入课表")
                    val displayWeek = week?.coerceAtLeast(1) ?: meta.displayWeek
                    cache.saveTimetableMeta(meta.copy(displayWeek = displayWeek))
                    return@withContext cache.loadCourses()
                }
                val ck = ensureMobileCookieHeader()
                val semester =
                    semesterCode
                        ?: api.fetchCurSemesterCode(ck)
                        ?: cache.loadTimetableMeta()?.semesterCode
                        ?: "25262"
                val currentWeek = api.fetchCurWeek(ck, semester) ?: 1
                val displayWeek = week?.coerceAtLeast(1) ?: currentWeek
                val prior = cache.loadTimetableMeta()
                val bumpDisplay =
                    week == null &&
                        prior != null &&
                        prior.semesterCode == semester &&
                        prior.displayWeek == prior.currentWeek &&
                        prior.currentWeek != currentWeek
                val resolvedDisplay = if (bumpDisplay) currentWeek else displayWeek
                val sameSemester = prior?.semesterCode == semester
                val weekOneMonday =
                    TeachingWeekEstimator.resolvePersistedWeekOneMonday(
                        stored = prior?.takeIf { sameSemester }?.weekOneMondayDate(),
                        sameSemester = sameSemester,
                        apiWeek = currentWeek,
                        userLocked = sameSemester && prior?.weekOneLocked == true,
                    ).toString()
                val meta =
                    TimetableMeta(
                        semesterCode = semester,
                        currentWeek = currentWeek,
                        displayWeek = resolvedDisplay,
                        weekOneMonday = weekOneMonday,
                        weekOneLocked = sameSemester && prior?.weekOneLocked == true,
                    )

                val fetchWeek = resolvedDisplay
                if (!forceNetwork) {
                    cache.loadWeekCourses(semester, fetchWeek)?.let {
                        cache.saveTimetableMeta(meta)
                        return@withContext cache.loadTimetableCoursesForUi(semester)
                    }
                }

                val code = api.resolveStudentCode(ck)
                val json =
                    api.fetchWeekTimetableJson(ck, semester, code, fetchWeek.toString())
                        ?: throw IllegalStateException("课表接口无数据")
                val courses = TimetableJsonParser.parse(json)
                cache.saveWeekCourses(semester, fetchWeek, courses)
                cache.saveTimetableMeta(meta)
                cache.setOfflineImported(false)
                cache.loadTimetableCoursesForUi(semester)
            }
        }

    fun hasCachedTimetableWeek(semesterCode: String, week: Int): Boolean =
        cache.hasWeekCourses(semesterCode, week)

    /**
     * 轻量同步当前教学周（不拉课表）。用于回到前台或点 [本周] 时纠正周次与第 1 周周一锚点。
     * @return 更新后的 meta；无法同步时 null
     */
    suspend fun syncCurrentTeachingWeek(): TimetableMeta? =
        withContext(Dispatchers.IO) {
            if (!hasLocalSession() || cache.isOfflineImported()) return@withContext null
            runCatching {
                val ck = ensureMobileCookieHeader()
                val semester =
                    api.fetchCurSemesterCode(ck)
                        ?: cache.loadTimetableMeta()?.semesterCode
                        ?: return@runCatching null
                val apiWeek = api.fetchCurWeek(ck, semester) ?: return@runCatching null
                val prior = cache.loadTimetableMeta()
                val sameSemester = prior?.semesterCode == semester
                val weekOneMonday =
                    TeachingWeekEstimator.resolvePersistedWeekOneMonday(
                        stored = prior?.takeIf { sameSemester }?.weekOneMondayDate(),
                        sameSemester = sameSemester,
                        apiWeek = apiWeek,
                        userLocked = sameSemester && prior?.weekOneLocked == true,
                    ).toString()
                val calendarWeek =
                    LocalDate.parse(weekOneMonday).let { anchor ->
                        TeachingWeekEstimator.teachingWeekForDate(anchor, LocalDate.now())
                    }
                val currentWeek = maxOf(apiWeek, calendarWeek)
                val bumpDisplay =
                    prior != null &&
                        sameSemester &&
                        prior.displayWeek == prior.currentWeek &&
                        prior.currentWeek != currentWeek
                val displayWeek =
                    if (bumpDisplay) {
                        currentWeek
                    } else {
                        prior?.displayWeek ?: currentWeek
                    }
                val meta =
                    TimetableMeta(
                        semesterCode = semester,
                        currentWeek = currentWeek,
                        displayWeek = displayWeek,
                        weekOneMonday = weekOneMonday,
                        weekOneLocked = sameSemester && prior?.weekOneLocked == true,
                    )
                cache.saveTimetableMeta(meta)
                meta
            }.getOrNull()
        }

    /** 后台每日同步当前教学周课表：当日已同步且本地有缓存则不联网；无本地会话则跳过。 */
    suspend fun syncCurrentWeekTimetableIfNeeded(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val meta = cache.loadTimetableMeta()
                if (meta != null) {
                    val semester = meta.semesterCode
                    val week = meta.currentWeek
                    if (
                        semester.isNotBlank() &&
                        cache.wasWeekTimetableSyncedToday(semester, week) &&
                        cache.hasWeekCourses(semester, week)
                    ) {
                        return@runCatching Unit
                    }
                }
                if (!hasLocalSession()) return@runCatching Unit
                refreshTimetable(week = meta?.currentWeek, forceNetwork = true).getOrNull()
                Unit
            }
        }

    suspend fun refreshGrades(): Result<GradesSummary> =
        runUserDataFetch {
            withContext(Dispatchers.IO) {
                val ck = ensureMobileCookieHeader()
                val code = api.resolveStudentCode(ck)
                val json =
                    api.fetchGradesJson(ck, code)
                        ?: throw IllegalStateException("成绩接口无数据")
                GradesJsonParser.parse(json).also { cache.saveGrades(it.items) }
            }
        }

    suspend fun refreshExams(semester: String? = null): Result<List<ExamItem>> =
        runUserDataFetch {
            withContext(Dispatchers.IO) {
                val ck = ensureMobileCookieHeader()
                val sem = semester ?: (api.fetchCurSemesterCode(ck) ?: "25262")
                val json =
                    api.fetchExamQueryJson(ck, sem)
                        ?: throw IllegalStateException("考试接口无数据")
                ExamJsonParser.parse(json)
                    .map { it.copy(semester = sem) }
                    .also { cache.saveExams(sem, it) }
            }
        }

    /** 登录成功后拉取资料、课表、成绩与考试。 */
    suspend fun refreshAllAfterLogin(): Result<Unit> =
        runUserDataFetch {
            val hdr = ensureMobileCookieHeader()
            refreshUserProfile(hdr).getOrThrow()
            refreshTimetable(forceNetwork = true).getOrThrow()
            refreshGrades().getOrThrow()
            refreshExams().getOrThrow()
            Unit
        }

    /** 按当前 Tab 只刷新对应数据。 */
    suspend fun refreshForTab(
        tab: Int,
        timetableDisplayWeek: Int?,
        semesterCode: String? = null,
    ): Result<Unit> =
        runUserDataFetch {
            when (tab) {
                0 ->
                    refreshTimetable(
                        week = timetableDisplayWeek,
                        forceNetwork = true,
                        semesterCode = semesterCode,
                    ).getOrThrow()
                1 -> {
                    if (semesterCode.isNullOrBlank()) {
                        refreshExams().getOrThrow()
                    } else {
                        refreshExams(semesterCode).getOrThrow()
                    }
                }
                2 -> refreshGrades().getOrThrow()
                else -> refreshUserProfile().getOrThrow()
            }
            Unit
        }

    suspend fun refreshUserProfile(cookieHeader: String? = null): Result<UserProfile> =
        runUserDataFetch {
            withContext(Dispatchers.IO) {
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

    fun cachedCourses(semesterCode: String): List<UestcCourse> =
        if (semesterCode.isBlank()) emptyList() else cache.loadTimetableCoursesForUi(semesterCode)
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
                        weekOneLocked = true,
                    )
                cache.setOfflineImported(true)
                cache.saveTimetableMeta(meta)
                for (w in 1..parsed.maxWeek) {
                    val weekCourses = CourseWeekFilter.filterForWeek(parsed.courses, w)
                    cache.saveWeekCourses(AcademicCache.IMPORT_SEMESTER, w, weekCourses)
                }
                cache.saveCourses(parsed.courses)
                parsed.courses.size
            }
        }

    /**
     * 仅切换教学周：树维导入只改 displayWeek；在线模式从周缓存读课表。
     * @return 是否已有该周课表缓存（false 时需再请求网络）
     */
    fun switchTimetableWeekLocal(week: Int): Boolean {
        val meta = cache.loadTimetableMeta() ?: return false
        val w = week.coerceAtLeast(1)
        if (cache.isOfflineImported()) {
            cache.saveTimetableMeta(meta.copy(displayWeek = w))
            return true
        }
        cache.saveTimetableMeta(meta.copy(displayWeek = w))
        return cache.hasWeekCourses(meta.semesterCode, w)
    }

    fun applyWeekOneMonday(selectedDay: LocalDate): TimetableMeta {
        val meta =
            cache.loadTimetableMeta()
                ?: TimetableMeta(semesterCode = "", currentWeek = 1, displayWeek = 1)
        val updated = TeachingWeekEstimator.alignTimetableMeta(meta, selectedDay)
        cache.saveTimetableMeta(updated)
        return updated
    }

    fun cachedGrades(): List<GradeItem> = cache.loadGrades()
    fun cachedExams(semester: String): List<ExamItem> = cache.loadExams(semester)

    /** 联网获取当前学期编码；无会话或失败时返回 null。 */
    suspend fun fetchCurrentSemesterCode(): String? =
        withContext(Dispatchers.IO) {
            if (!hasLocalSession()) return@withContext null
            runCatching {
                val ck = ensureMobileCookieHeader()
                api.fetchCurSemesterCode(ck)
            }.getOrNull()
        }

    private fun cookieHeaderOrNull(): String? {
        EamsAppCookie.pickJwtFromJar(jar)?.let { return EamsAppCookie.composeFromJar(jar, it) }
        return null
    }

    private fun ensureMobileCookieHeader(): String {
        cookieHeaderOrNull()?.let { return it }
        val jwt =
            ticketConsumer.ensureJwtAfterCas()
                ?: throw IllegalStateException("未建立移动教务会话，请先登录。")
        val hdr = EamsAppCookie.composeFromJar(jar, jwt)
        sessionStorage.persistFromJar(jar)
        return hdr
    }

    private suspend fun <T> runUserDataFetch(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: Throwable) {
            val mapped = UserDataFetchErrors.map(e)
            // 登录失效时不自动清空会话与缓存：学校会话经常过期，应默认沿用本地数据，
            // 仅在用户主动重新登录或点刷新后提示，避免反复让用户重新登录。
            Result.failure(mapped)
        }
}
