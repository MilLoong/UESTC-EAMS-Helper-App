package edu.uestc.eams.helper.ui.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.uestc.eams.helper.AppLinks
import edu.uestc.eams.helper.BuildConfig
import edu.uestc.eams.helper.domain.model.UserProfile

@Composable
fun ProfileTab(
    loggedIn: Boolean,
    profile: UserProfile?,
    onLogin: () -> Unit,
    onDebugNotify: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
                Text("已登录，点顶栏刷新可同步学号与姓名。")
            } else {
                Text("未登录，登录后可在此查看学号与姓名。")
                TextButton(onClick = onLogin, modifier = Modifier.padding(top = 4.dp)) {
                    Text("去登录")
                }
            }
        }

        if (BuildConfig.DEBUG && onDebugNotify != null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("调试", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "预览上课提醒通知样式",
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
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.GITHUB_REPO_APP)),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("GitHub: UESTC-EAMS-Helper-App")
            }
        }
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
