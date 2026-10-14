package com.attention.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 间距标度：三档固定间距，覆盖紧凑排列、标准分组与区块留白。
 * 页面从这里取间距，不再写裸 dp 值。
 */
@Immutable
data class AttentionSpacing(
    val small: Dp = 4.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 24.dp,
)

/**
 * 圆角标度：三档圆角，覆盖内联元素、卡片与浮层。
 */
@Immutable
data class AttentionRadius(
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 28.dp,
)

/**
 * 字体标度：三档字重层级。
 *
 * 全部使用 sp，因此跟随系统动态字号放大；行高统一按 Center 对齐，字号放大后仍然可读。
 */
@Immutable
data class AttentionTypography(
    val title: TextStyle = TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    ),
    val body: TextStyle = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    ),
    val caption: TextStyle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    ),
)

/**
 * 一个含义对应的一组品牌语义色：[accent] 用于描边、图标与强调文字，
 * [container] 与 [onContainer] 用于状态标签与色块。
 */
@Immutable
data class AttentionSemanticColor(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * 五种品牌语义色。名字就是含义：页面按语义取色，不写裸色值。
 */
@Immutable
data class AttentionBrandColors(
    /** 正在计时 */
    val timingInProgress: AttentionSemanticColor,
    /** 已安排 */
    val scheduled: AttentionSemanticColor,
    /** 已完成 */
    val completed: AttentionSemanticColor,
    /** 未归属 */
    val unattributed: AttentionSemanticColor,
    /** 错误 */
    val error: AttentionSemanticColor,
)

/**
 * 令牌层入口：页面从 [AttentionThemeTokens] 消费配色、间距、圆角与字体，不再写裸值。
 *
 * [colorScheme] 是两套固定品牌配色之一：浅色为暖纸色，深色为暖炭色。
 * 这里刻意不使用 Material You 动态取色——品牌观感必须固定，不随系统壁纸变化。
 */
@Immutable
data class AttentionThemeTokens(
    val colorScheme: ColorScheme = WarmPaperColorScheme,
    val brand: AttentionBrandColors = WarmPaperBrandColors,
    val spacing: AttentionSpacing = AttentionSpacing(),
    val radius: AttentionRadius = AttentionRadius(),
    val typography: AttentionTypography = AttentionTypography(),
) {
    companion object {
        /** 浅色系统下的暖纸色配色。 */
        val WarmPaperColorScheme: ColorScheme = lightColorScheme(
            primary = Color(0xFF8A5A2B),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDCC2),
            onPrimaryContainer = Color(0xFF2F1600),
            secondary = Color(0xFF755846),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFDCC2),
            onSecondaryContainer = Color(0xFF2F1600),
            tertiary = Color(0xFF6B5E4A),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFF4E7D3),
            onTertiaryContainer = Color(0xFF221B0D),
            error = Color(0xFFB3261E),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            background = Color(0xFFFBF6EE),
            onBackground = Color(0xFF1E1A16),
            surface = Color(0xFFFFFDF9),
            onSurface = Color(0xFF1E1A16),
            surfaceVariant = Color(0xFFF2E4D3),
            onSurfaceVariant = Color(0xFF4F4539),
            surfaceContainerLowest = Color(0xFFFFFDF9),
            surfaceContainerLow = Color(0xFFFBF3E8),
            surfaceContainer = Color(0xFFF5EADA),
            surfaceContainerHigh = Color(0xFFEFE3D1),
            surfaceContainerHighest = Color(0xFFE9DCC8),
            outline = Color(0xFF7A6E60),
            outlineVariant = Color(0xFFD5C4B1),
            inverseSurface = Color(0xFF35302A),
            inverseOnSurface = Color(0xFFF7EFE6),
            inversePrimary = Color(0xFFFFB784),
        )

        /** 深色系统下的暖炭色配色。 */
        val WarmCharcoalColorScheme: ColorScheme = darkColorScheme(
            primary = Color(0xFFFFB784),
            onPrimary = Color(0xFF522400),
            primaryContainer = Color(0xFF6F3B00),
            onPrimaryContainer = Color(0xFFFFDCC2),
            secondary = Color(0xFFE5BEAA),
            onSecondary = Color(0xFF432B1A),
            secondaryContainer = Color(0xFF5B402E),
            onSecondaryContainer = Color(0xFFFFDCC2),
            tertiary = Color(0xFFD7C3A0),
            onTertiary = Color(0xFF3B2F15),
            tertiaryContainer = Color(0xFF54452A),
            onTertiaryContainer = Color(0xFFF4E7D3),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = Color(0xFF17130F),
            onBackground = Color(0xFFECE0D4),
            surface = Color(0xFF1F1B16),
            onSurface = Color(0xFFECE0D4),
            surfaceVariant = Color(0xFF4F4539),
            onSurfaceVariant = Color(0xFFD5C4B1),
            surfaceContainerLowest = Color(0xFF1F1B16),
            surfaceContainerLow = Color(0xFF272219),
            surfaceContainer = Color(0xFF2C2621),
            surfaceContainerHigh = Color(0xFF37302A),
            surfaceContainerHighest = Color(0xFF423A33),
            outline = Color(0xFF9E8F80),
            outlineVariant = Color(0xFF4F4539),
            inverseSurface = Color(0xFFECE0D4),
            inverseOnSurface = Color(0xFF362F27),
            inversePrimary = Color(0xFF8A5A2B),
        )

        /** 浅色（暖纸色）下的五种品牌语义色。 */
        val WarmPaperBrandColors = AttentionBrandColors(
            timingInProgress = AttentionSemanticColor(
                accent = Color(0xFFA8481A),
                container = Color(0xFFFFE2D0),
                onContainer = Color(0xFF7A3410),
            ),
            scheduled = AttentionSemanticColor(
                accent = Color(0xFF1F5FA8),
                container = Color(0xFFD8E6FA),
                onContainer = Color(0xFF12395F),
            ),
            completed = AttentionSemanticColor(
                accent = Color(0xFF1E5B33),
                container = Color(0xFFD5EDDC),
                onContainer = Color(0xFF0F3A20),
            ),
            unattributed = AttentionSemanticColor(
                accent = Color(0xFF5F5A52),
                container = Color(0xFFE7E3DC),
                onContainer = Color(0xFF3F3B34),
            ),
            error = AttentionSemanticColor(
                accent = Color(0xFFB3261E),
                container = Color(0xFFF9DEDC),
                onContainer = Color(0xFF7A1811),
            ),
        )

        /** 深色（暖炭色）下的五种品牌语义色。 */
        val WarmCharcoalBrandColors = AttentionBrandColors(
            timingInProgress = AttentionSemanticColor(
                accent = Color(0xFFFFB784),
                container = Color(0xFF5C2E00),
                onContainer = Color(0xFFFFDCC2),
            ),
            scheduled = AttentionSemanticColor(
                accent = Color(0xFF9CC5F5),
                container = Color(0xFF123A5E),
                onContainer = Color(0xFFD3E3FD),
            ),
            completed = AttentionSemanticColor(
                accent = Color(0xFF8FD3A6),
                container = Color(0xFF12452A),
                onContainer = Color(0xFFCDEFD9),
            ),
            unattributed = AttentionSemanticColor(
                accent = Color(0xFFBDB6AC),
                container = Color(0xFF3F3B34),
                onContainer = Color(0xFFE0DAD0),
            ),
            error = AttentionSemanticColor(
                accent = Color(0xFFFFB4AB),
                container = Color(0xFF5C1512),
                onContainer = Color(0xFFFFDAD6),
            ),
        )

        private val LIGHT = AttentionThemeTokens(
            colorScheme = WarmPaperColorScheme,
            brand = WarmPaperBrandColors,
        )
        private val DARK = AttentionThemeTokens(
            colorScheme = WarmCharcoalColorScheme,
            brand = WarmCharcoalBrandColors,
        )

        /**
         * 按深浅系统解析出固定的品牌令牌。
         *
         * 只接受深浅两种输入：没有壁纸取色、没有系统主题参数，因此同一系统模式下结果恒定。
         */
        fun forDarkTheme(darkTheme: Boolean): AttentionThemeTokens = if (darkTheme) DARK else LIGHT
    }
}

