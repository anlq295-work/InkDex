package com.eink.reader

import com.eink.reader.util.NaturalOrderComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderComparatorTest {

    @Test
    fun testNaturalOrderSorting() {
        val list = listOf(
            "Chapter 1.cbz",
            "Chapter 12.cbz",
            "Chapter 2.cbz",
            "Chapter 11.cbz",
            "Chapter 10.cbz",
            "Chapter 3.cbz"
        )
        val sorted = list.sortedWith(NaturalOrderComparator)
        val expected = listOf(
            "Chapter 1.cbz",
            "Chapter 2.cbz",
            "Chapter 3.cbz",
            "Chapter 10.cbz",
            "Chapter 11.cbz",
            "Chapter 12.cbz"
        )
        assertEquals(expected, sorted)
    }

    @Test
    fun testChapterNumberParsing() {
        assertEquals(11.0f, NaturalOrderComparator.parseChapterNumber("Ch.11: Tiêu đề") ?: 0f, 0.001f)
        assertEquals(12.0f, NaturalOrderComparator.parseChapterNumber("Chapter 12") ?: 0f, 0.001f)
        assertEquals(1.0f, NaturalOrderComparator.parseChapterNumber("Ch.1") ?: 0f, 0.001f)
        assertEquals(11.5f, NaturalOrderComparator.parseChapterNumber("Ch. 11.5 - Extra") ?: 0f, 0.001f)
        assertEquals(10.0f, NaturalOrderComparator.parseChapterNumber("10.cbz") ?: 0f, 0.001f)
        assertEquals(2.0f, NaturalOrderComparator.parseChapterNumber("Tập 2") ?: 0f, 0.001f)
    }

    @Test
    fun testNotPrefixMatch() {
        val num11 = NaturalOrderComparator.parseChapterNumber("Ch.11: Tiêu đề")
        val num1 = NaturalOrderComparator.parseChapterNumber("Ch.1")
        assertTrue(num11 != num1)
    }

    @Test
    fun testMangaItemDecodingWithArrayDescription() {
        val jsonStr = """
            {
                "id": "test-manga-1",
                "type": "manga",
                "attributes": {
                    "title": { "ja": "葬送のフリーレン" },
                    "altTitles": [
                        { "en": "Frieren: Beyond Journey's End" },
                        { "vi": "Pháp sư tiễn táng Frieren" }
                    ],
                    "description": [],
                    "originalLanguage": "ja"
                },
                "relationships": []
            }
        """.trimIndent()
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
        val manga = json.decodeFromString<com.eink.reader.data.model.MangaItem>(jsonStr)
        // Ưu tiên tiếng Việt nếu có trong altTitles
        assertEquals("Pháp sư tiễn táng Frieren", manga.displayTitle)
        assertEquals("", manga.displayDescription)
    }

    @Test
    fun testMangaItemForeignLanguageOnly() {
        val jsonStr = """
            {
                "id": "test-manga-2",
                "type": "manga",
                "attributes": {
                    "title": {},
                    "altTitles": [
                        { "fr": "Solo Leveling (Français)" },
                        { "ko": "나 혼자만 레벨업" }
                    ],
                    "description": { "fr": "Description en français" },
                    "originalLanguage": "ko"
                },
                "relationships": []
            }
        """.trimIndent()
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
        val manga = json.decodeFromString<com.eink.reader.data.model.MangaItem>(jsonStr)
        // Khi không có vi hay en, lấy theo originalLanguage ("ko")
        assertEquals("나 혼자만 레벨업", manga.displayTitle)
        assertEquals("Description en français", manga.displayDescription)
    }

    @Test
    fun testDecimalChapterSortingAndNavigation() {
        val rawList = listOf(
            "Chapter 26.5.cbz",
            "Chapter 22.cbz",
            "Chapter 21.cbz",
            "Chapter 21.5.cbz"
        )
        val sorted = rawList.sortedWith(NaturalOrderComparator)
        val expected = listOf(
            "Chapter 21.cbz",
            "Chapter 21.5.cbz",
            "Chapter 22.cbz",
            "Chapter 26.5.cbz"
        )
        assertEquals(expected, sorted)

        // Kiểm tra parse số thập phân
        assertEquals(21.5f, NaturalOrderComparator.parseChapterNumber("Ch. 21.5") ?: 0f, 0.001f)
        assertEquals(21.5f, NaturalOrderComparator.parseChapterNumber("Ch. 21,5") ?: 0f, 0.001f)
        assertEquals(22.0f, NaturalOrderComparator.parseChapterNumber("Chapter 22") ?: 0f, 0.001f)
        assertEquals(26.5f, NaturalOrderComparator.parseChapterNumber("Ch. 26.5") ?: 0f, 0.001f)

        // Giả lập logic tìm chương tiếp theo: từ 21.5 phải sang 22, KHÔNG nhảy cóc sang 26.5
        val effChapterNum = 21.5f
        val upcomingNums = sorted.mapNotNull { NaturalOrderComparator.parseChapterNumber(it) }
            .filter { it > effChapterNum + 0.0001f }
        assertEquals(listOf(22.0f, 26.5f), upcomingNums)
        assertEquals(22.0f, upcomingNums.first(), 0.001f)
    }

    @Test
    fun testScopedScanlationGroupSelection() {
        data class MockChapter(val chapter: String, val group: String)

        val allChapters = listOf(
            MockChapter("21", "Group A"),
            MockChapter("21.5", "Group B"),
            MockChapter("22", "Group A"),
            MockChapter("23", "Group A"),
            MockChapter("26.5", "Group B")
        )

        val currentChapter = MockChapter("21.5", "Group B")
        val effNum = 21.5f
        val currentGroup = currentChapter.group

        // Upcoming chapters with number > 21.5
        val upcoming = allChapters.filter {
            val n = NaturalOrderComparator.parseChapterNumber(it.chapter)
            n != null && n > effNum + 0.0001f
        }

        // Logic mới: lấy số chương của chương kế tiếp
        val firstUpcoming = upcoming.first()
        val nextChapterNum = NaturalOrderComparator.parseChapterNumber(firstUpcoming.chapter)!!

        // Gom các ứng viên của đúng chương kế tiếp đó
        val immediateNextCandidates = upcoming.takeWhile {
            val n = NaturalOrderComparator.parseChapterNumber(it.chapter)
            n != null && kotlin.math.abs(n - nextChapterNum) < 0.001f
        }

        val chosen = immediateNextCandidates.firstOrNull { it.group == currentGroup }
            ?: immediateNextCandidates.first()

        // Kết quả phải là chương 22 (Group A), KHÔNG ĐƯỢC LÀ chương 26.5 (Group B)!
        assertEquals("22", chosen.chapter)
        assertEquals("Group A", chosen.group)
    }

    @Test
    fun testRemoteConfigJsonDecoding() {
        val jsonStr = """
            {
                "config_version": 1,
                "updated_at": "2026-09-13T12:00:00Z",
                "system_notice": {
                    "enabled": true,
                    "title": "Thông báo",
                    "message": "Hệ thống hoạt động bình thường"
                },
                "network": {
                    "backup_proxy_urls": ["https://proxy1.example.com", "https://proxy2.example.com"],
                    "doh_providers": {"cloudflare": "1.1.1.1"}
                },
                "chapter_rules": {
                    "extra_prefixes": ["hồi", "hồi thứ", "phần", "tiết", "màn", "act"],
                    "custom_regex": null
                }
            }
        """.trimIndent()

        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

        val config = json.decodeFromString<com.eink.reader.data.model.RemoteConfig>(jsonStr)
        assertEquals(1, config.configVersion)
        assertTrue(config.systemNotice.enabled)
        assertEquals("Thông báo", config.systemNotice.title)
        assertEquals(2, config.network.backupProxyUrls.size)
        assertTrue(config.chapterRules.extraPrefixes.contains("hồi"))
    }

    @Test
    fun testDynamicExtraPrefixesParsing() {
        // Trước khi nạp extraPrefixes
        NaturalOrderComparator.extraPrefixes = emptyList()
        val beforeParse = NaturalOrderComparator.parseChapterNumber("Hồi 10: Đại náo Thiên Cung")
        // Mặc định không có tiền tố "hồi", regex có thể bắt fallback số 10 hoặc tiền tố mới
        // Bây giờ gán extraPrefixes từ RemoteConfig
        NaturalOrderComparator.extraPrefixes = listOf("hồi", "hồi thứ", "phần", "tiết", "màn", "act", "episode")

        assertEquals(10.0f, NaturalOrderComparator.parseChapterNumber("Hồi 10: Đại náo Thiên Cung") ?: 0f, 0.001f)
        assertEquals(5.5f, NaturalOrderComparator.parseChapterNumber("Hồi thứ 5.5: Ngoại truyện") ?: 0f, 0.001f)
        assertEquals(3.0f, NaturalOrderComparator.parseChapterNumber("Phần 3: Khởi đầu mới") ?: 0f, 0.001f)
        assertEquals(12.0f, NaturalOrderComparator.parseChapterNumber("Act 12: Climax") ?: 0f, 0.001f)
        assertEquals(24.0f, NaturalOrderComparator.parseChapterNumber("Episode 24") ?: 0f, 0.001f)

        // Reset lại để không ảnh hưởng test khác
        NaturalOrderComparator.extraPrefixes = emptyList()
    }
}

