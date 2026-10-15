package com.attention.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.RoomAttentionStateRepository
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.createSettingsStore
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

    // 被销毁的 Activity 仍可能在收尾时写回「上次打开」：等写入停稳再放行下一条用例。
    @get:Rule(order = 1)
    val settingsBarrier = WorkspaceHostSettingsBarrier.quiesceAfterActivity

    /**
     * 今天工作台的每日容量卡片本次下线（最终形态由 #128/#131 决定），
     * 因此这条用例钉住的是仍然存在的那一半：合并后的日程页按规划日展示每日容量，
     * 并且在 Activity 重建后仍然读到同一份设置。
     */
    @Test
    fun the_agenda_page_keeps_showing_the_planning_day_capacity_across_recreation() {
        var summary = composeRule.onNodeWithTag("agenda-capacity-summary", useUnmergedTree = true).performScrollTo()
        summary.assert(hasText("已安排 90 / 60 分钟（超载）", substring = true))

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        waitForText("已安排 90 / 60 分钟（超载）")
        summary = composeRule.onNodeWithTag("agenda-capacity-summary", useUnmergedTree = true).performScrollTo()
        summary.assert(hasText("已安排 90 / 60 分钟（超载）", substring = true))

        runBlocking {
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .createSettingsStore()
                .setSnapshot(SettingsSnapshot(dailyCapacityMinutes = null, onboardingCompleted = true))
        }
        composeRule.waitForIdle()
        waitForText("已安排 90 分钟（每日容量未设置）")
        composeRule.onNodeWithTag("agenda-capacity-summary", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasText("已安排 90 分钟", substring = true))
            .assert(hasText("未设置", substring = true))
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    companion object {
        private const val SCHEDULE_ID = "ui-capacity-schedule"

        @JvmStatic
        @BeforeClass
        fun seedScheduleBeforeActivityStarts() {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val state = AttentionState(settings = StoredSettings())
            val date = state.planningDate(Instant.now(), ZoneId.systemDefault()).toString()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    RoomAttentionStateRepository(
                        business = RoomBusinessDataRepository(database),
                        settings = context.createSettingsStore(),
                    ).replace(
                        state.copy(
                            schedules = listOf(ScheduleEntry(SCHEDULE_ID, date, "UI-容量", estimatedMinutes = 90)),
                        ),
                    )
                    // replace 会写入这份状态里的规划设置，因此每日容量必须在它之后写。
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
