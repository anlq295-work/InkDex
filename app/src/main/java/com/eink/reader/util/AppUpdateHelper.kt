package com.eink.reader.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

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
        currentVersion: String = "1.3.1"
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
     * Thư mục lưu trữ tạm các file APK cập nhật.
     */
    fun getUpdateDirectory(context: Context): File {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Đường dẫn file APK đích cho phiên bản cụ thể.
     */
    fun getUpdateApkFile(context: Context, tagName: String): File {
        val safeTag = tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(getUpdateDirectory(context), "InkDex-$safeTag.apk")
    }

    /**
     * Tự động quét và xóa sạch các file APK đã tải sau khi cài đặt hoặc khi mở app.
     */
    fun cleanupUpdateApks(context: Context) {
        try {
            val dir = getUpdateDirectory(context)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.extension.equals("apk", ignoreCase = true) || file.extension.equals("tmp", ignoreCase = true)) {
                        file.delete()
                    }
                }
            }
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()?.forEach { file ->
                if (file.name.startsWith("InkDex", ignoreCase = true) && file.extension.equals("apk", ignoreCase = true)) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Kiểm tra quyền cài đặt ứng dụng từ nguồn không xác định.
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Mở màn hình cấp quyền cài đặt ứng dụng trong cài đặt hệ thống.
     */
    fun openInstallPermissionSetting(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Tải file APK trực tiếp trong ứng dụng kèm theo tiến trình (progress).
     */
    suspend fun downloadApk(
        client: OkHttpClient = OkHttpClient(),
        apkUrl: String,
        targetFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(apkUrl)
                .addHeader("User-Agent", "InkDex-EReader/1.3.1")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Tải file thất bại: Mã HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Không nhận được dữ liệu từ máy chủ"))
            val totalBytes = body.contentLength()

            if (targetFile.exists()) {
                targetFile.delete()
            }

            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            if (tempFile.exists()) {
                tempFile.delete()
            }

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var read: Int
                    var lastReported = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read

                        val now = System.currentTimeMillis()
                        if (now - lastReported > 80 || bytesRead == totalBytes) {
                            lastReported = now
                            onProgress(bytesRead, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.renameTo(targetFile)) {
                Result.success(targetFile)
            } else {
                Result.failure(Exception("Không thể lưu file APK cập nhật."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mở trình cài đặt gói hệ thống (Package Installer) thông qua FileProvider.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(Exception("File cài đặt APK không tồn tại hoặc bị lỗi."))
            }

            if (!canInstallPackages(context)) {
                openInstallPermissionSetting(context)
                return Result.failure(Exception("Vui lòng gạt bật 'Cho phép từ nguồn này' để cài đặt bản cập nhật."))
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(intent)
            Result.success(Unit)
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
