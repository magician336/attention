package com.attention.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.domain.AttentionState
import com.attention.domain.addTarget
import com.attention.domain.addTimeEntry
import com.attention.domain.planningDate
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceExperienceIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val TARGET_ID = "ui-experience-target"
        private const val TARGET_TITLE = "UI-投入经验"

        @JvmStatic
        @BeforeClass
        fun seedExperienceBeforeActivityStarts() {
            val planningDate = AttentionState().planningDate(Instant.now())
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    val withTarget = AttentionState().addTarget(TARGET_TITLE)
                    val target = withTarget.targets.single()
                    val state = withTarget
                        .copy(targets = listOf(target.copy(id = TARGET_ID)))
                        .addTimeEntry(planningDate.toString(), 15, TARGET_ID)
                    RoomBusinessDataRepository(database).replace(state)
                }
            } finally {
                database.close()
            }
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
