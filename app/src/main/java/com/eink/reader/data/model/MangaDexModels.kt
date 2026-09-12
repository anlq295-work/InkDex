package com.eink.reader.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaListResponse(
    val result: String = "",
    val data: List<MangaItem> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0
)

@Serializable
data class MangaItem(
    val id: String,
    val type: String = "manga",
    val attributes: MangaAttributes = MangaAttributes(),
    val relationships: List<Relationship> = emptyList()
) {
    val displayTitle: String
        get() = attributes.title["en"]
            ?: attributes.title["vi"]
            ?: attributes.title["ja-ro"]
            ?: attributes.title.values.firstOrNull()
            ?: "Untitled"

    val displayDescription: String
        get() = attributes.description["vi"]
            ?: attributes.description["en"]
            ?: attributes.description.values.firstOrNull()
            ?: ""

    val coverFileName: String?
        get() = relationships.firstOrNull { it.type == "cover_art" && !it.attributes?.fileName.isNullOrBlank() }?.attributes?.fileName
            ?: relationships.firstOrNull { it.type == "cover_art" }?.attributes?.fileName

    val coverUrl: String?
        get() = coverFileName?.let { "https://uploads.mangadex.org/covers/$id/$it.512.jpg" }

    fun getCoverUrl(proxyBaseUrl: String? = null): String? {
        return coverFileName?.let {
            if (!proxyBaseUrl.isNullOrBlank() && !proxyBaseUrl.contains("api.mangadex.org")) {
                val clean = proxyBaseUrl.trimEnd('/')
                "$clean/covers/$id/$it.512.jpg"
            } else {
                "https://uploads.mangadex.org/covers/$id/$it.512.jpg"
            }
        }
    }

    val authorName: String?
        get() = relationships.firstOrNull { it.type == "author" }?.attributes?.name
}

@Serializable
data class MangaAttributes(
    val title: Map<String, String> = emptyMap(),
    val description: Map<String, String> = emptyMap(),
    val status: String? = null,
    val year: Int? = null,
    val contentRating: String? = null,
    val tags: List<TagItem> = emptyList()
)

@Serializable
data class TagItem(
    val id: String = "",
    val attributes: TagAttributes = TagAttributes()
)

@Serializable
data class TagAttributes(
    val name: Map<String, String> = emptyMap()
)

@Serializable
data class Relationship(
    val id: String = "",
    val type: String = "",
    val attributes: RelationshipAttributes? = null
)

@Serializable
data class RelationshipAttributes(
    val fileName: String? = null,
    val name: String? = null
)

@Serializable
data class ChapterListResponse(
    val result: String = "",
    val data: List<ChapterItem> = emptyList(),
    val total: Int = 0
)

@Serializable
data class ChapterItem(
    val id: String,
    val type: String = "chapter",
    val attributes: ChapterAttributes = ChapterAttributes(),
    val relationships: List<Relationship> = emptyList()
) {
    val displayTitle: String
        get() {
            val ch = attributes.chapter?.let { "Ch.$it" } ?: "Ch.?"
            val t = attributes.title
            return if (!t.isNullOrBlank()) "$ch: $t" else ch
        }

    val scanlationGroup: String?
        get() = relationships.firstOrNull { it.type == "scanlation_group" }?.attributes?.name
}

@Serializable
data class ChapterAttributes(
    val volume: String? = null,
    val chapter: String? = null,
    val title: String? = null,
    val translatedLanguage: String = "en",
    val publishAt: String? = null,
    val pages: Int = 0
)

@Serializable
data class AtHomeServerResponse(
    val result: String = "",
    val baseUrl: String = "",
    val chapter: AtHomeChapterData = AtHomeChapterData()
)

@Serializable
data class AtHomeChapterData(
    val hash: String = "",
    val data: List<String> = emptyList(),
    val dataSaver: List<String> = emptyList()
)

enum class EInkColorMode(val title: String, val shortTitle: String) {
    KALEIDO_3("Bigme B751C / Kaleido 3 (Sống động)", "Bigme Kaleido 3"),
    COLOR_BOOST("Tăng nét E-Ink (Color Boost)", "Tăng nét"),
    MONOCHROME("Đen trắng 300PPI (Pure B&W)", "Đen trắng"),
    ORIGINAL("Gốc (Original)", "Màu gốc")
}

@Serializable
data class MangaReadingStatusResponse(
    val result: String = "",
    val statuses: Map<String, String> = emptyMap()
)

@Serializable
data class SingleMangaReadingStatusResponse(
    val result: String = "",
    val status: String? = null
)

enum class ReadingStatusEnum(val apiValue: String, val titleVi: String, val icon: String) {
    READING("reading", "Đang đọc", "📖"),
    PLAN_TO_READ("plan_to_read", "Dự định đọc", "📋"),
    COMPLETED("completed", "Đã đọc xong", "✅"),
    RE_READING("re_reading", "Đang đọc lại", "🔄"),
    ON_HOLD("on_hold", "Tạm ngưng", "⏸️"),
    DROPPED("dropped", "Bỏ dở", "❌");

    companion object {
        fun fromApiValue(value: String?): ReadingStatusEnum? = entries.firstOrNull { it.apiValue == value }
        fun getTitle(value: String?): String = fromApiValue(value)?.let { "${it.icon} ${it.titleVi}" } ?: "Chưa phân loại"
    }
}

@Serializable
data class CustomListResponse(
    val result: String = "",
    val data: List<CustomListItem> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0
)

@Serializable
data class CustomListItem(
    val id: String = "",
    val type: String = "custom_list",
    val attributes: CustomListAttributes = CustomListAttributes(),
    val relationships: List<Relationship> = emptyList()
)

@Serializable
data class CustomListAttributes(
    val name: String = "",
    val visibility: String = "public",
    val version: Int = 1
)