package com.eink.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val EInkWhite = Color(0xFFFFFFFF)
val EInkBlack = Color(0xFF000000)
val EInkDarkGray = Color(0xFF1E1E1E)
val EInkBorder = Color(0xFF333333)
val EInkSurface = Color(0xFFF9F9F9)
val EInkPrimary = Color(0xFF111111)

private val EInkColorScheme = lightColorScheme(
    primary = EInkPrimary,
    onPrimary = EInkWhite,
    primaryContainer = EInkSurface,
    onPrimaryContainer = EInkBlack,
    secondary = EInkDarkGray,
    onSecondary = EInkWhite,
    background = EInkWhite,
    onBackground = EInkBlack,
    surface = EInkWhite,
    onSurface = EInkBlack,
    surfaceVariant = EInkSurface,
    onSurfaceVariant = EInkDarkGray,
    outline = EInkBorder
)

// Không gán cứng color trong Typography để LocalContentColor tự động áp dụng chính xác cho nút đảo màu
val EInkTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    )
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EInkReaderTheme(
    disableOverscroll: Boolean = true,
    content: @Composable () -> Unit
) {
    if (disableOverscroll) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalOverscrollConfiguration provides null
        ) {
            MaterialTheme(
                colorScheme = EInkColorScheme,
                typography = EInkTypography,
                content = content
            )
        }
    } else {
        MaterialTheme(
            colorScheme = EInkColorScheme,
            typography = EInkTypography,
            content = content
        )
    }
}