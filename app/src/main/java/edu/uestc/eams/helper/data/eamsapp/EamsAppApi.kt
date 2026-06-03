package edu.uestc.eams.helper.data.eamsapp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import edu.uestc.eams.helper.data.network.ApiConstants
import edu.uestc.eams.helper.domain.model.UserProfile
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import kotlin.text.Charsets

/** 移动教务 REST 接口封装。 */
class EamsAppApi(private val client: OkHttpClient) {

    data class ProbeResult(
        val httpCode: Int,
        val ok: Boolean,
        val authFailed: Boolean,
        val contentEncoding: String,
        val bodyPreview: String,
    )

    fun probeSession(cookieHeader: String): Boolean = probeSessionDetail(cookieHeader).ok

    fun probeSessionDetail(cookieHeader: String): ProbeResult {
        val url = "${ApiConstants.EAMSAPP_ORIGIN}/api/ydzc-app/semester/getCurSemester"
        val (rsp, body) = get(url, cookieHeader)
        val authFailed = BladeJson.responseAuthFailed(rsp, body)
        val ok = BladeJson.responseOk(rsp, body) && !authFailed
        return ProbeResult(
            httpCode = rsp.code,
            ok = ok,
            authFailed = authFailed,
            contentEncoding = rsp.header("Content-Encoding").orEmpty(),
            bodyPreview = body.trim().take(200),
        )
    }

    fun fetchCurSemesterCode(cookieHeader: String): String? {
        val url = "${ApiConstants.EAMSAPP_ORIGIN}/api/ydzc-app/semester/getCurSemester"
        val (_, body) = get(url, cookieHeader)
        val root = BladeJson.parseApiBody(body) ?: return null
        return BladeJson.firstSemesterCode(BladeJson.unwrapRoot(root))
    }

    fun fetchCurWeek(cookieHeader: String, semesterCode: String): Int? {
        val q = URLEncoder.encode(semesterCode, Charsets.UTF_8.name())
        val url = "${ApiConstants.EAMSAPP_ORIGIN}/api/ydzc-app/semester/getCurWeek?code=$q"
        val (_, body) = get(url, cookieHeader)
        val root = BladeJson.parseApiBody(body) ?: return null
        return BladeJson.parseCurWeek(BladeJson.unwrapRoot(root))
    }

    fun fetchWeekTimetableJson(
        cookieHeader: String,
        semester: String,
        studentCode: String,
        week: String,
    ): JsonElement? {
        val url =
            ApiConstants.EAMSAPP_ORIGIN.toHttpUrl().newBuilder()
                .encodedPath("/api/ydzc-app/studentCourseTable/week")
                .addQueryParameter("semester", semester)
                .addQueryParameter("code", studentCode)
                .addQueryParameter("week", week)
                .build()
        return fetchApiDataJson(url.toString(), cookieHeader, "课表")
    }

    fun fetchGradesJson(cookieHeader: String, studentCode: String): JsonElement? {
        val url =
            ApiConstants.EAMSAPP_ORIGIN.toHttpUrl().newBuilder()
                .encodedPath("/api/ydzc-app/grade/student")
                .addQueryParameter("code", studentCode)
                .addQueryParameter("gradeType", "1")
                .build()
        return fetchApiDataJson(url.toString(), cookieHeader, "成绩")
    }

    fun fetchExamQueryJson(cookieHeader: String, semester: String, examTypeId: String = "1"): JsonElement? {
        val url =
            ApiConstants.EAMSAPP_ORIGIN.toHttpUrl().newBuilder()
                .encodedPath("/api/ydzc-app/examTake/query")
                .addQueryParameter("semester", semester)
                .addQueryParameter("examTypeId", examTypeId)
                .build()
        return fetchApiDataJson(url.toString(), cookieHeader, "考试")
    }

    fun resolveStudentCode(cookieHeader: String): String {
        profileFromJwt(cookieHeader)?.studentId?.let { return it }
        throw IllegalStateException("无法从登录令牌解析学号，请重新登录。")
    }

    fun resolveBladeUserId(cookieHeader: String): String? =
        profileFromJwt(cookieHeader)?.bladeUserId

