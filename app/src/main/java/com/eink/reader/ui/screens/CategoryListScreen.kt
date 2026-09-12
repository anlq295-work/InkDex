package com.eink.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import com.eink.reader.data.model.MangaItem
import com.eink.reader.data.repository.MangaRepository
import com.eink.reader.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    categoryKey: String,
    categoryTitle: String,
    repository: MangaRepository,
    onBackClick: () -> Unit,
    onMangaClick: (String) -> Unit,
    initialFilter: String = "all"
) {
    var mangaList by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
    var statusesMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedFilterStatus by remember(initialFilter) { mutableStateOf(initialFilter) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var isScreenRefreshing by remember { mutableStateOf(false) }
    val localView = androidx.compose.ui.platform.LocalView.current
    val localContext = androidx.compose.ui.platform.LocalContext.current

    fun loadData() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            if (categoryKey == "follows") {
                launch {
                    repository.getAllReadingStatuses().onSuccess {
                        statusesMap = it
                    }
                }
            }
            val result = when (categoryKey) {
                "popular_new" -> repository.getPopularNewTitles(limit = 40)
                "latest_uploads" -> repository.getLatestUploads(limit = 40)
                "recently_added" -> repository.getRecentlyAdded(limit = 40)
                "follows" -> repository.getUserFollowedManga(limit = 60)
                "random" -> {
                    // Lấy nhiều lần random
                    val list = mutableListOf<MangaItem>()
                    for (i in 1..10) {
                        repository.getRandomManga().onSuccess { list.add(it) }
                    }
                    Result.success(list)
                }
                else -> repository.getPopularManga(limit = 40)
            }

            result.onSuccess {
                mangaList = it
                isLoading = false
            }.onFailure {
                errorMessage = it.localizedMessage ?: "Lỗi tải dữ liệu"
                isLoading = false
            }
        }
    }

    LaunchedEffect(categoryKey) {
        loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = categoryTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = EInkBlack)
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
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "[ ĐANG TẢI $categoryTitle... ]",
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
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { loadData() },
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Text("Thử lại", color = EInkBlack)
                        }
                    }
                }
                else -> {
                    val displayedList = remember(mangaList, selectedFilterStatus, statusesMap) {
                        if (categoryKey != "follows" || selectedFilterStatus == "all") {
                            mangaList
                        } else {
                            mangaList.filter { statusesMap[it.id] == selectedFilterStatus }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (categoryKey == "follows" && mangaList.isNotEmpty()) {
                            val filterOptions = listOf(
                                "all" to "Tất cả (${mangaList.size})",
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
                                filterOptions.forEach { (code, label) ->
                                    val isSelected = selectedFilterStatus == code
                                    OutlinedButton(
                                        onClick = { selectedFilterStatus = code },
                                        shape = RoundedCornerShape(2.dp),
                                        border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, EInkBlack),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) EInkBlack else EInkWhite,
                                            contentColor = if (isSelected) EInkWhite else EInkBlack
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
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
                        }

                        if (displayedList.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (categoryKey == "follows") "Chưa có truyện nào trong mục này." else "Không có truyện nào.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EInkDarkGray
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(minSize = 135.dp),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(displayedList, key = { it.id }) { manga ->
                                    MangaGridCard(
                                        repository = repository,
                                        manga = manga,
                                        onClick = { onMangaClick(manga.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (repository.settingsManager.eInkPageButtonsEnabled && mangaList.isNotEmpty()) {
                com.eink.reader.ui.components.EInkScrollButtonsForLazyGrid(
                    gridState = gridState,
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
}
