package com.eink.reader.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.eink.reader.data.api.DoHDns
import com.eink.reader.data.model.ChapterItem
import com.eink.reader.data.model.MangaItem
import com.eink.reader.data.repository.MangaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    // OkHttpClient hỗ trợ DNS over HTTPS (DoH) và MangaDex Referer/User-Agent
    private val httpClient: OkHttpClient by lazy {
        val settings = com.eink.reader.data.repository.SettingsManager(context)
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .dns(DoHDns {
                if (settings.useDoH) settings.dohProvider else "system"
            })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "InkDex-Reader/1.0 (Android; Color E-Ink Manga Reader)")
                    .header("Referer", "https://mangadex.org/")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val _downloadStatusFlow = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatusFlow = _downloadStatusFlow.asStateFlow()

    /**
     * Kiểm tra thực tế xem một thư mục có quyền tạo và ghi file hay không.
     */
    fun isDirectoryWritable(dir: File): Boolean {
        return try {
            if (!dir.exists()) {
                if (!dir.mkdirs()) return false
            }
            val testFile = File(dir, ".eink_test_" + System.currentTimeMillis() + ".tmp")
            val created = testFile.createNewFile()
            if (created) {
                testFile.delete()
                true
            } else {
                testFile.exists() && testFile.delete()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Kiểm tra quyền quản lý tất cả tệp (MANAGE_EXTERNAL_STORAGE) trên Android 11+.
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Mở trang cài đặt quyền Quản lý tất cả tệp trong hệ thống.
     */
    fun openAllFilesAccessSetting(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:" + context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }

    /**
     * Tìm thư mục app chuyên dụng trên Thẻ nhớ ngoài SD (luôn có quyền ghi 100% không cần xin quyền).
     */
    fun getSdCardAppFilesDir(): File? {
        try {
            val dirs = context.getExternalFilesDirs(null)
            for (dir in dirs) {
                if (dir != null) {
                    val path = dir.absolutePath
                    if (Environment.isExternalStorageRemovable(dir) || !path.contains("emulated")) {
                        return dir
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Thư mục lưu trữ truyện hiệu dụng: Tự động đảm bảo ghi đúng vào Thẻ nhớ SD nếu được chọn,
     * ngăn chặn tuyệt đối việc âm thầm rớt về bộ nhớ trong máy.
     */
    val downloadBaseDir: File
        get() {
            val customPath = com.eink.reader.data.repository.SettingsManager(context).downloadStoragePath.trim()
            if (customPath.isNotBlank()) {
                val customDir = File(customPath)
                if (isDirectoryWritable(customDir)) {
                    return customDir
                }
                // Nếu đường dẫn tùy chỉnh nằm trên Thẻ nhớ SD (chứa /storage/ và không chứa emulated)
                // nhưng bị Android chặn quyền ghi ở thư mục gốc (do chưa cấp All Files Access),
                // tự động chuyển sang thư mục app chuyên dụng trên CHÍNH THẺ NHỚ ĐÓ:
                // /storage/XXXX-XXXX/Android/data/com.eink.reader/files/mangadex-download
                if (customPath.contains("/storage/") && !customPath.contains("emulated")) {
                    val sdAppDir = getSdCardAppFilesDir()
                    if (sdAppDir != null) {
                        val sdTarget = File(sdAppDir, "mangadex-download")
                        if (isDirectoryWritable(sdTarget)) {
                            return sdTarget
                        }
                    }
                }
            }

            // Mặc định: Thư mục Downloads chung của máy
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val defaultTarget = File(downloadsDir, "mangadex-download")
            if (isDirectoryWritable(defaultTarget)) {
                return defaultTarget
            }

            // Dự phòng an toàn: Thư mục Files của ứng dụng
            val fallback = File(context.getExternalFilesDir(null), "mangadex-download")
            fallback.mkdirs()
            return fallback
        }

    /**
     * Liệt kê danh sách các vị trí lưu trữ thực tế (Bộ nhớ máy & Thẻ nhớ SD).
     */
    fun getAvailableStorageLocations(): List<StorageLocation> {
        val list = mutableListOf<StorageLocation>()

        // 1. Bộ nhớ trong máy (Downloads/mangadex-download)
        val defaultDownloads = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "mangadex-download")
        list.add(
            StorageLocation(
                name = "Bộ nhớ máy (Downloads/mangadex-download)",
                path = defaultDownloads.absolutePath,
                isRemovable = false,
                freeSpaceBytes = defaultDownloads.freeSpace.takeIf { it > 0 } ?: context.filesDir.freeSpace
            )
        )

        // 2. Thẻ nhớ ngoài SD (Quét từ getExternalFilesDirs)
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (dir in externalDirs) {
                if (dir != null) {
                    val path = dir.absolutePath
                    val isRemovable = Environment.isExternalStorageRemovable(dir) || !path.contains("emulated")
                    if (isRemovable) {
                        // Trích xuất mã ID thẻ nhớ (ví dụ: 1234-5678)
                        val sdId = path.substringAfter("/storage/").substringBefore("/")
                        val sdLabel = if (sdId.isNotBlank() && sdId != "emulated") "Thẻ nhớ SD ($sdId)" else "Thẻ nhớ ngoài SD"

                        // Vị trí 2.1: Thư mục ứng dụng trên thẻ nhớ (Luôn có quyền ghi 100% out-of-the-box)
                        val sdAppTarget = File(dir, "mangadex-download")
                        if (list.none { it.path == sdAppTarget.absolutePath }) {
                            list.add(
                                StorageLocation(
                                    name = "$sdLabel - Tối ưu quyền ghi (Khuyên dùng)",
                                    path = sdAppTarget.absolutePath,
                                    isRemovable = true,
                                    freeSpaceBytes = dir.freeSpace
                                )
                            )
                        }

                        // Vị trí 2.2: Thư mục gốc trên thẻ nhớ (/storage/XXXX-XXXX/mangadex-download)
                        val parts = path.split("/Android/")
                        val sdRoot = parts.firstOrNull() ?: path
                        val sdRootTarget = File(sdRoot, "mangadex-download")
                        if (list.none { it.path == sdRootTarget.absolutePath }) {
                            list.add(
                                StorageLocation(
                                    name = "$sdLabel - Thư mục gốc thẻ nhớ",
                                    path = sdRootTarget.absolutePath,
                                    isRemovable = true,
                                    freeSpaceBytes = dir.freeSpace
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Quét thêm /storage cho các thiết bị máy đọc sách chạy Android tùy biến
        try {
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { f ->
                    if (f.isDirectory && f.name != "emulated" && f.name != "self" && !f.name.startsWith(".")) {
                        val sdRootTarget = File(f, "mangadex-download")
                        if (list.none { it.path == sdRootTarget.absolutePath || it.path.startsWith(f.absolutePath) }) {
                            list.add(
                                StorageLocation(
                                    name = "Thẻ nhớ ngoài (" + f.name + ")",
                                    path = sdRootTarget.absolutePath,
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
        return File(mangaFolder, sanitizeFilename(chapterTitle) + ".cbz")
    }

    fun isChapterDownloaded(mangaTitle: String, chapterTitle: String): Boolean {
        val safeManga = sanitizeFilename(mangaTitle)
        val safeChapter = sanitizeFilename(chapterTitle) + ".cbz"

        // Kiểm tra thư mục hiện tại
        val currentFile = File(File(downloadBaseDir, safeManga), safeChapter)
        if (currentFile.exists() && currentFile.length() > 1024) return true

        // Kiểm tra tất cả các vị trí lưu trữ khả dụng khác
        for (loc in getAvailableStorageLocations()) {
            val otherFile = File(File(loc.path, safeManga), safeChapter)
            if (otherFile.exists() && otherFile.length() > 1024) return true
        }

        return false
    }

    suspend fun downloadChapter(
        repository: MangaRepository,
        manga: MangaItem,
        chapter: ChapterItem
    ): Result<File> = withContext(Dispatchers.IO) {
        val chapterId = chapter.id
        val mangaTitle = manga.displayTitle
        val chapterTitle = chapter.displayTitle

        val baseDir = downloadBaseDir
        if (!isDirectoryWritable(baseDir)) {
            val errMsg = "Không có quyền ghi vào thư mục: " + baseDir.absolutePath + ". Vui lòng cấp quyền Quản lý tệp hoặc chọn Thẻ nhớ SD (Tối ưu quyền ghi)."
            updateStatus(chapterId, DownloadStatus.Failed(errMsg))
            return@withContext Result.failure(IOException(errMsg))
        }

        val mangaFolder = File(baseDir, sanitizeFilename(mangaTitle))
        if (!mangaFolder.exists()) {
            if (!mangaFolder.mkdirs() && !mangaFolder.exists()) {
                val errMsg = "Không thể tạo thư mục truyện: " + mangaFolder.absolutePath
                updateStatus(chapterId, DownloadStatus.Failed(errMsg))
                return@withContext Result.failure(IOException(errMsg))
            }
        }

        val cbzFile = File(mangaFolder, sanitizeFilename(chapterTitle) + ".cbz")
        val tempFile = File(mangaFolder, sanitizeFilename(chapterTitle) + ".cbz.tmp")

        try {
            updateStatus(chapterId, DownloadStatus.Downloading(0.05f, 0, 0))

            // Lưu metadata truyện manga_info.json để Thư viện đọc offline
            saveMangaMetadata(manga, mangaFolder, repository)

            // Lấy URLs của các trang ảnh
            val pageUrlsResult = repository.getChapterPageUrls(chapterId, false)
            val pageUrls = pageUrlsResult.getOrThrow()
            if (pageUrls.isEmpty()) {
                throw IOException("Danh sách trang truyện trống từ máy chủ")
            }

            val totalPages = pageUrls.size

            ZipOutputStream(FileOutputStream(tempFile).buffered()).use { zipOut ->
                // 1. Thêm ComicInfo.xml
                val comicInfoXml = buildComicInfoXml(manga, chapter, totalPages)
                zipOut.putNextEntry(ZipEntry("ComicInfo.xml"))
                zipOut.write(comicInfoXml.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // 2. Tải và ghi từng trang ảnh vào ZIP kèm cơ chế thử lại và proxy fallback
                for ((index, url) in pageUrls.withIndex()) {
                    val pageNum = index + 1
                    val progress = (pageNum.toFloat() / totalPages) * 0.9f + 0.05f
                    updateStatus(chapterId, DownloadStatus.Downloading(progress, pageNum, totalPages))

                    val entryName = String.format("%03d.jpg", pageNum)
                    zipOut.putNextEntry(ZipEntry(entryName))

                    var downloadedBytes: ByteArray? = null
                    var lastError: Exception? = null

                    for (attempt in 1..3) {
                        try {
                            val targetUrl = if (attempt == 3) {
                                // Fallback sang Cloudflare Worker proxy ở lần thử thứ 3 nếu direct CDN bị lỗi mạng
                                val proxyBase = repository.settingsManager.apiBaseUrl.trim().trimEnd('/')
                                if (!proxyBase.contains("api.mangadex.org") && !url.startsWith(proxyBase)) {
                                    val pathAfterHost = url.substringAfter("mangadex.org/").substringAfter(".org/")
                                    proxyBase + "/" + pathAfterHost
                                } else {
                                    url
                                }
                            } else {
                                url
                            }

                            val request = Request.Builder().url(targetUrl).build()
                            val response = httpClient.newCall(request).execute()
                            if (response.isSuccessful) {
                                val bytes = response.body?.bytes()
                                if (bytes != null && bytes.isNotEmpty()) {
                                    downloadedBytes = bytes
                                    response.close()
                                    break
                                }
                            }
                            response.close()
                        } catch (e: Exception) {
                            lastError = e
                            delay(400L * attempt)
                        }
                    }

                    val finalBytes = downloadedBytes
                        ?: throw IOException("Lỗi tải trang " + pageNum + "/" + totalPages + " sau 3 lần thử (" + (lastError?.localizedMessage ?: "Máy chủ ngắt kết nối") + ")")

                    zipOut.write(finalBytes)
                    zipOut.closeEntry()
                }
            }

            // Hoàn tất tải: đổi tên file tạm thành .cbz chính thức
            if (cbzFile.exists()) cbzFile.delete()
            if (!tempFile.renameTo(cbzFile)) {
                // Fallback copy nếu renameTo thất bại trên một số filesystem SD card
                tempFile.inputStream().use { input ->
                    cbzFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.delete()
            }

            updateStatus(chapterId, DownloadStatus.Downloaded)
            Result.success(cbzFile)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            val cleanMsg = e.localizedMessage ?: "Lỗi không xác định khi tải chương"
            updateStatus(chapterId, DownloadStatus.Failed(cleanMsg))
            Result.failure(e)
        }
    }

    private fun updateStatus(chapterId: String, status: DownloadStatus) {
        val current = _downloadStatusFlow.value.toMutableMap()
        current[chapterId] = status
        _downloadStatusFlow.value = current
    }

    private fun saveMangaMetadata(manga: MangaItem, folder: File, repository: MangaRepository) {
        try {
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
                    val request = Request.Builder().url(coverUrl).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.byteStream()?.use { input ->
                                coverFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun buildComicInfoXml(manga: MangaItem, chapter: ChapterItem, pageCount: Int): String {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<ComicInfo xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\n" +
            "    <Title>" + escapeXml(chapter.displayTitle) + "</Title>\n" +
            "    <Series>" + escapeXml(manga.displayTitle) + "</Series>\n" +
            "    <Number>" + (chapter.attributes.chapter ?: "") + "</Number>\n" +
            "    <Volume>" + (chapter.attributes.volume ?: "") + "</Volume>\n" +
            "    <Summary>" + escapeXml(manga.displayDescription) + "</Summary>\n" +
            "    <Writer>" + escapeXml(manga.authorName ?: "") + "</Writer>\n" +
            "    <PageCount>" + pageCount + "</PageCount>\n" +
            "    <Manga>Yes</Manga>\n" +
            "</ComicInfo>"
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    // Quét toàn bộ các thư mục lưu trữ (Bộ nhớ máy & Thẻ nhớ SD) để hiển thị đầy đủ trong Thư viện
    fun getDownloadedMangaList(): List<DownloadedManga> {
        val result = mutableListOf<DownloadedManga>()
        val scannedDirs = mutableSetOf<String>()

        val dirsToScan = mutableListOf<File>()
        dirsToScan.add(downloadBaseDir)

        getAvailableStorageLocations().forEach { loc ->
            val locDir = File(loc.path)
            if (locDir.exists() && locDir.isDirectory) {
                dirsToScan.add(locDir)
            }
        }

        for (base in dirsToScan) {
            val canonPath = try { base.canonicalPath } catch (_: Exception) { base.absolutePath }
            if (scannedDirs.contains(canonPath)) continue
            scannedDirs.add(canonPath)

            if (!base.exists() || !base.isDirectory) continue

            val mangaFolders = base.listFiles { f -> f.isDirectory } ?: continue

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
                        author = json.optString("author").takeIf { it.isNotEmpty() }
                        description = json.optString("description").takeIf { it.isNotEmpty() }
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
                }.sortedWith { a, b -> com.eink.reader.util.NaturalOrderComparator.compare(a.chapterTitle, b.chapterTitle) }

                val existing = result.find { it.mangaId == mangaId }
                if (existing != null) {
                    val mergedChapters = (existing.chapters + chapters).distinctBy { it.file.name }
                        .sortedWith { a, b -> com.eink.reader.util.NaturalOrderComparator.compare(a.chapterTitle, b.chapterTitle) }
                    result.remove(existing)
                    result.add(existing.copy(chapters = mergedChapters))
                } else {
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
            }
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
