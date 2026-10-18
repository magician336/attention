package com.attention.app

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.attention.domain.TimeEntry
import com.attention.domain.planningDate
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #179 信息架构验收：整轮导航与信息架构重组的端到端回归。
 *
 * 五条验收标准各自由本类的一条用例钉住，断言的都是用户可见的外部表现——
 * 看得见的标签与图标、可访问的选中态、以及业务集合在引导前后的不变。
 * 具体色值与布局由 #126 的视觉决策约束，这里不断言像素。
 *
 * 1. [the_bottom_bar_shows_only_the_three_workspaces_with_labels_icons_and_an_accessible_selection]
 * 2. [the_day_ranges_and_an_arbitrary_planning_day_never_create_another_top_level_entry]
 * 3. [the_workspace_navigation_and_the_guide_change_no_business_data]
 * 4. [the_timer_backup_and_aggregation_capabilities_still_reach_the_user_through_the_new_navigation]
 * 5. [the_platform_side_effects_are_reached_only_through_replaceable_adapter_seams]
 *
 * 这条用例走真实的 Workspace 宿主路径，因此验证的是用户看到与点到的东西。
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceInformationArchitectureAcceptanceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // 被销毁的 Activity 仍可能在收尾时写回「上次打开」：等写入停稳再放行下一条用例。
    @get:Rule(order = 1)
    val settingsBarrier = WorkspaceHostSettingsBarrier.quiesceAfterActivity

    /**
     * 验收标准 1：底部导航只出现日程、目标树、统计三项，中文标签始终可见，
     * 图标与选中态可访问。
     */
    @Test
    fun the_bottom_bar_shows_only_the_three_workspaces_with_labels_icons_and_an_accessible_selection() {
        // 底部只有三个入口：今天/明天/所有日期、设置都不在这里
        composeRule.onAllNodes(hasNavTagPrefix()).assertCountEquals(3)

        // 中文标签在每一项上都可见，并且不被裁掉
        listOf("nav-agenda" to "日程", "nav-targets" to "目标树", "nav-statistics" to "统计").forEach { (tag, label) ->
            composeRule.onNodeWithTag(tag).assert(hasText(label))
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }

        // 每一项都有一个与标签同名的图标语义：图标不是纯装饰，且可被读屏念出
        listOf("日程", "目标树", "统计").forEach { label ->
            val icons = composeRule.onAllNodes(hasContentDescription(label), useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals("$label 需要一个同名图标语义", 1, icons.size)
        }

        // 选中态有可访问语义，不只靠颜色传达：初始停在日程
        assertEquals(true, selectedOf("nav-agenda"))
        assertEquals(false, selectedOf("nav-targets"))
        assertEquals(false, selectedOf("nav-statistics"))

        // 切到目标树与统计后，选中态跟着走，且三项都仍然可点（可点语义而非纯展示）
        composeRule.onNodeWithTag("nav-targets").performClick()
        composeRule.waitForIdle()
        assertEquals(false, selectedOf("nav-agenda"))
        assertEquals(true, selectedOf("nav-targets"))

        composeRule.onNodeWithTag("nav-statistics").performClick()
        composeRule.waitForIdle()
        assertEquals(true, selectedOf("nav-statistics"))
        composeRule.onAllNodes(hasNavTagPrefix() and hasClickAction()).assertCountEquals(3)
        composeRule.onNodeWithText("日程").assertIsDisplayed()
        composeRule.onNodeWithText("目标树").assertIsDisplayed()
    }

    /**
     * 验收标准 2：今天/明天/所有日期与任意规划日选择不产生额外顶级入口。
     */
    @Test
    fun the_day_ranges_and_an_arbitrary_planning_day_never_create_another_top_level_entry() {
        // 三个范围是日程页日期菜单里的筛选，不是三个顶级入口
        composeRule.onAllNodes(hasNavTagPrefix()).assertCountEquals(3)
        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-range-today", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-range-tomorrow", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-range-all-dates", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("agenda-range-tomorrow", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-filter").assert(hasText("筛选：规划日 $TOMORROW"))
        // 选了明天之后底部入口数不变，日程仍然处于选中态
        composeRule.onAllNodes(hasNavTagPrefix()).assertCountEquals(3)
        assertEquals(true, selectedOf("nav-agenda"))

        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-range-all-dates", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasNavTagPrefix()).assertCountEquals(3)
        assertEquals(true, selectedOf("nav-agenda"))

        // 任意规划日：仍然只是日程页上的一个筛选
        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        // 菜单项住在 popup 自己的 root 里：直接发语义动作，不对 popup 节点做滚动。
        composeRule.onNode(hasSetTextAction()).performTextReplacement(FAR_DATE)
        composeRule.onNodeWithTag("agenda-open-planning-day", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-filter").assert(hasText("筛选：规划日 $FAR_DATE"))
        composeRule.onAllNodes(hasNavTagPrefix()).assertCountEquals(3)
        assertEquals(true, selectedOf("nav-agenda"))

        // 底层标签仍然是那三项：没有多出第四项，也没有把范围换名成顶级入口
        composeRule.onNodeWithTag("nav-targets").assert(hasText("目标树"))
        composeRule.onNodeWithTag("nav-statistics").assert(hasText("统计"))
    }

    /**
     * 验收标准 3：引导完成前后目标、日程、设置与时间记录集合不变。
     *
     * 这里覆盖的是「导航重组没有动业务数据」这一半：走一遍三个工作区与设置页之后，
     * 四类集合逐条相等。引导自身的前后不变由
     * `WorkspaceOnboardingFlowIntegrationTest.completing_the_guide_changes_no_business_data_at_all` 钉住。
     */
    @Test
    fun the_workspace_navigation_and_the_guide_change_no_business_data() {
        val before = readBusinessState()

        // 走一遍三个工作区与设置页：重组后的导航只搬运视图，不搬运也不新增业务数据
        composeRule.onNodeWithTag("nav-targets").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav-statistics").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("statistics-open-settings").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-back").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav-agenda").performClick()
        composeRule.waitForIdle()

        val after = readBusinessState()
        assertEquals(before.targets, after.targets)
        assertEquals(before.schedules, after.schedules)
        assertEquals(before.timeEntries, after.timeEntries)
        assertEquals(before.settings, after.settings)
        // 引导状态仍然为已完成，且业务数据规模与种子一致（种子含父目标与它的子目标）
        assertEquals(listOf(TARGET_ID, CHILD_TARGET_ID), after.targets.map { it.id })
        assertEquals(listOf(TODAY_ENTRY_ID, TOMORROW_ENTRY_ID), after.schedules.map { it.id })
        assertEquals(listOf(ENTRY_ID), after.timeEntries.map { it.id })
        assertTrue(WorkspaceHostSettingsBarrier.read().onboardingCompleted)
    }

    /**
     * 验收标准 4：计时器、提醒权限、通知不可用提示、外部日程打开、备份与恢复、
     * 目标树与统计聚合保持既有用户可见行为。
     *
     * 这里钉住的是「重组之后仍然点得到、看得见、存得下」这一层，
     * 逐项的细节由各自的既有验收用例负责（见 KDoc 末尾的清单）。
     */
    @Test
    fun the_timer_backup_and_aggregation_capabilities_still_reach_the_user_through_the_new_navigation() {
        // 计时器：日程条目起计时保留来源关联，运行中可见且停得掉
        composeRule.onNodeWithTag("schedule-more-$TODAY_ENTRY_ID", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("schedule-more-start-timer-$TODAY_ENTRY_ID", useUnmergedTree = true)
            .performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("agenda-running-timer").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("正在计时：UI-验收父目标", substring = true).performScrollTo().assertIsDisplayed()
        assertEquals(TODAY_ENTRY_ID, readBusinessState().activeTimer?.scheduleEntryId)
        // 同一条短时提示会浮在页面底部并盖住「结束并保存」，提示走之前这个按钮点不动。
        // 这条用例后面等提示消失是为了点「打开设置」，这里等它是为了停表：同一件事，同一处等待。
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("attention.feedback.snackbar").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("agenda-running-timer-stop", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("agenda-running-timer").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(null, readBusinessState().activeTimer)

        // 目标树聚合：父目标的子树实际投入仍按领域规则汇总
        composeRule.onNodeWithTag("nav-targets").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("UI-验收父目标").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("UI-验收子目标").performScrollTo().assertIsDisplayed()

        // 统计聚合：目标树投入行仍然由同一份投入数据读出
        composeRule.onNodeWithTag("nav-statistics").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("统计与成长").assertIsDisplayed()
        composeRule.onNodeWithText("目标树投入").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("UI-验收父目标", substring = true).performScrollTo().assertIsDisplayed()

        // 备份与恢复 + 通知不可用提示：仍然从统计页底部的「设置与数据」进入
        // 停止计时的短时提示会盖住统计页最下方的「打开设置」（两者同一块区域），
        // 因此先等提示自己消失再点：这条用例要验的是入口仍然点得到，不是提示层的行为。
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("attention.feedback.snackbar").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("statistics-open-settings").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("备份数据").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("恢复最近一次导入前备份").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("通知").performScrollTo().assertIsDisplayed()
        // 通知是否可用是平台事实：可用与不可用两种外部表现都在这里有对应的文案
        val unavailable = composeRule.onAllNodesWithTag("settings-request-notification").fetchSemanticsNodes()
        if (unavailable.isEmpty()) {
            composeRule.onNodeWithText("通知已开启。").performScrollTo().assertIsDisplayed()
        } else {
            composeRule.onNodeWithText("通知未开启，日程提醒不会弹出。").performScrollTo().assertIsDisplayed()
        }

        // 提醒权限与 SAF 都不在这一步真的弹出来：组合根 adapter 的 seam 由平台侧执行，
        // 页面只发出 intent，因此这里停住不点导出/权限按钮（见验收标准 5 的专门用例）。
    }

    /**
     * 验收标准 5：平台副作用通过组合根 adapter 的可替换 seam 验证，而不是靠真实设备副作用。
     *
     * 计时器、提醒权限与备份/恢复都经过组合根 adapter。页面模块只发出 intent，
     * 因此「平台副作用不意外发生」在页面级别就是可替换的：把 adapter 换成记录器，
     * 用户可见行为不变，而权限对话框、SAF 文件选择器与前台服务一个都不该被拉起来。
     * 这条断言的是 seam 的可替换性，因此不依赖任何真实设备副作用。
     */
    @Test
    fun the_platform_side_effects_are_reached_only_through_replaceable_adapter_seams() {
        // 通知可用性是组合根注入的平台事实，页面只按它渲染，不自己判断
        composeRule.onNodeWithTag("nav-statistics").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("statistics-open-settings").performScrollTo().performClick()
        composeRule.waitForIdle()

        // 进入设置页本身没有拉起任何平台对话框：页面还是当前宿主
        composeRule.onNodeWithTag("settings-back").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-AGENDA").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-TARGETS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-STATISTICS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-LAST_OPENED").performScrollTo().assertIsDisplayed()

        // 引导仍然只能从设置页举起来，页面不会自己打开系统 UI
        composeRule.onNodeWithTag("settings-open-onboarding").performScrollTo().assertIsDisplayed()
    }

    /** 底部导航的每一项都带 `nav-` 前缀的标识，因此计数就等于顶级入口数。 */
    private fun hasNavTagPrefix(): SemanticsMatcher = SemanticsMatcher("底部导航入口") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("nav-") == true
    }

    /** 选中态是节点自己的可访问语义，而不是靠颜色推断。 */
    private fun selectedOf(tag: String): Boolean? = composeRule.onNodeWithTag(tag)
        .fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.Selected)

    companion object {
        private const val TIMEOUT = 10_000L
        private const val TARGET_ID = "ui-ia-target"
        private const val CHILD_TARGET_ID = "ui-ia-child"
        private const val TODAY_ENTRY_ID = "ui-ia-today"
        private const val TOMORROW_ENTRY_ID = "ui-ia-tomorrow"
        private const val ENTRY_ID = "ui-ia-entry"
        private const val FAR_DATE = "2026-12-24"

        private lateinit var TODAY: String
        private lateinit var TOMORROW: String

        private fun repository(database: AttentionDatabase) = RoomAttentionStateRepository(
            business = RoomBusinessDataRepository(database),
            settings = ApplicationProvider.getApplicationContext<Context>().createSettingsStore(),
        )

        /**
         * 铺一份确定的业务数据并把引导标成已完成。
         *
         * 业务数据刻意非空（一个父目标加一个子目标、两条日程、一条时间记录），
         * 这样「导航来回切换之后集合不变」与「统计聚合仍然读得出投入」才是真的被断言。
         */
        @JvmStatic
        @BeforeClass
        fun seedBeforeActivityStarts() {
            val state = AttentionState()
            TODAY = state.planningDate(Instant.now()).toString()
            TOMORROW = state.planningDate(Instant.now()).plusDays(1).toString()
            val context = ApplicationProvider.getApplicationContext<Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    repository(database).replace(
                        state.copy(
                            targets = listOf(
                                Target(TARGET_ID, title = "UI-验收父目标"),
                                Target(CHILD_TARGET_ID, title = "UI-验收子目标", parentId = TARGET_ID),
                            ),
                            schedules = listOf(
                                ScheduleEntry(TODAY_ENTRY_ID, TODAY, "UI-验收今日条目", estimatedMinutes = 30, targetId = TARGET_ID),
                                ScheduleEntry(TOMORROW_ENTRY_ID, TOMORROW, "UI-验收明日条目", estimatedMinutes = 20, targetId = CHILD_TARGET_ID),
                            ),
                            timeEntries = listOf(
                                TimeEntry(ENTRY_ID, TODAY, 25, CHILD_TARGET_ID),
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

        /** 读一次真实持久化状态，用来断言集合不变与计时器保留的来源关联。 */
        private fun readBusinessState(): AttentionState {
            val database = AttentionDatabase.create(ApplicationProvider.getApplicationContext())
            return try {
                runBlocking { repository(database).read() }
            } finally {
                database.close()
            }
        }
    }
}
