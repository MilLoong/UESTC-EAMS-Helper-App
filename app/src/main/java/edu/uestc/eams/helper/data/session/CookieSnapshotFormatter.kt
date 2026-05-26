package edu.uestc.eams.helper.data.session

/**
 * Logcat / 文案调试：脱敏预览（不写 value 全貌以免泄露）。
 */
object CookieSnapshotFormatter {

    fun toDisplayLines(cookies: List<StoredCookie>, maxUrls: Int = 64): String =
        cookies.take(maxUrls).joinToString("\n") {
            val exp = it.expiresAtMillis?.let { t -> "; exp=$t" } ?: "; session"
            "[${it.domain}] ${it.name}=${it.value.take(6)}*** path=${it.path} sec=${it.secure} http=${it.httpOnly}$exp"
        }

    /** 与 Python `UESTC_EAMS_COOKIE` / curl `-b` 一致：`a=b; c=d`（值未 URL 编码）。*/
    fun toRawCookieHeader(stored: List<StoredCookie>): String =
        stored.joinToString("; ") { "${it.name}=${it.value}" }
}
