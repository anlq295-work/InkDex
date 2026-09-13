package com.eink.reader.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.eink.reader.data.api.DoHDns
import com.eink.reader.data.model.RemoteConfig
import com.eink.reader.util.NaturalOrderComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
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
        // Áp dụng ngay quy tắc từ cấu hình đã lưu
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
    }

    /**
     * Đồng bộ cấu hình từ xa từ GitHub.
     * @param force: Nếu false, bỏ qua nếu vừa đồng bộ cách đây ít hơn 6 tiếng.
     */
    suspend fun syncRemoteConfig(force: Boolean = false): Result<RemoteConfig> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSyncTime < 6 * 3600 * 1000L && _configFlow.value.updatedAt.isNotBlank()) {
            return@withContext Result.success(_configFlow.value)
        }

        try {
            val request = Request.Builder()
                .url(REMOTE_CONFIG_URL)
                .addHeader("Accept", "application/json")
                .addHeader("Cache-Control", "no-cache")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: Không thể tải app-config.json")
            }

            val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
            val newConfig = json.decodeFromString<RemoteConfig>(body)

            // Lưu vào SharedPreferences
            prefs.edit()
                .putString(KEY_CONFIG_JSON, body)
                .putLong(KEY_LAST_SYNC_TIME, now)
                .apply()

            _configFlow.value = newConfig
            applyRulesToSystem(newConfig)

            Result.success(newConfig)
        } catch (e: Exception) {
            // Khi lỗi mạng, tiếp tục dùng cấu hình offline đã lưu
            Result.failure(e)
        }
    }
}
