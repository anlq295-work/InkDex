package com.eink.reader.ui.screens

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eink.reader.data.model.MangaItem
import com.eink.reader.data.repository.MangaRepository
import com.eink.reader.ui.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    repository: MangaRepository,
    onMangaClick: (String) -> Unit,
    onCategoryClick: (String, String, String) -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isSearchLoading by remember { mutableStateOf(false) }

    var isPornographic by remember { mutableStateOf(repository.settingsManager.contentRatingPornographic) }

    val currentRatingsKey = remember(isPornographic) {
        repository.getRatingsKey()
    }
    val initialCache = repository.homeFeedCache
    val isCacheValid = initialCache != null && initialCache.ratingsKey == currentRatingsKey

    // Dữ liệu 5 đề mục phong cách Neko
    var followedList by remember { mutableStateOf(if (isCacheValid) initialCache!!.followedList else emptyList()) }
    var popularNewList by remember { mutableStateOf(if (isCacheValid) initialCache!!.popularNewList else emptyList()) }
    var latestUploadsList by remember { mutableStateOf(if (isCacheValid) initialCache!!.latestUploadsList else emptyList()) }
    var recentlyAddedList by remember { mutableStateOf(if (isCacheValid) initialCache!!.recentlyAddedList else emptyList()) }
    var randomList by remember { mutableStateOf(if (isCacheValid) initialCache!!.randomList else emptyList()) }
    var popularList by remember { mutableStateOf(if (isCacheValid) initialCache!!.popularList else emptyList()) }

    var isFeedsLoading by remember { mutableStateOf(!isCacheValid) }
    var feedsError by remember { mutableStateOf<String?>(null) }
    var followsError by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val searchGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val feedsScrollState = rememberScrollState()
    var isScreenRefreshing by remember { mutableStateOf(false) }
    val localView = androidx.compose.ui.platform.LocalView.current
    val localContext = androidx.compose.ui.platform.LocalContext.current

    fun loadFeeds(force: Boolean = false) {
        val ratingsKey = repository.getRatingsKey()
        val currentCache = repository.homeFeedCache
        if (!force && currentCache != null && currentCache.ratingsKey == ratingsKey) {
            followedList = currentCache.followedList
            popularNewList = currentCache.popularNewList
            latestUploadsList = currentCache.latestUploadsList
            recentlyAddedList = currentCache.recentlyAddedList
            popularList = currentCache.popularList
            randomList = currentCache.randomList
            isFeedsLoading = false
            return
        }

        coroutineScope.launch {
            isFeedsLoading = true
            feedsError = null
            followsError = null
            try {
                val popNewDeferred = async { repository.getPopularNewTitles(limit = 10) }
                val latestDeferred = async { repository.getLatestUploads(limit = 10) }
                val recentDeferred = async { repository.getRecentlyAdded(limit = 10) }
                val popularDeferred = async { repository.getPopularManga(limit = 10) }
                val followsDeferred = if (repository.settingsManager.isLoggedIn) {
                    async { repository.getUserFollowedManga(limit = 10) }
                } else null

                val pNew = popNewDeferred.await().getOrDefault(emptyList())
                val lUpload = latestDeferred.await().getOrDefault(emptyList())
                val rAdded = recentDeferred.await().getOrDefault(emptyList())
                val pop = popularDeferred.await().getOrDefault(emptyList())
                val followsRes = followsDeferred?.await()
                val follows = followsRes?.getOrDefault(emptyList()) ?: emptyList()
                followsError = followsRes?.exceptionOrNull()?.message


                // Lấy 6 truyện random song song
                val randomDeferreds = (1..6).map { async { repository.getRandomManga() } }
                val randItems = randomDeferreds.mapNotNull { it.await().getOrNull() }

                popularNewList = pNew
                latestUploadsList = lUpload
                recentlyAddedList = rAdded
                popularList = pop
                followedList = follows
                randomList = randItems

                repository.homeFeedCache = com.eink.reader.data.repository.HomeFeedCache(
                    followedList = follows,
                    popularNewList = pNew,
                    latestUploadsList = lUpload,
                    recentlyAddedList = rAdded,
                    popularList = pop,
                    randomList = randItems,
                    ratingsKey = ratingsKey
                )

                isFeedsLoading = false
            } catch (e: Exception) {
                feedsError = e.localizedMessage ?: "Lỗi tải dữ liệu đề mục MangaDex"
                isFeedsLoading = false
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && (key.startsWith("cr_") || key == "session_token")) {
                isPornographic = repository.settingsManager.contentRatingPornographic
                repository.invalidateHomeFeedCache()
                loadFeeds(force = true)
            }
        }
        repository.settingsManager.prefsInstance.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            repository.settingsManager.prefsInstance.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun performSearch(query: String) {
        if (query.isBlank()) {
            isSearching = false
            searchResults = emptyList()
            return
        }
        coroutineScope.launch {
            isSearching = true
            isSearchLoading = true
            repository.searchManga(query).onSuccess {
                searchResults = it
                isSearchLoading = false
            }.onFailure {
                isSearchLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!isCacheValid) {
            loadFeeds(force = false)
        }
    }

    LaunchedEffect(isFeedsLoading) {
        if (!isFeedsLoading) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EInkWhite)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "INKDEX",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val isPrivateBadge = !isSearching && isPornographic
                        val badgeText = if (isSearching) "TÌM KIẾM" else if (isPornographic) "PRIVATE" else "KHÁM PHÁ"
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isPrivateBadge) EInkWhite else EInkBlack,
                            modifier = Modifier
                                .background(
                                    color = if (isPrivateBadge) EInkBlack else Color.Transparent,
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isPrivateBadge) EInkBlack else EInkBorder,
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (!isSearching) {
                        IconButton(
                            onClick = { loadFeeds(force = true) },
                            modifier = Modifier
                                .size(36.dp)
                                .border(1.dp, EInkBorder, RoundedCornerShape(4.dp))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = EInkBlack)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Thanh tìm kiếm E-Ink tương phản cao
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        if (it.isBlank()) {
                            isSearching = false
                            searchResults = emptyList()
                        }
                    },
                    placeholder = { Text("Nhập tên truyện cần tìm...", color = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        performSearch(searchQuery)
                    }),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = EInkBlack)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                isSearching = false
                                searchResults = emptyList()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = EInkBlack)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EInkWhite),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EInkBlack,
                        unfocusedBorderColor = EInkBorder,
                        focusedTextColor = EInkBlack,
                        unfocusedTextColor = EInkBlack
                    ),
                    shape = RoundedCornerShape(4.dp)
                )
            }
        },
        containerColor = EInkWhite
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(EInkWhite)
        ) {
            if (isSearching) {
                // HIỂN THỊ KẾT QUẢ TÌM KIẾM
                if (isSearchLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "[ ĐANG TÌM KIẾM MANGADEX... ]",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.border(1.dp, EInkBorder, RoundedCornerShape(4.dp)).padding(16.dp)
                        )
                    }
                } else if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Không tìm thấy truyện nào phù hợp.", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyVerticalGrid(
                        state = searchGridState,
                        columns = GridCells.Adaptive(minSize = 135.dp),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(searchResults, key = { it.id }) { manga ->
                            MangaGridCard(
                                repository = repository,
                                manga = manga,
                                onClick = { onMangaClick(manga.id) }
                            )
                        }
                    }
                }
            } else {
                // HIỂN THỊ 5 ĐỀ MỤC PHONG CÁCH NEKO
                if (isFeedsLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "[ ĐANG TẢI CÁC ĐỀ MỤC TRUYỆN... ]",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.border(1.dp, EInkBorder, RoundedCornerShape(4.dp)).padding(16.dp)
                        )
                    }
                } else if (feedsError != null && popularList.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Lỗi kết nối: $feedsError", color = Color.Red, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { loadFeeds(force = true) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = EInkBlack)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thử lại", color = EInkBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(feedsScrollState)
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 0. Truyện đang theo dõi (Follows) - chỉ hiện khi đã đăng nhập
                        if (repository.settingsManager.isLoggedIn) {
                            if (followedList.isNotEmpty()) {
                                FeedCategoryRow(
                                    title = "★ Truyện đang theo dõi (Follows)",
                                    mangaList = followedList,
                                    repository = repository,
                                    onSeeAllClick = { onCategoryClick("follows", "Truyện Đang Theo Dõi (Follows)", "all") },
                                    onMangaClick = onMangaClick
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "★ Truyện theo dõi (Follows)",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Đang đọc",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = EInkBlack,
                                                modifier = Modifier
                                                    .clickable { onCategoryClick("follows", "Truyện Đang Đọc", "reading") }
                                                    .border(1.dp, EInkBorder, RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Đã xong",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = EInkBlack,
                                                modifier = Modifier
                                                    .clickable { onCategoryClick("follows", "Truyện Đã Đọc Xong", "completed") }
                                                    .border(1.dp, EInkBorder, RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clickable { onCategoryClick("follows", "Truyện Đang Theo Dõi (Follows)", "all") }
                                                    .border(1.dp, EInkBorder, RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(text = "Tất cả", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = EInkBlack)
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp), tint = EInkBlack)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val isSessionExpired = followsError?.contains("401") == true ||
                                            followsError?.contains("hết hạn", ignoreCase = true) == true ||
                                            com.eink.reader.data.api.OAuthHelper.isTokenExpired(repository.settingsManager.sessionToken)

                                    if (isSessionExpired) {
                                        Card(
                                            shape = RoundedCornerShape(4.dp),
                                            colors = CardDefaults.cardColors(containerColor = EInkSurface),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onSettingsClick() }
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "⚠️ PHIÊN ĐĂNG NHẬP MANGADEX ĐÃ HẾT HẠN (MÃ 401)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = EInkBlack
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Session token của MangaDex chỉ có hạn 15 phút. Bạn cần làm mới token hoặc đăng nhập lại bằng Personal Client / Web để hiển thị 6 truyện đang theo dõi.",
                                                    fontSize = 12.sp,
                                                    color = EInkDarkGray
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "👉 BẤM VÀO ĐÂY ĐỂ VÀO CÀI ĐẶT & ĐĂNG NHẬP LẠI",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EInkBlack
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, EInkBorder, RoundedCornerShape(4.dp))
                                                .background(EInkSurface)
                                                .clickable { loadFeeds(force = true) }
                                                .padding(12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "[ Chưa có truyện trong danh sách theo dõi • Nhấn để tải lại ]",
                                                style = MaterialTheme.typography.bodySmall.copy(color = EInkDarkGray)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 1. Feed updates / Chương mới cập nhật
                        if (latestUploadsList.isNotEmpty()) {
                            FeedCategoryRow(
                                title = "Mới cập nhật chương (Latest Uploads)",
                                mangaList = latestUploadsList,
                                repository = repository,
                                onSeeAllClick = { onCategoryClick("latest_uploads", "Mới Cập Nhật Chương", "all") },
                                onMangaClick = onMangaClick
                            )
                        }

                        // 2. Truyện mới nổi bật (Popular New Titles)
                        if (popularNewList.isNotEmpty()) {
                            FeedCategoryRow(
                                title = "Truyện mới nổi bật (Popular New Titles)",
                                mangaList = popularNewList,
                                repository = repository,
                                onSeeAllClick = { onCategoryClick("popular_new", "Truyện Mới Nổi Bật", "all") },
                                onMangaClick = onMangaClick
                            )
                        }

                        // 3. Mới thêm gần đây (Recently Added)
                        if (recentlyAddedList.isNotEmpty()) {
                            FeedCategoryRow(
                                title = "Mới thêm gần đây (Recently Added)",
                                mangaList = recentlyAddedList,
                                repository = repository,
                                onSeeAllClick = { onCategoryClick("recently_added", "Mới Thêm Gần Đây", "all") },
                                onMangaClick = onMangaClick
                            )
                        }

                        // 4. Khám phá ngẫu nhiên (Random)
                        if (randomList.isNotEmpty()) {
                            FeedCategoryRow(
                                title = "Khám phá ngẫu nhiên (Random Titles)",
                                mangaList = randomList,
                                repository = repository,
                                onSeeAllClick = { onCategoryClick("random", "Khám Phá Ngẫu Nhiên", "all") },
                                onMangaClick = onMangaClick
                            )
                        }

                        // 5. Nhiều người theo dõi nhất (Popular)
                        if (popularList.isNotEmpty()) {
                            FeedCategoryRow(
                                title = "Được theo dõi nhiều nhất (Most Popular)",
                                mangaList = popularList,
                                repository = repository,
                                onSeeAllClick = { onCategoryClick("popular", "Được Theo Dõi Nhiều Nhất", "all") },
                                onMangaClick = onMangaClick
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            if (repository.settingsManager.eInkPageButtonsEnabled) {
                if (isSearching && searchResults.isNotEmpty()) {
                    com.eink.reader.ui.components.EInkScrollButtonsForLazyGrid(
                        gridState = searchGridState,
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
                } else if (!isSearching && !isFeedsLoading && feedsError == null) {
                    com.eink.reader.ui.components.EInkScrollButtonsForScrollState(
                        scrollState = feedsScrollState,
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

@Composable
fun FeedCategoryRow(
    title: String,
    mangaList: List<MangaItem>,
    repository: MangaRepository,
    onSeeAllClick: () -> Unit,
    onMangaClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onSeeAllClick)
                    .border(1.dp, EInkBorder, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = "Xem tất cả", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = EInkBlack)
                Spacer(modifier = Modifier.width(2.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp), tint = EInkBlack)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(mangaList, key = { it.id }) { manga ->
                MangaRowCard(
                    repository = repository,
                    manga = manga,
                    onClick = { onMangaClick(manga.id) }
                )
            }
        }
    }
}

@Composable
fun MangaRowCard(
    repository: MangaRepository,
    manga: MangaItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(125.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = EInkWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp)
                    .background(EInkSurface),
                contentAlignment = Alignment.Center
            ) {
                if (!manga.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = manga.getCoverUrl(repository.settingsManager.apiBaseUrl) ?: manga.coverUrl,
                        contentDescription = manga.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    )
                } else {
                    Text(text = "[ Không có bìa ]", style = MaterialTheme.typography.labelSmall)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
            ) {
                Text(
                    text = manga.displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = manga.authorName ?: " ",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp, color = EInkDarkGray),
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MangaGridCard(
    repository: MangaRepository,
    manga: MangaItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                if (!manga.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = manga.getCoverUrl(repository.settingsManager.apiBaseUrl) ?: manga.coverUrl,
                        contentDescription = manga.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    )
                } else {
                    Text(
                        text = "[ Không có bìa ]",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = manga.displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (!manga.authorName.isNullOrBlank()) "Tác giả: ${manga.authorName}" else " ",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp, color = EInkDarkGray),
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}