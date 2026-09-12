package com.eink.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eink.reader.ui.theme.*

@Composable
fun AppLockScreen(
    correctPin: String,
    onUnlocked: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun onNumberClick(num: String) {
        if (enteredPin.length < 8) {
            val newPin = enteredPin + num
            enteredPin = newPin
            errorMessage = null

            // Tự động kiểm tra nếu độ dài bằng mã PIN đã đặt
            if (newPin.length == correctPin.length) {
                if (newPin == correctPin) {
                    onUnlocked()
                } else {
                    errorMessage = "Mã PIN không chính xác!"
                    enteredPin = ""
                }
            }
        }
    }

    fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EInkWhite)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .border(2.dp, EInkBlack, CircleShape)
                    .background(EInkSurface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Khóa ứng dụng",
                    tint = EInkBlack,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "INKDEX BẢO MẬT",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = EInkBlack
            )

            Text(
                text = "Vui lòng nhập mã PIN để mở khóa ứng dụng",
                style = MaterialTheme.typography.bodyMedium,
                color = EInkDarkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Hiển thị các chấm PIN
            val maxDots = maxOf(correctPin.length, 4)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until maxDots) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .border(2.dp, EInkBlack, CircleShape)
                            .background(if (isFilled) EInkBlack else EInkWhite)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bàn phím số E-Ink
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val numRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                for (row in numRows) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (digit in row) {
                            KeypadButton(text = digit, onClick = { onNumberClick(digit) })
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút Xóa toàn bộ C
                    OutlinedButton(
                        onClick = { enteredPin = ""; errorMessage = null },
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, EInkBlack),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = EInkWhite),
                        modifier = Modifier.size(68.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("C", fontSize = 20.sp, fontWeight = FontWeight.Black, color = EInkBlack)
                    }

                    // Số 0
                    KeypadButton(text = "0", onClick = { onNumberClick("0") })

                    // Nút Backspace
                    OutlinedButton(
                        onClick = { onBackspace() },
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, EInkBlack),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = EInkWhite),
                        modifier = Modifier.size(68.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Backspace, contentDescription = "Xóa", tint = EInkBlack)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, EInkBlack),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = EInkWhite,
            contentColor = EInkBlack
        ),
        modifier = Modifier.size(68.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = EInkBlack
        )
    }
}
