package com.eink.reader.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.eink.reader.data.api.DoHDns
import com.eink.reader.data.model.PatchDownloadProgress
import com.eink.reader.data.model.RemoteConfig
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

class RemoteConfigManager private constructor(private val context: Context) {

    companion object {
        const val REMOTE_CONFIG_URL =
            "https://raw.githubusercontent.com/anlq295-work/InkDex/main/app-config.json"

        private const val PREFS_NAME = "inkdex_remote_config"
        private const val KEY_CONFIG_JSON = "cached_config_json"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"

        @Volatile
        private var INSTANCE: RemoteConfigManager? = null

        fun getInstance(context: Context): RemoteConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteConfigManager(context.applicationContext).also { INSTANCE = it }
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

    private val _configFlow = MutableStateFlow(loadCachedConfig())
    val configFlow: StateFlow<RemoteConfig> = _configFlow.asStateFlow()

    val currentConfig: RemoteConfig
        get() = _configFlow.value

    var lastSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        private set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIME, value).apply()

    init {
        // Áp dụng ngay quy tắc và từ điển từ cấu hình đã lưu
        applyRulesToSystem(_configFlow.value)
    }

    private fun loadCachedConfig(): RemoteConfig {
        val savedJson = prefs.getString(KEY_CONFIG_JSON, null)
        if (!savedJson.isNullOrBlank()) {
            try {
                return json.decodeFromString<RemoteConfig>(savedJson)
            } catch (_: Exception) {}
        }
        return RemoteConfig()
    }

    private fun applyRulesToSystem(config: RemoteConfig) {
        NaturalOrderComparator.extraPrefixes = config.chapterRules.extraPrefixes
        NaturalOrderComparator.customRegex = config.chapterRules.customRegex
        if (config.strings.isNotEmpty()) {
            I18n.applyPatch(config.strings)
        }
    }

    /**
     * Kiểm tra nhanh xem trên GitHub có bản config/patch mới hơn không.
     */
    suspend fun checkForNewConfig(force: Boolean = false): Result<RemoteConfig?> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSyncTime < 2 * 3600 * 1000L && _configFlow.value.updatedAt.isNotBlank()) {
            return@withContext Result.success(null)
        }

        try {
            val request = Request.Builder()
                .url(REMOTE_CONFIG_URL)
                .addHeader("Accept", "application/json")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("User-Agent", "InkDex-EReader/1.6.4")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: Không thể kiểm tra app-config.json"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(IOException("Phản hồi rỗng"))
            val remoteConfig = json.decodeFromString<RemoteConfig>(body)

            if (remoteConfig.configVersion > _configFlow.value.configVersion) {
                Result.success(remoteConfig)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Đồng bộ cấu hình & tài nguyên từ xa từ GitHub.
     * Có thể truyền onProgress để hiển thị thanh tiến trình % phong cách Game.
     */
    suspend fun syncRemoteConfig(
        force: Boolean = false,
        onProgress: ((PatchDownloadProgress) -> Unit)? = null
    ): Result<RemoteConfig> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && onProgress == null && now - lastSyncTime < 6 * 3600 * 1000L && _configFlow.value.updatedAt.isNotBlank()) {
            return@withContext Result.success(_configFlow.value)
        }

        try {
            onProgress?.invoke(PatchDownloadProgress(percent = 0))

            val request = Request.Builder()
                .url(REMOTE_CONFIG_URL)
                .addHeader("Accept", "application/json")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("User-Agent", "InkDex-EReader/1.6.4")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "Tải cấu hình thất bại: Mã HTTP ${response.code}"
                onProgress?.invoke(PatchDownloadProgress(error = err))
                return@withContext Result.failure(IOException(err))
            }

            val responseBody = response.body ?: throw IOException("Không nhận được nội dung từ máy chủ")
            val totalBytes = responseBody.contentLength().takeIf { it > 0 } ?: 15_000L

            val inputStream = responseBody.byteStream()
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(2048)
            var bytesRead: Int
            var totalRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                val percent = ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 99)
                onProgress?.invoke(
                    PatchDownloadProgress(
                        percent = percent,
                        bytesDownloaded = totalRead,
                        totalBytes = totalBytes
                    )
                )
            }

            val rawJson = outputStream.toString("UTF-8")
            val newConfig = json.decodeFromString<RemoteConfig>(rawJson)

            // Lưu vào SharedPreferences (Offline-First)
            prefs.edit()
                .putString(KEY_CONFIG_JSON, rawJson)
                .putLong(KEY_LAST_SYNC_TIME, now)
                .apply()

            _configFlow.value = newConfig
            applyRulesToSystem(newConfig)

            onProgress?.invoke(
                PatchDownloadProgress(
                    percent = 100,
                    bytesDownloaded = totalRead,
                    totalBytes = totalRead,
                    isDone = true
                )
            )

            Result.success(newConfig)
        } catch (e: Exception) {
            onProgress?.invoke(PatchDownloadProgress(error = e.localizedMessage))
            Result.failure(e)
        }
    }
}
