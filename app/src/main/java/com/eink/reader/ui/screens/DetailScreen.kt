package com.eink.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eink.reader.data.model.ChapterItem
import com.eink.reader.data.model.MangaItem
import com.eink.reader.data.repository.MangaRepository
import com.eink.reader.ui.theme.*
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.platform.LocalContext
import com.eink.reader.data.download.DownloadManager
import com.eink.reader.data.download.DownloadStatus
import com.eink.reader.data.model.ReadingStatusEnum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    mangaId: String,
    repository: MangaRepository,
    onBackClick: () -> Unit,
    onChapterClick: (String, String, Int, String?, String?) -> Unit
) {
    val context = LocalContext.current
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val downloadStatusMap by downloadManager.downloadStatusFlow.collectAsState()

    var manga by remember { mutableStateOf<MangaItem?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedLanguage by remember { mutableStateOf("vi") }
    var isAscending by remember { mutableStateOf(true) }

    // Reading Record & Progress State
    var readingRecord by remember { mutableStateOf<com.eink.reader.data.repository.ReadingRecord?>(null) }
    var readChapterIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun refreshReadingRecord() {
        readingRecord = repository.readingHistoryManager.getRecord(mangaId)
        readChapterIds = repository.readingHistoryManager.getReadChapterIds(mangaId)
    }

    LaunchedEffect(mangaId) {
        refreshReadingRecord()
    }

    // MDList & Follows State
    var isFollowed by remember { mutableStateOf(false) }
    var readingStatus by remember { mutableStateOf<String?>(null) }
    var isFollowUpdating by remember { mutableStateOf(false) }
    var isStatusUpdating by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    fun loadChapters(lang: String) {
        coroutineScope.launch {
            isLoading = true
            val langList = if (lang == "all") emptyList() else listOf(lang)
            val result = repository.getChapters(mangaId, langList)
            result.onSuccess { list ->
                chapters = list
                isLoading = false
            }.onFailure { err ->
                errorMessage = err.localizedMessage
                isLoading = false
            }
        }
    }

    LaunchedEffect(mangaId) {
        coroutineScope.launch {
            isLoading = true
            repository.getMangaDetails(mangaId).onSuccess {
                manga = it
            }.onFailure {
                errorMessage = it.localizedMessage
            }
            loadChapters(selectedLanguage)
        }
        if (repository.settingsManager.isLoggedIn) {
            coroutineScope.launch {
                repository.isMangaFollowed(mangaId).onSuccess { isFollowed = it }
            }
            coroutineScope.launch {
                repository.getMangaReadingStatus(mangaId).onSuccess { readingStatus = it }
            }
        }
    }

    val displayedChapters = remember(chapters, isAscending) {
        if (isAscending) chapters else chapters.reversed()
    }

    val onFollowToggle: () -> Unit = {
        coroutineScope.launch {
            isFollowUpdating = true
            if (isFollowed) {
                val res = repository.unfollowManga(mangaId)
                if (res.isSuccess) isFollowed = false
            } else {
                val res = repository.followManga(mangaId)
                if (res.isSuccess) isFollowed = true
            }
            isFollowUpdating = false
        }
    }

    val detailListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var isScreenRefreshing by remember { mutableStateOf(false) }
    val localView = androidx.compose.ui.platform.LocalView.current
    val localContext = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = manga?.displayTitle ?: "Chi tiết truyện",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = EInkBlack
                        )
                    }
                },
                modifier = Modifier.statusBarsPadding(),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EInkWhite,
                    titleContentColor = EInkBlack
                )
            )
        },
        containerColor = EInkWhite
    ) { innerPadding ->
        // THÍCH ỨNG TỶ LỆ MÀN HÌNH (Responsive Split-Pane):
        // - Màn hình hẹp (máy dọc): dạng cuộn 1 cột truyền thống
        // - Màn hình rộng >= 600.dp (máy 10.3" hoặc xoay ngang): chia 2 cột song song tiện lợi
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(EInkWhite)
        ) {
            val isWideScreen = maxWidth >= 600.dp

            if (isWideScreen) {
                // GIAO DIỆN 2 CỘT CHO MÀN HÌNH RỘNG / XOAY NGANG
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cột trái: Thông tin bìa & Mô tả tóm tắt
                    Column(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        manga?.let { m ->
                            MangaInfoContent(
                                repository = repository,
                                manga = m,
                                isFollowed = isFollowed,
                                readingStatus = readingStatus,
                                isFollowUpdating = isFollowUpdating,
                                onFollowToggle = onFollowToggle,
                                onStatusClick = { showStatusDialog = true },
                                readingRecord = readingRecord,
                                firstChapter = displayedChapters.firstOrNull(),
                                onReadClick = { chId, chTitle, p ->
                                    onChapterClick(chId, chTitle, p, m.displayTitle, m.getCoverUrl(repository.settingsManager.apiBaseUrl) ?: m.coverUrl)
                                }
                            )
                        }
                    }

                    VerticalDivider(color = EInkBorder, thickness = 1.dp)

                    // Cột phải: Bộ lọc & Danh sách chương
                    Column(
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight()
                    ) {
                        ChapterHeaderFilter(
                            selectedLanguage = selectedLanguage,
                            availableLanguages = manga?.attributes?.availableTranslatedLanguages ?: emptyList(),
                            onLanguageSelected = {
                                selectedLanguage = it
                                loadChapters(it)
                            },
                            isAscending = isAscending,
                            onToggleSort = { isAscending = !isAscending }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        ChapterListContent(
                            isLoading = isLoading,
                            chapters = displayedChapters,
                            manga = manga,
                            downloadManager = downloadManager,
                            downloadStatusMap = downloadStatusMap,
                            readingRecord = readingRecord,
                            readChapterIds = readChapterIds,
                            onChapterClick = { chId, chTitle, p ->
                                onChapterClick(chId, chTitle, p, manga?.displayTitle, manga?.getCoverUrl(repository.settingsManager.apiBaseUrl) ?: manga?.coverUrl)
                            },
                            onDownloadClick = { ch ->
                                manga?.let { m ->
                                    coroutineScope.launch {
                                        downloadManager.downloadChapter(repository, m, ch)
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                // GIAO DIỆN DỌC TRUYỀN THỐNG CHO MÁY MÀN HÌNH NHỎ (PALMA, PAGE)
                LazyColumn(
                    state = detailListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    item {
                        manga?.let { m ->
                            MangaInfoContent(
                                repository = repository,
                                manga = m,
                                isFollowed = isFollowed,
                                readingStatus = readingStatus,
                                isFollowUpdating = isFollowUpdating,
                                onFollowToggle = onFollowToggle,
                                onStatusClick = { showStatusDialog = true },
                                readingRecord = readingRecord,
                                firstChapter = displayedChapters.firstOrNull(),
                                onReadClick = { chId, chTitle, p ->
                                    onChapterClick(chId, chTitle, p, m.displayTitle, m.getCoverUrl(repository.settingsManager.apiBaseUrl) ?: m.coverUrl)
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    item {
                        ChapterHeaderFilter(
                            selectedLanguage = selectedLanguage,
                            availableLanguages = manga?.attributes?.availableTranslatedLanguages ?: emptyList(),
                            onLanguageSelected = {
                                selectedLanguage = it
                                loadChapters(it)
                            },
                            isAscending = isAscending,
                            onToggleSort = { isAscending = !isAscending }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "[ ĐANG TẢI DANH SÁCH CHƯƠNG... ]",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.border(1.dp, EInkBorder, RoundedCornerShape(4.dp)).padding(12.dp)
                                )
                            }
                        }
                    } else if (errorMessage != null && displayedChapters.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Lỗi tải chương: $errorMessage",
                                    color = Color.Red,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    } else if (displayedChapters.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Chưa có chương nào với ngôn ngữ đã chọn.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        items(displayedChapters, key = { it.id }) { chapter ->
                            val isLastRead = chapter.id == readingRecord?.lastChapterId
                            val isRead = readChapterIds.contains(chapter.id)
                            val targetPage = if (isLastRead) readingRecord?.lastReadPage ?: 1 else 1

                            ChapterRow(
                                manga = manga,
                                chapter = chapter,
                                downloadManager = downloadManager,
                                downloadStatus = downloadStatusMap[chapter.id],
                                isLastRead = isLastRead,
                                isRead = isRead,
                                lastReadPage = targetPage,
                                onChapterClick = { chId, chTitle, p ->
                                    onChapterClick(chId, chTitle, p, manga?.displayTitle, manga?.getCoverUrl(repository.settingsManager.apiBaseUrl) ?: manga?.coverUrl)
                                },
                                onDownloadClick = {
                                    manga?.let { m ->
                                        coroutineScope.launch {
                                            downloadManager.downloadChapter(repository, m, chapter)
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }

            if (repository.settingsManager.eInkPageButtonsEnabled) {
                com.eink.reader.ui.components.EInkScrollButtonsForLazyList(
                    listState = detailListState,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp),
                    onRefresh = {
                        com.eink.reader.util.EInkHelper.triggerFullEInkRefresh(
                            scope = coroutineScope,
                            view = localView,
                            context = localContext,
                            onFlashStateChange = { isScreenRefreshing = it }
                        )
                    }
                )
            }

            if (isScreenRefreshing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
        }
    }

    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = {
                Text(
                    text = "TRẠNG THÁI ĐỌC (MDLIST)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = EInkBlack
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Chọn danh mục đọc trên tài khoản MangaDex:",
                        fontSize = 12.sp,
                        color = EInkDarkGray
                    )
                    ReadingStatusEnum.entries.forEach { statusEnum ->
                        val isSelected = readingStatus == statusEnum.apiValue
                        OutlinedButton(
                            onClick = {
                                showStatusDialog = false
                                coroutineScope.launch {
                                    isStatusUpdating = true
                                    val res = repository.updateMangaReadingStatus(mangaId, statusEnum.apiValue)
                                    if (res.isSuccess) {
                                        readingStatus = statusEnum.apiValue
                                    }
                                    isStatusUpdating = false
                                }
                            },
                            shape = RoundedCornerShape(2.dp),
                            border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, EInkBlack),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) EInkBlack else EInkWhite,
                                contentColor = if (isSelected) EInkWhite else EInkBlack
                            ),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text(
                                text = "${statusEnum.icon} ${statusEnum.titleVi}",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) EInkWhite else EInkBlack,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (readingStatus != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                showStatusDialog = false
                                coroutineScope.launch {
                                    isStatusUpdating = true
                                    val res = repository.updateMangaReadingStatus(mangaId, null)
                                    if (res.isSuccess) {
                                        readingStatus = null
                                    }
                                    isStatusUpdating = false
                                }
                            },
                            shape = RoundedCornerShape(2.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("🗑 Xóa khỏi danh sách đọc", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(
                    onClick = { showStatusDialog = false },
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack)
                ) {
                    Text("Đóng", color = EInkBlack, fontSize = 12.sp)
                }
            },
            containerColor = EInkWhite,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.border(2.dp, EInkBlack, RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun MangaInfoContent(
    repository: MangaRepository,
    manga: MangaItem,
    isFollowed: Boolean,
    readingStatus: String?,
    isFollowUpdating: Boolean,
    onFollowToggle: () -> Unit,
    onStatusClick: () -> Unit,
    readingRecord: com.eink.reader.data.repository.ReadingRecord? = null,
    firstChapter: ChapterItem? = null,
    onReadClick: (String, String, Int) -> Unit = { _, _, _ -> }
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(160.dp)
                    .border(1.dp, EInkBorder, RoundedCornerShape(4.dp))
                    .background(EInkSurface)
            ) {
                if (!manga.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = manga.getCoverUrl(repository.settingsManager.apiBaseUrl) ?: manga.coverUrl,
                        contentDescription = manga.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = manga.displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp)
                )
                manga.authorName?.let {
                    Text(
                        text = "Tác giả: $it",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                manga.attributes.status?.let {
                    Text(
                        text = "Trạng thái: ${it.uppercase()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                manga.attributes.year?.let {
                    Text(
                        text = "Năm phát hành: $it",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (repository.settingsManager.isLoggedIn) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Nút Theo dõi (Follow)
                        OutlinedButton(
                            onClick = onFollowToggle,
                            enabled = !isFollowUpdating,
                            shape = RoundedCornerShape(2.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isFollowed) EInkBlack else EInkWhite,
                                contentColor = if (isFollowed) EInkWhite else EInkBlack
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = if (isFollowed) "✓ Đang theo dõi" else "+ Theo dõi",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFollowed) EInkWhite else EInkBlack
                            )
                        }

                        // 2. Nút Trạng thái đọc (MDList)
                        OutlinedButton(
                            onClick = onStatusClick,
                            shape = RoundedCornerShape(2.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (readingStatus != null) EInkBlack else EInkWhite,
                                contentColor = if (readingStatus != null) EInkWhite else EInkBlack
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = ReadingStatusEnum.getTitle(readingStatus),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (readingStatus != null) EInkWhite else EInkBlack
                            )
                        }
                    }
                }
            }
        }

        // Nút Đọc tiếp hoặc Bắt đầu đọc (E-Ink High Contrast)
        if (readingRecord != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    onReadClick(
                        readingRecord.lastChapterId,
                        readingRecord.lastChapterTitle,
                        readingRecord.lastReadPage
                    )
                },
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite),
                modifier = Modifier.fillMaxWidth().height(38.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = EInkWhite, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                val pageInfo = if (readingRecord.totalPages > 0) " (Trang ${readingRecord.lastReadPage}/${readingRecord.totalPages})" else " (Trang ${readingRecord.lastReadPage})"
                Text(
                    text = "ĐỌC TIẾP: ${readingRecord.lastChapterTitle}$pageInfo",
                    color = EInkWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else if (firstChapter != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    onReadClick(firstChapter.id, firstChapter.displayTitle, 1)
                },
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite),
                modifier = Modifier.fillMaxWidth().height(38.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = EInkWhite, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "BẮT ĐẦU ĐỌC (${firstChapter.displayTitle})",
                    color = EInkWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (manga.displayDescription.isNotBlank()) {
            Text(
                text = "NỘI DUNG TÓM TẮT",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = manga.displayDescription,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                modifier = Modifier
                    .border(1.dp, EInkBorder, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun ChapterHeaderFilter(
    selectedLanguage: String,
    availableLanguages: List<String> = emptyList(),
    onLanguageSelected: (String) -> Unit,
    isAscending: Boolean,
    onToggleSort: () -> Unit
) {
    var showLanguagePicker by remember { mutableStateOf(false) }
    val commonLangs = listOf("vi" to "Tiếng Việt", "en" to "English", "all" to "Tất cả")
    val isCustomLang = commonLangs.none { it.first == selectedLanguage }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                commonLangs.forEach { (code, label) ->
                    val isSelected = selectedLanguage == code
                    OutlinedButton(
                        onClick = {
                            if (!isSelected) onLanguageSelected(code)
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
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) EInkWhite else EInkBlack,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (isCustomLang) {
                    val customName = com.eink.reader.data.model.Languages.getDisplayName(selectedLanguage)
                    OutlinedButton(
                        onClick = { showLanguagePicker = true },
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, EInkBlack),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = EInkBlack,
                            contentColor = EInkWhite
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "$customName ▼",
                            color = EInkWhite,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showLanguagePicker = true },
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = EInkWhite,
                        contentColor = EInkBlack
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "🌐 Khác ▼",
                        color = EInkBlack,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            IconButton(
                onClick = onToggleSort,
                modifier = Modifier
                    .size(32.dp)
                    .border(1.dp, EInkBorder, RoundedCornerShape(2.dp))
            ) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = "Đổi thứ tự chapter",
                    tint = EInkBlack
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = EInkBorder, thickness = 1.dp)
    }

    if (showLanguagePicker) {
        val detectedList = availableLanguages.filter { it.isNotBlank() }.distinct()
        val allLangs = if (detectedList.isNotEmpty()) {
            val list = detectedList.toMutableList()
            com.eink.reader.data.model.Languages.ALL_COMMON_LANGUAGES.forEach { (code, _) ->
                if (!list.contains(code)) list.add(code)
            }
            list
        } else {
            com.eink.reader.data.model.Languages.ALL_COMMON_LANGUAGES.map { it.first }
        }

        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            shape = RoundedCornerShape(4.dp),
            containerColor = EInkWhite,
            title = {
                Text(
                    text = "CHỌN NGÔN NGỮ CHƯƠNG",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = EInkBlack
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (detectedList.isNotEmpty()) "Các ngôn ngữ phát hiện có sẵn từ MangaDex:" else "Chọn ngôn ngữ bạn muốn lọc:",
                        style = MaterialTheme.typography.bodySmall,
                        color = EInkDarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            val isSelected = selectedLanguage == "all"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onLanguageSelected("all")
                                        showLanguagePicker = false
                                    }
                                    .background(if (isSelected) EInkSurface else Color.Transparent)
                                    .border(1.dp, if (isSelected) EInkBlack else EInkBorder, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🌐 Tất cả ngôn ngữ (Hiển thị toàn bộ)",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    color = EInkBlack
                                )
                                if (isSelected) {
                                    Text("✓", fontWeight = FontWeight.Bold, color = EInkBlack)
                                }
                            }
                        }

                        items(allLangs) { code ->
                            val isSelected = selectedLanguage == code
                            val name = com.eink.reader.data.model.Languages.getDisplayName(code)
                            val isDetected = detectedList.contains(code)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onLanguageSelected(code)
                                        showLanguagePicker = false
                                    }
                                    .background(if (isSelected) EInkSurface else Color.Transparent)
                                    .border(1.dp, if (isSelected) EInkBlack else EInkBorder, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "[${code.uppercase()}]",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = EInkBlack,
                                        modifier = Modifier
                                            .border(1.dp, EInkBlack, RoundedCornerShape(2.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = EInkBlack
                                    )
                                    if (isDetected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "• có sẵn",
                                            fontSize = 11.sp,
                                            color = EInkDarkGray
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Text("✓", fontWeight = FontWeight.Bold, color = EInkBlack)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguagePicker = false }) {
                    Text("ĐÓNG", color = EInkBlack, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun ChapterListContent(
    isLoading: Boolean,
    chapters: List<ChapterItem>,
    manga: MangaItem?,
    downloadManager: DownloadManager,
    downloadStatusMap: Map<String, DownloadStatus>,
    readingRecord: com.eink.reader.data.repository.ReadingRecord? = null,
    readChapterIds: Set<String> = emptySet(),
    onChapterClick: (String, String, Int) -> Unit,
    onDownloadClick: (ChapterItem) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "[ ĐANG TẢI DANH SÁCH CHƯƠNG... ]",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else if (chapters.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Chưa có chương nào với ngôn ngữ đã chọn.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(chapters, key = { it.id }) { chapter ->
                val isLastRead = chapter.id == readingRecord?.lastChapterId
                val isRead = readChapterIds.contains(chapter.id)
                val targetPage = if (isLastRead) readingRecord?.lastReadPage ?: 1 else 1

                ChapterRow(
                    manga = manga,
                    chapter = chapter,
                    downloadManager = downloadManager,
                    downloadStatus = downloadStatusMap[chapter.id],
                    isLastRead = isLastRead,
                    isRead = isRead,
                    lastReadPage = targetPage,
                    onChapterClick = onChapterClick,
                    onDownloadClick = { onDownloadClick(chapter) }
                )
            }
        }
    }
}

@Composable
fun ChapterRow(
    manga: MangaItem?,
    chapter: ChapterItem,
    downloadManager: DownloadManager,
    downloadStatus: DownloadStatus?,
    isLastRead: Boolean = false,
    isRead: Boolean = false,
    lastReadPage: Int = 1,
    onChapterClick: (String, String, Int) -> Unit,
    onDownloadClick: () -> Unit
) {
    val isDownloaded = remember(manga, chapter, downloadStatus) {
        if (manga != null) downloadManager.isChapterDownloaded(manga.displayTitle, chapter.displayTitle) else false
    } || (downloadStatus is DownloadStatus.Downloaded)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isLastRead) 2.dp else 1.dp,
                color = if (isLastRead) EInkBlack else EInkBorder,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable {
                    val targetPage = if (isLastRead) lastReadPage else 1
                    if (isDownloaded && manga != null) {
                        val cbz = downloadManager.getChapterCbzFile(manga.displayTitle, chapter.displayTitle)
                        onChapterClick(cbz.absolutePath, chapter.displayTitle, targetPage)
                    } else {
                        onChapterClick(chapter.id, chapter.displayTitle, targetPage)
                    }
                }
                .padding(vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLastRead) {
                    Text(
                        text = "ĐANG ĐỌC",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EInkWhite,
                        modifier = Modifier
                            .background(EInkBlack, RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                } else if (isRead) {
                    Text(
                        text = "✓ ĐÃ ĐỌC",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = EInkDarkGray,
                        modifier = Modifier
                            .border(1.dp, EInkBorder, RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (isDownloaded) {
                    Text(
                        text = "CBZ",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EInkWhite,
                        modifier = Modifier
                            .background(EInkBlack, RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = chapter.displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp)
                )
            }
            chapter.scanlationGroup?.let { group ->
                Text(
                    text = "Dịch bởi: $group",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp, color = Color.Gray)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = chapter.attributes.translatedLanguage.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .border(1.dp, EInkBlack, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            when (downloadStatus) {
                is DownloadStatus.Downloading -> {
                    Text(
                        text = "${(downloadStatus.progress * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EInkBlack,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                is DownloadStatus.Downloaded -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Đã tải offline",
                        tint = EInkBlack,
                        modifier = Modifier.size(24.dp)
                    )
                }
                else -> {
                    if (isDownloaded) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Đã tải offline",
                            tint = EInkBlack,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        IconButton(
                            onClick = onDownloadClick,
                            modifier = Modifier
                                .size(36.dp)
                                .border(1.dp, EInkBorder, RoundedCornerShape(4.dp))
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Tải CBZ offline",
                                tint = EInkBlack,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}