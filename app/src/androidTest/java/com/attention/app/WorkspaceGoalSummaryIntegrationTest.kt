package com.attention.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

@RunWith(AndroidJUnit4::class)
class WorkspaceGoalSummaryIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val ROOT_ID = "ui-summary-root"
        private const val CHILD_ID = "ui-summary-child"
        private const val GRANDCHILD_ID = "ui-summary-grandchild"
        private const val ROOT_FIRST_STAGE_ID = "ui-summary-root-first-stage"
        private const val ROOT_SECOND_STAGE_ID = "ui-summary-root-second-stage"
        private const val CHILD_STAGE_ID = "ui-summary-child-stage"
        private const val ROOT_TITLE = "UI-阶段汇总"
        private const val CHILD_TITLE = "UI-子目标"

        @JvmStatic
        @BeforeClass
        fun seedHierarchyBeforeActivityStarts() {
            val planningDate = AttentionState().planningDate(Instant.now())
            val firstStart = planningDate.minusDays(10)
            val firstEnd = planningDate.minusDays(8)
            val secondStart = planningDate.minusDays(5)
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    RoomBusinessDataRepository(database).replace(
                        AttentionState(
                            targets = listOf(
                                Target(ROOT_ID, title = ROOT_TITLE),
                                Target(CHILD_ID, parentId = ROOT_ID, title = CHILD_TITLE),
                                Target(GRANDCHILD_ID, parentId = CHILD_ID, title = "UI-孙目标"),
                            ),
                            goalStages = listOf(
                                GoalStage(ROOT_FIRST_STAGE_ID, ROOT_ID, GoalCadence.ONE_TIME, 60, firstStart.toString(), firstEnd.toString()),
                                GoalStage(ROOT_SECOND_STAGE_ID, ROOT_ID, GoalCadence.ONE_TIME, 90, secondStart.toString()),
                                GoalStage(CHILD_STAGE_ID, CHILD_ID, GoalCadence.ONE_TIME, 30, firstStart.toString()),
                            ),
                            timeEntries = listOf(
                                TimeEntry("ui-summary-root-entry", firstStart.toString(), 20, ROOT_ID),
                                TimeEntry("ui-summary-child-entry", firstStart.plusDays(1).toString(), 15, CHILD_ID),
                                TimeEntry("ui-summary-grandchild-first-entry", firstEnd.toString(), 25, GRANDCHILD_ID),
                                TimeEntry("ui-summary-child-second-entry", secondStart.toString(), 40, CHILD_ID),
                                TimeEntry("ui-summary-grandchild-second-entry", secondStart.plusDays(1).toString(), 50, GRANDCHILD_ID),
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
    fun target_tree_and_statistics_share_deduplicated_parent_and_child_summaries() {
        composeRule.onNode(hasText("目标树") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(ROOT_TITLE).assertExists()
        composeRule.onNodeWithText("一次性阶段汇总：实际 150/150 分钟", substring = true).assertExists()
        composeRule.onNodeWithText("一次性阶段汇总：实际 130/30 分钟", substring = true).assertExists()

        composeRule.onNode(hasText("统计") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("一次性阶段汇总：实际 150/150 分钟", substring = true).assertExists()
        composeRule.onNodeWithText("一次性阶段汇总：实际 130/30 分钟", substring = true).assertExists()
    }
}
