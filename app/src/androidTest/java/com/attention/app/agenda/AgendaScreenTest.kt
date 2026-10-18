package com.attention.app.agenda

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.ui.components.AttentionFeedbackTags
import com.attention.app.ui.components.AttentionStateKind
import com.attention.app.ui.theme.AttentionTheme
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.domain.TimeEntrySource
import com.attention.workspace.state.AgendaActions
import com.attention.workspace.state.AgendaActiveTimer
import com.attention.workspace.state.AgendaBackfillDraft
import com.attention.workspace.state.AgendaDateOption
import com.attention.workspace.state.AgendaDateSection
import com.attention.workspace.state.AgendaFreeTimerOption
import com.attention.workspace.state.AgendaRangeOption
import com.attention.workspace.state.AgendaRangeScope
import com.attention.workspace.state.AgendaRecurrenceRuleLine
import com.attention.workspace.state.AgendaRecurrenceDraft
import com.attention.workspace.state.AgendaScheduleDraft
import com.attention.workspace.state.AgendaScheduleRow
import com.attention.workspace.state.AgendaScreenState
import com.attention.workspace.state.AgendaStopResult
import com.attention.workspace.state.AgendaStoppedRecord
import com.attention.workspace.state.AgendaTargetOption
import com.attention.workspace.state.AgendaTimeRecord
import com.attention.workspace.state.AgendaWorkbenchSummary
import com.attention.workspace.state.AGENDA_RETRY_LABEL
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 日程页的直接测试：只给它 fake screen state 与 fake actions，
 * 断言可见标签、选中规划日、导航与筛选 intent、创建/编辑/完成 intent，
 * 以及从日程条目起计时的来源关联，不经过 ViewModel、Room 与权限 API。
 *
 * screen state 是手写的，不走 agendaScreenState 投影：页面测试钉住的是页面自己要渲染的那一行
 * 文本，条目筛选与重复日程展开这些领域规则由 workspace-state 与 domain 的纯 Kotlin 测试负责。
 * 页面版本因此不需要 Activity 宿主，用 [createComposeRule] 挂载即可。
 */
@RunWith(AndroidJUnit4::class)
class AgendaScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_page_shows_the_all_dates_view_with_a_capacity_summary_per_planning_day() {
        // 所有日期是特殊视图：条目按规划日分组，每天各有自己的每日容量
        mount(allDatesScreenState())

