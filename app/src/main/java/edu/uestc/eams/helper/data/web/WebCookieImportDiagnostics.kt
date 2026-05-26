package edu.uestc.eams.helper.data.web

import edu.uestc.eams.helper.data.eamsapp.EamsAppCookie
import edu.uestc.eams.helper.data.session.StoredCookie
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Debug：Web 导入会话时的 Cookie 探测报告。 */
object WebCookieImportDiagnostics {

    data class Context(
        val webViewUrl: String?,
        val importPageUrl: String?,
        val urlBarText: String?,
        val hintUrls: List<String>,
        val documentCookieHeader: String,
        val storageToken: String,
        val fromCookieManager: List<StoredCookie>,
        val fromDocument: List<StoredCookie>,
        val fromStorage: List<StoredCookie>,
        val fromUrl: List<StoredCookie> = emptyList(),
        val merged: List<StoredCookie>,
        val normalized: List<StoredCookie>,
        val probeByUrl: List<UrlProbe>,
    )

    data class UrlProbe(
        val url: String,
        val rawHeader: String?,
        val parsedCount: Int,
    )

    fun collectUrlProbes(hintUrls: List<String>): List<UrlProbe> {
        val probes = LinkedHashSet<String>()
        WebViewCookieReader.listProbeUrls(hintUrls).forEach { probes.add(it) }
        return probes.map { url ->
            val raw = WebViewCookieReader.probeRawCookieHeader(url)
            val parsed =
                if (raw.isNullOrBlank()) {
                    emptyList()
                } else {
                    try {
                        val host = url.toHttpUrl().host
                        WebViewCookieReader.parseCookiePairsPublic(raw, host, true)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            UrlProbe(url = url, rawHeader = raw, parsedCount = parsed.size)
        }
    }

    fun buildReport(ctx: Context): String {
        val sb = StringBuilder()
        sb.appendLine("[Web 导入诊断] 仅本机排障，请勿外传截图")
        sb.appendLine()
        sb.appendLine("── 页面 URL ──")
        sb.appendLine("WebView.url: ${ctx.webViewUrl ?: "(空)"}")
        sb.appendLine("导入校验用: ${ctx.importPageUrl ?: "(空)"}")
        sb.appendLine("地址栏: ${ctx.urlBarText?.takeIf { it.isNotBlank() } ?: "(空)"}")
        sb.appendLine()
        sb.appendLine("── URL 探测 (${ctx.hintUrls.size} 条 hint，${ctx.probeByUrl.size} 条展开) ──")
        ctx.hintUrls.take(12).forEachIndexed { i, u -> sb.appendLine("  hint[$i] $u") }
        if (ctx.hintUrls.size > 12) sb.appendLine("  …共 ${ctx.hintUrls.size} 条")
        sb.appendLine()
        sb.appendLine("── CookieManager.getCookie(按 URL) ──")
        val interesting =
            ctx.probeByUrl.filter { p ->
                p.url.contains("eamsapp", ignoreCase = true) ||
                    !p.rawHeader.isNullOrBlank()
            }
        if (interesting.isEmpty()) {
            sb.appendLine("  (eamsapp 相关 URL 均无返回，或其它域也为空)")
        } else {
            interesting.take(24).forEach { p ->
                sb.appendLine("  URL: ${p.url}")
                if (p.rawHeader.isNullOrBlank()) {
                    sb.appendLine("    → (空)")
                } else {
                    sb.appendLine("    → 原始: ${p.rawHeader}")
                    sb.appendLine("    → 解析 ${p.parsedCount} 项")
                }
                sb.appendLine()
            }
        }
        sb.appendLine("── document.cookie ──")
        if (ctx.documentCookieHeader.isBlank()) {
            sb.appendLine("  (空，HttpOnly 不会出现在这里)")
        } else {
            sb.appendLine("  原始: ${ctx.documentCookieHeader}")
            appendCookieList(sb, "  解析", ctx.fromDocument)
        }
        sb.appendLine()
        sb.appendLine("── 页面 URL ?jsessionid= ──")
        val urlJwt = WebSessionImport.extractJwtFromPageUrl(ctx.importPageUrl)
        if (urlJwt.isNullOrBlank()) {
            sb.appendLine("  (未解析到 JWT 查询参数)")
        } else {
            sb.appendLine("  token: $urlJwt")
            sb.appendLine("  looksLikeJwt: ${EamsAppCookie.looksLikeJwt(urlJwt)}")
        }
        sb.appendLine()
        sb.appendLine("── localStorage / sessionStorage ──")
        if (ctx.storageToken.isBlank()) {
            sb.appendLine("  未发现 JWT 形态 token")
        } else {
            sb.appendLine("  token: ${ctx.storageToken}")
            sb.appendLine("  looksLikeJwt: ${EamsAppCookie.looksLikeJwt(ctx.storageToken)}")
        }
        sb.appendLine()
        sb.appendLine("── 合并来源 ──")
        sb.appendLine("  CookieManager 合计: ${ctx.fromCookieManager.size} 项")
        appendCookieList(sb, "  ", ctx.fromCookieManager.filter { it.domain.contains("eamsapp", true) }.ifEmpty { ctx.fromCookieManager.take(20) })
        sb.appendLine("  document 解析: ${ctx.fromDocument.size} 项")
        sb.appendLine("  storage 合成: ${ctx.fromStorage.size} 项")
        sb.appendLine("  URL 查询参数: ${ctx.fromUrl.size} 项")
        sb.appendLine()
        sb.appendLine("── 规范化后 (${ctx.normalized.size} 项) ──")
        appendCookieList(sb, "  ", ctx.normalized.filter { it.domain.contains("eamsapp", true) }.ifEmpty { ctx.normalized })
        sb.appendLine()
        val jwt =
            WebSessionImport.resolveSessionJwt(
                ctx.importPageUrl,
                ctx.storageToken,
                ctx.normalized,
            )
        sb.appendLine("── 校验 ──")
        sb.appendLine("  resolveSessionJwt: ${jwt ?: "(无)"}")
        sb.appendLine(
            "  validate: ${
                WebSessionImport.validate(
                    ctx.importPageUrl,
                    ctx.storageToken,
                    ctx.normalized,
                ) ?: "通过"
            }",
        )
        return sb.toString().trimEnd()
    }

    private fun appendCookieList(sb: StringBuilder, prefix: String, cookies: List<StoredCookie>) {
        if (cookies.isEmpty()) {
            sb.appendLine("${prefix}(无)")
            return
        }
        cookies.forEach { c ->
            val jwtFlag =
                if (EamsAppCookie.looksLikeJwt(c.value)) " [JWT]" else ""
            sb.appendLine("$prefix${c.domain} | ${c.name}=${c.value}$jwtFlag")
        }
    }

}
