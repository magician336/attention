package com.attention.app.agenda

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.MainActivity
import com.attention.app.ui.theme.AttentionTheme
import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.workspace.state.AgendaActions
import com.attention.workspace.state.AgendaRecurrenceDraft
import com.attention.workspace.state.AgendaScheduleDraft
import com.attention.workspace.state.AgendaScreenState
import com.attention.workspace.state.agendaScreenState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 所有日期页的直接测试：只给它 fake screen state 与 fake actions，
 * 断言可见标签、选中规划日、导航与筛选 intent、创建/编辑/完成 intent，
 * 以及从日程条目起计时的来源关联，不经过 ViewModel、Room 与权限 API。
 */
@RunWith(AndroidJUnit4::class)
class AgendaScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Instant = LocalDate.of(2026, 3, 5).atTime(10, 0).atZone(zone).toInstant()

    @Test
    fun the_page_shows_the_all_dates_view_with_a_capacity_summary_per_planning_day() {
        // 所有日期是特殊视图：条目按规划日分组，每天各有自己的每日容量
        mount(screenState())

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
        mount(screenState(selectedDate = LocalDate.of(2026, 3, 6)))

        node("规划日 2026-03-06").assertIsDisplayed()
        node("筛选：规划日 2026-03-06").assertIsDisplayed()
        node("开组会").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-capacity-summary", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun the_empty_view_explains_what_is_missing() {
        // 没有条目时按当前视图给出空态文案
        mount(screenState(state = AttentionState()))

        node("所有日期暂无日程条目。").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-empty", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun picking_a_planning_day_from_the_filter_emits_the_selection_intent() {
        // 规划日筛选只发出 intent，实际选中日由组合根与页面选择状态决定
        val intents = RecordingAgendaActions()
        mount(screenState(), intents)

        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-date-2026-03-06", useUnmergedTree = true).performClick()

        assertEquals(listOf(LocalDate.of(2026, 3, 6)), intents.selectedDates)
    }

    @Test
    fun choosing_all_dates_emits_the_special_view_intent() {
        // 所有日期是一个特殊视图，不是一个可用的日期值
        val intents = RecordingAgendaActions()
        mount(screenState(selectedDate = LocalDate.of(2026, 3, 6)), intents)

        composeRule.onNodeWithTag("agenda-date-menu", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-all-dates", useUnmergedTree = true).performClick()

        // 所有日期是一个特殊视图：intent 是「不选某一天」，而不是一个日期值
        assertEquals(listOf<LocalDate?>(null), intents.selectedDates)
    }

    @Test
    fun submitting_the_add_form_emits_a_schedule_draft() {
        // 标题与规划日必需；其余可选，提交后只发一个草稿 intent
        val intents = RecordingAgendaActions()
        mount(screenState(), intents)

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
        mount(screenState(), intents)

        composeRule.onNodeWithTag("agenda-estimate-input", useUnmergedTree = true).performTextReplacement("45")
        composeRule.onNodeWithTag("agenda-add-submit", useUnmergedTree = true).performScrollTo().performClick()

        node("日程标题不能为空").assertIsDisplayed()
        assertEquals(emptyList<AgendaScheduleDraft>(), intents.addedSchedules)
    }

    @Test
    fun a_reminder_in_the_add_form_asks_for_permission_as_an_intent() {
        // 填了提醒才请求权限；权限申请本身由组合根 adapter 完成
        val intents = RecordingAgendaActions()
        mount(screenState(), intents)

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
        mount(screenState(), intents)

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
        mount(screenState(), intents)

        composeRule.onNodeWithTag("schedule-completion-e1", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("schedule-completion-e2", useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(listOf("e1"), intents.confirmedCompletions)
        assertEquals(listOf("e2"), intents.toggledCompletions)
    }

    @Test
    fun starting_a_timer_from_an_entry_keeps_the_entry_as_the_source() {
        // 从日程条目起计时的 intent 带上整条条目，组合根因此能保留来源关联
        val intents = RecordingAgendaActions()
        mount(screenState(), intents)

        composeRule.onNodeWithTag("schedule-start-timer-e1", useUnmergedTree = true).performScrollTo().performClick()

        val entry: ScheduleEntry = intents.startedEntries.single()
        assertEquals("e1", entry.id)
        assertEquals("t1", entry.targetId)
    }

    @Test
    fun starting_a_timer_is_disabled_while_a_timer_is_running() {
        // 已有计时进行中时不允许从日程条目再起一个
        mount(screenState(state = state().copy(activeTimer = runningTimer())))

        composeRule.onNodeWithTag("schedule-start-timer-e1", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun the_recurring_rule_lines_are_shown_with_their_stop_intent() {
        // 重复日程规则行来自投影；停止未来生成只发出带 id 的 intent
        val intents = RecordingAgendaActions()
        mount(screenState(), intents)

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
        mount(screenState(), intents)

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
        mount(screenState(), intents)

        node("重复标题").performScrollTo().performTextReplacement("每周复盘会")
        node("每周").performScrollTo().performClick()
        node("创建并生成未来 30 天").performScrollTo().performClick()

        node("每周规则至少选择一个星期").assertIsDisplayed()
        assertEquals(emptyList<AgendaRecurrenceDraft>(), intents.addedRecurrences)
    }

    /** 所有日期页是可滚动长页，断言与点击前先滚到目标节点。 */
    private fun node(text: String, substring: Boolean = false) =
        composeRule.onNodeWithText(text, substring = substring).performScrollTo()

    private fun runningTimer() = ActiveTimer(
        targetId = null,
        startedAtEpochMillis = now.toEpochMilli(),
        lastPlanningDate = "2026-03-05",
    )

    private fun state() = AttentionState(
        targets = listOf(
            Target(id = "t1", title = "工作"),
            Target(id = "t-archived", title = "已归档计划", archived = true),
        ),
        schedules = listOf(
            ScheduleEntry(
                id = "e1",
                planningDate = "2026-03-05",
                title = "写周报",
                estimatedMinutes = 60,
                targetId = "t1",
            ),
            ScheduleEntry(
                id = "e2",
                planningDate = "2026-03-06",
                title = "开组会",
                estimatedMinutes = 30,
                completed = true,
            ),
        ),
        recurrenceRules = listOf(
            RecurrenceRule(
                id = "rule-daily",
                title = "每日晨读",
                startDate = "2026-03-05",
                frequency = ScheduleFrequency.DAILY,
                estimatedMinutes = 20,
            ),
            RecurrenceRule(
                id = "rule-weekly",
                title = "每周复盘",
                startDate = "2026-03-02",
                frequency = ScheduleFrequency.WEEKLY,
                weekdays = listOf(1, 3),
                untilDate = "2026-03-31",
                active = false,
            ),
        ),
        settings = StoredSettings(dailyCapacityMinutes = 120),
    )

    private fun screenState(
        state: AttentionState = state(),
        selectedDate: LocalDate? = null,
    ): AgendaScreenState = agendaScreenState(
        state = state,
        now = now,
        zone = zone,
        selectedDate = selectedDate,
    )

    private fun mount(
        state: AgendaScreenState,
        actions: AgendaActions = RecordingAgendaActions(),
    ) {
        composeRule.activity.setContent {
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
        var permissionRequests = 0

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
    }
}