package com.attention.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceLevelProgressIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val TARGET_ID = "ui-level-progress-target"
        private const val TARGET_TITLE = "UI-等级进度"

        @JvmStatic
        @BeforeClass
        fun seedLevelProgressBeforeActivityStarts() {
            WorkspaceExperienceFixture.seed(TARGET_ID, TARGET_TITLE, 3_000)
        }
    }

    @Test
    fun statistics_screen_shows_cross_level_progress_and_upgrade_feedback() {
        composeRule.onNode(hasText("统计") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("投入经验：3000", substring = true).assertExists()
        composeRule.onNodeWithText("当前等级区间进度：0/3000 点").assertExists()
        composeRule.onNodeWithText("下一等级门槛：6000 点").assertExists()
        composeRule.onNodeWithText("剩余经验：3000 点").assertExists()
        composeRule.onNodeWithText("升级反馈：已达到等级 3").assertExists()
    }
}
