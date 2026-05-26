package edu.uestc.eams.helper.data.parser

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import edu.uestc.eams.helper.domain.model.UestcCourse

/**
 * 解析移动教务返回的周课表 JSON。
 */
object TimetableJsonParser {

    fun parse(root: JsonElement?): List<UestcCourse> {
        if (root == null) return emptyList()
        val raw = mutableListOf<UestcCourse>()
        walk(root, raw)
        return raw.distinctBy { "${it.lessonNo}|${it.weekday}|${it.period}|${it.endPeriod}|${it.room}|${it.teacher}" }
    }

    private fun walk(node: JsonElement?, out: MutableList<UestcCourse>) {
        when {
            node == null || node.isJsonNull -> Unit
            node.isJsonArray -> node.asJsonArray.forEach { walk(it, out) }
            node.isJsonObject -> {
                val obj = node.asJsonObject
                if (looksLikeCourse(obj)) out += toCourse(obj)
                obj.entrySet().forEach { (_, v) -> walk(v, out) }
            }
        }
    }

    private fun looksLikeCourse(obj: JsonObject): Boolean {
        val keys = obj.keySet().map { it.lowercase() }
        val hasName = keys.any { it == "coursename" || it == "kcmc" }
        val hasSlot =
            keys.any { it == "startunit" || it == "weekday" || it == "starttime" }
        return hasName && hasSlot
    }

    private fun toCourse(obj: JsonObject): UestcCourse {
        val name = pickString(obj, "courseName", "course_name", "kcmc") ?: "（课程）"
        val teacher = pickString(obj, "teacherName", "teacher", "jsxm") ?: ""
        val room = pickString(obj, "roomName", "room", "cdmc", "classroom") ?: ""
        val weekday = pickInt(obj, "weekDay", "weekday", "dayOfWeek", "xq")?.coerceIn(1, 7) ?: 1
        val startUnit = pickInt(obj, "startUnit", "startPeriod", "period", "ksjc") ?: 1
        val endUnit = pickInt(obj, "endUnit", "endPeriod", "jsjc") ?: startUnit
        val weeks = pickString(obj, "week", "weeks", "weeksStr", "zcd") ?: ""
        val lessonNo = pickString(obj, "no", "lessonNo", "lesson_no") ?: ""
        val courseCode = pickString(obj, "courseCode", "course_code", "kch") ?: ""
        val courseType = pickString(obj, "courseType", "course_type", "kclb") ?: ""
        val startTime = pickString(obj, "startTime", "start_time") ?: ""
        val endTime = pickString(obj, "endTime", "end_time") ?: ""
        return UestcCourse(
            courseName = name.take(200),
            teacher = teacher,
            room = room,
            weekday = weekday,
            period = startUnit.coerceAtLeast(1),
            endPeriod = maxOf(startUnit, endUnit).coerceAtLeast(1),
            weeks = weeks,
            courseId = courseCode,
            lessonNo = lessonNo,
            courseType = courseType,
            startTime = startTime,
            endTime = endTime,
        )
    }

    private fun pickString(obj: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val el = obj.get(k) ?: continue
            if (el.isJsonNull) continue
            if (el.isJsonPrimitive) {
                val s = el.asString.trim()
                if (s.isNotEmpty()) return s
            }
        }
        return null
    }

    private fun pickInt(obj: JsonObject, vararg keys: String): Int? {
        for (k in keys) {
            val el = obj.get(k) ?: continue
            if (el.isJsonPrimitive) {
                el.asString.trim().toIntOrNull()?.let { return it }
                if (el.asJsonPrimitive.isNumber) return el.asInt
            }
        }
        return null
    }
}
