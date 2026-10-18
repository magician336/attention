package com.attention.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 主导航与日程首页的验收：底部只有日程、目标树、统计三个工作区入口，
 * 日程默认打开当前规划日，并在同一页提供今天、明天、所有日期与任意规划日。
 *
 * 这条用例走真实的 Workspace 宿主路径，因此验证的是用户看到与点到的东西，
 * 而不是某个 Composable 的内部变量名。
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceAgendaNavigationIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // 被销毁的 Activity 仍可能在收尾时写回「上次打开」：等写入停稳再放行下一条用例。
    @get:Rule(order = 1)
    val settingsBarrier = WorkspaceHostSettingsBarrier.quiesceAfterActivity

    @Test
    fun the_bottom_bar_has_three_workspaces_with_icons_labels_and_a_selected_semantic() {
        // #173 把设置从主导航退出：底部只有日程、目标树、统计三个工作区入口，
        // 设置改从统计页底部的「设置与数据」进入；今天/明天/所有日期仍不产生额外顶级入口
        composeRule.onAllNodes(hasNavTagPrefix()).assertCountEquals(3)

        composeRule.onNodeWithTag("nav-agenda").assert(hasText("日程"))
        composeRule.onNodeWithTag("nav-targets").assert(hasText("目标树"))
        composeRule.onNodeWithTag("nav-statistics").assert(hasText("统计"))

        // 选中态有可访问语义，不只靠颜色传达
        val selected = composeRule.onNodeWithTag("nav-agenda")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Selected]
        assertEquals(true, selected)
    }

    @Test
    fun every_workspace_entry_keeps_its_icon_beside_the_label() {
        listOf("nav-agenda" to "日程", "nav-targets" to "目标树", "nav-statistics" to "统计").forEach { (tag, label) ->
            val icons = composeRule.onAllNodes(hasContentDescription(label), useUnmergedTree = true)
                .fetchSemanticsNodes()
            assertEquals("$label 需要一个同名图标语义", 1, icons.size)
            composeRule.onNodeWithTag(tag).assert(hasText(label))
        }
    }

    /** 底部导航的每一项都带 `nav-` 前缀的标识，因此计数就等于顶级入口数。 */
    private fun hasNavTagPrefix(): SemanticsMatcher = SemanticsMatcher("底部导航入口") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("nav-") == true
    }

    @Test
    fun the_agenda_page_opens_on_the_current_planning_day() {
        // 默认打开当前规划日时间轴，而不是所有日期
        composeRule.onNodeWithTag("agenda-filter").assert(hasText("筛选：规划日 $TODAY"))
        // 今天、明天、所有日期与任意规划日收在同一个日期菜单入口里
        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("UI-今日条目").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun choosing_tomorrow_switches_the_day_highlights_adding_and_never_copies_todays_schedule() {
        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-range-tomorrow", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("agenda-filter").assert(hasText("筛选：规划日 $TOMORROW"))
        // 常驻的「调整计划」在明天视图下打开添加面板，规划日随当前视图变成明天
        composeRule.onNodeWithTag("agenda-plan-action", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-plan-menu-add", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("为明天（$TOMORROW）添加日程").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-add-planning-day", useUnmergedTree = true)
            .assert(hasText(TOMORROW))
        // 今天的日程没有被复制到明天
        composeRule.onNodeWithText("UI-今日条目").assertDoesNotExist()
    }

    @Test
    fun the_all_dates_range_reuses_the_same_row_interactions() {
        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-range-all-dates", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // 所有日期按规划日分组，条目仍然是同一套行与同一套交互
        composeRule.onNodeWithText("UI-今日条目").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-more-$TODAY_ENTRY_ID", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-completion-$TODAY_ENTRY_ID", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-more-$TOMORROW_ENTRY_ID", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("schedule-more-start-timer-$TOMORROW_ENTRY_ID", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun an_arbitrary_planning_day_can_be_opened_from_the_date_picker() {
        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        // 菜单项住在 popup 自己的 root 里：直接发语义动作，不对 popup 节点做滚动。
        composeRule.onNode(hasSetTextAction()).performTextReplacement(FAR_DATE)
        composeRule.onNodeWithTag("agenda-open-planning-day", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("agenda-filter").assert(hasText("筛选：规划日 $FAR_DATE"))
    }

    @Test
    fun starting_a_timer_from_a_schedule_entry_keeps_that_schedule_as_the_source() {
        // 日程行来自异步加载的状态：先等今天的条目进入语义树，再做坐标滚动与点击。
        // 少了这一步，慢一些的设备上会在数据到达之前就去定位行菜单。
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("schedule-more-$TODAY_ENTRY_ID", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("schedule-more-$TODAY_ENTRY_ID", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("schedule-more-start-timer-$TODAY_ENTRY_ID", useUnmergedTree = true)
            .performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("agenda-running-timer").fetchSemanticsNodes().isNotEmpty()
        }

        // 运行中的计时在日程页看得见，也真的停得掉
        composeRule.onNodeWithText("正在计时：UI-导航目标", substring = true).performScrollTo().assertIsDisplayed()
        val running = readState().activeTimer
        assertEquals(TODAY_ENTRY_ID, running?.scheduleEntryId)

        // 从条目起计时会先弹操作结果、再弹「通知权限未开启」，两条短提示串行出现，
        // 浮在页面底部正好盖住「结束并保存」。提示消失之前这个按钮点不动——先等它们走，
        // 再验证停止计时这条路径。两条提示的停留时间加起来会超过普通等待窗口，因此单独给足时间。
        composeRule.waitUntil(FEEDBACK_TIMEOUT) {
            composeRule.onAllNodesWithTag("attention.feedback.snackbar").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("agenda-running-timer-stop", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("agenda-running-timer").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(null, readState().activeTimer)
    }

    companion object {
        private const val TIMEOUT = 10_000L

        /** 两条短提示串行出现时的等待窗口：每条提示自身就要停留数秒。 */
        private const val FEEDBACK_TIMEOUT = 30_000L
        private const val TARGET_ID = "ui-nav-target"
        private const val TODAY_ENTRY_ID = "ui-nav-today"
        private const val TOMORROW_ENTRY_ID = "ui-nav-tomorrow"

        private lateinit var TODAY: String
        private lateinit var TOMORROW: String
        private const val FAR_DATE = "2026-12-24"

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
                            targets = listOf(Target(TARGET_ID, title = "UI-导航目标")),
                            schedules = listOf(
                                ScheduleEntry(TODAY_ENTRY_ID, TODAY, "UI-今日条目", estimatedMinutes = 30, targetId = TARGET_ID),
                                ScheduleEntry(TOMORROW_ENTRY_ID, TOMORROW, "UI-明日条目", estimatedMinutes = 20, targetId = TARGET_ID),
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

        /** 读一次真实持久化状态，用来断言计时器保留的日程来源关联。 */
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
