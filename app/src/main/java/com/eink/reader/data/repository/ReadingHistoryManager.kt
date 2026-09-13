package com.eink.reader.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ReadingRecord(
    val mangaId: String,
    val mangaTitle: String,
    val coverUrl: String? = null,
    val lastChapterId: String,
    val lastChapterTitle: String,
    val lastChapterNumber: String? = null,
    val lastReadPage: Int = 1,
    val totalPages: Int = 0,
    val readingMode: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

class ReadingHistoryManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("inkdex_reading_history", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val KEY_HISTORY = "reading_history_records"
    private val KEY_READ_CHAPTERS_PREFIX = "read_chapters_"

    companion object {
        @Volatile
        private var instance: ReadingHistoryManager? = null

        fun getInstance(context: Context): ReadingHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: ReadingHistoryManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Lấy toàn bộ danh sách truyện đang đọc dở trên máy, sắp xếp mới nhất lên đầu
     */
    fun getAllRecords(): List<ReadingRecord> {
        val rawJson = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val list = json.decodeFromString<List<ReadingRecord>>(rawJson)
            list.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Lấy bản ghi tiến độ đọc của một bộ truyện cụ thể
     */
    fun getRecord(mangaId: String): ReadingRecord? {
        if (mangaId.isBlank()) return null
        return getAllRecords().firstOrNull { it.mangaId == mangaId }
    }

    /**
     * Lưu lại tiến độ đọc khi người dùng đọc đến một trang hoặc mở một chương
     */
    fun saveProgress(
        mangaId: String,
        mangaTitle: String,
        coverUrl: String? = null,
        chapterId: String,
        chapterTitle: String,
        chapterNumber: String? = null,
        page: Int = 1,
        totalPages: Int = 0
    ) {
        if (mangaId.isBlank() || chapterId.isBlank()) return

        val currentList = getAllRecords().toMutableList()
        val existingIndex = currentList.indexOfFirst { it.mangaId == mangaId }
        val existingRecord = currentList.getOrNull(existingIndex)

        // Bảo toàn tên truyện chính xác: Tránh bị ghi đè bởi "Ch.1..." hay "Truyện không tên"
        val resolvedTitle = when {
            mangaTitle.isNotBlank() && mangaTitle != "Truyện không tên" && mangaTitle != "Untitled" && !mangaTitle.startsWith("Ch.", ignoreCase = true) -> mangaTitle
            existingRecord != null && existingRecord.mangaTitle.isNotBlank() && existingRecord.mangaTitle != "Truyện không tên" && existingRecord.mangaTitle != "Untitled" && !existingRecord.mangaTitle.startsWith("Ch.", ignoreCase = true) -> existingRecord.mangaTitle
            mangaTitle.isNotBlank() -> mangaTitle
            else -> existingRecord?.mangaTitle ?: "Truyện không tên"
        }

        // Bảo toàn ảnh bìa: Nếu param mới null/rỗng thì giữ lại ảnh bìa cũ
        val resolvedCover = coverUrl?.takeIf { it.isNotBlank() } ?: existingRecord?.coverUrl

        val newRecord = ReadingRecord(
            mangaId = mangaId,
            mangaTitle = resolvedTitle,
            coverUrl = resolvedCover,
            lastChapterId = chapterId,
            lastChapterTitle = chapterTitle.ifBlank { "Chương đọc" },
            lastChapterNumber = chapterNumber ?: existingRecord?.lastChapterNumber,
            lastReadPage = page.coerceAtLeast(1),
            totalPages = totalPages.coerceAtLeast(0),
            readingMode = existingRecord?.readingMode,
            updatedAt = System.currentTimeMillis()
        )

        if (existingIndex >= 0) {
            currentList[existingIndex] = newRecord
        } else {
            currentList.add(0, newRecord)
        }

        // Giới hạn lưu tối đa 200 truyện gần nhất
        val trimmedList = currentList.sortedByDescending { it.updatedAt }.take(200)
        try {
            val serialized = json.encodeToString(trimmedList)
            prefs.edit().putString(KEY_HISTORY, serialized).apply()
        } catch (_: Exception) {}

        // Đánh dấu chương này là đã đọc
        markChapterRead(mangaId, chapterId)
    }

    /**
     * Cập nhật tiêu đề hoặc ảnh bìa bổ sung cho một bộ truyện trong lịch sử đọc
     */
    fun updateMangaInfo(mangaId: String, title: String?, coverUrl: String?) {
        if (mangaId.isBlank()) return
        val currentList = getAllRecords().toMutableList()
        val index = currentList.indexOfFirst { it.mangaId == mangaId }
        if (index >= 0) {
            val old = currentList[index]
            val newTitle = if (!title.isNullOrBlank() && title != "Untitled" && !title.startsWith("Ch.", ignoreCase = true)) title else old.mangaTitle
            val newCover = if (!coverUrl.isNullOrBlank()) coverUrl else old.coverUrl
            if (newTitle != old.mangaTitle || newCover != old.coverUrl) {
                currentList[index] = old.copy(mangaTitle = newTitle, coverUrl = newCover)
                try {
                    val serialized = json.encodeToString(currentList)
                    prefs.edit().putString(KEY_HISTORY, serialized).apply()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Lưu chế độ đọc riêng cho một bộ truyện (RTL, LTR, VERTICAL)
     */
    fun saveReadingMode(mangaId: String, mode: String) {
        if (mangaId.isBlank()) return
        val currentList = getAllRecords().toMutableList()
        val existingIndex = currentList.indexOfFirst { it.mangaId == mangaId }
        if (existingIndex >= 0) {
            val old = currentList[existingIndex]
            currentList[existingIndex] = old.copy(readingMode = mode)
            try {
                val serialized = json.encodeToString(currentList)
                prefs.edit().putString(KEY_HISTORY, serialized).apply()
            } catch (_: Exception) {}
        }
    }

    /**
     * Đánh dấu một chương là đã đọc
     */
    fun markChapterRead(mangaId: String, chapterId: String) {
        if (mangaId.isBlank() || chapterId.isBlank()) return
        val key = KEY_READ_CHAPTERS_PREFIX + mangaId
        val currentSet = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(chapterId)
        prefs.edit().putStringSet(key, currentSet).apply()
    }

    /**
     * Kiểm tra một chương đã từng được đọc hay chưa
     */
    fun isChapterRead(mangaId: String, chapterId: String): Boolean {
        if (mangaId.isBlank() || chapterId.isBlank()) return false
        val key = KEY_READ_CHAPTERS_PREFIX + mangaId
        val currentSet = prefs.getStringSet(key, emptySet()) ?: return false
        return currentSet.contains(chapterId)
    }

    /**
     * Lấy danh sách ID tất cả các chương đã đọc của truyện
     */
    fun getReadChapterIds(mangaId: String): Set<String> {
        if (mangaId.isBlank()) return emptySet()
        val key = KEY_READ_CHAPTERS_PREFIX + mangaId
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    /**
     * Xóa một truyện khỏi lịch sử đọc trên máy
     */
    fun removeRecord(mangaId: String) {
        if (mangaId.isBlank()) return
        val currentList = getAllRecords().toMutableList()
        currentList.removeAll { it.mangaId == mangaId }
        try {
            val serialized = json.encodeToString(currentList)
            prefs.edit().putString(KEY_HISTORY, serialized).apply()
        } catch (_: Exception) {}
    }

    /**
     * Xóa toàn bộ lịch sử đọc
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
