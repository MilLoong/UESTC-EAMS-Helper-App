package edu.uestc.eams.helper.data.parser

/** 将 vaildWeeks 二进制串转为周次范围文本。 */
object ValidWeeksBinary {

    fun toWeekSpec(bits: String): String {
        val b = bits.trim()
        if (b.isEmpty()) return ""
        val first = b.indexOfFirst { it == '1' }
        if (first < 0) return ""
        val last = b.indexOfLast { it == '1' }
        val ranges = mutableListOf<String>()
        var rangeStart: Int? = null
        for (i in first..last) {
            val on = b[i] == '1'
            val weekNum = i - first + 1
            if (on && rangeStart == null) rangeStart = weekNum
            if (!on && rangeStart != null) {
                ranges.add(formatRange(rangeStart, weekNum - 1))
                rangeStart = null
            }
        }
        if (rangeStart != null) {
            ranges.add(formatRange(rangeStart, last - first + 1))
        }
        return ranges.joinToString(",")
    }

    fun maxWeek(bits: String): Int {
        val b = bits.trim()
        val first = b.indexOfFirst { it == '1' }
        if (first < 0) return 0
        val last = b.indexOfLast { it == '1' }
        return last - first + 1
    }

    private fun formatRange(start: Int, end: Int): String =
        if (start == end) "$start" else "$start-$end"
}
