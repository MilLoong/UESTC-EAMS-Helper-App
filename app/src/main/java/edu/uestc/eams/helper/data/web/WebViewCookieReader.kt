package edu.uestc.eams.helper.data.web

import android.webkit.CookieManager
import edu.uestc.eams.helper.data.network.ApiConstants
import edu.uestc.eams.helper.data.session.StoredCookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 从嵌入式 WebView 所用的 [CookieManager] 读出 Cookie。
 *
 * JS 里的 `document.cookie` **读不到 HttpOnly**，而 [CookieManager.getCookie] 应包含 HttpOnly/Secure，
 * **但**必须与[本会话实际访问过的 URL]匹配 Cookie 的路径/分区等；单靠固定两三个 URL 常读不到会话。
 *
 * @param prioritizedUrls **当前 Tab 的真实 URL / 前进后退历史**，应放在最前面；其次再扫默认 CAS/一网通种子。
 */
object WebViewCookieReader {

    /** 固定种子：教务根、CAS 登录链等（与 prioritized 合并后再去重）。*/
    private val DEFAULT_SNAPSHOT_URLS =
        listOf(
            ApiConstants.casLoginUrlWithService(),
            "${ApiConstants.CAS_ORIGIN}/authserver/login",
            "${ApiConstants.CAS_ORIGIN}/authserver/",
            ApiConstants.CAS_SERVICE_RAW,
            ApiConstants.ONLINE_PAGE_URL,
            ApiConstants.ONLINE_SCHEDULE_LIST_URL,
            "https://portal.uestc.edu.cn/",
            "https://portal.uestc.edu.cn/auth/",
        )

    fun collectUeStcSnapshot(prioritizedUrls: List<String> = emptyList()): List<StoredCookie> {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            flush()
        }
        val merged = LinkedHashMap<String, StoredCookie>()
        for (url in buildProbeUrls(prioritizedUrls)) {
            val httpUrl =
                try {
                    url.toHttpUrl()
                } catch (_: Exception) {
                    continue
                }
            val hostLower = httpUrl.host.lowercase()
            if (!isSchoolRelatedHost(hostLower)) continue

            val raw = CookieManager.getInstance().getCookie(url) ?: continue
            parseCookiePairs(raw, domain = httpUrl.host, secure = httpUrl.scheme == "https").forEach { c ->
                merged["${c.domain.lowercase()}|${c.name}|${c.path}"] = c
            }
        }
        return merged.values.toList()
    }

    private fun buildProbeUrls(prioritized: List<String>): List<String> =
        LinkedHashSet<String>().apply {
            prioritized.forEach { expandUrlProbe(it)?.forEach { u -> add(u) } }
            DEFAULT_SNAPSHOT_URLS.forEach { expandUrlProbe(it)?.forEach { u -> add(u) } }
        }.toList()

    private fun expandUrlProbe(raw: String): List<String>? {
        val t = raw.trim()
        if (!t.startsWith("http")) return null
        val base: HttpUrl =
            try {
                t.toHttpUrl()
            } catch (_: Exception) {
                return null
            }
        if (!isSchoolRelatedHost(base.host.lowercase())) return null

        val schemeHost = "${base.scheme}://${base.host}"

        /*
         * getCookie(URL) 的匹配与[请求的 URL]有关：
         * 同时尝试主页根、无前导斜杠的路径、原始完整 URL，减少[看得到页面但枚举不到 Cookie]的假阴性。
         */
        return buildList {
            add(t)
            add("$schemeHost/")
            val path = base.encodedPath
            if (path.isNotEmpty() && path != "/") {
                add("$schemeHost$path")
            }
            if (base.host.lowercase().contains("idas.uestc")) {
                add("${ApiConstants.CAS_ORIGIN}/authserver/login")
            }
            if (base.host.lowercase().contains("online.uestc")) {
                add(ApiConstants.ONLINE_PAGE_URL)
                add(ApiConstants.ONLINE_SCHEDULE_LIST_URL)
            }
        }.distinct()
    }

    /** 电子科技大学相关域名（门户、CAS、教务子域等）。*/
    internal fun isSchoolRelatedHost(hostLower: String): Boolean =
        hostLower == "uestc.edu.cn" || hostLower.endsWith(".uestc.edu.cn")

    internal fun parseCookiePairs(raw: String, domain: String, secure: Boolean): List<StoredCookie> =
        if (raw.isBlank()) {
            emptyList()
        } else {
            raw.split(COOKIE_PAIR_DELIM).mapNotNull { chunk ->
                val part = chunk.trim().ifBlank { return@mapNotNull null }
                val eq = part.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val name = part.substring(0, eq).trim()
                val value = part.substring(eq + 1).trim()
                if (name.isEmpty() || name.startsWith('$')) return@mapNotNull null

                StoredCookie(
                    name = name,
                    value = value,
                    domain = domain,
                    path = "/",
                    expiresAtMillis = null,
                    secure = secure,
                    httpOnly = false,
                )
            }
        }

    /** `evaluateJavascript` 返回的 JSON 字符串外壳（带引号/转义）→ 纯文本。*/
    fun unwrapEvaluateJavascriptString(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty() || t == "null") return ""
        return kotlin.runCatching { org.json.JSONTokener(t).nextValue().toString() }
            .getOrElse { t.removeSurrounding("\"").replace("\\\"", "\"") }
    }

    /**
     * 顶栏 document.cookie：**不含 HttpOnly**，但在部分机型/Chromium 上 [CookieManager.getCookie] 滞后或拿不到时仍可补会话。
     */
    fun snapshotFromDocumentCookie(header: String, pageUrl: String?): List<StoredCookie> {
        val u =
            pageUrl?.trim()?.takeIf { it.startsWith("http") }
                ?: return emptyList()
        val hu =
            try {
                u.toHttpUrl()
            } catch (_: Exception) {
                return emptyList()
            }
        if (!isSchoolRelatedHost(hu.host.lowercase())) return emptyList()
        return parseCookiePairs(header.trim(), domain = hu.host, secure = hu.scheme == "https")
    }

    /** [CookieManager] + [snapshotFromDocumentCookie] 去重合并（后者覆盖同名同 path 时需整表重排：后写覆盖前先写）。 */
    fun mergeCookieLists(vararg parts: List<StoredCookie>): List<StoredCookie> {
        val m = LinkedHashMap<String, StoredCookie>()
        for (part in parts) {
            for (c in part) {
                m["${c.domain.lowercase()}|${c.name}|${c.path}"] = c
            }
        }
        return m.values.toList()
    }

    private val COOKIE_PAIR_DELIM = Regex(";\\s*")
}
