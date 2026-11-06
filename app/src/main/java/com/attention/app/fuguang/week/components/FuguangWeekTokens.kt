package com.attention.app.fuguang.week.components

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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Visual tokens for the week-rearrange prototype. Kept separate from the retired 1b UI. */
@Immutable
data class FuguangWeekColors(
    val canvas: Color = Color(0xFFF8F5EC),
    val surface: Color = Color(0xFFFFFEFA),
    val elevated: Color = Color(0xFFFFFFFF),
    val surfaceMuted: Color = Color(0xFFFAF6EA),
    val primary: Color = Color(0xFF4F725C),
    val primaryDark: Color = Color(0xFF3F5F4C),
    val primarySoft: Color = Color(0xFFE5EDE6),
    val primaryPale: Color = Color(0xFFF0F4EF),
    val gold: Color = Color(0xFFE3C56D),
    val goldSoft: Color = Color(0xFFF8E8B9),
    val goldDeep: Color = Color(0xFF96742A),
    val blueSoft: Color = Color(0xFFD8E8EA),
    val blueDeep: Color = Color(0xFF4F7780),
    val rose: Color = Color(0xFFC7837D),
    val roseSoft: Color = Color(0xFFF4E3DF),
    val block: Color = Color(0xFFDCD8CB),
    val border: Color = Color(0xFFE7E1D3),
    val borderStrong: Color = Color(0xFFCFC8B8),
    val text: Color = Color(0xFF29342D),
    val textSecondary: Color = Color(0xFF697268),
    val textMuted: Color = Color(0xFF969C91),
    val white: Color = Color(0xFFFFFFFF),
)

@Immutable
data class FuguangWeekType(
    val display: TextStyle = weekTextStyle(22.sp, 29.sp, FontWeight.Bold),
    val section: TextStyle = weekTextStyle(16.sp, 22.sp, FontWeight.SemiBold),
    val title: TextStyle = weekTextStyle(14.sp, 20.sp, FontWeight.SemiBold),
    val body: TextStyle = weekTextStyle(13.sp, 19.sp, FontWeight.Normal),
    val bodyStrong: TextStyle = weekTextStyle(13.sp, 19.sp, FontWeight.SemiBold),
    val label: TextStyle = weekTextStyle(11.sp, 16.sp, FontWeight.Medium),
    val caption: TextStyle = weekTextStyle(10.sp, 15.sp, FontWeight.Medium),
    val micro: TextStyle = weekTextStyle(9.sp, 13.sp, FontWeight.Normal),
)

@Immutable
data class FuguangWeekDims(
    val screenHorizontal: Dp = 20.dp,
    val sectionGap: Dp = 18.dp,
    val itemGap: Dp = 10.dp,
    val cardRadius: Dp = 18.dp,
    val controlRadius: Dp = 24.dp,
    val cardPadding: Dp = 16.dp,
    val compactPadding: Dp = 12.dp,
    val touchTarget: Dp = 44.dp,
)

private fun weekTextStyle(size: TextUnit, lineHeight: TextUnit, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size,
    lineHeight = lineHeight,
    fontWeight = weight,
    letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

val LocalFuguangWeekColors = staticCompositionLocalOf { FuguangWeekColors() }
val LocalFuguangWeekType = staticCompositionLocalOf { FuguangWeekType() }
val LocalFuguangWeekDims = staticCompositionLocalOf { FuguangWeekDims() }

@Composable
fun fuguangWeekColors(): FuguangWeekColors = LocalFuguangWeekColors.current

@Composable
fun fuguangWeekType(): FuguangWeekType = LocalFuguangWeekType.current

@Composable
fun fuguangWeekDims(): FuguangWeekDims = LocalFuguangWeekDims.current

@Composable
fun FuguangWeekTheme(
    colors: FuguangWeekColors = FuguangWeekColors(),
    type: FuguangWeekType = FuguangWeekType(),
    dims: FuguangWeekDims = FuguangWeekDims(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFuguangWeekColors provides colors,
        LocalFuguangWeekType provides type,
        LocalFuguangWeekDims provides dims,
        content = content,
    )
}