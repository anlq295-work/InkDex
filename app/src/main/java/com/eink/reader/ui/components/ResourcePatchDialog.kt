package com.eink.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eink.reader.data.model.PatchDownloadProgress
import com.eink.reader.data.model.ResourcePatch
import com.eink.reader.data.repository.ResourceManager
import com.eink.reader.ui.theme.EInkBlack
import com.eink.reader.ui.theme.EInkBorder
import com.eink.reader.ui.theme.EInkDarkGray
import com.eink.reader.ui.theme.EInkSurface
import com.eink.reader.ui.theme.EInkWhite
import com.eink.reader.util.LocalAppStrings
import kotlinx.coroutines.launch

@Composable
fun ResourcePatchDialog(
    resourceManager: ResourceManager,
    patch: ResourcePatch,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val coroutineScope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(PatchDownloadProgress(percent = 0)) }
    var isDownloading by remember { mutableStateOf(false) }

    fun startDownload() {
        if (isDownloading) return
        isDownloading = true
        coroutineScope.launch {
            resourceManager.downloadAndApplyPatch { p ->
                progress = p
            }
            isDownloading = false
        }
    }

    // Tự động tải gói tài nguyên ngay khi xuất hiện (phong cách Game)
    LaunchedEffect(Unit) {
        startDownload()
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (progress.isDone) Icons.Default.Done else Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = EInkBlack,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "GÓI TÀI NGUYÊN MỚI (PATCH v)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = EInkBlack
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (patch.changelog.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EInkSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, EInkBorder, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Nội dung cập nhật:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = EInkBlack
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = patch.changelog,
                            fontSize = 12.sp,
                            color = EInkDarkGray,
                            lineHeight = 16.sp
                        )
                    }
                }

                if (progress.error != null) {
                    Text(
                        text = "Lỗi: ",
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                } else if (progress.isDone) {
                    Text(
                        text = "✓ Đã tải và áp dụng tài nguyên mới thành công! Dữ liệu đã được nạp ngay trong app mà không cần cài đặt lại APK.",
                        fontWeight = FontWeight.SemiBold,
                        color = EInkBlack,
                        fontSize = 12.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Đang tải gói dữ liệu...",
                                fontSize = 11.sp,
                                color = EInkDarkGray
                            )
                            Text(
                                text = "%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = EInkBlack
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress.percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .border(1.dp, EInkBlack, RoundedCornerShape(2.dp)),
                            color = EInkBlack,
                            trackColor = EInkWhite
                        )
                        if (progress.totalBytes > 0) {
                            Text(
                                text = " / ",
                                fontSize = 10.sp,
                                color = EInkDarkGray
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (progress.isDone) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite)
                ) {
                    Text(strings.done, fontWeight = FontWeight.Bold)
                }
            } else if (progress.error != null) {
                Button(
                    onClick = { startDownload() },
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite)
                ) {
                    Text(strings.retry, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!progress.isDone && !isDownloading) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack)
                ) {
                    Text(strings.close, color = EInkBlack)
                }
            }
        },
        containerColor = EInkWhite,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.border(2.dp, EInkBlack, RoundedCornerShape(4.dp))
    )
}

private fun formatSize(bytes: Long): String {
    return if (bytes >= 1024 * 1024) {
        String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
    } else {
        String.format("%.1f KB", bytes.toDouble() / 1024)
    }
}
