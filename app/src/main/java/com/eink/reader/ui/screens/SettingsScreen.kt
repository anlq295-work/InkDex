package com.eink.reader.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eink.reader.data.download.DownloadManager
import com.eink.reader.data.model.EInkColorMode
import com.eink.reader.data.repository.MangaRepository
import com.eink.reader.ui.components.UpdateDialog
import com.eink.reader.ui.theme.*
import com.eink.reader.util.AppReleaseInfo
import com.eink.reader.util.AppUpdateHelper
import com.eink.reader.util.EInkHelper
import kotlinx.coroutines.launch

enum class SettingsSubScreen(val title: String) {
    MENU("CÀI ĐẶT HỆ THỐNG"),
    PERSONAL("CÀI ĐẶT CÁ NHÂN"),
    READER("CÀI ĐẶT ĐỌC & LƯU TRỮ"),
    EINK("TỐI ƯU HÓA MÀN HÌNH E-INK"),
    CREDIT("CREDIT & GIỚI THIỆU")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: MangaRepository,
    onOpenWebLogin: () -> Unit = {}
) {
    val settings = repository.settingsManager
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val localView = LocalView.current

    // Điều hướng giữa Menu chính và các màn hình con
    var currentSubScreen by remember { mutableStateOf(SettingsSubScreen.MENU) }

    // Xử lý phím Back quay lại Menu chính
    BackHandler(enabled = currentSubScreen != SettingsSubScreen.MENU) {
        currentSubScreen = SettingsSubScreen.MENU
    }

    // State cho Account
    var isLoggedIn by remember { mutableStateOf(settings.isLoggedIn && !settings.username.isNullOrBlank()) }
    var sessionTokenInput by remember { mutableStateOf(settings.sessionToken ?: "") }
    var currentUsername by remember { mutableStateOf(settings.username) }
    var isCheckingLogin by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf<String?>(null) }
    var autoSyncRead by remember { mutableStateOf(settings.autoSyncReadStatus) }
    var useProxyForAuth by remember { mutableStateOf(settings.useProxyForAuth) }

    // State cho Personal Client Login
    var showPersonalClientSection by remember { mutableStateOf(false) }
    var pcClientId by remember { mutableStateOf("") }
    var pcClientSecret by remember { mutableStateOf("") }
    var pcUsername by remember { mutableStateOf("") }
    var pcPassword by remember { mutableStateOf("") }
    var pcError by remember { mutableStateOf<String?>(null) }
    var isPcLoading by remember { mutableStateOf(false) }

    // State cho Token inputs & Làm mới
    var refreshTokenInput by remember { mutableStateOf("") }
    var pcSecretInput by remember { mutableStateOf("") }
    var isRefreshingToken by remember { mutableStateOf(false) }
    var tokenRefreshMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        currentUsername = settings.username
        sessionTokenInput = settings.sessionToken ?: ""
        refreshTokenInput = settings.refreshToken ?: ""
        pcSecretInput = settings.personalClientSecret ?: ""
    }

    // State cho Content Ratings
    var crSafe by remember { mutableStateOf(settings.contentRatingSafe) }
    var crSuggestive by remember { mutableStateOf(settings.contentRatingSuggestive) }
    var crErotica by remember { mutableStateOf(settings.contentRatingErotica) }
    var crPornographic by remember { mutableStateOf(settings.contentRatingPornographic) }

    // State cho Reader
    var readingDirection by remember { mutableStateOf(settings.readingDirection) }
    var preCacheCount by remember { mutableFloatStateOf(settings.preCachePagesCount.toFloat()) }
    var pageTurnKeys by remember { mutableStateOf(settings.pageTurnKeys) }

    // State cho Storage
    var downloadStoragePath by remember { mutableStateOf(settings.downloadStoragePath) }
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val currentEffectivePath = downloadStoragePath.ifBlank { downloadManager.downloadBaseDir.absolutePath }
    val availableLocations = remember { downloadManager.getAvailableStorageLocations() }

    // State cho Network
    var useDoH by remember { mutableStateOf(settings.useDoH) }
    var dohProvider by remember { mutableStateOf(settings.dohProvider) }
    var customApiUrl by remember { mutableStateOf(settings.apiBaseUrl) }

    // State cho Khóa ứng dụng (App Lock)
    var isAppLockEnabled by remember { mutableStateOf(settings.isAppLockEnabled) }
    var appLockPin by remember { mutableStateOf(settings.appLockPin) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var pinDialogError by remember { mutableStateOf<String?>(null) }
    var showDisablePinDialog by remember { mutableStateOf(false) }
    var disablePinInput by remember { mutableStateOf("") }
    var disablePinError by remember { mutableStateOf<String?>(null) }

    // State cho E-Ink Optimization
    var eInkDisableOverscroll by remember { mutableStateOf(settings.eInkDisableOverscroll) }
    var eInkPageButtonsEnabled by remember { mutableStateOf(settings.eInkPageButtonsEnabled) }
    var eInkReaderPagedScroll by remember { mutableStateOf(settings.eInkReaderPagedScroll) }
    var eInkAutoRefreshInterval by remember { mutableIntStateOf(settings.eInkAutoRefreshInterval) }
    var autoNextChapter by remember { mutableStateOf(settings.autoNextChapter) }
    var defaultReaderColorMode by remember { mutableStateOf(settings.defaultReaderColorMode) }
    var isScreenFlashRefreshing by remember { mutableStateOf(false) }

    // State cho GitHub Update
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<String?>(null) }
    var availableUpdateInfo by remember { mutableStateOf<AppReleaseInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentSubScreen.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    if (currentSubScreen != SettingsSubScreen.MENU) {
                        IconButton(onClick = { currentSubScreen = SettingsSubScreen.MENU }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Quay lại Menu",
                                tint = EInkBlack
                            )
                        }
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
            when (currentSubScreen) {
                // ==========================================
                // MÀN HÌNH MENU CHÍNH (DANH MỤC LỰA CHỌN)
                // ==========================================
                SettingsSubScreen.MENU -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "DANH MỤC CÀI ĐẶT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EInkDarkGray)
                        )

                        // 1. Cài đặt cá nhân
                        SettingsMenuItem(
                            title = "Cài đặt cá nhân",
                            subtitle = "Tài khoản MangaDex • Phân loại nội dung • Khóa PIN • DNS & Proxy Bypass",
                            icon = Icons.Default.AccountCircle,
                            onClick = { currentSubScreen = SettingsSubScreen.PERSONAL }
                        )

                        // 2. Cài đặt đọc
                        SettingsMenuItem(
                            title = "Cài đặt đọc & Lưu trữ",
                            subtitle = "Hướng đọc Manga • Tải trước trang • Phím vật lý • Quản lý thẻ nhớ/bộ nhớ",
                            icon = Icons.Default.Book,
                            onClick = { currentSubScreen = SettingsSubScreen.READER }
                        )

                        // 3. Cài đặt E-Ink
                        SettingsMenuItem(
                            title = "Tối ưu hóa E-Ink",
                            subtitle = "Chống lưu ảnh • Nút nhảy trang • Màu Kaleido 3 (Bigme) • Chuyển chương • Khử bóng ma",
                            icon = Icons.Default.Tune,
                            onClick = { currentSubScreen = SettingsSubScreen.EINK }
                        )

                        // 4. Credit & Giới thiệu
                        SettingsMenuItem(
                            title = "Credit & Giới thiệu",
                            subtitle = "InkDex v1.2 • Bigme B751C S • MangaDex API",
                            icon = Icons.Default.Info,
                            onClick = { currentSubScreen = SettingsSubScreen.CREDIT }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Thẻ tóm tắt phần cứng & Nút làm mới tức thì
                        val isBigme = remember { EInkHelper.isBigmeDevice() }
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = EInkSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Phần cứng thiết bị:", fontSize = 11.sp, color = EInkDarkGray)
                                        Text(
                                            text = if (isBigme) "✓ Bigme B751C / B751C S (Màn hình màu Kaleido 3)" else "Thiết bị E-Ink tiêu chuẩn (Carta B&W)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isBigme) Color(0xFF006600) else EInkBlack
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            EInkHelper.triggerFullEInkRefresh(
                                                scope = coroutineScope,
                                                view = localView,
                                                context = context,
                                                onFlashStateChange = { isScreenFlashRefreshing = it }
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite),
                                        shape = RoundedCornerShape(2.dp),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = EInkWhite, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("LÀM MỚI E-INK", color = EInkWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                isCheckingUpdate = true
                                                updateCheckResult = "Đang kiểm tra..."
                                                val res = AppUpdateHelper.checkForUpdate(currentVersion = "1.2")
                                                res.onSuccess { info ->
                                                    isCheckingUpdate = false
                                                    if (info.isNewer) {
                                                        availableUpdateInfo = info
                                                        updateCheckResult = "🎉 Có bản mới: ${info.tagName}!"
                                                    } else {
                                                        updateCheckResult = "✓ Đang ở bản mới nhất v1.2"
                                                    }
                                                }.onFailure { err ->
                                                    isCheckingUpdate = false
                                                    updateCheckResult = "❌ Lỗi: ${err.localizedMessage}"
                                                }
                                            }
                                        },
                                        enabled = !isCheckingUpdate,
                                        shape = RoundedCornerShape(2.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Text(if (isCheckingUpdate) "Đang check..." else "CẬP NHẬT", color = EInkBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                updateCheckResult?.let {
                                    Text(
                                        text = it,
                                        fontSize = 11.sp,
                                        color = EInkDarkGray,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Footer phiên bản
                        Text(
                            text = "InkDex Manga Reader v1.2 • Phiên bản tối ưu E-Ink & Bigme Kaleido 3",
                            fontSize = 11.sp,
                            color = EInkDarkGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }

                // ==========================================
                // MỤC 1, 4, 5: CÀI ĐẶT CÁ NHÂN
                // ==========================================
                SettingsSubScreen.PERSONAL -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // PHẦN 1: CHỨC NĂNG CÁ NHÂN & TÀI KHOẢN
                        Text(
                            text = "1. CHỨC NĂNG CÁ NHÂN & TÀI KHOẢN MANGADEX",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EInkBlack)
                        )

                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = EInkWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (isLoggedIn && !currentUsername.isNullOrBlank()) {
                                    val isExpired = com.eink.reader.data.api.OAuthHelper.isTokenExpired(settings.sessionToken)
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = EInkBlack)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text("Tài khoản MangaDex", fontSize = 12.sp, color = EInkDarkGray)
                                                    Text(currentUsername ?: "", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    settings.logout()
                                                    currentUsername = null
                                                    sessionTokenInput = ""
                                                    refreshTokenInput = ""
                                                    pcSecretInput = ""
                                                    isLoggedIn = false
                                                },
                                                shape = RoundedCornerShape(2.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Đăng xuất", color = EInkBlack, fontSize = 11.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        val expTimestamp = com.eink.reader.data.api.OAuthHelper.getTokenExpirationTimestamp(settings.sessionToken)
                                        val tokenStatusText = if (isExpired) {
                                            "⚠️ Phiên đăng nhập đã hết hạn (Mã 401). Vui lòng bấm 'Làm mới Token' hoặc 'Đăng nhập lại'."
                                        } else if (expTimestamp != null) {
                                            val remainMin = ((expTimestamp - System.currentTimeMillis() / 1000L) / 60).coerceAtLeast(0)
                                            "✓ Phiên đăng nhập: Hợp lệ (còn ~${remainMin} phút, tự động làm mới nếu có refresh token)"
                                        } else {
                                            "✓ Phiên đăng nhập: Đang hoạt động"
                                        }

                                        Text(
                                            text = tokenStatusText,
                                            fontSize = 11.sp,
                                            color = if (isExpired) Color.Red else EInkDarkGray
                                        )

                                        tokenRefreshMessage?.let {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(it, fontSize = 11.sp, color = EInkDarkGray)
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        isRefreshingToken = true
                                                        tokenRefreshMessage = "Đang thử làm mới token..."
                                                        val res = repository.refreshAuthToken()
                                                        res.onSuccess {
                                                            tokenRefreshMessage = "✓ Làm mới token thành công!"
                                                            sessionTokenInput = settings.sessionToken ?: ""
                                                            isRefreshingToken = false
                                                        }.onFailure { err ->
                                                            tokenRefreshMessage = "❌ Thất bại: ${err.localizedMessage}. Vui lòng đăng nhập lại."
                                                            isRefreshingToken = false
                                                        }
                                                    }
                                                },
                                                enabled = !isRefreshingToken,
                                                shape = RoundedCornerShape(2.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite),
                                                modifier = Modifier.weight(1f).height(34.dp)
                                            ) {
                                                Text(if (isRefreshingToken) "Đang làm mới..." else "LÀM MỚI TOKEN", fontSize = 11.sp, color = EInkWhite)
                                            }

                                            OutlinedButton(
                                                onClick = { isLoggedIn = false },
                                                shape = RoundedCornerShape(2.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                                                modifier = Modifier.weight(1f).height(34.dp)
                                            ) {
                                                Text("ĐĂNG NHẬP LẠI", fontSize = 11.sp, color = EInkBlack)
                                            }
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = onOpenWebLogin,
                                        shape = RoundedCornerShape(4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite),
                                        modifier = Modifier.fillMaxWidth().height(44.dp)
                                    ) {
                                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = EInkWhite)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ĐĂNG NHẬP QUA WEB (TK & MK)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EInkWhite
                                        )
                                    }

                                    Text(
                                        "Mở trang đăng nhập chính thức MangaDex (hỗ trợ Captcha Turnstile, bảo mật 2FA, tài khoản & mật khẩu).",
                                        fontSize = 11.sp,
                                        color = EInkDarkGray
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Dùng Proxy Worker khi đăng nhập Web", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Tắt nếu bạn dùng VPN hoặc mạng không bị chặn.", fontSize = 11.sp, color = EInkDarkGray)
                                        }
                                        Switch(
                                            checked = useProxyForAuth,
                                            onCheckedChange = {
                                                useProxyForAuth = it
                                                settings.useProxyForAuth = it
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = EInkWhite, checkedTrackColor = EInkBlack)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    HorizontalDivider(color = EInkBorder)
                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Personal API Client
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showPersonalClientSection = !showPersonalClientSection }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Hoặc đăng nhập bằng Personal API Client:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EInkBlack)
                                            Text("Khuyên dùng: Đăng nhập trực tiếp bằng TK & MK không cần WebView / Captcha", fontSize = 11.sp, color = EInkDarkGray)
                                        }
                                        Text(if (showPersonalClientSection) "▲ Thu gọn" else "▼ Mở rộng", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EInkBlack)
                                    }

                                    if (showPersonalClientSection) {
                                        Card(
                                            shape = RoundedCornerShape(2.dp),
                                            colors = CardDefaults.cardColors(containerColor = EInkSurface),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("Lấy Client ID & Secret: Vào mangadex.org trên trình duyệt máy tính -> Avatar -> User Settings -> API Clients -> Add Client.", fontSize = 11.sp, color = EInkDarkGray)

                                                OutlinedTextField(
                                                    value = pcUsername,
                                                    onValueChange = { pcUsername = it },
                                                    label = { Text("Tên tài khoản hoặc Email", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )

                                                OutlinedTextField(
                                                    value = pcPassword,
                                                    onValueChange = { pcPassword = it },
                                                    label = { Text("Mật khẩu MangaDex", fontSize = 11.sp) },
                                                    visualTransformation = PasswordVisualTransformation(),
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )

                                                OutlinedTextField(
                                                    value = pcClientId,
                                                    onValueChange = { pcClientId = it },
                                                    label = { Text("Personal Client ID", fontSize = 11.sp) },
                                                    placeholder = { Text("personal-client-...", fontSize = 11.sp, color = Color.Gray) },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )

                                                OutlinedTextField(
                                                    value = pcClientSecret,
                                                    onValueChange = { pcClientSecret = it },
                                                    label = { Text("Personal Client Secret", fontSize = 11.sp) },
                                                    visualTransformation = PasswordVisualTransformation(),
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )

                                                pcError?.let { Text(it, fontSize = 11.sp, color = Color.Red) }

                                                Button(
                                                    onClick = {
                                                        if (pcUsername.isNotBlank() && pcPassword.isNotBlank() && pcClientId.isNotBlank() && pcClientSecret.isNotBlank()) {
                                                            isPcLoading = true
                                                            pcError = null
                                                            coroutineScope.launch {
                                                                val res = repository.loginWithPersonalClient(
                                                                    clientId = pcClientId.trim(),
                                                                    clientSecret = pcClientSecret.trim(),
                                                                    username = pcUsername.trim(),
                                                                    pass = pcPassword
                                                                )
                                                                res.onSuccess { tokenResp ->
                                                                    settings.sessionToken = tokenResp.accessToken
                                                                    settings.refreshToken = tokenResp.refreshToken
                                                                    settings.personalClientId = pcClientId.trim()
                                                                    settings.personalClientSecret = pcClientSecret.trim()
                                                                    val meRes = repository.verifyUserMe(tokenResp.accessToken)
                                                                    meRes.onSuccess { uname ->
                                                                        settings.username = uname
                                                                        currentUsername = uname
                                                                    }.onFailure {
                                                                        settings.username = pcUsername
                                                                        currentUsername = pcUsername
                                                                    }
                                                                    isLoggedIn = true
                                                                    isPcLoading = false
                                                                    showPersonalClientSection = false
                                                                }.onFailure { err ->
                                                                    isPcLoading = false
                                                                    pcError = err.localizedMessage ?: "Lỗi xác thực"
                                                                }
                                                            }
                                                        } else {
                                                            pcError = "Vui lòng nhập đầy đủ 4 thông tin trên"
                                                        }
                                                    },
                                                    enabled = !isPcLoading,
                                                    shape = RoundedCornerShape(2.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite),
                                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                                ) {
                                                    Text(if (isPcLoading) "Đang xác thực..." else "ĐĂNG NHẬP PERSONAL CLIENT", color = EInkWhite, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    HorizontalDivider(color = EInkBorder)
                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Dán Token trực tiếp
                                    Text("Hoặc dán Token trực tiếp:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("💡 Lưu ý: Session Token MangaDex có hạn 15 phút. Khuyên bạn nên dán kèm Refresh Token để ứng dụng tự động làm mới:", fontSize = 11.sp, color = EInkDarkGray)
                                    OutlinedTextField(
                                        value = sessionTokenInput,
                                        onValueChange = { sessionTokenInput = it },
                                        label = { Text("Session Token (Bắt buộc)", fontSize = 11.sp) },
                                        placeholder = { Text("Bearer ey... hoặc ey...", fontSize = 11.sp, color = Color.Gray) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    OutlinedTextField(
                                        value = refreshTokenInput,
                                        onValueChange = { refreshTokenInput = it },
                                        label = { Text("Refresh Token (Khuyên dùng)", fontSize = 11.sp) },
                                        placeholder = { Text("ey... (refresh_token)", fontSize = 11.sp, color = Color.Gray) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    OutlinedTextField(
                                        value = pcSecretInput,
                                        onValueChange = { pcSecretInput = it },
                                        label = { Text("Client Secret (Nếu dùng Personal Client)", fontSize = 11.sp) },
                                        placeholder = { Text("Dán secret nếu dùng personal-client-...", fontSize = 11.sp, color = Color.Gray) },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(2.dp)
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        loginMessage?.let {
                                            Text(it, fontSize = 11.sp, color = EInkDarkGray, modifier = Modifier.weight(1f))
                                        } ?: Spacer(modifier = Modifier.weight(1f))

                                        Button(
                                            onClick = {
                                                val cleanSession = sessionTokenInput.trim().removePrefix("Bearer ").trim()
                                                val cleanRefresh = refreshTokenInput.trim()
                                                val cleanSecret = pcSecretInput.trim()
                                                if (cleanSession.isNotBlank()) {
                                                    coroutineScope.launch {
                                                        isCheckingLogin = true
                                                        loginMessage = "Đang kiểm tra..."
                                                        repository.verifyUserMe(cleanSession)
                                                            .onSuccess { uname ->
                                                                settings.sessionToken = cleanSession
                                                                if (cleanRefresh.isNotBlank()) settings.refreshToken = cleanRefresh
                                                                if (cleanSecret.isNotBlank()) settings.personalClientSecret = cleanSecret
                                                                val azp = com.eink.reader.data.api.OAuthHelper.extractClientIdFromToken(cleanRefresh.ifBlank { cleanSession })
                                                                if (!azp.isNullOrBlank() && azp.startsWith("personal-client-")) {
                                                                    settings.personalClientId = azp
                                                                }
                                                                settings.username = uname
                                                                currentUsername = uname
                                                                isLoggedIn = true
                                                                loginMessage = "Đăng nhập thành công: $uname"
                                                                isCheckingLogin = false
                                                            }
                                                            .onFailure { err ->
                                                                loginMessage = "Thất bại: ${err.localizedMessage}"
                                                                isCheckingLogin = false
                                                            }
                                                    }
                                                }
                                            },
                                            enabled = !isCheckingLogin && sessionTokenInput.isNotBlank(),
                                            shape = RoundedCornerShape(2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text(if (isCheckingLogin) "Đang kiểm tra..." else "ĐĂNG NHẬP TOKEN", color = EInkWhite, fontSize = 11.sp)
                                        }
                                    }
                                }

                                HorizontalDivider(color = EInkBorder)

                                // Phân loại nội dung (Content Ratings)
                                Text("Phân loại nội dung (Content Ratings):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Chọn các nhãn nội dung sẽ được hiển thị khi tìm kiếm và duyệt danh mục:", fontSize = 11.sp, color = EInkDarkGray)

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RatingChip("Safe", crSafe) {
                                        crSafe = it
                                        settings.contentRatingSafe = it
                                    }
                                    RatingChip("Suggestive", crSuggestive) {
                                        crSuggestive = it
                                        settings.contentRatingSuggestive = it
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RatingChip("Erotica", crErotica) {
                                        crErotica = it
                                        settings.contentRatingErotica = it
                                    }
                                    RatingChip("Pornographic", crPornographic) {
                                        crPornographic = it
                                        settings.contentRatingPornographic = it
                                    }
                                }

                                HorizontalDivider(color = EInkBorder)

                                // Tự động đồng bộ tiến độ đọc
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Tự động đồng bộ đã đọc (Auto Sync)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Tự động đánh dấu chapter đã đọc lên MangaDex khi đọc xong.", fontSize = 11.sp, color = EInkDarkGray)
                                    }
                                    Switch(
                                        checked = autoSyncRead,
                                        onCheckedChange = {
                                            autoSyncRead = it
                                            settings.autoSyncReadStatus = it
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = EInkWhite, checkedTrackColor = EInkBlack)
                                    )
                                }
                            }
                        }

                        // PHẦN 2: KHÓA BẢO VỆ ỨNG DỤNG (APP LOCK)
                        Text(
                            text = "2. KHÓA BẢO VỆ ỨNG DỤNG (APP LOCK)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EInkBlack)
                        )

                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = EInkWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val enable = !isAppLockEnabled
                                            if (enable) {
                                                if (appLockPin.isBlank()) {
                                                    newPinInput = ""
                                                    confirmPinInput = ""
                                                    pinDialogError = null
                                                    showSetPinDialog = true
                                                } else {
                                                    isAppLockEnabled = true
                                                    settings.isAppLockEnabled = true
                                                }
                                            } else {
                                                if (appLockPin.isNotBlank()) {
                                                    disablePinInput = ""
                                                    disablePinError = null
                                                    showDisablePinDialog = true
                                                } else {
                                                    isAppLockEnabled = false
                                                    settings.isAppLockEnabled = false
                                                    appLockPin = ""
                                                    settings.appLockPin = ""
                                                }
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Bật mật khẩu khóa ứng dụng", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            if (appLockPin.isNotBlank()) "Đã đặt mã PIN (Yêu cầu mã PIN khi mở app)" else "Chưa đặt mã PIN bảo vệ",
                                            fontSize = 11.sp,
                                            color = EInkDarkGray
                                        )
                                    }
                                    Switch(
                                        checked = isAppLockEnabled,
                                        onCheckedChange = { enable ->
                                            if (enable) {
                                                if (appLockPin.isBlank()) {
                                                    newPinInput = ""
                                                    confirmPinInput = ""
                                                    pinDialogError = null
                                                    showSetPinDialog = true
                                                } else {
                                                    isAppLockEnabled = true
                                                    settings.isAppLockEnabled = true
                                                }
                                            } else {
                                                if (appLockPin.isNotBlank()) {
                                                    disablePinInput = ""
                                                    disablePinError = null
                                                    showDisablePinDialog = true
                                                } else {
                                                    isAppLockEnabled = false
                                                    settings.isAppLockEnabled = false
                                                    appLockPin = ""
                                                    settings.appLockPin = ""
                                                }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = EInkWhite, checkedTrackColor = EInkBlack)
                                    )
                                }

                                if (appLockPin.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Mã PIN hiện tại: " + "●".repeat(appLockPin.length),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EInkBlack
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                newPinInput = ""
                                                confirmPinInput = ""
                                                pinDialogError = null
                                                showSetPinDialog = true
                                            },
                                            shape = RoundedCornerShape(2.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                                            modifier = Modifier.height(30.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Đổi mã PIN", color = EInkBlack, fontSize = 11.sp)
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            newPinInput = ""
                                            confirmPinInput = ""
                                            pinDialogError = null
                                            showSetPinDialog = true
                                        },
                                        shape = RoundedCornerShape(2.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                                        modifier = Modifier.fillMaxWidth().height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Thiết lập mã PIN ngay", color = EInkBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // PHẦN 3: KẾT NỐI MẠNG & PROXY BYPASS
                        Text(
                            text = "3. KẾT NỐI MẠNG & PROXY BYPASS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EInkBlack)
                        )

                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = EInkWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Bật DNS over HTTPS (DoH)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Vượt chặn DNS nhà mạng không cần VPN.", fontSize = 11.sp, color = EInkDarkGray)
                                    }
                                    Switch(
                                        checked = useDoH,
                                        onCheckedChange = {
                                            useDoH = it
                                            settings.useDoH = it
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = EInkWhite, checkedTrackColor = EInkBlack)
                                    )
                                }

                                if (useDoH) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("cloudflare" to "Cloudflare (1.1.1.1)", "google" to "Google (8.8.8.8)").forEach { (code, label) ->
                                            val isSelected = dohProvider == code
                                            OutlinedButton(
                                                onClick = {
                                                    dohProvider = code
                                                    settings.dohProvider = code
                                                },
                                                shape = RoundedCornerShape(2.dp),
                                                border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, EInkBlack),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = if (isSelected) EInkBlack else EInkWhite,
                                                    contentColor = if (isSelected) EInkWhite else EInkBlack
                                                ),
                                                modifier = Modifier.height(30.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(label, color = if (isSelected) EInkWhite else EInkBlack, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = EInkBorder)

                                Text("Reverse Proxy / Cloudflare Worker URL:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = customApiUrl,
                                    onValueChange = {
                                        customApiUrl = it
                                        settings.apiBaseUrl = it
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // ==========================================
                // MỤC 2, 3: CÀI ĐẶT ĐỌC & LƯU TRỮ
                // ==========================================
                SettingsSubScreen.READER -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // PHẦN 1: CÀI ĐẶT NGƯỜI ĐỌC (READER SETTINGS)
                        Text(
                            text = "1. CÀI ĐẶT TRÌNH ĐỌC TRUYỆN (READER)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EInkBlack)
                        )

                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = EInkWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Chế độ đọc mặc định:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val isRtl = readingDirection == "RTL"
                                    OutlinedButton(
                                        onClick = {
                                            readingDirection = "RTL"
                                            settings.readingDirection = "RTL"
                                        },
                                        shape = RoundedCornerShape(2.dp),
                                        border = androidx.compose.foundation.BorderStroke(if (isRtl) 2.dp else 1.dp, EInkBlack),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isRtl) EInkBlack else EInkWhite,
                                            contentColor = if (isRtl) EInkWhite else EInkBlack
                                        ),
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Phải sang Trái (Manga)", color = if (isRtl) EInkWhite else EInkBlack, fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            readingDirection = "LTR"
                                            settings.readingDirection = "LTR"
                                        },
                                        shape = RoundedCornerShape(2.dp),
                                        border = androidx.compose.foundation.BorderStroke(if (!isRtl) 2.dp else 1.dp, EInkBlack),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (!isRtl) EInkBlack else EInkWhite,
                                            contentColor = if (!isRtl) EInkWhite else EInkBlack
                                        ),
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Trái sang Phải", color = if (!isRtl) EInkWhite else EInkBlack, fontSize = 11.sp)
                                    }
                                }

                                HorizontalDivider(color = EInkBorder)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Số lượng trang tải trước (Pre-cache):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${preCacheCount.toInt()} trang", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Text(
                                    "Số lượng tải trước càng nhiều chất lượng đọc càng cao nhưng tốn nhiều bộ nhớ đệm hơn.",
                                    fontSize = 11.sp,
                                    color = EInkDarkGray
                                )
                                Slider(
                                    value = preCacheCount,
                                    onValueChange = {
                                        preCacheCount = it
                                        settings.preCachePagesCount = it.toInt()
                                    },
                                    valueRange = 1f..10f,
                                    steps = 8,
                                    colors = SliderDefaults.colors(thumbColor = EInkBlack, activeTrackColor = EInkBlack)
                                )

                                HorizontalDivider(color = EInkBorder)

                                Text("Chuyển trang bằng nút bấm vật lý (E-Reader Keys):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        "VOLUME" to "Phím Âm lượng (Lên / Xuống)",
                                        "LEFT_RIGHT" to "Phím Mũi tên (Trái / Phải - Boox/Meebook)",
                                        "UP_DOWN" to "Phím Mũi tên (Lên / Xuống)"
                                    ).forEach { (code, label) ->
                                        val isSelected = pageTurnKeys == code
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(if (isSelected) 2.dp else 1.dp, if (isSelected) EInkBlack else EInkBorder, RoundedCornerShape(2.dp))
                                                .clickable {
                                                    pageTurnKeys = code
                                                    settings.pageTurnKeys = code
                                                }
                                                .background(if (isSelected) EInkSurface else EInkWhite)
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = EInkBlack, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // PHẦN 2: VỊ TRÍ LƯU TRỮ TRUYỆN TẢI VỀ (STORAGE)
                        Text(
                            text = "2. VỊ TRÍ LƯU TRỮ TRUYỆN TẢI VỀ (THẺ NHỚ / BỘ NHỚ)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EInkBlack)
                        )

                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = EInkWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Đường dẫn lưu truyện hiện tại:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = currentEffectivePath,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EInkBlack
                                )

                                OutlinedTextField(
                                    value = downloadStoragePath,
                                    onValueChange = {
                                        downloadStoragePath = it
                                        settings.downloadStoragePath = it
                                    },
                                    placeholder = { Text("Mặc định: ${downloadManager.downloadBaseDir.absolutePath}", fontSize = 11.sp, color = Color.Gray) },
                                    label = { Text("Tùy chỉnh đường dẫn (VD: /storage/xxxx-xxxx/mangadex-download)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(2.dp)
                                )

                                Text("Vị trí bộ nhớ khả dụng (Nhấn để chọn):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    availableLocations.forEach { loc ->
                                        val isCurrent = (downloadStoragePath.isBlank() && !loc.isRemovable) || downloadStoragePath == loc.path
                                        OutlinedButton(
                                            onClick = {
                                                downloadStoragePath = loc.path
                                                settings.downloadStoragePath = loc.path
                                            },
                                            shape = RoundedCornerShape(2.dp),
                                            border = androidx.compose.foundation.BorderStroke(if (isCurrent) 2.dp else 1.dp, EInkBlack),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isCurrent) EInkBlack else EInkWhite,
                                                contentColor = if (isCurrent) EInkWhite else EInkBlack
                                            ),
                                            modifier = Modifier.fillMaxWidth().height(36.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = (if (isCurrent) "✓ " else "") + loc.name,
                                                    fontSize = 11.sp,
                                                    color = if (isCurrent) EInkWhite else EInkBlack,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (loc.freeSpaceBytes > 0) {
                                                    val freeGb = "%.1f GB".format(loc.freeSpaceBytes / (1024f * 1024f * 1024f))
                                                    Text(
                                                        text = "Trống: $freeGb",
                                                        fontSize = 10.sp,
                                                        color = if (isCurrent) EInkWhite.copy(alpha = 0.8f) else EInkDarkGray
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (downloadStoragePath.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                downloadStoragePath = ""
                                                settings.downloadStoragePath = ""
                                            },
                                            shape = RoundedCornerShape(2.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                                            modifier = Modifier.fillMaxWidth().height(32.dp)
                                        ) {
                                            Text("Đặt lại về mặc định (Downloads/mangadex-download)", fontSize = 11.sp, color = EInkBlack)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // ==========================================
                // MỤC 6: TỐI ƯU HÓA MÀN HÌNH E-INK
                // ==========================================
                SettingsSubScreen.EINK -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "1. TỐI ƯU HÓA MÀN HÌNH E-INK (CHỐNG LƯU ẢNH)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EInkBlack)
                        )

                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = EInkWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // 1. Tắt hiệu ứng co giãn trượt
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text("Tắt hiệu ứng co giãn trượt (Disable Overscroll)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Loại bỏ hiệu ứng nảy/co dãn đàn hồi khi cuộn tới mép danh sách, chống làm nhòe và bóng mờ màn hình.", fontSize = 11.sp, color = EInkDarkGray)
                                    }
                                    Switch(
                                        checked = eInkDisableOverscroll,
                                        onCheckedChange = {
                                            eInkDisableOverscroll = it
                                            settings.eInkDisableOverscroll = it
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = EInkWhite, checkedTrackColor = EInkBlack)
                                    )
                                }

                                HorizontalDivider(color = EInkBorder)

                                // 2. Nút nhảy trang nổi ở danh sách
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text("Nút nhảy trang nổi ở danh sách [▲] [▼]", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Hiển thị cụm phím tắt nổi giúp nhảy dứt khoát 1 trang màn hình thay vì vuốt trượt ngón tay.", fontSize = 11.sp, color = EInkDarkGray)
                                    }
                                    Switch(
                                        checked = eInkPageButtonsEnabled,
                                        onCheckedChange = {
                                            eInkPageButtonsEnabled = it
                                            settings.eInkPageButtonsEnabled = it
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = EInkWhite, checkedTrackColor = EInkBlack)
                                    )
                                }

                                HorizontalDivider(color = EInkBorder)

                                // 3. Cuộn nhảy khung hình khi đọc truyện
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text("Cuộn nhảy khung hình trong truyện (Paged Reader Jump)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Khi xem truyện ở chế độ Khớp ngang hoặc tranh dài, chạm hoặc bấm phím âm lượng sẽ nhảy dứt khoát theo khung hình thay vì kéo trượt mượt.", fontSize = 11.sp, color = EInkDarkGray)
                                    }
                                    Switch(
                                        checked = eInkReaderPagedScroll,
                                        onCheckedChange = {
                                            eInkReaderPagedScroll = it
                                            settings.eInkReaderPagedScroll = it
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = EInkWhite, checkedTrackColor = EInkBlack)
                                    )
                                }

                                HorizontalDivider(color = EInkBorder)

                                // 4. Tự động khử bóng ma định kỳ
                                Text("Tự động khử bóng ma (Làm mới màn hình định kỳ):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                val intervalOptions = listOf(
                                    0 to "Tắt",
                                    1 to "Mỗi 1 trang",
                                    5 to "Mỗi 5 trang",
                                    10 to "Mỗi 10 trang",
                                    15 to "Mỗi 15 trang"
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    intervalOptions.forEach { (count, label) ->
                                        val isSelected = eInkAutoRefreshInterval == count
                                        OutlinedButton(
                                            onClick = {
                                                eInkAutoRefreshInterval = count
                                                settings.eInkAutoRefreshInterval = count
                                            },
                                            shape = RoundedCornerShape(2.dp),
                                            border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, EInkBlack),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSelected) EInkBlack else EInkWhite,
                                                contentColor = if (isSelected) EInkWhite else EInkBlack
                                            ),
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(label, color = if (isSelected) EInkWhite else EInkBlack, fontSize = 11.sp)
                                        }
                                    }
                                }

                                HorizontalDivider(color = EInkBorder)

                                // 5. Tự động chuyển chương kế khi đọc hết chương
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text("Tự động chuyển chương kế (Auto Next Chapter)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Khi đọc đến trang cuối cùng của chương, chạm tiếp trang hoặc bấm phím âm lượng sẽ tự động tải chương tiếp theo liền mạch.", fontSize = 11.sp, color = EInkDarkGray)
                                    }
                                    Switch(
                                        checked = autoNextChapter,
                                        onCheckedChange = {
                                            autoNextChapter = it
                                            settings.autoNextChapter = it
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = EInkWhite, checkedTrackColor = EInkBlack)
                                    )
                                }

                                HorizontalDivider(color = EInkBorder)

                                // 6. Chế độ màu E-Ink mặc định (Tối ưu Bigme B751C / Kaleido 3)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Chế độ màu mặc định khi mở truyện:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    val isBigme = remember { EInkHelper.isBigmeDevice() }
                                    Text(
                                        text = if (isBigme) "✓ Đã nhận diện máy đọc sách Bigme B751C (Màn hình màu Kaleido 3)" else "Tối ưu cho màn hình màu Kaleido 3 (Bigme B751C) và màn đen trắng Carta 1200",
                                        fontSize = 11.sp,
                                        color = if (isBigme) Color(0xFF006600) else EInkDarkGray
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        EInkColorMode.entries.forEach { mode ->
                                            val isSelected = defaultReaderColorMode == mode.name
                                            OutlinedButton(
                                                onClick = {
                                                    defaultReaderColorMode = mode.name
                                                    settings.defaultReaderColorMode = mode.name
                                                },
                                                shape = RoundedCornerShape(2.dp),
                                                border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, EInkBlack),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = if (isSelected) EInkBlack else EInkWhite,
                                                    contentColor = if (isSelected) EInkWhite else EInkBlack
                                                ),
                                                modifier = Modifier.height(32.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(mode.shortTitle, color = if (isSelected) EInkWhite else EInkBlack, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = EInkBorder)

                                // 7. Nút Khử bóng ma ngay bây giờ
                                Button(
                                    onClick = {
                                        EInkHelper.triggerFullEInkRefresh(
                                            scope = coroutineScope,
                                            view = localView,
                                            context = context,
                                            onFlashStateChange = { isScreenFlashRefreshing = it }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite),
                                    shape = RoundedCornerShape(2.dp),
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = EInkWhite, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("KHỬ BÓNG MA NGAY BÂY GIỜ (LÀM MỚI E-INK)", color = EInkWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // ==========================================
                // MỤC CREDIT & GIỚI THIỆU
                // ==========================================
                SettingsSubScreen.CREDIT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Card tiêu đề ứng dụng v1.2
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = EInkWhite),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, EInkBlack),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "InkDex Manga Reader",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = EInkBlack
                                )
                                Text(
                                    text = "Phiên bản 1.2 (Build 4) • Tối ưu E-Ink & Cài đặt In-App",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = EInkBlack
                                )
                                Text(
                                    text = "Trình đọc truyện tranh MangaDex chuyên biệt cho Máy đọc sách E-Ink & Màn hình màu Kaleido 3",
                                    fontSize = 11.sp,
                                    color = EInkDarkGray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Phần cứng & Tối ưu hoá
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = EInkWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("TỐI ƯU HÓA PHẦN CỨNG", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EInkBlack)
                                Text(
                                    text = "• Bigme B751C S / B751C: Bộ lọc tăng cường độ bão hòa 1.75x, bù sáng nâng tương phản chống bết màu CFA, phát tín hiệu xRapid EPD refresh phần cứng chống bóng mờ.\n" +
                                            "• Máy đọc sách E-Ink Đen Trắng (Onyx Boox, Meebook, Kindle/Kobo Android): Chế độ đen trắng E-Ink thuần túy, loại bỏ hoàn toàn đổ bóng mờ và hiệu ứng động lướt trang.",
                                    fontSize = 11.sp,
                                    color = EInkDarkGray,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // Nguồn dữ liệu & Bản quyền
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = EInkWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("NGUỒN TRUYỆN & BẢN QUYỀN", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EInkBlack)
                                Text(
                                    text = "• Dữ liệu truyện và ảnh bìa được truy xuất trực tiếp từ nền tảng mở MangaDex API v5 (api.mangadex.org).\n" +
                                            "• Toàn bộ bản quyền hình ảnh, bản dịch và nội dung tác phẩm thuộc về các tác giả, nhà xuất bản và các nhóm dịch (Scanlation Groups).\n" +
                                            "• InkDex là ứng dụng khách (client) độc lập mã nguồn mở, phi thương mại và không lưu trữ truyện trên bất kỳ máy chủ nào.",
                                    fontSize = 11.sp,
                                    color = EInkDarkGray,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // Công nghệ & Thư viện sử dụng
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = EInkWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("CÔNG NGHỆ & THƯ VIỆN MÃ NGUỒN MỞ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EInkBlack)
                                Text(
                                    text = "• Android Jetpack Compose & Material 3\n" +
                                            "• Coil 3 (Xử lý hình ảnh & Bộ lọc màu Kaleido 3 ColorMatrix)\n" +
                                            "• OkHttp 3 & DNS-over-HTTPS (DoH Cloudflare & Google)\n" +
                                            "• GitHub Releases API Auto Update Checker\n" +
                                            "• Kotlinx Coroutines & StateFlow\n" +
                                            "• Bigme EPD Hardware Broadcast SDK\n" +
                                            "• Android Storage Access Framework (SAF)",
                                    fontSize = 11.sp,
                                    color = EInkDarkGray,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // Lời cảm ơn
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = EInkSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("LỜI CẢM ƠN", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EInkBlack)
                                Text(
                                    text = "Chân thành cảm ơn cộng đồng MangaDex đã phát triển nền tảng truyện tranh mở tuyệt vời. Cảm ơn cộng đồng người dùng máy đọc sách E-Ink tại Việt Nam đã nhiệt tình đóng góp ý kiến để hoàn thiện InkDex!",
                                    fontSize = 11.sp,
                                    color = EInkDarkGray,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (isScreenFlashRefreshing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    }

    availableUpdateInfo?.let { info ->
        UpdateDialog(
            releaseInfo = info,
            currentVersion = "1.2",
            onDismiss = { availableUpdateInfo = null }
        )
    }

    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showSetPinDialog = false
                if (appLockPin.isBlank()) {
                    isAppLockEnabled = false
                    settings.isAppLockEnabled = false
                }
            },
            title = {
                Text(
                    text = if (appLockPin.isBlank()) "THIẾT LẬP MÃ PIN KHÓA APP" else "ĐỔI MÃ PIN KHÓA APP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = EInkBlack
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nhập mã PIN gồm 4-6 chữ số để bảo vệ ứng dụng:", fontSize = 12.sp, color = EInkDarkGray)
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPinInput = it },
                        label = { Text("Mã PIN mới (4-6 số)", fontSize = 11.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                        label = { Text("Xác nhận lại mã PIN", fontSize = 11.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    pinDialogError?.let {
                        Text(it, fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinInput.length < 4) {
                            pinDialogError = "Mã PIN phải có ít nhất 4 chữ số"
                        } else if (newPinInput != confirmPinInput) {
                            pinDialogError = "Mã PIN xác nhận không khớp!"
                        } else {
                            appLockPin = newPinInput
                            settings.appLockPin = newPinInput
                            isAppLockEnabled = true
                            settings.isAppLockEnabled = true
                            showSetPinDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text("Lưu mã PIN", color = EInkWhite, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showSetPinDialog = false
                        if (appLockPin.isBlank()) {
                            isAppLockEnabled = false
                            settings.isAppLockEnabled = false
                        }
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text("Hủy", color = EInkBlack, fontSize = 12.sp)
                }
            },
            containerColor = EInkWhite,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.border(2.dp, EInkBlack, RoundedCornerShape(4.dp))
        )
    }

    if (showDisablePinDialog) {
        AlertDialog(
            onDismissRequest = { showDisablePinDialog = false },
            title = {
                Text(
                    text = "XÁC NHẬN TẮT KHÓA ỨNG DỤNG",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = EInkBlack
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Nhập mã PIN hiện tại để tắt bảo vệ và xóa mã PIN:",
                        fontSize = 12.sp,
                        color = EInkDarkGray
                    )
                    OutlinedTextField(
                        value = disablePinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) disablePinInput = it },
                        label = { Text("Mã PIN hiện tại", fontSize = 11.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    disablePinError?.let {
                        Text(it, fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (disablePinInput == appLockPin) {
                            isAppLockEnabled = false
                            settings.isAppLockEnabled = false
                            appLockPin = ""
                            settings.appLockPin = ""
                            showDisablePinDialog = false
                        } else {
                            disablePinError = "Mã PIN không chính xác!"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text("Xác nhận tắt khóa", color = EInkWhite, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDisablePinDialog = false },
                    border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text("Hủy", color = EInkBlack, fontSize = 12.sp)
                }
            },
            containerColor = EInkWhite,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.border(2.dp, EInkBlack, RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun SettingsMenuItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = EInkWhite),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, EInkBlack),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(EInkSurface, RoundedCornerShape(4.dp))
                    .border(1.dp, EInkBorder, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = EInkBlack,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = EInkBlack
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = EInkDarkGray,
                    lineHeight = 15.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = EInkBlack,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun RowScope.RatingChip(label: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
    OutlinedButton(
        onClick = { onToggle(!selected) },
        shape = RoundedCornerShape(2.dp),
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, EInkBlack),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) EInkBlack else EInkWhite,
            contentColor = if (selected) EInkWhite else EInkBlack
        ),
        modifier = Modifier.weight(1f).height(34.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            color = if (selected) EInkWhite else EInkBlack,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
