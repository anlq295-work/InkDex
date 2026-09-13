package com.eink.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eink.reader.ui.theme.*
import com.eink.reader.util.AppReleaseInfo
import com.eink.reader.util.AppUpdateHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private enum class UpdateDownloadState {
    IDLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    ERROR
}

@Composable
fun UpdateDialog(
    releaseInfo: AppReleaseInfo,
    currentVersion: String = "1.5",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var downloadState by remember { mutableStateOf(UpdateDownloadState.IDLE) }
    var bytesDownloaded by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    val startDownload = {
        val apkUrl = releaseInfo.apkDownloadUrl
        if (apkUrl.isNullOrBlank()) {
            AppUpdateHelper.openUrl(context, releaseInfo.htmlUrl)
            onDismiss()
        } else {
            val targetFile = AppUpdateHelper.getUpdateApkFile(context, releaseInfo.tagName)
            downloadedFile = targetFile
            downloadState = UpdateDownloadState.DOWNLOADING
            bytesDownloaded = 0L
            totalBytes = 0L
            errorMessage = null

            downloadJob = coroutineScope.launch {
                val result = AppUpdateHelper.downloadApk(
                    apkUrl = apkUrl,
                    targetFile = targetFile,
                    onProgress = { read, total ->
                        bytesDownloaded = read
                        totalBytes = total
                    }
                )

                result.onSuccess { file ->
                    downloadState = UpdateDownloadState.READY_TO_INSTALL
                    // Tự động mở trình cài đặt ngay khi tải xong
                    val installRes = AppUpdateHelper.installApk(context, file)
                    installRes.onFailure { err ->
                        errorMessage = err.localizedMessage
                    }
                }.onFailure { err ->
                    downloadState = UpdateDownloadState.ERROR
                    errorMessage = err.localizedMessage ?: "Tải bản cập nhật thất bại."
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Tự động tải bản cập nhật luôn mà không cần chờ người dùng bấm
        if (downloadState == UpdateDownloadState.IDLE && !releaseInfo.apkDownloadUrl.isNullOrBlank()) {
            startDownload()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (downloadState == UpdateDownloadState.DOWNLOADING) {
                downloadJob?.cancel()
            }
            onDismiss()
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (downloadState == UpdateDownloadState.DOWNLOADING) "📥 ĐANG TẢI BẢN CẬP NHẬT" else "🎉 CÓ BẢN CẬP NHẬT MỚI!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = EInkBlack
                )
                Text(
                    text = "Bản mới: ${releaseInfo.tagName} • Đang dùng: v$currentVersion",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EInkDarkGray
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (downloadState) {
                    UpdateDownloadState.IDLE -> {
                        if (releaseInfo.releaseName.isNotBlank() && releaseInfo.releaseName != releaseInfo.tagName) {
                            Text(
                                text = releaseInfo.releaseName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = EInkBlack
                            )
                        }

                        Text(
                            text = "Nội dung cập nhật (Changelog):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EInkDarkGray
                        )

                        Text(
                            text = releaseInfo.releaseNotes ?: "Bản cập nhật chứa các cải tiến hiển thị E-Ink và sửa lỗi.",
                            fontSize = 12.sp,
                            color = EInkBlack,
                            lineHeight = 17.sp
                        )
                    }

                    UpdateDownloadState.DOWNLOADING -> {
                        val progressRatio = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                        val progressPercent = (progressRatio * 100).toInt()

                        Text(
                            text = "Đang tải file APK cài đặt từ GitHub...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EInkBlack
                        )

                        // Thanh tiến trình E-Ink đậm nét
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .border(1.5.dp, EInkBlack, RoundedCornerShape(2.dp))
                                .background(EInkWhite)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressRatio)
                                    .background(EInkBlack)
                            )
                        }

                        val mbRead = bytesDownloaded / (1024f * 1024f)
                        val mbTotal = totalBytes / (1024f * 1024f)
                        val progressDetail = if (totalBytes > 0) {
                            String.format(Locale.US, "%.1f MB / %.1f MB (%d%%)", mbRead, mbTotal, progressPercent)
                        } else {
                            String.format(Locale.US, "%.1f MB đã tải...", mbRead)
                        }

                        Text(
                            text = progressDetail,
                            fontSize = 11.sp,
                            color = EInkDarkGray,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    UpdateDownloadState.READY_TO_INSTALL -> {
                        Text(
                            text = "✓ Đã tải xong file cập nhật!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF006600)
                        )

                        Text(
                            text = "Trình cài đặt hệ thống đã được mở. Nếu máy hỏi cấp quyền cài đặt, vui lòng gạt bật 'Cho phép từ nguồn này' rồi bấm nút CÀI ĐẶT bên dưới.",
                            fontSize = 12.sp,
                            color = EInkDarkGray,
                            lineHeight = 16.sp
                        )

                        errorMessage?.let {
                            Text(
                                text = "Lưu ý: $it",
                                fontSize = 11.sp,
                                color = Color.Red
                            )
                        }
                    }

                    UpdateDownloadState.ERROR -> {
                        Text(
                            text = "❌ Tải bản cập nhật thất bại",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.Red
                        )
                        Text(
                            text = errorMessage ?: "Đã có lỗi xảy ra trong quá trình tải APK.",
                            fontSize = 11.sp,
                            color = EInkDarkGray
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                UpdateDownloadState.IDLE -> {
                    Button(
                        onClick = startDownload,
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = EInkWhite, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("TẢI VỀ & CÀI ĐẶT NGAY", color = EInkWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                UpdateDownloadState.DOWNLOADING -> {
                    OutlinedButton(
                        onClick = {
                            downloadJob?.cancel()
                            downloadState = UpdateDownloadState.IDLE
                        },
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack)
                    ) {
                        Text("HỦY TẢI", color = EInkBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                UpdateDownloadState.READY_TO_INSTALL -> {
                    Button(
                        onClick = {
                            downloadedFile?.let { file ->
                                val installRes = AppUpdateHelper.installApk(context, file)
                                installRes.onFailure { err ->
                                    errorMessage = err.localizedMessage
                                }
                            }
                        },
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite)
                    ) {
                        Text("MỞ TRÌNH CÀI ĐẶT", color = EInkWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                UpdateDownloadState.ERROR -> {
                    Button(
                        onClick = startDownload,
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = EInkWhite, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("THỬ LẠI", color = EInkWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (downloadState != UpdateDownloadState.DOWNLOADING) {
                    OutlinedButton(
                        onClick = {
                            AppUpdateHelper.openUrl(context, releaseInfo.apkDownloadUrl ?: releaseInfo.htmlUrl)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = EInkBlack, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Trình duyệt", color = EInkBlack, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack)
                    ) {
                        Text("Để sau", color = EInkBlack, fontSize = 11.sp)
                    }
                }
            }
        },
        containerColor = EInkWhite,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.border(2.dp, EInkBlack, RoundedCornerShape(4.dp))
    )
}

