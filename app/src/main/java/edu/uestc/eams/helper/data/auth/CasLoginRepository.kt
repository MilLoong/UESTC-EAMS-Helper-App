package edu.uestc.eams.helper.data.auth

import edu.uestc.eams.helper.data.network.ApiConstants
import edu.uestc.eams.helper.data.network.GatewayTsShellHeuristic
import edu.uestc.eams.helper.data.network.InMemoryCookieJar
import edu.uestc.eams.helper.data.eamsapp.EamsAppApi
import edu.uestc.eams.helper.data.eamsapp.EamsAppCookie
import edu.uestc.eams.helper.data.eamsapp.EamsAppTicketConsumer
import edu.uestc.eams.helper.data.eamsapp.applyEamsAppNavigateHeaders
import edu.uestc.eams.helper.data.network.applyOnlineNavigateHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.security.SecureRandom
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.text.Charsets
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest

@Suppress("UNUSED_PARAMETER")
private fun traceCas(msg: String) {
    // 排障日志默认关闭；需要时可恢复 BuildConfig.DEBUG + Log.i。
}

private const val IDAS_ACCEPT_JSON_XHR = "application/json, text/javascript, */*; q=0.01"

private inline fun silentlyIgnore(block: () -> Unit) {
    try {
        block()
    } catch (_: Exception) {
    }
}

