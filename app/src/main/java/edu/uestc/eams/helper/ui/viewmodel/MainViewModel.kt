package edu.uestc.eams.helper.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.uestc.eams.helper.BuildConfig
import edu.uestc.eams.helper.EamsHelperApp
import edu.uestc.eams.helper.data.prefs.CourseReminderPreferences
import edu.uestc.eams.helper.data.prefs.TimetableDisplayPreferences
import edu.uestc.eams.helper.data.prefs.TimetableLayoutSettings
import edu.uestc.eams.helper.data.prefs.GradeSelectionPreferences
import edu.uestc.eams.helper.data.prefs.ThemePreferences
import edu.uestc.eams.helper.data.update.AppUpdateChecker
import edu.uestc.eams.helper.data.update.UpdateReminderStorage
import edu.uestc.eams.helper.data.auth.CasLoginRepository
import edu.uestc.eams.helper.data.auth.ReauthSmsSendOutcome
import edu.uestc.eams.helper.data.auth.LoginUserMessages
import edu.uestc.eams.helper.domain.grade.GradeStatsCalculator
import edu.uestc.eams.helper.domain.model.ExamItem
import edu.uestc.eams.helper.domain.model.GradeItem
import edu.uestc.eams.helper.domain.model.TimetableMeta
import edu.uestc.eams.helper.domain.model.teachingWeekOn
import edu.uestc.eams.helper.domain.model.UestcCourse
import edu.uestc.eams.helper.domain.model.UserProfile
import edu.uestc.eams.helper.data.network.EamsFetchException
import edu.uestc.eams.helper.data.parser.TeachingWeekEstimator
import edu.uestc.eams.helper.data.parser.WakeUpShuweiHtmlParser
import edu.uestc.eams.helper.notification.CourseNotificationHelper
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

data class MainUiState(
    val contentLoading: Boolean = false,
    val message: String? = null,
    val loggedIn: Boolean = false,
    val selectedTab: Int = 0,
    val courses: List<UestcCourse> = emptyList(),
    val timetableMeta: TimetableMeta? = null,
    val exams: List<ExamItem> = emptyList(),
    val grades: List<GradeItem> = emptyList(),
    val semesterOptions: List<String> = emptyList(),
    val currentSemesterCode: String? = null,
    val scheduleSemester: String? = null,
    val examSemester: String? = null,
    val showLogin: Boolean = false,
    val loginDeferred: Boolean = false,
    val loginStatus: String? = null,
    val awaitingSms: Boolean = false,
    val smsResendSecondsLeft: Int = 0,
    val userProfile: UserProfile? = null,
    val updatePrompt: UpdatePrompt? = null,
    val reminderLeadMinutes: Int = CourseReminderPreferences.DEFAULT_LEAD_MINUTES,
    val gradeKeysForAverage: Set<String> = emptySet(),
    val gradeKeysForGpa: Set<String> = emptySet(),
    val wakeUpImportPrompt: WakeUpImportPrompt? = null,
    val timetableLayout: TimetableLayoutSettings = TimetableLayoutSettings(),
    val themeName: String = ThemePreferences.DEFAULT_THEME,
)

/** 树维 HTML 已解析，待用户确认第 1 教学周起始日。 */
data class WakeUpImportPrompt(
    val fileText: String,
    val initialDate: LocalDate,
    /** 第 1 教学周内最早有课日，仅当文件已解析出第 1 周周一时可算。 */
    val firstClassDay: LocalDate?,
    val fromFile: Boolean,
)

