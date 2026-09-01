package edu.uestc.eams.helper.ui.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.uestc.eams.helper.BuildConfig
import edu.uestc.eams.helper.CourseWebActivity
import edu.uestc.eams.helper.data.network.ApiConstants
import edu.uestc.eams.helper.ui.viewmodel.MainViewModel

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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AppNavigationBar(
                selectedTab = state.selectedTab,
                onSelect = { viewModel.selectTab(it) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.selectedTab) {
                0 ->
                    ScheduleTab(
                        courses = state.courses,
                        timetableMeta = state.timetableMeta,
                        layout = state.timetableLayout,
                        contentLoading = state.contentLoading,
                        semesterOptions = state.semesterOptions,
                        activeSemesterCode = state.scheduleSemester,
                        currentSemesterCode = state.currentSemesterCode,
                        isCurrentSemester =
                            state.scheduleSemester == null ||
                                state.scheduleSemester == state.currentSemesterCode,
                        onSemesterSelect = { viewModel.selectScheduleSemester(it) },
                        pagerScrollWeek = state.timetablePagerScrollWeek,
                        modifier = Modifier.fillMaxSize(),
                        onPrevWeek = { viewModel.shiftTimetableWeek(-1) },
                        onNextWeek = { viewModel.shiftTimetableWeek(1) },
                        onGoCurrentWeek = { viewModel.goCurrentTimetableWeek() },
                        onSelectWeek = { viewModel.selectTimetableWeek(it) },
                        onPagerScrollConsumed = { viewModel.consumeTimetablePagerScroll() },
                        onRefresh = { viewModel.refreshAll() },
                        onLayoutChange = { viewModel.setTimetableLayout(it) },
                    )
                1 ->
                    ExamTab(
                        exams = state.exams,
                        modifier = Modifier.fillMaxSize(),
                        semesterOptions = state.semesterOptions,
                        activeSemesterCode = state.examSemester,
                        currentSemesterCode = state.currentSemesterCode,
                        onSemesterSelect = { viewModel.selectExamSemester(it) },
                    )
                2 ->
                    GradesTab(
                        grades = state.grades,
                        averageKeys = state.gradeKeysForAverage,
                        gpaKeys = state.gradeKeysForGpa,
                        onToggleAverage = { viewModel.toggleGradeForAverage(it) },
                        onToggleGpa = { viewModel.toggleGradeForGpa(it) },
                        onSelectAverageScope = { keys, selected ->
                            viewModel.setGradeKeysForAverage(keys, selected)
                        },
                        onSelectGpaScope = { keys, selected ->
                            viewModel.setGradeKeysForGpa(keys, selected)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                else ->
                    ProfileTab(
                        loggedIn = state.loggedIn,
                        profile = state.userProfile,
                        appVersion = BuildConfig.VERSION_NAME,
                        reminderLeadMinutes = state.reminderLeadMinutes,
                        weekOneMonday = state.timetableMeta?.weekOneMonday,
                        weekOneLocked = state.timetableMeta?.weekOneLocked == true,
                        onReminderLeadMinutesChange = { viewModel.setReminderLeadMinutes(it) },
                        onAdjustWeekOne = { viewModel.applyWeekOneMonday(it) },
                        onLogin = { viewModel.showLogin() },
                        onWebLogin = {
                            CourseWebActivity.start(
                                context,
                                ApiConstants.casLoginUrlWithService(),
                            )
                        },
                        onImportTimetable = {
                            importLauncher.launch(
                                arrayOf("text/html", "text/plain", "application/octet-stream"),
                            )
                        },
                        timetableLayout = state.timetableLayout,
                        onTimetableLayoutChange = { viewModel.setTimetableLayout(it) },
                        themeName = state.themeName,
                        onThemeChange = { viewModel.setTheme(it) },
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
            shape = MaterialTheme.shapes.large,
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

    if (state.showLogin) {
        CasLoginFlow(
            onDismiss = { viewModel.dismissLogin() },
            onLogin = { u, p -> viewModel.login(u, p) },
            onSubmitSms = { viewModel.submitSmsCode(it) },
            onResendSms = { viewModel.resendSmsCode() },
            onBackFromSms = { viewModel.cancelSmsStep() },
            loading = state.contentLoading,
            loginStatus = state.loginStatus,
            awaitingSms = state.awaitingSms,
            smsResendSecondsLeft = state.smsResendSecondsLeft,
        )
    }
}

private data class NavItem(
    val index: Int,
    val icon: ImageVector,
    val label: String,
)

@Composable
private fun AppNavigationBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    val items =
        listOf(
            NavItem(0, Icons.Default.TableChart, "课表"),
            NavItem(1, Icons.Default.DateRange, "考试"),
            NavItem(2, Icons.Default.School, "成绩"),
            NavItem(3, Icons.Default.Person, "我的"),
        )
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                items.forEach { item ->
                    val selected = selectedTab == item.index
                    val fg =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource =
                                        remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onSelect(item.index) },
                                ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Column(
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = fg,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = fg,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
