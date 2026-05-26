package edu.uestc.eams.helper.data.network

import java.util.Locale

/**
 * 网关防爬占位页会在 HTML/JSON 里出现 `$_ts` 或字面上的 `\u0024_ts`。
 * **正常一网通 SPA**（体量约 12k）的脚本/内联配置里也会出现同一串，单凭[出现过]不可判为拦截壳。
 *
 * 与 Python：`warm_online_schedule_context` 能拿到大号 HTML 时仍应继续走 scheduleList；
 * `_online_schedule_shell_html_usable`、`schedule_list_page_probe_ok` 对 scheduleList **壳** 的判定在下面单独给出。
 */
internal object GatewayTsShellHeuristic {

    fun containsTsBootstrapMarker(body: String): Boolean =
        body.contains("${'$'}_ts") || body.contains("\\u0024_ts")

    /** Python `_online_schedule_shell_html_usable`（URL 已为 scheduleList 域内页时调用）。*/
    fun onlineScheduleListShellUsable(html: String): Boolean {
        val tl = html.lowercase(Locale.ROOT)
        if (html.contains(ApiConstants.ONLINE_STRUTS_ONCE_PARAM)) return true
        if ("/site/schedule/" in tl) return true
        val hasApp = "id=\"app\"" in tl || "id='app'" in tl
        return hasApp && "/page/assets/" in tl
    }

    /** 大号门户壳（含 SPA 引导）的典型信号：有这些则即便带 ts 也**不**当占位壳。*/
    fun looksLikeUeStcOnlineSpaBootstrap(body: String): Boolean =
        body.length >= 8192 ||
            body.containsScheduleOrPortalSignals()

    private fun String.containsScheduleOrPortalSignals(): Boolean =
        contains("scheduleList", ignoreCase = true) ||
            contains("schedule/index", ignoreCase = true) ||
            contains("/site/schedule/", ignoreCase = true) ||
            contains("\"page\"", ignoreCase = true) ||
            contains("/page/", ignoreCase = true) ||
            contains("id=\"app\"", ignoreCase = true) ||
            contains("id='app'", ignoreCase = true)

    /** `true` = 更像是 **短小占位/挑战页**，应中断 OkHttp HTML 链路。*/
    fun isLikelyThinGatewayPlaceholder(body: String): Boolean {
        if (!containsTsBootstrapMarker(body)) return false
        if (looksLikeUeStcOnlineSpaBootstrap(body)) return false
        return true
    }
}
