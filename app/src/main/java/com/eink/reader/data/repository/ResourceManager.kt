package com.eink.reader.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.eink.reader.data.api.DoHDns
import com.eink.reader.data.model.PatchDownloadProgress
import com.eink.reader.data.model.ResourcePatch
import com.eink.reader.util.I18n
import com.eink.reader.util.NaturalOrderComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class ResourceManager private constructor(private val context: Context) {

    companion object {
        const val REMOTE_PATCH_URL =
            "https://raw.githubusercontent.com/anlq295-work/InkDex/main/resources/patch.json"

        private const val PREFS_NAME = "inkdex_resource_patch"
        private const val KEY_APPLIED_PATCH_VERSION = "applied_patch_version"
        private const val KEY_CACHED_PATCH_JSON = "cached_patch_json"
        private const val KEY_LAST_CHECK_TIME = "last_patch_check_time"

        @Volatile
        private var INSTANCE: ResourceManager? = null

        fun getInstance(context: Context): ResourceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ResourceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .dns(DoHDns { "cloudflare" })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val _currentPatchVersion = MutableStateFlow(prefs.getInt(KEY_APPLIED_PATCH_VERSION, 0))
    val currentPatchVersion: StateFlow<Int> = _currentPatchVersion.asStateFlow()

    var lastCheckTime: Long
        get() = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        private set(value) = prefs.edit().putLong(KEY_LAST_CHECK_TIME, value).apply()

    init {
        loadAndApplyCachedPatch()
    }

    private fun loadAndApplyCachedPatch() {
        val savedJson = prefs.getString(KEY_CACHED_PATCH_JSON, null)
        if (!savedJson.isNullOrBlank()) {
            try {
                val patch = json.decodeFromString<ResourcePatch>(savedJson)
                applyPatchToSystem(patch)
            } catch (_: Exception) {}
        }
    }

    private fun applyPatchToSystem(patch: ResourcePatch) {
        if (patch.strings.isNotEmpty()) {
            I18n.applyPatch(patch.strings)
        }
        patch.chapterRules?.let { rules ->
            if (rules.extraPrefixes.isNotEmpty()) {
                NaturalOrderComparator.extraPrefixes = rules.extraPrefixes
            }
            if (rules.customRegex != null) {
                NaturalOrderComparator.customRegex = rules.customRegex
            }
        }
    }

    /**
     * Kiểm tra xem trên GitHub có bản patch tài nguyên mới hơn phiên bản cục bộ không.
     */
    suspend fun checkForPatch(force: Boolean = false): Result<ResourcePatch?> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckTime < 2 * 3600 * 1000L && _currentPatchVersion.value > 0) {
            return@withContext Result.success(null)
        }

        try {
            val request = Request.Builder()
                .url(REMOTE_PATCH_URL)
                .addHeader("Accept", "application/json")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("User-Agent", "InkDex-EReader/1.6")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP : Không thể kiểm tra gói tài nguyên"))
            }

            val bodyString = response.body?.string()
                ?: return@withContext Result.failure(IOException("Dữ liệu tài nguyên rỗng"))

            val remotePatch = json.decodeFromString<ResourcePatch>(bodyString)
            lastCheckTime = now

            if (remotePatch.patchVersion > _currentPatchVersion.value) {
                Result.success(remotePatch)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Tải gói patch với tiến trình % như game và nạp trực tiếp vào bộ nhớ.
     */
    suspend fun downloadAndApplyPatch(
        onProgress: (PatchDownloadProgress) -> Unit = {}
    ): Result<ResourcePatch> = withContext(Dispatchers.IO) {
        try {
            onProgress(PatchDownloadProgress(percent = 0, bytesDownloaded = 0L, totalBytes = 0L))

            val request = Request.Builder()
                .url(REMOTE_PATCH_URL)
                .addHeader("Accept", "application/json")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("User-Agent", "InkDex-EReader/1.6")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "Tải gói tài nguyên thất bại: Mã HTTP "
                onProgress(PatchDownloadProgress(error = err))
                return@withContext Result.failure(IOException(err))
            }

            val body = response.body ?: throw IOException("Không nhận được nội dung từ máy chủ")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: 30_000L

            val inputStream = body.byteStream()
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var bytesRead: Int
            var totalRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                val percent = ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 99)
                onProgress(
                    PatchDownloadProgress(
                        percent = percent,
                        bytesDownloaded = totalRead,
                        totalBytes = totalBytes
                    )
                )
            }

            val rawJson = outputStream.toString("UTF-8")
            val patch = json.decodeFromString<ResourcePatch>(rawJson)

            // Lưu đè vào SharedPreferences (Offline-first)
            prefs.edit()
                .putString(KEY_CACHED_PATCH_JSON, rawJson)
                .putInt(KEY_APPLIED_PATCH_VERSION, patch.patchVersion)
                .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                .apply()

            _currentPatchVersion.value = patch.patchVersion

            // Nạp nóng vào I18n và Hệ thống
            applyPatchToSystem(patch)

            onProgress(
                PatchDownloadProgress(
                    percent = 100,
                    bytesDownloaded = totalRead,
                    totalBytes = totalRead,
                    isDone = true
                )
            )

            Result.success(patch)
        } catch (e: Exception) {
            onProgress(PatchDownloadProgress(error = e.localizedMessage))
            Result.failure(e)
        }
    }
}
