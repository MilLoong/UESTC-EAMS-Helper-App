package edu.uestc.eams.helper.data.eamsapp

import okhttp3.Response
import java.util.zip.GZIPInputStream
import kotlin.text.Charsets

/**
 * 读取响应正文。若应用层自行设置了 `Accept-Encoding`，OkHttp 不会透明解压 gzip，
 * 按 gzip 魔数或 Content-Encoding 做兜底解压，保证 JSON 可解析。
 */
internal fun Response.readTextAutoDecompress(): String {
    val bytes = body?.bytes() ?: return ""
    if (bytes.isEmpty()) return ""
    val enc = header("Content-Encoding").orEmpty()
    val gzipMagic =
        bytes.size >= 2 &&
            bytes[0] == 0x1f.toByte() &&
            bytes[1] == 0x8b.toByte()
    if (gzipMagic || enc.contains("gzip", ignoreCase = true)) {
        try {
            return GZIPInputStream(bytes.inputStream()).bufferedReader(Charsets.UTF_8).readText()
        } catch (_: Exception) {
        }
    }
    return bytes.toString(Charsets.UTF_8)
}
