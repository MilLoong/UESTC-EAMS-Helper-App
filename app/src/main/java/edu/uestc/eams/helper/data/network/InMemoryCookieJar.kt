package edu.uestc.eams.helper.data.network

import edu.uestc.eams.helper.data.session.StoredCookie
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 线程安全的进程内 Cookie 存储；全网共享 [cookieJar]，供 OkHttp CAS/教务链路复用，
 * 再由 [snapshot] 导出给 WebView 注入。
 */
class InMemoryCookieJar : CookieJar {

    /** key：响应 [HttpUrl.host]（与 Chrome 同源策略分列存储风格接近）。 */
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val list = store.getOrPut(host) { mutableListOf() }
        synchronized(list) {
            for (incoming in cookies) {
                if (incoming.persistent && incoming.expiresAt <= System.currentTimeMillis()) {
                    list.removeAll {
                        it.name == incoming.name &&
                            normalizeDomain(it.domain) == normalizeDomain(incoming.domain) &&
                            it.path == incoming.path
                    }
                    continue
                }
                list.removeAll {
                    it.name == incoming.name &&
                        normalizeDomain(it.domain) == normalizeDomain(incoming.domain) &&
                        it.path == incoming.path
                }
                list.add(incoming)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val out = mutableListOf<Cookie>()
        store.values.forEach { bucket ->
            synchronized(bucket) {
                val stale = mutableListOf<Cookie>()
                for (c in bucket) {
                    if (expiresBefore(c, now)) {
                        stale.add(c)
                        continue
                    }
                    if (c.matches(url)) out += c
                }
                bucket.removeAll(stale)
            }
        }
        return out
    }

    /**
     * 返回当前 Jar 内**全部** Cookie 的快照（不按 URL 过滤），同名同域同路径在去重时已折叠。
     */
    fun snapshot(): List<Cookie> {
        pruneExpiredGlobally(System.currentTimeMillis())
        val out = mutableListOf<Cookie>()
        store.values.forEach { bucket ->
            synchronized(bucket) { out.addAll(bucket) }
        }
        return out.distinctBy {
            "${normalizeDomain(it.domain)}|${it.name}|${it.path}"
        }
    }

    fun clearHost(host: String) {
        store.remove(host)
    }

    fun clearAll() {
        store.clear()
    }

    private fun pruneExpiredGlobally(now: Long) {
        store.values.forEach { bucket ->
            synchronized(bucket) {
                bucket.removeIf { expiresBefore(it, now) }
            }
        }
    }

    private fun expiresBefore(cookie: Cookie, now: Long): Boolean {
        if (!cookie.persistent) return false
        return cookie.expiresAt <= now
    }

    private fun normalizeDomain(domain: String): String =
        domain.removePrefix(".").trim()
}

/**
 * 将 [edu.uestc.eams.helper.data.session.StoredCookie] 合并进 Jar（不写 WebView）。
 * `data/session` 仅承载 DTO；合并逻辑归属于 `data/network`。
 */
fun InMemoryCookieJar.mergeFromStored(stored: List<StoredCookie>) {
    for (c in stored) {
        val host = c.domain.removePrefix(".").lowercase()
        val url =
            HttpUrl.Builder()
                .scheme("https")
                .host(host)
                .encodedPath("/")
                .build()
        val b =
            Cookie.Builder()
                .name(c.name)
                .value(c.value)
                .domain(host)
                .path(c.path.ifBlank { "/" })
        if (c.secure) {
            b.secure()
        }
        c.expiresAtMillis?.let { b.expiresAt(it) }
        saveFromResponse(url, listOf(b.build()))
    }
}

/**
 * 先清空 Jar 再写入：用于 **WebView 导入**，与原 OkHttp 会话整包对齐，不与旧 Jar [合并掺杂]。
 */
fun InMemoryCookieJar.replaceJarWithStored(stored: List<StoredCookie>) {
    clearAll()
    mergeFromStored(stored)
}
