package edu.uestc.eams.helper.data.eamsapp

import edu.uestc.eams.helper.data.network.InMemoryCookieJar
import okhttp3.Cookie

/** 移动教务 Cookie 组装、JWT 识别与 Jar 读写。 */
object EamsAppCookie {

    private const val HOST = "eamsapp.uestc.edu.cn"
    private val OPTIONAL_ORDER = listOf("_ga", "_ga_968CMWQK03")

    fun looksLikeJwt(value: String): Boolean =
        value.length > 20 && value.count { it == '.' } >= 2

    fun pickJwtFromJar(jar: InMemoryCookieJar): String? {
        jar.snapshot()
            .filter { it.domain.contains(HOST) && it.name.equals("JSESSIONID", ignoreCase = true) }
            .map { it.value }
            .firstOrNull { looksLikeJwt(it) }
            ?.let { return it }
        return jar.snapshot()
            .filter { it.name.equals("JSESSIONID", ignoreCase = true) }
            .map { it.value }
            .firstOrNull { looksLikeJwt(it) }
    }

    fun pickVjuid(jar: InMemoryCookieJar): String? {
        val names =
            setOf(
                "cookie_vjuid",
                "cookie_vjuid_portal_login",
                "vjuid",
            )
        return jar.snapshot()
            .firstOrNull { c -> c.name.lowercase() in names }
            ?.value
    }

    fun formatHeader(
        jsessionid: String,
        vjuid: String? = null,
        extras: List<Pair<String, String>> = emptyList(),
    ): String {
        val parts = mutableListOf("JSESSIONID=$jsessionid")
        if (!vjuid.isNullOrBlank()) {
            parts += if (vjuid.contains("cookie_vjuid")) vjuid else "cookie_vjuid=$vjuid"
        }
        extras.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) parts += "$k=$v" }
        return parts.joinToString("; ")
    }

    fun composeFromJar(jar: InMemoryCookieJar, jwt: String): String {
        val extras = mutableListOf<Pair<String, String>>()
        val seen = mutableMapOf<String, String>()
        jar.snapshot().forEach { c ->
            if (c.name in OPTIONAL_ORDER) seen[c.name] = c.value
        }
        OPTIONAL_ORDER.forEach { k -> seen[k]?.let { extras += k to it } }
        return formatHeader(jwt, pickVjuid(jar), extras)
    }

    fun parseHeaderValue(cookieHeader: String, name: String): String? {
        cookieHeader.split(';').forEach { part ->
            val idx = part.indexOf('=')
            if (idx > 0 && part.substring(0, idx).trim().equals(name, ignoreCase = true)) {
                return part.substring(idx + 1).trim()
            }
        }
        return null
    }

    /** 登录/换票前清掉移动教务域 Cookie，避免复用过期 JWT 或半截短 `JSESSIONID`。 */
    fun clearEamsappHosts(jar: InMemoryCookieJar) {
        jar.clearHost(HOST)
    }

    fun storeJwtInJar(jar: InMemoryCookieJar, jwt: String) {
        val cookie =
            Cookie.Builder()
                .name("JSESSIONID")
                .value(jwt)
                .domain(HOST)
                .path("/")
                .build()
        val url =
            okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host(HOST)
                .build()
        jar.saveFromResponse(url, listOf(cookie))
    }
}
