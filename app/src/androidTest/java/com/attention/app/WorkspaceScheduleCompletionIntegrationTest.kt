package com.attention.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.domain.AttentionState
import com.attention.domain.ScheduleEntry
import com.attention.domain.Target
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceScheduleCompletionIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun completion_toggle_is_available_from_the_agenda_row() {
        openAgenda()
        composeRule.onNodeWithTag("schedule-completion-$SCHEDULE_ID").performClick()
        composeRule.waitForIdle()
    }

    private fun openAgenda() {
        composeRule.onNode(hasText("所有日期") and hasClickAction()).performClick()
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
        }
    }
}
