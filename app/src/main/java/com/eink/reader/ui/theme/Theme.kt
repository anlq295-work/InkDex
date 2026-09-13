package com.eink.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class EInkColors(
    val white: Color,
    val black: Color,
    val darkGray: Color,
    val border: Color,
    val surface: Color,
    val primary: Color,
    val isDark: Boolean
)

val LightEInkColors = EInkColors(
    white = Color(0xFFFFFFFF),
    black = Color(0xFF000000),
    darkGray = Color(0xFF1E1E1E),
    border = Color(0xFF333333),
    surface = Color(0xFFF9F9F9),
    primary = Color(0xFF111111),
    isDark = false
)

val DarkEInkColors = EInkColors(
    white = Color(0xFF000000),       // Nền sáng -> Nền đen sâu E-Ink
    black = Color(0xFFFFFFFF),       // Chữ/icon đen -> Trắng sắc nét
    darkGray = Color(0xFFCCCCCC),    // Chữ phụ xám đậm -> Chữ phụ sáng
    border = Color(0xFFFFFFFF),      // Viền đen -> Viền trắng sắc nét
    surface = Color(0xFF161616),     // Thẻ phụ xám nhạt -> Thẻ nền tối
    primary = Color(0xFFFFFFFF),     // Nút bấm & điểm nhấn -> Trắng
    isDark = true
)

val LocalEInkColors = staticCompositionLocalOf { LightEInkColors }

val EInkWhite: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalEInkColors.current.white

val EInkBlack: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalEInkColors.current.black

val EInkDarkGray: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalEInkColors.current.darkGray

val EInkBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalEInkColors.current.border

val EInkSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalEInkColors.current.surface

val EInkPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalEInkColors.current.primary

private val EInkLightColorScheme = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF9F9F9),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF1E1E1E),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF9F9F9),
    onSurfaceVariant = Color(0xFF1E1E1E),
    outline = Color(0xFF333333)
)

private val EInkDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1E1E1E),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFCCCCCC),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFFFFFFFF)
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
    darkTheme: Boolean = false,
    disableOverscroll: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkEInkColors else LightEInkColors
    val colorScheme = if (darkTheme) EInkDarkColorScheme else EInkLightColorScheme
    val overscrollConfig = if (disableOverscroll) null else androidx.compose.foundation.OverscrollConfiguration()

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalEInkColors provides colors,
        androidx.compose.foundation.LocalOverscrollConfiguration provides overscrollConfig
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EInkTypography,
            content = content
        )
    }
}