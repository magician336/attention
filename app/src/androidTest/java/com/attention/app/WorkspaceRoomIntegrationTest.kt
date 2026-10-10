package com.attention.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
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
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceRoomIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val TARGET_ID = "ui-append-target"
        private const val STAGE_ID = "ui-append-stage"
        private const val ENTRY_ID = "ui-append-entry"
        private const val TARGET_TITLE = "UI-一次性阶段"

        @JvmStatic
        @BeforeClass
        fun seedCompletedOneTimeTargetBeforeActivityStarts() {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                val startDate = LocalDate.now().minusDays(2).toString()
                runBlocking {
                    RoomBusinessDataRepository(database).replace(
                        AttentionState(
                            targets = listOf(Target(TARGET_ID, title = TARGET_TITLE)),
                            goalStages = listOf(GoalStage(STAGE_ID, TARGET_ID, GoalCadence.ONE_TIME, 30, startDate)),
                            timeEntries = listOf(TimeEntry(ENTRY_ID, startDate, 30, TARGET_ID)),
                        ),
                    )
                }
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun target_tree_screen_is_available_from_room_backed_workspace() {
        composeRule.onNode(hasText("目标树") and hasClickAction()).performClick()
        composeRule.onNodeWithText("父目标显示整个子树的实际投入；归档目标会从默认列表隐藏。").assertExists()
    }

    @Test
    fun agenda_screen_is_available_from_room_backed_workspace() {
        composeRule.onNode(hasText("所有日期") and hasClickAction()).performClick()
        composeRule.onNodeWithText("日程条目可带预计时长；它不会自动变成实际投入。").assertExists()
    }

    @Test
    fun timer_controls_are_available_from_room_backed_workspace() {
        composeRule.onNode(hasText("今天") and hasClickAction()).performClick()
        composeRule.onNodeWithText("计时器").assertExists()
        composeRule.onNodeWithText("开始未归属计时").assertExists()
    }

    @Test
    fun completed_one_time_stage_can_be_appended_from_target_details() {
        openTargetDetails()
        composeRule.onNodeWithTag("append-goal-submit").performClick()
        composeRule.onAllNodesWithText("一次性目标 30/30 分钟", substring = true).assertCountEquals(2)

        composeRule.onNodeWithTag("append-goal-minutes").performTextInput("45")
        composeRule.onNodeWithTag("append-goal-submit").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("一次性目标 0/45 分钟", substring = true).assertCountEquals(2)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        openTargetDetails(expectAppend = false)
        composeRule.onAllNodesWithText("一次性目标 0/45 分钟", substring = true).assertCountEquals(2)
    }

    private fun openTargetDetails(expectAppend: Boolean = true) {
        composeRule.onNode(hasText("目标树") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TARGET_TITLE).assertExists()
        composeRule.onNodeWithText("添加子计划").performClick()
        composeRule.waitForIdle()
        if (expectAppend) composeRule.onNodeWithText("追加阶段").assertExists()
    }
}
