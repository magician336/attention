package com.attention.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.domain.AttentionState
import com.attention.domain.ScheduleEntry
import com.attention.domain.Target
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceScheduleCompletionIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // 被销毁的 Activity 仍可能在收尾时写回「上次打开」：等写入停稳再放行下一条用例。
    @get:Rule(order = 1)
    val settingsBarrier = WorkspaceHostSettingsBarrier.quiesceAfterActivity

    @Test
    fun completion_toggle_is_available_from_the_agenda_row() {
        openAgenda()
        composeRule.onNodeWithTag("schedule-completion-$SCHEDULE_ID", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("已完成", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = AttentionDatabase.create(context)
        try {
            val loaded = runBlocking { RoomBusinessDataRepository(database).read() }
            assertEquals(true, loaded.schedules.single { it.id == SCHEDULE_ID }.completed)
        } finally {
            database.close()
        }
    }

    private fun openAgenda() {
        // 「所有日期」现在是日程页里的一个日期范围，不再是底部的顶级入口
        composeRule.onNode(hasText("日程") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-range-all-dates", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(SCHEDULE_TITLE).assertExists()
    }

    companion object {
        private const val TARGET_ID = "ui-completion-target"
        private const val SCHEDULE_ID = "ui-completion-schedule"
        private const val SCHEDULE_TITLE = "UI-日程完成独立性"
        private const val DATE = "2026-10-08"

        @JvmStatic
        @BeforeClass
        fun seedScheduleBeforeActivityStarts() {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    RoomBusinessDataRepository(database).replace(
                        AttentionState(
                            targets = listOf(Target(TARGET_ID, title = "UI-目标")),
                            schedules = listOf(ScheduleEntry(SCHEDULE_ID, DATE, SCHEDULE_TITLE, estimatedMinutes = 30, targetId = TARGET_ID)),
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
}
