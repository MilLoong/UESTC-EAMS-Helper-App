package edu.uestc.eams.helper.data.web

import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** 等待 [CookieManager.removeAllCookies] 完成后再 [flush]，并清 [WebStorage]（会话类[缓存]常与 DOM 存储交织）。*/
object WebKitSessionCleaner {

    /**
     * 必须在 Main 线程调用 [CookieManager] 的典型实践；超时防 OEM 永远不回调。
     */
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
