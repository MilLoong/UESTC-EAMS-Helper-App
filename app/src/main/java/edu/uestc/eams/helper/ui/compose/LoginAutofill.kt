package edu.uestc.eams.helper.ui.compose

import android.content.Context
import android.os.Build
import android.view.autofill.AutofillManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics

fun Modifier.autofillUsername(): Modifier =
    semantics { contentType = ContentType.Username }

fun Modifier.autofillPassword(): Modifier =
    semantics { contentType = ContentType.Password }

/** 短信验证码字段。 */
fun Modifier.autofillSmsOtp(): Modifier =
    semantics { contentType = ContentType.SmsOtpCode }

fun commitLoginAutofill(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    context.getSystemService(AutofillManager::class.java)?.commit()
}

fun cancelLoginAutofill(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    context.getSystemService(AutofillManager::class.java)?.cancel()
}
