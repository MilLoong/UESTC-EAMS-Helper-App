package edu.uestc.eams.helper.data.parser

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import edu.uestc.eams.helper.domain.model.ExamItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
object ExamJsonParser {

    private val dateTimePatterns =
        listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        )
    private val dateOnly = DateTimeFormatter.ISO_LOCAL_DATE

    fun parse(root: JsonElement?): List<ExamItem> {
        if (root == null) return emptyList()
        val out = mutableListOf<ExamItem>()
        walk(root, out)
        return out.distinctBy { "${it.courseName}|${it.examTimeText}|${it.room}" }
    }

    private fun walk(node: JsonElement?, out: MutableList<ExamItem>) {
        when {
            node == null || node.isJsonNull -> Unit
            node.isJsonArray -> node.asJsonArray.forEach { walk(it, out) }
            node.isJsonObject -> {
                val o = node.asJsonObject
                if (looksLikeExam(o)) out += toExam(o)
                o.entrySet().forEach { (_, v) -> walk(v, out) }
            }
        }
    }

    private fun looksLikeExam(o: JsonObject): Boolean {
        val keys = o.keySet().map { it.lowercase() }
        return keys.any { it.contains("examdate") || it.contains("examplace") || it.contains("examarrange") } ||
            (keys.any { it.contains("coursename") || it.contains("kcmc") } &&
                keys.any { it.contains("exam") || it.contains("date") || it.contains("place") })
    }

    private fun toExam(o: JsonObject): ExamItem {
        val name = pick(o, "courseName", "course_name", "kcmc", "name", "title") ?: "（考试）"
        val date = pick(o, "examDate", "exam_date", "date") ?: ""
        val arrange = pick(o, "examArrange", "exam_arrange", "examTime", "time", "kssj") ?: ""
        val time =
            when {
                date.isNotEmpty() && arrange.isNotEmpty() -> "$date $arrange"
                date.isNotEmpty() -> date
                arrange.isNotEmpty() -> arrange
                else -> pick(o, "examTime", "startTime", "dateTime") ?: "待定"
            }
        val room = pick(o, "examPlace", "exam_place", "room", "address", "examRoom", "cdmc", "ksdd") ?: "待定"
        val seat = pick(o, "examSeat", "exam_seat", "seat", "seatNumber", "zwh", "seatNo") ?: "-"
        val type = pick(o, "examType", "exam_type", "typeName", "kslb") ?: ""
        return ExamItem(
            courseName = name,
            examTimeText = time,
            room = room,
            seat = seat,
            examType = type,
            startEpochMillis = parseTimeMillis(date, arrange, time),
        )
    }

    private fun pick(o: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val el = o.get(k) ?: continue
            if (el.isJsonNull) continue
            if (el.isJsonPrimitive) {
                val s = el.asString.trim()
                if (s.isNotEmpty()) return s
                if (el.asJsonPrimitive.isNumber) return el.asJsonPrimitive.asNumber.toString()
            }
        }
        return null
    }

    private fun parseTimeMillis(date: String, arrange: String, combined: String): Long? {
        val candidates = mutableListOf<String>()
        if (date.isNotEmpty() && arrange.isNotEmpty()) {
            candidates += "$date ${arrange.substringBefore('-').trim()}"
            candidates += "$date $arrange"
        }
        candidates += combined
        candidates += date
        for (c in candidates) {
            val t = c.trim()
            if (t.isEmpty()) continue
            for (fmt in dateTimePatterns) {
                try {
                    val ldt = LocalDateTime.parse(t, fmt)
                    return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (_: Exception) {
                }
            }
            try {
                val d = LocalDate.parse(t.take(10), dateOnly)
                return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {
            }
        }
        return null
    }
}
