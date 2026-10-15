package com.attention.app.onboarding

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.ui.theme.AttentionTheme
import com.attention.workspace.state.OnboardingActions
import com.attention.workspace.state.OnboardingScreenState
import com.attention.workspace.state.onboardingScreenState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首次使用引导页的直接测试：只给它投影后的 screen state 与 fake actions，
 * 断言可见文案、三种离开方式与发出的 intent，不经过 ViewModel、Room 与 DataStore。
 *
 * 引导页面本身不需要 Activity 上下文，因此用轻量的 [createComposeRule] 挂载：
 * 这与其它页面测试一致，也不会顺带把整个应用的平台副作用拉起来。
 *
 * 这条用例钉住本票的核心：引导页面上**没有**任何会写业务数据的动作接口——
 * 引导要离开时只发出 [OnboardingActions.completeOnboarding] 一条意图，
 * 旧引导卡片那种「创建计划、日程与设置」的能力在这一页上不存在。
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_guide_explains_agenda_then_targets_then_statistics_in_that_order() {
        // 顺序固定：日程、目标树、统计
        val actions = RecordingOnboardingActions()
        mount(actions)

        composeRule.onNodeWithTag("onboarding-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-page-title").assert(hasText("日程"))
        click("onboarding-skip-page")

        composeRule.onNodeWithTag("onboarding-page-title").assert(hasText("目标树"))
        click("onboarding-skip-page")

        composeRule.onNodeWithTag("onboarding-page-title").assert(hasText("统计"))
        // 看完三页仍然没有离开引导：说明它只是说明，不要求任何输入
        assertEquals(0, actions.completed)
    }

    @Test
    fun each_page_only_explains_its_entry_and_never_asks_for_data_to_be_created() {
        val actions = RecordingOnboardingActions()
        val state = onboardingScreenState()
        mount(actions, state = state)

        // 三页正文逐页断言：只讲职责，页面上没有任何输入框
        state.pages.forEachIndexed { index, page ->
            composeRule.onNodeWithTag("onboarding-page-body").assertIsDisplayed()
            composeRule.onNodeWithTag("onboarding-page-body").assert(hasText(page.body))
            composeRule.onAllNodesWithTag("onboarding-plan-title").assertCountEquals(0)
            composeRule.onAllNodesWithTag("onboarding-schedule-title").assertCountEquals(0)
            composeRule.onAllNodesWithTag("onboarding-boundary-input").assertCountEquals(0)
            if (index < state.pages.lastIndex) click("onboarding-skip-page")
        }
    }

    @Test
    fun skipping_the_current_page_turns_the_page_and_only_the_last_page_finishes() {
        val actions = RecordingOnboardingActions()
        mount(actions)

        composeRule.onNodeWithTag("onboarding-indicator").assert(hasText("第 1 页，共 3 页"))
        click("onboarding-skip-page")
        composeRule.onNodeWithTag("onboarding-indicator").assert(hasText("第 2 页，共 3 页"))
        click("onboarding-skip-page")
        composeRule.onNodeWithTag("onboarding-indicator").assert(hasText("第 3 页，共 3 页"))

        // 前两页跳页都不结束引导
        assertEquals(0, actions.completed)

        // 最后一页再跳一次就没有下一页可翻了，引导到此结束
        click("onboarding-skip-page")
        assertEquals(1, actions.completed)
    }

    @Test
    fun skipping_the_remaining_steps_leaves_the_guide_right_away() {
        val actions = RecordingOnboardingActions()
        mount(actions)

        click("onboarding-skip-remaining")

        assertEquals(1, actions.completed)
    }

    @Test
    fun starting_beside_leaves_the_guide_right_away() {
        val actions = RecordingOnboardingActions()
        mount(actions)

        click("onboarding-start")

        assertEquals(1, actions.completed)
    }

    private fun mount(
        actions: OnboardingActions,
        state: OnboardingScreenState = onboardingScreenState(),
    ) {
        composeRule.setContent {
            AttentionTheme { OnboardingScreen(state = state, actions = actions) }
        }
    }

    private fun click(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    /** 引导只有一条落地意图；它不携带任何业务数据，因此这里只需要一个计数器。 */
    private class RecordingOnboardingActions : OnboardingActions {
        var completed = 0

        override fun completeOnboarding() {
            completed++
        }
    }
}
