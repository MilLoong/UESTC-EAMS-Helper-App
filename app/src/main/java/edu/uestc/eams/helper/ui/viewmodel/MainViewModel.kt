package edu.uestc.eams.helper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.uestc.eams.helper.EamsHelperApp
import edu.uestc.eams.helper.data.auth.CasLoginRepository
import edu.uestc.eams.helper.data.auth.ReauthSmsSendOutcome
import edu.uestc.eams.helper.data.auth.LoginUserMessages
import edu.uestc.eams.helper.domain.model.ExamItem
import edu.uestc.eams.helper.domain.model.GradeItem
import edu.uestc.eams.helper.domain.model.TimetableMeta
import edu.uestc.eams.helper.domain.model.UestcCourse
import edu.uestc.eams.helper.domain.model.UserProfile
import edu.uestc.eams.helper.notification.CourseNotificationHelper
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

data class MainUiState(
    /** 登录或刷新数据时显示全屏加载；会话探测、点「稍后」不占用。 */
    val contentLoading: Boolean = false,
    val message: String? = null,
    val loggedIn: Boolean = false,
    val selectedTab: Int = 0,
    val courses: List<UestcCourse> = emptyList(),
    val timetableMeta: TimetableMeta? = null,
    val exams: List<ExamItem> = emptyList(),
    val grades: List<GradeItem> = emptyList(),
    val showLogin: Boolean = false,
    val loginDeferred: Boolean = false,
    /** 登录弹窗内状态文案（含验证码已发送、失败原因）。 */
    val loginStatus: String? = null,
    /** 二次认证：已发码，等待用户提交短信验证码。 */
    val awaitingSms: Boolean = false,
    /** 距可再次「重新发送验证码」的剩余秒数；0 表示可点。 */
    val smsResendSecondsLeft: Int = 0,
    val userProfile: UserProfile? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as EamsHelperApp
    private val repo = app.uestcRepository
    private val _ui = MutableStateFlow(MainUiState())
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    private val smsContinuation = AtomicReference<CancellableContinuation<String>?>(null)
    private var smsCooldownJob: Job? = null

    init {
        reloadFromCache()
        viewModelScope.launch {
            val ok = repo.probeSession()
            val needLogin = !ok && _ui.value.courses.isEmpty() && !_ui.value.loginDeferred
            _ui.update { current ->
                current.copy(
                    loggedIn = ok,
                    userProfile = if (ok) repo.cachedUserProfile() else null,
                    showLogin = needLogin,
                    contentLoading = false,
                )
            }
            if (ok && _ui.value.userProfile == null) {
                viewModelScope.launch {
                    repo.refreshUserProfile().getOrNull()?.let { p ->
                        _ui.update { it.copy(userProfile = p) }
                    }
                }
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
                awaitingSms = false,
                smsResendSecondsLeft = 0,
                loginStatus = "正在提交验证码…",
                contentLoading = true,
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
        val meta = _ui.value.timetableMeta ?: return
        loadTimetableWeek((meta.displayWeek + delta).coerceAtLeast(1))
    }

    fun goCurrentTimetableWeek() {
        val current = _ui.value.timetableMeta?.currentWeek ?: return
        loadTimetableWeek(current)
    }

    private fun loadTimetableWeek(week: Int, forceNetwork: Boolean = false) {
        viewModelScope.launch {
            val semester = _ui.value.timetableMeta?.semesterCode
            val useCache =
                !forceNetwork &&
                    semester != null &&
                    repo.hasCachedTimetableWeek(semester, week)
            if (!useCache) {
                _ui.update { it.copy(contentLoading = true, message = null) }
            }
            repo.refreshTimetable(week, forceNetwork = forceNetwork).fold(
                onSuccess = {
                    reloadFromCache()
                    _ui.update { it.copy(contentLoading = false) }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(
                            contentLoading = false,
                            message = e.message ?: "切换周次失败",
                        )
                    }
                },
            )
        }
    }

    /** 从 Web 返回或切回前台时，根据 Cookie 会话更新登录状态。 */
    fun onHostResume() {
        viewModelScope.launch {
            val ok = repo.probeSession()
            _ui.update { current ->
                current.copy(
                    loggedIn = ok,
                    showLogin = !ok && current.courses.isEmpty() && !current.loginDeferred,
                    userProfile =
                        when {
                            !ok -> null
                            current.userProfile != null -> current.userProfile
                            else -> repo.cachedUserProfile()
                        },
                )
            }
            if (ok && _ui.value.userProfile == null) {
                repo.refreshUserProfile().getOrNull()?.let { p ->
                    _ui.update { it.copy(userProfile = p) }
                }
            }
        }
    }

    /** 顶栏刷新：按当前 Tab 只拉对应接口（课表仅强制更新正在查看的那一周）。 */
    fun refreshAll() {
        viewModelScope.launch {
            val hasSession = repo.probeSession()
            if (!hasSession) {
                _ui.update {
                    it.copy(
                        loggedIn = false,
                        message =
                            if (it.courses.isEmpty()) {
                                "请先登录，或在 Web 中 [导入会话] 后点 [刷新]"
                            } else {
                                "会话已失效，请重新登录或通过 Web [导入会话]"
                            },
                    )
                }
                return@launch
            }
            val tab = _ui.value.selectedTab
            val displayWeek = _ui.value.timetableMeta?.displayWeek
            _ui.update {
                it.copy(contentLoading = true, message = null, loggedIn = true, showLogin = false)
            }
            repo.refreshForTab(tab, displayWeek).fold(
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
                    val msg = e.message ?: "刷新失败"
                    _ui.update {
                        it.copy(
                            contentLoading = false,
                            message = msg,
                            showLogin = msg.contains("登录"),
                        )
                    }
                },
            )
        }
    }

    private fun refreshAllDataAfterLogin() {
        viewModelScope.launch {
            val hasSession = repo.probeSession()
            if (!hasSession) return@launch
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
                        it.copy(
                            contentLoading = false,
                            message = e.message ?: "部分数据同步失败",
                        )
                    }
                },
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

    fun previewCourseNotification() {
        val ctx = getApplication<Application>()
        when (CourseNotificationHelper.showPreview(ctx, _ui.value.courses)) {
            CourseNotificationHelper.PreviewResult.Sent ->
                _ui.update { it.copy(message = "已发送调试上课通知，请看通知栏") }
            CourseNotificationHelper.PreviewResult.NoPermission ->
                _ui.update { it.copy(message = "未获得通知权限，请在系统设置中允许通知") }
            CourseNotificationHelper.PreviewResult.NoCourses ->
                _ui.update { it.copy(message = "暂无课表，请先 [刷新] 后再试发通知") }
        }
    }

    private fun reloadFromCache() {
        val courses = repo.cachedCourses()
        val meta = repo.cachedTimetableMeta()
        _ui.update {
            it.copy(
                courses = courses,
                timetableMeta = meta,
                exams = repo.cachedExams(),
                grades = repo.cachedGrades(),
                userProfile = repo.cachedUserProfile(),
                contentLoading = false,
            )
        }
    }
}
