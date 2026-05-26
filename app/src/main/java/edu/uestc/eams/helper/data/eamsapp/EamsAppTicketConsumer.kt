package edu.uestc.eams.helper.data.eamsapp

import edu.uestc.eams.helper.data.network.ApiConstants
import edu.uestc.eams.helper.data.network.InMemoryCookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import kotlin.text.Charsets

/**
 * CAS 票据换取移动教务会话：按步骤跟随跳转，将短会话升级为 JWT 并写入 Cookie。
 * 不自动跟随重定向，以便逐步解析 Location 与 Set-Cookie。
 */
class EamsAppTicketConsumer(
    private val client: OkHttpClient,
    private val jar: InMemoryCookieJar,
) {

    private val noRedirectClient: OkHttpClient =
        client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    private val idasRefererDefault = "https://idas.uestc.edu.cn/"

    /** CAS 登录 API：`redirectUrl` 为站点根地址，勿二次 URL 编码。 */
    private fun casLoginWithoutTicketUrl(): String =
        "${ApiConstants.EAMSAPP_CAS_LOGIN_API}?redirectUrl=${ApiConstants.EAMSAPP_ORIGIN}"

    /**
     * 从带 ticket 参数的地址起完成换票；成功时返回 JWT 形会话标识。
     */
    fun establishSessionFromTicketUrl(
        ticketUrl: String,
        idasReferer: String = idasRefererDefault,
    ): String? {
        var url = ticketUrl.trim()
        var hdrReferer = idasReferer
        var jwt = ""
        var landing = ""

        var stopTicketHops = false
        for (hop in 0 until 12) {
            if (stopTicketHops) break
            EamsAppJwtTrace.line("ticketHop#$hop GET ${url.take(200)}")
            noRedirectClient.newCall(
                Request.Builder()
                    .url(url)
                    .applyEamsAppNavigateHeaders(referer = hdrReferer)
                    .get()
                    .build(),
            ).execute().use { rsp ->
                val loc = (rsp.header("Location") ?: "").trim()
                val abs =
                    if (loc.isNotEmpty()) {
                        rsp.request.url.resolve(loc)?.toString()?.trim().orEmpty()
                    } else {
                        ""
                    }
                EamsAppJwtTrace.line(
                    "ticketHop#$hop HTTP ${rsp.code} loc=${abs.take(160).ifBlank { "—" }}",
                )

                if (abs.isNotEmpty()) {
                    parseLandingQuery(abs)?.let { (j, uid, roles) ->
                        if (EamsAppCookie.looksLikeJwt(j)) jwt = j
                        if (uid.isNotEmpty() || roles.isNotEmpty() ||
                            "jsessionid" in abs.lowercase(Locale.ROOT)
                        ) {
                            landing = abs
                        }
                    }
                }

                when {
                    jwt.isNotEmpty() && landing.isNotEmpty() -> {
                        EamsAppJwtTrace.line("ticketHop#$hop 已解析 JWT len=${jwt.length}，停止跟跳")
                        stopTicketHops = true
                    }
                    rsp.code in 301..308 && abs.isNotEmpty() -> {
                        hdrReferer = rsp.request.url.toString()
                        url = abs
                    }
                    rsp.code !in 200..399 -> {
                        EamsAppJwtTrace.line("ticketHop#$hop 非预期 HTTP ${rsp.code}，中止")
                        stopTicketHops = true
                    }
                }
            }
        }

        if (jwt.isNotEmpty()) {
            EamsAppCookie.storeJwtInJar(jar, jwt)
            val land = landing.ifBlank { "${ApiConstants.EAMSAPP_ORIGIN}/?jsessionid=$jwt" }
            EamsAppJwtTrace.line("ticket 落地 GET ${land.take(160)}")
            noRedirectClient.newCall(
                Request.Builder()
                    .url(land)
                    .applyEamsAppNavigateHeaders(referer = hdrReferer)
                    .get()
                    .build(),
            ).execute().use { landRsp ->
                EamsAppJwtTrace.line("ticket 落地 HTTP ${landRsp.code}")
            }
            return EamsAppCookie.pickJwtFromJar(jar) ?: jwt
        }

        EamsAppJwtTrace.line("ticket 链未解析到 JWT，尝试 promoteShortSessionToJwt")
        return promoteShortSessionToJwt()
    }

    /**
     * 二次认证后 `GET login?service=…`，手动跟 302 直到出现 ticket URL，再 [establishSessionFromTicketUrl]。
     */
    fun establishSessionAfterIdasLogin(
        loginUrlWithService: String,
        referer: String,
    ): String? {
        var url = loginUrlWithService.trim()
        var ref = referer
        idasHop@ repeat(20) { hop ->
            EamsAppJwtTrace.line("idasLoginHop#$hop GET ${url.take(200)}")
            noRedirectClient.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", ApiConstants.CLIENT_USER_AGENT_IDAS)
                    .header("Referer", ref)
                    .get()
                    .build(),
            ).execute().use { rsp ->
                val loc = (rsp.header("Location") ?: "").trim()
                val abs =
                    if (loc.isNotEmpty()) {
                        rsp.request.url.resolve(loc)?.toString()?.trim().orEmpty()
                    } else {
                        ""
                    }
                EamsAppJwtTrace.line(
                    "idasLoginHop#$hop HTTP ${rsp.code} loc=${abs.take(160).ifBlank { "—" }}",
                )
                val low = abs.lowercase(Locale.ROOT)
                if ("ticket=" in low && "eamsapp" in low && "cas-login" in low) {
                    EamsAppJwtTrace.line("idasLoginHop#$hop 命中 ticket URL，进入 eamsapp 换票链")
                    return establishSessionFromTicketUrl(abs, idasReferer = ref)
                }
                if (rsp.code in 301..308 && abs.isNotEmpty()) {
                    url = abs
                    ref = rsp.request.url.toString()
                    return@use
                }
            }
        }
        EamsAppJwtTrace.line("idas login 20 跳内无 ticket，pickJwt 或 promote")
        return EamsAppCookie.pickJwtFromJar(jar) ?: promoteShortSessionToJwt()
    }

    fun findTicketUrl(): String? {
        val serviceQ = URLEncoder.encode(ApiConstants.EAMSAPP_CAS_SERVICE, Charsets.UTF_8.name())
        var url = "${ApiConstants.CAS_BASE_URL}/login?service=$serviceQ"
        var referer = ApiConstants.CAS_BASE_URL + "/login"
        repeat(20) {
            noRedirectClient.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", ApiConstants.CLIENT_USER_AGENT_IDAS)
                    .header("Referer", referer)
                    .get()
                    .build(),
            ).execute().use { rsp ->
                val loc = rsp.header("Location")?.trim().orEmpty()
                if (loc.isNotEmpty()) {
                    val abs = rsp.request.url.resolve(loc)?.toString().orEmpty()
                    val low = abs.lowercase(Locale.ROOT)
                    if ("ticket=" in low && "eamsapp.uestc.edu.cn" in low && "cas-login" in low) {
                        return abs
                    }
                    if (rsp.code in 301..308) {
                        url = abs
                        referer = rsp.request.url.toString()
                        return@use
                    }
                }
                val final = rsp.request.url.toString()
                if ("ticket=" in final.lowercase(Locale.ROOT) && "eamsapp" in final.lowercase(Locale.ROOT)) {
                    return final
                }
            }
        }
        return null
    }

    /** 已有统一认证票据但尚无 JWT：继续换票或短会话升级。 */
    fun ensureJwtAfterCas(): String? {
        EamsAppCookie.pickJwtFromJar(jar)?.let {
            EamsAppJwtTrace.line("ensureJwt: Jar 已有 JWT len=${it.length}")
            return it
        }
        val ticket = findTicketUrl()
        if (ticket != null) {
            EamsAppJwtTrace.line("ensureJwt: findTicketUrl 命中 ${ticket.take(200)}")
            establishSessionFromTicketUrl(ticket)?.let { return it }
        } else {
            EamsAppJwtTrace.line("ensureJwt: findTicketUrl 未命中（CASTGC 可能已失效）")
        }
        return promoteShortSessionToJwt()
    }

    /**
     * 短会话已存在时，请求无 ticket 的 cas-login，从跳转地址解析 JWT。
     */
    private fun promoteShortSessionToJwt(): String? {
        val short =
            jar.snapshot()
                .filter { it.domain.contains("eamsapp.uestc.edu.cn", ignoreCase = true) }
                .firstOrNull { it.name.equals("JSESSIONID", ignoreCase = true) }
        EamsAppJwtTrace.line(
            "promoteShort: 短会话=${if (short != null) "有(len=${short.value.length})" else "无"}",
        )
        val url = casLoginWithoutTicketUrl()
        noRedirectClient.newCall(
            Request.Builder()
                .url(url)
                .applyEamsAppNavigateHeaders(referer = idasRefererDefault)
                .get()
                .build(),
        ).execute().use { rsp ->
            val loc = (rsp.header("Location") ?: "").trim()
            val abs =
                if (loc.isNotEmpty()) {
                    rsp.request.url.resolve(loc)?.toString()?.trim().orEmpty()
                } else {
                    ""
                }
            EamsAppJwtTrace.line("promoteShort HTTP ${rsp.code} loc=${abs.take(160).ifBlank { "—" }}")
            val jwt =
                parseLandingQuery(abs)?.first?.takeIf { EamsAppCookie.looksLikeJwt(it) }
                    ?: run {
                        EamsAppJwtTrace.line("promoteShort: Location 无 JWT 形 jsessionid")
                        return null
                    }
            EamsAppCookie.storeJwtInJar(jar, jwt)
            noRedirectClient.newCall(
                Request.Builder()
                    .url(abs)
                    .applyEamsAppNavigateHeaders(referer = url)
                    .get()
                    .build(),
            ).execute().close()
            return EamsAppCookie.pickJwtFromJar(jar) ?: jwt
        }
    }

    private fun parseLandingQuery(url: String): Triple<String, String, String>? {
        val http = url.toHttpUrlOrNull() ?: return null
        val j =
            http.queryParameter("jsessionid")
                ?: http.queryParameter("JSESSIONID")
                ?: ""
        if (!EamsAppCookie.looksLikeJwt(j)) {
            val m = Regex("[?&]jsessionid=([^&]+)", RegexOption.IGNORE_CASE).find(url)
            val fromRegex = m?.groupValues?.getOrNull(1).orEmpty()
            if (EamsAppCookie.looksLikeJwt(fromRegex)) {
                return Triple(
                    fromRegex,
                    http.queryParameter("userId").orEmpty(),
                    http.queryParameter("roles").orEmpty(),
                )
            }
            return null
        }
        return Triple(
            j,
            http.queryParameter("userId").orEmpty(),
            http.queryParameter("roles").orEmpty(),
        )
    }
}
