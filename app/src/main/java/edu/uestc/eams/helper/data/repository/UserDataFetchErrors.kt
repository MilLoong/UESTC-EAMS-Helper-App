package edu.uestc.eams.helper.data.repository

import edu.uestc.eams.helper.data.network.CampusNetworkReachability
import edu.uestc.eams.helper.data.network.EamsFetchException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal object UserDataFetchErrors {

    suspend fun map(throwable: Throwable): Throwable {
        if (throwable is EamsFetchException) return throwable
        val auth = isLikelyAuthFailure(throwable)
        val network = isLikelyNetworkFailure(throwable)
        if (!auth && !network) return throwable

        val reachable =
            withContext(Dispatchers.IO) {
                CampusNetworkReachability.canReachCampusAuth()
            }
        if (auth) {
            return if (reachable) {
                EamsFetchException.SessionInvalid(throwable)
            } else {
                EamsFetchException.OffCampus(throwable)
            }
        }
        if (network && !reachable) {
            return EamsFetchException.OffCampus(throwable)
        }
        return throwable
    }

    private fun isLikelyAuthFailure(e: Throwable): Boolean {
        var cur: Throwable? = e
        while (cur != null) {
            val msg = cur.message?.lowercase().orEmpty()
            if (
                msg.contains("会话失效") ||
                    msg.contains("请重新登录") ||
                    msg.contains("未建立移动教务会话") ||
                    msg.contains("未登录") ||
                    msg.contains("未授权") ||
                    msg.contains("unauthorized") ||
                    msg.contains("token") && msg.contains("失效")
            ) {
                return true
            }
            cur = cur.cause
        }
        return false
    }

    private fun isLikelyNetworkFailure(e: Throwable): Boolean =
        when (e) {
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException,
            is SSLException,
            is IOException,
            -> true
            else -> {
                val msg = e.message?.lowercase().orEmpty()
                msg.contains("failed to connect") ||
                    msg.contains("unable to resolve host") ||
                    msg.contains("timeout") ||
                    msg.contains("connection reset")
            }
        }
}
