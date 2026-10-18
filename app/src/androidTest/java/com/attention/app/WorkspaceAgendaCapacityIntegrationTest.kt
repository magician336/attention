package com.attention.app

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.createSettingsStore
import com.attention.domain.AttentionState
import com.attention.domain.ScheduleEntry
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceAgendaCapacityIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // 被销毁的 Activity 仍可能在收尾时写回「上次打开」：等写入停稳再放行下一条用例。
    @get:Rule(order = 1)
    val settingsBarrier = WorkspaceHostSettingsBarrier.quiesceAfterActivity

    @Test
    fun agenda_shows_capacity_per_planning_day_without_combining_all_dates() {
        openAgenda()

        composeRule.onNodeWithTag("agenda-capacity-summary-2026-10-08", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 45 / 60 分钟", substring = true))
        composeRule.onNodeWithTag("agenda-capacity-summary-2026-10-09", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 30 / 60 分钟", substring = true))

        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-date-2026-10-08", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("agenda-open-settings", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 45 / 60 分钟", substring = true))

        runBlocking {
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .createSettingsStore()
                .setSnapshot(SettingsSnapshot(dailyCapacityMinutes = null, onboardingCompleted = true))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-open-settings", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 45 分钟", substring = true))
            .assert(hasText("未设置", substring = true))

        runBlocking {
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .createSettingsStore()
                .setSnapshot(SettingsSnapshot(dailyCapacityMinutes = 60, onboardingCompleted = true))
        }
    }

    private fun openAgenda() {
        // 「所有日期」现在是日程页里的一个日期范围，不再是底部的顶级入口
        composeRule.onNodeWithTag("nav-agenda", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-range-all-dates", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun seedAgenda() {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    RoomBusinessDataRepository(database).replace(
                        AttentionState(
                            schedules = listOf(
                                ScheduleEntry("ui-agenda-capacity-08", "2026-10-08", "议程容量一", estimatedMinutes = 45),
                                ScheduleEntry("ui-agenda-capacity-09", "2026-10-09", "议程容量二", estimatedMinutes = 30),
                            ),
                        ),
                    )
                    context.createSettingsStore().setSnapshot(
                        SettingsSnapshot(dailyCapacityMinutes = 60, onboardingCompleted = true),
                    )
                }
            } finally {
                database.close()
            }
        }
    }
}