        composeRule.onNodeWithTag("agenda-filter", useUnmergedTree = true)
            .assert(hasText("筛选：所有日期"))
        composeRule.onNodeWithTag("agenda-summary-capacity", useUnmergedTree = true)
            .assert(hasText("每日容量 4 小时", substring = true))
        composeRule.onNodeWithTag("agenda-summary-planned", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("1小时30分")))
        composeRule.onNodeWithTag("agenda-summary-actual", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("0分")))
        node("规划日 2026-03-05").assertIsDisplayed()
        node("已安排 60 / 120 分钟").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-schedule-row-e1", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasAnyDescendant(hasText("写周报")))
        node("2026-03-05 · 60 分钟 · 工作 · 无提醒").assertIsDisplayed()
        // 每个规划日分区都在时间轴区块内，排在只读摘要之下：先滚到它再断言可见。
        composeRule.onNodeWithTag("agenda-capacity-summary-2026-03-05", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun a_selected_planning_day_shows_that_day_title_and_entries() {
        // 选中规划日：标题、筛选文案与容量卡片都跟着这一天
        mount(planningDayScreenState(LocalDate.of(2026, 3, 6)))

        composeRule.onNodeWithTag("agenda-filter", useUnmergedTree = true)
            .assert(hasText("筛选：规划日 2026-03-06"))
        node("开组会").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-capacity-summary", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun compact_timeline_omits_the_hero_and_redundant_timeline_headers() {
        // Prototype A keeps the date controls and summary, but the timeline itself is the page's heading.
        mount(
            planningDayScreenState(
                selected = planningDay,
                timelineRows = listOf(row(reportEntry, "2026-03-05 · 60 分钟 · 工作 · 无提醒", true)),
            ),
        )

        composeRule.onNodeWithTag("agenda-filter", useUnmergedTree = true)
            .assert(hasText("筛选：规划日 2026-03-05"))
        composeRule.onNodeWithText(
            "日程条目可带预计时长；它不会自动变成实际投入。",
            useUnmergedTree = true,
        ).assertDoesNotExist()
        composeRule.onNodeWithTag("agenda-timeline", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("3 项", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun workbench_sections_are_stacked_without_vertical_overlap() {
        mount(planningDayScreenState(planningDay))

        val summary = composeRule.onNodeWithTag("agenda-section-summary", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val timeline = composeRule.onNodeWithTag("agenda-section-timeline", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val operations = composeRule.onNodeWithTag("agenda-section-add", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "摘要与时间轴不能重叠：summary=$summary timeline=$timeline",
            summary.bottom <= timeline.top,
        )
        assertTrue(
            "时间轴与操作栈不能重叠：timeline=$timeline operations=$operations",
            timeline.bottom <= operations.top,
        )
    }

    @Test
    fun the_empty_view_explains_what_is_missing() {
        // 没有条目时按当前视图给出空态文案
        mount(emptyScreenState())

        node("所有日期暂无日程条目。").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-empty", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun picking_a_planning_day_from_the_filter_emits_the_selection_intent() {
        // 规划日筛选只发出 intent，实际选中日由组合根与页面选择状态决定
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)

        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-date-2026-03-06", useUnmergedTree = true).performClick()

        assertEquals(listOf(LocalDate.of(2026, 3, 6)), intents.selectedDates)
    }

    @Test
    fun choosing_all_dates_emits_the_special_view_intent() {
        // 所有日期是一个特殊视图，不是一个可用的日期值
        val intents = RecordingAgendaActions()
        mount(planningDayScreenState(LocalDate.of(2026, 3, 6)), intents)

        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-range-all-dates", useUnmergedTree = true).performClick()

        // 所有日期是一个特殊视图，走独立的 selectAllDates intent，而不是某一天
        assertEquals(emptyList<LocalDate?>(), intents.selectedDates)
        assertEquals(listOf("allDates"), intents.dataIntents)
    }

    @Test
    fun submitting_the_add_form_emits_a_schedule_draft() {
        // 标题与规划日必需；其余可选，提交后只发一个草稿 intent
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)
        openAddScheduleSheet()

        composeRule.onNodeWithTag("agenda-title-input", useUnmergedTree = true).performScrollTo()
            .performTextReplacement("整理需求")
        composeRule.onNodeWithTag("agenda-estimate-input", useUnmergedTree = true).performTextReplacement("45")
        composeRule.onNodeWithTag("agenda-add-submit", useUnmergedTree = true).performScrollTo().performClick()

        val draft: AgendaScheduleDraft = intents.addedSchedules.single()
        assertEquals("整理需求", draft.title)
        assertEquals("2026-03-05", draft.planningDate)
        assertEquals(45, draft.estimatedMinutes)
        assertNull(draft.startMinute)
        assertEquals("没有填提醒就不请求提醒权限", 0, intents.permissionRequests)
    }

    @Test
    fun submitting_the_add_form_with_backfill_emits_one_combined_intent() {
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)
        openAddScheduleSheet()

        composeRule.onNodeWithTag("agenda-title-input", useUnmergedTree = true).performScrollTo()
            .performTextReplacement("补记并安排")
        composeRule.onNodeWithTag("agenda-backfill-toggle", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-backfill-preset-30", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-add-submit", useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(0, intents.addedSchedules.size)
        assertEquals(1, intents.addedSchedulesWithBackfill.size)
        assertEquals(30, intents.addedSchedulesWithBackfill.single().second.durationMinutes)
    }

    @Test
    fun an_incomplete_add_form_shows_the_validation_error_instead_of_an_intent() {
        // 标题为空时不发 intent，页面就地给出与迁移前相同的提示
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)
        openAddScheduleSheet()

        composeRule.onNodeWithTag("agenda-estimate-input", useUnmergedTree = true).performTextReplacement("45")
        composeRule.onNodeWithTag("agenda-add-submit", useUnmergedTree = true).performScrollTo().performClick()

        node("日程标题不能为空").assertIsDisplayed()
        assertEquals(emptyList<AgendaScheduleDraft>(), intents.addedSchedules)
    }

    @Test
    fun a_reminder_in_the_add_form_asks_for_permission_as_an_intent() {
        // 填了提醒才请求权限；权限申请本身由组合根 adapter 完成
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)
        openAddScheduleSheet()

        composeRule.onNodeWithTag("agenda-title-input", useUnmergedTree = true).performScrollTo()
            .performTextReplacement("带提醒的日程")
        expandAddScheduleMoreSettings()
        composeRule.onNodeWithTag("agenda-reminder-input", useUnmergedTree = true).performScrollTo()
            .performTextReplacement("09:00")
        composeRule.onNodeWithTag("agenda-add-submit", useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(540, intents.addedSchedules.single().reminderMinuteOfDay)
        assertEquals(1, intents.permissionRequests)
    }

    @Test
    fun the_plan_action_is_a_persistent_menu_with_add_and_backfill_only() {
        // 主操作常驻右下角，菜单只承担添加与补记；修改日程回到时间轴行内。
        mount(planningDayScreenState(planningDay))

        composeRule.onNodeWithTag("agenda-plan-action", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(hasClickAction())
            .assert(hasAnyDescendant(hasText("调整计划")))

        openAdjustPlanMenu()

        composeRule.onNodeWithTag("agenda-plan-menu-add", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(hasAnyDescendant(hasText("添加日程")))
        composeRule.onNodeWithTag("agenda-plan-menu-backfill", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(hasAnyDescendant(hasText("事后补记")))
        // 菜单里恰好只有这两项；修改日程与取消完成都在行菜单里。
        composeRule.onAllNodes(
            hasClickAction() and hasAnyAncestor(hasTestTag("agenda-plan-menu")),
            useUnmergedTree = true,
        ).assertCountEquals(2)

        // 自由计时没有进菜单，仍在页面的操作栈里。
        composeRule.onNodeWithTag("agenda-operation-free-timer", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .assert(hasClickAction())
            .assert(hasAnyDescendant(hasText("开始自由计时")))
        composeRule.onAllNodesWithTag("agenda-operation-add-schedule", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("agenda-operation-backfill", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun the_plan_action_stays_visible_while_the_page_scrolls() {
        // 主操作在滚动容器之外，滚到底部仍要看得见。
        mount(planningDayScreenState(planningDay))

        composeRule.onNodeWithTag("agenda-section-recurrence", useUnmergedTree = true).performScrollTo()

        composeRule.onNodeWithTag("agenda-plan-action", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun the_plan_action_is_hidden_while_the_first_load_is_pending() {
        mount(planningDayScreenState(planningDay).copy(loading = true))

        composeRule.onNodeWithTag("agenda-plan-action", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun the_plan_action_is_hidden_when_the_local_read_failed() {
        mount(planningDayScreenState(planningDay).copy(loadError = "磁盘读不出来"))

        composeRule.onNodeWithTag("agenda-plan-action", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun the_backfill_operation_reuses_the_existing_combined_save_action_and_planning_day() {
        // 「事后补记」沿用现有的保存日程并补记 intent，只是改从常驻的「调整计划」菜单打开。
        val intents = RecordingAgendaActions()
        mount(planningDayScreenState(selected = planningDay), intents)

        openBackfillSheet()

        composeRule.onNodeWithTag("agenda-add-sheet", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-backfill-editor", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-add-planning-day", useUnmergedTree = true)
            .assert(hasText("2026-03-05"))
        composeRule.onNodeWithTag("agenda-title-input", useUnmergedTree = true)
            .performScrollTo().performTextReplacement("补记并安排")
        composeRule.onNodeWithTag("agenda-backfill-preset-30", useUnmergedTree = true)
            .performScrollTo().performClick()
        // 预设会改写补记编辑器的本地状态；等这次重组落定再提交，避免读到旧的 15 分钟。
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-add-submit", useUnmergedTree = true)
            .performScrollTo().performClick()

        assertEquals("2026-03-05", intents.addedSchedulesWithBackfill.single().first.planningDate)
        assertEquals(30, intents.addedSchedulesWithBackfill.single().second.durationMinutes)
    }

    @Test
    fun the_add_schedule_sheet_opens_on_the_planning_day_in_view() {
        // 底部面板打开时规划日已经填好：用户不必先输一遍日期，提交出去的就是这一天
        val intents = RecordingAgendaActions()
        mount(
            planningDayScreenState(selected = planningDay).copy(
                addScheduleDefaultDate = "2026-03-05",
                addScheduleDefaultDateLabel = "规划日 2026-03-05",
            ),
            intents,
        )
        openAddScheduleSheet()

        composeRule.onNodeWithTag("agenda-add-sheet", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-add-planning-day", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("agenda-title-input", useUnmergedTree = true).performScrollTo()
            .performTextReplacement("今天第一件事")
        composeRule.onNodeWithTag("agenda-add-submit", useUnmergedTree = true).performScrollTo().performClick()

        assertEquals("2026-03-05", intents.addedSchedules.single().planningDate)
    }

    @Test
    fun the_add_schedule_sheet_keeps_the_secondary_fields_behind_more_settings() {
        // 主要字段是标题与时间段、预计投入；备注、提醒与关联计划是低频的
        mount(allDatesScreenState())
        openAddScheduleSheet()

        // 面板的内容列是可滚动的，展开「更多设置」之后次要字段会落在面板可视高度以下：
        // 先在面板里滚到目标再断言「已显示」，断言本身仍然是「看得见」，不是「存在」。
        composeRule.onNodeWithTag("agenda-title-input", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-start-input", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-end-input", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-estimate-input", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag("agenda-reminder-input", useUnmergedTree = true).fetchSemanticsNodes().let {
            assertEquals("更多设置没展开时不渲染次要字段", emptyList<Any>(), it)
        }

        expandAddScheduleMoreSettings()

        composeRule.onNodeWithTag("agenda-reminder-input", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-note-input", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-add-target", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun starting_a_free_timer_chooses_a_target_in_place_without_creating_a_schedule() {
        // 自由计时不需要先建日程：挑一个未归档计划就开始，且不发出任何新建日程 intent
        val intents = RecordingAgendaActions()
        mount(
            planningDayScreenState(selected = planningDay).copy(
                freeTimerOptions = listOf(
                    AgendaFreeTimerOption(null, "未归属活动"),
                    AgendaFreeTimerOption("t1", "工作"),
                ),
            ),
            intents,
        )

        composeRule.onNodeWithTag("agenda-operation-free-timer", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("agenda-free-timer-target-t1", useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(listOf("t1"), intents.startedFreeTimers)
        assertEquals("自由计时不伪造日程来源", emptyList<AgendaScheduleDraft>(), intents.addedSchedules)
    }

    @Test
    fun the_free_timer_sheet_offers_the_unowned_activity_and_the_unarchived_plans() {
        mount(
            planningDayScreenState(selected = planningDay).copy(
                // 面板自己给当前目标补「（当前）」，因此注入的是干净的选项名。
                freeTimerOptions = listOf(
                    AgendaFreeTimerOption(null, "未归属活动"),
                    AgendaFreeTimerOption("t1", "工作", selected = true),
                ),
            ),
        )

        composeRule.onNodeWithTag("agenda-operation-free-timer", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("agenda-free-timer-unowned", useUnmergedTree = true).assertIsDisplayed()
        node("工作（当前）").assertIsDisplayed()
    }

    @Test
    fun editing_an_entry_saves_the_changed_draft() {
        // 编辑对话框是页面本地表单状态；保存发出被改过的条目
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)

        openRowMenu("e1")
        composeRule.onNodeWithTag("schedule-more-edit-e1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("schedule-edit-estimate-e1", useUnmergedTree = true).performScrollTo()
            .performTextReplacement("90")
        // 保存按钮在对话框的操作区，不在可滚动正文里，因此不滚动
        composeRule.onNodeWithTag("schedule-edit-save-e1", useUnmergedTree = true).performClick()

        assertEquals("e1", intents.updatedEntries.single().first.id)
        assertEquals(90, intents.updatedEntries.single().first.estimatedMinutes)
    }

    @Test
    fun completing_and_cancelling_completion_emit_separate_intents() {
        // 完成确认与取消完成各自一个 intent，都带条目 id
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)

        composeRule.onNodeWithTag("schedule-completion-e1", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("schedule-completion-e2", useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(listOf("e1"), intents.confirmedCompletions)
        assertEquals(listOf("e2"), intents.toggledCompletions)
    }

    @Test
    fun starting_a_timer_from_an_entry_keeps_the_entry_as_the_source() {
        // 从日程条目起计时的 intent 带上整条条目，组合根因此能保留来源关联
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)

        openRowMenu("e1")
        composeRule.onNodeWithTag("schedule-more-start-timer-e1", useUnmergedTree = true).performClick()

        val entry: ScheduleEntry = intents.startedEntries.single()
        assertEquals("e1", entry.id)
        assertEquals("t1", entry.targetId)
    }

    @Test
    fun starting_a_timer_is_disabled_while_a_timer_is_running() {
        // 已有计时进行中时不允许从日程条目再起一个；行菜单仍然打开，只是该项不可用
        mount(allDatesScreenState(canStartTimer = false))

        openRowMenu("e1")
        composeRule.onNodeWithTag("schedule-more-start-timer-e1", useUnmergedTree = true)
            .assertExists()
            .assertIsNotEnabled()
    }

    @Test
    fun the_recurring_schedule_block_is_a_secondary_entry_that_is_collapsed_by_default() {
        // 重复日程不再是首屏主路径：表单与规则行都收在次级入口后面。
        // 折叠只挪位置不删能力，因此这条用例两头都断言：折叠入口在，展开后条目也在。
        mount(allDatesScreenState())

        composeRule.onNodeWithTag("agenda-recurrence-toggle", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("重复标题", substring = false).assertDoesNotExist()
        composeRule.onNodeWithTag("recurrence-reminder-input", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("每日晨读 · 每日 · 2026-03-05").assertDoesNotExist()

        expandRecurrence()

        composeRule.onNodeWithTag("agenda-recurrence-title-input", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("recurrence-reminder-input", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("每日晨读 · 每日 · 2026-03-05").assertExists()
    }

    @Test
    fun the_recurring_rule_lines_are_shown_with_their_stop_intent() {
        // 重复日程规则行来自注入的 screen state；停止未来生成只发出带 id 的 intent
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)

        expandRecurrence()
        node("每日晨读 · 每日 · 2026-03-05").assertIsDisplayed()
        node("每周复盘 · 每周 一,三 · 2026-03-02 至 2026-03-31").assertIsDisplayed()
        node("已停止").assertIsDisplayed()
        node("停止未来生成").performClick()

        assertEquals(listOf("rule-daily"), intents.stoppedRules)
    }

    @Test
    fun creating_a_weekly_recurrence_emits_the_rule_draft() {
        // 每周规则至少选一个星期；选中后提交发出规则草稿
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)

        expandRecurrence()
        composeRule.onNodeWithTag("agenda-recurrence-title-input", useUnmergedTree = true)
            .performScrollTo().performTextReplacement("每周复盘会")
        node("每周").performScrollTo().performClick()
        // 星期由复选框本身切换，标签文字只是它的说明。
        // 指名这一个复选框而不是「页面上第一个可切换的控件」：重复日程是折叠的次级入口，
        // 排在时间轴之后，而时间轴行自带完成勾选框，按位置猜会点到完成勾选框上。
        composeRule.onNodeWithTag("agenda-recurrence-weekday-1", useUnmergedTree = true)
            .performScrollTo().performClick()
        node("创建并生成未来 30 天").performScrollTo().performClick()

        val draft: AgendaRecurrenceDraft = intents.addedRecurrences.single()
        assertEquals("每周复盘会", draft.title)
        assertEquals(ScheduleFrequency.WEEKLY, draft.frequency)
        assertEquals(listOf(1), draft.weekdays)
        assertEquals("2026-03-05", draft.startDate)
    }

    @Test
    fun a_weekly_recurrence_without_a_weekday_shows_the_validation_error() {
        // 每周规则没有选星期时不发 intent
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)

        expandRecurrence()
        composeRule.onNodeWithTag("agenda-recurrence-title-input", useUnmergedTree = true)
            .performScrollTo().performTextReplacement("每周复盘会")
        node("每周").performScrollTo().performClick()
        node("创建并生成未来 30 天").performScrollTo().performClick()

        node("每周规则至少选择一个星期").assertIsDisplayed()
        assertEquals(emptyList<AgendaRecurrenceDraft>(), intents.addedRecurrences)
    }

    /** 展开重复日程这个次级入口：能力没有删除，只是收在一次点击之后。 */
    private fun expandRecurrence() {
        composeRule.onNodeWithTag("agenda-recurrence-toggle", useUnmergedTree = true).performScrollTo().performClick()
    }

    @Test
    fun the_range_bar_switches_between_today_tomorrow_and_all_dates() {
        // 顶部范围切换在页面内完成：今天与明天各选一个规划日，所有日期是特殊视图
        val intents = RecordingAgendaActions()
        mount(planningDayScreenState(planningDay), intents)

        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-range-tomorrow", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-range-all-dates", useUnmergedTree = true).performClick()

        assertEquals(listOf(LocalDate.of(2026, 3, 6)), intents.selectedDates)
        assertEquals(listOf("allDates"), intents.dataIntents)
    }

    @Test
    fun a_running_timer_stays_visible_and_can_be_stopped() {
        // 计时卡下线之后，运行中的计时仍然必须在日程页看得见并能停止
        val intents = RecordingAgendaActions()
        mount(planningDayScreenState(planningDay, activeTimer = runningTimer()), intents)

        composeRule.onNodeWithTag("agenda-running-timer-stop", useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(1, intents.stoppedTimers)
    }

    @Test
    fun the_running_schedule_is_pinned_to_the_top_of_the_timeline() {
        // #193 的「置顶」指的是**时间轴内部**的第一位：正在计时的那条日程行排在其它行之前。
        // 整页区块顺序是 #189 的另一条规则，由
        // [the_first_screen_shows_its_sections_in_the_fixed_workbench_order] 单独断言——
        // 两条规则各由一条用例负责，用例之间因此不会互相推翻。
        val timingEntry = ScheduleEntry("e5", "2026-03-06", "正在做的", estimatedMinutes = 60)
        mount(
            planningDayScreenState(
                planningDay,
                activeTimer = runningTimer(),
                timelineRows = listOf(
                    row(timingEntry, "2026-03-06 · 60 分钟 · 无提醒", false, timing = true),
                    row(meetingEntry, "2026-03-06 · 30 分钟 · 无提醒 · 已完成", true),
                ),
            ),
        )

        val tags = orderedTestTags()
        val cueIndex = tags.indexOf("schedule-timing-cue")
        val timingRowIndex = tags.indexOf("agenda-schedule-row-e5")
        val otherRowIndex = tags.indexOf("agenda-schedule-row-e2")

        assertTrue("计时线索必须渲染：" + tags, cueIndex >= 0)
        assertTrue("正在计时的日程行必须渲染：" + tags, timingRowIndex >= 0)
        assertTrue("其它日程行必须渲染：" + tags, otherRowIndex >= 0)
        assertTrue("计时线索应落在正在计时的那一行：" + tags, cueIndex == timingRowIndex + 1)
        assertTrue("正在计时的条目应固定在时间轴第一位，实际顺序：" + tags, timingRowIndex < otherRowIndex)
    }

    @Test
    fun a_running_timer_shows_elapsed_status_and_three_controls() {
        // 运行中：已投入时长、颜色之外的状态语义，以及暂停 / 结束并保存两个主控制。
        val intents = RecordingAgendaActions()
        mount(planningDayScreenState(planningDay, activeTimer = runningTimer()), intents)

        composeRule.onNodeWithTag("agenda-running-timer-elapsed", useUnmergedTree = true)
            .performScrollTo().assert(hasText("已投入 3 分钟"))
        // 状态不只靠锈橙色：图标与文字都说出「计时中」。
        composeRule.onNodeWithTag("agenda-running-timer-status", useUnmergedTree = true)
            .performScrollTo().assert(hasText("计时中"))
        node("计时中").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("计时中").assertExists()
        // 断言「不存在」时不能用 node()：那个 helper 会先 performScrollTo，
        // 而滚动一个不存在的节点本身就是失败，「已暂停」根本到不了断言。
        composeRule.onNodeWithText("已暂停").assertDoesNotExist()

        composeRule.onNodeWithTag("agenda-running-timer-pause", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-running-timer-stop", useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(1, intents.pausedTimers)
        assertEquals(1, intents.stoppedTimers)
    }

    @Test
    fun the_running_timer_carries_a_brand_emphasis_chip_that_no_schedule_row_has() {
        // 锈橙强调是一个只属于运行中计时的强调标记：色块上再挂状态图标语义，
        // 日程条目行不带它，因此「这一条醒着」不会被读成一整页都醒着。
        mount(planningDayScreenState(planningDay, activeTimer = runningTimer()))

        composeRule.onNodeWithTag("agenda-running-timer-tint", useUnmergedTree = true)
            .assertExists()
            .assert(hasContentDescription("计时中"))
        composeRule.onNodeWithTag("agenda-running-timer", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasTestTag("agenda-running-timer-tint")))
        // 整页只有计时卡带这个强调标记，日程条目行不带。
        val emphasisMarks = composeRule.onAllNodesWithTag("agenda-running-timer-tint", useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("计时强调标记只能出现在运行中的计时上", 1, emphasisMarks.size)
    }

    @Test
    fun a_paused_timer_says_it_is_unsaved_and_offers_resume_or_end() {
        // 暂停不是保存：卡片必须说清「尚未保存」，并把恢复与结束并保存摆出来。
        val intents = RecordingAgendaActions()
        mount(planningDayScreenState(planningDay, activeTimer = pausedTimer()), intents)

        composeRule.onNodeWithTag("agenda-running-timer-status", useUnmergedTree = true)
            .performScrollTo().assert(hasText("已暂停"))
        composeRule.onNodeWithContentDescription("已暂停").assertExists()
        val hint = node("已投入的 3 分钟尚未保存：恢复继续计时，或结束并保存后计入实际投入。", substring = false)
        hint.assertIsDisplayed()

        composeRule.onNodeWithTag("agenda-running-timer-resume", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-running-timer-stop", useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(1, intents.resumedTimers)
        assertEquals(1, intents.stoppedTimers)
        composeRule.onNodeWithTag("agenda-running-timer-pause", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun the_more_menu_keeps_the_secondary_timer_actions_off_the_first_screen() {
        // 低频动作收进更多菜单：切换目标与从此刻继续都不占首屏。
        val intents = RecordingAgendaActions()
        mount(planningDayScreenState(planningDay, activeTimer = runningTimer()), intents)

        composeRule.onNodeWithTag("agenda-running-timer-more", useUnmergedTree = true).performScrollTo()
        composeRule.onNodeWithTag("agenda-running-timer-recover", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("agenda-running-timer-switch-t1", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithTag("agenda-running-timer-more", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-running-timer-recover", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("agenda-running-timer-more", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-running-timer-switch-t1", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("agenda-running-timer-more", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-running-timer-switch-unowned", useUnmergedTree = true).performClick()

        assertEquals(listOf("recover"), intents.dataIntents)
        assertEquals(listOf("t1", "unowned"), intents.switchedTimerTargets)
    }

    @Test
    fun the_more_menu_marks_the_current_timer_target_in_words() {
        // 当前目标同样用文字标出，不靠高亮颜色单独表达。
        mount(planningDayScreenState(planningDay, activeTimer = runningTimer()))

        composeRule.onNodeWithTag("agenda-running-timer-more", useUnmergedTree = true).performScrollTo().performClick()

        composeRule.onNodeWithTag("agenda-running-timer-switch-t1")
            .assert(hasText("切换到「工作」（当前）"))
    }

    @Test
    fun a_time_record_can_be_edited_and_deleted_after_confirmation() {
        // 手动时间账本的编辑与删除跟着日程页走：编辑发 intent，删除先确认再发 intent
        val intents = RecordingAgendaActions()
        mount(planningDayScreenState(planningDay, timeRecords = listOf(record())), intents)

        composeRule.onNodeWithTag("edit-time-r1", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("edit-time-minutes-r1", useUnmergedTree = true).performScrollTo()
            .performTextReplacement("90")
        // 展开后的编辑行贴着页面底部，可能落在悬浮主操作下面；这条用例钉的是保存发出的 intent，
        // 因此直接触发按钮的语义点击，不和悬浮层抢触摸。
        composeRule.onNodeWithTag("save-time-r1", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("r1" to 90), intents.editedRecords.map { it.entryId to it.minutes })

        composeRule.onNodeWithTag("delete-time-r1", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG_CONFIRM, useUnmergedTree = true).performClick()
        assertEquals(listOf("r1"), intents.deletedRecords)
    }

    // ------------------------------------------------------- 停止计时的结果面板（#194）

    @Test
    fun the_result_panel_shows_actual_planned_completion_and_attribution() {
        // 停止之后用户能核对的四件事：实际投入、计划时长、完成结果与记录归属
        mount(planningDayScreenState(planningDay, stopResult = shortStopResult()))

        node("本次计时结果").assertIsDisplayed()
        node("实际投入 20 分钟").assertIsDisplayed()
        node("计划时长 60 分钟").assertIsDisplayed()
        node("未完成").assertIsDisplayed()
        node("记录归属：工作").assertIsDisplayed()
    }

    @Test
    fun a_stop_short_of_the_plan_offers_two_separate_decisions() {
        // 不足计划时，「保留未完成」与「标记为完成」是两个可以分别决定的动作
        val intents = RecordingAgendaActions()
        mount(planningDayScreenState(planningDay, stopResult = shortStopResult()), intents)

        composeRule.onNodeWithTag("agenda-stop-result", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-stop-keep-incomplete", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(listOf("dismiss"), intents.dataIntents)
        assertTrue("保留未完成不写入完成意图", intents.confirmedCompletions.isEmpty())

        composeRule.onNodeWithTag("agenda-stop-mark-completed", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(listOf("e1"), intents.markedStopCompletions)
    }

    @Test
    fun a_stop_reaching_the_plan_is_already_completed_and_asks_for_nothing() {
        // 实际投入达到计划时日程已完成，用户不必再勾一次：面板不再摆出两个决定。
        // 是否还摆出两个决定由投影的 needsCompletionDecision 决定（没有计划时长、未完成时由用户自己决定），
        // 所以这条用例必须把它和完成状态一起改成 false，否则页面照旧摆出两个决定。
        mount(
            planningDayScreenState(
                planningDay,
                stopResult = shortStopResult().copy(
                    completed = true,
                    completionLabel = "已完成",
                    needsCompletionDecision = false,
                ),
            ),
        )

        node("已完成").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-stop-keep-incomplete", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("agenda-stop-mark-completed", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun an_entry_without_a_planned_duration_is_not_auto_completed() {
        // 没有计划时长的日程不由系统猜测完成：完成状态仍由用户手动决定
        mount(
            planningDayScreenState(
                planningDay,
                stopResult = shortStopResult().copy(
                    plannedMinutes = null,
                    plannedLabel = "未设置计划时长",
                    needsCompletionDecision = false,
                ),
            ),
        )

        node("未设置计划时长").assertIsDisplayed()
        node("未完成").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-stop-keep-incomplete", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun a_stop_split_across_planning_days_shows_every_record_it_produced() {
        // 跨规划日的那次停止如实呈现多笔，不自行合成一笔
        mount(
            planningDayScreenState(
                planningDay,
                stopResult = shortStopResult().copy(
                    records = listOf(
                        AgendaStoppedRecord("t1", "2026-03-05", 60, "2026-03-05 · 60 分钟"),
                        AgendaStoppedRecord("t2", "2026-03-06", 30, "2026-03-06 · 30 分钟"),
                    ),
                    actualMinutes = 90,
                    actualLabel = "实际投入 90 分钟",
                ),
            ),
        )

        node("2026-03-05 · 60 分钟").assertIsDisplayed()
        node("2026-03-06 · 30 分钟").assertIsDisplayed()
        node("实际投入 90 分钟").assertIsDisplayed()
    }

    @Test
    fun there_is_no_result_panel_before_a_timer_is_stopped() {
        mount(planningDayScreenState(planningDay))

        composeRule.onNodeWithTag("agenda-stop-result", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun a_timeline_row_shows_both_a_completion_check_and_a_more_menu() {
        // 完成勾选框与更多菜单并存：长按只能作为额外的熟练操作快捷方式
        mount(planningDayScreenState(planningDay, timelineRows = listOf(row(reportEntry, "2026-03-05 · 60 分钟 · 工作 · 无提醒", true, targetLabel = "工作"))))

        composeRule.onNodeWithTag("schedule-completion-e1", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-completion-check-e1", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-more-e1", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun the_row_completion_check_and_the_more_menu_each_emit_their_own_intent() {
        // 两个可见入口各自可达：勾选框改变完成状态，更多菜单收次级动作
        val intents = RecordingAgendaActions()
        mount(planningDayScreenState(planningDay, timelineRows = listOf(row(reportEntry, "2026-03-05 · 60 分钟 · 工作 · 无提醒", true, targetLabel = "工作"))), intents)

        composeRule.onNodeWithTag("schedule-completion-check-e1", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(listOf("e1"), intents.confirmedCompletions)

        // 更多菜单里真正发出完成意图的是「完成并记录」那一项：「修改日程」只是打开编辑对话框，
        // 本身不发 intent，因此不能拿它来断言「菜单发出了自己的意图」。
        composeRule.onNodeWithTag("schedule-more-e1", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("schedule-more-completion-e1", useUnmergedTree = true).performClick()
        assertEquals(listOf("e1", "e1"), intents.confirmedCompletions)
    }

    @Test
    fun tapping_the_row_card_opens_the_same_row_menu_as_the_more_button() {
        mount(
            planningDayScreenState(
                planningDay,
                timelineRows = listOf(row(reportEntry, "2026-03-05 · 60 分钟 · 工作 · 无提醒", true, targetLabel = "工作")),
            ),
        )

        // 行卡是 pointerInput 入口，没有语义 OnClick，因此必须用真实坐标点击。
        composeRule.onNodeWithTag("agenda-schedule-row-e1", useUnmergedTree = true)
            .performScrollTo()
            .performTouchInput { click(percentOffset(0.1f, 0.5f)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("schedule-more-edit-e1", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(hasAnyDescendant(hasText("修改日程")))
        composeRule.onNodeWithTag("schedule-more-start-timer-e1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-more-completion-e1", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("schedule-more-edit-e1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("schedule-edit-save-e1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tapping_the_row_completion_check_does_not_open_the_row_menu() {
        // 整卡点击不能吞掉完成勾选框：点勾选框只发完成 intent，不展开行菜单。
        val intents = RecordingAgendaActions()
        mount(
            planningDayScreenState(
                planningDay,
                timelineRows = listOf(row(reportEntry, "2026-03-05 · 60 分钟 · 工作 · 无提醒", true, targetLabel = "工作")),
            ),
            intents,
        )

        composeRule.onNodeWithTag("schedule-completion-check-e1", useUnmergedTree = true)
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(listOf("e1"), intents.confirmedCompletions)
        composeRule.onNodeWithTag("schedule-more-edit-e1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun a_completed_row_menu_offers_undo_completion() {
        mount(
            planningDayScreenState(
                planningDay,
                timelineRows = listOf(
                    row(
                        reportEntry,
                        "2026-03-05 · 60 分钟 · 无提醒",
                        true,
                        investmentLabel = "计划投入 60 分钟 · 实际投入 60 分钟 · 已完成",
                        completed = true,
                    ),
                ),
            ),
        )

        openRowMenu("e1")

        composeRule.onNodeWithTag("schedule-more-completion-e1", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(hasAnyDescendant(hasText("取消完成")))
    }

    @Test
    fun an_investment_completed_row_toggles_instead_of_confirming_a_second_investment() {
        val intents = RecordingAgendaActions()
        mount(
            planningDayScreenState(
                planningDay,
                timelineRows = listOf(
                    row(
                        reportEntry,
                        "2026-03-05 · 60 分钟 · 无提醒",
                        true,
                        investmentLabel = "计划投入 60 分钟 · 实际投入 60 分钟 · 已完成",
                        completed = true,
                    ),
                ),
            ),
            intents,
        )

        composeRule.onNodeWithTag("schedule-completion-e1", useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(listOf("e1"), intents.toggledCompletions)
        assertEquals(emptyList<String>(), intents.confirmedCompletions)
    }

    /** 一次停止后待核对的结果：投入不足计划，完成与否仍由用户决定。 */
    private fun shortStopResult() = AgendaStopResult(
        scheduleEntryId = "e1",
        records = listOf(AgendaStoppedRecord("t1", "2026-03-05", 20, "2026-03-05 · 20 分钟")),
        actualMinutes = 20,
        actualLabel = "实际投入 20 分钟",
        plannedMinutes = 60,
        plannedLabel = "计划时长 60 分钟",
        completed = false,
        completionLabel = "未完成",
        needsCompletionDecision = true,
        attributionLabel = "工作",
    )

    /** 日程页是可滚动长页，断言与点击前先滚到目标节点。 */
    private fun node(text: String, substring: Boolean = false) =
        composeRule.onNodeWithText(text, substring = substring).performScrollTo()

    @Test
    fun the_first_screen_shows_its_sections_in_the_fixed_workbench_order() {
        // 首屏自上而下固定为：规划日与每日容量摘要、时间轴、进行中的计时状态、自由计时、事后补记，
        // 重复日程作为折叠的次级入口排在最后；添加与补记的主入口常驻在右下角，不占区块顺序。
        // 这条用例只管**整页区块顺序**；「正在计时的条目固定在时间轴顶部」是时间轴内部的事，
        // 由 [the_running_schedule_is_pinned_to_the_top_of_the_timeline] 单独断言。
        // 计时区块只有真的有计时时才占位，所以这条用例必须注入一个运行中的计时。
        // 六个区块不可能同时落在窗口内：顺序由语义树先序给出，坐标在这个长滚动页上没有意义。
        mount(
            planningDayScreenState(
                selected = planningDay,
                activeTimer = runningTimer(),
                timeRecords = listOf(record()),
            ),
        )

        val tags = orderedTestTags()
        val sections = listOf(
            "agenda-section-summary",
            "agenda-section-timeline",
            "agenda-section-timer",
            "agenda-section-add",
            "agenda-section-backfill",
            "agenda-section-recurrence",
        )
        val indices = sections.map { section ->
            tags.indexOf(section).also {
                assertTrue("区块必须渲染：$section，实际顺序：$tags", it >= 0)
            }
        }

        assertEquals("区块顺序：$tags", indices.sorted(), indices)
    }

    @Test
    fun there_is_no_timer_section_before_a_timer_is_started() {
        // 没有运行中的计时时，计时区块不留空占位：首屏因此不出现一块空区块。
        // 这是 #180 的顺序规则（「进行中的计时状态」在时间轴之后）在无计时时的表现，
        // 而不是一条被忽略的顺序——因此这里断言的是**不存在**。
        mount(planningDayScreenState(planningDay))

        composeRule.onNodeWithTag("agenda-section-timer", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("agenda-running-timer", useUnmergedTree = true).assertDoesNotExist()
        // 没有计时时其余区块照旧在位，缺的是计时这一块而不是整页。
        composeRule.onNodeWithTag("agenda-section-summary", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("agenda-section-timeline", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("agenda-section-add", useUnmergedTree = true).assertExists()
    }

    @Test
    fun the_workbench_summarises_the_planning_day_read_only() {
        // 工作台只读展示规划日与三项紧凑投入读数；完整设置表单不在工作台，
        // 只由容量摘要卡指向统计页底部的「设置与数据」。
        mount(planningDayScreenState(planningDay))

        composeRule.onNodeWithTag("agenda-filter", useUnmergedTree = true)
            .assert(hasText("筛选：规划日 2026-03-05"))
        composeRule.onNodeWithTag("agenda-summary-capacity", useUnmergedTree = true)
            .assert(hasText("每日容量 2 小时", substring = true))
            .assert(hasText("剩余 90 分钟", substring = true))
            .assert(hasText("状态 未开始", substring = true))
        composeRule.onNodeWithTag("agenda-summary-planned", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("已排时间")))
            .assert(hasAnyDescendant(hasText("30分")))
        composeRule.onNodeWithTag("agenda-summary-actual", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("实际投入")))
            .assert(hasAnyDescendant(hasText("0分")))
        composeRule.onNodeWithTag("agenda-summary-capacity-status", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("未开始")))
        composeRule.onNodeWithTag("agenda-capacity-progress", useUnmergedTree = true).assertExists()

        // 详细边界与周起始设置不占紧凑摘要主内容，仍由现有设置入口负责。
        composeRule.onNodeWithText("规划日界线 03:00").assertDoesNotExist()
        composeRule.onNodeWithText("周起始日 周一").assertDoesNotExist()

        // 容量摘要只读，完整表单住在统计页底部的「设置与数据」。
        assertTrue(
            "容量与设置读数区里不提供任何可编辑输入",
            composeRule.onAllNodes(
                hasTestTag("agenda-open-settings") and hasAnyDescendant(hasSetTextAction()),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun the_compact_summary_shows_unset_capacity_without_a_fake_progress() {
        mount(
            planningDayScreenState(planningDay).copy(
                workbenchSummary = AgendaWorkbenchSummary(
                    planningDateLabel = "规划日 $planningDay",
                    boundaryLabel = "规划日界线 03:00",
                    weekStartLabel = "周起始日 周一",
                    capacitySettingLabel = "每日容量未设置",
                    capacityUsageLabel = "已安排 45 分钟（每日容量未设置）",
                    plannedMinutes = 45,
                    actualMinutes = 0,
                    capacityStatusLabel = "每日容量未设置",
                ),
            ),
        )

        composeRule.onNodeWithTag("agenda-summary-capacity", useUnmergedTree = true)
            .assert(hasText("未设置", substring = true))
            .assert(hasText("状态 未开始", substring = true))
        composeRule.onNodeWithTag("agenda-summary-planned", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("45分")))
        composeRule.onNodeWithTag("agenda-summary-actual", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("0分")))
        composeRule.onNodeWithTag("agenda-summary-capacity-status", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("未开始")))
        // 容量未设置时仍保留一条空轨道，但状态是「未开始」，不会画出一段假进度。
        composeRule.onNodeWithTag("agenda-capacity-progress", useUnmergedTree = true).assertExists()
    }

    @Test
    fun the_compact_summary_shows_overload_status_and_keeps_the_progress_capped() {
        mount(
            planningDayScreenState(planningDay).copy(
                workbenchSummary = AgendaWorkbenchSummary(
                    planningDateLabel = "规划日 $planningDay",
                    boundaryLabel = "规划日界线 03:00",
                    weekStartLabel = "周起始日 周一",
                    capacitySettingLabel = "每日容量 120 分钟",
                    capacityUsageLabel = "已安排 150 / 120 分钟",
                    capacityMinutes = 120,
                    plannedMinutes = 150,
                    actualMinutes = 30,
                    capacityOverloaded = true,
                    capacityStatusLabel = "超载 30 分钟",
                    capacityProgress = 1f,
                ),
            ),
        )

        composeRule.onNodeWithTag("agenda-summary-capacity", useUnmergedTree = true)
            .assert(hasText("每日容量 2 小时", substring = true))
            .assert(hasText("超载 30 分钟", substring = true))
            .assert(hasText("状态 超载", substring = true))
        composeRule.onNodeWithTag("agenda-summary-planned", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("2小时30分")))
        composeRule.onNodeWithTag("agenda-summary-actual", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("30分")))
        composeRule.onNodeWithTag("agenda-summary-capacity-status", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("超载")))
        composeRule.onNodeWithTag("agenda-capacity-progress", useUnmergedTree = true).assertExists()
    }

    @Test
    fun a_timeline_row_shows_planned_investment_actual_investment_and_completion() {
        // 时间轴行同时展示标题、关联计划、计划投入、实际投入与完成状态
        mount(
            planningDayScreenState(
                selected = planningDay,
                timelineRows = listOf(
                    row(
                        reportEntry,
                        "2026-03-05 · 60 分钟 · 工作 · 无提醒",
                        true,
                        investmentLabel = "计划投入 60 分钟 · 实际投入 0 分钟 · 未完成",
                        targetLabel = "工作",
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag("agenda-schedule-row-e1", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasAnyDescendant(hasText("写周报")))
            .assert(hasAnyDescendant(hasText("2026-03-05 · 60 分钟 · 工作 · 无提醒")))
            .assert(hasAnyDescendant(hasText("计划投入 60 分钟 · 实际投入 0 分钟 · 未完成")))
    }

    @Test
    fun the_entry_being_timed_is_rendered_at_the_top_of_the_timeline_with_a_text_cue() {
        // 正在计时的条目固定在时间轴顶部，并用文字标明状态而不只靠颜色
        mount(
            planningDayScreenState(
                selected = planningDay,
                timelineRows = listOf(
                    row(
                        looseEntry.copy(planningDate = "2026-03-05"),
                        "2026-03-05 · 30 分钟 · 无提醒",
                        false,
                        investmentLabel = "计划投入 30 分钟 · 实际投入 12 分钟 · 未完成",
                        timing = true,
                    ),
                    row(
                        reportEntry,
                        "2026-03-05 · 60 分钟 · 工作 · 无提醒",
                        false,
                        targetLabel = "工作",
                    ),
                ),
            ),
        )

        val tags = orderedTestTags()
        val cueIndex = tags.indexOf("schedule-timing-cue")
        val timingRowIndex = tags.indexOf("agenda-schedule-row-e3")
        val otherRowIndex = tags.indexOf("agenda-schedule-row-e1")

        assertTrue("计时线索必须渲染：" + tags, cueIndex >= 0)
        assertTrue("正在计时的日程行必须渲染：" + tags, timingRowIndex >= 0)
        assertTrue("其它日程行必须渲染：" + tags, otherRowIndex >= 0)
        assertTrue("计时线索落在正在计时的那一行：" + tags, cueIndex == timingRowIndex + 1)
        assertTrue("正在计时的条目排在时间轴顶部：" + tags, timingRowIndex < otherRowIndex)
        node("正在计时").assertIsDisplayed()
    }

    @Test
    fun an_overloaded_entry_shows_the_capacity_notice_and_keeps_its_actions() {
        // 容量超载只是提示：编辑与起计时入口都还在
        mount(
            planningDayScreenState(
                selected = planningDay,
                timelineRows = listOf(
                    row(
                        overloadedEntry,
                        "2026-03-05 · 300 分钟 · 无提醒",
                        true,
                        investmentLabel = "计划投入 300 分钟 · 实际投入 0 分钟 · 未完成",
                        capacityNotice = "已超出容量",
                    ),
                ),
            ),
        )

        node("已超出容量").assertIsDisplayed()
        openRowMenu("e4")
        composeRule.onNodeWithTag("schedule-more-edit-e4", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-more-start-timer-e4", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun entries_without_a_time_range_stay_visible_in_the_unscheduled_group() {
        // 没有具体时间的日程进入独立的「未安排」区域，仍然看得见
        mount(
            planningDayScreenState(
                selected = planningDay,
                timelineRows = listOf(
                    row(meetingEntry, "2026-03-06 · 30 分钟 · 无提醒 · 已完成", true),
                ),
                unscheduledRows = listOf(row(looseEntry, "2026-03-05 · 15 分钟 · 无提醒", true)),
            ),
        )

        composeRule.onNodeWithTag("agenda-side-unscheduled", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasAnyDescendant(hasText("未安排")))
        node("想到再做的").assertIsDisplayed()
    }

    @Test
    fun an_empty_planning_day_invites_the_first_schedule_and_opens_the_add_sheet() {
        // 当天没有日程时显示「安排今天第一件事」，常驻主操作仍可直接打开添加面板
        mount(emptyPlanningDayScreenState())

        node("安排今天第一件事").assertIsDisplayed()
        openAddScheduleSheet()

        composeRule.onNodeWithTag("agenda-add-sheet", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-title-input", useUnmergedTree = true).assertIsDisplayed()
    }

    /** 打开常驻在右下角的「调整计划」菜单。 */
    private fun openAdjustPlanMenu() {
        composeRule.onNodeWithTag("agenda-plan-action", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    /** 通过「调整计划」菜单打开添加日程底部面板。 */
    private fun openAddScheduleSheet() {
        openAdjustPlanMenu()
        composeRule.onNodeWithTag("agenda-plan-menu-add", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    /** 通过「调整计划」菜单打开事后补记。 */
    private fun openBackfillSheet() {
        openAdjustPlanMenu()
        composeRule.onNodeWithTag("agenda-plan-menu-backfill", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    /** 从时间轴行尾的「更多」打开该行的操作菜单。 */
    private fun openRowMenu(entryId: String) {
        composeRule.onNodeWithTag("schedule-more-$entryId", useUnmergedTree = true)
            .performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    /** 展开添加日程面板里的「更多设置」，露出备注、提醒与关联计划。 */
    private fun expandAddScheduleMoreSettings() {
        composeRule.onNodeWithTag("agenda-add-more-toggle", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    /**
     * 一次布局快照里出现的全部 test tag，按语义树的先序排列。
     *
     * 顺序必须在同一份布局上比较，因此这里刻意不滚动；也刻意不比较坐标：
     * 折叠线之外的节点坐标会被裁到 0，只有先序能表达「谁排在谁前面」。
     */
    private fun orderedTestTags(): List<String> {
        val tags = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.TestTag)?.let(tags::add)
            node.children.forEach(::walk)
        }
        walk(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return tags
    }

    private val planningDay: LocalDate = LocalDate.of(2026, 3, 5)

    private val reportEntry = ScheduleEntry(
        id = "e1",
        planningDate = "2026-03-05",
        title = "写周报",
        estimatedMinutes = 60,
        targetId = "t1",
    )

    private val meetingEntry = ScheduleEntry(
        id = "e2",
        planningDate = "2026-03-06",
        title = "开组会",
        estimatedMinutes = 30,
        completed = true,
    )

    /** 没有具体时间的日程：它属于这一天，但落在「未安排时间」区域。 */
    private val looseEntry = ScheduleEntry(
        id = "e3",
        planningDate = "2026-03-05",
        title = "想到再做的",
        estimatedMinutes = 15,
    )

    /** 超出每日容量的日程：提示是可解释的，编辑与计时入口仍然在。 */
    private val overloadedEntry = ScheduleEntry(
        id = "e4",
        planningDate = "2026-03-05",
        title = "一整天的课",
        estimatedMinutes = 300,
    )

    /** 手写的行：展示文本由测试自己钉住，条目本身仍带自己的字段供 intent 使用。 */
    private fun row(
        entry: ScheduleEntry,
        detailLabel: String,
        canStartTimer: Boolean,
        investmentLabel: String = "计划投入 ${entry.estimatedMinutes ?: 0} 分钟 · 实际投入 0 分钟 · " +
            if (entry.completed) "已完成" else "未完成",
        targetLabel: String? = null,
        capacityNotice: String? = null,
        timing: Boolean = false,
        completed: Boolean = entry.completed,
    ) = AgendaScheduleRow(
        entry = entry,
        title = entry.title,
        detailLabel = detailLabel,
        note = "",
        startText = "",
        endText = "",
        estimateText = entry.estimatedMinutes?.toString() ?: "",
        reminderText = "",
        targetId = entry.targetId,
        canStartTimer = canStartTimer,
        investmentLabel = investmentLabel,
        targetLabel = targetLabel,
        capacityNotice = capacityNotice,
        timing = timing,
        completed = completed,
    )

    private val availableDates = listOf(
        AgendaDateOption(planningDay, "规划日 2026-03-05"),
        AgendaDateOption(LocalDate.of(2026, 3, 6), "规划日 2026-03-06"),
    )

    private val recurrenceRules = listOf(
        AgendaRecurrenceRuleLine(ruleId = "rule-daily", label = "每日晨读 · 每日 · 2026-03-05", active = true),
        AgendaRecurrenceRuleLine(
            ruleId = "rule-weekly",
            label = "每周复盘 · 每周 一,三 · 2026-03-02 至 2026-03-31",
            active = false,
        ),
    )

    private val targets = listOf(AgendaTargetOption(targetId = "t1", title = "工作"))

    /** 工作台顶部的只读摘要：规划日与容量/计划/实际投入事实。 */
    private fun summary(planningDateLabel: String, capacityUsageLabel: String?) = AgendaWorkbenchSummary(
        planningDateLabel = planningDateLabel,
        boundaryLabel = "规划日界线 03:00",
        weekStartLabel = "周起始日 周一",
        capacitySettingLabel = "每日容量 120 分钟",
        capacityUsageLabel = capacityUsageLabel,
        capacityMinutes = capacityUsageLabel?.let { 120 },
        plannedMinutes = capacityUsageLabel?.let { 30 },
        actualMinutes = capacityUsageLabel?.let { 0 },
        remainingMinutes = capacityUsageLabel?.let { 90 },
        capacityStatusLabel = capacityUsageLabel?.let { "剩余 90 分钟" },
        capacityProgress = capacityUsageLabel?.let { 0.25f },
    )

    private val allDatesSummary = AgendaWorkbenchSummary(
        planningDateLabel = "所有日期",
        boundaryLabel = "规划日界线 03:00",
        weekStartLabel = "周起始日 周一",
        capacitySettingLabel = "每日容量 120 分钟",
        capacityUsageLabel = "已安排 90 / 240 分钟",
        capacityMinutes = 240,
        plannedMinutes = 90,
        actualMinutes = 0,
        remainingMinutes = 150,
        capacityStatusLabel = "剩余 150 分钟",
        capacityProgress = 0.375f,
    )

    /** 所有日期特殊视图：条目按规划日分组，每组带这一天的每日容量与自己的时间轴。 */
    private fun allDatesScreenState(canStartTimer: Boolean = true): AgendaScreenState = AgendaScreenState(
        planningDate = planningDay,
        selectedDate = null,
        headingLabel = "所有日期",
        filterLabel = "筛选：所有日期",
        availableDates = availableDates,
        selectedCapacityLabel = null,
        workbenchSummary = allDatesSummary,
        dateSections = listOf(
            AgendaDateSection(
                planningDate = "2026-03-05",
                capacityLabel = "已安排 60 / 120 分钟",
                timelineRows = listOf(
                    row(
                        reportEntry,
                        "2026-03-05 · 60 分钟 · 工作 · 无提醒",
                        canStartTimer,
                        targetLabel = "工作",
                        timing = true,
                    ),
                ),
                unscheduledRows = listOf(row(looseEntry, "2026-03-05 · 15 分钟 · 无提醒", canStartTimer)),
            ),
            AgendaDateSection(
                planningDate = "2026-03-06",
                capacityLabel = "已安排 30 / 120 分钟",
                timelineRows = listOf(
                    row(meetingEntry, "2026-03-06 · 30 分钟 · 无提醒 · 已完成", canStartTimer),
                ),
                unscheduledRows = emptyList(),
            ),
        ),
        emptyLabel = null,
        recurrenceRules = recurrenceRules,
        targets = targets,
        pendingEditRow = null,
    )

    /** 选中某个规划日的视图：标题、筛选文案与容量卡片都跟着这一天。 */
    private fun planningDayScreenState(
        selected: LocalDate,
        activeTimer: AgendaActiveTimer? = null,
        timeRecords: List<AgendaTimeRecord> = emptyList(),
        timelineRows: List<AgendaScheduleRow> = listOf(
            row(meetingEntry, "2026-03-06 · 30 分钟 · 无提醒 · 已完成", true),
        ),
        unscheduledRows: List<AgendaScheduleRow> = emptyList(),
        stopResult: AgendaStopResult? = null,
    ): AgendaScreenState = AgendaScreenState(
        planningDate = planningDay,
        selectedDate = selected,
        headingLabel = "规划日 $selected",
        filterLabel = "筛选：规划日 $selected",
        availableDates = availableDates,
        selectedCapacityLabel = "已安排 30 / 120 分钟",
        workbenchSummary = summary("规划日 $selected", "已安排 30 / 120 分钟"),
        timelineRows = timelineRows,
        unscheduledRows = unscheduledRows,
        dateSections = emptyList(),
        emptyLabel = null,
        recurrenceRules = recurrenceRules,
        targets = targets,
        pendingEditRow = null,
        ranges = listOf(
            AgendaRangeOption(AgendaRangeScope.TODAY, "今天", planningDay, selected == planningDay),
            AgendaRangeOption(AgendaRangeScope.TOMORROW, "明天", planningDay.plusDays(1), selected == planningDay.plusDays(1)),
            AgendaRangeOption(AgendaRangeScope.ALL_DATES, "所有日期", null, false),
        ),
        highlightAddSchedule = selected == planningDay.plusDays(1),
        addScheduleHeadingLabel = if (selected == planningDay.plusDays(1)) "为明天（2026-03-06）添加日程" else "新增日程",
        activeTimer = activeTimer,
        stopResult = stopResult,
        timeRecords = timeRecords,
        recordedMinutesLabel = if (timeRecords.isEmpty()) "" else "本规划日已记录：30 分钟",
    )

    /** 运行中的计时：目标名、已经投入的分钟数与状态文字都由投影给出。 */
    private fun runningTimer() = AgendaActiveTimer(
        targetLabel = "写周报",
        targetId = "t1",
        paused = false,
        elapsedMinutes = 3,
        elapsedLabel = "已投入 3 分钟",
        statusLabel = "计时中",
        pendingHintLabel = null,
    )

    /** 暂停中的计时：投影已经把「尚未保存」与下一步写成一句话。 */
    private fun pausedTimer() = runningTimer().copy(
        paused = true,
        statusLabel = "已暂停",
        pendingHintLabel = "已投入的 3 分钟尚未保存：恢复继续计时，或结束并保存后计入实际投入。",
    )

    /** 一条手动记的实际投入。 */
    private fun record() = AgendaTimeRecord(
        entryId = "r1",
        durationMinutes = 30,
        targetLabel = "工作",
        targetId = "t1",
        source = TimeEntrySource.MANUAL,
        note = "",
    )

    /** 一条日程都没有时的视图：空态提示由页面自己渲染。 */
    private fun emptyScreenState(): AgendaScreenState = AgendaScreenState(
        planningDate = planningDay,
        selectedDate = null,
        headingLabel = "所有日期",
        filterLabel = "筛选：所有日期",
        availableDates = listOf(AgendaDateOption(planningDay, "规划日 2026-03-05")),
        selectedCapacityLabel = null,
        workbenchSummary = summary("所有日期", null),
        dateSections = emptyList(),
        emptyLabel = "所有日期暂无日程条目。",
        recurrenceRules = emptyList(),
        targets = emptyList(),
        pendingEditRow = null,
    )

    /** 选中规划日却一条日程都没有：空态直接给出添加日程入口。 */
    private fun emptyPlanningDayScreenState(): AgendaScreenState = planningDayScreenState(
        selected = planningDay,
        timelineRows = emptyList(),
    ).copy(
        workbenchEmptyLabel = "安排今天第一件事",
        emptyLabel = "规划日 2026-03-05 暂无日程条目。",
        recurrenceRules = emptyList(),
    )

    // #195：首次读取本地数据期间的轻量时间轴骨架。加载与读取失败都是组合根注入的事实，
    // 页面只按它们渲染；骨架复用既有的区块容器标识，因此区块结构与顺序没有被重排。
    @Test
    fun the_first_load_renders_a_light_timeline_skeleton_in_the_workbench_sections() {
        mount(planningDayScreenState(planningDay).copy(loading = true))

        composeRule.onNodeWithTag(AttentionStateKind.LOADING.rootTag, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("agenda-section-summary", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("agenda-section-timeline", useUnmergedTree = true).assertExists()
        // 骨架是轻量的占位，不是把真实区块提前画出来：日程行与表单都不在。
        composeRule.onNodeWithTag("agenda-title-input", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onAllNodesWithTag("agenda-skeleton-row", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .let { assertTrue("时间轴骨架至少要有占位行", it.isNotEmpty()) }
        composeRule.onNodeWithTag(AttentionStateKind.ERROR.rootTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun a_local_read_failure_shows_an_error_state_with_a_retry_instead_of_a_blank_page() {
        val intents = RecordingAgendaActions()
        mount(planningDayScreenState(planningDay).copy(loadError = "磁盘读不出来"), intents)

        composeRule.onNodeWithTag(AttentionStateKind.ERROR.rootTag, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(AttentionStateKind.ERROR.descriptionTag, useUnmergedTree = true)
            .assert(hasText("磁盘读不出来"))
        // 错误状态带的是「重试」而不是只有关闭。
        // 按钮是可点击节点，它的文字在合并语义树上：在 useUnmergedTree 上找得到按钮，
        // 读不到按钮里的文字，因此这一处按合并树断言「重试」。
        composeRule.onNodeWithTag(AttentionStateKind.ERROR.actionTag)
            .assertIsDisplayed()
            .assert(hasText(AGENDA_RETRY_LABEL))

        composeRule.onNodeWithTag(AttentionStateKind.ERROR.actionTag, useUnmergedTree = true).performClick()
        assertEquals(1, intents.retriedLoads)
    }

    @Test
    fun retrying_after_a_read_failure_lands_on_the_working_timeline() {
        // 重试之后组合根重新注入一份读上来的状态：页面回到正常工作台，而不是停在错误或空白上。
        // 组合根重读之后会注入一份读上来的状态。用可观察状态驱动重组，
        // 否则改一个普通局部变量不会让页面重新渲染，断言的是上一次那一帧。
        val actions = RecordingAgendaActions()
        val screenState = mutableStateOf(
            planningDayScreenState(planningDay).copy(loadError = "磁盘读不出来"),
        )
        composeRule.setContent {
            AttentionTheme { AgendaScreen(state = screenState.value, actions = actions) }
        }

        composeRule.onNodeWithTag(AttentionStateKind.ERROR.actionTag, useUnmergedTree = true).performClick()
        assertEquals(1, actions.retriedLoads)
        composeRule.runOnIdle { screenState.value = planningDayScreenState(planningDay) }

        composeRule.onNodeWithTag(AttentionStateKind.ERROR.rootTag, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(AttentionStateKind.LOADING.rootTag, useUnmergedTree = true).assertDoesNotExist()
        node("开组会").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-section-timeline", useUnmergedTree = true).assertExists()
    }

    @Test
    fun the_capacity_summary_takes_the_user_to_the_full_settings() {
        // 容量摘要是只读的，但它是通往完整设置（统计页底部的「设置与数据」）的入口：
        // 页面只发出 intent，实际跳转由组合根完成。
        val intents = RecordingAgendaActions()
        mount(planningDayScreenState(planningDay), intents)

        composeRule.onNodeWithTag("agenda-open-settings", useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(1, intents.openedSettings)
    }

    @Test
    fun the_timeline_never_shows_a_notification_permission_error() {
        // 通知权限提示只在首次启动计时或需要通知栏控制时出现；时间轴本身不持续显示权限错误。
        mount(planningDayScreenState(planningDay, timelineRows = listOf(row(reportEntry, "2026-03-05 · 60 分钟 · 无提醒", true))))

        composeRule.onAllNodesWithTag(AttentionStateKind.PERMISSION_NOT_GRANTED.rootTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .let { assertEquals("时间轴不应持续显示通知权限错误", 0, it.size) }
    }

    private fun mount(
        state: AgendaScreenState,
        actions: AgendaActions = RecordingAgendaActions(),
    ) {
        composeRule.setContent {
            AttentionTheme { AgendaScreen(state = state, actions = actions) }
        }
    }

    private class RecordingAgendaActions : AgendaActions {
        val selectedDates = mutableListOf<LocalDate?>()
        val addedSchedules = mutableListOf<AgendaScheduleDraft>()
        val addedSchedulesWithBackfill = mutableListOf<Pair<AgendaScheduleDraft, AgendaBackfillDraft>>()
        val updatedEntries = mutableListOf<Pair<ScheduleEntry, Int?>>()
        val confirmedCompletions = mutableListOf<String>()
        val toggledCompletions = mutableListOf<String>()
        val markedStopCompletions = mutableListOf<String>()
        val startedEntries = mutableListOf<ScheduleEntry>()
        val startedFreeTimers = mutableListOf<String?>()
        val addedRecurrences = mutableListOf<AgendaRecurrenceDraft>()
        val stoppedRules = mutableListOf<String>()
        val dataIntents = mutableListOf<String>()
        val editedRecords = mutableListOf<EditedRecord>()
        val deletedRecords = mutableListOf<String>()
        var stoppedTimers = 0
        var permissionRequests = 0
        var pausedTimers = 0
        var resumedTimers = 0
        var retriedLoads = 0
        var openedSettings = 0
        val switchedTimerTargets = mutableListOf<String>()

        override fun selectPlanningDate(date: LocalDate?) {
            selectedDates += date
        }

        override fun selectAllDates() {
            dataIntents += "allDates"
        }

        override fun shiftPlanningDay(days: Long) {
            dataIntents += "shift:$days"
        }

        override fun addSchedule(draft: AgendaScheduleDraft) {
            addedSchedules += draft
        }

        override fun addScheduleWithBackfill(draft: AgendaScheduleDraft, backfill: AgendaBackfillDraft) {
            addedSchedulesWithBackfill += draft to backfill
        }

        override fun updateScheduleWithBackfill(
            entry: ScheduleEntry,
            reminderMinuteOfDay: Int?,
            backfill: AgendaBackfillDraft,
        ) = Unit

        override fun updateSchedule(entry: ScheduleEntry, reminderMinuteOfDay: Int?) {
            updatedEntries += entry to reminderMinuteOfDay
        }

        override fun confirmScheduleCompleted(entryId: String) {
            confirmedCompletions += entryId
        }

        override fun toggleScheduleCompleted(entryId: String) {
            toggledCompletions += entryId
        }

        override fun markStoppedScheduleCompleted(entryId: String) {
            markedStopCompletions += entryId
        }

        override fun dismissStopResult() {
            dataIntents += "dismiss"
        }

        override fun startTimerFromEntry(entry: ScheduleEntry) {
            startedEntries += entry
        }

        override fun startFreeTimer(targetId: String?) {
            startedFreeTimers += targetId
        }

        override fun addRecurrenceAndMaterialize(draft: AgendaRecurrenceDraft) {
            addedRecurrences += draft
        }

        override fun stopRecurrence(ruleId: String) {
            stoppedRules += ruleId
        }

        override fun requestReminderPermission() {
            permissionRequests++
        }

        override fun stopRunningTimer() {
            stoppedTimers++
        }

        override fun pauseRunningTimer() {
            pausedTimers++
        }

        override fun resumeRunningTimer() {
            resumedTimers++
        }

        override fun recoverRunningTimerFromNow() {
            dataIntents += "recover"
        }

        override fun switchRunningTimerTarget(targetId: String?) {
            switchedTimerTargets += targetId ?: "unowned"
        }

        override fun editTime(entryId: String, minutes: Int, targetId: String?, note: String) {
            editedRecords += EditedRecord(entryId, minutes, targetId, note)
        }

        override fun deleteTime(entryId: String) {
            deletedRecords += entryId
        }

        override fun retryLoad() {
            retriedLoads += 1
        }

        override fun openSettings() {
            openedSettings += 1
        }
    }

    /** 页面发出的一次编辑 intent：只带被改动的字段，规划日与来源不参与。 */
    private data class EditedRecord(
        val entryId: String,
        val minutes: Int,
        val targetId: String?,
        val note: String,
    )
}
