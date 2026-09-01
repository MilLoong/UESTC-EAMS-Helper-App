package edu.uestc.eams.helper.data.update

import edu.uestc.eams.helper.AppLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 从 GitHub Releases 检查是否有新版本。 */
class AppUpdateChecker(
    private val client: OkHttpClient = defaultClient(),
) {

    data class UpdateInfo(
        val releaseTag: String,
        val versionName: String,
        val releasePageUrl: String,
        val downloadUrl: String,
        val releaseNotes: String,
    )

    suspend fun fetchLatestIfNewer(localVersionName: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val release = fetchNewestReleaseJson() ?: return@runCatching null
                val tag = release.optString("tag_name").trim()
                if (tag.isEmpty()) return@runCatching null
                val versionName = AppVersion.normalizeTag(tag)
                if (!AppVersion.isRemoteNewer(versionName, localVersionName)) return@runCatching null

                val pageUrl =
                    release.optString("html_url").ifBlank { AppLinks.GITHUB_RELEASES_LATEST }
                val downloadUrl = pickApkDownloadUrl(release) ?: pageUrl
                val notes = release.optString("body").trim().take(MAX_NOTES_LEN)

                UpdateInfo(
                    releaseTag = tag,
                    versionName = versionName,
                    releasePageUrl = pageUrl,
                    downloadUrl = downloadUrl,
                    releaseNotes = notes,
                )
            }.getOrNull()
        }

    /**
     * 不直接信任 `/releases/latest`：多次改 tag / 重建 Release 后，
     * GitHub 的「Latest」标记可能仍停在旧版（例如 v1.3.0）。
     * 改为拉列表，按语义化版本取最高的正式版。
     */
    private fun fetchNewestReleaseJson(): JSONObject? {
        val releases = fetchReleaseList()
        if (!releases.isNullOrEmpty()) {
            return releases.maxWithOrNull { a, b ->
                val va = AppVersion.normalizeTag(a.optString("tag_name"))
                val vb = AppVersion.normalizeTag(b.optString("tag_name"))
                when {
                    AppVersion.isRemoteNewer(va, vb) -> 1
                    AppVersion.isRemoteNewer(vb, va) -> -1
                    else -> 0
                }
            }
        }
        return fetchLatestEndpointRelease()
    }

    private fun fetchReleaseList(): List<JSONObject>? {
        val request =
            Request.Builder()
                .url(AppLinks.GITHUB_RELEASES_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val array = JSONArray(body)
            val out = mutableListOf<JSONObject>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optBoolean("draft", false)) continue
                if (item.optBoolean("prerelease", false)) continue
                if (item.optString("tag_name").isBlank()) continue
                out += item
            }
            return out
        }
    }

    private fun fetchLatestEndpointRelease(): JSONObject? {
        val request =
            Request.Builder()
                .url(AppLinks.GITHUB_RELEASES_LATEST_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return JSONObject(body)
        }
    }

    private fun pickApkDownloadUrl(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        var fallback: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (!name.endsWith(".apk", ignoreCase = true) || url.isBlank()) continue
            if (name.contains("UESTC-EAMS-Helper", ignoreCase = true)) return url
            fallback = url
        }
        return fallback
    }

    companion object {
        private const val USER_AGENT = "UESTC-EAMS-Helper-App"
        private const val MAX_NOTES_LEN = 1500

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
    }
}
