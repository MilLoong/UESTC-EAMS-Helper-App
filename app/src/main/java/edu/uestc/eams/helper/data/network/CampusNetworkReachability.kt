package edu.uestc.eams.helper.data.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 判断当前网络是否像在校内网：探测统一认证 idas，无需已登录移动教务。
 * eamsapp 需有效会话才有正常响应，不适合用来判断校外/校内。
 */
object CampusNetworkReachability {

    private val probeClient: OkHttpClient by lazy {
        ApiConstants.buildOkHttp(InMemoryCookieJar()).newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun canReachCampusAuth(): Boolean =
        try {
            val req =
                Request.Builder()
                    .url("${ApiConstants.CAS_BASE_URL}/login")
                    .header("User-Agent", ApiConstants.CLIENT_USER_AGENT_IDAS)
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
