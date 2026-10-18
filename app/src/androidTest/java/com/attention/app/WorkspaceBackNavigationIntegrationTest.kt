package com.attention.app

import android.os.SystemClock
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoActivityResumedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 返回键行为的验收：主页面之间的切换不形成逐页返回栈，
 * 返回键优先关掉当前打开的日程详情、设置页与日期选择器，没有可关闭的层时才做系统级返回。
 *
 * 这条用例走真实的 Workspace 宿主路径并真的按下返回键，因此验证的是用户按到手感，
 * 而不是某个 Composable 的内部变量名。
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceBackNavigationIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // 被销毁的 Activity 仍可能在收尾时写回「上次打开」：等写入停稳再放行下一条用例。
    @get:Rule(order = 1)
    val settingsBarrier = WorkspaceHostSettingsBarrier.quiesceAfterActivity

    @Test
    fun several_workspace_switches_then_back_does_not_replay_the_tab_history() {
        // 在日程、目标树、统计之间来回切换多次
        listOf("nav-targets", "nav-statistics", "nav-agenda", "nav-statistics", "nav-targets").forEach { tag ->
            composeRule.onNodeWithTag(tag).performClick()
            composeRule.waitForIdle()
        }
        // 切换确实发生过：当前停在最后选中的目标树
        composeRule.onNodeWithTag("nav-targets").assertIsSelected()

        // 一次返回键既不回放到上一个标签，也不逐页往回走：它落到系统级返回
        pressBackUntilSystemBack()

        assertNotEquals(
            "底部导航切换不形成返回栈，返回键不应该把用户留在上一个标签页",
            Lifecycle.State.RESUMED,
            activityState(),
        )
    }

    @Test
    fun back_closes_the_open_settings_page_instead_of_leaving_the_app() {
        openSettingsFromStatistics()
        composeRule.onNodeWithTag("settings-back").assertIsDisplayed()

        Espresso.pressBack()
        composeRule.waitForIdle()

        // 设置页被返回键关掉，并且回到它进来时的统计页
        composeRule.onNodeWithTag("settings-back").assertDoesNotExist()
        composeRule.onNodeWithText("统计与成长").assertIsDisplayed()
        assertEquals(Lifecycle.State.RESUMED, activityState())
    }

    @Test
    fun back_closes_the_schedule_detail_dialog_and_keeps_the_agenda_page() {
        composeRule.onNodeWithTag("schedule-more-$TODAY_ENTRY_ID", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("schedule-more-edit-$TODAY_ENTRY_ID", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        // 对话框的标题永远在对话框顶部可见；表单字段在可滚动区域里，可能在视口之外
        composeRule.onNodeWithText("编辑日程").assertIsDisplayed()

        // 编辑对话框是独立窗口：当前窗口（对话框）本来就持有焦点，
        // Espresso 的默认 pressBack 会等 Activity 根窗口重新获得焦点而超时。
        // 无条件发送返回键，仍然是一次真实的返回事件。
        Espresso.pressBackUnconditionally()
        composeRule.waitForIdle()

        // 日程详情优先被关掉，日程页本身还在，应用没有退出
        composeRule.onNodeWithText("编辑日程").assertDoesNotExist()
        // 日程页是一列可滚动内容：断言之前先滚到筛选标题上，而不是假设它在视口里
        composeRule.onNodeWithTag("agenda-filter").performScrollTo().assertIsDisplayed()
        assertEquals(Lifecycle.State.RESUMED, activityState())
    }

    @Test
    fun back_closes_the_date_picker_without_leaving_the_agenda_day_unchanged() {
        composeRule.onNodeWithTag("agenda-date-menu").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-range-all-dates").assertIsDisplayed()

        Espresso.pressBack()
        composeRule.waitForIdle()

        // 日期选择器优先被关掉，且没有顺带切走当前规划日
        composeRule.onNodeWithTag("agenda-range-all-dates").assertDoesNotExist()
        composeRule.onNodeWithTag("agenda-filter").assert(hasText("筛选：规划日 $TODAY"))
        assertEquals(Lifecycle.State.RESUMED, activityState())
    }

    @Test
    fun back_with_no_open_layer_performs_the_system_back() {
        // 日程页上什么都没打开：返回键不被组合根接管，应用照旧退出
        assertEquals(Lifecycle.State.RESUMED, activityState())

        pressBackUntilSystemBack()

        assertNotEquals(Lifecycle.State.RESUMED, activityState())
    }

    @Test
    fun switching_workspaces_never_records_business_time_entries() {
        // 导航只是导航：切标签既不产生时间记录，也不改动任何日程条目
        listOf("nav-targets", "nav-statistics", "nav-agenda", "nav-targets").forEach { tag ->
            composeRule.onNodeWithTag(tag).performClick()
            composeRule.waitForIdle()
        }

        val state = readState()
        assertEquals(0, state.timeEntries.size)
        assertEquals(listOf(TODAY_ENTRY_ID, TOMORROW_ENTRY_ID), state.schedules.map { it.id })
    }

    /** 从统计页底部的「设置与数据」进入独立设置页。 */
    private fun openSettingsFromStatistics() {
        composeRule.onNodeWithTag("nav-statistics").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("statistics-open-settings").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun activityState(): Lifecycle.State = composeRule.activityRule.scenario.state

    /**
     * 按一次返回键并等系统级返回真的发生：应用不再是 RESUMED 之后才算按完。
     *
     * 返回键确实把应用退掉时，Espresso 会抛 [NoActivityResumedException]——那正是这几条用例
     * 要断言的结果，不是失败。这里捕获它，再由调用方断言应用真的退出了：留在上一个标签页的
     * 实现会消费掉返回键、应用仍然 RESUMED，因此这条断言仍然能区分两种行为。
     */
    private fun pressBackUntilSystemBack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            Espresso.pressBack()
        } catch (systemBackKilledTheApp: NoActivityResumedException) {
            // 预期的系统级返回：应用被退掉了
        }
        val deadline = SystemClock.uptimeMillis() + TIMEOUT
        while (SystemClock.uptimeMillis() < deadline && activityState() == Lifecycle.State.RESUMED) {
            instrumentation.waitForIdleSync()
        }
    }

    companion object {
        private const val TIMEOUT = 10_000L
        private const val TARGET_ID = "ui-back-target"
        private const val TODAY_ENTRY_ID = "ui-back-today"
        private const val TOMORROW_ENTRY_ID = "ui-back-tomorrow"

        private lateinit var TODAY: String

        private fun repository(database: AttentionDatabase) = RoomAttentionStateRepository(
            business = RoomBusinessDataRepository(database),
            settings = ApplicationProvider.getApplicationContext<android.content.Context>().createSettingsStore(),
        )

        @JvmStatic
        @BeforeClass
        fun seedBeforeActivityStarts() {
            val state = AttentionState()
            TODAY = state.planningDate(Instant.now()).toString()
            val tomorrow = state.planningDate(Instant.now()).plusDays(1).toString()
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    repository(database).replace(
                        state.copy(
                            targets = listOf(Target(TARGET_ID, title = "UI-返回键目标")),
                            schedules = listOf(
                                ScheduleEntry(TODAY_ENTRY_ID, TODAY, "UI-返回键今日条目", estimatedMinutes = 30, targetId = TARGET_ID),
                                ScheduleEntry(TOMORROW_ENTRY_ID, tomorrow, "UI-返回键明日条目", estimatedMinutes = 20, targetId = TARGET_ID),
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

        /** 读一次真实持久化状态，用来断言切换工作区没有写下任何业务记录。 */
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
