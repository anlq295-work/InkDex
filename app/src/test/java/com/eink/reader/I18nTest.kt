package com.eink.reader

import com.eink.reader.data.model.MangaAttributes
import com.eink.reader.data.model.MangaItem
import com.eink.reader.util.AppLanguage
import com.eink.reader.util.I18n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class I18nTest {

    @After
    fun tearDown() {
        I18n.currentLanguageCode = "vi"
        I18n.clearPatch()
    }

    @Test
    fun testAllLanguagesHaveValidStrings() {
        val codes = listOf("vi", "en", "fr", "es", "zh", "ko")
        for (code in codes) {
            val strings = I18n.getStrings(code)
            assertNotNull(strings)
            assertEquals(code, strings.langCode)
            assertNotNull(strings.navExplore)
            assertNotNull(strings.navLibrary)
            assertNotNull(strings.navSettings)
            assertNotNull(strings.searchPlaceholder)
            assertNotNull(strings.follow)
            assertNotNull(strings.readingStatus)
        }
    }

    @Test
    fun testAppLanguageEnumFromCode() {
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromCode("vi"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromCode("fr"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromCode("es"))
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromCode("zh"))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromCode("ko"))
        // Case insensitive and fallback
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("EN"))
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromCode("unknown"))
    }

    @Test
    fun testMangaItemPreferredTitleAndDescription() {
        val manga = MangaItem(
            id = "test-multilang",
            type = "manga",
            attributes = MangaAttributes(
                title = mapOf("ja" to "原題"),
                altTitles = listOf(
                    mapOf("en" to "English Title"),
                    mapOf("vi" to "Tiêu đề tiếng Việt"),
                    mapOf("fr" to "Titre français"),
                    mapOf("es" to "Título español")
                ),
                description = mapOf(
                    "en" to "English description",
                    "vi" to "Mô tả tiếng Việt",
                    "fr" to "Description en français"
                ),
                originalLanguage = "ja"
            )
        )

        // Test preferred language resolution
        assertEquals("Tiêu đề tiếng Việt", manga.getDisplayTitle("vi"))
        assertEquals("English Title", manga.getDisplayTitle("en"))
        assertEquals("Titre français", manga.getDisplayTitle("fr"))
        assertEquals("Título español", manga.getDisplayTitle("es"))
        // Fallback to en/original when requested lang not in altTitles
        assertEquals("English Title", manga.getDisplayTitle("zh"))

        // Test preferred description resolution
        assertEquals("Mô tả tiếng Việt", manga.getDisplayDescription("vi"))
        assertEquals("English description", manga.getDisplayDescription("en"))
        assertEquals("Description en français", manga.getDisplayDescription("fr"))
        // Fallback when not available
        assertEquals("English description", manga.getDisplayDescription("es"))
    }
}
