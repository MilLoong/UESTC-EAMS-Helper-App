package edu.uestc.eams.helper.data.parser

/** 解析周次范围字符串中的最大周次。 */
object WeekSpec {

    fun maxWeekNumber(spec: String): Int {
        val s = spec.trim()
        if (s.isEmpty()) return 1
        return s.split(",").maxOfOrNull { segment ->
            val part = segment.trim()
            if (part.contains("-")) {
                part.substringAfter("-").trim().toIntOrNull() ?: 0
            } else {
                part.toIntOrNull() ?: 0
            }
        }?.coerceAtLeast(1) ?: 1
    }
}
