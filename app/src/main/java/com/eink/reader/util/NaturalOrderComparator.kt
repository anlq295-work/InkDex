package com.eink.reader.util

import java.io.File
import java.math.BigInteger

object NaturalOrderComparator : Comparator<String> {
    private val tokenRegex = Regex("""\d+(?:[.,]\d+)?|\D+""")

    override fun compare(s1: String?, s2: String?): Int {
        if (s1 == null && s2 == null) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1

        // 1. Ưu tiên so sánh trực tiếp theo số chương nếu cả 2 chuỗi đều trích xuất được số chương
        val num1 = parseChapterNumber(s1)
        val num2 = parseChapterNumber(s2)
        if (num1 != null && num2 != null) {
            val diff = num1 - num2
            if (kotlin.math.abs(diff) >= 0.0001f) {
                return num1.compareTo(num2)
            }
        }

        // 2. So sánh tự nhiên theo từng token (hỗ trợ số thực, số nguyên, text)
        val tokens1 = tokenRegex.findAll(s1).map { it.value }.toList()
        val tokens2 = tokenRegex.findAll(s2).map { it.value }.toList()

        val minSize = minOf(tokens1.size, tokens2.size)
        for (i in 0 until minSize) {
            val t1 = tokens1[i]
            val t2 = tokens2[i]

            if (t1 != t2) {
                val d1 = t1.replace(',', '.').toDoubleOrNull()
                val d2 = t2.replace(',', '.').toDoubleOrNull()

                if (d1 != null && d2 != null) {
                    val numCmp = d1.compareTo(d2)
                    if (numCmp != 0) return numCmp
                    val lenCmp = t1.length.compareTo(t2.length)
                    if (lenCmp != 0) return lenCmp
                } else {
                    val strCmp = t1.compareTo(t2, ignoreCase = true)
                    if (strCmp != 0) return strCmp
                    val caseCmp = t1.compareTo(t2)
                    if (caseCmp != 0) return caseCmp
                }
            }
        }

        return tokens1.size.compareTo(tokens2.size)
    }

    val FileComparator = Comparator<File> { f1, f2 ->
        compare(f1?.name, f2?.name)
    }

    @Volatile
    var extraPrefixes: List<String> = emptyList()

    @Volatile
    var customRegex: String? = null

    /**
     * Trích xuất số chương từ tiêu đề hoặc tên file (ví dụ: Ch.11, Chapter 12, Tập 10, Hồi 5, v.v.)
     */
    fun parseChapterNumber(title: String?): Float? {
        if (title.isNullOrBlank()) return null
        val clean = title.trim().replace(',', '.')
        
        // 0. Nếu có custom regex từ Remote Config, ưu tiên thử trước
        customRegex?.let { pattern ->
            try {
                Regex(pattern).find(clean)?.groupValues?.let { g ->
                    if (g.size > 1) g[1].toFloatOrNull()?.let { return it }
                }
            } catch (_: Exception) {}
        }

        // 1. Ưu tiên tìm tiền tố chương: Ch. 11, Chapter 12, Chap 13, Tập 14 + các tiền tố từ Remote Config
        val allPrefixes = if (extraPrefixes.isNotEmpty()) {
            val escapedExtra = extraPrefixes.joinToString("|") { Regex.escape(it) }
            "(?:ch(?:apter)?|c|chap|tập|$escapedExtra)"
        } else {
            "(?:ch(?:apter)?|c|chap|tập)"
        }
        val prefixRegex = Regex("""(?i)(?:^|[\s\[(_-])$allPrefixes[\s._-]*([0-9]+(?:\.[0-9]+)?)""")
        prefixRegex.find(clean)?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }

        // 2. Tìm số ở đầu chuỗi hoặc sau dấu phân cách
        val boundaryRegex = Regex("""(?i)(?:^|[\s\[(_-])([0-9]+(?:\.[0-9]+)?)""")
        return boundaryRegex.find(clean)?.groupValues?.get(1)?.toFloatOrNull()
    }
}
