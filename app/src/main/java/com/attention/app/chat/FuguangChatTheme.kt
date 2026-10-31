package com.attention.app.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 浮光对话页的视觉令牌。
 *
 * 这套令牌刻意不修改全局 AttentionTheme：对话页设计使用了更浅的米白底、
 * 鼠尾草绿主色和更密的字阶。页面稳定后再决定是否合并到全局主题，避免 1b
 * 静态实现影响现有日程、目标和统计页面。
 */
@Immutable
data class FuguangChatPalette(
    val canvas: Color = Color(0xFFF7F4EA),
    val surface: Color = Color(0xFFFFFFFF),
    val surfaceMuted: Color = Color(0xFFFDF9EC),
    val primary: Color = Color(0xFF6E8E77),
    val primarySecondary: Color = Color(0xFF799B84),
    val primarySoft: Color = Color(0xFFEDF1E7),
    val primaryTint: Color = Color(0xFFDCE7D6),
    val primaryDeep: Color = Color(0xFF4F6A53),
    val text: Color = Color(0xFF2F3A33),
    val textSecondary: Color = Color(0xFF5E695D),
    val textTertiary: Color = Color(0xFF8A9384),
    val border: Color = Color(0xFFE9E4D3),
    val divider: Color = Color(0xFFE1DDCE),
    val gold: Color = Color(0xFFA8873F),
    val goldSoft: Color = Color(0xFFF1D996),
    val roseSoft: Color = Color(0xFFF3E3DE),
    val rose: Color = Color(0xFFB77A7D),
    val white: Color = Color(0xFFFFFFFF),
)

/** 设计文档中的 T1-T8 字阶；对话页实际使用 T3-T8。 */
@Immutable
data class FuguangChatTypography(
    val display: TextStyle = fuguangTextStyle(25.sp, 32.sp, FontWeight.Bold),
    val title: TextStyle = fuguangTextStyle(15.sp, 21.sp, FontWeight.Medium),
    val navigationTitle: TextStyle = fuguangTextStyle(13.sp, 18.sp, FontWeight.SemiBold),
    val body: TextStyle = fuguangTextStyle(12.sp, 18.sp, FontWeight.Normal),
    val bodyStrong: TextStyle = fuguangTextStyle(12.sp, 18.sp, FontWeight.SemiBold),
    val label: TextStyle = fuguangTextStyle(11.sp, 16.sp, FontWeight.Medium),
    val caption: TextStyle = fuguangTextStyle(10.sp, 15.sp, FontWeight.Medium),
    val micro: TextStyle = fuguangTextStyle(9.sp, 13.sp, FontWeight.Normal),
)

@Immutable
data class FuguangChatDimensions(
    val screenHorizontal: Dp = 20.dp,
    val screenTop: Dp = 18.dp,
    val sectionGap: Dp = 20.dp,
    val itemGap: Dp = 10.dp,
    val cardPadding: Dp = 16.dp,
    val cardRadius: Dp = 20.dp,
    val smallRadius: Dp = 12.dp,
    val bubbleRadius: Dp = 20.dp,
    val touchTarget: Dp = 48.dp,
)

private fun fuguangTextStyle(
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
): TextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = fontSize,
    lineHeight = lineHeight,
    fontWeight = weight,
    letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

val LocalFuguangChatPalette = staticCompositionLocalOf { FuguangChatPalette() }
val LocalFuguangChatTypography = staticCompositionLocalOf { FuguangChatTypography() }
val LocalFuguangChatDimensions = staticCompositionLocalOf { FuguangChatDimensions() }

@Composable
fun fuguangPalette(): FuguangChatPalette = LocalFuguangChatPalette.current

@Composable
fun fuguangTypography(): FuguangChatTypography = LocalFuguangChatTypography.current

@Composable
fun fuguangDimensions(): FuguangChatDimensions = LocalFuguangChatDimensions.current

/** 只提供对话页令牌，不覆盖应用已有 MaterialTheme。 */
@Composable
fun FuguangChatTheme(
    palette: FuguangChatPalette = FuguangChatPalette(),
    typography: FuguangChatTypography = FuguangChatTypography(),
    dimensions: FuguangChatDimensions = FuguangChatDimensions(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFuguangChatPalette provides palette,
        LocalFuguangChatTypography provides typography,
        LocalFuguangChatDimensions provides dimensions,
        content = content,
    )
}
