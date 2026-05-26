package edu.uestc.eams.helper.data.update

import edu.uestc.eams.helper.AppLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
                val release = fetchLatestReleaseJson() ?: return@runCatching null
                val tag = release.optString("tag_name").trim()
                if (tag.isEmpty()) return@runCatching null
                val versionName = AppVersion.normalizeTag(tag)
                if (!AppVersion.isRemoteNewer(versionName, localVersionName)) return@runCatching null

                val pageUrl = release.optString("html_url").ifBlank { AppLinks.GITHUB_REPO_APP }
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

    private fun fetchLatestReleaseJson(): JSONObject? {
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
            if (name.contains("成电") || name.contains("UESTC", ignoreCase = true)) return url
            fallback = url
        }
        return fallback
    }

    companion object {
        private const val USER_AGENT = "UESTC-EAMS-Helper-App"
        private const val MAX_NOTES_LEN = 600

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
    }
}
