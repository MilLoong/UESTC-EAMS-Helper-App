package edu.uestc.eams.helper.data.eamsapp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Response

/** 解析移动教务 Blade 风格 JSON 响应。 */
object BladeJson {

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

    fun firstSemesterCode(element: JsonElement?, depth: Int = 0): String? {
        if (depth > 14) return null
        return when {
            element == null || element.isJsonNull -> null
            element.isJsonPrimitive -> {
                val s = element.asString.trim()
                if (s.all { it.isDigit() } && s.length in 5..6) s else null
            }
            element.isJsonObject -> {
                val keys = listOf(
                    "code", "semesterCode", "semester_code", "xnxq", "xnxqh",
                    "dqxnxqh", "dqXnxq", "semesterId",
                )
                for (k in keys) {
                    element.asJsonObject.get(k)?.let { hit ->
                        firstSemesterCode(hit, depth + 1)?.let { return it }
                    }
                }
                element.asJsonObject.entrySet().forEach { (_, v) ->
                    firstSemesterCode(v, depth + 1)?.let { return it }
                }
                null
            }
            element.isJsonArray -> {
                element.asJsonArray.forEach { child ->
                    firstSemesterCode(child, depth + 1)?.let { return it }
                }
                null
            }
            else -> null
        }
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
