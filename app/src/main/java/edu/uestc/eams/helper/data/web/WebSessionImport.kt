package edu.uestc.eams.helper.data.web

import edu.uestc.eams.helper.data.eamsapp.EamsAppCookie
import edu.uestc.eams.helper.data.session.StoredCookie
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 校验内置浏览器「导入会话」是否来自移动教务且会话有效。
 */
object WebSessionImport {

    private const val EAMSAPP_HOST = "eamsapp.uestc.edu.cn"

    fun validate(
        pageUrl: String?,
        storageToken: String?,
        cookies: List<StoredCookie>,
    ): String? {
        if (resolveSessionJwt(pageUrl, storageToken, cookies) != null) return null
        val url = pageUrl?.trim().orEmpty().lowercase()
        if (url.isEmpty() || !url.contains(EAMSAPP_HOST)) {
            return "请先在移动教务网页完成登录后再导入。"
        }
        return "当前页面没有有效的移动教务登录信息，请确认网页里已登录成功。"
    }

    /** 兼容仅传 cookies 的校验（已规范化列表）。 */
    fun validate(pageUrl: String?, cookies: List<StoredCookie>): String? =
        validate(pageUrl, storageToken = null, cookies = cookies)

    /**
     * 移动教务 JWT 来源优先级：页面 URL `?jsessionid=` → localStorage → Cookie 中的 JWT。
     */
    fun resolveSessionJwt(
        pageUrl: String?,
        storageToken: String?,
        cookies: List<StoredCookie>,
    ): String? {
        extractJwtFromPageUrl(pageUrl)?.let { return it }
        storageToken?.trim()?.takeIf { EamsAppCookie.looksLikeJwt(it) }?.let { return it }
        return extractEamsappJwt(cookies)
    }

    /** 从 `https://eamsapp.../?jsessionid=eyJ...` 取出 JWT（SPA 登录后常见）。 */
    fun extractJwtFromPageUrl(pageUrl: String?): String? {
        val raw = pageUrl?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) } ?: return null
        if (!raw.contains(EAMSAPP_HOST, ignoreCase = true)) return null
        return try {
            val httpUrl = raw.toHttpUrl()
            listOf("jsessionid", "JSESSIONID")
                .firstNotNullOfOrNull { key -> httpUrl.queryParameter(key) }
                ?.takeIf { EamsAppCookie.looksLikeJwt(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** 从快照中取出移动教务 JWT（仅 Cookie 列表）。 */
    fun extractEamsappJwt(cookies: List<StoredCookie>): String? {
        val onEamsapp = cookies.filter { it.domain.contains(EAMSAPP_HOST, ignoreCase = true) }
        onEamsapp
            .firstOrNull { it.name.equals("JSESSIONID", ignoreCase = true) && EamsAppCookie.looksLikeJwt(it.value) }
            ?.value
            ?.let { return it }
        return onEamsapp.firstOrNull { EamsAppCookie.looksLikeJwt(it.value) }?.value
    }

    fun buildNormalizedSession(
        pageUrl: String?,
        storageToken: String?,
        cookies: List<StoredCookie>,
    ): List<StoredCookie> {
        val jwt = resolveSessionJwt(pageUrl, storageToken, cookies) ?: return cookies
        val rest =
            cookies.filterNot {
                it.domain.contains(EAMSAPP_HOST, ignoreCase = true) &&
                    it.name.equals("JSESSIONID", ignoreCase = true)
            }
        val jsession =
            StoredCookie(
                name = "JSESSIONID",
                value = jwt,
                domain = EAMSAPP_HOST,
                path = "/",
                expiresAtMillis = null,
                secure = true,
                httpOnly = true,
            )
        return rest + jsession
    }

    /** @deprecated 使用 [buildNormalizedSession] */
    fun normalizeForOkHttp(cookies: List<StoredCookie>): List<StoredCookie> =
        buildNormalizedSession(pageUrl = null, storageToken = null, cookies = cookies)

    fun jwtFromPageUrl(pageUrl: String?): StoredCookie? =
        extractJwtFromPageUrl(pageUrl)?.let { jwt ->
            StoredCookie(
                name = "JSESSIONID",
                value = jwt,
                domain = EAMSAPP_HOST,
                path = "/",
                expiresAtMillis = null,
                secure = true,
                httpOnly = false,
            )
        }

    fun jwtFromWebStorageToken(raw: String?): StoredCookie? {
        val jwt = raw?.trim().orEmpty()
        if (!EamsAppCookie.looksLikeJwt(jwt)) return null
        return StoredCookie(
            name = "JSESSIONID",
            value = jwt,
            domain = EAMSAPP_HOST,
            path = "/",
            expiresAtMillis = null,
            secure = true,
            httpOnly = false,
        )
    }
}
