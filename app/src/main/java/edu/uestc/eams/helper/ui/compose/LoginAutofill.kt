package edu.uestc.eams.helper.ui.compose

import android.content.Context
import android.os.Build
import android.view.autofill.AutofillManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics

/** 标记为学号/用户名，点输入框时由系统 Autofill 服务弹出已保存账号（与 Chrome 类似）。 */
fun Modifier.autofillUsername(): Modifier =
    semantics { contentType = ContentType.Username }

/** 标记为密码字段，与 [autofillUsername] 成对供系统识别登录表单。 */
fun Modifier.autofillPassword(): Modifier =
    semantics { contentType = ContentType.Password }

/** 短信验证码字段。 */
fun Modifier.autofillSmsOtp(): Modifier =
    semantics { contentType = ContentType.SmsOtpCode }

/** 登录成功后通知系统保存本次输入的账号密码（若用户已在密码管理器中开启保存）。 */
fun commitLoginAutofill(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    context.getSystemService(AutofillManager::class.java)?.commit()
}

/** 取消本次自动填充会话（关闭登录框且未成功时）。 */
fun cancelLoginAutofill(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    context.getSystemService(AutofillManager::class.java)?.cancel()
}
