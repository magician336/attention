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
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import com.attention.domain.awardEligibleMilestones
import com.attention.domain.planningDate
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceStageFeedbackIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val ACTIVE_ID = "ui-feedback-active"
        private const val ARCHIVED_ID = "ui-feedback-archived"
        private const val FIRST_STAGE_ID = "ui-feedback-first-stage"
        private const val SECOND_STAGE_ID = "ui-feedback-second-stage"
        private const val ARCHIVED_STAGE_ID = "ui-feedback-archived-stage"
        private const val ACTIVE_TITLE = "UI-阶段反馈"
        private const val ARCHIVED_TITLE = "UI-归档反馈"
        private lateinit var firstStart: String
        private lateinit var secondStart: String
        private lateinit var archivedStart: String

        @JvmStatic
        @BeforeClass
        fun seedFeedbackBeforeActivityStarts() {
            val planningDate = AttentionState().planningDate(Instant.now())
            firstStart = planningDate.minusDays(3).toString()
            secondStart = planningDate.minusDays(1).toString()
            archivedStart = planningDate.minusDays(2).toString()
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    val state = AttentionState(
                        targets = listOf(
                            Target(ACTIVE_ID, title = ACTIVE_TITLE),
                            Target(ARCHIVED_ID, title = ARCHIVED_TITLE, archived = true),
                        ),
                        goalStages = listOf(
                            GoalStage(FIRST_STAGE_ID, ACTIVE_ID, GoalCadence.ONE_TIME, 30, firstStart),
                            GoalStage(SECOND_STAGE_ID, ACTIVE_ID, GoalCadence.ONE_TIME, 45, secondStart),
                            GoalStage(ARCHIVED_STAGE_ID, ARCHIVED_ID, GoalCadence.ONE_TIME, 20, archivedStart),
                        ),
                        timeEntries = listOf(
                            TimeEntry("ui-feedback-first-entry", firstStart, 30, ACTIVE_ID),
                            TimeEntry("ui-feedback-second-entry", secondStart, 45, ACTIVE_ID),
                            TimeEntry("ui-feedback-archived-entry", archivedStart, 20, ARCHIVED_ID),
                        ),
                    ).awardEligibleMilestones(planningDate)
                    RoomBusinessDataRepository(database).replace(state)
                }
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun stage_feedback_is_visible_for_active_and_archived_targets_and_stays_idempotent() {
        composeRule.onNode(hasText("目标树") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(ACTIVE_TITLE).assertExists()
        composeRule.onNodeWithText("添加子计划").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("阶段达成反馈：$firstStart · +10000 投入经验", substring = true).assertCountEquals(2)
        composeRule.onAllNodesWithText("阶段达成反馈：$secondStart · +10000 投入经验", substring = true).assertCountEquals(2)
        composeRule.onNodeWithText("阶段达成反馈：$archivedStart · +10000 投入经验", substring = true).assertExists()

        composeRule.onNode(hasText("统计") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("阶段达成反馈：$firstStart · +10000 投入经验", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("阶段达成反馈：$secondStart · +10000 投入经验", substring = true).assertCountEquals(1)
        composeRule.onNodeWithText("结算可达成里程碑").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("阶段达成反馈：$firstStart · +10000 投入经验", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("阶段达成反馈：$secondStart · +10000 投入经验", substring = true).assertCountEquals(1)
    }
}
