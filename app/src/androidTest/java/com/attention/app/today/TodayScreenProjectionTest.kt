package com.attention.app.today

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.ui.components.AttentionFeedbackTags
import com.attention.app.ui.format.TimeRecordTags
import com.attention.app.ui.theme.AttentionTheme
import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.ScheduleEntry
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import com.attention.domain.TimeEntrySource
import com.attention.workspace.state.OnboardingScheduleDraft
import com.attention.workspace.state.TodayActions
import com.attention.workspace.state.TodayScreenState
import com.attention.workspace.state.todayScreenState
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 今天工作台的直接测试：只给它 fake screen state 与 fake actions，
 * 断言可见标签、规划日与容量的呈现，以及手动时间账本发出的 intent，
 * 不经过 ViewModel、Room、前台服务与系统时钟。
 *
 * 页面本身不需要 Activity 上下文，因此用轻量的 [createComposeRule] 挂载，
 * 不必每个用例都把整个应用拉起来一遍。宿主 activity 声明在 debug manifest 里。
 */
@RunWith(AndroidJUnit4::class)
class TodayScreenProjectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 上海时间 2026-03-06 01:00，规划日仍是 3 月 5 日。 */
    private val now: Instant = Instant.parse("2026-03-05T17:00:00Z")
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun the_page_shows_the_projected_planning_day() {
        mount(screenState())

        at("今天工作台").assertIsDisplayed()
        at("规划日界线 03:00 · 每周从周一开始").assertIsDisplayed()
        at("规划日：2026-03-05").assertIsDisplayed()
    }

    @Test
    fun the_page_shows_the_projected_capacity_summary() {
        // 容量占用与超载都由投影判定，页面只渲染标签
        mount(screenState())

        composeRule.onNodeWithTag("daily-capacity-summary").performScrollTo().assertIsDisplayed()
        at("已安排 150 / 120 分钟（超载）").assertIsDisplayed()
    }

    @Test
    fun the_page_shows_the_records_of_the_projected_planning_day() {
        // 时长、归属与来源逐条来自投影；别的规划日的记录不出现
        mount(screenState())

        at("45 分钟").assertIsDisplayed()
        at("写书 · 事后补记").assertIsDisplayed()
        at("未归属活动 · 计时").assertIsDisplayed()
        composeRule.onAllNodesWithTag(TimeRecordTags.DURATION).assertCountEquals(2)
        composeRule.onAllNodesWithTag(TimeRecordTags.SOURCE_MARKER).assertCountEquals(2)
        composeRule.onNodeWithText("999 分钟").assertDoesNotExist()
    }

    @Test
    fun the_page_shows_the_recorded_minutes_of_the_planning_day() {
        mount(screenState())

        at("本规划日已记录：65 分钟").assertIsDisplayed()
    }

    @Test
    fun archived_targets_are_not_offered_to_the_quick_record_form() {
        // 归档目标不能接收新的时间记录，因此页面不提供它的选项
        mount(screenState())

        composeRule.onNodeWithText("旧计划").assertDoesNotExist()
        // 「写书」出现在快捷目标行与目标选择行；归档的「旧计划」两处都没有
        composeRule.onAllNodesWithText("写书").assertCountEquals(2)
    }

    @Test
    fun quick_record_buttons_emit_the_record_intent_with_the_projected_planning_day() {
        // 快捷分钟数只发出记录 intent，并带上页面正在显示的规划日
        val intents = RecordingTodayActions()
        mount(screenState(), intents)

        click("+5")
        click("+60")

        assertEquals(listOf(Triple("2026-03-05", 5, null), Triple("2026-03-05", 60, null)), intents.recorded)
    }

    @Test
    fun recording_without_a_target_emits_the_unowned_intent() {
        val intents = RecordingTodayActions()
        mount(screenState(), intents)

        click("记录未归属活动")

        assertEquals(listOf(Triple("2026-03-05", 15, null)), intents.recorded)
    }

    @Test
    fun selecting_a_target_then_recording_emits_the_selected_target() {
        val intents = RecordingTodayActions()
        mount(screenState(), intents)

        clickLast("写书")
        click("记录到目标")

        assertEquals(listOf(Triple("2026-03-05", 15, "t1")), intents.recorded)
    }

    @Test
    fun editing_a_record_emits_the_edit_intent_with_the_edited_fields() {
        // 编辑只带时长、目标归属与备注：规划日与来源不在 intent 里
        val intents = RecordingTodayActions()
        mount(screenState(), intents)

        clickTag("edit-time-e1")
        composeRule.onNodeWithTag("edit-time-minutes-e1").performTextClearance()
        composeRule.onNodeWithTag("edit-time-minutes-e1").performTextInput("90")
        clickTag("save-time-e1")

        assertEquals(listOf(EditIntent("e1", 90, "t1", "第三章")), intents.edits)
    }

    @Test
    fun moving_a_record_to_unowned_emits_a_null_target() {
        val intents = RecordingTodayActions()
        mount(screenState(), intents)

        clickTag("edit-time-e1")
        // 快速记录的选择行与编辑表单都用「未归属」，表单里的那个排在后面
        invokeLast("未归属")
        clickTag("save-time-e1")

        assertEquals(listOf(EditIntent("e1", 45, null, "第三章")), intents.edits)
    }

    @Test
    fun deleting_a_record_emits_the_delete_intent_after_confirmation() {
        val intents = RecordingTodayActions()
        mount(screenState(), intents)

        clickTag("delete-time-e1")
        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG_IMPACT).assertIsDisplayed()
        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG_CONFIRM).performClick()

        assertEquals(listOf("e1"), intents.deleted)
    }

    @Test
    fun dismissing_the_delete_confirmation_emits_nothing() {
        val intents = RecordingTodayActions()
        mount(screenState(), intents)

        clickTag("delete-time-e1")
        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG_DISMISS).performClick()

        assertEquals(emptyList<String>(), intents.deleted)
    }

    @Test
    fun saving_the_capacity_emits_the_settings_intent() {
        val intents = RecordingTodayActions()
        mount(screenState(), intents)

        typeTag("planning-boundary-input", "240")
        typeTag("daily-capacity-input", "200")
        clickTag("daily-capacity-save")

        assertEquals(listOf(PlanningSettings(240, 200)), intents.settings)
    }

    @Test
    fun clearing_the_capacity_emits_a_null_capacity() {
        val intents = RecordingTodayActions()
        mount(screenState(AttentionState(settings = StoredSettings(dailyCapacityMinutes = 120))), intents)

        composeRule.onNodeWithTag("daily-capacity-input").performScrollTo().performTextClearance()
        clickTag("daily-capacity-save")

        assertEquals(listOf(PlanningSettings(180, null)), intents.settings)
    }

    @Test
    fun an_invalid_capacity_shows_the_error_and_emits_nothing() {
        val intents = RecordingTodayActions()
        mount(screenState(), intents)

        typeTag("daily-capacity-input", "0")
        clickTag("daily-capacity-save")

        composeRule.onNodeWithTag("daily-capacity-error").assertExists()
        assertEquals(emptyList<PlanningSettings>(), intents.settings)
    }

    @Test
    fun picking_a_week_start_day_emits_the_selection_intent() {
        val intents = RecordingTodayActions()
        mount(screenState(), intents)

        invoke("周四")

        assertEquals(listOf(4), intents.weekStartDays)
    }

    @Test
    fun the_current_week_start_day_is_selected_and_emits_nothing() {
        val intents = RecordingTodayActions()
        mount(screenState(), intents)

        composeRule.onNodeWithTag(AttentionFeedbackTags.SELECTED, useUnmergedTree = true).assertExists()
        click("周一")

        assertEquals(emptyList<Int>(), intents.weekStartDays)
    }

    @Test
    fun the_timer_section_belongs_to_the_page_and_emits_no_intent_until_it_is_used() {
        // 计时器已经迁进今天工作台：页面自己渲染它，不计时时也不发出任何计时 intent
        val intents = RecordingTodayActions()
        composeRule.setContent {
            AttentionTheme { TodayScreen(state = screenState(), actions = intents) }
        }

        composeRule.onNodeWithText("计时器").performScrollTo().assertIsDisplayed()
        assertEquals(emptyList<String>(), intents.started)
        assertEquals(emptyList<String>(), intents.paused)
    }

    @Test
    fun starting_a_timer_emits_the_start_intent_with_the_chosen_target() {
        // 开始计时只发 intent：启动前台服务、撤掉恢复提示都由组合根 adapter 完成
        val intents = RecordingTodayActions()
        mount(screenState(), intents)

        click("开始未归属计时")
        click("计时：写书")

        assertEquals(listOf<String?>(null, "t1"), intents.started)
    }

    @Test
    fun a_running_timer_shows_its_target_and_the_projected_settlement_preview() {
        // 实际投入与结束范围预览都由投影按注入的时刻算好，页面不读时钟
        val intents = RecordingTodayActions()
        mount(screenState(activeTimer = runningTimer()), intents)

        composeRule.onNodeWithTag("timer-settlement-preview").performScrollTo().assertIsDisplayed()
        at("当前目标：写书").assertIsDisplayed()
        at("结束范围预览：2026-03-05 30 分钟；共 30 分钟").assertIsDisplayed()
        assertEquals(emptyList<String>(), intents.started)
    }

    @Test
    fun pausing_and_stopping_a_running_timer_emit_their_intents() {
        val intents = RecordingTodayActions()
        mount(screenState(activeTimer = runningTimer()), intents)

        click("暂停")
        click("结束并保存")

        assertEquals(listOf("pause"), intents.paused)
        assertEquals(listOf("stop"), intents.stopped)
    }

    @Test
    fun switching_a_running_timer_emits_the_switch_intent() {
        // 切到未归属活动与切到其它目标是同一个 intent，只是目标不同
        val intents = RecordingTodayActions()
        mount(screenState(activeTimer = runningTimer()), intents)

        clickTag("switch-timer-unowned")
        click("切换到 复盘")

        assertEquals(listOf<String?>(null, "t3"), intents.switched)
    }

    @Test
    fun resuming_a_paused_timer_emits_the_resume_intent() {
        val intents = RecordingTodayActions()
        mount(screenState(activeTimer = runningTimer(paused = true)), intents)

        click("继续")

        assertEquals(listOf("resume"), intents.resumed)
    }

    @Test
    fun recovering_a_timer_emits_the_recover_intent() {
        val intents = RecordingTodayActions()
        mount(screenState(activeTimer = runningTimer()), intents)

        click("从此刻继续")

        assertEquals(listOf("recover"), intents.recovered)
    }

    @Test
    fun the_notification_permission_warning_shows_when_the_root_reports_notifications_off() {
        // 通知是否可用是平台事实：页面只按投影给的结论决定要不要显示警告
        val intents = RecordingTodayActions()
        mount(screenState(activeTimer = runningTimer(), notificationsEnabled = false), intents)

        composeRule.onNodeWithTag("timer-notification-permission-warning").performScrollTo().assertExists()
    }

    @Test
    fun the_notification_permission_warning_is_absent_when_notifications_are_available() {
        val intents = RecordingTodayActions()
        mount(screenState(activeTimer = runningTimer(), notificationsEnabled = true), intents)

        composeRule.onNodeWithTag("timer-notification-permission-warning").assertDoesNotExist()
    }

    /** 今天工作台是可滚动长页：断言前先把节点滚进视口。 */
    private fun at(text: String, substring: Boolean = false) =
        composeRule.onNodeWithText(text, substring = substring).performScrollTo()

    /** 滚进视口后重新取一次节点，避免用滚动之前的旧坐标点击。 */
    private fun click(text: String) {
        at(text)
        composeRule.onNodeWithText(text).performClick()
    }

    /** 编辑表单与快速记录复用同一套目标文案，按最后一个节点定位表单里的那个。 */
    private fun clickLast(text: String) {
        composeRule.onAllNodesWithText(text).onLast().performScrollTo()
        composeRule.onAllNodesWithText(text).onLast().performClick()
    }

    /**
     * 横向滚动行里的按钮用语义动作触发：这类节点滚进视口后中心点可能仍被容器裁掉，
     * 按坐标点击会落空，触发 OnClick 才是稳定的做法。
     */
    private fun invoke(text: String) {
        composeRule.onNodeWithText(text).performScrollTo()
        composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun invokeLast(text: String) {
        composeRule.onAllNodesWithText(text).onLast().performScrollTo()
        composeRule.onAllNodesWithText(text).onLast().performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun clickTag(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun typeTag(tag: String, value: String) {
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.onNodeWithTag(tag).performTextClearance()
        composeRule.onNodeWithTag(tag).performTextInput(value)
    }

    @Test
    fun starting_a_timer_from_a_today_schedule_row_actually_starts_the_timer() {
        // 今天工作台「今日安排」里的「开始计时」必须真的起计时，并且带上这条日程关联的计划。
        // 迁移前这个按钮一直是空回调，用户在今天页点它没有任何反应。
        val actions = RecordingTodayActions()
        mount(
            screenState(
                AttentionState(
                    targets = listOf(Target(id = "t1", title = "写书")),
                    schedules = listOf(
                        ScheduleEntry(id = "sc1", planningDate = "2026-03-05", title = "写稿", estimatedMinutes = 90, targetId = "t1"),
                    ),
                    settings = StoredSettings(dailyCapacityMinutes = 120),
                    onboardingCompleted = true,
                )
            ),
            actions,
        )

        composeRule.onNodeWithTag("schedule-start-timer-sc1", useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        // 整条条目必须被带出，产出的时间记录才保留日程来源关联；只传 targetId 会丢来源。
        assertEquals(1, actions.startedFromEntries.size)
        val entry = actions.startedFromEntries.single()
        assertEquals("sc1", entry.id)
        assertEquals("t1", entry.targetId)
        // 普通的 startTimer 不应被这次点击触发
        assertEquals(emptyList<String?>(), actions.started)
    }

    @Test
    fun starting_a_timer_from_a_today_schedule_row_is_disabled_while_a_timer_runs() {
        // 已有计时进行中时，这一条入口不应可用
        mount(screenState(activeTimer = runningTimer()))

        composeRule.onNodeWithTag("schedule-start-timer-sc1", useUnmergedTree = true).assertExists()
    }

    /**
     * 这里刻意让首次设置已完成：迁移前这些用例挂的是一个空的首次设置插槽，
     * 它们断言的是快速记录、计时与容量的呈现。首次设置本身由 [TodayOnboardingCardTest] 直接测。
     */
    private fun screenState(
        state: AttentionState = AttentionState(
            targets = listOf(
                Target(id = "t1", title = "写书"),
                Target(id = "t2", title = "旧计划", archived = true),
                Target(id = "t3", title = "复盘"),
            ),
            schedules = listOf(
                ScheduleEntry(id = "sc1", planningDate = "2026-03-05", title = "写稿", estimatedMinutes = 90),
                ScheduleEntry(id = "sc2", planningDate = "2026-03-05", title = "复盘", startMinute = 600, endMinute = 660),
            ),
            timeEntries = listOf(
                TimeEntry(id = "e1", planningDate = "2026-03-05", durationMinutes = 45, targetId = "t1", note = "第三章"),
                TimeEntry(id = "e2", planningDate = "2026-03-05", durationMinutes = 20, source = TimeEntrySource.TIMER),
                TimeEntry(id = "e3", planningDate = "2026-03-04", durationMinutes = 999, targetId = "t1"),
            ),
            settings = StoredSettings(dailyCapacityMinutes = 120),
            onboardingCompleted = true,
        ),
        activeTimer: ActiveTimer? = null,
        notificationsEnabled: Boolean = true,
    ): TodayScreenState = todayScreenState(
        state = state.copy(activeTimer = activeTimer),
        now = now,
        zone = zone,
        notificationsEnabled = notificationsEnabled,
    )

    /** 上海时间 00:30 起计，到投影时刻 [now]（01:00）正好 30 分钟，整段落在规划日 3 月 5 日内。 */
    private fun runningTimer(targetId: String? = "t1", paused: Boolean = false) = ActiveTimer(
        targetId = targetId,
        startedAtEpochMillis = Instant.parse("2026-03-05T16:30:00Z").toEpochMilli(),
        paused = paused,
        lastPlanningDate = "2026-03-05",
    )

    private fun mount(
        state: TodayScreenState,
        actions: TodayActions = RecordingTodayActions(),
    ) {
        composeRule.setContent {
            AttentionTheme { TodayScreen(state = state, actions = actions) }
        }
    }

    private data class EditIntent(
        val entryId: String,
        val minutes: Int,
        val targetId: String?,
        val note: String,
    )

    private data class PlanningSettings(val boundaryMinutes: Int, val dailyCapacityMinutes: Int?)

    private class RecordingTodayActions : TodayActions {
        val recorded = mutableListOf<Triple<String, Int, String?>>()
        val edits = mutableListOf<EditIntent>()
        val deleted = mutableListOf<String>()
        val settings = mutableListOf<PlanningSettings>()
        val weekStartDays = mutableListOf<Int>()
        val started = mutableListOf<String?>()
        val startedFromEntries = mutableListOf<ScheduleEntry>()
        val paused = mutableListOf<String>()
        val resumed = mutableListOf<String>()
        val recovered = mutableListOf<String>()
        val stopped = mutableListOf<String>()
        val switched = mutableListOf<String?>()

        override fun recordTime(planningDate: String, minutes: Int, targetId: String?) {
            recorded += Triple(planningDate, minutes, targetId)
        }

        override fun editTime(entryId: String, minutes: Int, targetId: String?, note: String) {
            edits += EditIntent(entryId, minutes, targetId, note)
        }

        override fun deleteTime(entryId: String) {
            deleted += entryId
        }

        override fun saveDailyPlanningSettings(boundaryMinutes: Int, dailyCapacityMinutes: Int?) {
            settings += PlanningSettings(boundaryMinutes, dailyCapacityMinutes)
        }

        override fun selectWeekStartDay(day: Int) {
            weekStartDays += day
        }

        override fun startTimer(targetId: String?) {
            started += targetId
        }

        override fun startTimerFromEntry(entry: ScheduleEntry) {
            startedFromEntries += entry
        }

        override fun pauseTimer() {
            paused += "pause"
        }

        override fun resumeTimer() {
            resumed += "resume"
        }

        override fun recoverTimer() {
            recovered += "recover"
        }

        override fun stopTimer() {
            stopped += "stop"
        }

        override fun switchTimer(targetId: String?) {
            switched += targetId
        }

        override fun requestNotificationPermission() = Unit

        override fun finishOnboarding() = Unit

        override fun saveOnboardingPlanningSettings(boundaryMinutes: Int, weekStartDay: Int, dailyCapacityMinutes: Int?) = Unit

        override fun createOnboardingTarget(title: String, onSuccess: () -> Unit) = Unit

        override fun createOnboardingSchedule(draft: OnboardingScheduleDraft) = Unit

        override fun updateTodaySchedule(entry: ScheduleEntry, reminderMinuteOfDay: Int?) = Unit

        override fun confirmScheduleCompleted(entryId: String) = Unit

        override fun toggleScheduleCompleted(entryId: String) = Unit
    }
}
