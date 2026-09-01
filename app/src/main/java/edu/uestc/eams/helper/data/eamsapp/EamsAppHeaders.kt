package edu.uestc.eams.helper.data.eamsapp

import edu.uestc.eams.helper.data.network.ApiConstants
import okhttp3.Request

fun Request.Builder.applyEamsAppNavigateHeaders(referer: String = "https://idas.uestc.edu.cn/"): Request.Builder {
    header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
    header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
    // 勿设 Accept-Encoding：由 OkHttp 自动加并透明解压；手写会导致 body 仍为 gzip 二进制。
    header("User-Agent", ApiConstants.CLIENT_USER_AGENT_EAMS)
    header("sec-ch-ua", ApiConstants.EAMS_SEC_CH_UA)
    header("sec-ch-ua-mobile", "?0")
    header("sec-ch-ua-platform", "\"Windows\"")
    header("Sec-Fetch-Dest", "document")
    header("Sec-Fetch-Mode", "navigate")
    header("Sec-Fetch-Site", "same-site")
    header("Upgrade-Insecure-Requests", "1")
    header("Referer", referer)
    return this
}

fun mobileApiHeaders(cookieHeader: String): Map<String, String> {
    val jwt =
        EamsAppCookie.parseHeaderValue(cookieHeader, "JSESSIONID").orEmpty()
            .takeIf { EamsAppCookie.looksLikeJwt(it) }
            .orEmpty()
    return mapOf(
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
        "Authorization" to "Basic ${ApiConstants.EAMSAPP_AUTHORIZATION_BASIC}",
        "Content-Type" to "application/x-www-form-urlencoded;charset=utf-8",
        "Cookie" to cookieHeader,
        "Referer" to "${ApiConstants.EAMSAPP_ORIGIN}/",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin",
        "User-Agent" to ApiConstants.CLIENT_USER_AGENT_EAMS,
        "blade-auth" to "bearer $jwt",
        "sec-ch-ua" to ApiConstants.EAMS_SEC_CH_UA,
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Windows\"",
    )
}
