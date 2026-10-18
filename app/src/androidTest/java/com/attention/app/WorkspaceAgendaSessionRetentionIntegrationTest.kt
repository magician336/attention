package com.attention.app

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import com.attention.domain.Target
import com.attention.domain.planningDate
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 日程页日期筛选的会话保留与重启重置（#176）。
 *
 * 这条用例走真实的 Workspace 宿主路径：会话内保留通过在底部导航之间来回切换来表达，
 * 重启重置通过重建 Activity（组合根重建、新建一个选择状态实例）来模拟进程重启。
 *
 * 重建的是宿主而不是选择状态，因此它验证的是「组合根新建实例即重置」这条真实路径，
 * 而不是直接断言某个内部变量。
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceAgendaSessionRetentionIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // 被销毁的 Activity 仍可能在收尾时写回「上次打开」：等写入停稳再放行下一条用例。
    @get:Rule(order = 1)
    val settingsBarrier = WorkspaceHostSettingsBarrier.quiesceAfterActivity

    /** 会话内：切到目标树再回到日程，仍然停在正在处理的那个日期范围。 */
    @Test
    fun the_date_range_survives_a_round_trip_to_the_target_tree() {
        selectTomorrow()
        assertAgendaShowsPlanningDay(TOMORROW)

        openWorkspace("nav-targets")
        composeRule.onNodeWithTag("nav-agenda", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertAgendaShowsPlanningDay(TOMORROW)
    }

    /** 会话内：切到统计再回到日程，同样保留此前的日期筛选。 */
    @Test
    fun the_date_range_survives_a_round_trip_to_statistics() {
        selectTomorrow()
        assertAgendaShowsPlanningDay(TOMORROW)

        openWorkspace("nav-statistics")
        composeRule.onNodeWithTag("nav-agenda", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertAgendaShowsPlanningDay(TOMORROW)
    }

    /** 「所有日期」是这个会话里选中的范围，同样在会话内保留。 */
    @Test
    fun the_all_dates_range_survives_a_round_trip_to_another_workspace() {
        openDateMenu()
        composeRule.onNodeWithTag("agenda-range-all-dates", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-filter").assert(hasText("筛选：所有日期"))

        openWorkspace("nav-targets")
        composeRule.onNodeWithTag("nav-agenda", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("agenda-filter").assert(hasText("筛选：所有日期"))
    }

    /** 重启：组合根重建后回到当前规划日，不恢复上一个会话遗留的日期筛选。 */
    @Test
    fun a_restart_returns_to_the_current_planning_day_instead_of_the_previous_range() {
        selectTomorrow()
        assertAgendaShowsPlanningDay(TOMORROW)

        // 重建宿主 = 重新启动应用：组合根新建一个选择状态实例
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        assertAgendaShowsPlanningDay(TODAY)
    }

    /** 重启：上一个会话选的是「所有日期」时，同样不残留。 */
    @Test
    fun a_restart_does_not_restore_a_previous_all_dates_range() {
        openDateMenu()
        composeRule.onNodeWithTag("agenda-range-all-dates", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        assertAgendaShowsPlanningDay(TODAY)
    }

    /** 重启：会话内保留不写入持久化，因此持久化状态里没有留下任何日期筛选。 */
    @Test
    fun the_session_range_is_never_written_to_persistence() {
        // 一个明显不属于当前规划日的范围：任何残留都会立刻显形
        openDateMenu()
        composeRule.onNode(hasSetTextAction()).performTextReplacement(FAR_DATE)
        composeRule.onNodeWithTag("agenda-open-planning-day", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertAgendaShowsPlanningDay(FAR_DATE)

        // 会话内切换页面不落盘
        openWorkspace("nav-targets")
        val afterSwitching = readSettings()
        composeRule.onNodeWithTag("nav-agenda", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertAgendaShowsPlanningDay(FAR_DATE)

        // 持久化状态里只有设置，没有任何位置记着 FAR_DATE 这个筛选
        assertEquals(false, afterSwitching.contains(FAR_DATE))
        assertEquals(false, readSettings().contains(FAR_DATE))
        assertEquals(false, readState().schedules.any { it.planningDate == FAR_DATE })

        // 重建宿主后它也没有回来
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        assertAgendaShowsPlanningDay(TODAY)
    }

    /** 打开日期菜单：三个范围与任意规划日输入都收在这个菜单里。 */
    private fun openDateMenu() {
        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun selectTomorrow() {
        openDateMenu()
        composeRule.onNodeWithTag("agenda-range-tomorrow", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    private fun openWorkspace(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    private fun assertAgendaShowsPlanningDay(date: String) {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-filter").assert(hasText("筛选：规划日 $date"))
    }

    companion object {
        private const val TARGET_ID = "ui-session-target"
        private const val TODAY_ENTRY_ID = "ui-session-today"
        private const val FAR_DATE = "2026-12-24"

        private lateinit var TODAY: String
        private lateinit var TOMORROW: String

        private fun repository(database: AttentionDatabase) = RoomAttentionStateRepository(
            business = RoomBusinessDataRepository(database),
            settings = ApplicationProvider.getApplicationContext<android.content.Context>().createSettingsStore(),
        )

        @JvmStatic
        @BeforeClass
        fun seedAgendaBeforeActivityStarts() {
            val state = AttentionState()
            TODAY = state.planningDate(Instant.now()).toString()
            TOMORROW = state.planningDate(Instant.now()).plusDays(1).toString()
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    repository(database).replace(
                        state.copy(
                            targets = listOf(Target(TARGET_ID, title = "UI-会话目标")),
                            schedules = listOf(
                                ScheduleEntry(TODAY_ENTRY_ID, TODAY, "UI-今日条目", estimatedMinutes = 30, targetId = TARGET_ID),
                            ),
                        ),
                    )
                }
                // 共享 DataStore 会被别的测试类的 Activity 写回：播种后读回确认才算数。
                WorkspaceHostSettingsBarrier.quiesceAndSeed(
                    SettingsSnapshot(onboardingCompleted = true),
                )
            } finally {
                database.close()
            }
        }

        /** 持久化设置快照的文本形式，用来断言日期筛选没有被写进 DataStore。 */
        private fun readSettings(): String = runBlocking {
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .createSettingsStore()
                .settings
                .first()
                .toString()
        }

        /** 读一次真实持久化领域状态。 */
        private fun readState() = runBlocking {
            val database = AttentionDatabase.create(ApplicationProvider.getApplicationContext())
            try {
                repository(database).read()
            } finally {
                database.close()
            }
        }
    }
}