    /** 拉取用户姓名等资料。 */
    fun fetchAppProfile(cookieHeader: String, bladeUserId: String): UserProfile? {
        val q = URLEncoder.encode(bladeUserId, Charsets.UTF_8.name())
        val url = "${ApiConstants.EAMSAPP_ORIGIN}/api/blade-user/appInfo?userId=$q"
        val (rsp, body) = get(url, cookieHeader)
        if (!BladeJson.responseOk(rsp, body) || BladeJson.responseAuthFailed(rsp, body)) {
            return null
        }
        val root = BladeJson.parseApiBody(body) ?: return null
        val jwtBase = profileFromJwt(cookieHeader)
        return parseAppInfoProfile(BladeJson.unwrapRoot(root), jwtBase)
    }

    fun profileFromJwt(cookieHeader: String): UserProfile? {
        val jwt = EamsAppCookie.parseHeaderValue(cookieHeader, "JSESSIONID").orEmpty()
        if (!EamsAppCookie.looksLikeJwt(jwt)) return null
        val claims = BladeJson.jwtClaims(jwt)
        val studentId =
            claims.get("user_name")?.asString?.trim()
                ?: claims.get("account")?.asString?.trim()
                ?: return null
        if (studentId.isEmpty()) return null
        val bladeUserId = claims.get("user_id")?.asString?.trim()?.takeIf { it.isNotEmpty() }
        return UserProfile(
            studentId = studentId,
            displayName = null,
            bladeUserId = bladeUserId,
        )
    }

    private fun parseAppInfoProfile(data: JsonElement?, jwtBase: UserProfile?): UserProfile? {
        val base = jwtBase ?: return null
        if (data == null || !data.isJsonObject) return base
        val obj = data.asJsonObject
        val name =
            firstNonBlankString(
                obj,
                "realName",
                "name",
                "nickName",
                "xm",
                "studentName",
                "userName",
            )?.takeUnless { looksLikeStudentId(it) }
        val studentId =
            firstNonBlankString(
                obj,
                "account",
                "code",
                "studentNo",
                "studentId",
                "userName",
            ) ?: base.studentId
        val bladeId =
            firstNonBlankString(obj, "id", "userId") ?: base.bladeUserId
        return UserProfile(
            studentId = studentId,
            displayName = name,
            bladeUserId = bladeId,
        )
    }

    private fun firstNonBlankString(obj: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val v = obj.get(k)?.takeIf { !it.isJsonNull }?.asString?.trim()
            if (!v.isNullOrEmpty()) return v
        }
        return null
    }

    private fun looksLikeStudentId(s: String): Boolean =
        s.length in 8..20 && s.all { it.isDigit() }

    private fun fetchApiDataJson(url: String, cookieHeader: String, label: String): JsonElement? {
        val (rsp, body) = get(url, cookieHeader)
        if (BladeJson.responseAuthFailed(rsp, body)) {
            throw IllegalStateException("$label 接口会话失效，请重新登录。")
        }
        if (!BladeJson.responseOk(rsp, body)) {
            throw IllegalStateException(
                "$label 接口异常（HTTP ${rsp.code}）：${apiBodyHint(body)}",
            )
        }
        val root = BladeJson.parseApiBody(body)
            ?: throw IllegalStateException("$label 接口返回非 JSON（HTTP ${rsp.code}）")
        if (body.contains("\"success\":false")) {
            val msg = BladeJson.apiErrorMessage(root) ?: "$label 请求被拒绝"
            throw IllegalStateException(msg)
        }
        return BladeJson.unwrapRoot(root)
    }

    private fun apiBodyHint(body: String): String {
        val t = body.trim()
        return when {
            t.isEmpty() -> "响应体为空"
            t.startsWith("<") -> "返回了 HTML 页面"
            else -> t.take(160)
        }
    }

    private fun get(url: String, cookieHeader: String): Pair<okhttp3.Response, String> {
        val req = Request.Builder().url(url).get()
        mobileApiHeaders(cookieHeader).forEach { (k, v) -> req.header(k, v) }
        val rsp = client.newCall(req.build()).execute()
        val body = rsp.readTextAutoDecompress()
        return rsp to body
    }
}
