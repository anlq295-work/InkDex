package com.eink.reader

import com.eink.reader.data.model.ResourcePatch
import com.eink.reader.util.AppLanguage
import com.eink.reader.util.I18n
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ResourceManagerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun testResourcePatchJsonDecoding() {
        val jsonStr = """
            {
                "patch_version": 2,
                "updated_at": "2026-09-13T20:45:00Z",
                "changelog": "Cập nhật từ điển tiếng Việt và bổ sung tiền tố",
                "strings": {
                    "vi": {
                        "continueReading": "TIẾP TỤC ĐỌC",
                        "checkUpdate": "CẬP NHẬT TÀI NGUYÊN"
                    },
                    "en": {
                        "continueReading": "KEEP READING"
                    }
                },
                "chapter_rules": {
                    "extra_prefixes": ["hồi", "phần", "màn", "act"],
                    "custom_regex": null
                }
            }
        """.trimIndent()

        val patch = json.decodeFromString<ResourcePatch>(jsonStr)
        assertEquals(2, patch.patchVersion)
        assertEquals("Cập nhật từ điển tiếng Việt và bổ sung tiền tố", patch.changelog)
        assertNotNull(patch.strings["vi"])
        assertEquals("TIẾP TỤC ĐỌC", patch.strings["vi"]?.get("continueReading"))
        assertEquals("KEEP READING", patch.strings["en"]?.get("continueReading"))
        assertEquals(4, patch.chapterRules?.extraPrefixes?.size)
    }

    @Test
    fun testI18nDynamicPatchingAndRevert() {
        // Reset patch
        I18n.clearPatch()

        val originalVi = I18n.getStrings("vi")
        assertEquals("ĐỌC TIẾP", originalVi.continueReading)

        // Áp dụng dynamic patch
        val patchMap = mapOf(
            "vi" to mapOf(
                "continueReading" to "TIẾP TỤC ĐỌC NGAY",
                "checkUpdate" to "CẬP NHẬT IN-APP NHƯ GAME"
            )
        )
        I18n.applyPatch(patchMap)

        val patchedVi = I18n.getStrings("vi")
        assertEquals("TIẾP TỤC ĐỌC NGAY", patchedVi.continueReading)
        assertEquals("CẬP NHẬT IN-APP NHƯ GAME", patchedVi.checkUpdate)
        // Các trường khác vẫn giữ nguyên
        assertEquals(originalVi.navExplore, patchedVi.navExplore)
        assertEquals(originalVi.searchPlaceholder, patchedVi.searchPlaceholder)

        // Sau khi clear
        I18n.clearPatch()
        val revertedVi = I18n.getStrings("vi")
        assertEquals("ĐỌC TIẾP", revertedVi.continueReading)
    }
}
