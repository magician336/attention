package com.attention.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.domain.AttentionState
import com.attention.domain.Milestone
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceCumulativeFeedbackIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // 被销毁的 Activity 仍可能在收尾时写回「上次打开」：等写入停稳再放行下一条用例。
    @get:Rule(order = 1)
    val settingsBarrier = WorkspaceHostSettingsBarrier.quiesceAfterActivity

    companion object {
        private const val MILESTONE_COUNT = 15

        @JvmStatic
        @BeforeClass
        fun seedCumulativeFeedback() {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    val state = AttentionState(
                        milestones = (1..MILESTONE_COUNT).map { index ->
                            Milestone(
                                id = "ui-cumulative-$index",
                                kind = "cumulative",
                                instanceKey = "cumulative:${index * 100}",
                                reward = when {
                                    index <= 4 -> 10_000L
                                    index <= 9 -> 20_000L
                                    index <= 14 -> 30_000L
                                    else -> 40_000L
                                },
                                achievedAtEpochMillis = index.toLong(),
                            )
                        },
                    )
                    RoomBusinessDataRepository(database).replace(state)
                }
            } finally {
                database.close()
            }
            // #177：未完成引导时第一次启动是全屏引导；这条用例要看的是引导之后的工作区
            WorkspaceGuideSeed.markCompleted()
        }
    }

    @Test
    fun cumulative_feedback_shows_all_nodes_and_remains_idempotent_after_settlement() {
        composeRule.onNode(hasText("统计") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("累计投入里程碑：", substring = true).assertCountEquals(MILESTONE_COUNT)
        composeRule.onNodeWithText("累计投入里程碑：100 小时 · +10000 投入经验").assertExists()
        composeRule.onNodeWithText("累计投入里程碑：1500 小时 · +40000 投入经验").assertExists()

        composeRule.onNodeWithText("结算可达成里程碑").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("累计投入里程碑：", substring = true).assertCountEquals(MILESTONE_COUNT)
    }
}
