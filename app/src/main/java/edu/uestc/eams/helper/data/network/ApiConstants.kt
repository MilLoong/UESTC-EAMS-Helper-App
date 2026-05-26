package edu.uestc.eams.helper.data.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.brotli.BrotliInterceptor
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 各站点 URL 与 OkHttp 默认请求头。 */
object ApiConstants {

    const val CAS_BASE_URL = "https://idas.uestc.edu.cn/authserver"
    const val CAS_ORIGIN = "https://idas.uestc.edu.cn"
    const val BASE_EAMS = "https://eams.uestc.edu.cn"

    const val ONLINE_ORIGIN = "https://online.uestc.edu.cn"

    const val ONLINE_STRUTS_ONCE_PARAM = "4oY1vBSn"

    val ONLINE_PAGE_URL: String get() = "$ONLINE_ORIGIN/page/"

    val ONLINE_SCHEDULE_LIST_URL: String get() = "$ONLINE_ORIGIN/page/scheduleList"

    fun buildOnlineScheduleIndexUrl(token: String): HttpUrl =
        ONLINE_ORIGIN.toHttpUrl().newBuilder()
            .encodedPath("/site/schedule/index")
            .addQueryParameter(ONLINE_STRUTS_ONCE_PARAM, token)
            .build()

    const val EAMSAPP_ORIGIN = "https://eamsapp.uestc.edu.cn"

    const val EAMSAPP_CAS_LOGIN_API = "$EAMSAPP_ORIGIN/api/blade-auth/cas-login"

    val EAMSAPP_CAS_SERVICE: String
        get() = "$EAMSAPP_CAS_LOGIN_API?redirectUrl=$EAMSAPP_ORIGIN"

    const val EAMSAPP_AUTHORIZATION_BASIC = "YXBwOmFwcF9zZWNyZXQ="

    val CAS_SERVICE_RAW: String get() = EAMSAPP_CAS_SERVICE

    val ONLINE_CAS_SERVICE: String
        get() =
            "${ONLINE_ORIGIN}/common/actionCasLogin?" +
                "redirect_url=" +
                URLEncoder.encode("${ONLINE_ORIGIN}/page/", Charsets.UTF_8.name())

    const val EAMS_SERVICE_RAW = "$BASE_EAMS/eams/login.action"

    fun casServiceEncoded(): String =
        URLEncoder.encode(CAS_SERVICE_RAW, Charsets.UTF_8.name())

    fun casLoginUrlWithService(): String =
        "$CAS_BASE_URL/login?service=${casServiceEncoded()}"

    fun casLoginRefererPlain(): String =
        "$CAS_BASE_URL/login?service=${CAS_SERVICE_RAW}"

    val EAMS_HOME_URL get() = "$BASE_EAMS/eams/home.action"

    const val DEFAULT_EAMS_MENU_ID = "844"

    val EAMS_CHILDMENUS_URL get() =
        "$BASE_EAMS/eams/home!childmenus.action?menu.id=$DEFAULT_EAMS_MENU_ID"

    /** 发往 idas/authserver 的非 eams UA。*/
    const val CLIENT_USER_AGENT_IDAS =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"

    /** 教务系统与一网通门户共用的桌面浏览器 User-Agent。 */
    const val CLIENT_USER_AGENT_EAMS =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0"

    /** 别名：与 CLIENT_USER_AGENT_EAMS 同串。*/
    const val EAMS_BROWSER_UA: String = CLIENT_USER_AGENT_EAMS

    val WEBVIEW_USER_AGENT: String get() = CLIENT_USER_AGENT_EAMS

    /**
     * 内置浏览器访问 Idas/CAS 时用：服务端走移动端布局，
     * 避免桌面浏览器 UA 与手机屏宽组合导致页面图层叠乱。
     * OkHttp 仍用上面的桌面 UA；WebView 与 Jar 脱节仅影响页面外观，一般不碍登录 Cookie。
     */
    const val WEBVIEW_USER_AGENT_IDAS_MOBILE =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"

    /** 当前页 host 是否应使用统一认证移动端布局。 */
    fun webViewShouldUseCasMobileLayout(host: String?): Boolean {
        val h = host?.lowercase(Locale.ROOT) ?: return false
        return h.contains("idas.uestc") ||
            ((h.endsWith(".uestc.edu.cn") || h.endsWith(".uestc.cn")) && "authserver" in h)
    }

    const val EAMS_SEC_CH_UA =
        "\"Chromium\";v=\"148\", \"Microsoft Edge\";v=\"148\", \"Not/A)Brand\";v=\"99\""

    const val SEC_CH_UA_EAMS: String = EAMS_SEC_CH_UA

    /** 按请求 host 补全 User-Agent 与 Client Hints；调用方已设置的 Referer、Accept 不覆盖。 */
    fun buildOkHttp(cookieJar: InMemoryCookieJar): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            // 声明 br 时需 BrotliInterceptor，否则响应体无法解压。
            .addInterceptor(BrotliInterceptor)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request()
                val host = req.url.host.lowercase()
                val b = req.newBuilder()
                val online = host.contains("online.uestc.edu.cn")
                val mobileEams = host.contains("eamsapp.uestc.edu.cn")
                val eams = host.contains("eams.uestc.edu.cn")

                fun setIfAbsent(name: String, value: String) {
                    if (req.header(name).isNullOrBlank()) b.header(name, value)
                }

                when {
                    mobileEams -> {
                        setIfAbsent("User-Agent", CLIENT_USER_AGENT_EAMS)
                        setIfAbsent("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        // 移动教务：不设 Accept-Encoding，由 OkHttp 透明解压
                        setIfAbsent("sec-ch-ua", EAMS_SEC_CH_UA)
                        setIfAbsent("sec-ch-ua-mobile", "?0")
                        setIfAbsent("sec-ch-ua-platform", "\"Windows\"")
                        if (req.header("Accept").isNullOrBlank()) {
                            setIfAbsent("Accept", "*/*")
                        }
                        if (req.header("Referer").isNullOrBlank()) {
                            b.header("Referer", "$EAMSAPP_ORIGIN/")
                        }
                    }
                    eams || online -> {
                        setIfAbsent("User-Agent", CLIENT_USER_AGENT_EAMS)
                        setIfAbsent("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        setIfAbsent("Accept-Encoding", "gzip, deflate, br")
                        setIfAbsent("sec-ch-ua", EAMS_SEC_CH_UA)
                        setIfAbsent("sec-ch-ua-mobile", "?0")
                        setIfAbsent("sec-ch-ua-platform", "\"Windows\"")
                        if (req.header("Accept").isNullOrBlank()) {
                            setIfAbsent(
                                "Accept",
                                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                            )
                        }
                        if (req.header("Referer").isNullOrBlank()) {
                            b.header(
                                "Referer",
                                if (online) "$ONLINE_ORIGIN/" else EAMS_HOME_URL,
                            )
                        }
                    }
                    else -> {
                        setIfAbsent("User-Agent", CLIENT_USER_AGENT_IDAS)
                        setIfAbsent(
                            "Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                        )
                        setIfAbsent("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        setIfAbsent("Upgrade-Insecure-Requests", "1")
                        setIfAbsent("Referer", "$CAS_BASE_URL/login")
                    }
                }
                chain.proceed(b.build())
            }
            .build()
}
