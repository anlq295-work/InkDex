package com.eink.reader

import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eink.reader.data.repository.MangaRepository
import com.eink.reader.data.repository.SettingsManager
import com.eink.reader.ui.components.UpdateDialog
import com.eink.reader.ui.screens.*
import com.eink.reader.ui.theme.*
import com.eink.reader.util.AppReleaseInfo
import com.eink.reader.util.AppUpdateHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private val repository by lazy {
        MangaRepository(SettingsManager(applicationContext))
    }

    private val volumeKeyFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private var isReaderActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        setContent {
            var disableOverscroll by remember { mutableStateOf(repository.settingsManager.eInkDisableOverscroll) }
            var appThemeMode by remember { mutableStateOf(repository.settingsManager.appThemeMode) }
            var isPornographic by remember { mutableStateOf(repository.settingsManager.contentRatingPornographic) }
            var appLanguage by remember { mutableStateOf(repository.settingsManager.appLanguage) }

            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "eink_disable_overscroll" || key == "eink_support_enabled") {
                        disableOverscroll = repository.settingsManager.eInkDisableOverscroll
                    }
                    if (key == "app_theme_mode") {
                        appThemeMode = repository.settingsManager.appThemeMode
                    }
                    if (key == "cr_pornographic") {
                        isPornographic = repository.settingsManager.contentRatingPornographic
                        appThemeMode = repository.settingsManager.appThemeMode
                    }
                    if (key == "app_language") {
                        appLanguage = repository.settingsManager.appLanguage
                    }
                }
                repository.settingsManager.prefsInstance.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    repository.settingsManager.prefsInstance.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            val patchVersion by com.eink.reader.util.I18n.patchVersionFlow.collectAsState()
            val appStrings = remember(appLanguage, patchVersion) { com.eink.reader.util.I18n.getStrings(appLanguage) }

            val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isAppDark = when {
                isPornographic -> true
                appThemeMode == "DARK" -> true
                appThemeMode == "SYSTEM" -> systemInDark
                else -> false
            }

            CompositionLocalProvider(com.eink.reader.util.LocalAppStrings provides appStrings) {
                EInkReaderTheme(darkTheme = isAppDark, disableOverscroll = disableOverscroll) {
                var isAppUnlocked by rememberSaveable { mutableStateOf(false) }
                val isAppLockActive = repository.settingsManager.isAppLockEnabled &&
                        repository.settingsManager.appLockPin.isNotBlank() &&
                        !isAppUnlocked

                if (isAppLockActive) {
                    AppLockScreen(
                        correctPin = repository.settingsManager.appLockPin,
                        onUnlocked = { isAppUnlocked = true }
                    )
                } else {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    val isBottomBarVisible = currentRoute in listOf("home", "library", "settings")
                    var startupUpdateInfo by remember { mutableStateOf<AppReleaseInfo?>(null) }
                    var availablePatchInfo by remember { mutableStateOf<com.eink.reader.data.model.RemoteConfig?>(null) }
                    val remoteConfig by repository.remoteConfigManager.configFlow.collectAsState()
                    var dismissedNoticeMessage by rememberSaveable { mutableStateOf("") }
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    val coroutineScope = rememberCoroutineScope()
                    var lastUpdateCheckTime by rememberSaveable { mutableLongStateOf(0L) }

                    // Tự động kiểm tra bản cập nhật mới mỗi khi khởi động app, sau khi mở khóa màn hình hoặc khi mở lại từ nền
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                val now = System.currentTimeMillis()
                                if (now - lastUpdateCheckTime > 30_000L) {
                                    lastUpdateCheckTime = now
                                    coroutineScope.launch {
                                        delay(1500)
                                        // Tự động kiểm tra gói dữ liệu/tài nguyên mới từ xa (phong cách Game)
                                        repository.remoteConfigManager.checkForNewConfig(force = false).onSuccess { cfg ->
                                            if (cfg != null) {
                                                availablePatchInfo = cfg
                                            }
                                        }
                                        val res = AppUpdateHelper.checkForUpdate(currentVersion = "1.6.1")
                                        res.onSuccess { info ->
                                            if (info.isNewer) {
                                                startupUpdateInfo = info
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    Scaffold(
                        bottomBar = {
                            if (isBottomBarVisible) {
                                NavigationBar(
                                    containerColor = EInkWhite,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(androidx.compose.foundation.BorderStroke(1.dp, EInkBorder))
                                ) {
                                    NavigationBarItem(
                                        selected = currentRoute == "home",
                                        onClick = {
                                            navController.navigate("home") {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                Icons.Default.Explore,
                                                contentDescription = appStrings.navExplore
                                            )
                                        },
                                        label = {
                                            Text(
                                                appStrings.navExplore,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = EInkBlack,
                                            selectedTextColor = EInkBlack,
                                            unselectedIconColor = EInkDarkGray,
                                            unselectedTextColor = EInkDarkGray,
                                            indicatorColor = EInkSurface
                                        )
                                    )

                                    NavigationBarItem(
                                        selected = currentRoute == "library",
                                        onClick = {
                                            navController.navigate("library") {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(Icons.Default.LocalLibrary, contentDescription = appStrings.navLibrary) },
                                        label = { Text(appStrings.navLibrary, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = EInkBlack,
                                            selectedTextColor = EInkBlack,
                                            unselectedIconColor = EInkDarkGray,
                                            unselectedTextColor = EInkDarkGray,
                                            indicatorColor = EInkSurface
                                        )
                                    )

                                    NavigationBarItem(
                                        selected = currentRoute == "settings",
                                        onClick = {
                                            navController.navigate("settings") {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(Icons.Default.Settings, contentDescription = appStrings.navSettings) },
                                        label = { Text(appStrings.navSettings, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = EInkBlack,
                                            selectedTextColor = EInkBlack,
                                            unselectedIconColor = EInkDarkGray,
                                            unselectedTextColor = EInkDarkGray,
                                            indicatorColor = EInkSurface
                                        )
                                    )
                                }
                            }
                        },
                        containerColor = EInkWhite
                    ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (!isReaderActive && remoteConfig.systemNotice.enabled && remoteConfig.systemNotice.message.isNotBlank() && dismissedNoticeMessage != remoteConfig.systemNotice.message) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                colors = CardDefaults.cardColors(containerColor = EInkSurface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = EInkBlack, modifier = Modifier.size(18.dp))
                                        Column {
                                            if (remoteConfig.systemNotice.title.isNotBlank()) {
                                                Text(
                                                    text = remoteConfig.systemNotice.title,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = EInkBlack
                                                )
                                            }
                                            Text(
                                                text = remoteConfig.systemNotice.message,
                                                fontSize = 11.sp,
                                                color = EInkBlack
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { dismissedNoticeMessage = remoteConfig.systemNotice.message },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Đóng thông báo", tint = EInkBlack, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            NavHost(
                            navController = navController,
                            startDestination = "home"
                        ) {
                            composable("home") {
                                isReaderActive = false
                                HomeScreen(
                                    repository = repository,
                                    onMangaClick = { mangaId ->
                                        navController.navigate("detail/$mangaId")
                                    },
                                    onCategoryClick = { key, title, filter ->
                                        val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                                        navController.navigate("category/$key/$encodedTitle?filter=$filter")
                                    },
                                    onSettingsClick = {
                                        navController.navigate("settings")
                                    }
                                )
                            }

                            val navigateToReader = { chapterId: String, title: String, mId: String?, mTitle: String?, cUrl: String?, page: Int ->
                                val encCh = URLEncoder.encode(chapterId, StandardCharsets.UTF_8.toString())
                                val encTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                                val encMId = if (!mId.isNullOrBlank()) URLEncoder.encode(mId, StandardCharsets.UTF_8.toString()) else ""
                                val encMTitle = if (!mTitle.isNullOrBlank()) URLEncoder.encode(mTitle, StandardCharsets.UTF_8.toString()) else ""
                                val encCover = if (!cUrl.isNullOrBlank()) URLEncoder.encode(cUrl, StandardCharsets.UTF_8.toString()) else ""
                                navController.navigate("reader/$encCh/$encTitle?mangaId=$encMId&mangaTitle=$encMTitle&coverUrl=$encCover&page=$page")
                            }

                            composable("library") {
                                isReaderActive = false
                                LibraryScreen(
                                    repository = repository,
                                    onMangaClick = { mangaId ->
                                        navController.navigate("detail/$mangaId")
                                    },
                                    onChapterClick = { filePath, title, mId, mTitle, cUrl, page ->
                                        navigateToReader(filePath, title, mId, mTitle, cUrl, page)
                                    },
                                    onSettingsClick = {
                                        navController.navigate("settings")
                                    }
                                )
                            }


                            composable("settings") {
                                isReaderActive = false
                                SettingsScreen(
                                    repository = repository,
                                    onOpenWebLogin = { navController.navigate("login_web") }
                                )
                            }

                            composable("login_web") {
                                isReaderActive = false
                                LoginWebScreen(
                                    repository = repository,
                                    onBackClick = { navController.popBackStack() },
                                    onLoginSuccess = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = "category/{key}/{title}?filter={filter}",
                                arguments = listOf(
                                    navArgument("key") { type = NavType.StringType },
                                    navArgument("title") { type = NavType.StringType },
                                    navArgument("filter") {
                                        type = NavType.StringType
                                        defaultValue = "all"
                                    }
                                )
                            ) { backStackEntry ->
                                isReaderActive = false
                                val key = backStackEntry.arguments?.getString("key") ?: ""
                                val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
                                val filter = backStackEntry.arguments?.getString("filter") ?: "all"
                                val title = URLDecoder.decode(encodedTitle, StandardCharsets.UTF_8.toString())

                                CategoryListScreen(
                                    categoryKey = key,
                                    categoryTitle = title,
                                    repository = repository,
                                    onBackClick = { navController.popBackStack() },
                                    onMangaClick = { mangaId ->
                                        navController.navigate("detail/$mangaId")
                                    },
                                    initialFilter = filter
                                )
                            }

                            composable(
                                route = "detail/{mangaId}",
                                arguments = listOf(navArgument("mangaId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                isReaderActive = false
                                val mangaId = backStackEntry.arguments?.getString("mangaId") ?: ""
                                DetailScreen(
                                    mangaId = mangaId,
                                    repository = repository,
                                    onBackClick = { navController.popBackStack() },
                                    onChapterClick = { chapterId, title, page, mTitle, cUrl ->
                                        navigateToReader(chapterId, title, mangaId, mTitle, cUrl, page)
                                    }
                                )
                            }

                            composable(
                                route = "reader/{chapterId}/{title}?mangaId={mangaId}&mangaTitle={mangaTitle}&coverUrl={coverUrl}&page={page}",
                                arguments = listOf(
                                    navArgument("chapterId") { type = NavType.StringType },
                                    navArgument("title") { type = NavType.StringType },
                                    navArgument("mangaId") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("mangaTitle") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("coverUrl") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("page") { type = NavType.IntType; defaultValue = 1 }
                                )
                            ) { backStackEntry ->
                                isReaderActive = true
                                val encodedChapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
                                val chapterId = URLDecoder.decode(encodedChapterId, StandardCharsets.UTF_8.toString())
                                val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
                                val title = URLDecoder.decode(encodedTitle, StandardCharsets.UTF_8.toString())
                                val encodedMangaId = backStackEntry.arguments?.getString("mangaId") ?: ""
                                val mangaId = if (encodedMangaId.isNotBlank()) URLDecoder.decode(encodedMangaId, StandardCharsets.UTF_8.toString()) else null
                                val encodedMangaTitle = backStackEntry.arguments?.getString("mangaTitle") ?: ""
                                val mangaTitle = if (encodedMangaTitle.isNotBlank()) URLDecoder.decode(encodedMangaTitle, StandardCharsets.UTF_8.toString()) else null
                                val encodedCover = backStackEntry.arguments?.getString("coverUrl") ?: ""
                                val coverUrl = if (encodedCover.isNotBlank()) URLDecoder.decode(encodedCover, StandardCharsets.UTF_8.toString()) else null
                                val initialPage = backStackEntry.arguments?.getInt("page") ?: 1

                                ReaderScreen(
                                    chapterId = chapterId,
                                    chapterTitle = title,
                                    repository = repository,
                                    onBackClick = {
                                        isReaderActive = false
                                        navController.popBackStack()
                                    },
                                    volumeKeyEventFlow = volumeKeyFlow,
                                    mangaId = mangaId,
                                    mangaTitle = mangaTitle,
                                    coverUrl = coverUrl,
                                    initialPage = initialPage
                                )
                            }
                        }

                        availablePatchInfo?.let { cfg ->
                            com.eink.reader.ui.components.ResourcePatchDialog(
                                remoteConfigManager = repository.remoteConfigManager,
                                config = cfg,
                                onDismiss = { availablePatchInfo = null }
                            )
                        }

                        startupUpdateInfo?.let { info ->
                            UpdateDialog(
                                releaseInfo = info,
                                currentVersion = "1.6.1",
                                onDismiss = { startupUpdateInfo = null }
                            )
                        }
                    }
                }
            }
        }
    }
}
}
}


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isReaderActive) {
            val keyConfig = repository.settingsManager.pageTurnKeys
            when (keyConfig) {
                "VOLUME" -> {
                    when (keyCode) {
                        KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                            volumeKeyFlow.tryEmit(true)
                            return true
                        }
                        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                            volumeKeyFlow.tryEmit(false)
                            return true
                        }
                    }
                }
                "LEFT_RIGHT" -> {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            volumeKeyFlow.tryEmit(true)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_VOLUME_UP -> {
                            volumeKeyFlow.tryEmit(false)
                            return true
                        }
                    }
                }
                "UP_DOWN" -> {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            volumeKeyFlow.tryEmit(true)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_VOLUME_UP -> {
                            volumeKeyFlow.tryEmit(false)
                            return true
                        }
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}