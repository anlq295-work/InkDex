package com.eink.reader.data.repository

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    val context: Context = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("inkdex_prefs", Context.MODE_PRIVATE)
    val prefsInstance: SharedPreferences get() = prefs

    companion object {
        const val DEFAULT_WORKER_URL = "https://mangadex-proxy.an-lq295-work.workers.dev"
    }

    // 1. Máy chủ Proxy / Bypass
    var apiBaseUrl: String
        get() = prefs.getString("api_base_url", DEFAULT_WORKER_URL) ?: DEFAULT_WORKER_URL
        set(value) = prefs.edit().putString("api_base_url", value.trim().trimEnd('/')).apply()

    var useDoH: Boolean
        get() = prefs.getBoolean("use_doh", true)
        set(value) = prefs.edit().putBoolean("use_doh", value).apply()

    var dohProvider: String
        get() = prefs.getString("doh_provider", "cloudflare") ?: "cloudflare"
        set(value) = prefs.edit().putString("doh_provider", value).apply()

    // 2. Chức năng cá nhân & Tài khoản MangaDex
    var sessionToken: String?
        get() = prefs.getString("session_token", null)
        set(value) = prefs.edit().putString("session_token", value).apply()

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    var username: String?
        get() = prefs.getString("username", null)
        set(value) = prefs.edit().putString("username", value).apply()

    var personalClientId: String?
        get() = prefs.getString("personal_client_id", null)
        set(value) = prefs.edit().putString("personal_client_id", value).apply()

    var personalClientSecret: String?
        get() = prefs.getString("personal_client_secret", null)
        set(value) = prefs.edit().putString("personal_client_secret", value).apply()

    val isLoggedIn: Boolean
        get() = !sessionToken.isNullOrBlank()

    var autoSyncReadStatus: Boolean
        get() = prefs.getBoolean("auto_sync_read_status", true)
        set(value) = prefs.edit().putBoolean("auto_sync_read_status", value).apply()

    // 3. Content Ratings (Safe, Suggestive, Erotica, Pornographic)
    var contentRatingSafe: Boolean
        get() = prefs.getBoolean("cr_safe", true)
        set(value) = prefs.edit().putBoolean("cr_safe", value).apply()

    var contentRatingSuggestive: Boolean
        get() = prefs.getBoolean("cr_suggestive", true)
        set(value) = prefs.edit().putBoolean("cr_suggestive", value).apply()

    var contentRatingErotica: Boolean
        get() = prefs.getBoolean("cr_erotica", false)
        set(value) = prefs.edit().putBoolean("cr_erotica", value).apply()

    var contentRatingPornographic: Boolean
        get() = prefs.getBoolean("cr_pornographic", false)
        set(value) = prefs.edit().putBoolean("cr_pornographic", value).apply()

    fun getContentRatings(): List<String> {
        val list = mutableListOf<String>()
        if (contentRatingSafe) list.add("safe")
        if (contentRatingSuggestive) list.add("suggestive")
        if (contentRatingErotica) list.add("erotica")
        if (contentRatingPornographic) list.add("pornographic")
        return if (list.isEmpty()) listOf("safe", "suggestive") else list
    }

    // 4. Cài đặt người đọc (Reader Settings)
    // Hướng đọc: "RTL" (Từ phải sang trái - mặc định Manga), "LTR" (Từ trái sang phải)
    var readingDirection: String
        get() = prefs.getString("reading_direction", "RTL") ?: "RTL"
        set(value) = prefs.edit().putString("reading_direction", value).apply()

    // Số trang tải trước: 1..10 (mặc định 4)
    var preCachePagesCount: Int
        get() = prefs.getInt("pre_cache_pages_count", 4)
        set(value) = prefs.edit().putInt("pre_cache_pages_count", value.coerceIn(1, 10)).apply()

    // Chuyển trang bằng nút bấm: "VOLUME", "LEFT_RIGHT", "UP_DOWN"
    var pageTurnKeys: String
        get() = prefs.getString("page_turn_keys", "VOLUME") ?: "VOLUME"
        set(value) = prefs.edit().putString("page_turn_keys", value).apply()

    // Tùy chọn màn hình E-Ink
    var defaultFitWidth: Boolean
        get() = prefs.getBoolean("default_fit_width", false)
        set(value) = prefs.edit().putBoolean("default_fit_width", value).apply()

    // Vị trí lưu trữ truyện tải về (Storage Path)
    var downloadStoragePath: String
        get() = prefs.getString("download_storage_path", "") ?: ""
        set(value) = prefs.edit().putString("download_storage_path", value.trim()).apply()

    // Có sử dụng Proxy Worker khi mở trang đăng nhập Web không
    var useProxyForAuth: Boolean
        get() = prefs.getBoolean("use_proxy_for_auth", true)
        set(value) = prefs.edit().putBoolean("use_proxy_for_auth", value).apply()

    var dualPageInLandscape: Boolean
        get() = prefs.getBoolean("dual_page_landscape", true)
        set(value) = prefs.edit().putBoolean("dual_page_landscape", value).apply()

    // Khóa mật khẩu ứng dụng (App Lock)
    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean("app_lock_enabled", false)
        set(value) = prefs.edit().putBoolean("app_lock_enabled", value).apply()

    var appLockPin: String
        get() = prefs.getString("app_lock_pin", "") ?: ""
        set(value) = prefs.edit().putString("app_lock_pin", value.trim()).apply()

    // 5. Tối ưu hóa Màn hình E-Ink chống bóng ma (Anti-Ghosting)
    var eInkSupportEnabled: Boolean
        get() = prefs.getBoolean("eink_support_enabled", true)
        set(value) = prefs.edit().putBoolean("eink_support_enabled", value).apply()

    var eInkDisableOverscroll: Boolean
        get() = if (eInkSupportEnabled) prefs.getBoolean("eink_disable_overscroll", true) else false
        set(value) = prefs.edit().putBoolean("eink_disable_overscroll", value).apply()

    val rawEInkDisableOverscroll: Boolean
        get() = prefs.getBoolean("eink_disable_overscroll", true)

    var eInkPageButtonsEnabled: Boolean
        get() = if (eInkSupportEnabled) prefs.getBoolean("eink_page_buttons_enabled", true) else false
        set(value) = prefs.edit().putBoolean("eink_page_buttons_enabled", value).apply()

    val rawEInkPageButtonsEnabled: Boolean
        get() = prefs.getBoolean("eink_page_buttons_enabled", true)

    var eInkReaderPagedScroll: Boolean
        get() = if (eInkSupportEnabled) prefs.getBoolean("eink_reader_paged_scroll", true) else false
        set(value) = prefs.edit().putBoolean("eink_reader_paged_scroll", value).apply()

    val rawEInkReaderPagedScroll: Boolean
        get() = prefs.getBoolean("eink_reader_paged_scroll", true)

    var eInkAutoRefreshInterval: Int
        get() = if (eInkSupportEnabled) prefs.getInt("eink_auto_refresh_interval", 5) else 0
        set(value) = prefs.edit().putInt("eink_auto_refresh_interval", value).apply()

    val rawEInkAutoRefreshInterval: Int
        get() = prefs.getInt("eink_auto_refresh_interval", 5)

    // 6. Tự động chuyển chương kế khi đọc hết chương
    var autoNextChapter: Boolean
        get() = prefs.getBoolean("auto_next_chapter", true)
        set(value) = prefs.edit().putBoolean("auto_next_chapter", value).apply()

    // 7. Chế độ màu mặc định khi mở truyện
    var defaultReaderColorMode: String
        get() = prefs.getString("default_reader_color_mode", "KALEIDO_3") ?: "KALEIDO_3"
        set(value) = prefs.edit().putString("default_reader_color_mode", value).apply()

    fun logout() {
        prefs.edit()
            .remove("session_token")
            .remove("refresh_token")
            .remove("username")
            .remove("personal_client_id")
            .remove("personal_client_secret")
            .apply()
    }
}