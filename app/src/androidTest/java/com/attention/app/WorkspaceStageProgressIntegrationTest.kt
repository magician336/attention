package com.attention.app

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
import com.attention.domain.TimeEntrySource
import com.attention.domain.planningDate
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.assertCountEquals

@RunWith(AndroidJUnit4::class)
class WorkspaceStageProgressIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val TARGET_ID = "ui-stage-range-target"
        private const val FIRST_STAGE_ID = "ui-stage-range-first"
        private const val SECOND_STAGE_ID = "ui-stage-range-second"
        private const val THIRD_STAGE_ID = "ui-stage-range-third"
        private const val FIRST_ENTRY_ID = "ui-stage-range-first-entry"
        private const val EXCESS_ENTRY_ID = "ui-stage-range-excess-entry"
        private const val TARGET_TITLE = "UI-阶段范围"
        private lateinit var firstStart: String
        private lateinit var firstEnd: String
        private lateinit var secondStart: String

        @JvmStatic
        @BeforeClass
        fun seedIndependentStagesBeforeActivityStarts() {
            val planningDate = AttentionState().planningDate(Instant.now())
            firstStart = planningDate.minusDays(2).toString()
            firstEnd = planningDate.minusDays(1).toString()
            secondStart = planningDate.toString()
            val thirdStart = planningDate.plusDays(1).toString()
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    RoomBusinessDataRepository(database).replace(
                        AttentionState(
                            targets = listOf(Target(TARGET_ID, title = TARGET_TITLE)),
                            goalStages = listOf(
                                GoalStage(FIRST_STAGE_ID, TARGET_ID, GoalCadence.ONE_TIME, 30, firstStart),
                                GoalStage(SECOND_STAGE_ID, TARGET_ID, GoalCadence.ONE_TIME, 40, secondStart),
                                GoalStage(THIRD_STAGE_ID, TARGET_ID, GoalCadence.ONE_TIME, 50, thirdStart),
                            ),
                            timeEntries = listOf(
                                TimeEntry(FIRST_ENTRY_ID, firstStart, 30, TARGET_ID, TimeEntrySource.MANUAL),
                                TimeEntry(EXCESS_ENTRY_ID, firstEnd, 5, TARGET_ID, TimeEntrySource.IMPORT),
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
    fun target_and_statistics_screens_show_independent_stage_ranges_and_statuses() {
        composeRule.onNode(hasText("目标树") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TARGET_TITLE).assertExists()
        composeRule.onNodeWithText("添加子计划").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("一次性目标 35/30 分钟", substring = true).assertCountEquals(2)
        composeRule.onAllNodesWithText("范围 $firstStart 至 $firstEnd", substring = true).assertCountEquals(2)
        composeRule.onAllNodesWithText("阶段封闭于 $firstEnd", substring = true).assertCountEquals(2)
        composeRule.onAllNodesWithText("超额 5 分钟", substring = true).assertCountEquals(2)
        composeRule.onAllNodesWithText(
            "一次性目标 0/40 分钟 · 范围 $secondStart 至 $secondStart · 开放 · 缺口 40 分钟",
            substring = true,
        ).assertCountEquals(2)
        composeRule.onAllNodesWithText("一次性目标 0/50 分钟", substring = true).assertCountEquals(2)
        composeRule.onAllNodesWithText("尚未开始", substring = true).assertCountEquals(2)

        composeRule.onNode(hasText("统计") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("范围 $firstStart 至 $firstEnd", substring = true).assertExists()
        composeRule.onNodeWithText("阶段封闭于 $firstEnd", substring = true).assertExists()
        composeRule.onNodeWithText("超额 5 分钟", substring = true).assertExists()
    }
}
