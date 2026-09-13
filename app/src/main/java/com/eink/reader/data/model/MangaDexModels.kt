package com.eink.reader.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Serializer an toàn cho Map<String, String>:
 * Tự động chuyển đổi nếu MangaDex trả về mảng rỗng [] thay vì object {} khi không có mô tả hoặc tiêu đề.
 */
object SafeStringMapSerializer : KSerializer<Map<String, String>> {
    override val descriptor: SerialDescriptor = MapSerializer(String.serializer(), String.serializer()).descriptor

    override fun serialize(encoder: Encoder, value: Map<String, String>) {
        MapSerializer(String.serializer(), String.serializer()).serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): Map<String, String> {
        val jsonInput = decoder as? JsonDecoder ?: return emptyMap()
        return when (val element = jsonInput.decodeJsonElement()) {
            is JsonObject -> {
                element.mapNotNull { (k, v) ->
                    if (v is JsonPrimitive && v.isString) k to v.content
                    else null
                }.toMap()
            }
            is JsonArray -> emptyMap()
            else -> emptyMap()
        }
    }
}

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
    fun getDisplayTitle(preferredLang: String = com.eink.reader.util.I18n.currentLanguageCode): String {
        // 1. Tiêu đề theo ngôn ngữ ưu tiên người dùng
        val prefTitle = attributes.title[preferredLang]?.takeIf { it.isNotBlank() }
            ?: attributes.altTitles.firstNotNullOfOrNull { it[preferredLang]?.takeIf { s -> s.isNotBlank() } }
        if (!prefTitle.isNullOrBlank()) return prefTitle

        // 2. Tiếng Anh (nếu ngôn ngữ chọn không phải en)
        if (preferredLang != "en") {
            val enTitle = attributes.title["en"]?.takeIf { it.isNotBlank() }
                ?: attributes.altTitles.firstNotNullOfOrNull { it["en"]?.takeIf { s -> s.isNotBlank() } }
            if (!enTitle.isNullOrBlank()) return enTitle
        }

        // 3. Tiếng Việt (nếu ngôn ngữ chọn không phải vi)
        if (preferredLang != "vi") {
            val viTitle = attributes.title["vi"]?.takeIf { it.isNotBlank() }
                ?: attributes.altTitles.firstNotNullOfOrNull { it["vi"]?.takeIf { s -> s.isNotBlank() } }
            if (!viTitle.isNullOrBlank()) return viTitle
        }

        // 4. Tiêu đề theo ngôn ngữ gốc của truyện (ví dụ: "ja", "ko", "zh", "fr", "es", "ru"...)
        val origLang = attributes.originalLanguage
        if (!origLang.isNullOrBlank()) {
            val origTitle = attributes.title[origLang]?.takeIf { it.isNotBlank() }
                ?: attributes.altTitles.firstNotNullOfOrNull { it[origLang]?.takeIf { s -> s.isNotBlank() } }
            if (!origTitle.isNullOrBlank()) return origTitle
        }

        // 5. Phiên âm Nhật (ja-ro)
        val jaRoTitle = attributes.title["ja-ro"]?.takeIf { it.isNotBlank() }
            ?: attributes.altTitles.firstNotNullOfOrNull { it["ja-ro"]?.takeIf { s -> s.isNotBlank() } }
        if (!jaRoTitle.isNullOrBlank()) return jaRoTitle

        // 6. Tiêu đề đầu tiên bất kỳ trong map title chính
        val firstTitle = attributes.title.values.firstOrNull { it.isNotBlank() }
        if (!firstTitle.isNullOrBlank()) return firstTitle

        // 7. Tiêu đề đầu tiên bất kỳ trong altTitles
        val firstAlt = attributes.altTitles.firstNotNullOfOrNull { alt ->
            alt.values.firstOrNull { it.isNotBlank() }
        }
        if (!firstAlt.isNullOrBlank()) return firstAlt

        return "Untitled"
    }

    val displayTitle: String
        get() = getDisplayTitle(com.eink.reader.util.I18n.currentLanguageCode)

    fun getDisplayDescription(preferredLang: String = com.eink.reader.util.I18n.currentLanguageCode): String {
        val pref = attributes.description[preferredLang]?.takeIf { it.isNotBlank() }
        if (!pref.isNullOrBlank()) return pref
        if (preferredLang != "en") {
            val en = attributes.description["en"]?.takeIf { it.isNotBlank() }
            if (!en.isNullOrBlank()) return en
        }
        if (preferredLang != "vi") {
            val vi = attributes.description["vi"]?.takeIf { it.isNotBlank() }
            if (!vi.isNullOrBlank()) return vi
        }
        val orig = attributes.originalLanguage?.let { attributes.description[it]?.takeIf { s -> s.isNotBlank() } }
        if (!orig.isNullOrBlank()) return orig
        return attributes.description.values.firstOrNull { it.isNotBlank() } ?: ""
    }

    val displayDescription: String
        get() = getDisplayDescription(com.eink.reader.util.I18n.currentLanguageCode)

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

    val isWebtoon: Boolean
        get() {
            val hasWebtoonTag = attributes.tags.any { tag ->
                val name = tag.attributes.name["en"] ?: tag.attributes.name.values.firstOrNull() ?: ""
                name.equals("Long Strip", ignoreCase = true) || name.equals("Web Comic", ignoreCase = true)
            }
            val isKorean = attributes.originalLanguage?.equals("ko", ignoreCase = true) == true
            return hasWebtoonTag || isKorean
        }
}

@Serializable
data class MangaAttributes(
    @Serializable(with = SafeStringMapSerializer::class)
    val title: Map<String, String> = emptyMap(),
    val altTitles: List<@Serializable(with = SafeStringMapSerializer::class) Map<String, String>> = emptyList(),
    @Serializable(with = SafeStringMapSerializer::class)
    val description: Map<String, String> = emptyMap(),
    val originalLanguage: String? = null,
    val availableTranslatedLanguages: List<String> = emptyList(),
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
    @Serializable(with = SafeStringMapSerializer::class)
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