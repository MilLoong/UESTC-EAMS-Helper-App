package edu.uestc.eams.helper.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.maxkeppeker.sheets.core.models.base.ButtonStyle
import com.maxkeppeker.sheets.core.models.base.Header
import com.maxkeppeker.sheets.core.models.base.SelectionButton
import com.maxkeppeker.sheets.core.models.base.rememberUseCaseState
import com.maxkeppeler.sheets.input.InputDialog
import com.maxkeppeler.sheets.input.models.InputCustomView
import com.maxkeppeler.sheets.input.models.InputHeader
import com.maxkeppeler.sheets.input.models.InputSelection
import com.maxkeppeler.sheets.input.models.InputTextField
import com.maxkeppeler.sheets.input.models.InputTextFieldType
import com.maxkeppeler.sheets.input.models.ValidationResult
import edu.uestc.eams.helper.R

private const val KEY_STUDENT_ID = "student_id"
private const val KEY_PASSWORD = "password"
private const val KEY_SMS = "sms"

@OptIn(ExperimentalMaterial3Api::class)
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
    var studentId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    if (awaitingSms) {
        SmsInputDialog(
            loginStatus = loginStatus,
            loading = loading,
            smsResendSecondsLeft = smsResendSecondsLeft,
            onSubmitSms = onSubmitSms,
            onResendSms = onResendSms,
            onBack = onBackFromSms,
        )
    } else {
        CredentialsInputDialog(
            studentId = studentId,
            password = password,
            onStudentIdChange = { studentId = it },
            onPasswordChange = { password = it },
            loginStatus = loginStatus,
            loading = loading,
            onLogin = onLogin,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CredentialsInputDialog(
    studentId: String,
    password: String,
    onStudentIdChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    loginStatus: String?,
    loading: Boolean,
    onLogin: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberUseCaseState(
            visible = true,
            onDismissRequest = { onDismiss() },
        )
    var showPassword by remember { mutableStateOf(false) }
    LaunchedEffect(loading, loginStatus) { state.show() }
    InputDialog(
        state = state,
        header = Header.Default(title = stringResource(R.string.login_credentials_title)),
        properties =
            DialogProperties(
                dismissOnBackPress = !loading,
                dismissOnClickOutside = !loading,
            ),
        selection =
            InputSelection(
                input =
                    listOf(
                        InputTextField(
                            text = studentId,
                            header = InputHeader(title = "学号"),
                            type = InputTextFieldType.OUTLINED,
                            singleLine = true,
                            required = true,
                            key = KEY_STUDENT_ID,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            changeListener = { onStudentIdChange(it.orEmpty()) },
                            validationListener = { value ->
                                if (value.isNullOrBlank()) {
                                    ValidationResult.Invalid("请填写学号")
                                } else {
                                    ValidationResult.Valid
                                }
                            },
                        ),
                        InputTextField(
                            text = password,
                            header = InputHeader(title = "密码"),
                            type = InputTextFieldType.OUTLINED,
                            singleLine = true,
                            required = true,
                            key = KEY_PASSWORD,
                            visualTransformation =
                                if (showPassword) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            changeListener = { onPasswordChange(it.orEmpty()) },
                            validationListener = { value ->
                                if (value.isNullOrBlank()) {
                                    ValidationResult.Invalid("请填写密码")
                                } else {
                                    ValidationResult.Valid
                                }
                            },
                        ),
                        InputCustomView(
                            view = {
                                PasswordVisibilityToggle(
                                    showPassword = showPassword,
                                    onToggle = { showPassword = !showPassword },
                                )
                            },
                        ),
                        InputCustomView(
                            view = { LoginCredentialsHints(loginStatus = loginStatus) },
                        ),
                    ),
                negativeButton = SelectionButton("稍后"),
                onNegativeClick = onDismiss,
                positiveButton =
                    SelectionButton(
                        if (loading) "登录中…" else "登录",
                        type = ButtonStyle.TEXT,
                    ),
                onPositiveClick = { result ->
                    if (!loading) {
                        onLogin(
                            result.getString(KEY_STUDENT_ID).orEmpty().trim(),
                            result.getString(KEY_PASSWORD).orEmpty(),
                        )
                    }
                },
            ),
    )
}

@Composable
private fun PasswordVisibilityToggle(
    showPassword: Boolean,
    onToggle: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.TextButton(onClick = onToggle) {
            Icon(
                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = null,
            )
            Text(if (showPassword) "隐藏密码" else "显示密码")
        }
    }
}

@Composable
private fun LoginCredentialsHints(loginStatus: String?) {
    val subtitle = stringResource(R.string.login_credentials_subtitle)
    val freeze = stringResource(R.string.login_freeze_warning)
    val fallback = stringResource(R.string.login_browser_fallback_hint)
    val subtitleText =
        buildAnnotatedString {
            append(subtitle)
            listOf("学号", "密码").forEach { term ->
                var start = 0
                while (true) {
                    val index = subtitle.indexOf(term, start)
                    if (index < 0) break
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), index, index + term.length)
                    start = index + term.length
                }
            }
        }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = freeze,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        if (!loginStatus.isNullOrBlank()) {
            Text(
                text = loginStatus,
                style = MaterialTheme.typography.bodySmall,
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
            text = fallback,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsInputDialog(
    loginStatus: String?,
    loading: Boolean,
    smsResendSecondsLeft: Int,
    onSubmitSms: (String) -> Unit,
    onResendSms: () -> Unit,
    onBack: () -> Unit,
) {
    val state =
        rememberUseCaseState(
            visible = true,
            onDismissRequest = { onBack() },
        )
    LaunchedEffect(loading, loginStatus, smsResendSecondsLeft) { state.show() }
    val subtitle = stringResource(R.string.login_sms_subtitle)
    val body =
        buildString {
            append(subtitle)
            if (!loginStatus.isNullOrBlank()) {
                append("\n\n")
                append(loginStatus)
            }
        }
    val resendLabel =
        if (smsResendSecondsLeft > 0) {
            "重新发送 (${smsResendSecondsLeft}s)"
        } else {
            "重新发送验证码"
        }
    InputDialog(
        state = state,
        header = Header.Default(title = stringResource(R.string.login_sms_title)),
        properties =
            DialogProperties(
                dismissOnBackPress = !loading,
                dismissOnClickOutside = !loading,
            ),
        selection =
            InputSelection(
                input =
                    listOf(
                        InputTextField(
                            header = InputHeader(title = "短信验证码", body = body),
                            type = InputTextFieldType.OUTLINED,
                            singleLine = true,
                            required = true,
                            key = KEY_SMS,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            validationListener = { value ->
                                if (value.isNullOrBlank()) {
                                    ValidationResult.Invalid("请填写短信验证码")
                                } else {
                                    ValidationResult.Valid
                                }
                            },
                        ),
                    ),
                extraButton = SelectionButton(resendLabel),
                onExtraButtonClick = {
                    if (!loading && smsResendSecondsLeft == 0) onResendSms()
                },
                negativeButton = SelectionButton("返回"),
                onNegativeClick = onBack,
                positiveButton =
                    SelectionButton(
                        if (loading) "提交中…" else "提交验证码",
                    ),
                onPositiveClick = { result ->
                    if (!loading) {
                        onSubmitSms(result.getString(KEY_SMS).orEmpty().trim())
                    }
                },
            ),
    )
}
