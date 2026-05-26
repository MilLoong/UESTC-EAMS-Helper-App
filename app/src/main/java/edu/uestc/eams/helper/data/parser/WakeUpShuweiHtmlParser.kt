package edu.uestc.eams.helper.data.parser

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import edu.uestc.eams.helper.data.mapper.UestcPeriodTime
import edu.uestc.eams.helper.domain.model.UestcCourse

/** 解析 WakeUp 树维教务导出的课表 HTML/JSON。 */
object WakeUpShuweiHtmlParser {

    data class ParseResult(
        val courses: List<UestcCourse>,
        val maxWeek: Int,
        val year: Int?,
    )

    fun parse(fileText: String): ParseResult {
        val json = extractJsonPayload(fileText)
        val root =
            JsonParser.parseString(json).asJsonObject
                ?: throw IllegalArgumentException("课表文件不是有效的 JSON")
        val unitCount = resolveUnitCount(root)
        val activities =
            root.getAsJsonArray("activities")
                ?: throw IllegalArgumentException("缺少 activities 字段")
        val year = root.get("year")?.takeIf { it.isJsonPrimitive }?.asInt

        val raw = mutableListOf<UestcCourse>()
        for (i in 0 until activities.size()) {
            val cell = activities.get(i)
            if (!cell.isJsonArray) continue
            val weekday = i / unitCount + 1
            val period = i % unitCount + 1
            if (weekday !in 1..7 || period !in 1..unitCount) continue
            val arr = cell.asJsonArray
            for (j in 0 until arr.size()) {
                val obj = arr.get(j)
                if (!obj.isJsonObject) continue
                raw += activityToCourse(obj.asJsonObject, weekday, period)
            }
        }

        val merged = AdjacentCourseMerge.merge(raw)
        val maxWeek =
            merged.maxOfOrNull { WeekSpec.maxWeekNumber(it.weeks) }
                ?.coerceAtLeast(1) ?: 1
        return ParseResult(merged, maxWeek, year)
    }

    private fun extractJsonPayload(text: String): String {
        val trimmed = text.trim().removePrefix("\uFEFF")
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw IllegalArgumentException("文件中未找到 JSON 课表数据")
        }
        return trimmed.substring(start, end + 1)
    }

    private fun resolveUnitCount(root: JsonObject): Int {
        root.get("unitCount")?.asInt?.takeIf { it in 1..20 }?.let { return it }
        val total = root.get("unitCounts")?.asInt
        if (total != null && total >= 7) {
            val perDay = total / 7
            if (perDay in 1..20) return perDay
        }
        return UestcPeriodTime.maxPeriod
    }

    private fun activityToCourse(
        obj: JsonObject,
        weekday: Int,
        period: Int,
    ): UestcCourse {
        val name = obj.string("courseName")
        if (name.isBlank()) throw IllegalArgumentException("课程名为空")
        val bits = obj.string("vaildWeeks").ifBlank { obj.string("validWeeks") }
        val weeks = ValidWeeksBinary.toWeekSpec(bits)
        val startSlot = UestcPeriodTime.slots.getOrNull(period - 1)
        val endSlot = UestcPeriodTime.slots.getOrNull(period - 1)
        return UestcCourse(
            courseName = name,
            teacher = obj.string("teacherName"),
            room = obj.string("roomName"),
            weekday = weekday,
            period = period,
            endPeriod = period,
            weeks = weeks,
            courseId = obj.string("courseId"),
            startTime = startSlot?.start.orEmpty(),
            endTime = endSlot?.end.orEmpty(),
        )
    }

    private fun JsonObject.string(key: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
}
