package com.attention.workspace.state

import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.occurrences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 所有日期页投影的纯 Kotlin 测试：页面只读这份状态，不读完整领域状态，也不读系统时钟。
 * 规划日导航、日程条目筛选、所有日期特殊视图、每日容量与重复日程展开都由投影给出，
 * 时间只能来自组合根注入的 now 与 zone。
 */
class AgendaScreenStateTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 3, 5)

    /** 2026-03-05 10:00 上海时间，在默认 03:00 界线下落在 3 月 5 日这个规划日。 */
    private val now: Instant = LocalDate.of(2026, 3, 5).atTime(10, 0).atZone(zone).toInstant()

    private fun project(
        state: AttentionState = AttentionState(),
        instant: Instant = now,
        zone: ZoneId = this.zone,
        selectedDate: LocalDate? = null,
        openScheduleId: String? = null,
    ) = agendaScreenState(
        state = state,
        now = instant,
        zone = zone,
        selectedDate = selectedDate,
        openScheduleId = openScheduleId,
    )

    private fun stateWith(vararg entries: ScheduleEntry, settings: StoredSettings = StoredSettings()) =
        AttentionState(schedules = entries.toList(), settings = settings)

    @Test
    fun all_dates_is_a_special_view_grouping_entries_by_planning_day() {
        // 所有日期不是一个可以写进日程条目的日期值：它把所有条目按规划日分组展示，且不显示单日容量卡片
        val screenState = project(
            state = stateWith(
                ScheduleEntry("b", "2026-03-06", "次日条目", estimatedMinutes = 30),
                ScheduleEntry("a", "2026-03-05", "当日条目", estimatedMinutes = 45),
                ScheduleEntry("c", "2026-03-05", "当日第二条", estimatedMinutes = 15),
            ),
        )

        assertNull("所有日期不是日期值", screenState.selectedDate)
        assertEquals("所有日期", screenState.headingLabel)
        assertEquals("筛选：所有日期", screenState.filterLabel)
        assertNull("所有日期视图不显示单日容量卡片", screenState.selectedCapacityLabel)
        assertEquals(listOf("2026-03-05", "2026-03-06"), screenState.dateSections.map { it.planningDate })
        assertEquals(
            listOf("a", "c"),
            screenState.dateSections.first().rows.map { it.entry.id },
        )
        assertNull("有条目时不显示空态文案", screenState.emptyLabel)
    }

    @Test
    fun a_selected_planning_day_shows_only_that_day_entries() {
        // 选中规划日时只显示当天的日程条目，容量卡片针对该日
        val screenState = project(
            state = stateWith(
                ScheduleEntry("a", "2026-03-05", "当日条目", estimatedMinutes = 45),
                ScheduleEntry("b", "2026-03-06", "次日条目", estimatedMinutes = 30),
            ),
            selectedDate = LocalDate.of(2026, 3, 5),
        )

        assertEquals("规划日 2026-03-05", screenState.headingLabel)
        assertEquals("筛选：规划日 2026-03-05", screenState.filterLabel)
        assertEquals(listOf("a"), screenState.selectedRows.map { it.entry.id })
        assertTrue(screenState.dateSections.isEmpty())
        assertEquals("已安排 45 分钟（每日容量未设置）", screenState.selectedCapacityLabel)
    }

    @Test
    fun the_selected_planning_day_defaults_to_the_injected_planning_day() {
        // 还没选过日期时跟随组合根注入的当前规划日
        val screenState = project(state = stateWith(ScheduleEntry("a", "2026-03-05", "当日条目")))

        assertEquals(today, screenState.planningDate)
        assertNull(screenState.selectedDate)
    }

    @Test
    fun available_dates_offer_every_day_with_entries_plus_the_injected_planning_day() {
        // 可选日期来自日程条目出现过的日期加上当前规划日，升序去重
        val screenState = project(
            state = stateWith(
                ScheduleEntry("a", "2026-03-07", "更晚的条目"),
                ScheduleEntry("b", "2026-03-05", "当日条目"),
            ),
        )

        assertEquals(
            listOf("2026-03-05", "2026-03-07"),
            screenState.availableDates.map { it.date.toString() },
        )
        assertEquals(listOf("规划日 2026-03-05", "规划日 2026-03-07"), screenState.availableDates.map { it.label })
    }

    @Test
    fun the_empty_label_follows_the_selected_view() {
        val allDates = project(state = stateWith())
        val singleDay = project(state = stateWith(), selectedDate = LocalDate.of(2026, 3, 5))

        assertEquals("所有日期暂无日程条目。", allDates.emptyLabel)
        assertEquals("规划日 2026-03-05 暂无日程条目。", singleDay.emptyLabel)
    }

    @Test
    fun the_daily_capacity_summary_reports_every_planning_day_it_is_asked_about() {
        // 每日容量按规划日汇总：预计时长优先，其次完整时间段
        val state = AttentionState(
            schedules = listOf(
                ScheduleEntry("a", "2026-03-05", "有预计时长", estimatedMinutes = 45),
                ScheduleEntry("b", "2026-03-05", "只有时间段", startMinute = 540, endMinute = 570),
                ScheduleEntry("c", "2026-03-06", "超载的一天", estimatedMinutes = 200),
            ),
            settings = StoredSettings(dailyCapacityMinutes = 120),
        )

        val screenState = project(state = state)

        assertEquals(
            listOf("已安排 75 / 120 分钟", "已安排 200 / 120 分钟（超载）"),
            screenState.dateSections.map { it.capacityLabel },
        )
    }

    @Test
    fun the_daily_capacity_summary_falls_back_to_the_time_range() {
        val state = AttentionState(
            schedules = listOf(ScheduleEntry("b", "2026-03-05", "只有时间段", startMinute = 540, endMinute = 570)),
            settings = StoredSettings(dailyCapacityMinutes = 120),
        )

        assertEquals("已安排 30 / 120 分钟", project(state = state).dateSections.single().capacityLabel)
    }

    @Test
    fun a_recurring_rule_expands_into_one_independent_entry_per_occurrence() {
        // 重复日程按规则物化成独立日程条目；规则行给出可读标签
        val rule = RecurrenceRule(
            id = "rule-1",
            title = "每日晨读",
            startDate = "2026-03-05",
            frequency = ScheduleFrequency.DAILY,
            estimatedMinutes = 20,
        )
        val through = LocalDate.of(2026, 3, 7)
        val occurrences = AttentionState().occurrences(rule, through, zone)
        val state = AttentionState(
            schedules = occurrences,
            recurrenceRules = listOf(rule),
        )

        val screenState = project(state = state)

        assertEquals(
            listOf("2026-03-05", "2026-03-06", "2026-03-07"),
            screenState.dateSections.map { it.planningDate },
        )
        screenState.dateSections.forEach { section ->
            assertEquals(1, section.rows.size)
        }
        val line = screenState.recurrenceRules.single()
        assertEquals("rule-1", line.ruleId)
        assertEquals("每日晨读 · 每日 · 2026-03-05", line.label)
        assertTrue(line.active)
    }

    @Test
    fun recurrence_rule_lines_explain_weekly_and_monthly_rules() {
        val state = AttentionState(
            recurrenceRules = listOf(
                RecurrenceRule(
                    id = "w",
                    title = "每周复盘",
                    startDate = "2026-03-02",
                    frequency = ScheduleFrequency.WEEKLY,
                    weekdays = listOf(1, 3),
                    untilDate = "2026-03-31",
                    active = false,
                ),
                RecurrenceRule(
                    id = "m",
                    title = "每月结算",
                    startDate = "2026-03-01",
                    frequency = ScheduleFrequency.MONTHLY,
                    dayOfMonth = 15,
                ),
            ),
        )

        val lines = project(state = state).recurrenceRules

        assertEquals("每周复盘 · 每周 一,三 · 2026-03-02 至 2026-03-31", lines[0].label)
        assertFalse(lines[0].active)
        assertEquals("每月结算 · 每月 15 日 · 2026-03-01", lines[1].label)
        assertTrue(lines[1].active)
    }

    @Test
    fun a_schedule_row_carries_its_labels_and_the_editable_seeds() {
        // 页面不自己格式化：标题、详情行、备注与编辑表单初值都由投影给出
        val state = stateWith(
            ScheduleEntry(
                id = "e1",
                planningDate = "2026-03-05",
                title = "写周报",
                startMinute = 540,
                endMinute = 600,
                estimatedMinutes = 60,
                note = "含数据附录",
                targetId = "t1",
                completed = true,
                reminderEpochMillis = com.attention.domain.reminderEpochMillis(
                    LocalDate.of(2026, 3, 5),
                    9 * 60,
                    zone,
                ),
            ),
            ).copy(
            targets = listOf(Target("t1", title = "工作")),
            activeTimer = ActiveTimer(
                targetId = null,
                startedAtEpochMillis = now.toEpochMilli(),
                lastPlanningDate = "2026-03-05",
            ),
        )

        val row = project(state = state).dateSections.single().rows.single()

        assertEquals("写周报", row.title)
        assertEquals(
            "2026-03-05 · 09:00-10:00 · 60 分钟 · 工作 · 提醒 09:00 · 已完成",
            row.detailLabel,
        )
        assertEquals("含数据附录", row.note)
        assertEquals("09:00", row.startText)
        assertEquals("10:00", row.endText)
        assertEquals("60", row.estimateText)
        assertEquals("09:00", row.reminderText)
        assertEquals("t1", row.targetId)
        assertFalse("已有计时进行中时不能从日程条目再起计时", row.canStartTimer)
    }

    @Test
    fun a_reminder_is_rendered_in_the_injected_zone_not_the_device_zone() {
        // 提醒时间按注入的时区显示：同一毫秒在洛杉矶落在前一天 18:00
        val epoch = com.attention.domain.reminderEpochMillis(LocalDate.of(2026, 3, 5), 9 * 60, zone)
        val state = stateWith(
            ScheduleEntry("e1", "2026-03-05", "写周报", reminderEpochMillis = epoch),
        )

        val shanghai = project(state = state, zone = zone).dateSections.single().rows.single()
        val losAngeles = project(state = state, zone = ZoneId.of("America/Los_Angeles"))
            .dateSections.single().rows.single()

        assertTrue(shanghai.detailLabel.contains(" · 提醒 09:00"))
        assertEquals("17:00", losAngeles.reminderText)
    }

    @Test
    fun start_timer_is_enabled_only_when_no_timer_is_running() {
        val entry = ScheduleEntry("e1", "2026-03-05", "写周报")

        assertTrue(project(state = stateWith(entry)).dateSections.single().rows.single().canStartTimer)
    }

    @Test
    fun the_entry_requested_by_a_notification_is_resolved_for_the_edit_dialog() {
        // 通知打开的日程条目不受当前筛选影响，单独作为待编辑条目给出
        val state = stateWith(
            ScheduleEntry("other", "2026-03-09", "别的一天", estimatedMinutes = 10),
            ScheduleEntry("wanted", "2026-03-20", "被通知打开的条目", estimatedMinutes = 25),
        )

        val screenState = project(state = state, openScheduleId = "wanted")

        assertEquals("wanted", screenState.pendingEditRow?.entry?.id)
        assertEquals("25", screenState.pendingEditRow?.estimateText)
        assertNull(project(state = state).pendingEditRow)
    }

    @Test
    fun only_unarchived_targets_are_offered_to_link_a_schedule() {
        val state = stateWith().copy(
            targets = listOf(
                Target("a", title = "在用计划"),
                Target("b", title = "已归档计划", archived = true),
            ),
        )

        assertEquals(listOf("a"), project(state = state).targets.map { it.targetId })
    }

    @Test
    fun the_planning_day_follows_the_boundary_across_the_injected_clock() {
        // 默认 03:00 界线：02:59 还属于前一天，03:00 才进入新一天
        val beforeBoundary = LocalDate.of(2026, 3, 6).atTime(2, 59).atZone(zone).toInstant()
        val atBoundary = LocalDate.of(2026, 3, 6).atTime(3, 0).atZone(zone).toInstant()

        assertEquals(LocalDate.of(2026, 3, 5), project(instant = beforeBoundary).planningDate)
        assertEquals(LocalDate.of(2026, 3, 6), project(instant = atBoundary).planningDate)
    }

    @Test
    fun the_same_instant_lands_on_different_planning_days_in_different_zones() {
        val instant = Instant.parse("2026-03-05T19:00:00Z")

        assertEquals(
            LocalDate.of(2026, 3, 6),
            project(instant = instant, zone = zone).planningDate,
        )
        assertEquals(
            LocalDate.of(2026, 3, 5),
            project(instant = instant, zone = ZoneId.of("America/Los_Angeles")).planningDate,
        )
    }

    @Test
    fun the_date_ranges_offer_today_tomorrow_and_all_dates_with_one_selection() {
        // 顶部范围切换是同一页上的三个范围，不是三个顶级入口：任何时刻只有一个范围被选中
        val todayView = project(state = stateWith(), selectedDate = today)
        val tomorrowView = project(state = stateWith(), selectedDate = today.plusDays(1))
        val allDatesView = project(state = stateWith())

        assertEquals(
            listOf("今天", "明天", "所有日期"),
            todayView.ranges.map { it.label },
        )
        assertEquals(listOf(true, false, false), todayView.ranges.map { it.selected })
        assertEquals(today, todayView.ranges[0].date)
        assertEquals(today.plusDays(1), todayView.ranges[1].date)
        assertNull("所有日期不是日期值", todayView.ranges[2].date)
        assertEquals(listOf(false, true, false), tomorrowView.ranges.map { it.selected })
        assertEquals(listOf(false, false, true), allDatesView.ranges.map { it.selected })
    }

    @Test
    fun choosing_tomorrow_only_changes_the_selected_day_and_highlights_adding_a_schedule() {
        // 明天只是切换所选规划日：不复制今天的日程，并且突出添加入口
        val state = stateWith(ScheduleEntry("a", "2026-03-05", "今日条目", estimatedMinutes = 45))
        val screenState = project(state = state, selectedDate = today.plusDays(1))

        assertTrue(screenState.highlightAddSchedule)
        assertEquals("为明天（2026-03-06）添加日程", screenState.addScheduleHeadingLabel)
        assertTrue("今天的条目不会被复制到明天", screenState.selectedRows.isEmpty())
        assertEquals("规划日 2026-03-06 暂无日程条目。", screenState.emptyLabel)

        val todayView = project(state = state, selectedDate = today)
        assertFalse(todayView.highlightAddSchedule)
        assertEquals("新增日程", todayView.addScheduleHeadingLabel)
        assertFalse(project(state = state).highlightAddSchedule)
    }

    @Test
    fun a_running_timer_is_visible_on_the_agenda_page_so_it_can_be_stopped() {
        // 计时卡下线之后，运行中的计时仍然必须在日程页看得见并能停止
        val state = stateWith().copy(
            targets = listOf(Target("t1", title = "写周报")),
            activeTimer = ActiveTimer(
                targetId = "t1",
                startedAtEpochMillis = now.minusSeconds(90).toEpochMilli(),
                lastPlanningDate = "2026-03-05",
            ),
        )

        val screenState = project(state = state)

        val timer = screenState.activeTimer
        assertEquals("写周报", timer?.targetLabel)
        assertFalse(timer?.paused ?: true)
        assertEquals(1, timer?.elapsedMinutes)
        assertNull("没有计时进行时不显示运行中计时", project(state = stateWith()).activeTimer)
    }

    @Test
    fun the_selected_planning_day_lists_its_time_records_for_editing() {
        // 手动时间账本的编辑与删除跟着合并后的日程页走，因此记录按所选规划日给出
        val state = AttentionState(
            timeEntries = listOf(
                com.attention.domain.TimeEntry("e1", "2026-03-05", 30, "t1", com.attention.domain.TimeEntrySource.MANUAL, note = "整理"),
                com.attention.domain.TimeEntry("e2", "2026-03-06", 20),
            ),
            targets = listOf(Target("t1", title = "写周报")),
        )

        val screenState = project(state = state, selectedDate = today)

        assertEquals(listOf("e1"), screenState.timeRecords.map { it.entryId })
        assertEquals(30, screenState.timeRecords.single().durationMinutes)
        assertEquals("写周报", screenState.timeRecords.single().targetLabel)
        assertEquals(com.attention.domain.TimeEntrySource.MANUAL, screenState.timeRecords.single().source)
        assertEquals("整理", screenState.timeRecords.single().note)
        assertEquals("本规划日已记录：30 分钟", screenState.recordedMinutesLabel)
        assertTrue("所有日期视图不给出单日的记录列表", project(state = state).timeRecords.isEmpty())
    }

    @Test
    fun a_selected_date_does_not_drift_with_the_injected_clock() {
        // 给了固定选中日就不随时钟漂移，页面因此可确定地测试
        val selected = LocalDate.of(2026, 3, 20)
        val later = LocalDate.of(2026, 4, 1).atTime(10, 0).atZone(zone).toInstant()

        assertEquals(selected, project(instant = later, selectedDate = selected).selectedDate)
        assertEquals(LocalDate.of(2026, 4, 1), project(instant = later).planningDate)
    }
}