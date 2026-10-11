package com.attention.app

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.createSettingsStore
import com.attention.domain.AttentionState
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceCapacityAcceptanceIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun user_can_observe_and_adjust_capacity_across_agenda_and_workbench() {
        openAgenda()

        // The all-dates view keeps each planning day separate and includes a zero-cost entry.
        composeRule.onNodeWithTag("agenda-capacity-summary-$PRIMARY_DATE", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 60 / 60 分钟", substring = true))
        composeRule.onNodeWithTag("agenda-capacity-summary-$SECONDARY_DATE", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 45 / 60 分钟", substring = true))
        composeRule.onNodeWithText("UI-无时长日程").assertExists()
        composeRule.onNodeWithText("UI-重复发生条目").assertExists()

        selectDate(PRIMARY_DATE)
        composeRule.onNodeWithTag("agenda-capacity-summary", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 60 / 60 分钟", substring = true))

        // Adding an estimated-duration entry is allowed even when it makes the day overloaded.
        replace("agenda-title-input", "UI-新增超载")
        replace("agenda-estimate-input", "1")
        click("agenda-add-submit")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-capacity-summary", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 61 / 60 分钟（超载）", substring = true))
        composeRule.onNodeWithText("UI-新增超载").assertExists()

        // Editing remains available while overloaded, and the summary follows the edit.
        click("schedule-edit-$ESTIMATED_ID")
        replace("schedule-edit-estimate-$ESTIMATED_ID", "31")
        click("schedule-edit-save-$ESTIMATED_ID")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-capacity-summary", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 62 / 60 分钟（超载）", substring = true))

        // Completing a schedule is still saved and does not change reserved capacity.
        click("schedule-completion-$RANGE_ID")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-capacity-summary", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 62 / 60 分钟（超载）", substring = true))
        composeRule.onNodeWithText("已完成", substring = true).assertExists()

        // Reducing the edited entry removes the overload notice without touching its date.
        click("schedule-edit-$ESTIMATED_ID")
        replace("schedule-edit-estimate-$ESTIMATED_ID", "29")
        click("schedule-edit-save-$ESTIMATED_ID")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-capacity-summary", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 60 / 60 分钟", substring = true))

        // Date switching and the all-dates grouping continue to show independent totals.
        selectDate(SECONDARY_DATE)
        composeRule.onNodeWithTag("agenda-capacity-summary", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 45 / 60 分钟", substring = true))
        selectAllDates()
        composeRule.onNodeWithTag("agenda-capacity-summary-$PRIMARY_DATE", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 60 / 60 分钟", substring = true))

        // Workbench settings survive activity recreation, and JSON backup remains user-visible.
        composeRule.onNode(hasText("今天") and hasClickAction()).performClick()
        replace("planning-boundary-input", "240")
        click("daily-capacity-save")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("规划日界线 04:00 · 每周从周一开始").assertExists()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("规划日界线 04:00 · 每周从周一开始").assertExists()
        composeRule.onNodeWithTag("daily-capacity-summary", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("/ 60 分钟", substring = true))

        composeRule.onNode(hasText("统计") and hasClickAction()).performClick()
        composeRule.onNodeWithText("预览 JSON 备份").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("JSON 预览：", substring = true).assertExists()
    }

    private fun openAgenda() {
        composeRule.onNode(hasText("所有日期") and hasClickAction()).performClick()
        composeRule.waitForIdle()
    }

    private fun selectDate(date: String) {
        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-date-$date", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    private fun selectAllDates() {
        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-all-dates", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    private fun replace(tag: String, value: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().performTextReplacement(value)
    }

    private fun click(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().performClick()
    }

    companion object {
        private const val PRIMARY_DATE = "2026-10-08"
        private const val SECONDARY_DATE = "2026-10-09"
        private const val ESTIMATED_ID = "ui-acceptance-estimated"
        private const val RANGE_ID = "ui-acceptance-range"

        @JvmStatic
        @BeforeClass
        fun seedAcceptanceScenarioBeforeActivityStarts() {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    RoomBusinessDataRepository(database).replace(
                        AttentionState(
                            schedules = listOf(
                                ScheduleEntry(ESTIMATED_ID, PRIMARY_DATE, "UI-预计时长日程", estimatedMinutes = 30),
                                ScheduleEntry(RANGE_ID, PRIMARY_DATE, "UI-时间段日程", startMinute = 9 * 60, endMinute = 9 * 60 + 30),
                                ScheduleEntry("ui-acceptance-no-duration", PRIMARY_DATE, "UI-无时长日程"),
                                ScheduleEntry("ui-acceptance-secondary", SECONDARY_DATE, "UI-第二规划日", estimatedMinutes = 45),
                                ScheduleEntry("ui-acceptance-recurrence", SECONDARY_DATE, "UI-重复发生条目", estimatedMinutes = 0, recurrenceRuleId = "ui-acceptance-rule"),
                            ),
                            recurrenceRules = listOf(
                                RecurrenceRule(
                                    id = "ui-acceptance-rule",
                                    title = "UI-重复规则",
                                    startDate = SECONDARY_DATE,
                                    frequency = ScheduleFrequency.DAILY,
                                    estimatedMinutes = 0,
                                ),
                            ),
                        ),
                    )
                    context.createSettingsStore().setSnapshot(
                        SettingsSnapshot(
                            dailyCapacityMinutes = 60,
                            onboardingCompleted = true,
                        ),
                    )
                }
            } finally {
                database.close()
            }
        }
    }
}
