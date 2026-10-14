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
import com.attention.app.data.RoomAttentionStateRepository
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.createSettingsStore
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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import com.attention.app.ui.components.AttentionFeedbackTags

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
            val state = AttentionState()
            planningDate = state.planningDate(Instant.now()).toString()
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    RoomAttentionStateRepository(
                        business = RoomBusinessDataRepository(database),
                        settings = context.createSettingsStore(),
                    ).replace(
                        state.copy(
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
        assertSummary(10)

        composeRule.onNodeWithText("全选").performClick()
        composeRule.onNodeWithText("归入当前计划").performClick()
        composeRule.waitForIdle()
        assertSummary(15)

        composeRule.onNode(hasText("今天") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit-time-$OWNED_ENTRY_ID").performScrollTo().performClick()
        composeRule.onNodeWithTag("edit-time-minutes-$OWNED_ENTRY_ID", useUnmergedTree = true).performScrollTo().performTextReplacement("20")
        composeRule.onNodeWithTag("save-time-$OWNED_ENTRY_ID").performClick()
        composeRule.waitForIdle()
        openTargetDetails()
        assertSummary(25)

        composeRule.onNode(hasText("今天") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("delete-time-$OWNED_ENTRY_ID").performScrollTo().performClick()
        composeRule.waitForIdle()
        // Deleting a time record is destructive, so it is confirmed before anything changes.
        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG_CONFIRM, useUnmergedTree = true).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("delete-time-$OWNED_ENTRY_ID").fetchSemanticsNodes().isEmpty()
        }
        openTargetDetails()
        assertSummary(5)
    }

    private fun assertSummary(actualMinutes: Int) {
        val summary = "一次性阶段汇总：实际 $actualMinutes/30 分钟"
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(summary, substring = true).fetchSemanticsNodes().size == 2
        }
        composeRule.onAllNodesWithText(summary, substring = true).assertCountEquals(2)
    }

    private fun openTargetDetails() {
        composeRule.onNode(hasText("目标树") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("添加子计划").performClick()
        composeRule.waitForIdle()
    }
}
