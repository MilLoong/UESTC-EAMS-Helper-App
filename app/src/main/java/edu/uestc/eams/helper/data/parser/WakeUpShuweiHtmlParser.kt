package edu.uestc.eams.helper.data.parser

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import edu.uestc.eams.helper.data.mapper.UestcPeriodTime
import edu.uestc.eams.helper.domain.model.UestcCourse
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 解析 WakeUp 树维教务导出的课表 HTML/JSON。 */
object WakeUpShuweiHtmlParser {

    data class ParseResult(
        val courses: List<UestcCourse>,
        val maxWeek: Int,
        val year: Int?,
        val weekOneMonday: LocalDate?,
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
        val year = root.intField("year")

        val raw = mutableListOf<UestcCourse>()
        for (i in 0 until activities.size()) {
            val cell = activities.get(i)
            if (!cell.isJsonArray) continue
            val weekday = i / unitCount + 1
            val gridPeriod = i % unitCount + 1
            if (weekday !in 1..7 || gridPeriod !in 1..unitCount) continue
            val arr = cell.asJsonArray
            for (j in 0 until arr.size()) {
                val obj = arr.get(j)
                if (!obj.isJsonObject) continue
                raw += activityToCourse(obj.asJsonObject, weekday, gridPeriod)
            }
        }

        val merged = AdjacentCourseMerge.merge(raw)
        val maxWeek =
            merged.maxOfOrNull { WeekSpec.maxWeekNumber(it.weeks) }
                ?.coerceAtLeast(1) ?: 1
        val weekOneMonday = parseWeekOneMonday(root, year)
        return ParseResult(merged, maxWeek, year, weekOneMonday)
    }

    private fun parseWeekOneMonday(root: JsonObject, year: Int?): LocalDate? {
        parseDateText(root.string("beginDate"))?.let { return it }
        parseDateText(root.string("startDate"))?.let { return it }
        parseDateText(root.string("firstDate"))?.let { return it }
        val y = year ?: root.intField("year") ?: return null
        val month =
            root.intField("month")
                ?: root.intField("beginMonth")
                ?: root.intField("startMonth")
        val day =
            root.intField("day")
                ?: root.intField("beginDay")
                ?: root.intField("startDay")
        if (month != null && day != null) {
            val begin = runCatching { LocalDate.of(y, month, day) }.getOrNull() ?: return null
            return begin.with(DayOfWeek.MONDAY)
        }
        return null
    }

    /** 第 1 教学周内最早有课的一天；须已知第 1 教学周周一。 */
    fun suggestFirstClassDayInWeekOne(
        courses: List<UestcCourse>,
        weekOneMonday: LocalDate?,
    ): LocalDate? {
        val anchor = weekOneMonday ?: return null
        val week1 = CourseWeekFilter.filterForWeek(courses, 1)
        if (week1.isEmpty()) return null
        val minWeekday = week1.minOf { it.weekday }
        return anchor.plusDays((minWeekday - 1).toLong())
    }

    private fun parseDateText(text: String): LocalDate? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val patterns =
            listOf(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy/M/d"),
                DateTimeFormatter.ofPattern("yyyy/M/dd"),
            )
        for (fmt in patterns) {
            val d = runCatching { LocalDate.parse(t, fmt) }.getOrNull()
            if (d != null) return d.with(DayOfWeek.MONDAY)
        }
        return null
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
        root.intField("unitCount")?.takeIf { it in 1..20 }?.let { return it }
        val total = root.intField("unitCounts")
        if (total != null && total >= 7) {
            val perDay = total / 7
            if (perDay in 1..20) return perDay
        }
        return UestcPeriodTime.maxPeriod
    }

    private fun activityToCourse(
        obj: JsonObject,
        weekday: Int,
        gridPeriod: Int,
    ): UestcCourse {
        val name = obj.string("courseName")
        if (name.isBlank()) throw IllegalArgumentException("课程名为空")
        val bits = obj.string("vaildWeeks").ifBlank { obj.string("validWeeks") }
        val weeks = ValidWeeksBinary.toWeekSpec(bits)
        val startUnit =
            obj.intField("startUnit")
                ?: obj.intField("startunit")
                ?: gridPeriod
        val endUnit =
            obj.intField("endUnit")
                ?: obj.intField("endunit")
                ?: startUnit
        val period = startUnit.coerceAtLeast(1)
        val endPeriod = maxOf(startUnit, endUnit).coerceAtLeast(period)
        val startSlot = UestcPeriodTime.slots.getOrNull(period - 1)
        val endSlot = UestcPeriodTime.slots.getOrNull(endPeriod - 1)
        return UestcCourse(
            courseName = name,
            teacher = obj.string("teacherName"),
            room = obj.string("roomName"),
            weekday = weekday,
            period = period,
            endPeriod = endPeriod,
            weeks = weeks,
            courseId = obj.string("courseId"),
            startTime = startSlot?.start.orEmpty(),
            endTime = endSlot?.end.orEmpty(),
        )
    }

    private fun JsonObject.string(key: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()

    private fun JsonObject.intField(vararg keys: String): Int? {
        for (key in keys) {
            val el = get(key) ?: continue
            if (!el.isJsonPrimitive) continue
            val n = el.asString.trim().toIntOrNull() ?: el.asInt
            if (n > 0) return n
        }
        return null
    }
}
