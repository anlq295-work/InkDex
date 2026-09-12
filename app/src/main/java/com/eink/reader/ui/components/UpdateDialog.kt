package com.eink.reader.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eink.reader.ui.theme.*
import com.eink.reader.util.AppReleaseInfo
import com.eink.reader.util.AppUpdateHelper

@Composable
fun UpdateDialog(
    releaseInfo: AppReleaseInfo,
    currentVersion: String = "1.1",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "🎉 CÓ BẢN CẬP NHẬT MỚI!",
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
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetUrl = releaseInfo.apkDownloadUrl ?: releaseInfo.htmlUrl
                    AppUpdateHelper.openUrl(context, targetUrl)
                    onDismiss()
                },
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EInkBlack, contentColor = EInkWhite)
            ) {
                Text("📥 TẢI VỀ CẬP NHẬT (APK)", color = EInkWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = {
                        AppUpdateHelper.openUrl(context, releaseInfo.htmlUrl)
                    },
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EInkBorder)
                ) {
                    Text("GitHub", color = EInkBlack, fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EInkBlack)
                ) {
                    Text("Để sau", color = EInkBlack, fontSize = 11.sp)
                }
            }
        },
        containerColor = EInkWhite,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.border(2.dp, EInkBlack, RoundedCornerShape(4.dp))
    )
}
