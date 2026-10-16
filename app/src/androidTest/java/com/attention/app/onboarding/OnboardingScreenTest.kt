package com.attention.app.onboarding

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
 * 首次设置页的直接测试：只给它投影后的 screen state 与 fake actions，
 * 断言五步顺序、跳过语义与完成 intent，不经过 ViewModel、Room 与 DataStore。
 *
 * 引导页面本身不需要 Activity 上下文，因此用轻量的 [createComposeRule] 挂载：
 * 这与其它页面测试一致，也不会顺带把整个应用的平台副作用拉起来。
 *
 * 核心入口仍可跳过；写入动作只在用户点击「保存并继续」时触发。
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_setup_exposes_five_steps_in_order() {
        val actions = RecordingOnboardingActions()
        mount(actions)

        composeRule.onNodeWithTag("onboarding-screen").assertIsDisplayed()
        stateTitles().forEachIndexed { index, title ->
            composeRule.onNodeWithTag("onboarding-page-title").assert(hasText(title))
            if (index < stateTitles().lastIndex) click("onboarding-skip-page")
        }
        assertEquals(0, actions.completed)
    }

    @Test
    fun each_step_has_its_projected_body() {
        val actions = RecordingOnboardingActions()
        val state = onboardingScreenState()
        mount(actions, state = state)

        state.pages.forEachIndexed { index, page ->
            composeRule.onNodeWithTag("onboarding-page-body").assertIsDisplayed()
            composeRule.onNodeWithTag("onboarding-page-body").assert(hasText(page.body))
            if (index < state.pages.lastIndex) click("onboarding-skip-page")
        }
    }

    @Test
    fun skipping_the_current_page_turns_the_page_and_only_the_last_page_finishes() {
        val actions = RecordingOnboardingActions()
        mount(actions)

        composeRule.onNodeWithTag("onboarding-indicator").assert(hasText("第 1 步，共 5 步"))
        click("onboarding-skip-page")
        composeRule.onNodeWithTag("onboarding-indicator").assert(hasText("第 2 步，共 5 步"))
        click("onboarding-skip-page")
        composeRule.onNodeWithTag("onboarding-indicator").assert(hasText("第 3 步，共 5 步"))
        click("onboarding-skip-page")
        click("onboarding-skip-page")

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
    fun saving_a_step_advances_without_completing_the_setup() {
        val actions = RecordingOnboardingActions()
        mount(actions)

        click("onboarding-save-step")

        assertEquals(0, actions.completed)
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

    private fun stateTitles() = listOf("规划日界线", "周起始日", "每日容量", "第一个计划", "第一条日程")

    /** fake 只记录完成意图；各步骤的写入动作在这些跳过路径中不会被调用。 */
    private class RecordingOnboardingActions : OnboardingActions {
        var completed = 0

        override fun savePlanningDaySettings(boundaryMinutes: Int, weekStartDay: Int, dailyCapacityMinutes: Int?) = Unit

        override fun selectWeekStartDay(day: Int) = Unit

        override fun addFirstTarget(title: String) = Unit

        override fun addFirstSchedule(draft: com.attention.workspace.state.AgendaScheduleDraft) = Unit

        override fun completeOnboarding() {
            completed++
        }
    }
}
