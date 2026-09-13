package com.eink.reader.util

import java.io.File
import java.math.BigInteger

object NaturalOrderComparator : Comparator<String> {
    private val tokenRegex = Regex("""\d+|\D+""")

    override fun compare(s1: String?, s2: String?): Int {
        if (s1 == null && s2 == null) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1

        val tokens1 = tokenRegex.findAll(s1).map { it.value }.toList()
        val tokens2 = tokenRegex.findAll(s2).map { it.value }.toList()

        val minSize = minOf(tokens1.size, tokens2.size)
        for (i in 0 until minSize) {
            val t1 = tokens1[i]
            val t2 = tokens2[i]

            if (t1 != t2) {
                val isNum1 = t1.all { it.isDigit() }
                val isNum2 = t2.all { it.isDigit() }

                if (isNum1 && isNum2) {
                    val b1 = t1.toBigIntegerOrNull()
                    val b2 = t2.toBigIntegerOrNull()
                    if (b1 != null && b2 != null) {
                        val numCmp = b1.compareTo(b2)
                        if (numCmp != 0) return numCmp
                    }
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

    /**
     * Trích xuất số chương từ tiêu đề hoặc tên file (ví dụ: Ch.11, Chapter 12, Tập 10, v.v.)
     */
    fun parseChapterNumber(title: String?): Float? {
        if (title.isNullOrBlank()) return null
        val clean = title.trim()
        
        // 1. Ưu tiên tìm tiền tố chương: Ch. 11, Chapter 12, Chap 13, Tập 14...
        val prefixRegex = Regex("""(?i)(?:ch(?:apter)?|c|chap|tập)[\s._-]*([0-9]+(?:\.[0-9]+)?)""")
        prefixRegex.find(clean)?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }

        // 2. Tìm số ở đầu chuỗi hoặc sau dấu phân cách
        val boundaryRegex = Regex("""(?i)(?:^|[\s\[(_-])([0-9]+(?:\.[0-9]+)?)""")
        return boundaryRegex.find(clean)?.groupValues?.get(1)?.toFloatOrNull()
    }
}
