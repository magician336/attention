package com.attention.app.ui.theme

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.MainActivity
import com.attention.app.WorkspaceGuideSeed
import com.attention.app.ui.components.AttentionButton
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
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

    private companion object {
        /** 探针标识：只为强制组合真正发生，不承担断言职责。 */
        const val TOKEN_PROBE = "accessibility-token-probe"

        /** 等页面切换与 DataStore 读取的上限。 */
        const val TIMEOUT = 10_000L

        /**
         * #177 之后，未完成引导的第一次启动看到的是独立全屏引导而不是工作区。
         * 这一层要测的是工作区页面的触控目标，因此在启动 Activity 之前先把引导状态置为已完成；
         * 引导控件本身由 [the_full_screen_guide_controls_meet_the_material3_touch_target_baseline]
         * 从设置页重新打开引导来覆盖，引导的行为由 `WorkspaceOnboardingFlowIntegrationTest` 覆盖。
         */
        @JvmStatic
        @BeforeClass
        fun markTheGuideAsCompleted() {
            WorkspaceGuideSeed.markCompleted()
        }
    }

    /**
     * 页面级接入 #137 修好了这个差距：Material 3 的 [Button] 固定 40dp 且不响应
     * `LocalMinimumInteractiveComponentEnforcement`，因此页面上的按钮改为显式声明
     * 48dp 最小高度，而不是靠主题层的强制约束。
     *
     * 被测控件跟着主工作区收敛一起移动：今天工作台的每日容量卡片在 #172 下线，
     * 因此钉在合并后日程页上同属 [AttentionButton] 的「添加日程」按钮。
     */
    @Test
    fun page_controls_meet_the_material3_touch_target_baseline() {
        composeRule.onNodeWithTag("agenda-add-submit", useUnmergedTree = true)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
    }

    /**
     * 引导的触控目标改由独立全屏引导自己的控件承担（#178 删除了旧引导卡片之后，
     * 旧引导卡片上那个「保存并继续」按钮的测试标识已经不存在了）。
     *
     * 断言的意图不变：页面级按钮满足 Material 3 的 48dp 基线。引导只有在从设置页
     * 重新打开时才出现在一个引导状态已完成的宿主上，因此这里走
     * 「统计 → 设置 → 重新打开引导」这条唯一入口。
     */
    @Test
    fun the_full_screen_guide_controls_meet_the_material3_touch_target_baseline() {
        composeRule.onNodeWithTag("nav-statistics").performClick()
        composeRule.waitForTag("statistics-open-settings")
        // 统计页很长，「设置与数据」入口在首屏之外：必须先滚到它再点，否则点的是一个屏幕外的节点
        composeRule.onNodeWithTag("statistics-open-settings").performScrollTo().performClick()
        composeRule.waitForTag("settings-open-onboarding")
        composeRule.onNodeWithTag("settings-open-onboarding").performClick()

        composeRule.waitForTag("onboarding-screen")
        composeRule.onNodeWithTag("onboarding-start").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("onboarding-skip-page").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun interactive_controls_expose_a_semantic_label() {
        val label = composeRule.onNodeWithTag("agenda-add-submit")
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

    /**
     * 深色（暖炭色）主题在设备上真正组合出来之后仍然可读。
     *
     * 这一条补的是 #136 留下的缺口：暖炭色此前只有静态色值断言，没有在设备上确认
     * 组合后的深色表面与文字、品牌语义色仍然满足 4.5:1。
     */
    @Test
    fun dark_theme_meets_the_material3_contrast_baseline_on_device() {
        val tokens = renderResolvedTokens(darkTheme = true)
        val scheme = tokens.colorScheme
        val brand = tokens.brand

        assertAtLeast4_5To1(scheme.onSurface, scheme.surface, "深色正文")
        assertAtLeast4_5To1(scheme.onSurfaceVariant, scheme.surface, "深色次要正文")
        assertAtLeast4_5To1(scheme.onSurfaceVariant, scheme.surfaceVariant, "深色次要正文于次要表面")
        assertAtLeast4_5To1(scheme.onPrimary, scheme.primary, "深色主要按钮文字")
        assertAtLeast4_5To1(scheme.inverseOnSurface, scheme.inverseSurface, "深色短时提示")

        assertAtLeast4_5To1(brand.timingInProgress.accent, scheme.surface, "深色正在计时")
        assertAtLeast4_5To1(brand.scheduled.accent, scheme.surface, "深色已安排")
        assertAtLeast4_5To1(brand.completed.accent, scheme.surface, "深色已完成")
        assertAtLeast4_5To1(brand.unattributed.accent, scheme.surface, "深色未归属")
        assertAtLeast4_5To1(brand.error.accent, scheme.surface, "深色错误")

        assertAtLeast4_5To1(brand.timingInProgress.onContainer, brand.timingInProgress.container, "深色正在计时容器文字")
        assertAtLeast4_5To1(brand.scheduled.onContainer, brand.scheduled.container, "深色已安排容器文字")
        assertAtLeast4_5To1(brand.completed.onContainer, brand.completed.container, "深色已完成容器文字")
        assertAtLeast4_5To1(brand.unattributed.onContainer, brand.unattributed.container, "深色未归属容器文字")
        assertAtLeast4_5To1(brand.error.onContainer, brand.error.container, "深色错误容器文字")
    }

    /**
     * 浅色（暖纸色）主题在同一台设备上同样满足基线，避免只把深色当作可接受的那一套。
     */
    @Test
    fun light_theme_meets_the_material3_contrast_baseline_on_device() {
        val tokens = renderResolvedTokens(darkTheme = false)
        val scheme = tokens.colorScheme
        val brand = tokens.brand

        assertAtLeast4_5To1(scheme.onSurface, scheme.surface, "浅色正文")
        assertAtLeast4_5To1(scheme.onSurfaceVariant, scheme.surface, "浅色次要正文")
        assertAtLeast4_5To1(scheme.onSurfaceVariant, scheme.surfaceVariant, "浅色次要正文于次要表面")
        assertAtLeast4_5To1(scheme.onPrimary, scheme.primary, "浅色主要按钮文字")
        assertAtLeast4_5To1(scheme.inverseOnSurface, scheme.inverseSurface, "浅色短时提示")

        assertAtLeast4_5To1(brand.timingInProgress.accent, scheme.surface, "浅色正在计时")
        assertAtLeast4_5To1(brand.scheduled.accent, scheme.surface, "浅色已安排")
        assertAtLeast4_5To1(brand.completed.accent, scheme.surface, "浅色已完成")
        assertAtLeast4_5To1(brand.unattributed.accent, scheme.surface, "浅色未归属")
        assertAtLeast4_5To1(brand.error.accent, scheme.surface, "浅色错误")

        assertAtLeast4_5To1(brand.timingInProgress.onContainer, brand.timingInProgress.container, "浅色正在计时容器文字")
        assertAtLeast4_5To1(brand.scheduled.onContainer, brand.scheduled.container, "浅色已安排容器文字")
        assertAtLeast4_5To1(brand.completed.onContainer, brand.completed.container, "浅色已完成容器文字")
        assertAtLeast4_5To1(brand.unattributed.onContainer, brand.unattributed.container, "浅色未归属容器文字")
        assertAtLeast4_5To1(brand.error.onContainer, brand.error.container, "浅色错误容器文字")
    }

    /**
     * 字号放大到 1.8 倍后，页面上的主要操作入口仍然够大、仍然可见。
     *
     * 只断言令牌文字变大是不够的：大字号下按钮高度会被内容撑开，触控尺寸可能因此变化，
     * 因此这里在页面上直接断言放大之后按钮仍然满足 48dp。
     */
    @Test
    fun page_controls_keep_the_touch_target_when_the_user_enlarges_the_font() {
        composeRule.activity.setContent {
            AttentionTheme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = 1.8f),
                ) {
                    AttentionButton(
                        onClick = {},
                        modifier = Modifier.testTag("enlarged-font-save"),
                    ) {
                        Text(text = "保存设置", style = LocalAttentionTokens.current.typography.title)
                    }
                }
            }
        }

        composeRule.onNodeWithTag("enlarged-font-save").assertHeightIsAtLeast(48.dp)
    }

    private fun renderResolvedTokens(darkTheme: Boolean): AttentionThemeTokens {
        // 令牌必须在设备上真正组合出来之后再断言：静态色值断言无法证明主题挂载正确。
        val holder = arrayOfNulls<AttentionThemeTokens>(1)
        composeRule.activity.setContent {
            AttentionTheme(darkTheme = darkTheme) {
                Text(text = "令牌探针", modifier = Modifier.testTag(TOKEN_PROBE))
                holder[0] = LocalAttentionTokens.current
            }
        }
        // 查一次语义树强制完成组合，否则读到的仍然是空。
        composeRule.onNodeWithTag(TOKEN_PROBE).assertIsDisplayed()
        return requireNotNull(holder[0]) { "令牌未挂载：请用 AttentionTheme 包住内容" }
    }

    private fun assertAtLeast4_5To1(foreground: Color, background: Color, label: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label 对比度 $ratio 低于 4.5:1", ratio >= 4.5)
    }

    /**
     * 等到带 [tag] 的节点真的出现在语义树里再继续。
     *
     * 页面切换与 DataStore 读取都是异步的：直接查询会在节点出现之前就失败。
     */
    private fun ComposeTestRule.waitForTag(tag: String) {
        waitUntil(TIMEOUT) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
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

/**
 * WCAG 相对亮度对比度。
 *
 * 这里自带一份实现：仪器化源集看不到单元测试源集里的同名工具，而对比度必须在设备上算。
 */
private fun contrastRatio(foreground: Color, background: Color): Double {
    fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
    val lighter = maxOf(luminance(foreground), luminance(background))
    val darker = minOf(luminance(foreground), luminance(background))
    return (lighter + 0.05) / (darker + 0.05)
}