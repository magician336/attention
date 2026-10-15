package com.attention.workspace.state

import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.TimerSegment
import com.attention.domain.occurrences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日程页投影的纯 Kotlin 测试：页面只读这份状态，不读完整领域状态，也不读系统时钟。
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
        selectedScope: AgendaRangeScope? = null,
        openScheduleId: String? = null,
        stoppedEntryIds: List<String>? = null,
    ) = agendaScreenState(
        state = state,
        now = instant,
        zone = zone,
        selectedDate = selectedDate,
        selectedScope = selectedScope,
        openScheduleId = openScheduleId,
        stoppedEntryIds = stoppedEntryIds,
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
            emptyList<String>(),
            screenState.dateSections.first().timelineRows.map { it.entry.id },
        )
        assertEquals(
            listOf("a", "c"),
            screenState.dateSections.first().unscheduledRows.map { it.entry.id },
        )
        assertNull("有条目时不显示空态文案", screenState.emptyLabel)
    }

    @Test
    fun a_selected_planning_day_shows_only_that_day_entries() {
        // 选中规划日时只显示当天的日程条目，容量卡片针对该日
        val screenState = project(
            state = stateWith(
                ScheduleEntry("a", "2026-03-05", "当日条目", startMinute = 540, endMinute = 585, estimatedMinutes = 45),
                ScheduleEntry("b", "2026-03-06", "次日条目", estimatedMinutes = 30),
            ),
            selectedDate = LocalDate.of(2026, 3, 5),
        )

        assertEquals("规划日 2026-03-05", screenState.headingLabel)
        assertEquals("筛选：规划日 2026-03-05", screenState.filterLabel)
        assertEquals(listOf("a"), screenState.timelineRows.map { it.entry.id })
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
            // 物化出的条目没有具体时间，因此落在未安排时间，而不是被排到时间轴上
            assertEquals(0, section.timelineRows.size)
            assertEquals(1, section.unscheduledRows.size)
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

        val row = project(state = state).dateSections.single().timelineRows.single()

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
            ScheduleEntry("e1", "2026-03-05", "写周报", reminderEpochMillis = epoch, startMinute = 540, endMinute = 600),
        )

        val shanghai = project(state = state, zone = zone).dateSections.single().timelineRows.single()
        val losAngeles = project(state = state, zone = ZoneId.of("America/Los_Angeles"))
            .dateSections.single().timelineRows.single()

        assertTrue(shanghai.detailLabel.contains(" · 提醒 09:00"))
        assertEquals("17:00", losAngeles.reminderText)
    }

    @Test
    fun start_timer_is_enabled_only_when_no_timer_is_running() {
        val entry = ScheduleEntry("e1", "2026-03-05", "写周报", startMinute = 540, endMinute = 600)

        assertTrue(project(state = stateWith(entry)).dateSections.single().timelineRows.single().canStartTimer)
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
        val todayView = project(
            state = stateWith(),
            selectedDate = today,
            selectedScope = AgendaRangeScope.TODAY,
        )
        val tomorrowView = project(
            state = stateWith(),
            selectedDate = today.plusDays(1),
            selectedScope = AgendaRangeScope.TOMORROW,
        )
        val allDatesView = project(
            state = stateWith(),
            selectedScope = AgendaRangeScope.ALL_DATES,
        )

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
    fun an_explicitly_chosen_range_stays_selected_after_the_planning_day_boundary() {
        // 用户显式点了「今天」之后应用跨过规划日界线：范围选择由页面选择状态给出，
        // 不由当前时刻重算的规划日反推，因此「今天」仍然可见地被选中。
        val laterInstant = LocalDate.of(2026, 3, 6).atTime(10, 0).atZone(zone).toInstant()
        val afterBoundary = project(
            state = stateWith(),
            instant = laterInstant,
            selectedDate = LocalDate.of(2026, 3, 6),
            selectedScope = AgendaRangeScope.TODAY,
        )

        assertEquals(LocalDate.of(2026, 3, 6), afterBoundary.planningDate)
        assertEquals(listOf(true, false, false), afterBoundary.ranges.map { it.selected })
        // 页面停在用户显式选中的那一天上，标题与范围高亮说的是同一天
        assertEquals(LocalDate.of(2026, 3, 6), afterBoundary.selectedDate)
    }

    @Test
    fun choosing_tomorrow_only_changes_the_selected_day_and_highlights_adding_a_schedule() {
        // 明天只是切换所选规划日：不复制今天的日程，并且突出添加入口
        val state = stateWith(ScheduleEntry("a", "2026-03-05", "今日条目", estimatedMinutes = 45))
        val screenState = project(
            state = state,
            selectedDate = today.plusDays(1),
            selectedScope = AgendaRangeScope.TOMORROW,
        )

        assertTrue(screenState.highlightAddSchedule)
        assertEquals("为明天（2026-03-06）添加日程", screenState.addScheduleHeadingLabel)
        assertTrue("今天的条目不会被复制到明天", screenState.timelineRows.isEmpty() && screenState.unscheduledRows.isEmpty())
        assertEquals("规划日 2026-03-06 暂无日程条目。", screenState.emptyLabel)

        val todayView = project(state = state, selectedDate = today, selectedScope = AgendaRangeScope.TODAY)
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
    fun the_elapsed_minutes_come_from_the_injected_now_and_not_from_a_wall_clock() {
        // 页面不读时钟：已投入只能随组合根注入的那一份 now 增长。
        val state = stateWith().copy(
            targets = listOf(Target("t1", title = "写周报")),
            activeTimer = ActiveTimer(
                targetId = "t1",
                startedAtEpochMillis = now.minusSeconds(90).toEpochMilli(),
                lastPlanningDate = "2026-03-05",
            ),
        )

        val atStart = project(state = state, instant = now).activeTimer
        val later = project(state = state, instant = now.plusSeconds(5 * 60)).activeTimer

        assertEquals(1, atStart?.elapsedMinutes)
        assertEquals(6, later?.elapsedMinutes)
    }

    @Test
    fun a_paused_timer_freezes_the_elapsed_minutes_and_names_the_next_step() {
        // 暂停不等于已保存：这行文字必须同时说清「还没记入」与「下一步做什么」。
        val pausedAt = now.minusSeconds(30 * 60)
        val state = stateWith().copy(
            targets = listOf(Target("t1", title = "写周报")),
            activeTimer = ActiveTimer(
                targetId = "t1",
                startedAtEpochMillis = pausedAt.toEpochMilli(),
                accumulatedMillis = 5 * 60_000L,
                paused = true,
                lastPlanningDate = "2026-03-05",
                completedSegments = listOf(
                    TimerSegment(now.minusSeconds(35 * 60).toEpochMilli(), pausedAt.toEpochMilli()),
                ),
            ),
        )

        val timer = project(state = state, instant = now.plusSeconds(60 * 60)).activeTimer

        assertTrue(timer?.paused ?: false)
        // 暂停后时钟继续走，已投入仍停在暂停那一刻的 5 分钟。
        assertEquals(5, timer?.elapsedMinutes)
        assertEquals("已投入 5 分钟", timer?.elapsedLabel)
        assertEquals("已暂停", timer?.statusLabel)
        val hint = timer?.pendingHintLabel
        assertNotNull("暂停时必须告诉用户下一步", hint)
        assertTrue("提示要说明这 5 分钟尚未保存：" + hint, hint!!.contains("尚未保存"))
        assertTrue("提示要给出恢复这条路：" + hint, hint.contains("恢复"))
        assertTrue("提示要给出结束保存这条路：" + hint, hint.contains("结束并保存"))
    }

    @Test
    fun a_running_timer_carries_no_pending_hint_and_its_target_id() {
        // 运行中不需要「还没保存」提示；目标 id 供「切换计时目标」菜单标出当前目标。
        val state = stateWith().copy(
            targets = listOf(Target("t1", title = "写周报")),
            activeTimer = ActiveTimer(
                targetId = "t1",
                startedAtEpochMillis = now.minusSeconds(120).toEpochMilli(),
                lastPlanningDate = "2026-03-05",
            ),
        )

        val timer = project(state = state).activeTimer

        assertEquals("t1", timer?.targetId)
        assertEquals("计时中", timer?.statusLabel)
        assertEquals("已投入 2 分钟", timer?.elapsedLabel)
        assertNull("运行中的计时没有待保存的投入", timer?.pendingHintLabel)
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

    // ── 工作台首屏：固定区块顺序、时间轴与未安排时间分组、投入与容量的展示口径 ──

    @Test
    fun the_first_screen_sections_keep_one_fixed_order_in_every_view_and_across_the_boundary() {
        // 首屏自上而下固定为：规划日与每日容量摘要、时间轴、进行中的计时状态、添加日程入口、事后补记，
        // 重复日程作为折叠的次级入口排在最后。顺序由投影给出，因此它对注入的时间与时区是纯函数。
        val expected = listOf(
            AgendaWorkbenchSection.PLANNING_DAY_SUMMARY,
            AgendaWorkbenchSection.TIMELINE,
            AgendaWorkbenchSection.RUNNING_TIMER,
            AgendaWorkbenchSection.ADD_SCHEDULE,
            AgendaWorkbenchSection.BACKFILL,
            AgendaWorkbenchSection.RECURRENCE,
        )
        val beforeBoundary = LocalDate.of(2026, 3, 6).atTime(2, 59).atZone(zone).toInstant()
        val atBoundary = LocalDate.of(2026, 3, 6).atTime(3, 0).atZone(zone).toInstant()

        assertEquals(expected, project(selectedDate = today).workbenchSections)
        assertEquals(expected, project(selectedDate = today.plusDays(1), selectedScope = AgendaRangeScope.TOMORROW).workbenchSections)
        assertEquals(expected, project(selectedScope = AgendaRangeScope.ALL_DATES).workbenchSections)
        assertEquals(expected, project(instant = beforeBoundary, selectedDate = today).workbenchSections)
        assertEquals(expected, project(instant = atBoundary, selectedDate = LocalDate.of(2026, 3, 6)).workbenchSections)
        assertEquals(
            expected,
            project(instant = beforeBoundary, zone = ZoneId.of("America/Los_Angeles"), selectedDate = today).workbenchSections,
        )
    }

    @Test
    fun the_timeline_orders_entries_by_start_time_and_groups_the_rest_as_unscheduled() {
        // 有具体时间的按开始/结束时间排列；没有具体时间的进入独立的「未安排时间」区域
        val state = stateWith(
            ScheduleEntry("late", "2026-03-05", "下午的", startMinute = 14 * 60, endMinute = 15 * 60),
            ScheduleEntry("open", "2026-03-05", "没定时间的", estimatedMinutes = 20),
            ScheduleEntry("early", "2026-03-05", "上午的", startMinute = 9 * 60, endMinute = 10 * 60),
        )

        val screenState = project(state = state, selectedDate = today)

        assertEquals(listOf("early", "late"), screenState.timelineRows.map { it.entry.id })
        assertEquals(listOf("open"), screenState.unscheduledRows.map { it.entry.id })
        // 所有日期视图每一天各自有一份同样的分组
        val allDates = project(state = state, selectedScope = AgendaRangeScope.ALL_DATES).dateSections.single()
        assertEquals(listOf("early", "late"), allDates.timelineRows.map { it.entry.id })
        assertEquals(listOf("open"), allDates.unscheduledRows.map { it.entry.id })
    }

    @Test
    fun the_entry_being_timed_is_pinned_to_the_top_of_the_timeline() {
        // 正在计时的条目固定在时间轴顶部，即使它的时段排在后面
        val state = stateWith(
            ScheduleEntry("early", "2026-03-05", "上午的", startMinute = 9 * 60, endMinute = 10 * 60),
            ScheduleEntry("timing", "2026-03-05", "正在做的", startMinute = 13 * 60, endMinute = 14 * 60),
        ).copy(
            activeTimer = ActiveTimer(
                targetId = null,
                startedAtEpochMillis = now.minusSeconds(60).toEpochMilli(),
                lastPlanningDate = "2026-03-05",
                scheduleEntryId = "timing",
            ),
        )

        val rows = project(state = state, selectedDate = today).timelineRows

        assertEquals(listOf("timing", "early"), rows.map { it.entry.id })
        assertTrue("置顶的行标明自己正在计时", rows.first().timing)
        assertFalse("其余行不标正在计时", rows[1].timing)
        // 自由计时没有日程来源，时间轴不会被置顶任何一行
        val freeTimer = stateWith(ScheduleEntry("early", "2026-03-05", "上午的", startMinute = 9 * 60)).copy(
            activeTimer = ActiveTimer(targetId = null, startedAtEpochMillis = now.toEpochMilli(), lastPlanningDate = "2026-03-05"),
        )
        assertTrue(project(state = freeTimer, selectedDate = today).timelineRows.none { it.timing })
    }

    @Test
    fun timing_an_entry_without_a_start_time_puts_it_at_the_top_of_the_timeline() {
        // 「未安排时间」里的日程也能起计时：一旦正在计时，它就出现在时间轴第一位
        val state = stateWith(
            ScheduleEntry("early", "2026-03-05", "上午的", startMinute = 9 * 60, endMinute = 10 * 60),
            ScheduleEntry("open", "2026-03-05", "想到再做的", estimatedMinutes = 30),
        ).copy(
            activeTimer = ActiveTimer(
                targetId = null,
                startedAtEpochMillis = now.toEpochMilli(),
                lastPlanningDate = "2026-03-05",
                scheduleEntryId = "open",
            ),
        )

        val screenState = project(state = state, selectedDate = today)

        assertEquals(listOf("open", "early"), screenState.timelineRows.map { it.entry.id })
        assertTrue(screenState.timelineRows.first().timing)
        assertTrue("它不再留在未安排时间区域", screenState.unscheduledRows.isEmpty())
    }

    @Test
    fun a_timeline_row_separates_planned_investment_from_actual_investment() {
        // 预计时长是计划投入，永远不被当成实际投入展示；实际投入只来自与这条日程关联的记录
        val state = stateWith(
            ScheduleEntry("planned", "2026-03-05", "写周报", startMinute = 540, endMinute = 600, estimatedMinutes = 60, targetId = "t1"),
        ).copy(
            targets = listOf(Target("t1", title = "工作")),
        )

        val row = project(state = state, selectedDate = today).timelineRows.single()

        assertEquals("计划投入 60 分钟 · 实际投入 0 分钟 · 未完成", row.investmentLabel)
        assertEquals("工作", row.targetLabel)
        assertFalse(row.completed)
        assertNull("没有容量就不提示超载", row.capacityNotice)
    }

    @Test
    fun a_timeline_row_sums_every_record_linked_to_that_entry_as_actual_investment() {
        // 事后补记、日程确认与计时结算都算这条日程的实际投入，页面不按来源过滤
        val state = stateWith(
            ScheduleEntry("e1", "2026-03-05", "写周报", startMinute = 540, endMinute = 600, estimatedMinutes = 60),
        ).copy(
            timeEntries = listOf(
                com.attention.domain.TimeEntry("t1", "2026-03-05", 15, source = com.attention.domain.TimeEntrySource.MANUAL, scheduleEntryId = "e1"),
                com.attention.domain.TimeEntry("t2", "2026-03-05", 20, source = com.attention.domain.TimeEntrySource.TIMER, scheduleEntryId = "e1"),
                com.attention.domain.TimeEntry("t3", "2026-03-05", 5, source = com.attention.domain.TimeEntrySource.SCHEDULE_CONFIRMED, scheduleEntryId = "e1"),
                com.attention.domain.TimeEntry("t4", "2026-03-05", 99, source = com.attention.domain.TimeEntrySource.MANUAL, scheduleEntryId = "other"),
                com.attention.domain.TimeEntry("t5", "2026-03-05", 7),
            ),
        )

        val row = project(state = state, selectedDate = today).timelineRows.single()

        assertEquals("计划投入 60 分钟 · 实际投入 40 分钟 · 未完成", row.investmentLabel)
    }

    @Test
    fun a_timeline_row_marks_completion_once_the_actual_investment_reaches_the_plan() {
        // 完成状态由领域按计划时长与实际投入判定，页面只显示结果
        val state = stateWith(
            ScheduleEntry("e1", "2026-03-05", "写周报", startMinute = 540, endMinute = 600, estimatedMinutes = 30),
        ).copy(
            timeEntries = listOf(
                com.attention.domain.TimeEntry("t1", "2026-03-05", 45, source = com.attention.domain.TimeEntrySource.TIMER, scheduleEntryId = "e1"),
            ),
        )

        val row = project(state = state, selectedDate = today).timelineRows.single()

        assertTrue("实际投入达到计划时长即完成", row.completed)
        assertEquals("计划投入 30 分钟 · 实际投入 45 分钟 · 已完成", row.investmentLabel)
    }

    @Test
    fun an_entry_that_exceeds_the_daily_capacity_is_flagged_but_still_editable() {
        // 容量超载只产生「已超出容量」提示，不阻止保存日程或实际投入
        val state = stateWith(
            ScheduleEntry("huge", "2026-03-05", "一整天的课", startMinute = 540, endMinute = 600, estimatedMinutes = 300),
            ScheduleEntry("fits", "2026-03-05", "晨读", startMinute = 480, endMinute = 500, estimatedMinutes = 20),
        ).let { it.copy(settings = StoredSettings(dailyCapacityMinutes = 120)) }

        val rows = project(state = state, selectedDate = today).timelineRows

        assertEquals("已超出容量", rows.first { it.entry.id == "huge" }.capacityNotice)
        assertNull("没超载的条目不显示超载提示", rows.first { it.entry.id == "fits" }.capacityNotice)
        assertEquals(2, rows.size)
        assertTrue("超载条目仍然可以起计时", rows.first { it.entry.id == "huge" }.canStartTimer)
    }

    @Test
    fun the_workbench_summarises_the_planning_day_boundary_week_start_and_daily_capacity_read_only() {
        // 工作台只读展示规划日界线、周起始日与每日容量摘要，不提供完整设置表单
        val state = stateWith(
            ScheduleEntry("a", "2026-03-05", "晨读", estimatedMinutes = 45),
        ).let {
            it.copy(
                settings = StoredSettings(
                    planningDayBoundaryMinutes = 180,
                    weekStartDay = 7,
                    dailyCapacityMinutes = 120,
                ),
            )
        }

        val summary = project(state = state, selectedDate = today).workbenchSummary

        assertEquals("规划日 2026-03-05", summary.planningDateLabel)
        assertEquals("规划日界线 03:00", summary.boundaryLabel)
        assertEquals("周起始日 周日", summary.weekStartLabel)
        assertEquals("每日容量 120 分钟", summary.capacitySettingLabel)
        assertEquals("已安排 45 / 120 分钟", summary.capacityUsageLabel)
    }

    @Test
    fun the_daily_capacity_summary_says_so_when_no_capacity_is_set() {
        val summary = project(
            state = stateWith(ScheduleEntry("a", "2026-03-05", "晨读", estimatedMinutes = 45)),
            selectedDate = today,
        ).workbenchSummary

        assertEquals("每日容量未设置", summary.capacitySettingLabel)
        assertEquals("已安排 45 分钟（每日容量未设置）", summary.capacityUsageLabel)
    }

    @Test
    fun the_all_dates_view_summarises_the_day_without_a_single_day_capacity_readout() {
        // 所有日期是特殊视图：容量按天在各自的时间轴分组里给出，顶层摘要不冒充某一天
        val state = stateWith(
            ScheduleEntry("a", "2026-03-05", "晨读", estimatedMinutes = 45),
            ScheduleEntry("b", "2026-03-06", "上课", estimatedMinutes = 90),
        ).let { it.copy(settings = StoredSettings(dailyCapacityMinutes = 120)) }

        val screenState = project(state = state, selectedScope = AgendaRangeScope.ALL_DATES)

        assertEquals("所有日期", screenState.workbenchSummary.planningDateLabel)
        assertNull(screenState.workbenchSummary.capacityUsageLabel)
        assertEquals(
            listOf("已安排 45 / 120 分钟", "已安排 90 / 120 分钟"),
            screenState.dateSections.map { it.capacityLabel },
        )
    }

    @Test
    fun an_empty_planning_day_invites_the_first_schedule_with_an_add_entry_point() {
        // 当天没有日程时给出「安排今天第一件事」，页面据此直接给出添加日程入口
        val todayEmpty = project(state = stateWith(), selectedDate = today, selectedScope = AgendaRangeScope.TODAY)
        val tomorrowEmpty = project(
            state = stateWith(),
            selectedDate = today.plusDays(1),
            selectedScope = AgendaRangeScope.TOMORROW,
        )
        val withEntries = project(
            state = stateWith(ScheduleEntry("a", "2026-03-05", "晨读", estimatedMinutes = 20)),
            selectedDate = today,
        )

        assertEquals("安排今天第一件事", todayEmpty.workbenchEmptyLabel)
        assertEquals("安排今天第一件事", tomorrowEmpty.workbenchEmptyLabel)
        assertNull("已经有安排时不显示空态引导", withEntries.workbenchEmptyLabel)
        assertTrue("所有日期视图的空态仍走所有日期的文案", project(state = stateWith()).workbenchEmptyLabel == null)
    }

    @Test
    fun the_quick_add_menu_offers_add_schedule_and_start_a_free_timer() {
        // 「+」菜单只有两个主要动作：安排一件事，和为已经发生的事开始计时。
        // 顺序与标签都由投影给出，页面因此没有自己的动作副本。
        val menu = project(state = stateWith()).quickActions

        assertEquals(
            listOf(
                AgendaQuickActionOption(AgendaQuickAction.ADD_SCHEDULE, "添加日程"),
                AgendaQuickActionOption(AgendaQuickAction.START_FREE_TIMER, "开始自由计时"),
            ),
            menu,
        )
    }

    @Test
    fun the_free_timer_options_are_the_unowned_activity_and_the_unarchived_plans() {
        // 自由计时不需要先建日程：能选的是未归属活动与未归档计划，归档计划不出现
        val state = AttentionState(
            targets = listOf(
                Target(id = "t-active", title = "写作"),
                Target(id = "t-archived", title = "旧计划", archived = true),
            ),
        )

        val options = project(state = state).freeTimerOptions

        assertEquals(
            listOf(
                AgendaFreeTimerOption(null, "未归属活动", false),
                AgendaFreeTimerOption("t-active", "写作", false),
            ),
            options,
        )
    }

    @Test
    fun the_free_timer_option_marks_the_current_timer_target_in_words() {
        // 选中态不靠颜色：正在计时的那个目标由投影标出来，页面原样展示。
        val state = AttentionState(
            targets = listOf(Target(id = "t1", title = "写作")),
            activeTimer = ActiveTimer(
                targetId = "t1",
                startedAtEpochMillis = now.minusSeconds(300).toEpochMilli(),
                lastPlanningDate = "2026-03-05",
            ),
        )

        val options = project(state = state).freeTimerOptions

        assertEquals(listOf(false, true), options.map { it.selected })
    }

    @Test
    fun the_add_schedule_sheet_defaults_to_the_planning_day_in_view() {
        // 底部面板打开时规划日已经填好：没选过就落在当前规划日，选了哪一天就用那一天
        val allDates = project(state = stateWith(), selectedScope = AgendaRangeScope.ALL_DATES)
        val tomorrow = project(
            state = stateWith(),
            selectedDate = today.plusDays(1),
            selectedScope = AgendaRangeScope.TOMORROW,
        )

        assertEquals("2026-03-05", allDates.addScheduleDefaultDate)
        assertEquals("规划日 2026-03-05", allDates.addScheduleDefaultDateLabel)
        assertEquals("2026-03-06", tomorrow.addScheduleDefaultDate)
        assertEquals("规划日 2026-03-06", tomorrow.addScheduleDefaultDateLabel)
    }

    // ---------------------------------------------------------------- 停止计时的结果面板

    /** 停止计时后落下的那笔记录：领域命令已经把它写进状态，面板按 id 读回它。 */
    private fun stoppedState(
        entry: ScheduleEntry,
        records: List<com.attention.domain.TimeEntry>,
    ) = stateWith(entry).copy(
        targets = listOf(Target("t1", title = "工作")),
        timeEntries = records,
    )

    private fun timerRecord(
        id: String,
        date: String = "2026-03-05",
        minutes: Int = 20,
        scheduleEntryId: String? = "e1",
    ) = com.attention.domain.TimeEntry(
        id = id,
        planningDate = date,
        durationMinutes = minutes,
        targetId = "t1",
        source = com.attention.domain.TimeEntrySource.TIMER,
        scheduleEntryId = scheduleEntryId,
    )

    @Test
    fun there_is_no_result_panel_until_a_timer_is_stopped() {
        // 没有刚停止的那一笔时页面不展示任何结果面板：它不是一段历史
        assertNull(project(state = stateWith(ScheduleEntry("e1", "2026-03-05", "写周报", estimatedMinutes = 60))).stopResult)
        assertNull(project(state = stoppedState(ScheduleEntry("e1", "2026-03-05", "写周报", estimatedMinutes = 60), listOf(timerRecord("t1")))).stopResult)
    }

    @Test
    fun the_result_panel_reports_actual_planned_completion_and_attribution() {
        // 面板的四个要素：实际投入、计划时长、完成结果与记录归属，全部来自领域读数
        val entry = ScheduleEntry("e1", "2026-03-05", "写周报", startMinute = 540, endMinute = 600, estimatedMinutes = 60, targetId = "t1")
        val state = stoppedState(entry, listOf(timerRecord("t1", minutes = 20)))

        val result = project(state = state, selectedDate = today, stoppedEntryIds = listOf("t1")).stopResult!!

        assertEquals("e1", result.scheduleEntryId)
        assertEquals(20, result.actualMinutes)
        assertEquals("实际投入 20 分钟", result.actualLabel)
        assertEquals(60, result.plannedMinutes)
        assertEquals("计划时长 60 分钟", result.plannedLabel)
        // 不足计划：完成状态仍由用户决定，因此既不算完成也不自动跳过决策
        assertFalse(result.completed)
        assertEquals("未完成", result.completionLabel)
        assertTrue("有计划且未达标时把完成决定交给用户", result.needsCompletionDecision)
        assertEquals("工作", result.attributionLabel)
    }

    @Test
    fun an_entry_without_a_planned_duration_reports_no_plan_and_leaves_completion_alone() {
        // 没有计划时长的日程不自动改变完成状态，也不冒充「未达标」
        val entry = ScheduleEntry("e1", "2026-03-05", "随手做的", targetId = "t1")
        val state = stoppedState(entry, listOf(timerRecord("t1", minutes = 90)))

        val result = project(state = state, selectedDate = today, stoppedEntryIds = listOf("t1")).stopResult!!

        assertNull("没有计划就没有计划时长", result.plannedMinutes)
        assertEquals("未设置计划时长", result.plannedLabel)
        assertFalse(result.completed)
        assertEquals("未完成", result.completionLabel)
        assertFalse("没有计划就不存在「未达标」这回事", result.needsCompletionDecision)
    }

    @Test
    fun reaching_the_plan_completes_the_entry_so_the_user_does_not_tick_it_again() {
        // 实际投入达到计划时日程已由领域结算成完成，面板如实说完成、不再要求用户勾选
        val entry = ScheduleEntry("e1", "2026-03-05", "写周报", startMinute = 540, endMinute = 600, estimatedMinutes = 30, targetId = "t1")
        val state = stoppedState(entry, listOf(timerRecord("t1", minutes = 45)))

        val result = project(state = state, selectedDate = today, stoppedEntryIds = listOf("t1")).stopResult!!

        assertTrue("实际投入 45 分钟超过计划 30 分钟", result.completed)
        assertEquals("已完成", result.completionLabel)
        assertFalse("已经完成就不需要用户再决定一次", result.needsCompletionDecision)
    }

    @Test
    fun a_stop_split_across_planning_days_is_reported_as_several_records() {
        // 跨规划日界线的那次停止落成多笔，面板如实呈现多笔，不自行合成一笔
        val entry = ScheduleEntry("e1", "2026-03-05", "夜间长跑", startMinute = 1320, endMinute = 1439, estimatedMinutes = 240, targetId = "t1")
        val state = stoppedState(
            entry,
            listOf(
                timerRecord("t1", date = "2026-03-05", minutes = 60),
                timerRecord("t2", date = "2026-03-06", minutes = 30),
            ),
        )

        val result = project(state = state, selectedDate = today, stoppedEntryIds = listOf("t1", "t2")).stopResult!!

        assertEquals(2, result.records.size)
        assertEquals(listOf("2026-03-05", "2026-03-06"), result.records.map { it.planningDate })
        assertEquals(listOf(60, 30), result.records.map { it.minutes })
        assertEquals("2026-03-05 · 60 分钟", result.records.first().label)
        assertEquals("两笔合计才是这次停止的实际投入", 90, result.actualMinutes)
    }

    @Test
    fun the_result_panel_disappears_once_its_records_are_consumed() {
        // 组合根消费掉这次结果（清空 id 列表）后面板不再出现，陈旧数据不会被反复展示
        val entry = ScheduleEntry("e1", "2026-03-05", "写周报", startMinute = 540, endMinute = 600, estimatedMinutes = 60, targetId = "t1")
        val state = stoppedState(entry, listOf(timerRecord("t1", minutes = 20)))

        assertNotNull(project(state = state, selectedDate = today, stoppedEntryIds = listOf("t1")).stopResult)
        assertNull("消费之后不再展示", project(state = state, selectedDate = today, stoppedEntryIds = null).stopResult)
        assertNull(project(state = state, selectedDate = today, stoppedEntryIds = emptyList()).stopResult)
    }

    @Test
    fun stopping_without_a_schedule_still_reports_investment_and_attribution() {
        // 自由计时没有日程来源：面板仍然如实呈现投入与归属，只是不谎称某条日程完成了
        val state = AttentionState(
            targets = listOf(Target("t1", title = "阅读")),
            timeEntries = listOf(
                com.attention.domain.TimeEntry(
                    id = "t1",
                    planningDate = "2026-03-05",
                    durationMinutes = 15,
                    targetId = "t1",
                    source = com.attention.domain.TimeEntrySource.TIMER,
                ),
            ),
        )

        val result = project(state = state, selectedDate = today, stoppedEntryIds = listOf("t1")).stopResult!!

        assertNull("自由计时没有关联日程", result.scheduleEntryId)
        assertEquals(15, result.actualMinutes)
        assertEquals("阅读", result.attributionLabel)
        assertFalse(result.completed)
        assertFalse(result.needsCompletionDecision)
    }

    @Test
    fun a_stale_result_id_that_no_longer_exists_does_not_resurrect_the_panel() {
        // 记录已被删除时，面板不凭一个失效的 id 重建已经消失的事实
        val entry = ScheduleEntry("e1", "2026-03-05", "写周报", estimatedMinutes = 60, targetId = "t1")
        val state = stoppedState(entry, listOf(timerRecord("t1", minutes = 20)))

        assertNull(project(state = state, selectedDate = today, stoppedEntryIds = listOf("gone")).stopResult)
    }

    @Test
    fun every_timeline_row_offers_a_visible_completion_check_and_a_more_menu() {
        // 完成勾选框与更多菜单是行上并存的可见入口；长按只能作为额外的熟练操作快捷方式
        val entry = ScheduleEntry("e1", "2026-03-05", "写周报", startMinute = 540, endMinute = 600, estimatedMinutes = 60, targetId = "t1")
        val row = project(state = stoppedState(entry, listOf(timerRecord("t1", minutes = 20))), selectedDate = today).timelineRows.single()

        assertEquals("e1", row.entry.id)
        assertTrue("行上给出可见的完成勾选框", row.offersCompletionCheck)
        assertTrue("行上给出可见的更多菜单", row.offersMoreMenu)
    }
}