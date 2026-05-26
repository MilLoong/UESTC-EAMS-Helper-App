package edu.uestc.eams.helper.data.parser

/** 将 vaildWeeks 二进制串转为周次范围文本。 */
object ValidWeeksBinary {

    fun toWeekSpec(bits: String): String {
        val b = bits.trim()
        if (b.isEmpty()) return ""
        val ranges = mutableListOf<String>()
        var rangeStart: Int? = null
        for (i in b.indices) {
            val on = b[i] == '1'
            if (on && rangeStart == null) rangeStart = i + 1
            if (!on && rangeStart != null) {
                val end = i
                ranges.add(formatRange(rangeStart, end))
                rangeStart = null
            }
        }
        if (rangeStart != null) {
            ranges.add(formatRange(rangeStart, b.length))
        }
        return ranges.joinToString(",")
    }

    fun maxWeek(bits: String): Int {
        val b = bits.trim()
        return b.length.coerceAtLeast(1)
    }

    private fun formatRange(start: Int, end: Int): String =
        if (start == end) "$start" else "$start-$end"
}
