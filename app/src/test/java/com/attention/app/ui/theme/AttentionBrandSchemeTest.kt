package com.attention.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 两套固定品牌配色的契约：浅色为暖纸色，深色为暖炭色。
 *
 * 固定意味着色值来自本主题文件而不是系统取色，因此每次打开都是同一套观感。
 */
class AttentionBrandSchemeTest {

    @Test
    fun light_scheme_is_warm_paper() {
        val scheme = AttentionThemeTokens.forDarkTheme(darkTheme = false).colorScheme

        assertEquals(Color(0xFFFBF6EE), scheme.background)
        assertEquals(Color(0xFFFFFDF9), scheme.surface)
        assertEquals(Color(0xFF1E1A16), scheme.onSurface)
        assertEquals(Color(0xFF8A5A2B), scheme.primary)
        assertEquals(Color(0xFFFFFDF9), scheme.surfaceContainerLowest)
    }

    @Test
    fun dark_scheme_is_warm_charcoal() {
        val scheme = AttentionThemeTokens.forDarkTheme(darkTheme = true).colorScheme

        assertEquals(Color(0xFF17130F), scheme.background)
        assertEquals(Color(0xFF1F1B16), scheme.surface)
        assertEquals(Color(0xFFECE0D4), scheme.onSurface)
        assertEquals(Color(0xFFFFB784), scheme.primary)
        assertEquals(Color(0xFF1F1B16), scheme.surfaceContainerLowest)
    }

    @Test
    fun both_schemes_are_warm_rather_than_neutral_gray() {
        val light = AttentionThemeTokens.forDarkTheme(darkTheme = false).colorScheme
        val dark = AttentionThemeTokens.forDarkTheme(darkTheme = true).colorScheme

        listOf(light.background, light.surface, dark.background, dark.surface).forEach { surface ->
            assertTrue(
                "暖色调表面应偏红（R 必须高于 B）: $surface",
                surface.red > surface.blue,
            )
        }
    }

    @Test
    fun light_and_dark_schemes_are_distinct() {
        val light = AttentionThemeTokens.forDarkTheme(darkTheme = false).colorScheme
        val dark = AttentionThemeTokens.forDarkTheme(darkTheme = true).colorScheme

        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.surface, dark.surface)
        assertTrue(
            "深色背景必须比浅色背景暗",
            dark.background.luminance() < light.background.luminance(),
        )
    }

    @Test
    fun error_role_carries_the_brand_red_in_both_schemes() {
        val light = AttentionThemeTokens.forDarkTheme(darkTheme = false).colorScheme
        val dark = AttentionThemeTokens.forDarkTheme(darkTheme = true).colorScheme

        assertEquals(Color(0xFFB3261E), light.error)
        assertEquals(Color(0xFFFFB4AB), dark.error)
    }

    @Test
    fun body_text_meets_material3_contrast_baseline_in_both_schemes() {
        val light = AttentionThemeTokens.forDarkTheme(darkTheme = false).colorScheme
        val dark = AttentionThemeTokens.forDarkTheme(darkTheme = true).colorScheme

        assertAtLeast4_5To1(light.onSurface, light.surface, "浅色正文")
        assertAtLeast4_5To1(light.onSurfaceVariant, light.surface, "浅色次要正文")
        assertAtLeast4_5To1(light.onSurfaceVariant, light.surfaceVariant, "浅色次要正文于次要表面")
        assertAtLeast4_5To1(light.onPrimary, light.primary, "浅色主要按钮文字")
        assertAtLeast4_5To1(light.onPrimaryContainer, light.primaryContainer, "浅色主要容器文字")
        assertAtLeast4_5To1(light.onError, light.error, "浅色错误文字")
        assertAtLeast4_5To1(dark.onSurface, dark.surface, "深色正文")
        assertAtLeast4_5To1(dark.onSurfaceVariant, dark.surface, "深色次要正文")
        assertAtLeast4_5To1(dark.onSurfaceVariant, dark.surfaceVariant, "深色次要正文于次要表面")
        assertAtLeast4_5To1(dark.onPrimary, dark.primary, "深色主要按钮文字")
        assertAtLeast4_5To1(dark.onPrimaryContainer, dark.primaryContainer, "深色主要容器文字")
        assertAtLeast4_5To1(dark.onError, dark.error, "深色错误文字")
    }

    private fun assertAtLeast4_5To1(foreground: Color, background: Color, label: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label 对比度 $ratio 低于 4.5:1", ratio >= 4.5)
    }
}

/** WCAG 相对亮度对比度，独立于实现计算，用于验证配色本身是否可读。 */
internal fun contrastRatio(foreground: Color, background: Color): Double {
    val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
    val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
    return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}