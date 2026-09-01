package edu.uestc.eams.helper.domain.model

/** 教务学期编码归一化：兼容「25262」与「2025-2026-2」等写法。 */
object SemesterCodes {

    fun label(code: String): String {
        val t = code.trim()
        parts(t)?.let { (start, end, term) ->
            return "$start-$end 第${term}学期"
        }
        return t
    }

    fun canonicalKey(code: String): String {
        val t = code.trim()
        if (t.isEmpty()) return t
        parts(t)?.let { (start, end, term) ->
            return "$start-$end-$term"
        }
        return t
    }

    fun same(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (a.trim() == b.trim()) return true
        return canonicalKey(a) == canonicalKey(b)
    }

    private fun parts(code: String): Triple<Int, Int, Int>? {
        val t = code.trim()
        if (t.length == 5 && t.all { it.isDigit() }) {
            val y1 = t.substring(0, 2).toIntOrNull() ?: return null
            val y2 = t.substring(2, 4).toIntOrNull() ?: return null
            val s = t.substring(4).toIntOrNull() ?: return null
            if (s !in 1..3) return null
            return Triple(2000 + y1, 2000 + y2, s)
        }
        val m =
            Regex("""(20\d{2})\s*[-–/]\s*(20\d{2})\s*(?:学年)?\s*[-–/]?\s*(?:第\s*)?([123])\s*学期?""")
                .find(t)
                ?: Regex("""(20\d{2})\s*[-–/]\s*(20\d{2})\s*[-–/]\s*([123])""").find(t)
                ?: return null
        val start = m.groupValues[1].toIntOrNull() ?: return null
        val end = m.groupValues[2].toIntOrNull() ?: return null
        val term = m.groupValues[3].toIntOrNull() ?: return null
        return Triple(start, end, term)
    }
}
