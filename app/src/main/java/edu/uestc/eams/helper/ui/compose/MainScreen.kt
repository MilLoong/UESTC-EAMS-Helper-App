package edu.uestc.eams.helper.ui.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.uestc.eams.helper.BuildConfig
import edu.uestc.eams.helper.CourseWebActivity
import edu.uestc.eams.helper.R
import edu.uestc.eams.helper.data.network.ApiConstants
import edu.uestc.eams.helper.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.importWakeUpTimetable(context, it) }
        }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearMessage()
    }

    LaunchedEffect(state.loggedIn, state.showLogin) {
        if (state.loggedIn && !state.showLogin) {
            commitLoginAutofill(context)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.onHostResume()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                },
                actions = {
                    TextButton(onClick = { viewModel.showLogin() }) {
                        Text("登录")
                    }
                    IconButton(
                        onClick = { viewModel.refreshAll() },
                        enabled = !state.contentLoading,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    TextButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf("text/html", "text/plain", "application/octet-stream"),
                            )
                        },
                    ) {
                        Text("导入")
                    }
                    TextButton(
                        onClick = {
                            CourseWebActivity.start(
                                context,
                                ApiConstants.casLoginUrlWithService(),
                            )
                        },
                    ) {
                        Text("Web")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.TableChart, null) },
                    label = { Text("课表") },
                )
                NavigationBarItem(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.DateRange, null) },
                    label = { Text("考试") },
                )
                NavigationBarItem(
                    selected = state.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.School, null) },
                    label = { Text("成绩") },
                )
                NavigationBarItem(
                    selected = state.selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text("我的") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.selectedTab) {
                0 ->
                    ScheduleTab(
                        courses = state.courses,
                        timetableMeta = state.timetableMeta,
                        pagerScrollWeek = state.timetablePagerScrollWeek,
                        modifier = Modifier.fillMaxSize(),
                        onPrevWeek = { viewModel.shiftTimetableWeek(-1) },
                        onNextWeek = { viewModel.shiftTimetableWeek(1) },
                        onWeekSelected = { viewModel.selectTimetableWeek(it) },
                        onGoCurrentWeek = { viewModel.goCurrentTimetableWeek() },
                        onPagerScrollConsumed = { viewModel.consumeTimetablePagerScroll() },
                    )
                1 -> ExamTab(state.exams, Modifier.fillMaxSize())
                2 ->
                    GradesTab(
                        grades = state.grades,
                        averageKeys = state.gradeKeysForAverage,
                        gpaKeys = state.gradeKeysForGpa,
                        onToggleAverage = { viewModel.toggleGradeForAverage(it) },
                        onToggleGpa = { viewModel.toggleGradeForGpa(it) },
                        onSelectAllAverage = { viewModel.setAllGradesForAverage(it) },
                        onSelectAllGpa = { viewModel.setAllGradesForGpa(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                else ->
                    ProfileTab(
                        loggedIn = state.loggedIn,
                        profile = state.userProfile,
                        appVersion = BuildConfig.VERSION_NAME,
                        reminderLeadMinutes = state.reminderLeadMinutes,
                        onReminderLeadMinutesChange = { viewModel.setReminderLeadMinutes(it) },
                        onLogin = { viewModel.showLogin() },
                        onCheckUpdate = { viewModel.checkAppUpdate(force = true) },
                        onDebugNotify = { viewModel.previewCourseNotification() },
                        modifier = Modifier.fillMaxSize(),
                    )
            }
            if (state.contentLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    state.wakeUpImportPrompt?.let { prompt ->
        WakeUpImportDialog(
            prompt = prompt,
            loading = state.contentLoading,
            onDismiss = { viewModel.dismissWakeUpImport() },
            onConfirm = { viewModel.confirmWakeUpImport(it) },
        )
    }

    state.updatePrompt?.let { update ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdatePrompt() },
            title = { Text("发现新版本 ${update.versionLabel}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "当前版本：${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (update.releaseNotes.isNotBlank()) {
                        ReleaseNotesMarkdownText(
                            update.releaseNotes,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        Text(
                            "建议下载安装最新版本以获得修复与功能更新。",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)),
                        )
                        viewModel.clearUpdatePrompt()
                    },
                ) {
                    Text("前往下载")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdatePrompt() }) {
                    Text("稍后")
                }
            },
        )
    }

    if (state.showLogin || (!state.loggedIn && state.courses.isEmpty() && !state.loginDeferred)) {
        LoginDialog(
            onDismiss = { viewModel.dismissLogin() },
            onLogin = { u, p -> viewModel.login(u, p) },
            onSubmitSms = { viewModel.submitSmsCode(it) },
            onResendSms = { viewModel.resendSmsCode() },
            loading = state.contentLoading,
            loginStatus = state.loginStatus,
            awaitingSms = state.awaitingSms,
            smsResendSecondsLeft = state.smsResendSecondsLeft,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginDialog(
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onSubmitSms: (String) -> Unit,
    onResendSms: () -> Unit,
    loading: Boolean,
    loginStatus: String?,
    awaitingSms: Boolean,
    smsResendSecondsLeft: Int,
) {
    val context = LocalContext.current
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var sms by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!loading) {
                cancelLoginAutofill(context)
                onDismiss()
            }
        },
        title = { Text("统一身份认证登录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.login_freeze_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (!loginStatus.isNullOrBlank()) {
                    Text(
                        loginStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            when {
                                loginStatus.contains("正在") ||
                                    loginStatus.contains("请填写") ||
                                    loginStatus.contains("验证码已") -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            },
                    )
                }
                Text(
                    stringResource(R.string.login_browser_fallback_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("学号") },
                    singleLine = true,
                    enabled = !loading && !awaitingSms,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().autofillUsername(),
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("密码") },
                    singleLine = true,
                    enabled = !loading && !awaitingSms,
                    visualTransformation =
                        if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().autofillPassword(),
                )
                // 验证码框始终可看见；发码成功前不可编辑，避免误以为只需学号密码
                OutlinedTextField(
                    value = sms,
                    onValueChange = { sms = it },
                    label = { Text("短信验证码") },
                    singleLine = true,
                    enabled = !loading && awaitingSms,
                    modifier = Modifier.fillMaxWidth().autofillSmsOtp(),
                )
                if (awaitingSms) {
                    TextButton(
                        onClick = onResendSms,
                        enabled = !loading && smsResendSecondsLeft == 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (smsResendSecondsLeft > 0) {
                                "重新发送验证码 (${smsResendSecondsLeft}s)"
                            } else {
                                "重新发送验证码"
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (awaitingSms) {
                TextButton(
                    onClick = { onSubmitSms(sms) },
                    enabled = !loading && sms.isNotBlank(),
                ) { Text(if (loading) "登录中…" else "登录") }
            } else {
                TextButton(
                    onClick = { onLogin(user.trim(), pass) },
                    enabled = !loading && user.isNotBlank() && pass.isNotBlank(),
                ) { Text(if (loading) "获取中…" else "获取验证码") }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    cancelLoginAutofill(context)
                    onDismiss()
                },
                enabled = !loading,
            ) { Text("稍后") }
        },
    )
}