/**
 * 令牌在组合树中的载体。页面用 [attentionTokens] 取值，测试也用它按语义取色。
 */
val LocalAttentionTokens = staticCompositionLocalOf<AttentionThemeTokens> {
    error("令牌未挂载：请用 AttentionTheme 包住内容")
}

/** 取当前品牌令牌。这是页面消费颜色、间距、圆角与字体的唯一入口。 */
@Composable
fun attentionTokens(): AttentionThemeTokens = LocalAttentionTokens.current

/**
 * 在应用顶层挂载品牌主题，覆盖所有现有页面。
 *
 * 刻意不使用 `dynamicLightColorScheme` / `dynamicDarkColorScheme`：
 * 暖纸色与暖炭色是固定品牌观感，不随系统壁纸变化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttentionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = AttentionThemeTokens.forDarkTheme(darkTheme = darkTheme)
    val typography = tokens.typography

    CompositionLocalProvider(
        // 打开 Material 3 的最小交互尺寸强制约束：视觉尺寸很小的控件（Checkbox 等）
        // 会获得 48dp 触控区域。注意 Button 固定 40dp 且不响应此约束（见可访问性 seam 的已知差距）。
        LocalMinimumInteractiveComponentEnforcement provides true,
        LocalAttentionTokens provides tokens,
    ) {
        MaterialTheme(
            colorScheme = tokens.colorScheme,
            typography = Typography(
                titleLarge = typography.title,
                titleMedium = typography.title,
                bodyLarge = typography.body,
                bodyMedium = typography.body,
                labelSmall = typography.caption,
            ),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(tokens.radius.small),
                small = RoundedCornerShape(tokens.radius.small),
                medium = RoundedCornerShape(tokens.radius.medium),
                large = RoundedCornerShape(tokens.radius.large),
                extraLarge = RoundedCornerShape(tokens.radius.large),
            ),
            content = content,
        )
    }
}