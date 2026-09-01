package edu.uestc.eams.helper.ui.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.uestc.eams.helper.AppLinks
import edu.uestc.eams.helper.BuildConfig
import edu.uestc.eams.helper.R
import edu.uestc.eams.helper.data.parser.TeachingWeekEstimator
import edu.uestc.eams.helper.data.prefs.CourseReminderPreferences
import edu.uestc.eams.helper.data.prefs.TimetableCourseNameMode
import edu.uestc.eams.helper.data.prefs.TimetableLayoutSettings
import edu.uestc.eams.helper.domain.model.UserProfile
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProfileTab(
    loggedIn: Boolean,
    profile: UserProfile?,
    appVersion: String,
    reminderLeadMinutes: Int,
    weekOneMonday: String? = null,
    weekOneLocked: Boolean = false,
    onReminderLeadMinutesChange: (Int) -> Unit,
    onAdjustWeekOne: (LocalDate) -> Unit = {},
    onLogin: () -> Unit,
    onLogout: () -> Unit = {},
    onWebLogin: () -> Unit = {},
    onImportTimetable: () -> Unit = {},
    timetableLayout: TimetableLayoutSettings = TimetableLayoutSettings(),
    onTimetableLayoutChange: (TimetableLayoutSettings) -> Unit = {},
    themeName: String = edu.uestc.eams.helper.data.prefs.ThemePreferences.DEFAULT_THEME,
    onThemeChange: (String) -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onDebugNotify: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var reminderDraft by rememberSaveable { mutableStateOf(reminderLeadMinutes.toString()) }
    var showAdjustWeekOne by rememberSaveable { mutableStateOf(false) }
    val weekOneLabel =
        weekOneMonday?.let {
            runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("yyyy/M/d")) }.getOrNull()
        }

    LaunchedEffect(reminderLeadMinutes) {
        reminderDraft = reminderLeadMinutes.toString()
    }

    fun applyReminderDraft() {
        val minutes = reminderDraft.trim().toIntOrNull() ?: return
        onReminderLeadMinutesChange(minutes)
        focusManager.clearFocus()
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("我的", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            if (loggedIn && profile != null) {
                ProfileRow("学号", profile.studentId)
                profile.displayName?.takeIf { it.isNotBlank() }?.let { name ->
                    ProfileRow("姓名", name)
                } ?: ProfileRow("姓名", "暂未获取到姓名")
            } else if (loggedIn) {
                Text("已登录，点课表页刷新可同步学号与姓名。")
            } else if (profile != null) {
                ProfileRow("学号", profile.studentId)
                profile.displayName?.takeIf { it.isNotBlank() }?.let { name ->
                    ProfileRow("姓名", name)
                }
                Text(
                    "登录已过期，请重新登录以继续同步。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Text("未登录，登录后可在此查看学号与姓名。")
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 已登录：退出登录；未登录/会话过期：账号登录或重新登录
                if (loggedIn) {
                    OutlinedButton(
                        onClick = onLogout,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("退出登录")
                    }
                } else {
                    Button(
                        onClick = onLogin,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(if (profile != null) "重新登录" else "账号登录")
                    }
                }
                OutlinedButton(
                    onClick = onWebLogin,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("网页登录")
                }
                OutlinedButton(
                    onClick = onImportTimetable,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("导入课表文件")
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("应用主题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "选择喜欢的配色，会同步影响课表、成绩、考试与「我的」页面。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                edu.uestc.eams.helper.ui.theme.AppTheme.entries.chunked(2).forEach { rowThemes ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowThemes.forEach { theme ->
                            FilterChip(
                                selected = themeName == theme.key,
                                onClick = { onThemeChange(theme.key) },
                                shape = MaterialTheme.shapes.small,
                                label = { Text(theme.displayName, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowThemes.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("课表排版", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "可在课表页双指缩放；此处可微调字号、行高与列宽，设置会自动保存。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 6.dp),
            )
            TimetableLayoutSlider(
                label = "字号",
                value = timetableLayout.fontScale,
                valueRange = TimetableLayoutSettings.MIN_FONT_SCALE..TimetableLayoutSettings.MAX_FONT_SCALE,
                valueLabel = "${(timetableLayout.fontScale * 100).toInt()}%",
                onValueChange = {
                    onTimetableLayoutChange(timetableLayout.copy(fontScale = it))
                },
            )
            TimetableLayoutSlider(
                label = "行高",
                value = timetableLayout.rowHeightDp,
                valueRange = TimetableLayoutSettings.MIN_ROW_HEIGHT_DP..TimetableLayoutSettings.MAX_ROW_HEIGHT_DP,
                valueLabel = "${timetableLayout.rowHeightDp.toInt()} dp",
                onValueChange = {
                    onTimetableLayoutChange(timetableLayout.copy(rowHeightDp = it))
                },
            )
            TimetableLayoutSlider(
                label = "列宽",
                value = timetableLayout.dayColumnWidthDp,
                valueRange = TimetableLayoutSettings.MIN_DAY_COLUMN_WIDTH_DP..TimetableLayoutSettings.MAX_DAY_COLUMN_WIDTH_DP,
                valueLabel = "${timetableLayout.dayColumnWidthDp.toInt()} dp",
                onValueChange = {
                    onTimetableLayoutChange(timetableLayout.copy(dayColumnWidthDp = it))
                },
            )
            Text(
                "显示",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("中午分界线", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "在第 4、5 节之间显示横线，区分上午与下午",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(
                    checked = timetableLayout.showNoonDivider,
                    onCheckedChange = {
                        onTimetableLayoutChange(timetableLayout.copy(showNoonDivider = it))
                    },
                )
            }
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text("课程名称", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "课表小卡片上的课程名显示方式",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = timetableLayout.courseNameMode == TimetableCourseNameMode.FULL,
                        onClick = {
                            onTimetableLayoutChange(
                                timetableLayout.copy(courseNameMode = TimetableCourseNameMode.FULL),
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                        label = { Text("完整版") },
                    )
                    FilterChip(
                        selected = timetableLayout.courseNameMode == TimetableCourseNameMode.COMPACT,
                        onClick = {
                            onTimetableLayoutChange(
                                timetableLayout.copy(courseNameMode = TimetableCourseNameMode.COMPACT),
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                        label = { Text("精简版（前四字）") },
                    )
                }
            }
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("网纹背景", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "在课表网格上叠加淡灰色网纹，不改变配色",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = timetableLayout.gridMesh,
                        onCheckedChange = {
                            onTimetableLayoutChange(timetableLayout.copy(gridMesh = it))
                        },
                    )
                }
            }
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("课程卡片包边", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "给每个课表卡片加一圈细边框，让分界更明显",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = timetableLayout.courseCardBorder,
                        onCheckedChange = {
                            onTimetableLayoutChange(timetableLayout.copy(courseCardBorder = it))
                        },
                    )
                }
            }
            TextButton(
                onClick = { onTimetableLayoutChange(TimetableLayoutSettings()) },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("恢复课表默认排版")
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("课表日期", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (weekOneLabel != null) {
                    "第 1 教学周周一：$weekOneLabel${if (weekOneLocked) "（已手动对齐）" else ""}"
                } else {
                    "尚未对齐开学周。导入或刷新课表后，也可在此指定第 1 周日期。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 6.dp),
            )
            OutlinedButton(
                onClick = { showAdjustWeekOne = true },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.adjust_week_one_action))
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("上课提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "在距开课 1～${CourseReminderPreferences.formatLeadLabel(reminderLeadMinutes)} 内发送通知（可选 ${CourseReminderPreferences.formatLeadLabel(CourseReminderPreferences.MIN_LEAD_MINUTES)}～${CourseReminderPreferences.formatLeadLabel(CourseReminderPreferences.MAX_LEAD_MINUTES)}，后台约每 15 分钟检查当天与次日课表）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "提前提醒时间",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            OutlinedTextField(
                value = reminderDraft,
                onValueChange = { reminderDraft = it.filter { ch -> ch.isDigit() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = { Text("提前分钟数") },
                supportingText = {
                    Text(
                        "范围 ${CourseReminderPreferences.MIN_LEAD_MINUTES}～${CourseReminderPreferences.MAX_LEAD_MINUTES} 分钟（最长 1 天）",
                    )
                },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { applyReminderDraft() }),
            )
            TextButton(
                onClick = { applyReminderDraft() },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("保存提前时间（当前 ${CourseReminderPreferences.formatLeadLabel(reminderLeadMinutes)}）")
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("关于", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "当前版本 $appVersion",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedButton(
                onClick = onCheckUpdate,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text("检查更新")
            }
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("开源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "成电教务助手",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "https://github.com/MilLoong/UESTC-EAMS-Helper-App",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.GITHUB_REPO_APP)),
                    )
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("GitHub: UESTC-EAMS-Helper-App")
            }
        }
        if (BuildConfig.DEBUG && onDebugNotify != null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("调试", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "试发通知正文与正式提醒一致，格式为 [提前 ${CourseReminderPreferences.formatLeadLabel(reminderLeadMinutes)}]",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "需已允许通知权限",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = onDebugNotify,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("试发上课通知")
                }
            }
        }
    }
    if (showAdjustWeekOne) {
        AdjustWeekOneDialog(
            initialDate =
                weekOneMonday?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: TeachingWeekEstimator.upcomingSemesterStartMonday(),
            onDismiss = { showAdjustWeekOne = false },
            onConfirm = { day ->
                showAdjustWeekOne = false
                onAdjustWeekOne(day)
            },
        )
    }
}

@Composable
private fun TimetableLayoutSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Text(
        "$label：$value",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
