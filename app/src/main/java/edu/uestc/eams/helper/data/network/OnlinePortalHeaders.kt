package edu.uestc.eams.helper.data.network

import okhttp3.Request

/** Python `_online_browser_headers_nav`：`GET …/page/`、`GET …/scheduleList`、以及 **`GET` ticket 消费**。 */
fun Request.Builder.applyOnlineNavigateHeaders(referer: String): Request.Builder {
    header("Referer", referer)
    header(
        "Accept",
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/png,image/svg+xml,*/*;q=0.8",
    )
    header("Upgrade-Insecure-Requests", "1")
    header("Sec-Fetch-Site", "same-origin")
    header("Sec-Fetch-Mode", "navigate")
    header("Sec-Fetch-User", "?1")
    header("Sec-Fetch-Dest", "document")
    header("Priority", "u=0, i")
    return this
}

/** Python `_online_browser_headers_ajax`：`POST …/site/schedule/index`。 */
fun Request.Builder.applyOnlineAjaxPostHeaders(
    referer: String,
    origin: String,
): Request.Builder {
    header("Referer", referer)
    header("Origin", origin.trimEnd('/'))
    header("Accept", "application/json, text/javascript, */*; q=0.01")
    header("X-Requested-With", "XMLHttpRequest")
    header("Sec-Fetch-Site", "same-origin")
    header("Sec-Fetch-Mode", "cors")
    header("Sec-Fetch-Dest", "empty")
    header("Priority", "u=4, i")
    return this
}
