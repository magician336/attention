package com.attention.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 品牌语义色契约：五种含义各自有固定色相，页面按含义取色，不写裸色值。
 *
 * 锈橙=正在计时、蓝=已安排、森林绿=已完成、灰=未归属、红=错误。
 */
class AttentionBrandColorTest {

    @Test
    fun light_scheme_resolves_the_five_brand_semantic_colors() {
        val brand = AttentionThemeTokens.forDarkTheme(darkTheme = false).brand

        assertEquals(Color(0xFFA8481A), brand.timingInProgress.accent)
        assertEquals(Color(0xFF1F5FA8), brand.scheduled.accent)
        assertEquals(Color(0xFF1E5B33), brand.completed.accent)
        assertEquals(Color(0xFF5F5A52), brand.unattributed.accent)
        assertEquals(Color(0xFFB3261E), brand.error.accent)
    }

    @Test
    fun dark_scheme_resolves_the_five_brand_semantic_colors() {
        val brand = AttentionThemeTokens.forDarkTheme(darkTheme = true).brand

        assertEquals(Color(0xFFFFB784), brand.timingInProgress.accent)
        assertEquals(Color(0xFF9CC5F5), brand.scheduled.accent)
        assertEquals(Color(0xFF8FD3A6), brand.completed.accent)
        assertEquals(Color(0xFFBDB6AC), brand.unattributed.accent)
        assertEquals(Color(0xFFFFB4AB), brand.error.accent)
    }

    @Test
    fun the_five_meanings_are_visually_distinguishable_from_each_other() {
        listOf(false, true).forEach { dark ->
            val brand = AttentionThemeTokens.forDarkTheme(darkTheme = dark).brand
            val accents = listOf(
                brand.timingInProgress.accent,
                brand.scheduled.accent,
                brand.completed.accent,
                brand.unattributed.accent,
                brand.error.accent,
            )

            assertEquals("深浅=$dark 时五种含义必须互不相同", 5, accents.toSet().size)
        }
    }

    @Test
    fun brand_error_is_the_same_red_as_the_material_error_role() {
        listOf(false, true).forEach { dark ->
            val tokens = AttentionThemeTokens.forDarkTheme(darkTheme = dark)

            assertEquals(
                "深浅=$dark 时品牌错误色与 Material error 角色必须是同一个红",
                tokens.colorScheme.error,
                tokens.brand.error.accent,
            )
        }
    }

    @Test
    fun every_brand_accent_is_readable_on_its_scheme_surface() {
        listOf(false, true).forEach { dark ->
            val tokens = AttentionThemeTokens.forDarkTheme(darkTheme = dark)
            val surface = tokens.colorScheme.surface
            val brand = tokens.brand

            mapOf(
                "正在计时" to brand.timingInProgress,
                "已安排" to brand.scheduled,
                "已完成" to brand.completed,
                "未归属" to brand.unattributed,
                "错误" to brand.error,
            ).forEach { (meaning, color) ->
                val ratio = contrastRatio(color.accent, surface)
                assertTrue("深浅=$dark 的$meaning 对比度 $ratio 低于 4.5:1", ratio >= 4.5)
            }
        }
    }

    @Test
    fun every_brand_container_pairs_with_a_readable_label_color() {
        listOf(false, true).forEach { dark ->
            val brand = AttentionThemeTokens.forDarkTheme(darkTheme = dark).brand

            mapOf(
                "正在计时" to brand.timingInProgress,
                "已安排" to brand.scheduled,
                "已完成" to brand.completed,
                "未归属" to brand.unattributed,
                "错误" to brand.error,
            ).forEach { (meaning, color) ->
                val ratio = contrastRatio(color.onContainer, color.container)
                assertTrue("深浅=$dark 的$meaning 容器文字对比度 $ratio 低于 4.5:1", ratio >= 4.5)
                assertNotEquals("深浅=$dark 的$meaning 容器不能与自身底色同色", color.container, color.onContainer)
            }
        }
    }
}