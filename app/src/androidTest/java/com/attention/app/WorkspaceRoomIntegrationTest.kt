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

    // 被销毁的 Activity 仍可能在收尾时写回「上次打开」：等写入停稳再放行下一条用例。
    @get:Rule(order = 1)
    val settingsBarrier = WorkspaceHostSettingsBarrier.quiesceAfterActivity

    companion object {
        private const val TARGET_ID = "ui-append-target"
        private const val STAGE_ID = "ui-append-stage"
        private const val ENTRY_ID = "ui-append-entry"
        private const val TARGET_TITLE = "UI-一次性阶段"
        private const val TIMEOUT = 10_000L

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
            // #177：未完成引导时第一次启动是全屏引导；这条用例要看的是引导之后的工作区
            WorkspaceGuideSeed.markCompleted()
        }
    }

    @Test
    fun target_tree_screen_is_available_from_room_backed_workspace() {
        composeRule.onNode(hasText("目标树") and hasClickAction()).performClick()
        composeRule.onNodeWithText("父目标显示整个子树的实际投入；归档目标会从默认列表隐藏。").assertExists()
    }

    @Test
    fun agenda_screen_is_available_from_room_backed_workspace() {
        composeRule.onNodeWithTag("nav-agenda", useUnmergedTree = true).performClick()
        // Prototype A keeps the compact date filter as the agenda entry point;
        // the former hero explanation is intentionally absent.
        composeRule.onNodeWithTag("agenda-filter", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("日程条目可带预计时长；它不会自动变成实际投入。").assertDoesNotExist()
    }

    @Test
    fun the_merged_agenda_page_replaces_the_workbench_with_date_ranges() {
        // 今天工作台的计时卡、快速记录与每日容量卡片本次下线：日程页只有日期范围与日程时间轴
        composeRule.onNodeWithTag("nav-agenda", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("agenda-range-today", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("agenda-range-tomorrow", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("agenda-range-all-dates", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("开始未归属计时").assertDoesNotExist()
        composeRule.onNodeWithText("快速记录实际投入").assertDoesNotExist()
    }

    @Test
    fun completed_one_time_stage_can_be_appended_from_target_details() {
        openTargetDetails()
        composeRule.onNodeWithTag("append-goal-submit").performClick()
        composeRule.onAllNodesWithText("一次性目标 30/30 分钟", substring = true).assertCountEquals(2)

        composeRule.onNodeWithTag("append-goal-minutes").performTextInput("45")
        composeRule.onNodeWithTag("append-goal-submit").performClick()
        // 追加阶段是一次异步写入：waitForIdle() 可能在 Room 写回流并触发重组之前就返回，
        // 于是新阶段还没出现就断言。这里等目标真的出现（#185 前的一次间歇失败）。
        composeRule.waitUntil(TIMEOUT) {
            runCatching {
                composeRule.onAllNodesWithText("一次性目标 0/45 分钟", substring = true).fetchSemanticsNodes()
            }.getOrDefault(emptyList()).isNotEmpty()
        }

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
