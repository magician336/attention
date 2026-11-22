package com.attention.app.fuguang.components

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

@Immutable
data class FuguangSeeds(val ink: Color, val paper: Color, val main: Color, val accent: Color)

@Immutable
data class FuguangRoles(
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val inkOnN: Color,
    val paper: Color,
    val line: Color,
    val line2: Color,
    val n3: Color,
    val n2: Color,
    val n1: Color,
    val main: Color,
    val mainDeep: Color,
    val mainInk: Color,
    val mainSoft: Color,
    val mainTint: Color,
    val accent: Color,
    val accentDeep: Color,
    val accentDeep2: Color,
    val accentTint: Color,
    val surface: Color,
    val danger: Color,
)

enum class FuguangThemeName(val label: String) {
    GILDED("浮光跃金"),
    BLUSH("先帝最爱"),
    GUAVA("青提芭乐"),
    WALDEN("瓦尔登"),
    ISLAND("孤岛"),
    MINT("薄荷可颂"),
}

object FuguangThemeSeeds {
    private val gilded = FuguangSeeds(hex(0x2F302B), hex(0xF7F4EA), hex(0x799B84), hex(0xF1D996))
    private val blush = FuguangSeeds(hex(0x44262E), hex(0xFBF3EC), hex(0xF0708C), hex(0xF5C7C9))
    private val guava = FuguangSeeds(hex(0x4F2028), hex(0xFBF7EC), hex(0xF17596), hex(0xE4F6A9))
    private val walden = FuguangSeeds(hex(0x153540), hex(0xF4F8F7), hex(0x4DA4C3), hex(0xCCE7D9))
    private val island = FuguangSeeds(hex(0x16324F), hex(0xF6F7F2), hex(0x225DAB), hex(0xCADEC3))
    private val mint = FuguangSeeds(hex(0x3D2C26), hex(0xF9F4EA), hex(0x8D6C63), hex(0xFCD3D5))

    fun seedsFor(theme: FuguangThemeName): FuguangSeeds = when (theme) {
        FuguangThemeName.GILDED -> gilded
        FuguangThemeName.BLUSH -> blush
        FuguangThemeName.GUAVA -> guava
        FuguangThemeName.WALDEN -> walden
        FuguangThemeName.ISLAND -> island
        FuguangThemeName.MINT -> mint
    }
}

object FuguangDerivedThemes {
    private val cache = FuguangThemeName.entries.associateWith { deriveRoles(FuguangThemeSeeds.seedsFor(it)) }
    fun rolesFor(theme: FuguangThemeName): FuguangRoles = cache.getValue(theme)
}

@Immutable
data class FuguangTypeScale(
    val t8: TextStyle = textStyle(8.sp, 12.sp, FontWeight.Normal),
    val t95: TextStyle = textStyle(9.5.sp, 14.sp, FontWeight.Normal),
    val t11: TextStyle = textStyle(11.sp, 16.sp, FontWeight.Normal),
    val t125: TextStyle = textStyle(12.5.sp, 19.sp, FontWeight.Normal),
    val t135: TextStyle = textStyle(13.5.sp, 22.4.sp, FontWeight.Normal),
    val t17: TextStyle = textStyle(17.sp, 25.sp, FontWeight.SemiBold),
    val t29: TextStyle = textStyle(29.sp, 36.sp, FontWeight.Bold),
    val t46: TextStyle = textStyle(46.sp, 52.sp, FontWeight.Bold),
)

@Immutable
data class FuguangDimensions(
    val hairline: Dp = 1.dp,
    val navHeight: Dp = 58.dp,
    val goalHeight: Dp = 25.dp,
    val inputHeight: Dp = 38.dp,
    val sendSize: Dp = 38.dp,
    val tabBarHeight: Dp = 52.dp,
    val avatarSize: Dp = 25.dp,
    val bubbleMaxWidth: Dp = 270.dp,
    val cardWidth: Dp = 300.dp,
    val feishuWidth: Dp = 296.dp,
    val dateBarHeight: Dp = 46.dp,
    val timelineHeight: Dp = 270.dp,
    val buttonHeight: Dp = 34.dp,
    val badgeHeight: Dp = 25.dp,
    val rowMinTouch: Dp = 44.dp,
)

private fun textStyle(size: TextUnit, lineHeight: TextUnit, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size,
    lineHeight = lineHeight,
    fontWeight = weight,
    letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None),
)

val LocalFuguangRoles = staticCompositionLocalOf { FuguangDerivedThemes.rolesFor(FuguangThemeName.GILDED) }
val LocalFuguangTypeScale = staticCompositionLocalOf { FuguangTypeScale() }
val LocalFuguangDimensions = staticCompositionLocalOf { FuguangDimensions() }

@Composable fun fuguangRoles(): FuguangRoles = LocalFuguangRoles.current
@Composable fun fuguangType(): FuguangTypeScale = LocalFuguangTypeScale.current
@Composable fun fuguangDims(): FuguangDimensions = LocalFuguangDimensions.current

@Composable
fun FuguangTheme(
    theme: FuguangThemeName = FuguangThemeName.GILDED,
    type: FuguangTypeScale = FuguangTypeScale(),
    dims: FuguangDimensions = FuguangDimensions(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFuguangRoles provides FuguangDerivedThemes.rolesFor(theme),
        LocalFuguangTypeScale provides type,
        LocalFuguangDimensions provides dims,
        content = content,
    )
}

private fun hex(rgb: Int): Color = Color(0xFF000000.toInt() or rgb)
