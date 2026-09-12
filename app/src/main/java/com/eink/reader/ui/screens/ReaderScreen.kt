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
import com.eink.reader.data.model.EInkColorMode
import com.eink.reader.data.repository.MangaRepository
import com.eink.reader.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val isRtl = remember { repository.settingsManager.readingDirection == "RTL" }

    // Quản lý danh sách toàn bộ chương để tự động chuyển chương (Auto Next Chapter)
    var chaptersList by remember { mutableStateOf<List<com.eink.reader.data.model.ChapterItem>>(emptyList()) }
    var siblingCbzFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var chapterNoticeMessage by remember { mutableStateOf<String?>(null) }

    // Tải danh sách chương khi đọc online hoặc offline
    LaunchedEffect(mangaId, activeChapterId) {
        if (!isOfflineCbz && !mangaId.isNullOrBlank()) {
            if (chaptersList.isEmpty()) {
                val res = repository.getChapters(mangaId, listOf("vi", "en"))
                res.onSuccess { list ->
                    // Sắp xếp thứ tự chương tăng dần: Ch.1 -> Ch.2 -> Ch.3...
                    chaptersList = list.sortedWith(
                        compareBy { it.attributes.chapter?.toFloatOrNull() ?: Float.MAX_VALUE }
                    )
                }
            }
        } else if (isOfflineCbz) {
            val file = java.io.File(activeChapterId)
            val parent = file.parentFile
            if (parent != null && parent.isDirectory) {
                val cbzs = parent.listFiles { f -> f.extension.equals("cbz", ignoreCase = true) }
                    ?.sortedWith(compareBy { it.name.lowercase() }) ?: emptyList()
                siblingCbzFiles = cbzs
            }
        }
    }

    // Xác định vị trí chương hiện tại và chương kế tiếp / chương trước đó
    val currentOnlineIdx = remember(activeChapterId, chaptersList) {
        if (chaptersList.isEmpty()) -1
        else {
            val idx = chaptersList.indexOfFirst { it.id == activeChapterId }
            if (idx != -1) idx
            else {
                chaptersList.indexOfFirst { ch ->
                    ch.displayTitle == activeChapterTitle ||
                    (ch.attributes.chapter != null && activeChapterTitle.contains("Ch.${ch.attributes.chapter}"))
                }
            }
        }
    }
    val nextOnlineChapter = remember(currentOnlineIdx, chaptersList) {
        if (currentOnlineIdx in 0 until chaptersList.size - 1) chaptersList[currentOnlineIdx + 1] else null
    }
    val prevOnlineChapter = remember(currentOnlineIdx, chaptersList) {
        if (currentOnlineIdx > 0) chaptersList[currentOnlineIdx - 1] else null
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

    fun loadPages(dataSaver: Boolean, startPage: Int = 0) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            if (isOfflineCbz) {
                try {
                    val files = com.eink.reader.data.download.CbzReader.extractCbzToCache(context, java.io.File(activeChapterId))
                    if (files.isEmpty()) throw java.io.IOException("Không có ảnh trong file CBZ")
                    pageUrls = files.map { it.absolutePath }
                    currentPageIndex = if (startPage in files.indices) startPage else 0
                    isLoading = false
                } catch (e: Exception) {
                    errorMessage = "Lỗi đọc file CBZ: ${e.localizedMessage}"
                    isLoading = false
                }
            } else {
                val result = repository.getChapterPageUrls(activeChapterId, dataSaver)
                result.onSuccess { urls ->
                    pageUrls = urls
                    currentPageIndex = if (startPage in urls.indices) startPage else 0
                    isLoading = false
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

    fun goToNextChapter() {
        if (isOfflineCbz) {
            nextOfflineCbz?.let { nextFile ->
                chapterNoticeMessage = "Đang tải: ${nextFile.nameWithoutExtension}"
                activeChapterId = nextFile.absolutePath
                activeChapterTitle = nextFile.nameWithoutExtension
                currentPageIndex = 0
                coroutineScope.launch {
                    scrollState.scrollTo(0)
                    delay(2500)
                    if (chapterNoticeMessage?.contains(nextFile.nameWithoutExtension) == true) {
                        chapterNoticeMessage = null
                    }
                }
            } ?: run {
                chapterNoticeMessage = "Bạn đã đọc đến chương cuối cùng!"
                coroutineScope.launch {
                    delay(2500)
                    chapterNoticeMessage = null
                }
            }
        } else {
            nextOnlineChapter?.let { nextCh ->
                chapterNoticeMessage = "Đang chuyển sang: ${nextCh.displayTitle}"
                activeChapterId = nextCh.id
                activeChapterTitle = nextCh.displayTitle
                currentPageIndex = 0
                coroutineScope.launch {
                    scrollState.scrollTo(0)
                    delay(2500)
                    if (chapterNoticeMessage?.contains(nextCh.displayTitle) == true) {
                        chapterNoticeMessage = null
                    }
                }
            } ?: run {
                chapterNoticeMessage = "Bạn đã đọc đến chương mới nhất của truyện!"
                coroutineScope.launch {
                    delay(2500)
                    chapterNoticeMessage = null
                }
            }
        }
    }

    fun goToPrevChapter() {
        if (isOfflineCbz) {
            prevOfflineCbz?.let { prevFile ->
                chapterNoticeMessage = "Đang tải: ${prevFile.nameWithoutExtension}"
                activeChapterId = prevFile.absolutePath
                activeChapterTitle = prevFile.nameWithoutExtension
                currentPageIndex = 0
                coroutineScope.launch {
                    scrollState.scrollTo(0)
                    delay(2500)
                    if (chapterNoticeMessage?.contains(prevFile.nameWithoutExtension) == true) {
                        chapterNoticeMessage = null
                    }
                }
            }
        } else {
            prevOnlineChapter?.let { prevCh ->
                chapterNoticeMessage = "Đang tải: ${prevCh.displayTitle}"
                activeChapterId = prevCh.id
                activeChapterTitle = prevCh.displayTitle
                currentPageIndex = 0
                coroutineScope.launch {
                    scrollState.scrollTo(0)
                    delay(2500)
                    if (chapterNoticeMessage?.contains(prevCh.displayTitle) == true) {
                        chapterNoticeMessage = null
                    }
                }
            }
        }
    }

    // Tự động lưu tiến độ đọc vào ReadingHistoryManager trên máy
    LaunchedEffect(currentPageIndex, pageUrls.size, activeChapterId) {
        if (!mangaId.isNullOrBlank() && pageUrls.isNotEmpty()) {
            repository.readingHistoryManager.saveProgress(
                mangaId = mangaId,
                mangaTitle = mangaTitle ?: activeChapterTitle,
                coverUrl = coverUrl,
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

    // Tự động khử bóng ma định kỳ theo cài đặt eInkAutoRefreshInterval
    LaunchedEffect(currentPageIndex) {
        val autoInterval = repository.settingsManager.eInkAutoRefreshInterval
        if (autoInterval > 0 && currentPageIndex > 0 && (currentPageIndex + 1) % autoInterval == 0) {
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
        }
    }

    val imageColorFilter = remember(colorMode) {
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

    fun triggerEInkRefresh() {
        com.eink.reader.util.EInkHelper.triggerFullEInkRefresh(
            scope = coroutineScope,
            view = view,
            context = context,
            onFlashStateChange = { isScreenFlashRefreshing = it }
        )
    }

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
                }
            }
        }

        // Bắt sự kiện phím cứng âm lượng hoặc nút điều hướng
        LaunchedEffect(volumeKeyEventFlow, pageUrls.size, isDualPageMode, scaleMode, isPagedScroll) {
            volumeKeyEventFlow?.collect { isNext ->
                if (pageUrls.isNotEmpty()) {
                    if (isNext) handleNextAction() else handlePrevAction()
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

        if (isScreenFlashRefreshing) {
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
                            onValueChange = { currentPageIndex = it.toInt() },
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

                Spacer(modifier = Modifier.height(6.dp))

                // Dòng 2: Tùy chọn chế độ màu E-Ink (Bigme Kaleido 3, Tăng nét, Đen trắng, Gốc)
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
}