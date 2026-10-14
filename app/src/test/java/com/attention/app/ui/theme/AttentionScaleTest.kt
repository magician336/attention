package com.attention.app.ui.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 令牌层标度契约：间距、圆角、字体三档标度在浅色与深色下都解析为同一组固定值，
 * 字体档位必须使用 sp 以便跟随系统动态字号。
 */
class AttentionScaleTest {

    @Test
    fun spacing_scale_has_three_tiers_in_both_brand_schemes() {
        val light = AttentionThemeTokens.forDarkTheme(darkTheme = false).spacing
        val dark = AttentionThemeTokens.forDarkTheme(darkTheme = true).spacing

        assertEquals(4.dp, light.small)
        assertEquals(12.dp, light.medium)
        assertEquals(24.dp, light.large)
        assertEquals("间距标度不随深浅主题变化", light, dark)
    }

    @Test
    fun radius_scale_has_three_tiers_in_both_brand_schemes() {
        val light = AttentionThemeTokens.forDarkTheme(darkTheme = false).radius
        val dark = AttentionThemeTokens.forDarkTheme(darkTheme = true).radius

        assertEquals(8.dp, light.small)
        assertEquals(16.dp, light.medium)
        assertEquals(28.dp, light.large)
        assertEquals("圆角标度不随深浅主题变化", light, dark)
    }

    @Test
    fun typography_scale_has_four_tiers_ordered_by_emphasis() {
        val typography = AttentionThemeTokens.forDarkTheme(darkTheme = false).typography

        assertEquals(28.sp, typography.display.fontSize)
        assertEquals(20.sp, typography.title.fontSize)
        assertEquals(15.sp, typography.body.fontSize)
        assertEquals(13.sp, typography.caption.fontSize)
        assertEquals(FontWeight.Medium, typography.title.fontWeight)
        assertTrue(
            "四档字号的层级顺序必须递减",
            typography.display.fontSize.value > typography.title.fontSize.value &&
                typography.title.fontSize.value > typography.body.fontSize.value &&
                typography.body.fontSize.value > typography.caption.fontSize.value,
        )
        assertTrue(
            "页面标题与区块标题必须保持明显字号差，否则页面失去主次结构",
            typography.display.fontSize.value - typography.title.fontSize.value >= 6.0,
        )
    }

    @Test
    fun typography_scale_uses_sp_so_it_scales_with_the_system_font_size() {
        val typography = AttentionThemeTokens.forDarkTheme(darkTheme = true).typography

        listOf(typography.display, typography.title, typography.body, typography.caption).forEach { style ->
            assertEquals(TextUnitType.Sp, style.fontSize.type)
            assertEquals(TextUnitType.Sp, style.lineHeight.type)
            assertEquals(
                "行高对齐到基线之上，字号放大后仍保持可读节奏",
                LineHeightStyle.Alignment.Center,
                style.lineHeightStyle?.alignment,
            )
        }
    }
}