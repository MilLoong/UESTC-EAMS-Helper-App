package edu.uestc.eams.helper.data.web

import android.webkit.CookieManager
import android.webkit.ValueCallback
import edu.uestc.eams.helper.data.session.StoredCookie
import java.util.concurrent.atomic.AtomicInteger

/**
 * OkHttp Cookie 快照 → [CookieManager]，供嵌入式 WebView 复用会话。
 * **`online.uestc.edu.cn` 域名**若在 [StoredCookie.domain] 中也会按 `https://online.uestc.edu.cn` 写入，一网通 SPA 备选入口需同源 Cookie。
 */
class WebViewCookieInjector {

    /**
     * [CookieManager.setCookie] 会将 Cookie **异步** 写入 Chromium 存储；若在回调完成前调用 [CookieManager.flush]，
     * 随后立刻 [CookieManager.getCookie] 常为 **空**。此处等全部回调后再 [flush]。
     */
    fun injectAll(cookies: List<StoredCookie>) {
        val cm = CookieManager.getInstance().apply { setAcceptCookie(true) }
        if (cookies.isEmpty()) {
            cm.flush()
            return
        }
        val pending = AtomicInteger(cookies.size)
        val finishFlush = Runnable {
            if (pending.decrementAndGet() == 0) {
                cm.flush()
            }
        }
        cookies.forEach { c ->
            val origin = originForStoredCookie(c)
            val pair = buildSetCookiePair(c)
            cm.setCookie(origin, pair, ValueCallback<Boolean> { _ -> finishFlush.run() })
        }
    }

    /** Debug：清空应用 WebView Cookie（影响所有站点）。仅在调试调用。 */
    fun clearAllCookiesDebug() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies { /* no-op ack */ }
        cm.flush()
    }

    private fun originForStoredCookie(c: StoredCookie): String =
        "https://${normalizeHost(c.domain)}".trimEnd('/')

    private fun normalizeHost(domain: String): String =
        domain.removePrefix(".").trim()

    private fun buildSetCookiePair(c: StoredCookie): String {
        val sb = StringBuilder()
        sb.append(c.name).append('=').append(c.value)
        val dom = normalizeHost(c.domain)

        sb.append("; Domain=").append(dom)
        sb.append("; Path=").append(c.path.ifBlank { "/" })

        c.expiresAtMillis?.let { exp ->
            val maxAgeSeconds = ((exp - System.currentTimeMillis()) / 1000L).coerceAtLeast(60L)
            sb.append("; Max-Age=").append(maxAgeSeconds)
        }

        if (c.secure) {
            sb.append("; Secure")
        }

        /*
         * HttpOnly 在 Cookie 字符串中的表现因 WebView/Chromium 版本而异；
         * HttpOnly Cookie 仍可随 OkHttp Jar 快照注入；如遇失败可去掉 Secure/Domain 再试。
         */
        return sb.toString()
    }
}
