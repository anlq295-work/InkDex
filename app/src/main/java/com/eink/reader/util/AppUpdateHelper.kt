package com.eink.reader.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class AppReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String?,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val isNewer: Boolean
)

object AppUpdateHelper {
    private const val GITHUB_API_LATEST_RELEASE =
        "https://api.github.com/repos/anlq295-work/InkDex/releases/latest"

    /**
     * Tách chuỗi version thành danh sách các số nguyên.
     * Hỗ trợ: "Ver_1.0", "v1.1", "1.2.3", "V1.0.0" -> [1, 0], [1, 1], [1, 2, 3]
     */
    fun parseVersionParts(versionStr: String): List<Int> {
        val cleaned = versionStr.replace(Regex("[^0-9.]"), "")
        return cleaned.split(".")
            .filter { it.isNotBlank() }
            .mapNotNull { it.toIntOrNull() }
    }

    /**
     * So sánh xem phiên bản trên GitHub (remote) có mới hơn phiên bản hiện tại (current) không.
     */
    fun isRemoteVersionNewer(currentVersion: String, remoteVersion: String): Boolean {
        val currentParts = parseVersionParts(currentVersion)
        val remoteParts = parseVersionParts(remoteVersion)
        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val curr = currentParts.getOrElse(i) { 0 }
            val rem = remoteParts.getOrElse(i) { 0 }
            if (rem > curr) return true
            if (rem < curr) return false
        }
        return false
    }

    /**
     * Kiểm tra bản cập nhật mới nhất từ GitHub Releases qua API.
     */
    suspend fun checkForUpdate(
        client: OkHttpClient = OkHttpClient(),
        currentVersion: String = "1.1"
    ): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_LATEST_RELEASE)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "InkDex-EReader/$currentVersion")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Máy chủ GitHub phản hồi mã lỗi: ${response.code}")
                )
            }

            val bodyStr = response.body?.string()
                ?: return@withContext Result.failure(Exception("Không nhận được dữ liệu từ GitHub"))

            val json = JSONObject(bodyStr)
            val tagName = json.optString("tag_name", "")
            val releaseName = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "")
            val htmlUrl = json.optString("html_url", "https://github.com/anlq295-work/InkDex/releases")

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        break
                    }
                }
            }

            val isNewer = isRemoteVersionNewer(currentVersion, tagName)

            Result.success(
                AppReleaseInfo(
                    tagName = tagName,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes.takeIf { it.isNotBlank() },
                    htmlUrl = htmlUrl,
                    apkDownloadUrl = apkUrl ?: htmlUrl,
                    isNewer = isNewer
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mở đường dẫn tải về hoặc trang Release trên trình duyệt của máy.
     */
    fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
