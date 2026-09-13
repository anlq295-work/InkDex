package com.eink.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import android.graphics.BitmapFactory
import com.eink.reader.data.model.EInkColorMode
import com.eink.reader.data.repository.MangaRepository
import com.eink.reader.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.eink.reader.util.NaturalOrderComparator

enum class PageScaleMode(val label: String) {
    FIT_SCREEN("Vừa màn hình"),
    FIT_WIDTH("Khớp chiều ngang")
}

@Composable
fun ReaderScreen(
    chapterId: String,
    chapterTitle: String,
    repository: MangaRepository,
    onBackClick: () -> Unit,
    volumeKeyEventFlow: kotlinx.coroutines.flow.SharedFlow<Boolean>? = null,
    mangaId: String? = null,
    mangaTitle: String? = null,
    coverUrl: String? = null,
    initialPage: Int = 1
) {
    var activeChapterId by remember(chapterId) { mutableStateOf(chapterId) }
    var activeChapterTitle by remember(chapterTitle) { mutableStateOf(chapterTitle) }
    var activeMangaTitle by remember(mangaTitle) { mutableStateOf(mangaTitle) }
    var activeCoverUrl by remember(coverUrl) { mutableStateOf(coverUrl) }
    var pageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }

    val defaultMode = remember {
        try {
            EInkColorMode.valueOf(repository.settingsManager.defaultReaderColorMode)
        } catch (e: Exception) {
            EInkColorMode.KALEIDO_3
        }
    }
    var colorMode by remember { mutableStateOf(defaultMode) }
    var isDataSaver by remember { mutableStateOf(false) }
    var isScreenFlashRefreshing by remember { mutableStateOf(false) }
    
    // Tùy chọn tối ưu tỷ lệ màn hình
    var scaleMode by remember { mutableStateOf(PageScaleMode.FIT_SCREEN) }
    var isDualPageMode by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val isOfflineCbz = remember(activeChapterId) {
        activeChapterId.endsWith(".cbz", ignoreCase = true) || java.io.File(activeChapterId).exists()
    }

    // Tự động nhận diện chế độ đọc (Reading Direction):
    // 1. Chế độ riêng từng bộ đã lưu (Per-manga saved preference)
    // 2. Metadata MangaDex (Tag Long Strip, Web Comic, hoặc tiếng Hàn 'ko')
    // 3. Cài đặt mặc định của hệ thống
    val initialReadingMode = remember(mangaId) {
        val savedMode = if (!mangaId.isNullOrBlank()) {
            repository.readingHistoryManager.getRecord(mangaId)?.readingMode
        } else null

        val isWebtoonDetected = if (!mangaId.isNullOrBlank()) {
            repository.tagCacheManager.isWebtoon(mangaId)
        } else false

        when {
            !savedMode.isNullOrBlank() -> savedMode
            isWebtoonDetected -> "VERTICAL"
            else -> repository.settingsManager.readingDirection
        }
    }
    var readingDirection by remember(mangaId) { mutableStateOf(initialReadingMode) }
    val isRtl = readingDirection == "RTL"
    val webtoonListState = rememberLazyListState()

    // Tự động kiểm tra tỷ lệ khung hình ảnh để phát hiện Webtoon / Manhwa (Fallback cho CBZ hoặc truyện chưa kịp lưu tag)
    LaunchedEffect(pageUrls, mangaId) {
        if (pageUrls.isNotEmpty() && readingDirection != "VERTICAL") {
            val savedMode = if (!mangaId.isNullOrBlank()) {
                repository.readingHistoryManager.getRecord(mangaId)?.readingMode
            } else null

            if (savedMode == null) {
                val firstUrl = pageUrls.first()
                if (isOfflineCbz) {
                    try {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(firstUrl, opts)
                        if (opts.outWidth > 0 && opts.outHeight > 0) {
                            val ratio = opts.outHeight.toFloat() / opts.outWidth.toFloat()
                            if (ratio >= 1.6f) {
                                readingDirection = "VERTICAL"
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // Đồng bộ trang hiện tại trong chế độ cuộn dọc Webtoon
    LaunchedEffect(webtoonListState.firstVisibleItemIndex, readingDirection) {
        if (readingDirection == "VERTICAL" && pageUrls.isNotEmpty()) {
            val idx = webtoonListState.firstVisibleItemIndex
            if (idx in pageUrls.indices) {
                currentPageIndex = idx
            }
        }
    }

    // Quản lý danh sách toàn bộ chương để tự động chuyển chương (Auto Next Chapter)
    var chaptersList by remember { mutableStateOf<List<com.eink.reader.data.model.ChapterItem>>(emptyList()) }
    var siblingCbzFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var chapterNoticeMessage by remember { mutableStateOf<String?>(null) }

    // Tải danh sách chương khi đọc online hoặc offline (lấy mọi ngôn ngữ để hỗ trợ chuyển chương chính xác)
    LaunchedEffect(mangaId, activeChapterId) {
        if (!isOfflineCbz && !mangaId.isNullOrBlank()) {
            if (chaptersList.isEmpty()) {
                val res = repository.getChapters(mangaId, emptyList())
                res.onSuccess { list ->
                    // Sắp xếp thứ tự chương tăng dần: Ch.1 -> Ch.2 -> Ch.3...
                    chaptersList = list.sortedWith(
                        compareBy(
                            { it.attributes.chapter?.toFloatOrNull() ?: Float.MAX_VALUE },
                            { it.attributes.publishAt ?: "" }
                        )
                    )
                }
            }
        } else if (isOfflineCbz) {
            val file = java.io.File(activeChapterId)
            val parent = file.parentFile
            if (parent != null && parent.isDirectory) {
                val cbzs = parent.listFiles { f -> f.extension.equals("cbz", ignoreCase = true) }
                    ?.sortedWith(NaturalOrderComparator.FileComparator) ?: emptyList()
                siblingCbzFiles = cbzs
            }
        }
    }

    // Tự động tải bù thông tin manga (tên truyện và ảnh bìa) nếu bị thiếu hoặc không chính xác
    LaunchedEffect(mangaId) {
        if (!isOfflineCbz && !mangaId.isNullOrBlank()) {
            val needsTitle = activeMangaTitle.isNullOrBlank() || activeMangaTitle == "Untitled" || activeMangaTitle?.startsWith("Ch.", ignoreCase = true) == true
            val needsCover = activeCoverUrl.isNullOrBlank()
            if (needsTitle || needsCover) {
                repository.getMangaDetails(mangaId).onSuccess { details ->
                    val resolved = details.displayTitle
                    if (needsTitle && resolved.isNotBlank() && resolved != "Untitled") {
                        activeMangaTitle = resolved
                    }
                    val cover = details.getCoverUrl(repository.settingsManager.apiBaseUrl) ?: details.coverUrl
                    if (needsCover && !cover.isNullOrBlank()) {
                        activeCoverUrl = cover
                    }
                }
            }
        }
    }

    // Trạng thái hiển thị popup cảnh báo nhảy chương (Skip Chapter Warning Dialog)
    var showSkipChapterDialog by remember { mutableStateOf(false) }
    var pendingOnlineChapter by remember { mutableStateOf<com.eink.reader.data.model.ChapterItem?>(null) }
    var pendingOfflineCbz by remember { mutableStateOf<java.io.File?>(null) }

    // Xác định chương hiện tại đang đọc
    val currentOnlineChapter = remember(activeChapterId, chaptersList, activeChapterTitle) {
        if (chaptersList.isEmpty()) null
        else {
            chaptersList.firstOrNull { it.id == activeChapterId }
                ?: run {
                    val targetNum = NaturalOrderComparator.parseChapterNumber(activeChapterTitle)
                    chaptersList.firstOrNull { ch ->
                        ch.displayTitle.equals(activeChapterTitle, ignoreCase = true) ||
                        (targetNum != null && ch.attributes.chapter?.toFloatOrNull() == targetNum)
                    }
                }
        }
    }
    val currentLanguage = remember(currentOnlineChapter) {
        currentOnlineChapter?.attributes?.translatedLanguage
    }
    val sameLangChapters = remember(chaptersList, currentLanguage) {
        if (!currentLanguage.isNullOrBlank()) {
            val filtered = chaptersList.filter { it.attributes.translatedLanguage.equals(currentLanguage, ignoreCase = true) }
            if (filtered.isNotEmpty()) filtered else chaptersList
        } else chaptersList
    }

    val currentGroupId = remember(currentOnlineChapter) {
        currentOnlineChapter?.relationships?.firstOrNull { it.type == "scanlation_group" }?.id
    }
    val currentGroupName = remember(currentOnlineChapter) {
        currentOnlineChapter?.scanlationGroup
    }
    val currentChapterNum = remember(currentOnlineChapter, activeChapterTitle) {
        currentOnlineChapter?.attributes?.chapter?.toFloatOrNull()
            ?: NaturalOrderComparator.parseChapterNumber(activeChapterTitle)
    }

    fun isSameGroup(item: com.eink.reader.data.model.ChapterItem): Boolean {
        val gId = item.relationships.firstOrNull { it.type == "scanlation_group" }?.id
        if (!currentGroupId.isNullOrBlank() && !gId.isNullOrBlank() && gId == currentGroupId) {
            return true
        }
        val gName = item.scanlationGroup
        if (!currentGroupName.isNullOrBlank() && !gName.isNullOrBlank() && gName.equals(currentGroupName, ignoreCase = true)) {
            return true
        }
        return false
    }

    val currentSameLangIdx = remember(activeChapterId, sameLangChapters) {
        sameLangChapters.indexOfFirst { it.id == activeChapterId }
    }

    // Tự động tìm chương kế tiếp: Ưu tiên cùng nhóm dịch, fallback nhóm khác nếu hết
    val nextOnlineChapter = remember(currentOnlineChapter, sameLangChapters, currentChapterNum, currentGroupId, currentGroupName, currentSameLangIdx, activeChapterTitle) {
        if (sameLangChapters.isEmpty()) null
        else {
            val effChapterNum = currentChapterNum
                ?: NaturalOrderComparator.parseChapterNumber(activeChapterTitle)

            val upcoming = if (effChapterNum != null) {
                sameLangChapters.filter { cand ->
                    val n = cand.attributes.chapter?.toFloatOrNull()
                    n != null && n > effChapterNum
                }
            } else if (currentSameLangIdx in 0 until sameLangChapters.size - 1) {
                sameLangChapters.subList(currentSameLangIdx + 1, sameLangChapters.size)
            } else emptyList()

            if (upcoming.isEmpty()) null
            else {
                // 1. Ưu tiên tìm chương tiếp theo của CÙNG nhóm dịch
                val sameGroupCandidates = upcoming.filter { isSameGroup(it) }
                if (sameGroupCandidates.isNotEmpty()) {
                    sameGroupCandidates.first()
                } else {
                    // 2. Nếu nhóm hiện tại không có chương tiếp theo -> lấy chương kế tiếp của nhóm bất kỳ
                    upcoming.first()
                }
            }
        }
    }

    // Tự động tìm chương trước đó: Ưu tiên cùng nhóm dịch
    val prevOnlineChapter = remember(currentOnlineChapter, sameLangChapters, currentChapterNum, currentGroupId, currentGroupName, currentSameLangIdx, activeChapterTitle) {
        if (sameLangChapters.isEmpty()) null
        else {
            val effChapterNum = currentChapterNum
                ?: NaturalOrderComparator.parseChapterNumber(activeChapterTitle)

            val previous = if (effChapterNum != null) {
                sameLangChapters.filter { cand ->
                    val n = cand.attributes.chapter?.toFloatOrNull()
                    n != null && n < effChapterNum
                }
            } else if (currentSameLangIdx > 0) {
                sameLangChapters.subList(0, currentSameLangIdx)
            } else emptyList()

            if (previous.isEmpty()) null
            else {
                // 1. Ưu tiên cùng nhóm dịch (lấy chương gần nhất trước đó)
                val sameGroupCandidates = previous.filter { isSameGroup(it) }
                if (sameGroupCandidates.isNotEmpty()) {
                    sameGroupCandidates.last()
                } else {
                    // 2. Fallback sang nhóm khác
                    previous.last()
                }
            }
        }
    }

    val currentCbzIdx = remember(activeChapterId, siblingCbzFiles) {
        siblingCbzFiles.indexOfFirst { it.absolutePath == activeChapterId }
    }
    val nextOfflineCbz = remember(currentCbzIdx, siblingCbzFiles) {
        if (currentCbzIdx in 0 until siblingCbzFiles.size - 1) siblingCbzFiles[currentCbzIdx + 1] else null
    }
    val prevOfflineCbz = remember(currentCbzIdx, siblingCbzFiles) {
        if (currentCbzIdx > 0) siblingCbzFiles[currentCbzIdx - 1] else null
    }

    val hasNextChapter = if (isOfflineCbz) nextOfflineCbz != null else nextOnlineChapter != null
    val hasPrevChapter = if (isOfflineCbz) prevOfflineCbz != null else prevOnlineChapter != null
    val nextChapterTitleString = if (isOfflineCbz) nextOfflineCbz?.nameWithoutExtension ?: "" else nextOnlineChapter?.displayTitle ?: ""
    val prevChapterTitleString = if (isOfflineCbz) prevOfflineCbz?.nameWithoutExtension ?: "" else prevOnlineChapter?.displayTitle ?: ""

    var shouldStartAtLastPage by remember { mutableStateOf(false) }

    fun loadPages(dataSaver: Boolean, startPage: Int = 0) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            if (isOfflineCbz) {
                try {
                    val files = com.eink.reader.data.download.CbzReader.extractCbzToCache(context, java.io.File(activeChapterId))
                    if (files.isEmpty()) throw java.io.IOException("Không có ảnh trong file CBZ")
                    pageUrls = files.map { it.absolutePath }
                    val finalPage = if (shouldStartAtLastPage) {
                        shouldStartAtLastPage = false
                        (files.size - 1).coerceAtLeast(0)
                    } else if (startPage in files.indices) startPage else 0
                    currentPageIndex = finalPage
                    isLoading = false
                    if (finalPage > 0 && readingDirection == "VERTICAL") {
                        webtoonListState.scrollToItem(finalPage)
                    }
                } catch (e: Exception) {
                    errorMessage = "Lỗi đọc file CBZ: ${e.localizedMessage}"
                    isLoading = false
                }
            } else {
                val result = repository.getChapterPageUrls(activeChapterId, dataSaver)
                result.onSuccess { urls ->
                    pageUrls = urls
                    val finalPage = if (shouldStartAtLastPage) {
                        shouldStartAtLastPage = false
                        (urls.size - 1).coerceAtLeast(0)
                    } else if (startPage in urls.indices) startPage else 0
                    currentPageIndex = finalPage
                    isLoading = false
                    if (finalPage > 0 && readingDirection == "VERTICAL") {
                        webtoonListState.scrollToItem(finalPage)
                    }
                }.onFailure { err ->
                    errorMessage = err.localizedMessage ?: "Không thể tải trang truyện"
                    isLoading = false
                }
            }
        }
    }

    // Tự động load trang khi activeChapterId thay đổi
    LaunchedEffect(activeChapterId) {
        val targetPage = if (activeChapterId == chapterId && initialPage > 1) initialPage - 1 else 0
        loadPages(isDataSaver, targetPage)
    }

    // Phát hiện nhảy chương (Skip chapter detection)
    fun isChapterSkipped(currChapter: com.eink.reader.data.model.ChapterItem?, nextChapter: com.eink.reader.data.model.ChapterItem?): Boolean {
        if (nextChapter == null) return false
        val currNum = currChapter?.attributes?.chapter?.toFloatOrNull()
            ?: NaturalOrderComparator.parseChapterNumber(activeChapterTitle)
        val nextNum = nextChapter.attributes.chapter?.toFloatOrNull()
        if (currNum == null || nextNum == null) return false

        // Kiểm tra 1: Nhảy cách số nguyên (ví dụ: Ch.1 -> Ch.3, Ch.1 -> Ch.2.5, Ch.5 -> Ch.7)
        if (nextNum - currNum >= 1.5f || nextNum.toInt() > currNum.toInt() + 1) {
            return true
        }

        // Kiểm tra 2: Có chương nguyên nằm giữa trong danh sách cùng ngôn ngữ
        val hasIntermediateWhole = sameLangChapters.any { ch ->
            val n = ch.attributes.chapter?.toFloatOrNull()
            n != null && n > currNum && n < nextNum && (n.toInt() > currNum.toInt() && n.toInt() < nextNum.toInt())
        }
        return hasIntermediateWhole
    }

    fun isCbzSkipped(currTitle: String, nextName: String): Boolean {
        val currMatch = NaturalOrderComparator.parseChapterNumber(currTitle)
        val nextMatch = NaturalOrderComparator.parseChapterNumber(nextName)
        if (currMatch != null && nextMatch != null) {
            return (nextMatch - currMatch >= 1.5f || nextMatch.toInt() > currMatch.toInt() + 1)
        }
        return false
    }

    fun executeLoadOfflineCbz(file: java.io.File) {
        chapterNoticeMessage = "Đang tải: ${file.nameWithoutExtension}"
        activeChapterId = file.absolutePath
        activeChapterTitle = file.nameWithoutExtension
        currentPageIndex = 0
        coroutineScope.launch {
            scrollState.scrollTo(0)
            webtoonListState.scrollToItem(0)
            delay(2500)
            if (chapterNoticeMessage?.contains(file.nameWithoutExtension) == true) {
                chapterNoticeMessage = null
            }
        }
    }

    fun executeLoadOnlineChapter(ch: com.eink.reader.data.model.ChapterItem) {
        chapterNoticeMessage = "Đang chuyển sang: ${ch.displayTitle}"
        activeChapterId = ch.id
        activeChapterTitle = ch.displayTitle
        currentPageIndex = 0
        coroutineScope.launch {
            scrollState.scrollTo(0)
            webtoonListState.scrollToItem(0)
            delay(2500)
            if (chapterNoticeMessage?.contains(ch.displayTitle) == true) {
                chapterNoticeMessage = null
            }
        }
    }

    fun goToNextChapter(force: Boolean = false) {
        if (isOfflineCbz) {
            val nextFile = nextOfflineCbz
            if (nextFile == null) {
                chapterNoticeMessage = "Bạn đã đọc đến chương cuối cùng!"
                coroutineScope.launch {
                    delay(2500)
                    chapterNoticeMessage = null
                }
                return
            }

            if (!force && isCbzSkipped(activeChapterTitle, nextFile.nameWithoutExtension)) {
                pendingOfflineCbz = nextFile
                pendingOnlineChapter = null
                showSkipChapterDialog = true
                return
            }

            executeLoadOfflineCbz(nextFile)
        } else {
            val nextCh = nextOnlineChapter
            if (nextCh == null) {
                chapterNoticeMessage = "Bạn đã đọc đến chương mới nhất của truyện!"
                coroutineScope.launch {
                    delay(2500)
                    chapterNoticeMessage = null
                }
                return
            }

            if (!force && isChapterSkipped(currentOnlineChapter, nextCh)) {
                pendingOnlineChapter = nextCh
                pendingOfflineCbz = null
                showSkipChapterDialog = true
                return
            }

            executeLoadOnlineChapter(nextCh)
        }
    }

    fun goToPrevChapter(fromFirstPage: Boolean = false) {
        if (fromFirstPage) {
            shouldStartAtLastPage = true
        }
        if (isOfflineCbz) {
            val prevFile = prevOfflineCbz
            if (prevFile != null) {
                executeLoadOfflineCbz(prevFile)
            } else {
                shouldStartAtLastPage = false
                chapterNoticeMessage = "Bạn đã ở chương đầu tiên của truyện!"
                coroutineScope.launch {
                    delay(2500)
                    if (chapterNoticeMessage?.contains("đầu tiên") == true) {
                        chapterNoticeMessage = null
                    }
                }
            }
        } else {
            val prevCh = prevOnlineChapter
            if (prevCh != null) {
                executeLoadOnlineChapter(prevCh)
            } else {
                shouldStartAtLastPage = false
                chapterNoticeMessage = "Bạn đã ở chương đầu tiên của truyện!"
                coroutineScope.launch {
                    delay(2500)
                    if (chapterNoticeMessage?.contains("đầu tiên") == true) {
                        chapterNoticeMessage = null
                    }
                }
            }
        }
    }

    // Tự động lưu tiến độ đọc vào ReadingHistoryManager trên máy
    LaunchedEffect(currentPageIndex, pageUrls.size, activeChapterId, activeMangaTitle, activeCoverUrl) {
        if (!mangaId.isNullOrBlank() && pageUrls.isNotEmpty()) {
            val finalMangaTitle = activeMangaTitle?.takeIf { it.isNotBlank() && it != "Untitled" && !it.startsWith("Ch.", ignoreCase = true) }
                ?: mangaTitle?.takeIf { it.isNotBlank() && it != "Untitled" && !it.startsWith("Ch.", ignoreCase = true) }
                ?: activeChapterTitle

            repository.readingHistoryManager.saveProgress(
                mangaId = mangaId,
                mangaTitle = finalMangaTitle,
                coverUrl = activeCoverUrl ?: coverUrl,
                chapterId = activeChapterId,
                chapterTitle = activeChapterTitle,
                page = currentPageIndex + 1,
                totalPages = pageUrls.size
            )
        }
    }

    // Tự động đồng bộ đánh dấu đã đọc lên MangaDex khi đến trang cuối
    LaunchedEffect(currentPageIndex, pageUrls.size, activeChapterId) {
        if (!isOfflineCbz && pageUrls.isNotEmpty() && currentPageIndex >= pageUrls.size - 1) {
            repository.markChapterRead(activeChapterId)
        }
    }

    // Pre-cache các trang tiếp theo theo cài đặt preCachePagesCount
    LaunchedEffect(currentPageIndex, pageUrls) {
        if (pageUrls.isNotEmpty()) {
            val preCacheLimit = repository.settingsManager.preCachePagesCount
            val step = if (isDualPageMode) preCacheLimit * 2 else preCacheLimit
            for (offset in 1..step) {
                val idx = currentPageIndex + offset
                if (idx < pageUrls.size) {
                    val target = if (isOfflineCbz) java.io.File(pageUrls[idx]) else pageUrls[idx]
                    val request = ImageRequest.Builder(context)
                        .data(target)
                        .build()
                    context.imageLoader.enqueue(request)
                }
            }
        }
    }

    val isEInkSupportEnabled = repository.settingsManager.eInkSupportEnabled

    // Tự động khử bóng ma định kỳ theo cài đặt eInkAutoRefreshInterval
    LaunchedEffect(currentPageIndex, isEInkSupportEnabled) {
        val autoInterval = repository.settingsManager.eInkAutoRefreshInterval
        if (isEInkSupportEnabled && autoInterval > 0 && currentPageIndex > 0 && (currentPageIndex + 1) % autoInterval == 0) {
            com.eink.reader.util.EInkHelper.triggerFullEInkRefresh(
                scope = coroutineScope,
                view = view,
                context = context,
                onFlashStateChange = { isScreenFlashRefreshing = it }
            )
        }
    }

    val pageStep = if (isDualPageMode) 2 else 1

    fun goToNextPage() {
        if (currentPageIndex < pageUrls.size - 1) {
            currentPageIndex = (currentPageIndex + pageStep).coerceAtMost(pageUrls.size - 1)
        }
    }

    fun goToPrevPage() {
        if (currentPageIndex > 0) {
            currentPageIndex = (currentPageIndex - pageStep).coerceAtLeast(0)
        } else {
            // Đang ở trang đầu tiên: quay về chương trước hoặc thông báo đã là chương đầu
            goToPrevChapter(fromFirstPage = true)
        }
    }

    val imageColorFilter = remember(colorMode, isEInkSupportEnabled) {
        if (!isEInkSupportEnabled) {
            null
        } else {
            when (colorMode) {
                EInkColorMode.ORIGINAL -> null
                EInkColorMode.KALEIDO_3 -> {
                    // Tối ưu đặc biệt cho màn hình E-Ink màu Bigme B751C / B751C S (Kaleido 3):
                    // 1. Nâng độ bão hòa lên 1.75f để bù đắp 4096 màu pastel của tấm nền Kaleido 3
                    val matrix = ColorMatrix().apply { setToSaturation(1.75f) }
                    // 2. Tăng độ tương phản (1.20f) kết hợp nâng sáng (+20f) để bù đắp lượng ánh sáng
                    // hao hụt do lớp kính lọc màu CFA hấp thụ, giữ nền trang trắng sáng và nét vẽ đen đậm
                    val contrast = 1.20f
                    val brightnessLift = 20f
                    val translate = (-0.5f * contrast + 0.5f) * 255f + brightnessLift
                    val contrastMatrix = ColorMatrix(
                        floatArrayOf(
                            contrast, 0f, 0f, 0f, translate,
                            0f, contrast, 0f, 0f, translate,
                            0f, 0f, contrast, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                    matrix.timesAssign(contrastMatrix)
                    ColorFilter.colorMatrix(matrix)
                }
                EInkColorMode.COLOR_BOOST -> {
                    val matrix = ColorMatrix().apply { setToSaturation(1.4f) }
                    val contrast = 1.2f
                    val translate = (-0.5f * contrast + 0.5f) * 255f + 8f
                    val contrastMatrix = ColorMatrix(
                        floatArrayOf(
                            contrast, 0f, 0f, 0f, translate,
                            0f, contrast, 0f, 0f, translate,
                            0f, 0f, contrast, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                    matrix.timesAssign(contrastMatrix)
                    ColorFilter.colorMatrix(matrix)
                }
                EInkColorMode.MONOCHROME -> {
                    val matrix = ColorMatrix().apply { setToSaturation(0f) }
                    val contrast = 1.35f
                    val translate = (-0.5f * contrast + 0.5f) * 255f + 10f
                    val contrastMatrix = ColorMatrix(
                        floatArrayOf(
                            contrast, 0f, 0f, 0f, translate,
                            0f, contrast, 0f, 0f, translate,
                            0f, 0f, contrast, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                    matrix.timesAssign(contrastMatrix)
                    ColorFilter.colorMatrix(matrix)
                }
            }
        }
    }

    fun triggerEInkRefresh() {
        com.eink.reader.util.EInkHelper.triggerFullEInkRefresh(
            scope = coroutineScope,
            view = view,
            context = context,
            onFlashStateChange = { isScreenFlashRefreshing = it }
        )
    }

    val isPornographic = remember(mangaId) {
        if (!mangaId.isNullOrBlank()) {
            repository.tagCacheManager.isPornographic(mangaId)
        } else false
    }
    val isDarkEffective = isPornographic ||
        repository.settingsManager.contentRatingPornographic ||
        com.eink.reader.ui.theme.LocalEInkColors.current.isDark

    com.eink.reader.ui.theme.EInkReaderTheme(darkTheme = isDarkEffective) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(EInkWhite)
        ) {
        val isLandscape = maxWidth > maxHeight
        val density = androidx.compose.ui.platform.LocalDensity.current
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val jumpStepPx = (viewportHeightPx * 0.82f).toInt()
        val isPagedScroll = repository.settingsManager.eInkReaderPagedScroll
        val autoNextChapter = repository.settingsManager.autoNextChapter

        fun handleNextAction() {
            if (isPagedScroll && scaleMode == PageScaleMode.FIT_WIDTH && scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue) {
                coroutineScope.launch {
                    val target = (scrollState.value + jumpStepPx).coerceAtMost(scrollState.maxValue)
                    scrollState.scrollTo(target)
                }
            } else {
                if (currentPageIndex < pageUrls.size - 1) {
                    goToNextPage()
                    coroutineScope.launch {
                        scrollState.scrollTo(0)
                    }
                } else {
                    // Đang ở trang cuối cùng của chương
                    if (autoNextChapter && hasNextChapter) {
                        goToNextChapter()
                    } else if (hasNextChapter) {
                        chapterNoticeMessage = "Hết chương. Nhấn [ĐỌC TIẾP] để sang $nextChapterTitleString"
                        coroutineScope.launch {
                            delay(3000)
                            if (chapterNoticeMessage?.startsWith("Hết chương") == true) {
                                chapterNoticeMessage = null
                            }
                        }
                    } else {
                        chapterNoticeMessage = "Bạn đã đọc đến chương mới nhất!"
                        coroutineScope.launch {
                            delay(2500)
                            if (chapterNoticeMessage?.contains("mới nhất") == true) {
                                chapterNoticeMessage = null
                            }
                        }
                    }
                }
            }
        }

        fun handlePrevAction() {
            if (isPagedScroll && scaleMode == PageScaleMode.FIT_WIDTH && scrollState.maxValue > 0 && scrollState.value > 0) {
                coroutineScope.launch {
                    val target = (scrollState.value - jumpStepPx).coerceAtLeast(0)
                    scrollState.scrollTo(target)
                }
            } else {
                if (currentPageIndex > 0) {
                    goToPrevPage()
                    coroutineScope.launch {
                        scrollState.scrollTo(scrollState.maxValue)
                    }
                } else {
                    // Đang ở trang đầu tiên
                    goToPrevPage()
                }
            }
        }

        // Bắt sự kiện phím cứng âm lượng hoặc nút điều hướng
        LaunchedEffect(volumeKeyEventFlow, pageUrls.size, isDualPageMode, scaleMode, isPagedScroll, readingDirection) {
            volumeKeyEventFlow?.collect { isNext ->
                if (pageUrls.isNotEmpty()) {
                    if (readingDirection == "VERTICAL") {
                        if (isNext) {
                            if (!webtoonListState.canScrollForward) {
                                if (autoNextChapter && hasNextChapter) {
                                    goToNextChapter()
                                } else if (hasNextChapter) {
                                    chapterNoticeMessage = "Hết chương. Nhấn [ĐỌC TIẾP] để sang $nextChapterTitleString"
                                }
                            } else {
                                coroutineScope.launch {
                                    webtoonListState.animateScrollBy(jumpStepPx.toFloat())
                                }
                            }
                        } else {
                            if (!webtoonListState.canScrollBackward) {
                                goToPrevChapter(fromFirstPage = true)
                            } else {
                                coroutineScope.launch {
                                    webtoonListState.animateScrollBy(-jumpStepPx.toFloat())
                                }
                            }
                        }
                    } else {
                        if (isNext) handleNextAction() else handlePrevAction()
                    }
                }
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[ ĐANG TẢI TRANG CHƯƠNG... ]",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .border(1.dp, EInkBorder, RoundedCornerShape(4.dp))
                            .padding(16.dp)
                    )
                }
            }
            errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Lỗi: $errorMessage", color = Color.Red)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { loadPages(isDataSaver) },
                        border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Thử lại", color = EInkBlack)
                    }
                }
            }
            pageUrls.isNotEmpty() -> {
                if (readingDirection == "VERTICAL") {
                    // CHẾ ĐỘ CUỘN DỌC LIÊN TỤC (WEBTOON / MANHWA / MANHUA)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(jumpStepPx) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val screenHeight = size.height
                                        val touchY = offset.y
                                        when {
                                            touchY < screenHeight * 0.30f -> {
                                                coroutineScope.launch {
                                                    if (!webtoonListState.canScrollBackward) {
                                                        goToPrevChapter(fromFirstPage = true)
                                                    } else {
                                                        webtoonListState.animateScrollBy(-jumpStepPx.toFloat())
                                                    }
                                                }
                                            }
                                            touchY > screenHeight * 0.70f -> {
                                                coroutineScope.launch {
                                                    if (!webtoonListState.canScrollForward) {
                                                        if (autoNextChapter && hasNextChapter) goToNextChapter()
                                                    } else {
                                                        webtoonListState.animateScrollBy(jumpStepPx.toFloat())
                                                    }
                                                }
                                            }
                                            else -> {
                                                showControls = !showControls
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        LazyColumn(
                            state = webtoonListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            itemsIndexed(pageUrls, key = { index, url -> "$url-$index" }) { index, url ->
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(if (isOfflineCbz) java.io.File(url) else url)
                                        .crossfade(false)
                                        .build(),
                                    contentDescription = "Trang ${index + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    colorFilter = imageColorFilter,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Thẻ chuyển chương ở cuối danh sách
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp, horizontal = 16.dp)
                                        .clickable {
                                            if (hasNextChapter) goToNextChapter() else onBackClick()
                                        },
                                    color = EInkBlack,
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, EInkWhite)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (hasNextChapter) {
                                            Text(
                                                text = "⏭ ĐỌC TIẾP: $nextChapterTitleString ❯",
                                                color = EInkWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        } else {
                                            Text(
                                                text = "✓ ĐÃ HẾT BỘ / CHƯƠNG MỚI NHẤT (QUAY LẠI)",
                                                color = EInkWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(70.dp))
                            }
                        }

                        // Chỉ số trang tĩnh góc dưới màn hình
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(8.dp)
                                .background(EInkWhite)
                                .border(1.dp, EInkBorder, RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${currentPageIndex + 1} / ${pageUrls.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EInkBlack
                                )
                            )
                        }
                    }
                } else {
                    // CHẾ ĐỘ LẬT TRANG MANGA (RTL) / COMIC (LTR)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isRtl, scaleMode, isPagedScroll, jumpStepPx) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val screenWidth = size.width
                                        val touchX = offset.x
                                        when {
                                            touchX < screenWidth * 0.33f -> {
                                                if (isRtl) handleNextAction() else handlePrevAction()
                                            }
                                            touchX > screenWidth * 0.67f -> {
                                                if (isRtl) handlePrevAction() else handleNextAction()
                                            }
                                            else -> {
                                                showControls = !showControls
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        val contentScale = if (scaleMode == PageScaleMode.FIT_WIDTH) ContentScale.FillWidth else ContentScale.Fit

                        if (isDualPageMode) {
                            // CHẾ ĐỘ TRANG ĐÔI (DUAL PAGE): 2 trang cạnh nhau khi xoay ngang
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Trang 1
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    AsyncImage(
                                        model = pageUrls[currentPageIndex],
                                        contentDescription = "Trang ${currentPageIndex + 1}",
                                        contentScale = ContentScale.Fit,
                                        colorFilter = imageColorFilter,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                // Trang 2 (nếu có)
                                if (currentPageIndex + 1 < pageUrls.size) {
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        AsyncImage(
                                            model = pageUrls[currentPageIndex + 1],
                                            contentDescription = "Trang ${currentPageIndex + 2}",
                                            contentScale = ContentScale.Fit,
                                            colorFilter = imageColorFilter,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        } else {
                            // CHẾ ĐỘ 1 TRANG (ĐƠN TRANG)
                            val pageModifier = if (scaleMode == PageScaleMode.FIT_WIDTH) {
                                Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                            } else {
                                Modifier.fillMaxSize()
                            }

                            AsyncImage(
                                model = pageUrls[currentPageIndex],
                                contentDescription = "Trang ${currentPageIndex + 1}",
                                contentScale = contentScale,
                                colorFilter = imageColorFilter,
                                modifier = pageModifier
                            )
                        }

                        // Chỉ số trang tĩnh góc dưới màn hình
                        val pageText = if (isDualPageMode && currentPageIndex + 1 < pageUrls.size) {
                            "${currentPageIndex + 1}-${currentPageIndex + 2} / ${pageUrls.size}"
                        } else {
                            "${currentPageIndex + 1} / ${pageUrls.size}"
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(8.dp)
                                .background(EInkWhite)
                                .border(1.dp, EInkBorder, RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = pageText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EInkBlack
                                )
                            )
                        }

                        // Nút chuyển chương nổi bật ở trang cuối cùng
                        if (currentPageIndex >= pageUrls.size - 1 && pageUrls.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 44.dp)
                                    .clickable {
                                        if (hasNextChapter) goToNextChapter() else onBackClick()
                                    },
                                color = EInkBlack,
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EInkWhite)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (hasNextChapter) {
                                        Text(
                                            text = "⏭ ĐỌC TIẾP: $nextChapterTitleString ❯",
                                            color = EInkWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        Text(
                                            text = "✓ ĐÃ HẾT BỘ / CHƯƠNG MỚI NHẤT (QUAY LẠI)",
                                            color = EInkWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Thông báo chuyển chương dạng E-Ink Toast
        chapterNoticeMessage?.let { msg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 44.dp)
                    .border(1.dp, EInkBlack, RoundedCornerShape(4.dp)),
                color = EInkWhite,
                shadowElevation = 3.dp
            ) {
                Text(
                    text = msg,
                    color = EInkBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        if (isScreenFlashRefreshing && isEInkSupportEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        // Header controls
        if (showControls) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(EInkWhite)
                    .border(1.dp, EInkBorder)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Thoát trình đọc",
                        tint = EInkBlack
                    )
                }
                Text(
                    text = activeChapterTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )

                if (isEInkSupportEnabled) {
                    OutlinedButton(
                        onClick = { triggerEInkRefresh() },
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Khử bóng ma", color = EInkBlack, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Bottom controls panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(EInkWhite)
                    .border(1.dp, EInkBorder)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .navigationBarsPadding()
                    .padding(12.dp)
            ) {
                // Điều hướng nhanh giữa các chương
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { goToPrevChapter() },
                        enabled = hasPrevChapter,
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (hasPrevChapter) EInkBlack else Color.LightGray),
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("⏮ Trước", fontSize = 11.sp, color = if (hasPrevChapter) EInkBlack else Color.LightGray)
                    }

                    Text(
                        text = activeChapterTitle,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                    )

                    OutlinedButton(
                        onClick = { goToNextChapter() },
                        enabled = hasNextChapter,
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (hasNextChapter) EInkBlack else Color.LightGray),
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Kế tiếp ⏭", fontSize = 11.sp, color = if (hasNextChapter) EInkBlack else Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (pageUrls.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentPageIndex + 1}",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Slider(
                            value = currentPageIndex.toFloat(),
                            onValueChange = {
                                val target = it.toInt()
                                currentPageIndex = target
                                if (readingDirection == "VERTICAL") {
                                    coroutineScope.launch {
                                        webtoonListState.scrollToItem(target)
                                    }
                                }
                            },
                            valueRange = 0f..(pageUrls.size - 1).toFloat(),
                            steps = if (pageUrls.size > 2) pageUrls.size - 2 else 0,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = EInkBlack,
                                activeTrackColor = EInkBlack,
                                inactiveTrackColor = Color.LightGray
                            )
                        )
                        Text(
                            text = "${pageUrls.size}",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Dòng chọn chế độ đọc trực tiếp: Manga (RTL) | Comic (LTR) | Webtoon (Dọc)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isRtlMode = readingDirection == "RTL"
                    val isLtrMode = readingDirection == "LTR"
                    val isVerticalMode = readingDirection == "VERTICAL"

                    OutlinedButton(
                        onClick = {
                            readingDirection = "RTL"
                            if (!mangaId.isNullOrBlank()) {
                                repository.readingHistoryManager.saveReadingMode(mangaId, "RTL")
                            }
                        },
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(if (isRtlMode) 2.dp else 1.dp, EInkBlack),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isRtlMode) EInkBlack else EInkWhite,
                            contentColor = if (isRtlMode) EInkWhite else EInkBlack
                        ),
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "📖 Manga (RTL)",
                            color = if (isRtlMode) EInkWhite else EInkBlack,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            readingDirection = "LTR"
                            if (!mangaId.isNullOrBlank()) {
                                repository.readingHistoryManager.saveReadingMode(mangaId, "LTR")
                            }
                        },
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(if (isLtrMode) 2.dp else 1.dp, EInkBlack),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isLtrMode) EInkBlack else EInkWhite,
                            contentColor = if (isLtrMode) EInkWhite else EInkBlack
                        ),
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "📘 Comic (LTR)",
                            color = if (isLtrMode) EInkWhite else EInkBlack,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            readingDirection = "VERTICAL"
                            if (!mangaId.isNullOrBlank()) {
                                repository.readingHistoryManager.saveReadingMode(mangaId, "VERTICAL")
                            }
                        },
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(if (isVerticalMode) 2.dp else 1.dp, EInkBlack),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isVerticalMode) EInkBlack else EInkWhite,
                            contentColor = if (isVerticalMode) EInkWhite else EInkBlack
                        ),
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "📜 Webtoon (Dọc)",
                            color = if (isVerticalMode) EInkWhite else EInkBlack,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }

                if (readingDirection != "VERTICAL") {
                    Spacer(modifier = Modifier.height(6.dp))

                    // Dòng 1: Tối ưu tỷ lệ màn hình (Fit Screen vs Fit Width) & (1 Trang vs Trang Đôi)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Nút Fit Mode
                        OutlinedButton(
                            onClick = {
                                scaleMode = if (scaleMode == PageScaleMode.FIT_SCREEN) PageScaleMode.FIT_WIDTH else PageScaleMode.FIT_SCREEN
                            },
                            shape = RoundedCornerShape(2.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (scaleMode == PageScaleMode.FIT_WIDTH) EInkBlack else EInkWhite,
                                contentColor = if (scaleMode == PageScaleMode.FIT_WIDTH) EInkWhite else EInkBlack
                            ),
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = if (scaleMode == PageScaleMode.FIT_WIDTH) "📐 Khớp ngang" else "📐 Vừa màn hình",
                                color = if (scaleMode == PageScaleMode.FIT_WIDTH) EInkWhite else EInkBlack,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Nút Chế độ Trang Đôi (Dual Page)
                        OutlinedButton(
                            onClick = {
                                isDualPageMode = !isDualPageMode
                            },
                            shape = RoundedCornerShape(2.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isDualPageMode) EInkBlack else EInkWhite,
                                contentColor = if (isDualPageMode) EInkWhite else EInkBlack
                            ),
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = if (isDualPageMode) "📖 Trang Đôi" else "📄 1 Trang",
                                color = if (isDualPageMode) EInkWhite else EInkBlack,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Dòng 2: Tùy chọn chế độ màu E-Ink (Bigme Kaleido 3, Tăng nét, Đen trắng, Gốc)
                if (isEInkSupportEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EInkColorMode.entries.forEach { mode ->
                            val isSelected = colorMode == mode
                            OutlinedButton(
                                onClick = {
                                    colorMode = mode
                                    repository.settingsManager.defaultReaderColorMode = mode.name
                                },
                                shape = RoundedCornerShape(2.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    EInkBlack
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) EInkBlack else EInkWhite,
                                    contentColor = if (isSelected) EInkWhite else EInkBlack
                                ),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = mode.shortTitle,
                                    color = if (isSelected) EInkWhite else EInkBlack,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Chế độ nén tiết kiệm mạng (Data-Saver):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isDataSaver,
                        onCheckedChange = {
                            isDataSaver = it
                            loadPages(it, currentPageIndex)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EInkWhite,
                            checkedTrackColor = EInkBlack,
                            uncheckedThumbColor = EInkBlack,
                            uncheckedTrackColor = EInkWhite
                        )
                    )
                }
            }
        }
    }

    // Popup cảnh báo nhảy chương (Skip Chapter Warning Dialog)
    if (showSkipChapterDialog) {
        val pendingTitle = pendingOnlineChapter?.displayTitle
            ?: pendingOfflineCbz?.nameWithoutExtension
            ?: ""
        val group = pendingOnlineChapter?.scanlationGroup

        AlertDialog(
            onDismissRequest = {
                showSkipChapterDialog = false
                pendingOnlineChapter = null
                pendingOfflineCbz = null
            },
            title = {
                Text(
                    text = "Phát hiện nhảy chương",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = EInkBlack
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Bạn đang đọc: $activeChapterTitle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EInkBlack
                    )
                    Text(
                        text = "Chương tiếp theo: $pendingTitle",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = EInkBlack
                    )
                    if (!group.isNullOrBlank()) {
                        Text(
                            text = "Nhóm dịch: $group",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }
                    Text(
                        text = "Chương kế tiếp không liền kề với chương hiện tại (có thể bị nhảy chương hoặc thiếu chương ở giữa). Bạn có muốn tiếp tục đọc không?",
                        style = MaterialTheme.typography.bodySmall,
                        color = EInkBlack
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetCh = pendingOnlineChapter
                        val targetCbz = pendingOfflineCbz
                        showSkipChapterDialog = false
                        pendingOnlineChapter = null
                        pendingOfflineCbz = null
                        if (targetCh != null) {
                            executeLoadOnlineChapter(targetCh)
                        } else if (targetCbz != null) {
                            executeLoadOfflineCbz(targetCbz)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EInkBlack,
                        contentColor = EInkWhite
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Tiếp tục đọc", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showSkipChapterDialog = false
                        pendingOnlineChapter = null
                        pendingOfflineCbz = null
                        onBackClick()
                    },
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = EInkBlack
                    )
                ) {
                    Text("Quay lại chi tiết truyện", fontWeight = FontWeight.Medium)
                }
            },
            containerColor = EInkWhite,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.border(1.dp, EInkBlack, RoundedCornerShape(8.dp))
        )
    }
    }
}