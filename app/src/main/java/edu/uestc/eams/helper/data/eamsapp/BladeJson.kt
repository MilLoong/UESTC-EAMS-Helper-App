package edu.uestc.eams.helper.data.eamsapp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import edu.uestc.eams.helper.domain.model.CurSemester
import okhttp3.Response
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 解析移动教务 Blade 风格 JSON 响应。 */
object BladeJson {

    private val startOnDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val startOnDate = DateTimeFormatter.ISO_LOCAL_DATE

    fun unwrapRoot(element: JsonElement?): JsonElement? {
        if (element == null || !element.isJsonObject) return element
        val obj = element.asJsonObject
        if (obj.has("data") && !obj.get("data").isJsonNull) return obj.get("data")
        if (obj.has("records")) return obj.get("records")
        if (obj.has("rows")) return obj.get("rows")
        return element
    }

    fun parseApiBody(text: String): JsonElement? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return try {
            JsonParser.parseString(trimmed)
        } catch (_: Exception) {
            null
        }
    }

    fun responseOk(response: Response, bodyText: String): Boolean {
        if (response.code in 200..299) {
            if (bodyText.trim().isEmpty()) return false
            val el = parseApiBody(bodyText) ?: return false
            if (!el.isJsonObject) return true
            val obj = el.asJsonObject
            if (obj.has("success")) {
                return obj.get("success").asBoolean
            }
            val code = obj.get("code")?.asString ?: obj.get("code")?.toString()
            return code in listOf("200", "0", "0.0") || obj.get("code")?.asInt in listOf(200, 0)
        }
        return false
    }

    fun apiErrorMessage(root: JsonElement?): String? {
        if (root == null || !root.isJsonObject) return null
        val obj = root.asJsonObject
        val msg =
            obj.get("msg")?.asString?.trim()
                ?: obj.get("message")?.asString?.trim()
        return msg?.takeIf { it.isNotEmpty() }
    }

    fun responseAuthFailed(response: Response, bodyText: String): Boolean {
        if (response.code in listOf(401, 403)) return true
        val el = parseApiBody(bodyText) ?: return false
        if (!el.isJsonObject) return false
        val obj = el.asJsonObject
        val msg = (obj.get("msg")?.asString ?: obj.get("message")?.asString ?: "").lowercase()
        if (
            listOf("登录", "token", "unauthorized", "未授权", "失效", "过期", "未登录")
                .any { msg.contains(it) }
        ) {
            return true
        }
        if (responseOk(response, bodyText)) return false
        val code = obj.get("code")?.asString ?: obj.get("code")?.toString()
        return code in listOf("401", "403", "10001")
    }

    fun firstSemesterCode(element: JsonElement?, depth: Int = 0): String? =
        parseCurSemester(element)?.code ?: firstSemesterCodeDeep(element, depth)

    /** 解析 getCurSemester：学期编码、startOn / endOn、总周数等。 */
    fun parseCurSemester(element: JsonElement?, depth: Int = 0): CurSemester? {
        if (depth > 14 || element == null || element.isJsonNull) return null
        when {
            element.isJsonObject -> {
                val o = element.asJsonObject
                val code = semesterCodeFromObject(o)
                if (code != null) {
                    return CurSemester(
                        code = code,
                        year = stringField(o, "year", "schoolYear", "xn"),
                        name = stringField(o, "name", "term", "xq"),
                        startOn = dateField(o, "startOn", "start_on", "beginDate", "startDate"),
                        endOn = dateField(o, "endOn", "end_on", "endDate"),
                        firstWeek = intField(o, "first", "firstWeek", "startWeek"),
                        weeks = intField(o, "weeks", "weekCount", "totalWeeks"),
                    )
                }
                o.entrySet().forEach { (_, v) ->
                    parseCurSemester(v, depth + 1)?.let { return it }
                }
            }
            element.isJsonArray -> {
                element.asJsonArray.forEach { child ->
                    parseCurSemester(child, depth + 1)?.let { return it }
                }
            }
        }
        return null
    }

    private fun firstSemesterCodeDeep(element: JsonElement?, depth: Int): String? {
        if (depth > 14) return null
        return when {
            element == null || element.isJsonNull -> null
            element.isJsonPrimitive -> {
                val s = element.asString.trim()
                if (s.all { it.isDigit() } && s.length in 5..6) s else null
            }
            element.isJsonObject -> {
                val keys =
                    listOf(
                        "code", "semesterCode", "semester_code", "xnxq", "xnxqh",
                        "dqxnxqh", "dqXnxq", "semesterId",
                    )
                for (k in keys) {
                    element.asJsonObject.get(k)?.let { hit ->
                        firstSemesterCodeDeep(hit, depth + 1)?.let { return it }
                    }
                }
                element.asJsonObject.entrySet().forEach { (_, v) ->
                    firstSemesterCodeDeep(v, depth + 1)?.let { return it }
                }
                null
            }
            element.isJsonArray -> {
                element.asJsonArray.forEach { child ->
                    firstSemesterCodeDeep(child, depth + 1)?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    private fun semesterCodeFromObject(o: JsonObject): String? {
        for (k in listOf("code", "semesterCode", "semester_code", "xnxq", "xnxqh")) {
            val s = o.get(k)?.takeIf { !it.isJsonNull }?.asString?.trim() ?: continue
            if (s.all { it.isDigit() } && s.length in 5..6) return s
        }
        return null
    }

    private fun stringField(o: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val s = o.get(k)?.takeIf { !it.isJsonNull }?.asString?.trim()
            if (!s.isNullOrEmpty()) return s
        }
        return null
    }

    private fun intField(o: JsonObject, vararg keys: String): Int? {
        for (k in keys) {
            val el = o.get(k) ?: continue
            if (el.isJsonNull || !el.isJsonPrimitive) continue
            val n = el.asString.trim().toIntOrNull() ?: runCatching { el.asInt }.getOrNull()
            if (n != null) return n
        }
        return null
    }

    private fun dateField(o: JsonObject, vararg keys: String): LocalDate? {
        for (k in keys) {
            val raw = o.get(k)?.takeIf { !it.isJsonNull }?.asString?.trim() ?: continue
            parseFlexibleLocalDate(raw)?.let { return it }
        }
        return null
    }

    fun parseFlexibleLocalDate(raw: String): LocalDate? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        runCatching { LocalDate.parse(t.take(10), startOnDate) }.getOrNull()?.let { return it }
        runCatching { LocalDate.parse(t, startOnDateTime) }.getOrNull()?.let { return it }
        if (t.length >= 10 && t[4] == '-' && t[7] == '-') {
            return runCatching { LocalDate.parse(t.substring(0, 10)) }.getOrNull()
        }
        return null
    }

    /** 解析 getCurWeek 等接口的当前教学周；优先 curWeek，避免误读嵌套里的 week=1。 */
    fun parseCurWeek(element: JsonElement?, lo: Int = 1, hi: Int = 40): Int? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonPrimitive) {
            val n = element.asString.trim().toIntOrNull() ?: element.asInt
            return if (n in lo..hi) n else null
        }
        if (element.isJsonObject) {
            val o = element.asJsonObject
            for (k in listOf("curWeek", "currentWeek", "cur_week", "weekNum", "weekNo")) {
                o.get(k)?.let { v ->
                    weekIntFromPrimitive(v, lo, hi)?.let { return it }
                    parseCurWeek(v, lo, hi)?.let { return it }
                }
            }
            // 泛化 week 仅在本对象无 curWeek 时读取，且跳过 1（常为「第 1 周」占位而非当前周）
            o.get("week")?.let { v ->
                weekIntFromPrimitive(v, lo, hi)?.takeIf { it > 1 }?.let { return it }
            }
            o.entrySet().forEach { (_, v) ->
                parseCurWeek(v, lo, hi)?.let { return it }
            }
        }
        if (element.isJsonArray) {
            element.asJsonArray.forEach { child ->
                parseCurWeek(child, lo, hi)?.let { return it }
            }
        }
        return null
    }

    fun firstWeekInt(element: JsonElement?, lo: Int = 1, hi: Int = 40): Int? =
        parseCurWeek(element, lo, hi)

    private fun weekIntFromPrimitive(element: JsonElement?, lo: Int, hi: Int): Int? {
        if (element == null || element.isJsonNull || !element.isJsonPrimitive) return null
        val n = element.asString.trim().toIntOrNull() ?: element.asInt
        return if (n in lo..hi) n else null
    }

    fun jwtClaims(jwt: String): JsonObject {
        val parts = jwt.split('.')
        if (parts.size < 2) return JsonObject()
        var b = parts[1]
        val pad = "=".repeat((4 - b.length % 4) % 4)
        b += pad
        return try {
            val raw = String(android.util.Base64.decode(b, android.util.Base64.URL_SAFE))
            JsonParser.parseString(raw).asJsonObject
        } catch (_: Exception) {
            JsonObject()
        }
    }
}
