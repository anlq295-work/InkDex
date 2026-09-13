package com.eink.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eink.reader.data.download.DownloadManager
import com.eink.reader.data.download.DownloadedManga
import com.eink.reader.data.repository.MangaRepository
import com.eink.reader.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    repository: MangaRepository? = null,
    onMangaClick: ((String) -> Unit)? = null,
    onChapterClick: (String, String, String?, String?, String?, Int) -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val downloadManager = remember { DownloadManager.getInstance(context) }
    var downloadedMangaList by remember { mutableStateOf<List<DownloadedManga>>(emptyList()) }
    var selectedManga by remember { mutableStateOf<DownloadedManga?>(null) }
    var selectedLibraryTab by remember { mutableIntStateOf(0) } // 0: Đang đọc, 1: Offline, 2: MangaDex Sync

    // Reading History State
    var readingHistoryList by remember { mutableStateOf<List<com.eink.reader.data.repository.ReadingRecord>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    fun refreshHistory() {
        if (repository != null) {
            val records = repository.readingHistoryManager.getAllRecords()
            val allowPornographic = repository.settingsManager.contentRatingPornographic
            val filtered = if (!allowPornographic) {
                records.filterNot { rec -> repository.tagCacheManager.isPornographic(rec.mangaId) }
            } else {
                records
            }
            readingHistoryList = filtered

            // Tự động quét và vá lại tên truyện / ảnh bìa nếu lịch sử trước đây bị thiếu
            coroutineScope.launch {
                var hasUpdates = false
                for (rec in records) {
                    val isOffline = rec.mangaId.endsWith(".cbz", ignoreCase = true) || rec.lastChapterId.endsWith(".cbz", ignoreCase = true)
                    val isTitleInvalid = rec.mangaTitle.isBlank() || rec.mangaTitle == "Truyện không tên" || rec.mangaTitle == "Untitled" || rec.mangaTitle.startsWith("Ch.", ignoreCase = true)
                    val isCoverMissing = rec.coverUrl.isNullOrBlank()
                    if (!isOffline && (isTitleInvalid || isCoverMissing)) {
                        repository.getMangaDetails(rec.mangaId).onSuccess { details ->
                            val resolvedTitle = details.displayTitle
                            val resolvedCover = details.getCoverUrl(repository.settingsManager.apiBaseUrl) ?: details.coverUrl
                            repository.readingHistoryManager.updateMangaInfo(rec.mangaId, resolvedTitle, resolvedCover)
                            hasUpdates = true
                        }
                    }
                }
                if (hasUpdates) {
                    val updated = repository.readingHistoryManager.getAllRecords()
                    readingHistoryList = if (!allowPornographic) {
                        updated.filterNot { rec -> repository.tagCacheManager.isPornographic(rec.mangaId) }
                    } else {
                        updated
                    }
                }
            }
        }
    }

    // MangaDex Sync state — restore from cache if available
    val cachedSync = repository?.librarySyncCache
    var onlineMangaList by remember { mutableStateOf(cachedSync?.mangaList ?: emptyList()) }
    var onlineStatusesMap by remember { mutableStateOf(cachedSync?.statusesMap ?: emptyMap()) }
    var selectedFilterStatus by remember { mutableStateOf("all") }
    var isOnlineLoading by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }

    val historyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val downloadedGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val onlineGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val chaptersListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var isScreenRefreshing by remember { mutableStateOf(false) }
    val localView = androidx.compose.ui.platform.LocalView.current

    fun refreshOnline() {
        if (repository?.settingsManager?.isLoggedIn == true) {
            isOnlineLoading = true
            onlineError = null
            coroutineScope.launch {
                try {
                    val st = repository.getAllReadingStatuses().getOrDefault(emptyMap())
                    onlineStatusesMap = st
                    val fRes = repository.getUserFollowedManga(limit = 100)
                    if (fRes.isSuccess) {
                        onlineMangaList = fRes.getOrThrow()
                        onlineError = null
                        // Save to in-memory cache so data survives navigation
                        repository.librarySyncCache = com.eink.reader.data.repository.LibrarySyncCache(
                            mangaList = onlineMangaList,
                            statusesMap = onlineStatusesMap
                        )
                    } else {
                        onlineError = fRes.exceptionOrNull()?.message ?: "Lỗi tải thư viện MangaDex"
                        onlineMangaList = emptyList()
                    }
                    isOnlineLoading = false
                } catch (e: Exception) {
                    onlineError = e.localizedMessage ?: "Lỗi tải thư viện MangaDex"
                    isOnlineLoading = false
                }
            }
        }
    }

    fun refresh() {
        refreshHistory()
        downloadedMangaList = downloadManager.getDownloadedMangaList()
        selectedManga = selectedManga?.let { cur ->
            downloadedMangaList.firstOrNull { it.mangaId == cur.mangaId }
        }
        if (selectedLibraryTab == 2) {
            refreshOnline()
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    LaunchedEffect(selectedLibraryTab) {
        if (selectedLibraryTab == 0) {
            refreshHistory()
        } else if (selectedLibraryTab == 2 && onlineMangaList.isEmpty()) {
            refreshOnline()
        }
    }

    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && (key.startsWith("cr_") || key == "session_token")) {
                refreshHistory()
                if (selectedLibraryTab == 2) {
                    refreshOnline()
                }
            }
        }
        repository?.settingsManager?.prefsInstance?.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            repository?.settingsManager?.prefsInstance?.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedManga?.title
                            ?: when (selectedLibraryTab) {
                                0 -> "TIẾN ĐỘ ĐỌC TRÊN MÁY"
                                1 -> "THƯ VIỆN OFFLINE (CBZ)"
                                else -> "THƯ VIỆN MANGADEX"
                            },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (selectedManga != null) {
                        IconButton(onClick = { selectedManga = null }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Quay lại",
                                tint = EInkBlack
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Làm mới thư viện",
                            tint = EInkBlack
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EInkWhite)
            )
        },
        containerColor = EInkWhite
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(EInkWhite)
        ) {
            val currentManga = selectedManga
            if (currentManga != null) {
                // DANH SÁCH CHƯƠNG ĐÃ TẢI CỦA TRUYỆN ĐƯỢC CHỌN
                if (currentManga.chapters.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Chưa có chương nào trong thư mục này.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(
                        state = chaptersListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "Tổng cộng: ${currentManga.chapters.size} chương offline (CBZ)",
                                fontSize = 12.sp,
                                color = EInkDarkGray,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        items(currentManga.chapters, key = { it.file.absolutePath }) { ch ->
                            val sizeMb = String.format("%.1f MB", ch.sizeBytes.toFloat() / (1024 * 1024))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, EInkBorder, RoundedCornerShape(4.dp))
                                    .clickable {
                                        onChapterClick(ch.file.absolutePath, ch.chapterTitle, currentManga.mangaId, currentManga.title, currentManga.coverFile?.absolutePath, 1)
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = EInkBlack,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = ch.chapterTitle,
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp)
                                        )
                                        Text(
                                            text = "Định dạng CBZ • $sizeMb",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp, color = EInkDarkGray)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        downloadManager.deleteChapter(ch.file)
                                        refresh()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Xóa file CBZ",
                                        tint = EInkBlack,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Thanh chuyển đổi 3 chế độ Thư viện E-Ink
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .border(1.dp, EInkBorder, RoundedCornerShape(4.dp))
                            .background(EInkSurface)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedLibraryTab = 0 }
                                .background(if (selectedLibraryTab == 0) EInkBlack else Color.Transparent)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Đang đọc (${readingHistoryList.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (selectedLibraryTab == 0) EInkWhite else EInkBlack
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedLibraryTab = 1 }
                                .background(if (selectedLibraryTab == 1) EInkBlack else Color.Transparent)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tải về (${downloadedMangaList.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (selectedLibraryTab == 1) EInkWhite else EInkBlack
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedLibraryTab = 2 }
                                .background(if (selectedLibraryTab == 2) EInkBlack else Color.Transparent)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "MangaDex Sync",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (selectedLibraryTab == 2) EInkWhite else EInkBlack
                            )
                        }
                    }

                    if (selectedLibraryTab == 0) {
                        // TAB 0: TIẾN ĐỘ ĐỌC TRÊN MÁY (READING HISTORY)
                        if (readingHistoryList.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "[ CHƯA CÓ TRUYỆN ĐANG ĐỌC ]",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Khi bạn đọc một chương truyện bất kỳ, tiến độ và chương đang đọc dở sẽ được tự động lưu tại đây để bạn có thể tiếp tục đọc bất cứ lúc nào.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EInkDarkGray
                                )
                            }
                        } else {
                            LazyColumn(
                                state = historyListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(readingHistoryList, key = { it.mangaId }) { record ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, EInkBorder, RoundedCornerShape(4.dp))
                                            .clickable {
                                                onMangaClick?.invoke(record.mangaId)
                                            },
                                        shape = RoundedCornerShape(4.dp),
                                        colors = CardDefaults.cardColors(containerColor = EInkWhite)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Ảnh bìa
                                            Box(
                                                modifier = Modifier
                                                    .width(70.dp)
                                                    .height(100.dp)
                                                    .border(1.dp, EInkBorder, RoundedCornerShape(3.dp))
                                                    .background(EInkSurface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!record.coverUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = record.coverUrl,
                                                        contentDescription = record.mangaTitle,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(RoundedCornerShape(3.dp))
                                                    )
                                                } else {
                                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color.Gray)
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            // Thông tin chương đang đọc
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = record.mangaTitle,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Đang đọc: ${record.lastChapterTitle}",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val pageProgress = if (record.totalPages > 0) {
                                                    "Trang ${record.lastReadPage} / ${record.totalPages} • ${(record.lastReadPage * 100 / record.totalPages)}%"
                                                } else {
                                                    "Trang ${record.lastReadPage}"
                                                }
                                                Text(
                                                    text = pageProgress,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp, color = EInkDarkGray)
                                                )
                                                Text(
                                                    text = formatTimeAgo(record.updatedAt),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = Color.Gray)
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            onChapterClick(
                                                                record.lastChapterId,
                                                                record.lastChapterTitle,
                                                                record.mangaId,
                                                                record.mangaTitle,
                                                                record.coverUrl,
                                                                record.lastReadPage
                                                            )
                                                        },
                                                        shape = RoundedCornerShape(2.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = EInkBlack,
                                                            contentColor = EInkWhite
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = EInkWhite, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("ĐỌC TIẾP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EInkWhite)
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            repository?.readingHistoryManager?.removeRecord(record.mangaId)
                                                            refreshHistory()
                                                        },
                                                        shape = RoundedCornerShape(2.dp),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Xóa khỏi lịch sử", tint = EInkDarkGray, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (selectedLibraryTab == 1) {
                        // DANH SÁCH TẤT CẢ CÁC ĐẦU TRUYỆN ĐÃ TẢI OFFLINE
                        if (downloadedMangaList.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "[ THƯ VIỆN OFFLINE TRỐNG ]",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Các chương truyện được tải về dưới dạng file .cbz sẽ được lưu trong thư mục mangadex-download và hiển thị tại đây để đọc offline mà không cần mạng Internet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EInkDarkGray
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                state = downloadedGridState,
                                columns = GridCells.Adaptive(minSize = 140.dp),
                                contentPadding = PaddingValues(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(downloadedMangaList, key = { it.mangaId }) { manga ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedManga = manga },
                                        shape = RoundedCornerShape(4.dp),
                                        colors = CardDefaults.cardColors(containerColor = EInkWhite),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(180.dp)
                                                    .background(EInkSurface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (manga.coverFile != null && manga.coverFile.exists()) {
                                                    AsyncImage(
                                                        model = manga.coverFile,
                                                        contentDescription = manga.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                    )
                                                } else {
                                                    Text(
                                                        text = "[ Bìa offline ]",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }

                                                Text(
                                                    text = "${manga.chapters.size} CBZ",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EInkWhite,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(6.dp)
                                                        .background(EInkBlack, RoundedCornerShape(2.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp)
                                            ) {
                                                Text(
                                                    text = manga.title,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                                                    minLines = 2,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = manga.author ?: " ",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp, color = EInkDarkGray),
                                                    minLines = 1,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // DANH SÁCH MANGADEX SYNC (ĐÃ ĐỌC & THEO DÕI)
                        if (repository == null || !repository.settingsManager.isLoggedIn) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "[ CHƯA ĐĂNG NHẬP MANGADEX ]",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Vào mục Cài đặt -> Đăng nhập tài khoản MangaDex để tự động đồng bộ danh sách truyện bạn đang đọc, đã đọc xong và đang theo dõi tại đây.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EInkDarkGray
                                )
                            }
                        } else if (isOnlineLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "[ ĐANG ĐỒNG BỘ THƯ VIỆN MANGADEX... ]",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .border(1.dp, EInkBorder, RoundedCornerShape(4.dp))
                                        .padding(16.dp)
                                )
                            }
                        } else if (onlineError != null && onlineMangaList.isEmpty()) {
                            val isExpired = onlineError?.contains("401") == true ||
                                    onlineError?.contains("hết hạn", ignoreCase = true) == true ||
                                    com.eink.reader.data.api.OAuthHelper.isTokenExpired(repository.settingsManager.sessionToken)

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (isExpired) {
                                    Text(
                                        text = "⚠️ PHIÊN ĐĂNG NHẬP ĐÃ HẾT HẠN",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = EInkBlack
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Session token của MangaDex chỉ có hạn 15 phút. Vui lòng đăng nhập lại (hoặc làm mới token) trong Cài đặt để đồng bộ 6 truyện đang theo dõi của bạn.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = EInkDarkGray
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = onSettingsClick,
                                        shape = RoundedCornerShape(2.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite),
                                        modifier = Modifier.fillMaxWidth().height(40.dp)
                                    ) {
                                        Text("ĐĂNG NHẬP LẠI TRONG CÀI ĐẶT", color = EInkWhite, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { refreshOnline() },
                                        border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                                        shape = RoundedCornerShape(2.dp),
                                        modifier = Modifier.fillMaxWidth().height(40.dp)
                                    ) {
                                        Text("Thử tải lại", color = EInkBlack)
                                    }
                                } else {
                                    Text(text = "Lỗi kết nối: $onlineError", color = Color.Red)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { refreshOnline() },
                                        border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                                        shape = RoundedCornerShape(2.dp)
                                    ) {
                                        Text("Thử lại", color = EInkBlack)
                                    }
                                }
                            }
                        } else if (onlineMangaList.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "[ CHƯA CÓ TRUYỆN ĐƯỢC ĐỒNG BỘ ]",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Bạn chưa theo dõi hoặc chưa đánh dấu trạng thái đọc cho truyện nào trên tài khoản MangaDex.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EInkDarkGray
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { refreshOnline() },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                                    shape = RoundedCornerShape(2.dp)
                                ) {
                                    Text("Tải lại", color = EInkBlack)
                                }
                            }
                        } else {
                            val filterOptions = listOf(
                                "all" to "Tất cả (${onlineMangaList.size})",
                                "reading" to "Đang đọc",
                                "plan_to_read" to "Dự định",
                                "completed" to "Đã xong",
                                "on_hold" to "Tạm ngưng",
                                "dropped" to "Bỏ dở"
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                filterOptions.forEach { (statusKey, label) ->
                                    val isSelected = selectedFilterStatus == statusKey
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, if (isSelected) EInkBlack else EInkBorder, RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) EInkBlack else EInkWhite,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { selectedFilterStatus = statusKey }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) EInkWhite else EInkBlack
                                        )
                                    }
                                }
                            }

                            val displayedOnlineList = remember(onlineMangaList, selectedFilterStatus, onlineStatusesMap) {
                                if (selectedFilterStatus == "all") onlineMangaList
                                else onlineMangaList.filter { onlineStatusesMap[it.id] == selectedFilterStatus }
                            }

                            if (displayedOnlineList.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Không có truyện nào ở mục này.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = EInkDarkGray
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    state = onlineGridState,
                                    columns = GridCells.Adaptive(minSize = 135.dp),
                                    contentPadding = PaddingValues(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(displayedOnlineList, key = { it.id }) { manga ->
                                        if (repository != null) {
                                            MangaGridCard(
                                                repository = repository,
                                                manga = manga,
                                                onClick = { onMangaClick?.invoke(manga.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (repository?.settingsManager?.eInkPageButtonsEnabled == true) {
                val currentMangaSelected = selectedManga
                if (currentMangaSelected != null && currentMangaSelected.chapters.isNotEmpty()) {
                    com.eink.reader.ui.components.EInkScrollButtonsForLazyList(
                        listState = chaptersListState,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 12.dp),
                        onRefresh = {
                            com.eink.reader.util.EInkHelper.triggerFullEInkRefresh(
                                scope = coroutineScope,
                                view = localView,
                                context = context,
                                onFlashStateChange = { isScreenRefreshing = it }
                            )
                        }
                    )
                } else if (currentMangaSelected == null) {
                    when (selectedLibraryTab) {
                        0 -> {
                            if (readingHistoryList.isNotEmpty()) {
                                com.eink.reader.ui.components.EInkScrollButtonsForLazyList(
                                    listState = historyListState,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 12.dp, bottom = 12.dp),
                                    onRefresh = {
                                        com.eink.reader.util.EInkHelper.triggerFullEInkRefresh(
                                            scope = coroutineScope,
                                            view = localView,
                                            context = context,
                                            onFlashStateChange = { isScreenRefreshing = it }
                                        )
                                    }
                                )
                            }
                        }
                        1 -> {
                            if (downloadedMangaList.isNotEmpty()) {
                                com.eink.reader.ui.components.EInkScrollButtonsForLazyGrid(
                                    gridState = downloadedGridState,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 12.dp, bottom = 12.dp),
                                    onRefresh = {
                                        com.eink.reader.util.EInkHelper.triggerFullEInkRefresh(
                                            scope = coroutineScope,
                                            view = localView,
                                            context = context,
                                            onFlashStateChange = { isScreenRefreshing = it }
                                        )
                                    }
                                )
                            }
                        }
                        2 -> {
                            if (onlineMangaList.isNotEmpty()) {
                                com.eink.reader.ui.components.EInkScrollButtonsForLazyGrid(
                                    gridState = onlineGridState,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 12.dp, bottom = 12.dp),
                                    onRefresh = {
                                        com.eink.reader.util.EInkHelper.triggerFullEInkRefresh(
                                            scope = coroutineScope,
                                            view = localView,
                                            context = context,
                                            onFlashStateChange = { isScreenRefreshing = it }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
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
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = (diff / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Vừa xong"
        minutes < 60 -> "$minutes phút trước"
        hours < 24 -> "$hours giờ trước"
        days < 7 -> "$days ngày trước"
        else -> {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}
