package com.eink.reader.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eink.reader.data.api.OAuthHelper
import com.eink.reader.data.repository.MangaRepository
import com.eink.reader.ui.theme.*
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginWebScreen(
    repository: MangaRepository,
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val verifier = rememberSaveable { OAuthHelper.generateCodeVerifier() }
    var useProxyForAuth by rememberSaveable { mutableStateOf(repository.settingsManager.useProxyForAuth) }
    val proxyUrl = repository.settingsManager.apiBaseUrl
    val authUrl = remember(verifier, useProxyForAuth, proxyUrl) {
        val proxy = if (useProxyForAuth) proxyUrl else null
        OAuthHelper.buildAuthUrl(verifier, proxy)
    }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }
    var isExchangingToken by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authUrl) {
        webViewInstance?.loadUrl(authUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ĐĂNG NHẬP MANGADEX",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
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
                    IconButton(onClick = { 
                        errorMessage = null
                        webViewInstance?.reload() 
                    }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Tải lại trang",
                            tint = EInkBlack
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EInkWhite,
                    titleContentColor = EInkBlack
                ),
                modifier = Modifier.border(1.dp, EInkBorder)
            )
        },
        containerColor = EInkWhite
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Thanh chuyển đổi chế độ kết nối (Proxy Worker hoặc Trực tiếp/VPN)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EInkSurface)
                    .border(androidx.compose.foundation.BorderStroke(1.dp, EInkBorder))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (useProxyForAuth) "Đang qua: Cloudflare Worker" else "Đang qua: Trực tiếp (Cần VPN)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EInkBlack
                )
                OutlinedButton(
                    onClick = {
                        val next = !useProxyForAuth
                        useProxyForAuth = next
                        repository.settingsManager.useProxyForAuth = next
                        errorMessage = null
                        isLoadingPage = true
                    },
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = EInkWhite),
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (useProxyForAuth) "Đổi sang Trực tiếp" else "Đổi sang Proxy",
                        fontSize = 11.sp,
                        color = EInkBlack
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            webViewInstance = this
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                            }

                            CookieManager.getInstance().setAcceptCookie(true)

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoadingPage = true

                                    // Tự động chuyển hướng action của form đăng nhập sang Proxy để tránh bị nhà mạng chặn connection reset
                                    if (useProxyForAuth && !proxyUrl.isNullOrBlank()) {
                                        val cleanProxy = proxyUrl.trimEnd('/')
                                        val jsCode = """
                                            (function() {
                                                function patchMangaDexUrls() {
                                                    try {
                                                        var forms = document.querySelectorAll('form');
                                                        for (var i = 0; i < forms.length; i++) {
                                                            var act = forms[i].getAttribute('action') || forms[i].action || '';
                                                            if (act.indexOf('auth.mangadex.org') !== -1) {
                                                                forms[i].action = act.replace('https://auth.mangadex.org', '$cleanProxy');
                                                            }
                                                        }
                                                    } catch(e) {}
                                                }
                                                patchMangaDexUrls();
                                                if (!window._md_patched) {
                                                    window._md_patched = true;
                                                    document.addEventListener('submit', function(e) {
                                                        try {
                                                            var f = e.target;
                                                            if (f && f.action && f.action.indexOf('auth.mangadex.org') !== -1) {
                                                                f.action = f.action.replace('https://auth.mangadex.org', '$cleanProxy');
                                                            }
                                                        } catch(err) {}
                                                    }, true);
                                                }
                                            })();
                                        """.trimIndent()
                                        view?.evaluateJavascript(jsCode, null)
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoadingPage = false

                                    // Patch lại form sau khi trang tải xong
                                    if (useProxyForAuth && !proxyUrl.isNullOrBlank()) {
                                        val cleanProxy = proxyUrl.trimEnd('/')
                                        val jsCode = """
                                            (function() {
                                                function patchMangaDexUrls() {
                                                    try {
                                                        var forms = document.querySelectorAll('form');
                                                        for (var i = 0; i < forms.length; i++) {
                                                            var act = forms[i].getAttribute('action') || forms[i].action || '';
                                                            if (act.indexOf('auth.mangadex.org') !== -1) {
                                                                forms[i].action = act.replace('https://auth.mangadex.org', '$cleanProxy');
                                                            }
                                                        }
                                                        var links = document.querySelectorAll('a');
                                                        for (var j = 0; j < links.length; j++) {
                                                            var href = links[j].getAttribute('href') || links[j].href || '';
                                                            if (href.indexOf('https://auth.mangadex.org') !== -1) {
                                                                links[j].href = href.replace('https://auth.mangadex.org', '$cleanProxy');
                                                            }
                                                        }
                                                    } catch(e) {}
                                                }
                                                patchMangaDexUrls();
                                                setInterval(patchMangaDexUrls, 500);
                                            })();
                                        """.trimIndent()
                                        view?.evaluateJavascript(jsCode, null)
                                    }

                                    // Kiểm tra xem trang có báo lỗi 404 No route found của Worker cũ không
                                    view?.evaluateJavascript("(function() { return document.body ? document.body.innerText : ''; })();") { bodyText ->
                                        if (bodyText != null && bodyText.contains("No route found for") && bodyText.contains("realms")) {
                                            errorMessage = "Cloudflare Worker chưa được cập nhật mã định tuyến auth.mangadex.org (Lỗi 404: No route found).\n\n" +
                                                    "👉 Vui lòng dán đoạn mã Worker mới nhất vào Cloudflare, hoặc bấm 'Đổi sang Trực tiếp' nếu dùng VPN."
                                        }
                                    }
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val targetUrl = request?.url?.toString() ?: return false

                                    if (targetUrl.startsWith(OAuthHelper.REDIRECT_URI) || targetUrl.startsWith("neko://mangadex-auth")) {
                                        val uri = Uri.parse(targetUrl)
                                        val code = uri.getQueryParameter("code")
                                        val error = uri.getQueryParameter("error")

                                        if (code != null) {
                                            isExchangingToken = true
                                            errorMessage = null

                                            coroutineScope.launch {
                                                val exchangeResult = repository.exchangeOAuthCode(code, verifier)
                                                exchangeResult.onSuccess { tokenResp ->
                                                    repository.settingsManager.sessionToken = tokenResp.accessToken
                                                    repository.settingsManager.refreshToken = tokenResp.refreshToken

                                                    // Lấy username người dùng
                                                    val meResult = repository.verifyUserMe(tokenResp.accessToken)
                                                    meResult.onSuccess { uname ->
                                                        repository.settingsManager.username = uname
                                                    }

                                                    isExchangingToken = false
                                                    onLoginSuccess()
                                                }.onFailure { err ->
                                                    isExchangingToken = false
                                                    errorMessage = "Lỗi nhận token: ${err.localizedMessage}"
                                                }
                                            }
                                            return true
                                        } else if (error != null) {
                                            errorMessage = "MangaDex từ chối đăng nhập: $error"
                                            return true
                                        }
                                    }
                                    return false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    val reqUrl = request?.url?.toString() ?: ""
                                    if (reqUrl.startsWith("neko://") || request?.url?.scheme == "neko") {
                                        return
                                    }
                                    if (request?.isForMainFrame == true) {
                                        val errDesc = error?.description?.toString() ?: ""
                                        val errCode = error?.errorCode ?: 0
                                        val isReset = errDesc.contains("RESET", ignoreCase = true) ||
                                                errDesc.contains("FAILED", ignoreCase = true) ||
                                                errDesc.contains("CONNECTION", ignoreCase = true) ||
                                                errCode == ERROR_CONNECT ||
                                                errCode == ERROR_FAILED_SSL_HANDSHAKE ||
                                                errCode == ERROR_TIMEOUT

                                        if (isReset) {
                                            errorMessage = "LỖI KẾT NỐI (CONNECTION RESET / TIMEOUT):\n" +
                                                    "Máy chủ đăng nhập auth.mangadex.org bị nhà mạng chặn kết nối trực tiếp khi gửi form đăng nhập.\n\n" +
                                                    "👉 Hãy cập nhật mã Cloudflare Worker mới (có hỗ trợ proxy form đăng nhập) hoặc bật VPN/1.1.1.1 rồi bấm 'Tải lại'."
                                        } else {
                                            errorMessage = "Không thể kết nối đến máy chủ đăng nhập MangaDex ($errDesc)"
                                        }
                                        isLoadingPage = false
                                    }
                                }
                            }

                        loadUrl(authUrl)
                    }
                }
            )

            // Hiệu ứng đang tải trang đăng nhập
            if (isLoadingPage && !isExchangingToken) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                        .background(EInkWhite, RoundedCornerShape(4.dp))
                        .border(1.dp, EInkBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[ ĐANG TẢI GIAO DIỆN ĐĂNG NHẬP MANGADEX... ]",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = EInkBlack
                    )
                }
            }

            // Hiển thị trạng thái đang trao đổi Token
            if (isExchangingToken) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(EInkWhite.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .border(2.dp, EInkBlack, RoundedCornerShape(8.dp))
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "ĐĂNG NHẬP THÀNH CÔNG!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "[ Đang xác thực phiên & lưu tài khoản... ]",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EInkDarkGray
                        )
                    }
                }
            }

            // Hiển thị lỗi nếu có
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(EInkWhite, RoundedCornerShape(4.dp))
                        .border(2.dp, Color.Black, RoundedCornerShape(4.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Lưu ý:",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (useProxyForAuth) {
                                Button(
                                    onClick = {
                                        useProxyForAuth = false
                                        repository.settingsManager.useProxyForAuth = false
                                        errorMessage = null
                                        isLoadingPage = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("Đổi sang Trực tiếp (VPN)", fontSize = 12.sp, color = EInkWhite)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    errorMessage = null
                                    webViewInstance?.loadUrl(authUrl)
                                },
                                border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Tải lại", color = EInkBlack, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onBackClick,
                                border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Dùng API Token", color = EInkBlack, fontSize = 12.sp)
                            }
                        }
                }
            }
        }
    }
}
}
}