data class UpdatePrompt(
    val releaseTag: String,
    val versionLabel: String,
    val releaseNotes: String,
    val downloadUrl: String,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as EamsHelperApp
    private val repo = app.uestcRepository
    private val updateChecker = AppUpdateChecker()
    private val updateStorage = UpdateReminderStorage(application)
    private val reminderPrefs = CourseReminderPreferences(application)
    private val timetableDisplayPrefs = TimetableDisplayPreferences(application)
    private val gradeSelectionPrefs = GradeSelectionPreferences(application)
    private val _ui =
        MutableStateFlow(
            MainUiState(
                reminderLeadMinutes = CourseReminderPreferences(application).leadMinutes,
                timetableLayout = TimetableDisplayPreferences(application).load(),
                themeName = app.themePreferences.theme.value,
            ),
        )
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    private val smsContinuation = AtomicReference<CancellableContinuation<String>?>(null)
    private var smsCooldownJob: Job? = null
    private var timetableWeekLoadJob: Job? = null
    private var timetableWeekLoadGeneration = 0

    init {
        reloadFromCache()
        repo.alignTimetableDisplayToToday()?.let { aligned ->
            reloadFromCache()
            _ui.update { it.copy(timetablePagerScrollWeek = aligned.displayWeek) }
            if (repo.hasLocalSession() && !repo.isOfflineImported()) {
                loadTimetableWeek(aligned.displayWeek)
            }
        }
        viewModelScope.launch {
            val hasSession = repo.hasLocalSession()
            _ui.update { current ->
                current.copy(
                    loggedIn = hasSession,
                    userProfile = if (hasSession) repo.cachedUserProfile() else null,
                    showLogin = false,
                    contentLoading = false,
                )
            }
            if (hasSession && _ui.value.userProfile == null) {
                viewModelScope.launch {
                    repo.refreshUserProfile().getOrNull()?.let { p ->
                        _ui.update { it.copy(userProfile = p) }
                    }
                }
            }
        }
        viewModelScope.launch {
            val cur = repo.fetchCurrentSemesterCode()
            val priorMetaSemester = repo.cachedTimetableMeta()?.semesterCode
            _ui.update { it.copy(currentSemesterCode = cur) }
            val semesterChanged =
                cur != null &&
                    cur != priorMetaSemester &&
                    !repo.isOfflineImported()
            if (semesterChanged && _ui.value.scheduleSemester.isNullOrBlank()) {
                refreshCurrentSemesterData()
            } else {
                reloadFromCache()
            }
        }
        viewModelScope.launch { checkAppUpdate(force = false) }
    }

    fun dismissUpdatePrompt() {
        val tag = _ui.value.updatePrompt?.releaseTag ?: return
        updateStorage.dismiss(tag)
        _ui.update { it.copy(updatePrompt = null) }
    }

    fun clearUpdatePrompt() {
        _ui.update { it.copy(updatePrompt = null) }
    }

    fun checkAppUpdate(force: Boolean = false) {
        viewModelScope.launch {
            if (!force && !updateStorage.shouldCheckNow()) return@launch
            if (!force) updateStorage.markCheckedNow()
            val info = updateChecker.fetchLatestIfNewer(BuildConfig.VERSION_NAME)
            if (info == null) {
                if (force) {
                    _ui.update {
                        it.copy(message = "当前已是最新版本（${BuildConfig.VERSION_NAME}）")
                    }
                }
                return@launch
            }
            if (!force && updateStorage.isDismissed(info.releaseTag)) return@launch
            _ui.update {
                it.copy(
                    updatePrompt =
                        UpdatePrompt(
                            releaseTag = info.releaseTag,
                            versionLabel = info.versionName,
                            releaseNotes = info.releaseNotes,
                            downloadUrl = info.downloadUrl,
                        ),
                )
            }
        }
    }

    fun selectTab(index: Int) {
        _ui.update { it.copy(selectedTab = index) }
    }

    fun showLogin() {
        stopSmsResendCooldown()
        _ui.update {
            it.copy(
                showLogin = true,
                loginStatus = null,
                awaitingSms = false,
                smsResendSecondsLeft = 0,
                contentLoading = false,
            )
        }
    }

    fun dismissLogin() {
        cancelPendingSms()
        stopSmsResendCooldown()
        _ui.update {
            it.copy(
                showLogin = false,
                loginDeferred = true,
                contentLoading = false,
                loginStatus = null,
                awaitingSms = false,
                smsResendSecondsLeft = 0,
            )
        }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun submitSmsCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            _ui.update { it.copy(loginStatus = "请先填写短信验证码") }
            return
        }
        smsContinuation.getAndSet(null)?.resume(trimmed)
        stopSmsResendCooldown()
        _ui.update {
            it.copy(
                awaitingSms = true,
                loginStatus = "正在提交验证码…",
                contentLoading = true,
            )
        }
    }

    /** 验证码页点返回：取消等待中的二次认证，回到学号密码页，不结束整个登录框。 */
    fun cancelSmsStep() {
        cancelPendingSms()
        stopSmsResendCooldown()
        _ui.update {
            it.copy(
                awaitingSms = false,
                smsResendSecondsLeft = 0,
                contentLoading = false,
                loginStatus = null,
            )
        }
    }

    fun resendSmsCode() {
        if (_ui.value.smsResendSecondsLeft > 0 || !_ui.value.awaitingSms) return
        viewModelScope.launch {
            _ui.update { it.copy(loginStatus = "正在重新发送验证码…") }
            repo.resendLoginSms().fold(
                onSuccess = { outcome -> applyReauthSmsOutcome(outcome) },
                onFailure = { e ->
                    val msg = e.message?.trim().orEmpty().ifBlank { "重发失败" }
                    _ui.update { it.copy(loginStatus = msg) }
                },
            )
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            cancelPendingSms()
            stopSmsResendCooldown()
            _ui.update {
                it.copy(
                    contentLoading = true,
                    showLogin = true,
                    message = null,
                    loginStatus = "正在连接统一身份认证…",
                    awaitingSms = false,
                    smsResendSecondsLeft = 0,
                )
            }
            val result =
                repo.login(username, password) { prompt ->
                    awaitSmsFromUser(prompt)
                }
            stopSmsResendCooldown()
            result.fold(
                onSuccess = {
                    val profile = repo.cachedUserProfile()
                    _ui.update {
                        it.copy(
                            contentLoading = false,
                            loggedIn = true,
                            showLogin = false,
                            loginStatus = null,
                            awaitingSms = false,
                            smsResendSecondsLeft = 0,
                            userProfile = profile,
                            message = "登录成功",
                        )
                    }
                    refreshAllDataAfterLogin()
                },
                onFailure = { e ->
                    if (isLoginCancelled(e)) {
                        _ui.update {
                            it.copy(
                                contentLoading = false,
                                awaitingSms = false,
                                smsResendSecondsLeft = 0,
                            )
                        }
                        return@fold
                    }
                    val msg = LoginUserMessages.fromThrowable(e)
                    _ui.update {
                        it.copy(
                            contentLoading = false,
                            loggedIn = false,
                            showLogin = true,
                            awaitingSms = false,
                            smsResendSecondsLeft = 0,
                            loginStatus = msg,
                            message = "登录不成功",
                        )
                    }
                },
            )
        }
    }

    fun shiftTimetableWeek(delta: Int) {
        val base = _ui.value.timetableMeta?.displayWeek ?: 1
        val target = (base + delta).coerceAtLeast(1)
        selectTimetableWeek(target)
    }

    fun selectTimetableWeek(week: Int) {
        val w = week.coerceAtLeast(1)
        if (_ui.value.timetableMeta == null) {
            _ui.update {
                it.copy(
                    timetableMeta =
                        TimetableMeta(
                            semesterCode = "",
                            currentWeek = 1,
                            displayWeek = w,
                        ),
                )
            }
        }
        loadTimetableWeek(w)
    }

    fun goCurrentTimetableWeek() {
        viewModelScope.launch {
            _ui.update { it.copy(scheduleSemester = null) }
            val synced = repo.syncCurrentTeachingWeek()
            reloadFromCache()
            val meta = synced ?: _ui.value.timetableMeta ?: return@launch
            val current = meta.teachingWeekOn(LocalDate.now())
            loadTimetableWeek(current, forceNetwork = synced != null)
        }
    }

    /** 仅切换课表所查看的学期；[code] 为 null 表示回到当前学期（不影响考试页）。 */
    fun selectScheduleSemester(code: String?) {
        val current = _ui.value.currentSemesterCode
        val isCurrent = code == null || (current != null && code == current)
        if (isCurrent) {
            _ui.update {
                it.copy(
                    scheduleSemester = null,
                    courses = emptyList(),
                    contentLoading = true,
                )
            }
            if (repo.isOfflineImported()) {
                reloadFromCache()
                return
            }
            goCurrentTimetableWeek()
            return
        }
        val target = code
        if (target.isNullOrBlank()) return
        _ui.update {
            it.copy(
                scheduleSemester = target,
                courses = emptyList(),
                contentLoading = true,
            )
        }
        loadTimetableWeek(1, forceNetwork = true)
    }

    /** 仅切换考试所查看的学期；[code] 为 null 表示回到当前学期（不影响课表页）。 */
    fun selectExamSemester(code: String?) {
        val current = _ui.value.currentSemesterCode
        val isCurrent = code == null || (current != null && code == current)
        if (isCurrent) {
            _ui.update {
                it.copy(
                    examSemester = null,
                    exams = emptyList(),
                    contentLoading = true,
                )
            }
            refreshCurrentExams()
            return
        }
        val target = code
        if (target.isNullOrBlank()) return
        _ui.update {
            it.copy(
                examSemester = target,
                exams = emptyList(),
                contentLoading = true,
            )
        }
        refreshExamsFor(target)
    }

    private fun refreshCurrentExams() {
        viewModelScope.launch {
            repo.refreshExams().fold(
                onSuccess = {
                    _ui.update { it.copy(contentLoading = false) }
                    reloadFromCache()
                },
                onFailure = { e ->
                    _ui.update {
                        applyDataFetchFailure(it.copy(contentLoading = false), e)
                    }
                },
            )
        }
    }

    private fun refreshExamsFor(semester: String) {
        viewModelScope.launch {
            repo.refreshExams(semester).fold(
                onSuccess = {
                    _ui.update { it.copy(contentLoading = false) }
                    reloadFromCache()
                },
                onFailure = { e ->
                    _ui.update {
                        applyDataFetchFailure(it.copy(contentLoading = false), e)
                    }
                },
            )
        }
    }

    /** 检测到新学期时，拉取最新当前学期的课表与考试，避免显示旧学期数据。 */
    private fun refreshCurrentSemesterData() {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    scheduleSemester = null,
                    examSemester = null,
                    courses = emptyList(),
                    exams = emptyList(),
                    contentLoading = true,
                )
            }
            val cur = _ui.value.currentSemesterCode
            repo.refreshTimetable(forceNetwork = true, semesterCode = cur).fold(
                onSuccess = {
                    reloadFromCache()
                    _ui.update { it.copy(contentLoading = false) }
                },
                onFailure = { e ->
                    _ui.update {
                        applyDataFetchFailure(it.copy(contentLoading = false), e)
                    }
                },
            )
            repo.refreshExams().fold(
                onSuccess = { reloadFromCache() },
                onFailure = { e ->
                    _ui.update {
                        applyDataFetchFailure(it.copy(contentLoading = false), e)
                    }
                },
            )
        }
    }

    fun applyWeekOneMonday(selectedDay: LocalDate) {
        val updated = repo.applyWeekOneMonday(selectedDay)
        reloadFromCache()
        _ui.update {
            it.copy(message = "已将第 1 教学周对齐到 ${updated.weekOneMonday}")
        }
    }

    private fun loadTimetableWeek(week: Int, forceNetwork: Boolean = false) {
        timetableWeekLoadJob?.cancel()
        val generation = ++timetableWeekLoadGeneration
        timetableWeekLoadJob =
            viewModelScope.launch {
                val w = week.coerceAtLeast(1)
                if (!forceNetwork && repo.switchTimetableWeekLocal(w)) {
                    if (generation != timetableWeekLoadGeneration) return@launch
                    reloadFromCache()
                    prefetchAdjacentTimetableWeeks(w, generation)
                    return@launch
                }
                val sem = _ui.value.scheduleSemester?.takeIf { it.isNotBlank() }
                repo.refreshTimetable(w, forceNetwork = forceNetwork, semesterCode = sem).fold(
                    onSuccess = {
                        if (generation != timetableWeekLoadGeneration) return@fold
                        reloadFromCache()
                        prefetchAdjacentTimetableWeeks(w, generation)
                    },
                    onFailure = { e ->
                        if (generation != timetableWeekLoadGeneration) return@fold
                        _ui.update { applyDataFetchFailure(it, e) }
                    },
                )
            }
    }

    private fun prefetchAdjacentTimetableWeeks(centerWeek: Int, generation: Int) {
        if (repo.isOfflineImported()) return
        viewModelScope.launch {
            val semester =
                _ui.value.scheduleSemester?.takeIf { it.isNotBlank() }
                    ?: repo.cachedTimetableMeta()?.semesterCode
                    ?: return@launch
            for (delta in intArrayOf(-1, 1)) {
                if (generation != timetableWeekLoadGeneration) return@launch
                val w = centerWeek + delta
                if (w < 1) continue
                if (repo.hasCachedTimetableWeek(semester, w)) continue
                repo.refreshTimetable(w, forceNetwork = false, semesterCode = semester).getOrNull()
            }
        }
    }

    fun onHostResume() {
        viewModelScope.launch {
            val hasSession = repo.hasLocalSession()
            _ui.update { current ->
                current.copy(
                    loggedIn = hasSession,
                    userProfile =
                        when {
                            !hasSession -> null
                            current.userProfile != null -> current.userProfile
                            else -> repo.cachedUserProfile()
                        },
                )
            }
            if (hasSession && _ui.value.userProfile == null) {
                repo.refreshUserProfile().getOrNull()?.let { p ->
                    _ui.update { it.copy(userProfile = p) }
                }
            }
            if (hasSession && _ui.value.selectedTab == 0 && !repo.isOfflineImported()) {
                val before = _ui.value.timetableMeta
                val synced = repo.syncCurrentTeachingWeek()
                if (synced != null) {
                    reloadFromCache()
                    if (synced.displayWeek != before?.displayWeek) {
                        loadTimetableWeek(synced.displayWeek)
                    }
                }
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            val hasSession = repo.hasLocalSession()
            val tab = _ui.value.selectedTab
            if (!hasSession) {
                if (repo.isOfflineImported() && tab == 0) {
                    _ui.update {
                        it.copy(
                            loggedIn = false,
                            message = "当前为导入课表，请在「我的」页更新文件或登录后在课表页点刷新从教务拉取",
                        )
                    }
                    return@launch
                }
                _ui.update {
                    it.copy(
                        message =
                            if (it.courses.isEmpty()) {
                                "请先登录，或在「我的」页使用网页登录 / 导入课表文件"
                            } else {
                                "暂无登录信息，在课表页点刷新将尝试使用已保存会话；失败请到「我的」页重新登录"
                            },
                    )
                }
                return@launch
            }
            val displayWeek = _ui.value.timetableMeta?.displayWeek
            _ui.update {
                it.copy(contentLoading = true, message = null, loggedIn = true, showLogin = false)
            }
            val activeSem = _ui.value.scheduleSemester?.takeIf { it.isNotBlank() }
            repo.refreshForTab(tab, displayWeek, activeSem).fold(
                onSuccess = {
                    reloadFromCache()
                    val weekAfter = repo.cachedTimetableMeta()?.displayWeek
                    _ui.update {
                        it.copy(
                            contentLoading = false,
                            loggedIn = true,
                            userProfile = repo.cachedUserProfile(),
                            message = refreshMessageForTab(tab, weekAfter),
                        )
                    }
                },
                onFailure = { e ->
                    _ui.update {
                        applyDataFetchFailure(
                            it.copy(contentLoading = false),
                            e,
                        )
                    }
                },
            )
        }
    }

    private fun refreshAllDataAfterLogin() {
        viewModelScope.launch {
            if (!repo.hasLocalSession()) return@launch
            _ui.update { it.copy(contentLoading = true, message = null, loggedIn = true, showLogin = false) }
            repo.refreshAllAfterLogin().fold(
                onSuccess = {
                    reloadFromCache()
                    _ui.update {
                        it.copy(
                            contentLoading = false,
                            loggedIn = true,
                            userProfile = repo.cachedUserProfile(),
                            message = "登录成功，已同步课表、成绩与考试",
                        )
                    }
                },
                onFailure = { e ->
                    reloadFromCache()
                    _ui.update {
                        applyDataFetchFailure(
                            it.copy(contentLoading = false),
                            e,
                        )
                    }
                },
            )
        }
    }

    private fun applyDataFetchFailure(
        state: MainUiState,
        error: Throwable,
    ): MainUiState {
        val msg = error.message ?: "刷新失败"
        return when (error) {
            is EamsFetchException.OffCampus ->
                state.copy(
                    message = msg,
                    loggedIn = state.loggedIn || repo.hasLocalSession(),
                    showLogin = false,
                )
            is EamsFetchException.SessionInvalid ->
                state.copy(
                    message = "登录可能已过期，当前展示本地缓存数据；如需刷新请到「我的」页点「账号登录」重新登录。",
                    showLogin = false,
                )
            else ->
                state.copy(
                    message = msg,
                    loggedIn = state.loggedIn || repo.hasLocalSession(),
                    showLogin = false,
                )
        }
    }

    private fun refreshMessageForTab(tab: Int, displayWeek: Int?): String =
        when (tab) {
            0 -> {
                val w = displayWeek ?: repo.cachedTimetableMeta()?.displayWeek
                if (w != null) "已更新第 ${w} 周课表" else "已更新课表"
            }
            1 -> "已更新考试安排"
            2 -> "已更新成绩"
            else -> "已更新个人信息"
        }

    private suspend fun awaitSmsFromUser(prompt: String): String {
        val (cooldownSec, display) = parseSmsPrompt(prompt)
        return suspendCancellableCoroutine { cont ->
            smsContinuation.set(cont)
            applySmsResendCooldown(cooldownSec)
            _ui.update {
                it.copy(
                    awaitingSms = true,
                    loginStatus = display.ifBlank { "请填写短信验证码。" },
                    contentLoading = false,
                )
            }
            cont.invokeOnCancellation {
                smsContinuation.compareAndSet(cont, null)
                stopSmsResendCooldown()
            }
        }
    }

    private fun applyReauthSmsOutcome(outcome: ReauthSmsSendOutcome) {
        applySmsResendCooldown(outcome.resendCooldownSec)
        val display =
            if (outcome.sent == true) {
                CasLoginRepository.formatSmsSentHint(outcome.mobile, outcome.userMessage)
            } else {
                outcome.userMessage.trim()
            }
        _ui.update { it.copy(loginStatus = display) }
    }

    private fun parseSmsPrompt(prompt: String): Pair<Int?, String> {
        val timePrefix = CasLoginRepository.SMS_PROMPT_CODE_TIME_PREFIX
        val mobilePrefix = CasLoginRepository.SMS_PROMPT_MOBILE_PREFIX
        var cooldownSec: Int? = null
        val body = mutableListOf<String>()
        for (line in prompt.lines()) {
            when {
                line.startsWith(timePrefix) ->
                    cooldownSec =
                        line.removePrefix(timePrefix).trim().toIntOrNull()?.takeIf { it in 1..600 }
                line.startsWith(mobilePrefix) -> Unit
                line.isNotBlank() && !line.startsWith("填写后点") -> body.add(line)
            }
        }
        return cooldownSec to body.joinToString("\n")
    }

    private fun applySmsResendCooldown(totalSeconds: Int?) {
        stopSmsResendCooldown()
        val sec = totalSeconds ?: 0
        if (sec <= 0) {
            _ui.update { it.copy(smsResendSecondsLeft = 0) }
            return
        }
        smsCooldownJob =
            viewModelScope.launch {
                for (left in sec.coerceIn(1, 600) downTo 0) {
                    _ui.update { it.copy(smsResendSecondsLeft = left) }
                    if (left == 0) break
                    delay(1000)
                }
            }
    }

    private fun stopSmsResendCooldown() {
        smsCooldownJob?.cancel()
        smsCooldownJob = null
        _ui.update { it.copy(smsResendSecondsLeft = 0) }
    }

    private fun cancelPendingSms() {
        smsContinuation.getAndSet(null)?.cancel()
        stopSmsResendCooldown()
    }

    fun importWakeUpTimetable(context: Context, uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(contentLoading = true, message = null, wakeUpImportPrompt = null) }
            val text =
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    }
                }
            if (text.isNullOrBlank()) {
                _ui.update { it.copy(contentLoading = false, message = "无法读取所选文件") }
                return@launch
            }
            val parsed =
                runCatching { WakeUpShuweiHtmlParser.parse(text) }
                    .getOrElse { e ->
                        _ui.update {
                            it.copy(
                                contentLoading = false,
                                message = e.message ?: "导入失败，请确认是树维课表 HTML",
                            )
                        }
                        return@launch
                    }
            val firstClass =
                WakeUpShuweiHtmlParser.suggestFirstClassDayInWeekOne(
                    parsed.courses,
                    parsed.weekOneMonday,
                )
            val initial =
                TeachingWeekEstimator.defaultImportWeekOneMonday(
                    parsed.weekOneMonday,
                    firstClass,
                )
            _ui.update {
                it.copy(
                    contentLoading = false,
                    wakeUpImportPrompt =
                        WakeUpImportPrompt(
                            fileText = text,
                            initialDate = initial,
                            firstClassDay = firstClass,
                            fromFile = parsed.weekOneMonday != null,
                        ),
                )
            }
        }
    }

    fun dismissWakeUpImport() {
        _ui.update { it.copy(wakeUpImportPrompt = null) }
    }

    fun confirmWakeUpImport(selectedDay: LocalDate) {
        val prompt = _ui.value.wakeUpImportPrompt ?: return
        val weekOneMonday = selectedDay.with(DayOfWeek.MONDAY)
        viewModelScope.launch {
            _ui.update { it.copy(contentLoading = true, wakeUpImportPrompt = null) }
            repo.importWakeUpTimetableFile(prompt.fileText, weekOneMonday).fold(
                onSuccess = { count ->
                    reloadFromCache()
                    val week = repo.cachedTimetableMeta()?.currentWeek
                    val weekHint = week?.let { "（估算第 $it 周）" }.orEmpty()
                    _ui.update {
                        it.copy(
                            contentLoading = false,
                            selectedTab = 0,
                            loginDeferred = true,
                            showLogin = false,
                            message = "已导入 $count 条课程记录$weekHint，可切换教学周",
                        )
                    }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(
                            contentLoading = false,
                            message = e.message ?: "导入失败，请确认是树维课表 HTML",
                        )
                    }
                },
            )
        }
    }

    fun setTimetableLayout(settings: TimetableLayoutSettings) {
        val clamped = settings.coerce()
        timetableDisplayPrefs.save(clamped)
        _ui.update { it.copy(timetableLayout = clamped) }
    }

    fun setReminderLeadMinutes(minutes: Int) {
        val clamped =
            minutes.coerceIn(
                CourseReminderPreferences.MIN_LEAD_MINUTES,
                CourseReminderPreferences.MAX_LEAD_MINUTES,
            )
        reminderPrefs.leadMinutes = clamped
        _ui.update {
            it.copy(
                reminderLeadMinutes = clamped,
                message = "已设置：开课前 ${CourseReminderPreferences.formatLeadLabel(clamped)} 内提醒",
            )
        }
    }

    fun setTheme(name: String) {
        app.themePreferences.setTheme(name)
        _ui.update { it.copy(themeName = name) }
    }

    fun previewCourseNotification() {
        val ctx = getApplication<Application>()
        val lead = _ui.value.reminderLeadMinutes
        when (CourseNotificationHelper.showPreview(ctx, _ui.value.courses)) {
            CourseNotificationHelper.PreviewResult.Sent ->
                _ui.update {
                    it.copy(message = "已发送调试通知 [提前 $lead 分钟]，请看通知栏")
                }
            CourseNotificationHelper.PreviewResult.NoPermission ->
                _ui.update { it.copy(message = "未获得通知权限，请在系统设置中允许通知") }
            CourseNotificationHelper.PreviewResult.NoCourses ->
                _ui.update { it.copy(message = "暂无课表，请先在课表页刷新后再试发通知") }
        }
    }

    fun toggleGradeForAverage(key: String) {
        val next =
            _ui.value.gradeKeysForAverage.toMutableSet().apply {
                if (!add(key)) remove(key)
            }
        gradeSelectionPrefs.saveAverageKeys(next)
        _ui.update { it.copy(gradeKeysForAverage = next) }
    }

    fun toggleGradeForGpa(key: String) {
        val next =
            _ui.value.gradeKeysForGpa.toMutableSet().apply {
                if (!add(key)) remove(key)
            }
        gradeSelectionPrefs.saveGpaKeys(next)
        _ui.update { it.copy(gradeKeysForGpa = next) }
    }

    fun setAllGradesForAverage(selected: Boolean) {
        val keys =
            if (selected) {
                _ui.value.grades.map { GradeStatsCalculator.stableKey(it) }.toSet()
            } else {
                emptySet()
            }
        gradeSelectionPrefs.saveAverageKeys(keys)
        _ui.update { it.copy(gradeKeysForAverage = keys) }
    }

    fun setAllGradesForGpa(selected: Boolean) {
        val keys =
            if (selected) {
                _ui.value.grades.map { GradeStatsCalculator.stableKey(it) }.toSet()
            } else {
                emptySet()
            }
        gradeSelectionPrefs.saveGpaKeys(keys)
        _ui.update { it.copy(gradeKeysForGpa = keys) }
    }

    /** 仅对给定的课程 key 集合做批量勾选/取消（用于「按学期全选」等局部操作）。 */
    fun setGradeKeysForAverage(keys: Collection<String>, selected: Boolean) {
        val next =
            if (selected) {
                _ui.value.gradeKeysForAverage + keys
            } else {
                _ui.value.gradeKeysForAverage - keys.toSet()
            }
        gradeSelectionPrefs.saveAverageKeys(next)
        _ui.update { it.copy(gradeKeysForAverage = next) }
    }

    fun setGradeKeysForGpa(keys: Collection<String>, selected: Boolean) {
        val next =
            if (selected) {
                _ui.value.gradeKeysForGpa + keys
            } else {
                _ui.value.gradeKeysForGpa - keys.toSet()
            }
        gradeSelectionPrefs.saveGpaKeys(next)
        _ui.update { it.copy(gradeKeysForGpa = next) }
    }

    private fun syncGradeSelections(grades: List<GradeItem>): GradeSelectionPreferences.GradeSelectionState {
        val keys = grades.map { GradeStatsCalculator.stableKey(it) }.toSet()
        return gradeSelectionPrefs.syncWithCurrentGrades(keys, grades)
    }

    private fun reloadFromCache() {
        val meta = repo.cachedTimetableMeta()
        val grades = repo.cachedGrades()
        val gradeSelection = syncGradeSelections(grades)
        val current = _ui.value.currentSemesterCode
        val scheduleChoice = _ui.value.scheduleSemester
        val examChoice = _ui.value.examSemester
        val scheduleDisplay =
            when {
                !scheduleChoice.isNullOrBlank() -> scheduleChoice
                !current.isNullOrBlank() -> current
                else -> meta?.semesterCode.orEmpty()
            }
        val examDisplay =
            when {
                !examChoice.isNullOrBlank() -> examChoice
                !current.isNullOrBlank() -> current
                else -> meta?.semesterCode.orEmpty()
            }
        val courses =
            if (scheduleDisplay.isBlank()) {
                emptyList()
            } else {
                repo.cachedCourses(scheduleDisplay)
            }
        val exams =
            if (examDisplay.isBlank()) {
                emptyList()
            } else {
                repo.cachedExams(examDisplay)
            }
        val options =
            if (repo.isOfflineImported()) {
                emptyList()
            } else {
                semesterOptionsFrom(grades, current)
            }
        _ui.update {
            it.copy(
                courses = courses,
                timetableMeta = meta,
                exams = exams,
                grades = grades,
                userProfile = repo.cachedUserProfile(),
                gradeKeysForAverage = gradeSelection.averageKeys,
                gradeKeysForGpa = gradeSelection.gpaKeys,
                currentSemesterCode = current,
                scheduleSemester = _ui.value.scheduleSemester,
                examSemester = _ui.value.examSemester,
                semesterOptions = options,
                contentLoading = false,
            )
        }
    }

    private fun semesterOptionsFrom(
        grades: List<GradeItem>,
        current: String?,
    ): List<String> {
        val fromGrades =
            grades.map { it.semester }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedDescending()
        val currentFirst = listOfNotNull(current)
        val order =
            (currentFirst + fromGrades)
                .distinct()
                .sortedBy { if (it == current) 0 else 1 }
        return order
    }

    private fun isLoginCancelled(error: Throwable): Boolean {
        var cur: Throwable? = error
        while (cur != null) {
            if (cur is CancellationException) return true
            cur = cur.cause
        }
        return false
    }
}
