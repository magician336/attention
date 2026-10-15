package com.attention.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceExperienceIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // 被销毁的 Activity 仍可能在收尾时写回「上次打开」：等写入停稳再放行下一条用例。
    @get:Rule(order = 1)
    val settingsBarrier = WorkspaceHostSettingsBarrier.quiesceAfterActivity

    companion object {
        private const val TARGET_ID = "ui-experience-target"
        private const val TARGET_TITLE = "UI-投入经验"

        @JvmStatic
        @BeforeClass
        fun seedExperienceBeforeActivityStarts() {
            WorkspaceExperienceFixture.seed(TARGET_ID, TARGET_TITLE, 15)
            // #177：未完成引导时第一次启动是全屏引导；这条用例要看的是引导之后的工作区
            WorkspaceGuideSeed.markCompleted()
        }
    }

    @Test
    fun statistics_screen_shows_latest_investment_experience() {
        composeRule.onNode(hasText("统计") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("投入经验：15", substring = true).assertCountEquals(2)
    }

    @Test
    fun statistics_screen_shows_level_progress_and_upgrade_feedback() {
        composeRule.onNode(hasText("统计") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("当前等级区间进度：15/1000 点").assertExists()
        composeRule.onNodeWithText("下一等级门槛：1000 点").assertExists()
        composeRule.onNodeWithText("剩余经验：985 点").assertExists()
        composeRule.onNodeWithText("升级反馈：达到下一等级门槛后会显示").assertExists()
    }
}
