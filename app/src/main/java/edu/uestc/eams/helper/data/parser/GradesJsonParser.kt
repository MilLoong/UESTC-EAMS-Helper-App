package edu.uestc.eams.helper.data.parser

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import edu.uestc.eams.helper.domain.model.GradeItem
import edu.uestc.eams.helper.domain.model.GradesSummary

object GradesJsonParser {

    fun parse(root: JsonElement?): GradesSummary {
        if (root == null) return GradesSummary(emptyList())
        val items = mutableListOf<GradeItem>()
        walk(root, items)
        val distinct =
            items.distinctBy { "${it.courseCode}|${it.semester}|${it.courseName}" }
        val gpa = findScalar(root, "gpa", "jd", "gradePointAvg", "avgGradePoint")
        val credits = findScalar(root, "totalCredit", "totalCredits", "zxf", "creditSum")
        return GradesSummary(distinct, gpa, credits)
    }

    private fun walk(node: JsonElement?, out: MutableList<GradeItem>) {
        when {
            node == null || node.isJsonNull -> Unit
            node.isJsonArray -> node.asJsonArray.forEach { walk(it, out) }
            node.isJsonObject -> {
                val o = node.asJsonObject
                if (looksLikeGrade(o)) out += toGrade(o)
                o.entrySet().forEach { (_, v) -> walk(v, out) }
            }
        }
    }

    private fun looksLikeGrade(o: JsonObject): Boolean {
        val keys = o.keySet().map { it.lowercase() }
        return keys.any { it.contains("coursename") || it == "kcmc" } &&
            (keys.any { it.contains("score") || it == "scoretext" } ||
                keys.any { it.contains("gp") })
    }

    private fun toGrade(o: JsonObject): GradeItem {
        val name = pick(o, "courseName", "course_name", "kcmc", "name") ?: "（课程）"
        val score =
            pick(o, "scoreText", "score_text", "score", "grade", "cj", "finalGrade") ?: "-"
        val credit = pick(o, "credit", "credits", "xf") ?: "-"
        val gp = pick(o, "gradePoint", "gp", "jd", "gpa", "point") ?: "-"
        val type = pick(o, "courseTypeName", "courseType", "typeName", "kclb") ?: ""
        val semester = pick(o, "semester", "xnxq", "semesterCode") ?: ""
        val examMode = pick(o, "examMode", "ksfs") ?: ""
        val necessary = pick(o, "necessary", "kcxz") ?: ""
        val code = pick(o, "courseCode", "course_code", "kch") ?: ""
        val passedRaw = pick(o, "passed", "isPassed", "tg")
        val passed =
            when (passedRaw?.lowercase()) {
                "true", "1", "是", "通过" -> true
                "false", "0", "否" -> false
                else -> null
            }
        return GradeItem(
            courseName = name,
            score = score,
            credit = credit,
            gradePoint = gp,
            courseType = type,
            semester = semester,
            examMode = examMode,
            necessary = necessary,
            courseCode = code,
            passed = passed,
        )
    }

    private fun pick(o: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val el = o.get(k) ?: continue
            if (el.isJsonNull) continue
            if (el.isJsonPrimitive) {
                val p = el.asJsonPrimitive
                when {
                    p.isNumber -> return p.asNumber.toString()
                    p.isString -> {
                        val s = p.asString.trim()
                        if (s.isNotEmpty()) return s
                    }
                    p.isBoolean -> return p.asBoolean.toString()
                }
            }
        }
        return null
    }

    private fun findScalar(root: JsonElement?, vararg keys: String): String? {
        if (root == null) return null
        if (root.isJsonObject) {
            val o = root.asJsonObject
            for (k in keys) pick(o, k)?.let { return it }
            o.entrySet().forEach { (_, v) -> findScalar(v, *keys)?.let { return it } }
        } else if (root.isJsonArray) {
            root.asJsonArray.forEach { findScalar(it, *keys)?.let { return it } }
        }
        return null
    }
}
