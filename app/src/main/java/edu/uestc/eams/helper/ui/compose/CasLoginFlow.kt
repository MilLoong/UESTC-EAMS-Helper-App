package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import edu.uestc.eams.helper.R

/**
 * 登录框（对齐 v1.2.10）：同一 [AlertDialog] + 本地 remember 输入态 + 稳定主按钮。
 * 避免 sheets InputDialog 在倒计时 / status 刷新时重建导致验证码按钮只亮一瞬。
 */
@Composable
fun CasLoginFlow(
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onSubmitSms: (String) -> Unit,
    onResendSms: () -> Unit,
    onBackFromSms: () -> Unit,
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
                if (awaitingSms) onBackFromSms() else onDismiss()
            }
        },
        title = {
            Text(
                if (awaitingSms) {
                    stringResource(R.string.login_sms_title)
                } else {
                    stringResource(R.string.login_credentials_title)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.login_freeze_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    if (awaitingSms) {
                        stringResource(R.string.login_sms_subtitle)
                    } else {
                        stringResource(R.string.login_credentials_subtitle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!loginStatus.isNullOrBlank()) {
                    Text(
                        loginStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            when {
                                loginStatus.contains("正在") ||
                                    loginStatus.contains("请填写") ||
                                    loginStatus.contains("验证码已") ->
                                    MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            },
                    )
                }
                if (!awaitingSms) {
                    Text(
                        stringResource(R.string.login_browser_fallback_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                                if (passwordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription =
                                    if (passwordVisible) "隐藏密码" else "显示密码",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().autofillPassword(),
                )
                OutlinedTextField(
                    value = sms,
                    onValueChange = { sms = it },
                    label = { Text("短信验证码") },
                    singleLine = true,
                    enabled = !loading && awaitingSms,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
            // 同一 TextButton，避免 awaitingSms 切换时替换按钮导致误触再次 login()
            TextButton(
                onClick = {
                    if (awaitingSms) {
                        onSubmitSms(sms)
                    } else {
                        onLogin(user.trim(), pass)
                    }
                },
                enabled =
                    if (awaitingSms) {
                        !loading && sms.isNotBlank()
                    } else {
                        !loading && user.isNotBlank() && pass.isNotBlank()
                    },
            ) {
                Text(
                    when {
                        loading && awaitingSms -> "登录中…"
                        loading -> "获取中…"
                        awaitingSms -> "登录"
                        else -> "获取验证码"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (awaitingSms) {
                        sms = ""
                        onBackFromSms()
                    } else {
                        cancelLoginAutofill(context)
                        onDismiss()
                    }
                },
                enabled = !loading,
            ) {
                Text(if (awaitingSms) "返回" else "稍后")
            }
        },
    )
}
