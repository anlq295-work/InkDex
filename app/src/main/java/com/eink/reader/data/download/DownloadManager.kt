package com.eink.reader.data.download

import android.content.Context
import android.os.Environment
import com.eink.reader.data.model.ChapterItem
import com.eink.reader.data.model.MangaItem
import com.eink.reader.data.repository.MangaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class DownloadStatus {
    object NotDownloaded : DownloadStatus()
    data class Downloading(val progress: Float, val current: Int, val total: Int) : DownloadStatus()
    object Downloaded : DownloadStatus()
    data class Failed(val error: String) : DownloadStatus()
}

data class DownloadedChapter(
    val file: File,
    val mangaId: String,
    val mangaTitle: String,
    val chapterId: String,
    val chapterTitle: String,
    val sizeBytes: Long
)

data class DownloadedManga(
    val mangaId: String,
    val title: String,
    val author: String?,
    val coverFile: File?,
    val description: String?,
    val chapters: List<DownloadedChapter>
)

data class StorageLocation(
    val name: String,
    val path: String,
    val isRemovable: Boolean,
    val freeSpaceBytes: Long
)

class DownloadManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _downloadStatusFlow = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatusFlow = _downloadStatusFlow.asStateFlow()

    // Thư mục lưu trữ truyện: mangadex-download hoặc thư mục do người dùng chỉ định
    val downloadBaseDir: File
        get() {
            val customPath = com.eink.reader.data.repository.SettingsManager(context).downloadStoragePath
            if (customPath.isNotBlank()) {
                val customDir = File(customPath)
                if (customDir.exists() || customDir.mkdirs()) {
                    return customDir
                }
            }
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val target = File(downloadsDir, "mangadex-download")
            return if (target.exists() || target.mkdirs()) {
                target
            } else {
                File(context.getExternalFilesDir(null), "mangadex-download").apply { mkdirs() }
            }
        }

    fun getAvailableStorageLocations(): List<StorageLocation> {
        val list = mutableListOf<StorageLocation>()
        val defaultDownloads = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "mangadex-download")
        list.add(
            StorageLocation(
                name = "Bộ nhớ trong (Downloads/mangadex-download)",
                path = defaultDownloads.absolutePath,
                isRemovable = false,
                freeSpaceBytes = defaultDownloads.freeSpace
            )
        )

        // Kiểm tra các phân vùng thẻ nhớ ngoài
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (dir in externalDirs) {
                if (dir != null) {
                    val path = dir.absolutePath
                    if (Environment.isExternalStorageRemovable(dir) || !path.contains("emulated")) {
                        val parts = path.split("/Android/")
                        val sdRoot = parts.firstOrNull() ?: path
                        val sdTarget = File(sdRoot, "mangadex-download")
                        if (list.none { it.path == sdTarget.absolutePath }) {
                            list.add(
                                StorageLocation(
                                    name = "Thẻ nhớ ngoài SD (${File(sdRoot).name})",
                                    path = sdTarget.absolutePath,
                                    isRemovable = true,
                                    freeSpaceBytes = dir.freeSpace
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { f ->
                    if (f.isDirectory && f.name != "emulated" && f.name != "self" && !f.name.startsWith(".")) {
                        val sdPath = File(f, "mangadex-download").absolutePath
                        if (list.none { it.path == sdPath }) {
                            list.add(
                                StorageLocation(
                                    name = "Thẻ nhớ SD (${f.name})",
                                    path = sdPath,
                                    isRemovable = true,
                                    freeSpaceBytes = f.freeSpace
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return list
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    fun getChapterCbzFile(mangaTitle: String, chapterTitle: String): File {
        val mangaFolder = File(downloadBaseDir, sanitizeFilename(mangaTitle))
        return File(mangaFolder, "${sanitizeFilename(chapterTitle)}.cbz")
    }

    fun isChapterDownloaded(mangaTitle: String, chapterTitle: String): Boolean {
        val file = getChapterCbzFile(mangaTitle, chapterTitle)
        return file.exists() && file.length() > 1024
    }

    suspend fun downloadChapter(
        repository: MangaRepository,
        manga: MangaItem,
        chapter: ChapterItem
    ): Result<File> = withContext(Dispatchers.IO) {
        val chapterId = chapter.id
        val mangaTitle = manga.displayTitle
        val chapterTitle = chapter.displayTitle

        val mangaFolder = File(downloadBaseDir, sanitizeFilename(mangaTitle))
        if (!mangaFolder.exists()) mangaFolder.mkdirs()

        val cbzFile = File(mangaFolder, "${sanitizeFilename(chapterTitle)}.cbz")
        val tempFile = File(mangaFolder, "${sanitizeFilename(chapterTitle)}.cbz.tmp")

        try {
            updateStatus(chapterId, DownloadStatus.Downloading(0.05f, 0, 0))

            // Lưu metadata truyện manga_info.json để Thư viện đọc offline
            saveMangaMetadata(manga, mangaFolder, repository)

            // Lấy URLs của các trang ảnh (áp dụng proxy Reverse Proxy nếu có)
            val pageUrlsResult = repository.getChapterPageUrls(chapterId, false)
            val pageUrls = pageUrlsResult.getOrThrow()
            if (pageUrls.isEmpty()) {
                throw IOException("Danh sách trang trống")
            }

            val totalPages = pageUrls.size

            ZipOutputStream(FileOutputStream(tempFile).buffered()).use { zipOut ->
                // 1. Thêm ComicInfo.xml
                val comicInfoXml = buildComicInfoXml(manga, chapter, totalPages)
                zipOut.putNextEntry(ZipEntry("ComicInfo.xml"))
                zipOut.write(comicInfoXml.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // 2. Tải và ghi từng trang ảnh vào ZIP
                for ((index, url) in pageUrls.withIndex()) {
                    val pageNum = index + 1
                    val progress = (pageNum.toFloat() / totalPages) * 0.9f + 0.05f
                    updateStatus(chapterId, DownloadStatus.Downloading(progress, pageNum, totalPages))

                    val entryName = String.format("%03d.jpg", pageNum)
                    zipOut.putNextEntry(ZipEntry(entryName))

                    val request = Request.Builder().url(url).build()
                    val response = httpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        throw IOException("Lỗi tải trang $pageNum (HTTP ${response.code})")
                    }

                    response.body?.byteStream()?.use { input ->
                        input.copyTo(zipOut)
                    } ?: throw IOException("Ảnh trang $pageNum rỗng")

                    zipOut.closeEntry()
                }
            }

            // Hoàn tất tải: đổi tên file tạm thành .cbz chính thức
            if (cbzFile.exists()) cbzFile.delete()
            tempFile.renameTo(cbzFile)

            updateStatus(chapterId, DownloadStatus.Downloaded)
            Result.success(cbzFile)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            updateStatus(chapterId, DownloadStatus.Failed(e.localizedMessage ?: "Lỗi tải"))
            Result.failure(e)
        }
    }

    private fun updateStatus(chapterId: String, status: DownloadStatus) {
        val current = _downloadStatusFlow.value.toMutableMap()
        current[chapterId] = status
        _downloadStatusFlow.value = current
    }

    private fun saveMangaMetadata(manga: MangaItem, folder: File, repository: MangaRepository) {
        val infoFile = File(folder, "manga_info.json")
        if (!infoFile.exists()) {
            val json = JSONObject().apply {
                put("id", manga.id)
                put("title", manga.displayTitle)
                put("author", manga.authorName ?: "")
                put("description", manga.displayDescription)
                put("status", manga.attributes.status ?: "")
                put("year", manga.attributes.year ?: 0)
            }
            infoFile.writeText(json.toString(2), Charsets.UTF_8)
        }

        // Tải ảnh bìa lưu offline
        val coverFile = File(folder, "cover.jpg")
        if (!coverFile.exists()) {
            val coverUrl = manga.getCoverUrl(repository.settingsManager.apiBaseUrl)
            if (!coverUrl.isNullOrBlank()) {
                try {
                    val request = Request.Builder().url(coverUrl).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.byteStream()?.use { input ->
                                coverFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun buildComicInfoXml(manga: MangaItem, chapter: ChapterItem, pageCount: Int): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                <Title>${escapeXml(chapter.displayTitle)}</Title>
                <Series>${escapeXml(manga.displayTitle)}</Series>
                <Number>${chapter.attributes.chapter ?: ""}</Number>
                <Volume>${chapter.attributes.volume ?: ""}</Volume>
                <Summary>${escapeXml(manga.displayDescription)}</Summary>
                <Writer>${escapeXml(manga.authorName ?: "")}</Writer>
                <PageCount>$pageCount</PageCount>
                <Manga>Yes</Manga>
            </ComicInfo>
        """.trimIndent()
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    // Quét toàn bộ thư mục mangadex-download để lấy danh sách truyện cho tab Thư viện
    fun getDownloadedMangaList(): List<DownloadedManga> {
        val result = mutableListOf<DownloadedManga>()
        val base = downloadBaseDir
        if (!base.exists() || !base.isDirectory) return emptyList()

        val mangaFolders = base.listFiles { f -> f.isDirectory } ?: return emptyList()

        for (folder in mangaFolders) {
            val cbzFiles = folder.listFiles { f -> f.extension.equals("cbz", ignoreCase = true) }
            if (cbzFiles.isNullOrEmpty()) continue

            var mangaId = folder.name
            var title = folder.name
            var author: String? = null
            var description: String? = null

            val infoFile = File(folder, "manga_info.json")
            if (infoFile.exists()) {
                try {
                    val json = JSONObject(infoFile.readText(Charsets.UTF_8))
                    mangaId = json.optString("id", mangaId)
                    title = json.optString("title", title)
                    author = json.optString("author", null)
                    description = json.optString("description", null)
                } catch (_: Exception) {}
            }

            val coverFile = File(folder, "cover.jpg").takeIf { it.exists() }

            val chapters = cbzFiles.map { file ->
                val chTitle = file.nameWithoutExtension
                DownloadedChapter(
                    file = file,
                    mangaId = mangaId,
                    mangaTitle = title,
                    chapterId = file.absolutePath, // Dùng đường dẫn file làm ID offline
                    chapterTitle = chTitle,
                    sizeBytes = file.length()
                )
            }.sortedBy { it.chapterTitle }

            result.add(
                DownloadedManga(
                    mangaId = mangaId,
                    title = title,
                    author = author,
                    coverFile = coverFile,
                    description = description,
                    chapters = chapters
                )
            )
        }

        return result.sortedBy { it.title }
    }

    fun deleteChapter(file: File): Boolean {
        return try {
            val parent = file.parentFile
            val deleted = file.delete()
            // Nếu folder không còn file cbz nào thì có thể xoá luôn cả thư mục truyện
            if (parent != null && parent.isDirectory) {
                val remaining = parent.listFiles { f -> f.extension.equals("cbz", ignoreCase = true) }
                if (remaining.isNullOrEmpty()) {
                    parent.deleteRecursively()
                }
            }
            deleted
        } catch (_: Exception) {
            false
        }
    }
}
