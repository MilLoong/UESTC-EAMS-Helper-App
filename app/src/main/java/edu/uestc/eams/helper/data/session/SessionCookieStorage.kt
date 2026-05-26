package edu.uestc.eams.helper.data.session

import android.content.Context
import android.content.SharedPreferences
import edu.uestc.eams.helper.data.network.ApiConstants
import edu.uestc.eams.helper.data.network.InMemoryCookieJar
import edu.uestc.eams.helper.data.network.mergeFromStored
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * 将 OkHttp Jar 快照以 JSON 明文落在 [SharedPreferences]（仅供本机复用会话，不构成安全边界）。
 *
 * WebView [读取页面 Cookie]合并进 Jar 后也应调用 [persistFromJar]，与 OkHttp 登录路径一致。
 */
class SessionCookieStorage(
    private val prefs: SharedPreferences,
) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
    )

    /** 快照落盘。使用 [commit]，避免紧接着杀进程时被 [apply] 异步丢写入。*/
    fun persistFromJar(jar: InMemoryCookieJar) {
        prefs
            .edit()
            .putString(KEY_JSON, storedListToJson(CookieExporter.fromOkHttp(jar.snapshot())))
            .commit()
    }

    fun restoreInto(jar: InMemoryCookieJar) {
        val list = loadStoredList()
        if (list.isEmpty()) return
        jar.clearAll()
        jar.mergeFromStored(list)
    }

    /**
     * 清空 Jar 并 **同步**删除 prefs 快照（须 [commit]，否则易被当成[清空无效、下次照旧读缓存]）。
     * 应用内 WebView / [android.webkit.CookieManager] 需由界面层另外 [android.webkit.CookieManager.removeAllCookies]。
     */
    fun clearJarAndPersistence(jar: InMemoryCookieJar): Boolean {
        jar.clearAll()
        // 本文件专用 prefs：整库清空避免残留键；须与 [PREF_NAME] 独享一致。
        return prefs.edit().clear().commit()
    }

    private fun storedListToJson(stored: List<StoredCookie>): String {
        val arr = JSONArray()
        for (c in stored) {
            val o =
                JSONObject().apply {
                    put("name", c.name)
                    put("value", c.value)
                    put("domain", c.domain)
                    put("path", c.path)
                    put("expiresAtMillis", c.expiresAtMillis ?: JSONObject.NULL)
                    put("secure", c.secure)
                    put("httpOnly", c.httpOnly)
                }
            arr.put(o)
        }
        return arr.toString()
    }

    private fun loadStoredList(): List<StoredCookie> {
        val raw = prefs.getString(KEY_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        StoredCookie(
                            name = o.getString("name"),
                            value = o.getString("value"),
                            domain = o.getString("domain"),
                            path = o.optString("path", "/").ifBlank { "/" },
                            expiresAtMillis =
                                when {
                                    !o.has("expiresAtMillis") || o.isNull("expiresAtMillis") -> null
                                    else ->
                                        runCatching { o.getLong("expiresAtMillis") }.getOrNull()?.takeIf { it > 0L }
                                },
                            secure = o.optBoolean("secure", false),
                            httpOnly = o.optBoolean("httpOnly", false),
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val PREF_NAME = "uestc_okhttp_cookie_snapshot_v1"
        private const val KEY_JSON = "cookies_json"

        /**
         * 一网通日程前：**仅 idas CASTGC≠已在 online 门户落盘**。多探针 URL（/page vs /page/）+ Snapshot 兜底，减少误报。
         */
        fun hasCookiesForOnline(jar: InMemoryCookieJar): Boolean {
            val probes = onlineProbeHttpUrls()
            if (probes.any { jar.loadForRequest(it).isNotEmpty() }) return true
            val snap = jar.snapshot()
            return probes.any { u -> snap.any { it.matchesRequestUrl(u) } }
        }

        private fun onlineProbeHttpUrls(): List<HttpUrl> =
            buildList {
                add(ApiConstants.ONLINE_PAGE_URL)
                val root = ApiConstants.ONLINE_ORIGIN.trimEnd('/')
                add("$root/")
                add("$root/page")
                add("$root/page/")
                add(ApiConstants.ONLINE_SCHEDULE_LIST_URL)
            }.mapNotNull { it.toHttpUrlOrNull() }

        private fun Cookie.matchesRequestUrl(url: HttpUrl): Boolean {
            if (persistent && expiresAt <= System.currentTimeMillis()) return false
            return matches(url)
        }
    }
}
