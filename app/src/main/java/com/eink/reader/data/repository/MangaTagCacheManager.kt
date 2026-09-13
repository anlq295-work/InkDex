package com.eink.reader.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.eink.reader.data.model.MangaItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CachedMangaTag(
    val mangaId: String,
    val contentRating: String? = null,
    val originalLanguage: String? = null,
    val tagNames: List<String> = emptyList(),
    val isPornographic: Boolean = false
)

class MangaTagCacheManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("inkdex_manga_tag_cache", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val KEY_TAG_CACHE = "manga_tag_cache_map"
    private var memoryCache: MutableMap<String, CachedMangaTag>? = null

    companion object {
        @Volatile
        private var instance: MangaTagCacheManager? = null

        fun getInstance(context: Context): MangaTagCacheManager {
            return instance ?: synchronized(this) {
                instance ?: MangaTagCacheManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun loadCache(): MutableMap<String, CachedMangaTag> {
        memoryCache?.let { return it }
        val rawJson = prefs.getString(KEY_TAG_CACHE, null)
        val loaded = if (!rawJson.isNullOrBlank()) {
            try {
                json.decodeFromString<Map<String, CachedMangaTag>>(rawJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }
        } else {
            mutableMapOf()
        }
        memoryCache = loaded
        return loaded
    }

    private fun persistCache() {
        val cache = memoryCache ?: return
        try {
            // Giới hạn lưu tối đa 1000 manga tags
            val trimmed = if (cache.size > 1000) {
                cache.entries.toList().takeLast(1000).associate { it.key to it.value }
            } else {
                cache
            }
            val raw = json.encodeToString(trimmed)
            prefs.edit().putString(KEY_TAG_CACHE, raw).apply()
        } catch (_: Exception) {}
    }

    /**
     * Lưu thông tin tag và contentRating của một manga vào cache
     */
    fun saveMangaTag(manga: MangaItem) {
        val cache = loadCache()
        val rating = manga.attributes.contentRating?.lowercase()?.trim()
        val origLang = manga.attributes.originalLanguage?.lowercase()?.trim()
        val tags = manga.attributes.tags.mapNotNull { it.attributes.name["en"] ?: it.attributes.name.values.firstOrNull() }
        val isPorno = rating == "pornographic" || tags.any {
            it.equals("hentai", ignoreCase = true) ||
            it.equals("pornographic", ignoreCase = true) ||
            (it.equals("erotica", ignoreCase = true) && rating == "pornographic")
        }

        cache[manga.id] = CachedMangaTag(
            mangaId = manga.id,
            contentRating = rating,
            originalLanguage = origLang,
            tagNames = tags,
            isPornographic = isPorno
        )
        persistCache()
    }

    /**
     * Lưu hàng loạt manga vào cache
     */
    fun saveMangaTags(mangaList: List<MangaItem>) {
        if (mangaList.isEmpty()) return
        val cache = loadCache()
        var changed = false
        for (manga in mangaList) {
            val rating = manga.attributes.contentRating?.lowercase()?.trim()
            val origLang = manga.attributes.originalLanguage?.lowercase()?.trim()
            val tags = manga.attributes.tags.mapNotNull { it.attributes.name["en"] ?: it.attributes.name.values.firstOrNull() }
            val isPorno = rating == "pornographic" || tags.any {
                it.equals("hentai", ignoreCase = true) ||
                it.equals("pornographic", ignoreCase = true) ||
                (it.equals("erotica", ignoreCase = true) && rating == "pornographic")
            }

            cache[manga.id] = CachedMangaTag(
                mangaId = manga.id,
                contentRating = rating,
                originalLanguage = origLang,
                tagNames = tags,
                isPornographic = isPorno
            )
            changed = true
        }
        if (changed) {
            persistCache()
        }
    }

    /**
     * Kiểm tra xem truyện có phải là Webtoon / Manhwa (cuộn dọc) không
     */
    fun isWebtoon(mangaId: String): Boolean {
        val cached = loadCache()[mangaId] ?: return false
        val hasWebtoonTag = cached.tagNames.any {
            it.equals("Long Strip", ignoreCase = true) ||
            it.equals("Web Comic", ignoreCase = true)
        }
        val isKorean = cached.originalLanguage?.equals("ko", ignoreCase = true) == true
        return hasWebtoonTag || isKorean
    }

    /**
     * Kiểm tra xem truyện có phải là truyện người lớn (Pornographic / Mature) không
     */
    fun isPornographic(mangaId: String, fallbackRating: String? = null, fallbackTags: List<String> = emptyList()): Boolean {
        val cache = loadCache()
        val cached = cache[mangaId]
        if (cached != null) {
            return cached.isPornographic || cached.contentRating == "pornographic"
        }
        val rating = fallbackRating?.lowercase()?.trim()
        if (rating == "pornographic") return true
        if (fallbackTags.any { it.equals("hentai", ignoreCase = true) || it.equals("pornographic", ignoreCase = true) }) return true
        return false
    }

    /**
     * Lọc danh sách MangaItem theo danh sách Content Rating được người dùng cho phép
     */
    fun filterMangaByRatings(
        list: List<MangaItem>,
        allowedRatings: List<String>,
        allowPornographic: Boolean
    ): List<MangaItem> {
        // Lưu lại cache ngay khi lọc
        saveMangaTags(list)

        val normalizedAllowed = allowedRatings.map { it.lowercase().trim() }.toSet()
        return list.filter { manga ->
            val rating = manga.attributes.contentRating?.lowercase()?.trim() ?: "safe"
            val isPorno = isPornographic(
                mangaId = manga.id,
                fallbackRating = rating,
                fallbackTags = manga.attributes.tags.mapNotNull { it.attributes.name["en"] ?: it.attributes.name.values.firstOrNull() }
            )

            if (!allowPornographic && (isPorno || rating == "pornographic")) {
                return@filter false
            }

            // Nếu rating không nằm trong danh sách được phép
            if (normalizedAllowed.isNotEmpty() && !normalizedAllowed.contains(rating)) {
                return@filter false
            }

            true
        }
    }
}
