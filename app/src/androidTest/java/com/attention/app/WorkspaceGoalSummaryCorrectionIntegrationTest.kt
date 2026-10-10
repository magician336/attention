package com.attention.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.domain.AttentionState
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import com.attention.domain.planningDate
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.assertCountEquals

@RunWith(AndroidJUnit4::class)
class WorkspaceGoalSummaryCorrectionIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val TARGET_ID = "ui-summary-correction-target"
        private const val STAGE_ID = "ui-summary-correction-stage"
        private const val OWNED_ENTRY_ID = "ui-summary-correction-owned"
        private const val UNOWNED_ENTRY_ID = "ui-summary-correction-unowned"
        private const val TARGET_TITLE = "UI-汇总纠错"
        private lateinit var planningDate: String

        @JvmStatic
        @BeforeClass
        fun seedCorrectionStateBeforeActivityStarts() {
            planningDate = AttentionState().planningDate(Instant.now()).toString()
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    RoomBusinessDataRepository(database).replace(
                        AttentionState(
                            targets = listOf(Target(TARGET_ID, title = TARGET_TITLE)),
                            goalStages = listOf(GoalStage(STAGE_ID, TARGET_ID, GoalCadence.ONE_TIME, 30, planningDate)),
                            timeEntries = listOf(
                                TimeEntry(OWNED_ENTRY_ID, planningDate, 10, TARGET_ID),
                                TimeEntry(UNOWNED_ENTRY_ID, planningDate, 5),
                            ),
                        ),
                    )
                }
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun summary_refreshes_after_edit_assignment_and_delete() {
        openTargetDetails()
        composeRule.onAllNodesWithText("一次性阶段汇总：实际 10/30 分钟", substring = true).assertCountEquals(2)

        composeRule.onNodeWithText("全选").performClick()
        composeRule.onNodeWithText("归入当前计划").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("一次性阶段汇总：实际 15/30 分钟", substring = true).assertCountEquals(2)

        composeRule.onNode(hasText("今天") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit-time-$OWNED_ENTRY_ID").performScrollTo().performClick()
        composeRule.onNodeWithTag("edit-time-minutes-$OWNED_ENTRY_ID", useUnmergedTree = true).performScrollTo().performTextReplacement("20")
        composeRule.onNodeWithTag("save-time-$OWNED_ENTRY_ID").performClick()
        composeRule.waitForIdle()
        openTargetDetails()
        composeRule.onAllNodesWithText("一次性阶段汇总：实际 25/30 分钟", substring = true).assertCountEquals(2)

        composeRule.onNode(hasText("今天") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("delete-time-$OWNED_ENTRY_ID").performScrollTo().performClick()
        composeRule.waitForIdle()
        openTargetDetails()
        composeRule.onAllNodesWithText("一次性阶段汇总：实际 5/30 分钟", substring = true).assertCountEquals(2)
    }

    private fun openTargetDetails() {
        composeRule.onNode(hasText("目标树") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("添加子计划").performClick()
        composeRule.waitForIdle()
    }
}
