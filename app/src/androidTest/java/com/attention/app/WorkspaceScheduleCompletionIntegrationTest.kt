package com.attention.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.domain.AttentionState
import com.attention.domain.Milestone
import com.attention.domain.ScheduleEntry
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceScheduleCompletionIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun completion_toggle_is_visible_after_reentering_and_keeps_actual_investment_independent() {
        openAgenda()
        composeRule.onNodeWithTag("schedule-completion-$SCHEDULE_ID").performClick()
        composeRule.waitForIdle()

        val completed = readBusinessState()
        assertTrue(completed.schedules.single { it.id == SCHEDULE_ID }.completed)
        assertEquals(listOf(TimeEntry(TIME_ENTRY_ID, DATE, 20, TARGET_ID)), completed.timeEntries)
        assertEquals(1234L, completed.experience)
        assertEquals(listOf(Milestone(MILESTONE_ID, "goal_period", "period", 500L, 1L)), completed.milestones)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        openAgenda()
        composeRule.onNodeWithText("取消完成").assertExists()
        composeRule.onNodeWithText("取消完成").performClick()
        composeRule.waitForIdle()

        val reopened = readBusinessState()
        assertFalse(reopened.schedules.single { it.id == SCHEDULE_ID }.completed)
        assertEquals(completed.timeEntries, reopened.timeEntries)
        assertEquals(completed.experience, reopened.experience)
        assertEquals(completed.milestones, reopened.milestones)
    }

    private fun openAgenda() {
        composeRule.onNode(hasText("所有日期") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(SCHEDULE_TITLE).assertExists()
    }

    private fun readBusinessState(): AttentionState {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = AttentionDatabase.create(context)
        return try {
            runBlocking { RoomBusinessDataRepository(database).read() }
        } finally {
            database.close()
        }
    }

    companion object {
        private const val TARGET_ID = "ui-completion-target"
        private const val SCHEDULE_ID = "ui-completion-schedule"
        private const val TIME_ENTRY_ID = "ui-completion-entry"
        private const val MILESTONE_ID = "ui-completion-milestone"
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
                            timeEntries = listOf(TimeEntry(TIME_ENTRY_ID, DATE, 20, TARGET_ID)),
                            schedules = listOf(ScheduleEntry(SCHEDULE_ID, DATE, SCHEDULE_TITLE, estimatedMinutes = 30, targetId = TARGET_ID)),
                            milestones = listOf(Milestone(MILESTONE_ID, "goal_period", "period", 500L, 1L)),
                            experience = 1234L,
                        ),
                    )
                }
            } finally {
                database.close()
            }
        }
    }
}
