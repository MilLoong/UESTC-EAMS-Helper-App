package edu.uestc.eams.helper.data.parser

/** 将课表周次字段格式化为详情展示文案，如 1-5,7-16 → 第1-5周，第7-16周。 */
object WeekSpecDisplay {

    fun formatForUi(spec: String): String {
        val s = spec.trim()
        if (s.isEmpty()) return "未标注周次"
        return s.split(",")
            .mapNotNull { segment ->
                val part = segment.trim()
                if (part.isEmpty()) return@mapNotNull null
                if (part.contains("-")) {
                    val dash = part.indexOf('-')
                    val start = part.substring(0, dash).trim()
                    val end = part.substring(dash + 1).trim()
                    if (start.isEmpty() || end.isEmpty()) return@mapNotNull null
                    if (start == end) "第${start}周" else "第${start}-${end}周"
                } else {
                    "第${part}周"
                }
            }
            .joinToString("，")
            .ifBlank { "未标注周次" }
    }
}
