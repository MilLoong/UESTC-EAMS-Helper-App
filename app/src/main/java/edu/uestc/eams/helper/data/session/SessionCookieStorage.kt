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

/** 持久化 OkHttp Cookie，供下次启动恢复会话。 */
class SessionCookieStorage(
    private val prefs: SharedPreferences,
) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
    )

    fun persistFromJar(jar: InMemoryCookieJar) {
        val stored = CookieExporter.fromOkHttp(jar.snapshot())
        // 不要用空快照覆盖已有会话：避免偶发的登录探测失败把有效 Cookie 冲掉。
        if (stored.isEmpty()) return
        val json = storedListToJson(stored)
        prefs.edit().putString(KEY_JSON, json).commit()
        DebugSessionSidecar.save(json)
    }

    fun restoreInto(jar: InMemoryCookieJar) {
        if (loadStoredList().isEmpty()) {
            DebugSessionSidecar.load()?.let { json ->
                prefs.edit().putString(KEY_JSON, json).commit()
            }
        } else {
            prefs.getString(KEY_JSON, null)?.let(DebugSessionSidecar::save)
        }
        val list = loadStoredList()
        if (list.isEmpty()) return
        jar.clearAll()
        jar.mergeFromStored(list)
    }

    /** 清空内存 Jar 与本地 Cookie 快照。 */
    fun clearJarAndPersistence(jar: InMemoryCookieJar): Boolean {
        jar.clearAll()
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

        /** 判断是否已有可用于一网通门户的 Cookie。 */
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
