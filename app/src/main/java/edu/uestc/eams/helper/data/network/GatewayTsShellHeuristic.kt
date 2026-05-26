package edu.uestc.eams.helper.data.network

import java.util.Locale

/** 判断响应是否为网关占位页，避免与正常一网通 SPA 混淆。 */
internal object GatewayTsShellHeuristic {

    fun containsTsBootstrapMarker(body: String): Boolean =
        body.contains("${'$'}_ts") || body.contains("\\u0024_ts")

    /** 判断 scheduleList 页 HTML 是否可用。 */
    fun onlineScheduleListShellUsable(html: String): Boolean {
        val tl = html.lowercase(Locale.ROOT)
        if (html.contains(ApiConstants.ONLINE_STRUTS_ONCE_PARAM)) return true
        if ("/site/schedule/" in tl) return true
        val hasApp = "id=\"app\"" in tl || "id='app'" in tl
        return hasApp && "/page/assets/" in tl
    }

    /** 是否像正常一网通 SPA 引导页。 */
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
