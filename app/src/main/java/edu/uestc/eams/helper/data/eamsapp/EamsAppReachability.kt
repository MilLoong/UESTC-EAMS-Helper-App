package edu.uestc.eams.helper.data.eamsapp

import edu.uestc.eams.helper.data.network.ApiConstants
import edu.uestc.eams.helper.data.network.InMemoryCookieJar
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** 判断当前网络能否访问移动教务站点（不携带登录 Cookie）。 */
object EamsAppReachability {

    private val probeClient: OkHttpClient by lazy {
        ApiConstants.buildOkHttp(InMemoryCookieJar()).newBuilder()
      .connectTimeout(6, TimeUnit.SECONDS)
      .readTimeout(6, TimeUnit.SECONDS)
      .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    fun canReachEamsAppHost(): Boolean =
        try {
            val req =
                Request.Builder()
                    .url("${ApiConstants.EAMSAPP_ORIGIN}/")
                    .header("User-Agent", ApiConstants.CLIENT_USER_AGENT_EAMS)
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
