package edu.uestc.eams.helper.data.session

import okhttp3.Cookie

fun Cookie.toStored(): StoredCookie =
    StoredCookie(
        name = name,
        value = value,
        domain = domain,
        path = path ?: "/",
        expiresAtMillis = if (persistent) expiresAt else null,
        secure = secure,
        httpOnly = httpOnly,
    )

object CookieExporter {

    /** 去重：`domain + name + path`（对齐 OkHttp Cookie 等价键）。 */
    fun fromOkHttp(all: List<Cookie>): List<StoredCookie> {
        val out = LinkedHashMap<String, StoredCookie>()
        all.forEach { c ->
            val key = "${c.domain.lowercase()}|${c.name}|${c.path}"
            out[key] = c.toStored()
        }
        return out.values.toList()
    }
}
