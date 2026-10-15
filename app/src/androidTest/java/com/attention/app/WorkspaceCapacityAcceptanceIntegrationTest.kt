package com.attention.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
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
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceCapacityAcceptanceIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun stubDocumentPickerIntents() {
        Intents.init()
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
    }

    @After
    fun releaseIntentStubs() {
        Intents.release()
    }

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
        waitForText("已安排 61 / 60 分钟（超载）")
        composeRule.onNodeWithText("UI-新增超载").assertExists()

        // Editing remains available while overloaded, and the summary follows the edit.
        click("schedule-edit-$ESTIMATED_ID")
        replace("schedule-edit-estimate-$ESTIMATED_ID", "31")
        click("schedule-edit-save-$ESTIMATED_ID")
        waitForText("已安排 62 / 60 分钟（超载）")

        // Completing a schedule is still saved and does not change reserved capacity.
        click("schedule-completion-$RANGE_ID")
        waitForText("已完成")
        waitForText("已安排 62 / 60 分钟（超载）")

        // Reducing the edited entry removes the overload notice without touching its date.
        click("schedule-edit-$ESTIMATED_ID")
        replace("schedule-edit-estimate-$ESTIMATED_ID", "29")
        click("schedule-edit-save-$ESTIMATED_ID")
        waitForText("已安排 60 / 60 分钟")

        // Date switching and the all-dates grouping continue to show independent totals.
        selectDate(SECONDARY_DATE)
        composeRule.onNodeWithTag("agenda-capacity-summary", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 45 / 60 分钟", substring = true))
        selectAllDates()
        composeRule.onNodeWithTag("agenda-capacity-summary-$PRIMARY_DATE", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 60 / 60 分钟", substring = true))

        // JSON backup remains user-visible: #173 moved it off the statistics page into the
        // standalone settings page reached from 「设置与数据」.
        openSettingsFromStatistics()
        composeRule.onNodeWithText("预览 JSON 备份").performScrollTo().performClick()
        // The export launches the system document picker in its own process; the stubbed
        // intent keeps the round trip in-process so the Compose hierarchy stays alive.
        waitForText("JSON 预览：")
        waitForText("已取消保存 JSON 备份")
        intended(
            allOf(
                hasAction(Intent.ACTION_CREATE_DOCUMENT),
                hasType("application/json"),
                hasExtra(Intent.EXTRA_TITLE, "attention-backup.json"),
            ),
        )
    }

    @Test
    fun restore_import_backup_requires_explicit_confirmation() {
        // 恢复入口随「设置与数据」搬到了设置页，确认对话框与取消行为不变（#173）
        openSettingsFromStatistics()
        composeRule.onNodeWithText("恢复最近一次导入前备份").performScrollTo().performClick()

        composeRule.onNodeWithTag("restore-import-dialog", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("restore-import-confirm", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("restore-import-cancel", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("restore-import-dialog", useUnmergedTree = true).assertDoesNotExist()
    }

    /** 从统计页底部的「设置与数据」进入独立设置页。 */
    private fun openSettingsFromStatistics() {
        composeRule.onNode(hasText("统计") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("statistics-open-settings").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun openAgenda() {
        // 「所有日期」现在是日程页里的一个日期范围，不再是底部的顶级入口
        composeRule.onNodeWithTag("nav-agenda", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-range-all-dates", useUnmergedTree = true).performScrollTo().performClick()
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
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
        if (tag.startsWith("schedule-edit-save-")) {
            node.performClick()
        } else {
            node.performScrollTo().performClick()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
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
