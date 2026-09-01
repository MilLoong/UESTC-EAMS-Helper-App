package edu.uestc.eams.helper.data.session

import com.google.gson.Gson
import com.google.gson.JsonObject
import edu.uestc.eams.helper.BuildConfig
import java.io.File

/**
 * Debug 构建把登录 Cookie 额外写到卸载后仍在的目录（模拟器 /data/local/tmp、Downloads）。
 * 正式包不会启用。请勿把该文件提交或发给他人。
 */
internal object DebugSessionSidecar {
    const val FILE_NAME = "uestc-eams-helper-debug-session.json"
    private const val KEY = "cookies_json"
    private val gson = Gson()

    internal fun encode(cookiesJson: String): String {
        val obj = JsonObject()
        obj.addProperty(KEY, cookiesJson)
        return gson.toJson(obj)
    }

    internal fun decode(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            gson.fromJson(trimmed, JsonObject::class.java)
                ?.get(KEY)
                ?.asString
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun save(cookiesJson: String) {
        if (!BuildConfig.DEBUG) return
        if (cookiesJson.isBlank()) return
        val payload = encode(cookiesJson)
        candidateFiles().forEach { file ->
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(payload)
                file.setReadable(true, false)
                file.setWritable(true, false)
            }
        }
    }

    fun load(): String? {
        if (!BuildConfig.DEBUG) return null
        candidateFiles().forEach { file ->
            val json = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()
            decode(json.orEmpty())?.let { return it }
        }
        return null
    }

    private fun candidateFiles(): List<File> =
        buildList {
            add(File("/data/local/tmp", FILE_NAME))
            add(File("/sdcard/Download", FILE_NAME))
            add(File("/storage/emulated/0/Download", FILE_NAME))
        }
}
