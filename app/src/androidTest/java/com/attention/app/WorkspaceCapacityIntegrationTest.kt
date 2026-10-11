package com.attention.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.domain.AttentionState
import com.attention.domain.ScheduleEntry
import com.attention.domain.StoredSettings
import com.attention.domain.planningDate
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceCapacityIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun capacity_rejects_invalid_input_preserves_value_and_clears_across_recreation() {
        var capacityInput = composeRule.onNodeWithTag("daily-capacity-input", useUnmergedTree = true).performScrollTo()
        var saveButton = composeRule.onNodeWithTag("daily-capacity-save", useUnmergedTree = true).performScrollTo()
        var summary = composeRule.onNodeWithTag("daily-capacity-summary", useUnmergedTree = true).performScrollTo()

        capacityInput.performTextReplacement("60")
        saveButton.performClick()
        composeRule.waitForIdle()
        summary.assert(hasText("已安排 90 / 60 分钟（超载）", substring = true))

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        capacityInput = composeRule.onNodeWithTag("daily-capacity-input", useUnmergedTree = true).performScrollTo()
        saveButton = composeRule.onNodeWithTag("daily-capacity-save", useUnmergedTree = true).performScrollTo()
        summary = composeRule.onNodeWithTag("daily-capacity-summary", useUnmergedTree = true).performScrollTo()
        summary.assert(hasText("已安排 90 / 60 分钟（超载）", substring = true))

        listOf("0", "-5", "abc").forEach { invalidInput ->
            capacityInput.performTextReplacement(invalidInput)
            saveButton.performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("daily-capacity-error", useUnmergedTree = true).assert(hasText("正整数", substring = true))
            summary.assert(hasText("已安排 90 / 60 分钟（超载）", substring = true))
        }

        capacityInput.performTextReplacement("")
        saveButton.performClick()
        composeRule.waitForIdle()
        summary.assert(hasText("已安排 90 分钟", substring = true))
        summary.assert(hasText("未设置", substring = true))

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("daily-capacity-summary", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 90 分钟", substring = true))
            .assert(hasText("未设置", substring = true))
    }

    companion object {
        private const val SCHEDULE_ID = "ui-capacity-schedule"

        @JvmStatic
        @BeforeClass
        fun seedScheduleBeforeActivityStarts() {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val date = AttentionState(settings = StoredSettings()).planningDate(Instant.now(), ZoneId.systemDefault()).toString()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    RoomBusinessDataRepository(database).replace(
                        AttentionState(
                            schedules = listOf(ScheduleEntry(SCHEDULE_ID, date, "UI-容量", estimatedMinutes = 90)),
                        ),
                    )
                }
            } finally {
                database.close()
            }
        }
    }
}
