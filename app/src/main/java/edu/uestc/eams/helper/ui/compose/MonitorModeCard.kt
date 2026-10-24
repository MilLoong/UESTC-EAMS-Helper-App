package edu.uestc.eams.helper.ui.compose

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import edu.uestc.eams.helper.notification.CourseReminderAlarmScheduler
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import edu.uestc.eams.helper.server.ServerNotificationService
import edu.uestc.eams.helper.server.ServerNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun MonitorModeCard() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(ServerNotifications.mode(context)) }
    var showServer by remember { mutableStateOf(mode != "local") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var boundServer by remember { mutableStateOf(runCatching { ServerNotifications.read(context).optString("server") }.getOrDefault("")) }
    var pendingQr by remember { mutableStateOf<JSONObject?>(null) }
    var confirmOfflineLocal by remember { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { contents ->
            try {
                val qr = JSONObject(contents)
                require(qr.getInt("protocol") == 4) { "二维码版本不匹配" }
                require(qr.getLong("expires_at") > System.currentTimeMillis() / 1000) { "二维码已过期，请刷新电脑页面" }
                ServerNotifications.validateOrigin(qr.getString("server"), qr.optBoolean("local_dev"))
                pendingQr = qr
                showServer = true
            } catch (e: Exception) {
                error = "扫码失败：${e.message}"
                showServer = true
            }
        }
    }

    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mode = ServerNotifications.mode(context)
                if (mode != "local") showServer = true
                boundServer = runCatching { ServerNotifications.read(context).optString("server") }.getOrDefault("")
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun select(target: String) {
        if (busy) return
        error = null
        if (target == "server" && !runCatching { ServerNotifications.read(context).has("token") }.getOrDefault(false)) {
            showServer = true
            return
        }
        if (mode == target) {
            showServer = target == "server"
            return
        }
        busy = true
        scope.launch {
            try {
                context.stopService(Intent(context, ServerNotificationService::class.java))
                ServerNotifications.switchMode(context, target)
                if (target == "local") CourseReminderAlarmScheduler.reschedule(context)
                else ServerNotifications.startReceiving(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "切换未完成：${e.message}"
            } finally {
                mode = ServerNotifications.mode(context)
                showServer = mode != "local"
                busy = false
            }
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text("监听模式", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("local" to "本地监听", "server" to "服务器监听").forEach { (value, label) ->
                val selected = if (showServer && mode == "local") value == "server" else ServerNotifications.selectedMode(context) == value
                Row(
                    Modifier.fillMaxWidth().selectable(selected, enabled = !busy, role = Role.RadioButton, onClick = { select(value) }).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null, enabled = !busy)
                    Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Text(
            "本地：服务器确认暂停后由手机定时提醒；服务器：云端生成课程提醒，手机接收通知，恢复运行后补取通知",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (busy) Text("正在切换…", style = MaterialTheme.typography.bodySmall)
        if (mode == "switching" && !busy) Text("切换待确认，请重新选择模式", color = MaterialTheme.colorScheme.error)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!busy && (error != null || mode == "switching" || mode == "blocked")) {
            TextButton(onClick = { confirmOfflineLocal = true }) { Text("离线恢复本地监听") }
        }
        if (showServer) {
            Text(
                if (boundServer.isBlank()) "未绑定服务器" else "已绑定：$boundServer",
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy, onClick = {
                error = null
                scanner.launch(
                    ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setBeepEnabled(false).setPrompt("扫描服务器配对二维码")
                        .setOrientationLocked(true)
                        .setCaptureActivity(edu.uestc.eams.helper.server.PortraitCaptureActivity::class.java),
                )
            }) {
                Text(if (boundServer.isBlank()) "扫码绑定" else "重新扫码")
            }
            if (boundServer.isNotBlank()) {
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = {
                        if (busy) return@TextButton
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                context.stopService(Intent(context, ServerNotificationService::class.java))
                                ServerNotifications.unbind(context)
                                CourseReminderAlarmScheduler.reschedule(context)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = "解绑失败：${e.message}"
                            } finally {
                                mode = ServerNotifications.mode(context)
                                boundServer =
                                    runCatching {
                                        ServerNotifications.read(context).optString("server")
                                    }.getOrDefault("")
                                showServer = mode != "local" || boundServer.isNotBlank()
                                busy = false
                            }
                        }
                    },
                ) {
                    Text("解绑服务器")
                }
            }
        }
    }
    if (confirmOfflineLocal) {
        AlertDialog(
            onDismissRequest = { confirmOfflineLocal = false },
            title = { Text("恢复本地监听？") },
            text = { Text("手机将停止接收服务器通知，改用本地课表提醒。此操作无法确认旧服务器是否已暂停，服务器仍可能保留并生成提醒记录。联网后可重新选择模式同步状态。") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        ServerNotifications.restoreLocalOffline(context)
                        mode = ServerNotifications.mode(context)
                        showServer = false
                        error = null
                    } catch (e: Exception) {
                        error = "恢复失败：${e.message}"
                    }
                    confirmOfflineLocal = false
                }) { Text("恢复本地监听") }
            },
            dismissButton = { TextButton(onClick = { confirmOfflineLocal = false }) { Text("取消") } },
        )
    }
    pendingQr?.let { qr ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingQr = null },
            title = { Text("连接服务器") },
            text = { Text("${qr.getString("server")}\n将上传设备信息与未来 7 天缓存课表，不上传学校密码或 Cookie。${if (qr.optBoolean("local_dev")) "\n局域网 HTTP 为明文连接，仅在可信网络使用。" else ""}") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            // 重新扫码：先尽量在服务端解绑；若地址已失效则仅清本地，再配对。
                            if (runCatching { ServerNotifications.read(context).has("token") }.getOrDefault(false)) {
                                runCatching { ServerNotifications.unbind(context) }
                                    .onFailure { ServerNotifications.forgetAfterRevocation(context) }
                            }
                            ServerNotifications.pair(context, qr.getString("server"), qr.getString("code"), qr.optBoolean("local_dev"))
                            ServerNotifications.startReceiving(context)
                            error = null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "连接失败：${e.message}"
                        } finally {
                            pendingQr = null
                            mode = ServerNotifications.mode(context)
                            boundServer = runCatching { ServerNotifications.read(context).optString("server") }.getOrDefault("")
                            showServer = true
                            busy = false
                        }
                    }
                }) { Text(if (busy) "连接中…" else "连接") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingQr = null }) { Text("取消") } },
        )
    }
}
