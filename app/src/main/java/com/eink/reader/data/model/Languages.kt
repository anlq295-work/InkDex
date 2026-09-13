package com.eink.reader.data.model

object Languages {
    private val CODE_TO_NAME = mapOf(
        "vi" to "Tiếng Việt",
        "en" to "English",
        "ja" to "日本語",
        "zh" to "中文 (Giản thể)",
        "zh-hk" to "中文 (Phồn thể)",
        "ko" to "한국어",
        "fr" to "Français",
        "es" to "Español",
        "es-la" to "Español (LatAm)",
        "pt-br" to "Português (Brasil)",
        "pt" to "Português",
        "ru" to "Русский",
        "de" to "Deutsch",
        "id" to "Bahasa Indonesia",
        "it" to "Italiano",
        "th" to "ไทย",
        "pl" to "Polski",
        "ar" to "العربية",
        "tr" to "Türkçe",
        "uk" to "Українська",
        "hi" to "हिन्दी",
        "tl" to "Filipino",
        "my" to "မြန်မာ",
        "ms" to "Bahasa Melayu",
        "nl" to "Nederlands",
        "hu" to "Magyar",
        "cs" to "Čeština",
        "el" to "Ελληνικά",
        "sv" to "Svenska",
        "he" to "עברית",
        "fa" to "فارسی"
    )

    fun getDisplayName(code: String): String {
        return CODE_TO_NAME[code.lowercase()] ?: code.uppercase()
    }

    val ALL_COMMON_LANGUAGES: List<Pair<String, String>> = listOf(
        "vi" to "Tiếng Việt",
        "en" to "English",
        "ja" to "日本語",
        "zh" to "中文 (Giản thể)",
        "zh-hk" to "中文 (Phồn thể)",
        "ko" to "한국어",
        "fr" to "Français",
        "es" to "Español",
        "es-la" to "Español (LatAm)",
        "pt-br" to "Português (Brasil)",
        "pt" to "Português",
        "ru" to "Русский",
        "de" to "Deutsch",
        "id" to "Bahasa Indonesia",
        "it" to "Italiano",
        "th" to "ไทย",
        "pl" to "Polski",
        "ar" to "العربية",
        "tr" to "Türkçe"
    )
}
