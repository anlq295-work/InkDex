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
}
