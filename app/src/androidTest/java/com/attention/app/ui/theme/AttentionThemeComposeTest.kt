package com.attention.app.ui.theme

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 界面 seam：品牌语义色按含义可取，深浅两套配色都生效，且不随系统动态取色变化。
 *
 * 断言只读用户能观察到的结果：渲染出的品牌色与主题令牌，不读内部调用。
 */
@RunWith(AndroidJUnit4::class)
class AttentionThemeComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun five_brand_semantic_colors_are_resolvable_by_meaning_in_the_system_scheme() {
        mountProbe("brand-probe-system")

        assertEquals(expectedProbeText(darkTheme = isDeviceInDarkMode()), probeText("brand-probe-system"))
    }

    @Test
    fun light_scheme_resolves_the_five_brand_semantic_colors() {
        mountProbe(darkTheme = false, tag = "brand-probe-light")

        assertEquals(expectedProbeText(darkTheme = false), probeText("brand-probe-light"))
    }

    @Test
    fun dark_scheme_resolves_the_five_brand_semantic_colors() {
        mountProbe(darkTheme = true, tag = "brand-probe-dark")

        assertEquals(expectedProbeText(darkTheme = true), probeText("brand-probe-dark"))
    }

    @Test
    fun brand_colors_do_not_follow_material_you_dynamic_color() {
        mountProbe(darkTheme = false, tag = "brand-probe-light-again")

        assertEquals(
            "浅色主题的正在计时色必须是固定锈橙，而不是壁纸取色结果",
            "#FFA8481A",
            probeText("brand-probe-light-again").substringAfter("timingInProgress=").substringBefore(";"),
        )
        assertEquals(
            "浅色主题的已安排色必须是固定蓝，而不是壁纸取色结果",
            "#FF1F5FA8",
            probeText("brand-probe-light-again").substringAfter("scheduled=").substringBefore(";"),
        )
        assertEquals(
            "同一深浅模式下重复挂载主题，品牌色必须完全一致",
            expectedProbeText(darkTheme = false),
            probeText("brand-probe-light-again"),
        )
    }

    @Test
    fun spacing_radius_and_typography_tokens_resolve_for_pages() {
        mountProbe(darkTheme = false, tag = "brand-probe-scales")

        val text = probeText("brand-probe-scales")
        assertEquals("4.0,12.0,24.0", text.substringAfter("spacing=").substringBefore(";"))
        assertEquals("8.0,16.0,28.0", text.substringAfter("radius=").substringBefore(";"))
        assertEquals("title:20.0,body:15.0,caption:13.0", text.substringAfter("typography="))
    }

    @Test
    fun app_renders_the_fixed_brand_background_for_the_current_system_scheme() {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val darkTheme = isDeviceInDarkMode()
        val expectedBackground = if (darkTheme) 0xFF17130F.toInt() else 0xFFFBF6EE.toInt()

        val counts = pixelHistogram(bitmap)
        val backgroundShare = (counts[expectedBackground] ?: 0).toDouble() / counts.values.sum()

        assertTrue(
            "应用必须渲染固定品牌底色（深浅=$darkTheme），实际占比 $backgroundShare，" +
                "主色为 ${"0x%08X".format(counts.maxBy { it.value }.key)}",
            backgroundShare >= 0.01,
        )
    }

    private fun mountProbe(tag: String, darkTheme: Boolean = isDeviceInDarkMode()) {
        composeRule.activity.setContent {
            AttentionTheme(darkTheme = darkTheme) {
                BrandTokenProbe(modifier = Modifier.testTag(tag))
            }
        }
    }

    private fun probeText(tag: String): String = composeRule.onNodeWithTag(tag)
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.Text)
        ?.joinToString(" ")
        .orEmpty()

    private fun isDeviceInDarkMode(): Boolean {
        val uiMode = composeRule.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    /** 采样渲染结果的像素分布，用来确认固定品牌底色真的被画出来了。 */
    private fun pixelHistogram(bitmap: Bitmap): Map<Int, Int> {
        val counts = HashMap<Int, Int>()
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                counts.merge(bitmap.getPixel(x, y), 1, Int::plus)
                x += 8
            }
            y += 8
        }
        return counts
    }

    /** 期望值是规格里声明的品牌色与标度字面量，不是从实现里重新算出来的。 */
    private fun expectedProbeText(darkTheme: Boolean): String {
        val timing = if (darkTheme) "#FFFFB784" else "#FFA8481A"
        val scheduled = if (darkTheme) "#FF9CC5F5" else "#FF1F5FA8"
        val completed = if (darkTheme) "#FF8FD3A6" else "#FF1E5B33"
        val unattributed = if (darkTheme) "#FFBDB6AC" else "#FF5F5A52"
        val error = if (darkTheme) "#FFFFB4AB" else "#FFB3261E"
        return listOf(
            "timingInProgress=$timing",
            "scheduled=$scheduled",
            "completed=$completed",
            "unattributed=$unattributed",
            "error=$error",
            "spacing=4.0,12.0,24.0",
            "radius=8.0,16.0,28.0",
            "typography=title:20.0,body:15.0,caption:13.0",
        ).joinToString(";")
    }
}

@Composable
private fun BrandTokenProbe(modifier: Modifier = Modifier) {
    val tokens = LocalAttentionTokens.current
    Text(
        text = listOf(
            "timingInProgress=${tokens.brand.timingInProgress.accent.hexForProbe()}",
            "scheduled=${tokens.brand.scheduled.accent.hexForProbe()}",
            "completed=${tokens.brand.completed.accent.hexForProbe()}",
            "unattributed=${tokens.brand.unattributed.accent.hexForProbe()}",
            "error=${tokens.brand.error.accent.hexForProbe()}",
            "spacing=${tokens.spacing.small.value},${tokens.spacing.medium.value},${tokens.spacing.large.value}",
            "radius=${tokens.radius.small.value},${tokens.radius.medium.value},${tokens.radius.large.value}",
            "typography=title:${tokens.typography.title.fontSize.value}," +
                "body:${tokens.typography.body.fontSize.value}," +
                "caption:${tokens.typography.caption.fontSize.value}",
        ).joinToString(";"),
        modifier = modifier,
    )
}

private fun Color.hexForProbe(): String = String.format("#%08X", toArgb())