/** CAS 登录、短信二次认证与移动教务换票。 */
class CasLoginRepository(
    private val client: OkHttpClient,
    private val jar: InMemoryCookieJar,
) {

    private data class PendingReauthSms(
        val uid: String,
        val authTypeName: String,
        val reauthReferer: String,
    )

    @Volatile
    private var pendingReauthSms: PendingReauthSms? = null

    private val idasNoFollowClient: OkHttpClient by lazy {
        client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    class WafOrAntiBotException(message: String) : IOException(message)

    companion object {
        const val TS_WAF_NOTICE =
            "响应疑似网关拦截。请用右上角内置浏览器完成登录。"
        const val FALLBACK_ONLY_WEB =
            "可点 Web 打开移动教务网页登录，登录后 导入会话，再回主页点 顶栏刷新。"

        const val SMS_PROMPT_CODE_TIME_PREFIX = "SMS_CODE_TIME="
        const val SMS_PROMPT_MOBILE_PREFIX = "SMS_MOBILE="

        fun buildSmsUserPrompt(outcome: ReauthSmsSendOutcome): String {
            val lines = mutableListOf<String>()
            outcome.resendCooldownSec?.takeIf { it > 0 }?.let { sec ->
                lines += "$SMS_PROMPT_CODE_TIME_PREFIX$sec"
            }
            outcome.mobile?.trim()?.takeIf { it.isNotEmpty() }?.let { mob ->
                lines += "$SMS_PROMPT_MOBILE_PREFIX$mob"
            }
            val msg =
                when {
                    outcome.sent == true ->
                        formatSmsSentHint(outcome.mobile, outcome.userMessage)
                    outcome.userMessage.isNotBlank() -> outcome.userMessage.trim()
                    outcome.sent == false ->
                        "短信验证码发送失败，请查看上方提示或稍后点 重新发送验证码。"
                    else -> "请查收短信或点 重新发送验证码。"
                }
            lines += msg
            lines += "填写后点对话框中的 提交验证码。"
            return lines.joinToString("\n")
        }

        fun formatSmsSentHint(mobile: String?, serverMessage: String?): String {
            val mob = mobile?.trim().orEmpty()
            if (mob.isNotEmpty()) {
                return "已向 $mob 发送验证码，请查收短信。"
            }
            return serverMessage?.trim().orEmpty().ifBlank { "验证码已发送至手机，请查收短信。" }
        }

        private const val EXECUTION_MISSING = "未发现 execution。"
        private const val SALT_MISSING = "未发现 pwdEncryptSalt / pwdDefaultEncryptSalt。"

        private inline fun quietly(block: () -> Unit) {
            try {
                block()
            } catch (_: Exception) {
            }
        }

        /** 探测是否需要图形验证码。 */
        private fun CasLoginRepository.quietlyCheckNeedCaptcha(username: String) {
            try {
                val enc = URLEncoder.encode(username, Charsets.UTF_8.name())
                val u =
                    "${ApiConstants.CAS_BASE_URL}/checkNeedCaptcha.htl?username=$enc&_=${System.currentTimeMillis()}"
                client.newCall(Request.Builder().url(u).get().build()).execute().use { rsp ->
                    if (!rsp.isSuccessful) return
                    val compact = rsp.body?.string().orEmpty().replace(" ", "")
                    traceCas("checkNeedCaptcha: ${compact.take(80)}")
                    if ("\"isNeed\":true" in compact) {
                        throw IOException(
                            "需要验证码（checkNeedCaptcha）；当前 OkHttp 流程不支持。\n${FALLBACK_ONLY_WEB}",
                        )
                    }
                }
            } catch (e: IOException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** 重新发送二次认证短信验证码。 */
    suspend fun resendReauthDynamicCode(): Result<ReauthSmsSendOutcome> =
        withContext(Dispatchers.IO) {
            val ctx =
                pendingReauthSms
                    ?: return@withContext Result.failure(
                        IOException("当前无法重发验证码，请关闭弹窗后重新登录。"),
                    )
            warmReauthSessionIfNeeded(ctx.reauthReferer, "resend")
            runCatching { postReauthDynamicCode(ctx) }
        }

    suspend fun login(
        username: String,
        password: String,
        smsCallback: suspend (prompt: String) -> String?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {

                coroutineContext.ensureActive()
                quietly {
                    client.drain("${ApiConstants.CAS_ORIGIN}/authserver/systemTime")
                }

                val loginUrlStr = ApiConstants.casLoginUrlWithService()
                val loginRefererPlain = ApiConstants.casLoginRefererPlain()
                traceCas(
                    "===== login START user=${username.trim().take(4)}*** service=${ApiConstants.CAS_SERVICE_RAW}",
                )
                EamsAppCookie.clearEamsappHosts(jar)
                traceCas("login: 已清空移动教务域旧 Cookie")
                /* 进入登录页前需准备多因素浏览器指纹 Cookie。 */
                val bfpEarly = ensureMultifactorBfpCookieValue()
                primeIdasLocaleCookie()
                traceCas("pre-login cookies: bfp=${bfpEarly.take(8)}… locale=zh_CN")
                val loginPage = client.getBody(loginUrlStr)
                coroutineContext.ensureActive()
                    traceCas(
                        "GET loginPage len=${loginPage.length} has_ts=${loginPage.containsAntiBotTs()} " +
                            "mfa_like=${Forms.needsIdasSmsOrMultifactorReauth(loginPage, "")} " +
                            "smsPage=${Forms.smsPage(loginPage)} casForm=${Forms.casLoginFormInner(loginPage) != null}",
                    )

                val execution =
                    Forms.execution(loginPage)
                        ?: throw IOException(
                            EXECUTION_MISSING + "\n" + executionMissingDiag(loginUrlStr, loginPage),
                        )
                val saltInfo =
                    Forms.pwdSaltInfo(loginPage) ?: throw IOException(SALT_MISSING)
                val salt = saltInfo.salt
                coroutineContext.ensureActive()

                quietlyCheckNeedCaptcha(username.trim())

                val encPwd = CryptoHelper.encryptLoginPassword(password, salt)
                val cipherBytes =
                    android.util.Base64.decode(encPwd, android.util.Base64.NO_WRAP).size
                val pwdUtf8Len = password.encodeToByteArray().size
                val expectedB64Len = expectedAesPasswordBase64Length(pwdUtf8Len)
                if (cipherBytes != 80 || encPwd.length != expectedB64Len) {
                    throw IOException(
                        "密码加密长度异常（cipherBytes=$cipherBytes encLen=${encPwd.length}，" +
                            "应为 cipherBytes=80 encLen=$expectedB64Len）。请卸载后重装最新 APK。",
                    )
                }
                val postFieldsPreview =
                    Forms.credentialPostFields(loginPage, username.trim(), execution, encPwd)
                val hiddenKeys = postFieldsPreview.keys.sorted().joinToString(",")
                traceCas(
                    "login form hidden=[$hiddenKeys] execution=${execution.take(8)}… " +
                        "saltField=${saltInfo.fieldId} saltLen=${salt.length} saltPrefix=${salt.take(4)}*** " +
                        "graphicalCaptcha=${Forms.loginFormRequiresGraphicalCaptcha(loginPage)}",
                )
                traceCas(
                    "credential transmit: userLen=${username.length} userTrimLen=${username.trim().length} " +
                        "pwdLen=${password.length} pwdTrimLen=${password.trim().length} " +
                        "pwdEdgeSpace=${password != password.trim()} cipherBytes=$cipherBytes " +
                        "encLen=${encPwd.length} expectedB64Len=$expectedB64Len " +
                        "match106=${encPwd.length == expectedB64Len && cipherBytes == 80} " +
                        "encPrefix=${encPwd.take(16)}… pwdSha256=${sha256HexPrefix(password, 12)}",
                )
                primeIdasFingerprintBeforeCredentialPost(loginRefererPlain)

                // 密码 POST 不自动跟跳，避免 CASTGC 被后续响应覆盖。
                val credentialNoFollow =
                    client.newBuilder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .build()

                credentialNoFollow.newCall(
                    loginPost(
                        loginUrlStr = loginUrlStr,
                        loginRefererPlain = loginRefererPlain,
                        loginPageHtml = loginPage,
                        username = username.trim(),
                        execution = execution,
                        encPwd = encPwd,
                    ),
                ).execute().use { credentialRsp ->
                    logRedirectChainSummary("credentialPOST", credentialRsp)
                    logCredentialSetCookieHeaders(credentialRsp)
                    ingestCastgcFromCredentialResponse(credentialRsp)
                    val postedBody = credentialRsp.body?.string().orEmpty()
                    val posted = ReadBody(credentialRsp.code, postedBody)
                    val locationAbs =
                        credentialRsp.header("Location")?.let { loc ->
                            credentialRsp.request.url.resolve(loc)?.toString()?.trim()
                        }
                    val postFinalUrl =
                        (locationAbs ?: credentialRsp.request.url.toString().trim())
                            .substringBefore("#")
                    traceCas(
                        "credential bodyLen=${postedBody.length} leafHttp=${credentialRsp.code} " +
                            "effectiveHttp=${posted.code} LocationTrail=${locationAbs?.take(180) ?: "—"} " +
                            "finalUrlTrail=${postFinalUrl.take(180)}… " +
                            "mfa_like=${Forms.needsIdasSmsOrMultifactorReauth(postedBody, postFinalUrl)} smsPage=${Forms.smsPage(postedBody)} " +
                            "bodyHasLoginForm=${postedBody.lowercase().contains("casloginform")}",
                    )

                    // 以 CASTGC 判断登录成功，勿扫描整页 HTML 中的验证码文案。
                    if (!posted.httpStatusLikelyOk()) {
                        Forms.extractError(posted.body)?.takeIf(String::isNotBlank)?.let {
                            throw IOException("CAS：$it")
                        }
                        throw IOException("CAS POST 异常(HTTP ${posted.code})。$FALLBACK_ONLY_WEB")
                    }

                    logJarCookiesAfterCredentialPost(jar, posted.code)
                    logCastgcDetailSnapshot(jar, "after credential POST")

                    var smsLandingHtml = posted.body
                    var smsLandingUrl = postFinalUrl
                    when {
                        posted.code in 300..399 -> {
                            val loc =
                                locationAbs
                                    ?: throw IOException(
                                        "CAS 密码 POST 返回 ${posted.code} 但无跳转地址（正常应为 302 进入二次认证页）。",
                                    )
                            traceCas("credential ${posted.code} GET reAuthLoginView ${loc.take(200)}")
                            credentialNoFollow.newCall(
                                Request.Builder()
                                    .url(loc)
                                    .header("Referer", loginRefererPlain)
                                    .applyIdasNavigateGetHeaders()
                                    .get()
                                    .build(),
                            ).execute().use { reauthRsp ->
                                smsLandingHtml = reauthRsp.body?.string().orEmpty()
                                smsLandingUrl =
                                    reauthRsp.request.url.toString().trim().substringBefore("#")
                                traceCas(
                                    "reAuthLoginView GET ${reauthRsp.code} len=${smsLandingHtml.length} " +
                                        "CASTGC=${jar.snapshot().containsCastGc()}",
                                )
                            }
                            logCastgcDetailSnapshot(jar, "after reAuthLoginView GET")
                        }
                    }

                    if (!jar.snapshot().containsCastGc()) {
                        Forms.extractError(postedBody)?.takeIf { it.isNotBlank() }?.let { tip ->
                            traceCas("credential pageError: ${tip.take(240)}")
                        }
                        val diagnosis = Forms.diagnoseCredentialFailure(postedBody)
                        traceCas("credential diagnose: ${diagnosis.traceLabel()}")
                        throw credentialPostFailedIOException(
                            httpCode = posted.code,
                            body = postedBody,
                            usernameHint = username.trim().take(4),
                            diagnosis = diagnosis,
                        )
                    }

                    when {
                        jar.snapshot().containsCastGc() -> Unit
                        else -> {
                            /* 误判 mfa_like=false 时仍须进入 smsIfNeeded 自校正，勿此处 castgcMissing 直接抛错。*/
                            val embeddedTickets =
                                Forms.extractCasOnlineTicketCandidates(postedBody)
                            if (embeddedTickets.isNotEmpty()) {
                                traceCas(
                                    "CASTGC 仍未入账：正文含 ${embeddedTickets.size} 条 ticket，开始 GET 消费…",
                                )
                                for (ticketUrl in embeddedTickets) {
                                    consumeOneCasTicket(client, ticketUrl, loginUrlStr)
                                }
                                logJarCookiesAfterCredentialPost(jar, posted.code)
                                logCastgcDetailSnapshot(jar, "after embedded ticket consume")
                            }
                            /* 服务端明确登录错误仍可立即失败 */
                            if (!jar.snapshot().containsCastGc()) {
                                val tip = Forms.extractError(posted.body)?.trim().orEmpty()
                                if (tip.isNotEmpty()) {
                                    val graphHint =
                                        if (tip.contains("图形")) {
                                            "\n[提示] 文案含“图形”未必是验证码，可试右上角 WebView。"
                                        } else {
                                            ""
                                        }
                                    throw IOException("CAS：$tip$graphHint")
                                }
                            }
                        }
                    }

                    smsIfNeeded(
                        loginUrlStr = loginUrlStr,
                        loginUsername = username.trim(),
                        smsCallback = smsCallback,
                        credentialLandingHtml = smsLandingHtml,
                        credentialLandingUrl = smsLandingUrl,
                    )

                    if (!jar.snapshot().containsCastGc()) {
                        logCastgcDetailSnapshot(jar, "still missing CASTGC after smsIfNeeded")
                        throw IOException(castgcMissingExplain())
                    }
                    logCastgcDetailSnapshot(jar, "before consumeTicket")

                    // 仅消费本次 POST 的 Location 中的 ticket。
                    consumeCasTicketIfNeededAlongPriorChain(client, credentialRsp, loginUrlStr)
                    logJarCookiesAfterCredentialPost(jar, credentialRsp.code)
                }

                verifySessionAfterCas().also {
                    traceCas("verify post-CAS session OK")
                    traceCas("===== login END =====")
                }
            }
        }

    /** 探测一网通门户会话是否有效。 */
    suspend fun probeOnlinePortalSession(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { verifySessionAfterCas() }
        }

    /** 消费 CAS ticket，避免重复跟跳导致 INVALID_TICKET。 */
    private fun consumeCasTicketIfNeededAlongPriorChain(
        client: OkHttpClient,
        leaf: Response,
        casLoginUrl: String,
    ) {
        if (leaf.priorResponse != null) {
            traceCas(
                "consumeTicket: skip priorResponse replay (auto-redirect already consumed ticket chain) " +
                    "final=${leaf.request.url.toString().take(180)}",
            )
            return
        }
        leaf.header("Location")?.trim()?.takeIf { it.isNotEmpty() }?.let { loc ->
            val abs = leaf.request.url.resolve(loc)?.toString()?.trim().orEmpty()
            if (abs.contains("ticket=", ignoreCase = true)) {
                traceCas("consumeTicket: Location ticket ${abs.take(220)}")
                consumeOneCasTicket(client, abs, casLoginUrl)
            }
        }
    }

    private fun consumeOneCasTicket(
        client: OkHttpClient,
        url: String,
        casLoginUrl: String,
    ) {
        if (!url.contains("ticket=", ignoreCase = true)) return
        val host =
            url.toHttpUrlOrNull()?.host?.lowercase(Locale.ROOT).orEmpty()
        val schoolHost =
            host == "uestc.edu.cn" || host.endsWith(".uestc.edu.cn")
        if (!schoolHost) return
        val b = Request.Builder().url(url).get()
        if (host.contains("eamsapp.uestc.edu.cn")) {
            traceCas("consumeTicket: 移动教务换票 ${url.take(220)}")
            val jwt =
                EamsAppTicketConsumer(client, jar).establishSessionFromTicketUrl(
                    ticketUrl = url,
                    idasReferer = casLoginUrl,
                )
            traceCas(
                "consumeTicket: 换票结果 jwt=${if (jwt != null) "ok(len=${jwt.length})" else "失败"}",
            )
            if (jwt == null) {
                throw IOException(
                    "移动教务登录换票未完成，请用 Web 登录后 导入会话。",
                )
            }
            return
        }
        b.applyOnlineNavigateHeaders(referer = casLoginUrl)
        client.newCall(b.build()).execute().use { rsp ->
            traceCas(
                "consumeTicket rsp=${rsp.code} for=${url.take(220)}",
            )
        }
    }

    private fun verifySessionAfterCas() {
        if (ApiConstants.CAS_SERVICE_RAW.contains("eamsapp", ignoreCase = true)) {
            verifyEamsAppApiSession()
        } else {
            verifyOnlinePortalHome()
        }
    }

    private fun verifyEamsAppApiSession() {
        traceCas("verify: 开始 JWT 探针 …")
        val jwt =
            EamsAppCookie.pickJwtFromJar(jar)
                ?: EamsAppTicketConsumer(client, jar).ensureJwtAfterCas()
                ?: throw IOException(
                    "登录后未获得移动教务会话，请重新登录或使用 Web 导入会话。",
                )
        if (!EamsAppCookie.looksLikeJwt(jwt)) {
            throw IOException(
                "会话未完成换票（JSESSIONID 无效）。$FALLBACK_ONLY_WEB",
            )
        }
        val hdr = EamsAppCookie.composeFromJar(jar, jwt)
        val probe = EamsAppApi(client).probeSessionDetail(hdr)
        traceCas(
            "verify: probe HTTP=${probe.httpCode} enc=${probe.contentEncoding.ifBlank { "—" }} " +
                "ok=${probe.ok} authFail=${probe.authFailed} bodyLen=${probe.bodyPreview.length} " +
                "preview=${probe.bodyPreview.take(120)}",
        )
        if (!probe.ok) {
            val compressedHint =
                probe.contentEncoding.contains("zstd", ignoreCase = true) ||
                    probe.bodyPreview.any { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
            throw IOException(
                buildString {
                    append("移动教务 API 探针失败（HTTP ${probe.httpCode}）")
                    if (probe.authFailed) append("，会话已失效")
                    if (compressedHint) {
                        append("，响应似未解压（Content-Encoding=${probe.contentEncoding.ifBlank { "?" }}）")
                    } else {
                        val hint = probe.bodyPreview.take(80)
                        if (hint.isNotEmpty()) append("：$hint")
                    }
                    append("。")
                    append(FALLBACK_ONLY_WEB)
                },
            )
        }
        traceCas("verifyEamsAppApiSession OK jwtLen=${jwt.length}")
    }

    /** 校验一网通门户页是否仍保持登录。 */
    private fun verifyOnlinePortalHome() {
        val req =
            Request.Builder()
                .url(ApiConstants.ONLINE_PAGE_URL)
                .applyOnlineNavigateHeaders(referer = "${ApiConstants.ONLINE_ORIGIN.trimEnd('/')}/")
                .get()
                .build()
        client.newCall(req).execute().use { rsp ->
            val body = rsp.body?.string().orEmpty()
            traceCas(
                "probe ONLINE_PAGE HTTP=${rsp.code} bodyLen=${body.length} kicksCas=${bodyLooksKickedCas(body)} ts=${body.containsAntiBotTs()}",
            )
            if (rsp.code == 202 || body.containsAntiBotTs()) {
                throw WafOrAntiBotException(TS_WAF_NOTICE)
            }

            val isRedirect = rsp.code in 300..399
            if (!isRedirect && rsp.code >= 400) {
                throw IOException(
                    "一网通门户 HTTP ${rsp.code}。$FALLBACK_ONLY_WEB",
                )
            }

            val kickedCas = bodyLooksKickedCas(body)

            if (kickedCas) {
                throw IOException("一网通仍会跳转统一认证：$FALLBACK_ONLY_WEB")
            }
        }
    }

    private fun bodyLooksKickedCas(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("/authserver/login") &&
            (lower.contains("<form") || lower.contains("location"))
    }

    private suspend fun smsIfNeeded(
        loginUrlStr: String,
        loginUsername: String,
        smsCallback: suspend (prompt: String) -> String?,
        credentialLandingHtml: String? = null,
        credentialLandingUrl: String? = null,
    ) {
        val landUrl = credentialLandingUrl?.trim().orEmpty().substringBefore("#")
        val landHtml = credentialLandingHtml.orEmpty()

        val useCredentialLanding =
            landUrl.isNotBlank() &&
                Forms.needsIdasSmsOrMultifactorReauth(landHtml, landUrl)

        var pair =
            if (useCredentialLanding) {
                traceCas(
                    "smsIfNeeded: 使用 credential 着陆页 ${landUrl.take(240)}…",
                )
                landHtml to landUrl
            } else {
                client.getHtmlPair(loginUrlStr)
            }

        var html = pair.first

        Forms.skipHref(html)?.let {
            client.drain(it.toString())
            pair = client.getHtmlPair(loginUrlStr)
            html = pair.first
        }

        val pageUrlTrim = pair.second.trim().substringBefore("#")
        if (!Forms.needsIdasSmsOrMultifactorReauth(html, pageUrlTrim)) {
            traceCas("smsIfNeeded: skip，未命中 MFA 页面")
            return
        }

        traceCas("smsIfNeeded: 进入 MFA 链路（将弹短信口令框）pageUrlTrail=${pageUrlTrim.take(200)}…")

        completeIdasReauthLikePython(
            loginUrlStr = loginUrlStr,
            loginUsername = loginUsername,
            navigatedFinalUrl = pageUrlTrim,
            seedHtml = html,
            smsCallback = smsCallback,
        )

        val afterPair = client.getHtmlPair(loginUrlStr)
        val afterHtml = afterPair.first
        val afterUrl = afterPair.second.trim().substringBefore("#")
        coroutineContext.ensureActive()
        if (Forms.needsIdasSmsOrMultifactorReauth(afterHtml, afterUrl)) {
            throw IOException(
                "二次认证后仍停留在验证页。$FALLBACK_ONLY_WEB",
            )
        }
    }

    /** 执行二次认证完整流程。 */
    private suspend fun completeIdasReauthLikePython(
        loginUrlStr: String,
        loginUsername: String,
        navigatedFinalUrl: String,
        seedHtml: String,
        smsCallback: suspend (prompt: String) -> String?,
    ) {
        var serviceDecoded = resolveReAuthServiceDecoded(navigatedFinalUrl, seedHtml)

        fun loginFormReferrerForService(svc: String): String =
            "${ApiConstants.CAS_BASE_URL}/login?service=${normalizeEamsappCasService(svc)}"

        var loginFormReferrer = loginFormReferrerForService(serviceDecoded)
        var reauthReferer = resolveReauthReferer(navigatedFinalUrl, serviceDecoded, loginUrlStr)

        silentlyIgnore {
            idasNoFollowClient.newCall(
                Request.Builder()
                    .url("${ApiConstants.CAS_BASE_URL}/tenant/info")
                    .header("Referer", reauthReferer)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept", IDAS_ACCEPT_JSON_XHR)
                    .get()
                    .build(),
            ).execute().close()
        }

        val fingerprint = ensureMultifactorBfpCookieValue()

        silentlyIgnore {
            val bfpReq =
                "${ApiConstants.CAS_BASE_URL}/bfp/info?" +
                    "bfp=${URLEncoder.encode(fingerprint, Charsets.UTF_8.name())}" +
                    "&_=${System.currentTimeMillis()}"
            idasNoFollowClient.newCall(
                Request.Builder()
                    .url(bfpReq)
                    .header("Referer", reauthReferer)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept", IDAS_ACCEPT_JSON_XHR)
                    .get()
                    .build(),
            ).execute().close()
        }

        var pageHtml = seedHtml
        silentlyIgnore {
            client.newCall(
                Request.Builder()
                    .url(reauthReferer)
                    .header("Referer", loginFormReferrer)
                    .get()
                    .build(),
            ).execute().use { rsp ->
                val t = rsp.body?.string().orEmpty()
                if (rsp.code in 200..299 && t.isNotBlank()) {
                    pageHtml = t
                }
            }
        }

        parseReAuthInlineService(pageHtml)?.let { embedded ->
            val fixed = embedded.replace("\\/", "/")
            if (fixed.startsWith("http", ignoreCase = true)) {
                serviceDecoded = fixed
                loginFormReferrer = loginFormReferrerForService(serviceDecoded)
                reauthReferer = resolveReauthReferer(reauthReferer, serviceDecoded, loginUrlStr)
            }
        }

        warmReauthSessionIfNeeded(reauthReferer, "reauth-before-sms")

        val htmlBundle = pageHtml + "\n" + seedHtml
        val uid =
            parseReAuthUserId(htmlBundle)
                .ifBlank { loginUsername.trim() }
        val rt = parseReAuthType(htmlBundle).ifBlank { "3" }
        val authTypeName = mapReAuthToAuthCodeTypeName(rt)

        val smsCtx =
            if (uid.isNotBlank() && authTypeName != null) {
                PendingReauthSms(uid, authTypeName, reauthReferer)
            } else {
                traceCas(
                    "reauth: skip auto sms (uid=${uid.take(6)} loginUser=${loginUsername.take(6)} reAuthType=$rt)",
                )
                null
            }

        val smsOutcome: ReauthSmsSendOutcome =
            if (smsCtx != null) {
                pendingReauthSms = smsCtx
                val outcome = postReauthDynamicCode(smsCtx)
                traceCas(
                    "reauth: 发码 uid=${uid.take(6)}… res=${outcome.sent} mobile=${outcome.mobile ?: "—"} " +
                        "cooldown=${outcome.resendCooldownSec ?: "—"} msg=${outcome.userMessage.take(80)}",
                )
                outcome
            } else {
                ReauthSmsSendOutcome(
                    userMessage = "未能自动请求发短信，请点 重新发送验证码 或填写已有验证码。",
                )
            }

        coroutineContext.ensureActive()
        val dynamicCode =
            try {
                smsCallback(
                    buildSmsUserPrompt(smsOutcome),
                ) ?: throw IOException(FALLBACK_ONLY_WEB)
            } finally {
                pendingReauthSms = null
            }

        silentlyIgnore {
            val emptyBody = byteArrayOf().toRequestBody(null)
            idasNoFollowClient.newCall(
                Request.Builder()
                    .url("${ApiConstants.CAS_BASE_URL}/systemTime")
                    .header("Referer", reauthReferer)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept", "*/*")
                    .post(emptyBody)
                    .build(),
            ).execute().close()
        }

        val uuid = parseUuidFromReauth(pageHtml)
        val isSleep = parseIsSleepAccount(pageHtml)

        val submitBody =
            FormBody.Builder(Charsets.UTF_8).apply {
                add("service", serviceDecoded)
                add("reAuthType", rt)
                add("isMultifactor", "true")
                add("password", "")
                add("dynamicCode", dynamicCode.trim())
                add("uuid", uuid)
                add("answer1", "")
                add("answer2", "")
                add("otpCode", "")
                if (isSleep == "0") {
                    add("skipTmpReAuth", "false")
                }
            }.build()

        idasNoFollowClient.newCall(
            Request.Builder()
                .url("${ApiConstants.CAS_BASE_URL}/reAuthCheck/reAuthSubmit.do")
                .header("Referer", reauthReferer)
                .header("Origin", ApiConstants.CAS_ORIGIN)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", IDAS_ACCEPT_JSON_XHR)
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .post(submitBody)
                .build(),
        ).execute().use { rsp ->
            val raw = rsp.body?.string().orEmpty()
            if (rsp.code in 300..399) {
                val loc = rsp.header("Location").orEmpty()
                logRedirectChainSummary("reAuthSubmit", rsp)
                throw IOException(
                    "二次认证提交被重定向（HTTP ${rsp.code}）${loc.take(120)}。请重新登录。$FALLBACK_ONLY_WEB",
                )
            }
            if (rsp.code != 200) {
                throw IOException("二次认证提交 HTTP ${rsp.code} ${raw.take(220)} …$FALLBACK_ONLY_WEB")
            }
            if (raw.trimStart().startsWith("<")) {
                throw IOException("二次认证提交返回网页而非 JSON，会话可能已失效。$FALLBACK_ONLY_WEB")
            }
            summarizeReAuthJsonFailure(raw)?.let { msg -> throw IOException("$msg $FALLBACK_ONLY_WEB") }
        }

        val jump =
            "${ApiConstants.CAS_BASE_URL}/login?service=${URLEncoder.encode(serviceDecoded, Charsets.UTF_8.name())}"
        EamsAppCookie.clearEamsappHosts(jar)
        traceCas("reauth: 已清空移动教务 Cookie，开始换票")
        traceCas("reauth: 统一认证换票链")
        val jwtAfterReauth =
            EamsAppTicketConsumer(client, jar).establishSessionAfterIdasLogin(
                loginUrlWithService = jump,
                referer = reauthReferer,
            )
        val jwtOk = jwtAfterReauth != null && EamsAppCookie.looksLikeJwt(jwtAfterReauth)
        traceCas(
            "reauth: 移动教务 JWT ${if (jwtOk) "已取得 len=${jwtAfterReauth!!.length}" else "未取得（verify 将 ensureJwt 重试）"}",
        )
    }

    /** 提交浏览器指纹。 */
    private fun primeIdasFingerprintBeforeCredentialPost(loginRefererPlain: String) {
        val fp = ensureMultifactorBfpCookieValue()
        silentlyIgnore {
            val bfpReq =
                "${ApiConstants.CAS_BASE_URL}/bfp/info?" +
                    "bfp=${URLEncoder.encode(fp, Charsets.UTF_8.name())}" +
                    "&_=${System.currentTimeMillis()}"
            client.newCall(
                Request.Builder()
                    .url(bfpReq)
                    .header("Referer", loginRefererPlain)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept", IDAS_ACCEPT_JSON_XHR)
                    .get()
                    .build(),
            ).execute().close()
        }
        traceCas("bfp/info before credential POST fp=${fp.take(8)}…")
    }

    /** 从密码 POST 响应恢复 CASTGC。 */
    private fun ingestCastgcFromCredentialResponse(rsp: Response) {
        var tgt: String? = null
        for (h in rsp.headers("Set-Cookie")) {
            val line = h.trim()
            if (!line.startsWith("CASTGC=", ignoreCase = true)) continue
            val v = line.substringAfter("=").substringBefore(";").trim()
            if (v.isNotEmpty()) tgt = v
        }
        if (tgt.isNullOrBlank()) return
        val url = rsp.request.url
        val cookie =
            Cookie.Builder()
                .name("CASTGC")
                .value(tgt)
                .domain("idas.uestc.edu.cn")
                .path("/authserver")
                .httpOnly()
                .secure()
                .build()
        jar.saveFromResponse(url, listOf(cookie))
        traceCas("credential: 从 Set-Cookie 恢复 CASTGC（避免 Max-Age=0 覆盖 TGT）len=${tgt.length}")
    }

    private fun logCredentialSetCookieHeaders(rsp: Response) {
        rsp.headers("Set-Cookie").forEach { raw ->
            val brief =
                raw.trim().let { line ->
                    if (line.startsWith("CASTGC=", ignoreCase = true)) {
                        val v = line.substringAfter("=").substringBefore(";")
                        "CASTGC len=${v.length} empty=${v.isEmpty()}"
                    } else {
                        line.substringBefore(";").take(60)
                    }
                }
            traceCas("credential Set-Cookie: $brief")
        }
    }

    private fun warmReauthSessionIfNeeded(reauthReferer: String, label: String) {
        if (jar.snapshot().containsCastGc()) return
        traceCas("$label: Jar 无 CASTGC，GET 预热二次认证页 … referer=${reauthReferer.take(120)}")
        silentlyIgnore {
            idasNoFollowClient.newCall(
                Request.Builder()
                    .url(reauthReferer)
                    .header("Referer", reauthReferer)
                    .get()
                    .build(),
            ).execute().use { rsp ->
                traceCas("$label warm HTTP ${rsp.code} loc=${rsp.header("Location")?.take(80) ?: "—"}")
            }
        }
        logCastgcDetailSnapshot(jar, "$label after warm")
    }

    private fun postReauthDynamicCode(ctx: PendingReauthSms): ReauthSmsSendOutcome {
        val t0 = System.nanoTime()
        val hasCastgc = jar.snapshot().containsCastGc()
        traceCas(
            "getDynamicCode START uid=${ctx.uid.take(6)}… auth=${ctx.authTypeName} " +
                "CASTGC=$hasCastgc referer=${ctx.reauthReferer.take(100)}",
        )
        try {
            idasNoFollowClient.newCall(
                Request.Builder()
                    .url("${ApiConstants.CAS_BASE_URL}/dynamicCode/getDynamicCodeByReauth.do")
                    .header("Referer", ctx.reauthReferer)
                    .header("Origin", ApiConstants.CAS_ORIGIN)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept", IDAS_ACCEPT_JSON_XHR)
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .post(
                        FormBody.Builder(Charsets.UTF_8)
                            .add("userName", ctx.uid)
                            .add("authCodeTypeName", ctx.authTypeName)
                            .build(),
                    )
                    .build(),
            ).execute().use { rsp ->
                val raw = rsp.body?.string().orEmpty()
                val elapsedMs = (System.nanoTime() - t0) / 1_000_000
                traceCas(
                    "getDynamicCode END ${elapsedMs}ms HTTP ${rsp.code} user=${ctx.uid.take(6)}… " +
                        "redirect=${rsp.code in 300..399} loc=${rsp.header("Location")?.take(80) ?: "—"} " +
                        "body=${raw.take(160)}",
                )
                idasXhrFailureOutcome(rsp, raw, "发送验证码")?.let { return it }
                return parseReauthDynamicCodeResponse(raw)
            }
        } catch (e: java.net.SocketTimeoutException) {
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000
            traceCas("getDynamicCode TIMEOUT ${elapsedMs}ms (SocketTimeout) CASTGC=$hasCastgc")
            return ReauthSmsSendOutcome(
                sent = false,
                userMessage =
                    "发送验证码超时（${elapsedMs}ms）。请换校园网/流量重试，或使用右上角 Web 登录。",
            )
        } catch (e: IOException) {
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000
            val hint = e.message?.trim().orEmpty()
            val timeoutLike =
                hint.contains("timeout", ignoreCase = true) ||
                    hint.contains("timed out", ignoreCase = true)
            traceCas("getDynamicCode FAIL ${elapsedMs}ms: $hint")
            return ReauthSmsSendOutcome(
                sent = false,
                userMessage =
                    when {
                        timeoutLike ->
                            "发送验证码超时（${elapsedMs}ms）：$hint。可改用右上角 Web 登录。"
                        hint.isNotEmpty() -> hint
                        else -> "发送验证码网络错误（${elapsedMs}ms）"
                    },
            )
        }
    }

    /** 处理 Idas XHR，禁止自动跟跳。 */
    private fun idasXhrFailureOutcome(
        rsp: Response,
        raw: String,
        label: String,
    ): ReauthSmsSendOutcome? {
        if (rsp.code in 300..399) {
            val loc = rsp.header("Location").orEmpty()
            logRedirectChainSummary("getDynamicCodeXHR", rsp)
            return ReauthSmsSendOutcome(
                sent = false,
                userMessage =
                    buildString {
                        append("$label 被重定向（HTTP ${rsp.code}）")
                        if (loc.isNotBlank()) append(" loc=${loc.take(100)}")
                        append("。二次认证会话可能已失效，请关闭登录框后重新登录。")
                    },
            )
        }
        if (rsp.code != 200) {
            return ReauthSmsSendOutcome(
                sent = false,
                userMessage = "$label 失败（HTTP ${rsp.code}）${raw.take(120).trim()}",
            )
        }
        val trimmed = raw.trim()
        if (trimmed.startsWith("<") || trimmed.contains("<html", ignoreCase = true)) {
            return ReauthSmsSendOutcome(
                sent = false,
                userMessage = "$label 返回了网页而非 JSON，会话可能已失效，请重新登录。",
            )
        }
        return null
    }

    /** 构建二次认证 Referer。 */
    private fun resolveReauthReferer(
        navigatedFinalUrl: String,
        serviceDecoded: String,
        @Suppress("UNUSED_PARAMETER") loginUrlStr: String,
    ): String {
        val nav = navigatedFinalUrl.trim().substringBefore("#")
        val low = nav.lowercase(Locale.ROOT)
        if (low.contains("reauthcheck") || low.contains("reauthloginview") || low.contains("ismultifactor")) {
            return nav
        }
        val svc = URLEncoder.encode(serviceDecoded, Charsets.UTF_8.name())
        return "${ApiConstants.CAS_BASE_URL}/reAuthCheck/reAuthLoginView.do?isMultifactor=true&service=$svc"
    }

    /** 解析发码接口响应。 */
    private fun parseReauthDynamicCodeResponse(raw: String): ReauthSmsSendOutcome {
        val trimmed = raw.trim()
        traceCas(
            "getDynamicCode bodyLen=${trimmed.length} jsonLike=${trimmed.contains("\"res\"")}",
        )
        val o =
            extractJSONObject(trimmed)
                ?: return ReauthSmsSendOutcome(
                    sent = false,
                    userMessage = "发码响应无法解析为 JSON：${trimmed.take(120)}",
                )
        if (o.has("success") && !o.optBoolean("success", true)) {
            val errCode = o.optString("errCode").trim()
            val msg = o.optString("message").trim().ifBlank { "发码失败" }
            val data = o.optString("data").trim()
            val hint =
                when (errCode) {
                    "206302" ->
                        "$msg（会话未建立：请重新打开登录或改用 Web 登录）"
                    else -> msg
                }
            return ReauthSmsSendOutcome(
                sent = false,
                userMessage =
                    buildString {
                        if (errCode.isNotEmpty()) append("[$errCode] ")
                        append(hint)
                        if (data.isNotEmpty()) append(" data=$data")
                    },
            )
        }
        val res = o.optString("res").trim()
        var msg =
            o.optString("returnMessage").trim()
                .ifBlank { o.optString("message").trim() }
        val mob =
            mobileFromReauthPayload(o)
                ?: parseMaskedMobileFromText(trimmed)
                ?: parseMaskedMobileFromText(msg)
        val codeTimeField = o.optInt("codeTime", -1).takeIf { it > 0 }
        val cooldown = codeTimeField ?: parseWaitSecondsFromMessage(msg)

        when (res) {
            "success", "wechat_success", "cpdaily_success" -> {
                return ReauthSmsSendOutcome(
                    mobile = mob,
                    sent = true,
                    userMessage = formatSmsSentHint(mob, msg),
                    resendCooldownSec = cooldown,
                )
            }
            "code_time_fail" -> {
                val display =
                    msg.ifBlank { "发送过于频繁，请稍后再试。" }
                return ReauthSmsSendOutcome(
                    mobile = mob,
                    sent = false,
                    userMessage = display,
                    resendCooldownSec = cooldown,
                )
            }
            else -> {
                if (res.isNotEmpty() || msg.isNotEmpty()) {
                    val piece =
                        listOfNotNull(res.takeIf { it.isNotEmpty() }, msg.takeIf { it.isNotEmpty() })
                            .joinToString(" ")
                    return ReauthSmsSendOutcome(
                        mobile = mob,
                        sent = false,
                        userMessage = piece,
                        resendCooldownSec = cooldown,
                    )
                }
                return ReauthSmsSendOutcome(mobile = mob, sent = null, userMessage = msg)
            }
        }
    }

    private fun mobileFromReauthPayload(o: JSONObject): String? {
        o.optString("mobile").takeIf { it.isNotBlank() }?.let { return it.trim() }
        val data = o.optJSONObject("data") ?: return null
        return data.optString("mobile").takeIf { it.isNotBlank() }?.trim()
    }

    private fun parseWaitSecondsFromMessage(msg: String): Int? {
        if (msg.isBlank()) return null
        Regex("(\\d+)\\s*秒").find(msg)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { sec ->
            if (sec in 1..600) return sec
        }
        return null
    }

    private fun extractJSONObject(raw: String): JSONObject? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        try {
            if (t.startsWith("{")) return JSONObject(t)
        } catch (_: Exception) {
        }
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start >= 0 && end > start) {
            try {
                return JSONObject(t.substring(start, end + 1))
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** 解析脱敏手机号。 */
    private fun parseMaskedMobileFromText(text: String): String? {
        if (text.isBlank()) return null
        Regex(""""mobile"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.contains('*') && it.length >= 7 }
            ?.let { return it }
        Regex("""1[3-9]\d\*{2,6}\d{2,4}""")
            .find(text)
            ?.value
            ?.let { return it }
        return null
    }

    private fun primeIdasLocaleCookie() {
        val url = ApiConstants.casLoginUrlWithService().toHttpUrl()
        val c =
            Cookie.Builder()
                .name("org.springframework.web.servlet.i18n.CookieLocaleResolver.LOCALE")
                .value("zh_CN")
                .domain("idas.uestc.edu.cn")
                .path("/")
                .build()
        jar.saveFromResponse(url, listOf(c))
    }

    private fun ensureMultifactorBfpCookieValue(): String {
        jar.snapshot()
            .firstOrNull {
                it.name == "MULTIFACTOR_BROWSER_FINGERPRINT" &&
                    it.domain.contains("idas", ignoreCase = true)
            }?.value
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        val rnd = SecureRandom()
        val fb =
            ByteArray(16).also { rnd.nextBytes(it) }.joinToString("") { octet ->
                "%02X".format(octet)
            }
        val url = ApiConstants.casLoginUrlWithService().toHttpUrl()
        val c =
            Cookie.Builder()
                .name("MULTIFACTOR_BROWSER_FINGERPRINT")
                .value(fb)
                .domain("idas.uestc.edu.cn")
                .path("/")
                .secure()
                .build()
        jar.saveFromResponse(url, listOf(c))
        return fb
    }

    private fun resolveReAuthServiceDecoded(finalNavUrl: String, html: String): String =
        decodeCasServiceParam(finalNavUrl, html)

    /** 解析 CAS service URL。 */
    private fun decodeCasServiceParam(finalNavUrl: String, html: String): String {
        parseReAuthInlineService(html)?.replace("\\/", "/")?.takeIf { it.startsWith("http", ignoreCase = true) }?.let {
            return normalizeEamsappCasService(it)
        }
        finalNavUrl.toHttpUrlOrNull()?.queryParameter("service")?.takeIf(String::isNotBlank)?.let {
            return normalizeEamsappCasService(it)
        }
        return ApiConstants.CAS_SERVICE_RAW
    }

    private fun normalizeEamsappCasService(raw: String): String {
        var s = raw.trim().replace("\\/", "/")
        repeat(3) { _ ->
            val dec = URLDecoder.decode(s, Charsets.UTF_8.name())
            if (dec == s) return@repeat
            s = dec
        }
        if (s.contains("%253A", ignoreCase = true) || s.contains("%252F", ignoreCase = true)) {
            traceCas("WARN service 仍含双层编码，回退 CAS_SERVICE_RAW")
            return ApiConstants.CAS_SERVICE_RAW
        }
        return s.ifBlank { ApiConstants.CAS_SERVICE_RAW }
    }

    private fun parseReAuthInlineService(html: String): String? {
        val m = Regex(""""service"\s*:\s*"([^"]+)"""").find(html) ?: return null
        return m.groupValues.getOrNull(1)?.trim()?.takeUnless(String::isBlank)
    }

    private fun parseReAuthUserId(html: String): String {
        val patterns =
            listOf(
                Regex(""""reAuthUserId"\s*:\s*"([^"]*)"""", RegexOption.IGNORE_CASE),
                Regex("""reAuthUserId\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
                Regex("""name=["']reAuthUserId["'][^>]*value=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex("""value=["']([^"']+)["'][^>]*name=["']reAuthUserId["']""", RegexOption.IGNORE_CASE),
            )
        for (rx in patterns) {
            rx.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                return it
            }
        }
        return ""
    }

    private fun parseReAuthType(html: String): String =
        Regex(""""reAuthType"\s*:\s*"([^"]*)"""").find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()

    private fun parseIsSleepAccount(html: String): String =
        Regex(""""isSleepAccount"\s*:\s*"([^"]*)"""").find(html)?.groupValues?.getOrNull(1)?.trim()
            ?: "1"

    private fun parseUuidFromReauth(html: String): String {
        Regex(
            """name\s*=\s*["']uuid["'][^>]*value\s*=\s*["']([^"']*)["']""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.getOrNull(1)?.let {
            return it.trim()
        }
        return ""
    }

    private fun mapReAuthToAuthCodeTypeName(reAuthType: String): String? =
        when (reAuthType.trim()) {
            "3" -> "reAuthDynamicCodeType"
            "4" -> "reAuthWChatDynamicCodeType"
            "5" -> "reAuthCpdailyDynamicCodeType"
            "11" -> "reAuthEmailDynamicCodeType"
            "12" -> "reAuthDingTalkDynamicCodeType"
            "13" -> "reAuthWeLinkDynamicCodeType"
            else -> null
        }

    private fun summarizeReAuthJsonFailure(raw: String): String? {
        if (raw.isBlank()) return null
        return try {
            val o = JSONObject(raw)
            val code = o.optString("code", "")
            when (code) {
                "reAuth_failed", "reAuth_unauthorized" ->
                    o.optString("msg", o.optString("message", "")).ifBlank {
                        "二次认证未通过"
                    }

                else ->
                    when {
                        o.has("success") && !o.getBoolean("success") ->
                            o.optString("message", o.optString("msg", "")).ifBlank {
                                "二次认证未通过"
                            }

                        else -> null
                    }
            }
        } catch (_: Exception) {
            val cleaned = raw.replace(" ", "").lowercase()
            when {
                "reauth_failed" in cleaned || "reauth_unauthorized" in cleaned -> "二次认证失败（正文非 JSON）"
                "\"success\":false" in cleaned || "success:false" in cleaned -> "二次认证失败（正文片段）"
                else -> null
            }
        }
    }
}

private data class ReadBody(
    val code: Int,
    val body: String,
)

private fun ReadBody.httpStatusLikelyOk(): Boolean {
    if (code == 401 || code == 403) return false
    // 302/303 等与登录成功也常出现；≥500 / 418 等为失败态
    if (code < 200 || code >= 500) return false
    return true
}

private fun Iterable<Cookie>.containsCastGc(): Boolean =
    any {
        it.name == "CASTGC" && it.domain.contains("uestc.edu.cn", ignoreCase = true)
    }

private fun logRedirectChainSummary(label: String, leaf: Response) {
    val chronological = ArrayList<String>()
    var r: Response? = leaf
    while (r != null) {
        chronological.add("${r.code} ${r.request.url}")
        r = r.priorResponse
    }
    chronological.reverse()
    traceCas("$label chain (${chronological.size} hops): ${chronological.joinToString(" » ")}".take(4000))
}

private fun logCastgcDetailSnapshot(jar: InMemoryCookieJar, ctx: String) {
    val snap = jar.snapshot()
    val gcs = snap.filter { it.name == "CASTGC" && it.domain.contains("uestc", ignoreCase = true) }
    traceCas(
        "[$ctx] jarEntries=${snap.size} CASTGC domainRows=${gcs.size}",
    )
    for (c in gcs) {
        val dom = c.domain.removePrefix(".").lowercase()
        traceCas(
            "  CASTGC dom=$dom path=${c.path} sec=${c.secure} ho=${c.httpOnly} persist=${c.persistent} expiresAt=${if (c.persistent) c.expiresAt else -1} valueLen=${c.value.length}",
        )
    }
    if (gcs.isEmpty()) {
        traceCas("  (Jar 内无 CASTGC；全部键=${snap.map { normalizeCookieDomainBrief(it.domain) + "/" + it.name }.distinct().sorted()})".take(2000))
    }
}

private fun logJarCookiesAfterCredentialPost(jar: InMemoryCookieJar, httpCode: Int) {
    val snap = jar.snapshot()
    val brief =
        snap
            .map { "${normalizeCookieDomainBrief(it.domain)}/${it.name}" }
            .distinct()
            .sorted()
    traceCas(
        "credentialRsp http=$httpCode hasCASTGC=${snap.containsCastGc()} jarKeys=$brief",
    )
}

private fun normalizeCookieDomainBrief(domain: String): String =
    domain.removePrefix(".").lowercase()

private fun castgcMissingExplain(): String =
    "未能完成统一身份认证，请检查学号与密码后重试；若仍失败请用右上角 Web 登录。"

private fun executionMissingDiag(loginUrlUsed: String, html: String): String {
    val h = html.lowercase()
    val parts =
        buildList {
            add("长度=${html.length}")
            add("casLoginForm=${h.contains("casloginform")}")
            add("credentials=${Regex("""\bid\s*=\s*(["'])credentials\1""", RegexOption.IGNORE_CASE).containsMatchIn(html)}")
            add("password输入框=${Regex("""type\s*=\s*["']password["']""", RegexOption.IGNORE_CASE).containsMatchIn(html)}")
            add("含execution字面=${h.contains("execution")}")
            add("网关壳_TS=${html.containsAntiBotTs()}")
        }.joinToString(", ")
    return "[诊断] GET $loginUrlUsed: $parts。可先 WebView 登录；若仍无登录表单，需更新解析规则。"
}

private fun String.containsAntiBotTs(): Boolean =
    GatewayTsShellHeuristic.isLikelyThinGatewayPlaceholder(this)

private enum class CredentialFailKind {
    WRONG_USERNAME_PASSWORD,
    CAPTCHA_OR_RISK,
    SESSION_OR_FORM,
    PAGE_MESSAGE_OTHER,
    UNKNOWN,
}

private data class CredentialFailureDiagnosis(
    val kind: CredentialFailKind,
    val pageTip: String,
) {
    fun traceLabel(): String =
        buildString {
            append("kind=$kind")
            if (pageTip.isNotEmpty()) append(" pageTip=${pageTip.take(120)}")
        }
}

private object Forms {

    fun execution(html: String): String? {
        casLoginFormInner(html)?.let { inner ->
            matchExecution(inner)?.let { return it }
        }
        return matchExecution(html)
    }

    fun pwdSalt(html: String): String? = pwdSaltInfo(html)?.salt

    fun pwdSaltInfo(html: String): PwdSaltInfo? {
        matchPwdSaltField(html, "pwdDefaultEncryptSalt")?.let {
            return PwdSaltInfo(it, "pwdDefaultEncryptSalt")
        }
        casLoginFormInner(html)?.let { inner ->
            matchPwdSaltField(inner, "pwdEncryptSalt")?.let {
                return PwdSaltInfo(it, "pwdEncryptSalt")
            }
            matchPwdSaltField(inner, "pwdDefaultEncryptSalt")?.let {
                return PwdSaltInfo(it, "pwdDefaultEncryptSalt")
            }
        }
        matchPwdSaltField(html, "pwdEncryptSalt")?.let {
            return PwdSaltInfo(it, "pwdEncryptSalt")
        }
        return null
    }

    data class PwdSaltInfo(val salt: String, val fieldId: String)

    private fun matchExecution(htmlFragment: String): String? {
        val patterns =
            listOf(
                Regex("""id="execution"[^>]*name="execution"[^>]*value="([^"]+)"""", RegexOption.IGNORE_CASE),
                Regex("""name=["']execution["']\s+value=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex(
                    """<input[^>]+name=["']execution["'][^>]+value=["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(
                    """<input\b[\s\S]*?\bname\s*=\s*["']execution["'][\s\S]*?\bvalue\s*=\s*["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(
                    """<input\b[\s\S]*?\bvalue\s*=\s*["']([^"']+)["'][\s\S]*?\bname\s*=\s*["']execution["']""",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(
                    """<input\b[\s\S]*?\bid\s*=\s*["']execution["'][\s\S]*?\bvalue\s*=\s*["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(
                    """id\s*=\s*["']execution["'][^>]*name\s*=\s*["']execution["'][^>]*value\s*=\s*["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE,
                ),
                Regex("""name\s*=\s*["']execution["'][^>]*value\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex(
                    """<input[^>]+name\s*=\s*["']execution["'][^>]+value\s*=\s*["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(
                    """value\s*=\s*["']([^"']+)["'][^>]*name\s*=\s*["']execution["']""",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(""""execution"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE),
            )
        return patterns.firstNotNullOfOrNull { rx ->
            rx.find(htmlFragment)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotEmpty)
        }
    }

    private fun matchPwdSaltField(fragment: String, fieldId: String): String? {
        val patterns =
            listOf(
                Regex(
                    """${Regex.escape(fieldId)}[\s\S]{0,800}?value\s*=\s*["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE,
                ),
                Regex("""id=["']${Regex.escape(fieldId)}["'][^>]*value=["']([^"']+)"""", RegexOption.IGNORE_CASE),
                Regex("""value=["']([^"']+)["'][^>]*id=["']${Regex.escape(fieldId)}["']""", RegexOption.IGNORE_CASE),
                Regex("""name=["']${Regex.escape(fieldId)}["'][^>]*value=["']([^"']+)"""", RegexOption.IGNORE_CASE),
            )
        return patterns.firstNotNullOfOrNull { rx ->
            rx.find(fragment)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotEmpty)
        }
    }

    fun casLoginFormInner(html: String): String? {
        val byId =
            listOf(
                Regex("""<form[^>]*\bid\s*=\s*["']casLoginForm["'][^>]*>([\s\S]*?)</form>""", RegexOption.IGNORE_CASE),
                Regex("""<form[^>]*\bid\s*=\s*["']fm1["'][^>]*>([\s\S]*?)</form>""", RegexOption.IGNORE_CASE),
                Regex("""<form[^>]*\bid\s*=\s*["']credentials["'][^>]*>([\s\S]*?)</form>""", RegexOption.IGNORE_CASE),
                Regex(
                    """<form[^>]*>([\s\S]*?\bname\s*=\s*["']execution["'][\s\S]*?)</form>""",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(
                    """<form[^>]*\b(?:action|data-action)\s*=\s*["'][^"']*/(?:login|cas)[^"']*["'][^>]*>([\s\S]*?)</form>""",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(
                    """<form[^>]*>([\s\S]*?\btype\s*=\s*["']password["'][\s\S]*?)</form>""",
                    RegexOption.IGNORE_CASE,
                ),
            )
        for (r in byId) {
            r.find(html)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    fun casLoginFormHidden(loginPageHtml: String): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        casLoginFormInner(loginPageHtml)?.let { out.putAll(hiddenInputs(it)) }
        hiddenInputs(loginPageHtml).forEach { (k, v) ->
            if (k !in out) out[k] = v
        }
        return out
    }

    fun credentialPostFields(
        loginPageHtml: String,
        username: String,
        execution: String,
        encPwd: String,
    ): LinkedHashMap<String, String> {
        val inner = casLoginFormInner(loginPageHtml) ?: loginPageHtml
        val hidden = hiddenInputs(inner)
        return linkedMapOf(
            "username" to username,
            "password" to encPwd,
            "captcha" to hidden["captcha"].orEmpty(),
            "rememberMe" to "true",
            "_eventId" to hidden["_eventId"].orEmpty().ifBlank { "submit" },
            "cllt" to "userNameLogin",
            "dllt" to hidden["dllt"].orEmpty().ifBlank { "generalLogin" },
            "lt" to hidden["lt"].orEmpty(),
            "execution" to execution,
        )
    }

    fun casLoginPostUrl(html: String, fallbackUrl: String): HttpUrl {
        val patterns =
            listOf(
                Regex(
                    """<form[^>]*\bid\s*=\s*["']casLoginForm["'][^>]*\baction\s*=\s*["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(
                    """<form[^>]*\baction\s*=\s*["']([^"']+)["'][^>]*\bid\s*=\s*["']casLoginForm["']""",
                    RegexOption.IGNORE_CASE,
                ),
            )
        val base = ApiConstants.CAS_ORIGIN.toHttpUrl()
        for (r in patterns) {
            val action = r.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (action.isNotEmpty() && action != "#") {
                mergeHttp(base, action)?.let { return it }
            }
        }
        return fallbackUrl.toHttpUrl()
    }

    fun extractError(html: String): String? {
        val patterns =
            listOf(
                Regex(
                    """<span[^>]+id=['"]showErrorTip['"][^>]*>([\s\S]*?)</span>""",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(
                    """<span[^>]+id=['"]errorTip['"][^>]*>([\s\S]*?)</span>""",
                    RegexOption.IGNORE_CASE,
                ),
                Regex(
                    """<div[^>]+class=['"][^'"]*errors?[^'"]*['"][^>]*>([\s\S]*?)</div>""",
                    RegexOption.IGNORE_CASE,
                ),
            )
        for (r in patterns) {
            r.find(html)?.groups?.get(1)?.value?.replace('\n', ' ')?.trim()?.takeIf { it.isNotEmpty() }?.let {
                return it
            }
        }
        return null
    }

    /** 登录页是否需要图形验证码。 */
    fun loginFormRequiresGraphicalCaptcha(html: String): Boolean {
        val inner = casLoginFormInner(html) ?: return false
        if (Regex("""id=["']captchaResponse["']""", RegexOption.IGNORE_CASE).containsMatchIn(inner)) {
            return true
        }
        if (Regex("""geetest|slider-captcha|nc-container|needCaptcha\s*[=:]\s*true""", RegexOption.IGNORE_CASE)
                .containsMatchIn(inner)
        ) {
            return true
        }
        return false
    }

    /** 根据页面文案判断登录失败原因。 */
    fun diagnoseCredentialFailure(html: String): CredentialFailureDiagnosis {
        val pageTip = extractError(html)?.trim().orEmpty()
        val formInner = casLoginFormInner(html).orEmpty()
        val formScan = (pageTip + "\n" + formInner).lowercase(Locale.ROOT)
        val kind =
            when {
                pageTip.isNotEmpty() &&
                    (
                        "用户名" in pageTip || "密码" in pageTip || "账号" in pageTip ||
                            "口令" in pageTip || "不正确" in pageTip
                    ) &&
                    (
                        "错误" in pageTip || "失败" in pageTip || "不正确" in pageTip ||
                            "invalid" in formScan
                    ) ->
                    CredentialFailKind.WRONG_USERNAME_PASSWORD
                loginFormRequiresGraphicalCaptcha(html) ||
                    (
                        pageTip.isNotEmpty() &&
                            listOf("图形验证", "滑块", "geetest", "人机验证", "图形验证码").any { it in pageTip }
                    ) ||
                    (
                        pageTip.isNotEmpty() &&
                            "验证码" in pageTip &&
                            "短信" !in pageTip &&
                            "动态" !in pageTip
                    ) ->
                    CredentialFailKind.CAPTCHA_OR_RISK
                listOf("execution", "过期", "失效", "频繁").any { it in formScan } && pageTip.isNotEmpty() ->
                    CredentialFailKind.SESSION_OR_FORM
                pageTip.isNotEmpty() -> CredentialFailKind.PAGE_MESSAGE_OTHER
                else -> CredentialFailKind.UNKNOWN
            }
        return CredentialFailureDiagnosis(kind, pageTip)
    }

    /** 判断页面是否需要短信或多因素二次认证。 */
    fun needsIdasSmsOrMultifactorReauth(html: String, pageUrl: String): Boolean {
        if (smsPage(html)) return true
        val u = pageUrl.lowercase(Locale.ROOT)
        if ("reauthcheck" in u || "ismultifactor=true" in u || "multifactor" in u) return true
        if (html.isEmpty()) return false
        val maxScan = kotlin.math.min(html.length, 400_000)
        val win = html.substring(0, maxScan)
        if ("二次认证" in win || "多因素" in win) return true
        val low = win.lowercase(Locale.ROOT)

        fun anyToken(vararg t: String) = t.any { it in low }

        if (
            anyToken(
                "reauth",
                "/reauthcheck/",
                "reauthsubmit",
                "reauthloginview",
                "/reauthcheck/reauthsubmit",
                "ismultifactor",
                "\"ismultifactor\":\"true",
                "=ismultifactor",
                "ismultifactor%3dtrue",
                "dynauthplatform",
                "getdynamiccode",
                "手机短信",
                "短信验证",
                "动态口令",
                "请输入验证码",
            )
        ) {
            return true
        }

        if (Regex(""""reAuthUserId"\s*:\s*"[^"]*[0-9]""", RegexOption.IGNORE_CASE).containsMatchIn(win)) {
            return true
        }
        return Regex(""""reAuthType"\s*:\s*"\d"""").containsMatchIn(win)
    }

    /** 从 HTML 中提取 CAS ticket URL。 */
    fun extractCasOnlineTicketCandidates(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val raw = html.replace("&amp;", "&")
        val out = LinkedHashSet<String>()
        val scan =
            Regex(
                pattern = """(?i)(https://(?:[a-z0-9-]+\.)*uestc\.edu\.cn[^\s"'<>]{12,8000})""",
            )
        for (m in scan.findAll(raw)) {
            coerceParsableOnlineTicketUrl(m.groupValues[1])?.let { out.add(it) }
        }
        Regex(pattern = "ticket=", option = RegexOption.IGNORE_CASE).findAll(raw).forEach tk@{
            val i = it.range.first
            var hi = raw.lastIndexOf("https://", i)
            while (hi >= 0) {
                val tail = raw.substring(hi, kotlin.math.min(raw.length, hi + 8192))
                Regex("""(?i)^(https://(?:[a-z0-9-]+\.)*uestc\.edu\.cn[^\s"'<>]{12,})""")
                    .find(tail)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let { cand ->
                        coerceParsableOnlineTicketUrl(cand)?.let { u ->
                            out.add(u)
                            return@tk
                        }
                    }
                hi = raw.lastIndexOf("https://", hi - 1)
            }
        }
        return out.distinct().take(8)
    }

    private fun coerceParsableOnlineTicketUrl(candidate: String): String? {
        if (!candidate.contains("ticket=", ignoreCase = true)) return null
        var norm = candidate.replace("\\/", "/").trim()
        while (norm.endsWith("\\")) norm = norm.dropLast(1)
        val cap = minOf(norm.length, 4096)
        var len = cap
        while (len >= 48) {
            val slice =
                norm.take(len).trimEnd(',', ';', '\"', '\'').trimEnd(')', ']').trimEnd()
            val hu = slice.toHttpUrlOrNull()
            if (hu != null) {
                val h = hu.host.lowercase(Locale.ROOT)
                val school = h == "uestc.edu.cn" || h.endsWith(".uestc.edu.cn")
                if (school && !hu.queryParameter("ticket").isNullOrBlank()) return hu.toString()
            }
            len--
        }
        return null
    }


    fun smsPage(html: String): Boolean {
        val lower = html.lowercase()
        val needsForm =
            listOf("tmpreauth", "reauthsubmit", "dynamiccodevalidate").any(lower::contains)
        val smsish = listOf("dynamiccode", "smscode", "短信").any(lower::contains)
        return needsForm && smsish
    }

    fun skipHref(html: String): HttpUrl? {
        val m =
            Regex(
                """href\s*=\s*["']([^"']*skipTmpReAuth[^"']*)["']""",
                RegexOption.IGNORE_CASE,
            ).find(html) ?: return null
        val raw = m.groupValues[1].trim().replace("&amp;", "&")

        val casLoginHttp = ApiConstants.casLoginUrlWithService().toHttpUrl()
        val casOriginHttp = ApiConstants.CAS_ORIGIN.toHttpUrl()

        return mergeHttp(casLoginHttp, raw)
            ?: mergeHttp(casOriginHttp, raw)
            ?: mergeHttp(ApiConstants.casLoginUrlWithService().toHttpUrl(), "/" + raw.removePrefix("/"))
    }

    fun hiddenInputs(html: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        Regex("<input[^>]*>", RegexOption.IGNORE_CASE).findAll(html).forEach { match ->
            val t = match.value
            if (!Regex("type\\s*=\\s*[\"']hidden[\"']", RegexOption.IGNORE_CASE).containsMatchIn(t)) return@forEach
            val name =
                Regex("name\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                    .find(t)?.groups?.get(1)?.value
                    ?: return@forEach

            val value =
                Regex("value\\s*=\\s*[\"']([^\"]*)[\"']", RegexOption.IGNORE_CASE).find(t)?.groups?.get(1)?.value
                    ?: Regex("value\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE).find(t)?.groups?.get(1)?.value
                        ?: ""
            out[name] = value
        }
        return out
    }
}

private fun OkHttpClient.drain(url: String) {
    newCall(Request.Builder().url(url).get().build()).execute().close()
}

private fun OkHttpClient.getBody(url: String): String =
    newCall(Request.Builder().url(url).get().build())
        .execute()
        .use { it.body?.string().orEmpty() }

private fun OkHttpClient.getHtmlPair(url: String): Pair<String, String> =
    newCall(Request.Builder().url(url).get().build()).execute().use { rsp ->
        (rsp.body?.string().orEmpty()) to rsp.request.url.toString()
    }

private fun Request.Builder.applyIdasNavigateGetHeaders(): Request.Builder =
    header("Upgrade-Insecure-Requests", "1")
        .header("Sec-Fetch-Site", "same-origin")
        .header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-User", "?1")
        .header("Sec-Fetch-Dest", "document")

private fun expectedAesPasswordBase64Length(passwordUtf8Bytes: Int, randomPrefixLen: Int = 64): Int {
    val plainLen = randomPrefixLen + passwordUtf8Bytes
    val paddedLen = plainLen + (16 - (plainLen % 16))
    return 4 * ((paddedLen + 2) / 3)
}

private fun sha256HexPrefix(text: String, hexChars: Int): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray())
    return digest.joinToString("") { b -> "%02x".format(b) }.take(hexChars)
}

private fun credentialPostFailedIOException(
    httpCode: Int,
    body: String,
    usernameHint: String,
    diagnosis: CredentialFailureDiagnosis,
): IOException {
    val pageErr = diagnosis.pageTip
    val msg =
        when (diagnosis.kind) {
            CredentialFailKind.WRONG_USERNAME_PASSWORD -> "学号或密码不正确，请核对后重试。"
            CredentialFailKind.CAPTCHA_OR_RISK -> "需要图形验证码，请点右上角 Web 在网页中登录。"
            CredentialFailKind.SESSION_OR_FORM -> "登录已过期，请关闭登录框后重新登录。"
            CredentialFailKind.PAGE_MESSAGE_OTHER ->
                pageErr.ifBlank { "登录未成功，请稍后重试或使用 Web 登录。" }
            CredentialFailKind.UNKNOWN ->
                pageErr.ifBlank { "登录未成功，请检查账号密码；仍失败请用右上角 Web 登录。" }
        }
    return IOException(msg)
}

private fun loginPost(
    loginUrlStr: String,
    loginRefererPlain: String,
    loginPageHtml: String,
    username: String,
    execution: String,
    encPwd: String,
): Request {
    val postUrl = Forms.casLoginPostUrl(loginPageHtml, loginUrlStr)
    val merged =
        Forms.credentialPostFields(
            loginPageHtml = loginPageHtml,
            username = username,
            execution = execution,
            encPwd = encPwd,
        )

    traceCas(
        "credential POST url=${postUrl.toString().take(220)} fields=${merged.keys.sorted().joinToString(",")}",
    )

    val body =
        FormBody.Builder(Charsets.UTF_8).apply {
            merged.forEach { (name, value) -> add(name, value) }
        }.build()

    return Request.Builder()
        .url(postUrl)
        .header("Cache-Control", "max-age=0")
        .header("Referer", loginRefererPlain)
        .header("Origin", ApiConstants.CAS_ORIGIN)
        .header("Upgrade-Insecure-Requests", "1")
        .header("Sec-Fetch-Site", "same-origin")
        .header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-User", "?1")
        .header("Sec-Fetch-Dest", "document")
        .header(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        )
        .post(body)
        .build()
}

private fun mergeHttp(
    base: HttpUrl,
    relRaw: String,
): HttpUrl? {
    val rel = relRaw.replace("&amp;", "&").trim().ifBlank { return null }

    return try {
        when {
            rel.startsWith("http://", ignoreCase = true) || rel.startsWith("https://", ignoreCase = true) ->
                rel.toHttpUrl()
            rel.startsWith("/") ->
                "${base.scheme}://${base.host}$rel".toHttpUrl()

            rel.startsWith("authserver/", ignoreCase = true) ->
                "${ApiConstants.CAS_ORIGIN}/$rel".toHttpUrl()

            else ->
                base.resolve(rel)
                    ?: ApiConstants.casLoginUrlWithService().toHttpUrl().resolve(rel)
        }
    } catch (_: Exception) {
        null
    }
}
