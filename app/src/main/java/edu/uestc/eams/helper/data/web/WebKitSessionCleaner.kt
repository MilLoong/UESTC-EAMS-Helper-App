package edu.uestc.eams.helper.data.web

import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** 清除 WebView Cookie 与 DOM 存储。 */
object WebKitSessionCleaner {

    /** 在主线程等待 removeAllCookies 完成。 */
    suspend fun removeCookiesAndDomStorage(timeoutMs: Long = 22_000L) {
        withContext(Dispatchers.Main) {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.removeAllCookies { _ ->
                        cm.flush()
                        runCatching { WebStorage.getInstance().deleteAllData() }
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
        }
    }
}
