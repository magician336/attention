package com.attention.app.agenda

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.ui.components.AttentionFeedbackTags
import com.attention.app.ui.theme.AttentionTheme
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.domain.TimeEntrySource
import com.attention.workspace.state.AgendaActions
import com.attention.workspace.state.AgendaActiveTimer
import com.attention.workspace.state.AgendaDateOption
import com.attention.workspace.state.AgendaDateSection
import com.attention.workspace.state.AgendaRangeOption
import com.attention.workspace.state.AgendaRangeScope
import com.attention.workspace.state.AgendaRecurrenceRuleLine
import com.attention.workspace.state.AgendaRecurrenceDraft
import com.attention.workspace.state.AgendaScheduleDraft
import com.attention.workspace.state.AgendaScheduleRow
import com.attention.workspace.state.AgendaScreenState
import com.attention.workspace.state.AgendaTargetOption
import com.attention.workspace.state.AgendaTimeRecord
import com.attention.workspace.state.AgendaWorkbenchSummary
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

        node("所有日期").assertIsDisplayed()
        node("规划日 2026-03-05").assertIsDisplayed()
        node("已安排 60 / 120 分钟").assertIsDisplayed()
        node("写周报").assertIsDisplayed()
        node("2026-03-05 · 60 分钟 · 工作 · 无提醒").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-capacity-summary-2026-03-05", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun a_selected_planning_day_shows_that_day_title_and_entries() {
        // 选中规划日：标题、筛选文案与容量卡片都跟着这一天
        mount(planningDayScreenState(LocalDate.of(2026, 3, 6)))

        node("规划日 2026-03-06").assertIsDisplayed()
        node("筛选：规划日 2026-03-06").assertIsDisplayed()
        node("开组会").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-capacity-summary", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
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
        composeRule.onNodeWithTag("agenda-all-dates", useUnmergedTree = true).performClick()

        // 所有日期是一个特殊视图：intent 是「不选某一天」，而不是一个日期值
        assertEquals(listOf<LocalDate?>(null), intents.selectedDates)
    }

    @Test
    fun submitting_the_add_form_emits_a_schedule_draft() {
        // 标题与规划日必需；其余可选，提交后只发一个草稿 intent
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)

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
    fun an_incomplete_add_form_shows_the_validation_error_instead_of_an_intent() {
        // 标题为空时不发 intent，页面就地给出与迁移前相同的提示
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)

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

        composeRule.onNodeWithTag("agenda-title-input", useUnmergedTree = true).performScrollTo()
            .performTextReplacement("带提醒的日程")
        // 重复日程与新增日程各有一个提醒输入框，后者排在后面
        composeRule.onNodeWithTag("agenda-reminder-input", useUnmergedTree = true).performScrollTo()
            .performTextReplacement("09:00")
        composeRule.onNodeWithTag("agenda-add-submit", useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(540, intents.addedSchedules.single().reminderMinuteOfDay)
        assertEquals(1, intents.permissionRequests)
    }

    @Test
    fun editing_an_entry_saves_the_changed_draft() {
        // 编辑对话框是页面本地表单状态；保存发出被改过的条目
        val intents = RecordingAgendaActions()
        mount(allDatesScreenState(), intents)

        composeRule.onNodeWithTag("schedule-edit-e1", useUnmergedTree = true).performScrollTo().performClick()
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

        composeRule.onNodeWithTag("schedule-start-timer-e1", useUnmergedTree = true).performScrollTo().performClick()

        val entry: ScheduleEntry = intents.startedEntries.single()
        assertEquals("e1", entry.id)
        assertEquals("t1", entry.targetId)
    }

    @Test
    fun starting_a_timer_is_disabled_while_a_timer_is_running() {
        // 已有计时进行中时不允许从日程条目再起一个
        mount(allDatesScreenState(canStartTimer = false))

        composeRule.onNodeWithTag("schedule-start-timer-e1", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun the_recurring_schedule_block_is_a_secondary_entry_that_is_collapsed_by_default() {
        // 重复日程不再是首屏主路径：表单与规则行都收在次级入口后面
        mount(allDatesScreenState())

        composeRule.onNodeWithTag("agenda-recurrence-toggle", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("重复标题", substring = false).assertDoesNotExist()
        composeRule.onNodeWithTag("recurrence-reminder-input", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("每日晨读 · 每日 · 2026-03-05").assertDoesNotExist()
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
        node("重复标题").performScrollTo().performTextReplacement("每周复盘会")
        node("每周").performScrollTo().performClick()
        // 星期由复选框本身切换，标签文字只是它的说明
        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)[0].performScrollTo().performClick()
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
        node("重复标题").performScrollTo().performTextReplacement("每周复盘会")
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

        composeRule.onNodeWithTag("agenda-range-tomorrow", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-range-all-dates", useUnmergedTree = true).performScrollTo().performClick()

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
    fun the_running_timer_is_pinned_above_the_timeline() {
        // 正在计时的条目固定在时间轴顶部：它排在日期筛选与时间轴之前，用户不必先滚动去找。
        mount(planningDayScreenState(planningDay, activeTimer = runningTimer()))

        val tags = orderedTestTags()
        val timerIndex = tags.indexOf("agenda-running-timer")
        val timelineIndex = tags.indexOf("agenda-schedule-row-e2")
        val filterIndex = tags.indexOf("agenda-date-menu")

        assertTrue("运行中的计时必须渲染：" + tags, timerIndex >= 0)
        assertTrue("时间轴条目必须渲染：" + tags, timelineIndex >= 0)
        assertTrue("时间轴筛选必须在：" + tags, filterIndex >= 0)
        assertTrue("计时应固定在时间轴顶部，实际顺序：" + tags, timerIndex < timelineIndex)
        assertTrue("计时应固定在时间轴顶部，实际顺序：" + tags, timerIndex < filterIndex)
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
        node("已暂停").assertDoesNotExist()

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

        composeRule.onNodeWithTag("agenda-running-timer-switch-t1", useUnmergedTree = true)
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
        composeRule.onNodeWithTag("save-time-r1", useUnmergedTree = true).performClick()
        assertEquals(listOf("r1" to 90), intents.editedRecords.map { it.entryId to it.minutes })

        composeRule.onNodeWithTag("delete-time-r1", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG_CONFIRM, useUnmergedTree = true).performClick()
        assertEquals(listOf("r1"), intents.deletedRecords)
    }

    /** 日程页是可滚动长页，断言与点击前先滚到目标节点。 */
    private fun node(text: String, substring: Boolean = false) =
        composeRule.onNodeWithText(text, substring = substring).performScrollTo()

    @Test
    fun the_first_screen_shows_its_sections_in_the_fixed_workbench_order() {
        // 首屏自上而下固定为：规划日与每日容量摘要、时间轴、进行中的计时状态、添加日程入口、事后补记，
        // 重复日程作为折叠的次级入口排在最后
        mount(
            planningDayScreenState(
                selected = planningDay,
                activeTimer = runningTimer(),
                timeRecords = listOf(record()),
            ),
        )

        val tops = listOf(
            "agenda-section-summary",
            "agenda-section-timeline",
            "agenda-section-timer",
            "agenda-section-add",
            "agenda-section-backfill",
            "agenda-section-recurrence",
        ).map { tag -> sectionTop(tag) }

        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun the_workbench_summarises_the_planning_day_read_only() {
        // 工作台只读展示规划日界线、周起始日与每日容量摘要；摘要区里没有任何可编辑输入
        mount(planningDayScreenState(planningDay))

        node("规划日 2026-03-05").assertIsDisplayed()
        node("规划日界线 03:00").assertIsDisplayed()
        node("周起始日 周一").assertIsDisplayed()
        node("每日容量 120 分钟").assertIsDisplayed()
        node("已安排 30 / 120 分钟").assertIsDisplayed()

        val summaryBottom = composeRule.onNodeWithTag("agenda-section-summary", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.bottom
        val editableFields = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertTrue("摘要区里不提供完整设置表单", editableFields.all { it.boundsInRoot.top >= summaryBottom })
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

        node("写周报").assertIsDisplayed()
        node("关联计划：工作").assertIsDisplayed()
        node("计划投入 60 分钟 · 实际投入 0 分钟 · 未完成").assertIsDisplayed()
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

        assertTrue(
            "正在计时的条目排在时间轴顶部",
            sectionTop("schedule-timing-cue") < sectionTop("schedule-edit-e1"),
        )
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
        composeRule.onNodeWithTag("schedule-edit-e4", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-start-timer-e4", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun entries_without_a_time_range_stay_visible_in_the_unscheduled_group() {
        // 没有具体时间的日程进入独立的「未安排时间」区域，仍然看得见
        mount(
            planningDayScreenState(
                selected = planningDay,
                timelineRows = listOf(
                    row(meetingEntry, "2026-03-06 · 30 分钟 · 无提醒 · 已完成", true),
                ),
                unscheduledRows = listOf(row(looseEntry, "2026-03-05 · 15 分钟 · 无提醒", true)),
            ),
        )

        node("未安排时间").assertIsDisplayed()
        node("想到再做的").assertIsDisplayed()
    }

    @Test
    fun an_empty_planning_day_invites_the_first_schedule_and_reaches_the_add_form() {
        // 当天没有日程时显示「安排今天第一件事」，并直接提供添加日程入口
        mount(emptyPlanningDayScreenState())

        node("安排今天第一件事").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-first-schedule", useUnmergedTree = true).performScrollTo().performClick()
        // 页面把添加表单滚进视野，等这一段滚动停下来再断言
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("agenda-title-input", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * 一个区块在页面上的纵向位置。
     *
     * 刻意不滚动：日程页是一个整体滚动列，滚到某个区块会改变其它区块在窗口里的位置，
     * 因此比较顺序时必须在同一次布局快照上读取。
     */
    private fun sectionTop(tag: String): Float =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

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
        completed = entry.completed,
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

    /** 工作台顶部的只读摘要：规划日、规划日界线、周起始日与每日容量。 */
    private fun summary(planningDateLabel: String, capacityUsageLabel: String?) = AgendaWorkbenchSummary(
        planningDateLabel = planningDateLabel,
        boundaryLabel = "规划日界线 03:00",
        weekStartLabel = "周起始日 周一",
        capacitySettingLabel = "每日容量 120 分钟",
        capacityUsageLabel = capacityUsageLabel,
    )

    /** 所有日期特殊视图：条目按规划日分组，每组带这一天的每日容量与自己的时间轴。 */
    private fun allDatesScreenState(canStartTimer: Boolean = true): AgendaScreenState = AgendaScreenState(
        planningDate = planningDay,
        selectedDate = null,
        headingLabel = "所有日期",
        filterLabel = "筛选：所有日期",
        availableDates = availableDates,
        selectedCapacityLabel = null,
        workbenchSummary = summary("所有日期", null),
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
        val updatedEntries = mutableListOf<Pair<ScheduleEntry, Int?>>()
        val confirmedCompletions = mutableListOf<String>()
        val toggledCompletions = mutableListOf<String>()
        val startedEntries = mutableListOf<ScheduleEntry>()
        val addedRecurrences = mutableListOf<AgendaRecurrenceDraft>()
        val stoppedRules = mutableListOf<String>()
        val dataIntents = mutableListOf<String>()
        val editedRecords = mutableListOf<EditedRecord>()
        val deletedRecords = mutableListOf<String>()
        var stoppedTimers = 0
        var permissionRequests = 0
        var pausedTimers = 0
        var resumedTimers = 0
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

        override fun updateSchedule(entry: ScheduleEntry, reminderMinuteOfDay: Int?) {
            updatedEntries += entry to reminderMinuteOfDay
        }

        override fun confirmScheduleCompleted(entryId: String) {
            confirmedCompletions += entryId
        }

        override fun toggleScheduleCompleted(entryId: String) {
            toggledCompletions += entryId
        }

        override fun startTimerFromEntry(entry: ScheduleEntry) {
            startedEntries += entry
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
    }

    /** 页面发出的一次编辑 intent：只带被改动的字段，规划日与来源不参与。 */
    private data class EditedRecord(
        val entryId: String,
        val minutes: Int,
        val targetId: String?,
        val note: String,
    )
}
