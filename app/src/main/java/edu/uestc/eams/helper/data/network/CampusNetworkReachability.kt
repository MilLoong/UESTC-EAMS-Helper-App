package edu.uestc.eams.helper.data.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 判断当前网络是否像在校内网：探测统一认证 idas，无需已登录移动教务。
 * eamsapp 需有效会话才有正常响应，不适合用来判断校外/校内。
 *
 * 须在后台线程调用（会发起同步 HTTP）。已登录时常为 **302** 跳教务，未登录为 **200** 登录页，
 * 任意 HTTP 状态码（100–599）均视为可达；仅连接/超时/SSL 等异常视为不可达。
 */
object CampusNetworkReachability {

    private val probeClient: OkHttpClient by lazy {
        ApiConstants.buildOkHttp(InMemoryCookieJar()).newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun canReachCampusAuth(): Boolean =
        try {
            val req =
                Request.Builder()
                    .url(ApiConstants.CAS_CAMPUS_PROBE_LOGIN_URL)
                    .header("User-Agent", ApiConstants.CLIENT_USER_AGENT_EAMS)
                    .header("sec-ch-ua", ApiConstants.EAMS_SEC_CH_UA)
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"Windows\"")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
                    )
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .get()
                    .build()
            probeClient.newCall(req).execute().use { rsp ->
                rsp.code in 100..599
            }
        } catch (_: IOException) {
            false
        } catch (_: Exception) {
            false
        }
}
