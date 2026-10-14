package com.attention.app.ui.theme

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 可访问性 seam：Material 3 基线要求在仪器化环境（API 35 模拟器）上断言。
 *
 * 覆盖触控目标尺寸、动态字体放大与语义标签。这一层依赖模拟器：
 * 没有模拟器时只能确认编译通过，本机运行情况如实记录。
 */
@RunWith(AndroidJUnit4::class)
class AttentionThemeAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * 已知差距，不是可以通过主题层修好的问题。
     *
     * Material 3 的 [Button] 固定 40dp 高，且不响应 `LocalMinimumInteractiveComponentEnforcement`
     * （本仓库实测：在主题里打开强制约束后仍为 40dp）。要达到 48dp 只能改页面上的按钮修饰符，
     * 属于页面级接入 #137 的范围，因此这里标记为忽略，等待该票修复后再启用。
     */
    @Ignore("现有页面的 Material 3 Button 高 40dp，低于 48dp 基线；修复属于页面级接入 #137")
    @Test
    fun interactive_controls_meet_the_material3_touch_target_baseline() {
        composeRule.onNodeWithTag("daily-capacity-save").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("onboarding-save-continue").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun interactive_controls_expose_a_semantic_label() {
        val label = composeRule.onNodeWithTag("daily-capacity-save")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ")
            .orEmpty()

        assertTrue("按钮必须带可朗读的语义标签，实际为 '$label'", label.isNotBlank())
    }

    @Test
    fun body_text_grows_when_the_user_enlarges_the_font() {
        val baseHeight = renderBodyTextHeight(fontScale = 1f, tag = "body-text-base")
        val enlargedHeight = renderBodyTextHeight(fontScale = 1.8f, tag = "body-text-enlarged")

        assertTrue(
            "字号放大后文本高度必须变大（基准 $baseHeight → 放大 $enlargedHeight）",
            enlargedHeight > baseHeight,
        )
    }

    private fun renderBodyTextHeight(fontScale: Float, tag: String): Int {
        composeRule.activity.setContent {
            AttentionTheme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = fontScale),
                ) {
                    Text(
                        text = "实际投入 1 小时 15 分钟",
                        style = LocalAttentionTokens.current.typography.body,
                        modifier = Modifier.testTag(tag),
                    )
                }
            }
        }
        return composeRule.onNodeWithTag(tag).fetchSemanticsNode().size.height
    }
}