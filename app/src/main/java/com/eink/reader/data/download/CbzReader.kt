package com.eink.reader.data.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

import com.eink.reader.util.NaturalOrderComparator

object CbzReader {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")

    /**
     * Giải nén file CBZ vào thư mục cache để trình đọc lấy danh sách file ảnh theo thứ tự tự nhiên
     */
    suspend fun extractCbzToCache(context: Context, cbzFile: File): List<File> = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "cbz_cache/${cbzFile.nameWithoutExtension}")
        
        // Nếu đã giải nén trước đó và có file ảnh hợp lệ thì tái sử dụng
        if (cacheDir.exists()) {
            val existingImages = cacheDir.listFiles { file ->
                file.isFile && IMAGE_EXTENSIONS.contains(file.extension.lowercase())
            }?.sortedWith(compareByNaturalOrder())
            if (!existingImages.isNullOrEmpty()) {
                return@withContext existingImages
            }
        }

        cacheDir.mkdirs()

        val extractedFiles = mutableListOf<File>()

        ZipFile(cbzFile).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory && IMAGE_EXTENSIONS.contains(File(it.name).extension.lowercase()) }
                .sortedWith { e1, e2 -> NaturalOrderComparator.compare(e1.name, e2.name) }
                .toList()

            for ((index, entry) in entries.withIndex()) {
                val extension = File(entry.name).extension.ifBlank { "jpg" }
                val targetFile = File(cacheDir, String.format("page_%04d.%s", index + 1, extension))

                zip.getInputStream(entry).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                extractedFiles.add(targetFile)
            }
        }

        extractedFiles.sortedWith(compareByNaturalOrder())
    }

    private fun compareByNaturalOrder(): Comparator<File> {
        return NaturalOrderComparator.FileComparator
    }
}
