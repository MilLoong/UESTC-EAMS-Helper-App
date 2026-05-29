package edu.uestc.eams.helper.domain.grade

import edu.uestc.eams.helper.domain.model.GradeItem

object GradeStatsCalculator {

    data class Stat(
        val value: Double?,
        val courseCount: Int,
        val creditSum: Double,
    )

    fun stableKey(item: GradeItem): String =
        "${item.courseCode}|${item.semester}|${item.courseName}"

    fun averageScore(items: List<GradeItem>, selectedKeys: Set<String>): Stat =
        weightedMean(
            items = items,
            selectedKeys = selectedKeys,
            valueOf = { parseScoreValue(it.score) },
        )

    fun averageGpa(items: List<GradeItem>, selectedKeys: Set<String>): Stat =
        weightedMean(
            items = items,
            selectedKeys = selectedKeys,
            valueOf = { parseNumber(it.gradePoint) },
        )

    private fun weightedMean(
        items: List<GradeItem>,
        selectedKeys: Set<String>,
        valueOf: (GradeItem) -> Double?,
    ): Stat {
        var weighted = 0.0
        var credits = 0.0
        var count = 0
        for (item in items) {
            if (stableKey(item) !in selectedKeys) continue
            val value = valueOf(item) ?: continue
            val credit = parseNumber(item.credit) ?: continue
            if (credit <= 0.0) continue
            weighted += value * credit
            credits += credit
            count++
        }
        return Stat(
            value = if (credits > 0.0) weighted / credits else null,
            courseCount = count,
            creditSum = credits,
        )
    }

    fun parseScoreValue(raw: String): Double? {
        parseNumber(raw)?.let { return it }
        return when (raw.trim().uppercase()) {
            "P" -> 85.0
            else -> null
        }
    }

    fun parseNumber(raw: String): Double? {
        val s = raw.trim()
        if (s.isEmpty() || s == "-") return null
        s.toDoubleOrNull()?.let { return it }
        if (s.endsWith("%")) return s.removeSuffix("%").trim().toDoubleOrNull()
        return null
    }
}
