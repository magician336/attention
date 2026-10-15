package com.attention.workspace.state

import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.ScheduleEntry
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import com.attention.domain.TimeEntrySource
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 今天工作台投影的纯 Kotlin 测试：页面只读这份状态，不读完整领域状态，也不读系统时钟。
 *
 * 取样跨过规划日界线（默认 03:00），因此这些断言在「凌晨还没过界线」与「刚过界线」
 * 两个时刻上都成立，而不是只测同一天的顺利路径。
 */
class TodayScreenStateTest {

    /** 上海时间 2026-03-06 01:00，规划日仍是 3 月 5 日。 */
    private val beforeBoundary = Instant.parse("2026-03-05T17:00:00Z")

    /** 上海时间 2026-03-06 02:59，距离规划日界线还差一分钟。 */
    private val lastMinuteBeforeBoundary = Instant.parse("2026-03-05T18:59:00Z")

    /** 上海时间 2026-03-06 03:00，刚好进入新的规划日。 */
    private val firstMinuteAfterBoundary = Instant.parse("2026-03-05T19:00:00Z")

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun project(
        state: AttentionState = AttentionState(),
        instant: Instant = beforeBoundary,
    ) = todayScreenState(state = state, now = instant, zone = zone)

    @Test
    fun the_planning_day_before_the_boundary_is_still_the_previous_day() {
        // 凌晨两点还在昨天：规划日必须是 3 月 5 日
        val screenState = project()

        assertEquals("2026-03-05", screenState.planningDate.toString())
    }

    @Test
    fun the_planning_day_at_the_boundary_rolls_over_to_the_new_day() {
        // 界线上最后一分钟与界线后第一分钟分属两个规划日
        assertEquals("2026-03-05", project(instant = lastMinuteBeforeBoundary).planningDate.toString())
        assertEquals("2026-03-06", project(instant = firstMinuteAfterBoundary).planningDate.toString())
    }

    @Test
    fun the_planning_day_follows_the_injected_zone_rather_than_the_system_clock() {
        // 同一个瞬间在不同时区落在不同的规划日
        val instant = Instant.parse("2026-03-05T20:00:00Z")

        assertEquals(
            "2026-03-06",
            todayScreenState(AttentionState(), instant, ZoneId.of("Asia/Shanghai")).planningDate.toString(),
        )
        assertEquals(
            "2026-03-05",
            todayScreenState(AttentionState(), instant, ZoneId.of("America/Los_Angeles")).planningDate.toString(),
        )
    }

    @Test
    fun the_boundary_label_and_defaults_come_from_the_stored_settings() {
        // 界线的显示与两个输入框的默认值都来自设置，不是页面自己算的
        val screenState = project(
            AttentionState(
                settings = StoredSettings(planningDayBoundaryMinutes = 240, weekStartDay = 3, dailyCapacityMinutes = 240),
            ),
        )

        assertEquals("04:00", screenState.planningDayBoundaryLabel)
        assertEquals(240, screenState.planningDayBoundaryMinutes)
        assertEquals(240, screenState.dailyCapacityMinutes)
        assertEquals("周三", screenState.weekDayOptions.single { it.selected }.label)
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7),
            screenState.weekDayOptions.map { it.day },
        )
    }

    @Test
    fun an_unset_daily_capacity_has_no_default_input_text() {
        // 每日容量未设置时输入框是空的，等待用户填写或清除
        assertNull(project().dailyCapacityMinutes)
    }

    @Test
    fun the_capacity_summary_reports_the_schedules_reserved_on_the_planning_day() {
        // 只有落在规划日上的日程条目占用容量
        val screenState = project(
            AttentionState(
                settings = StoredSettings(dailyCapacityMinutes = 120),
                schedules = listOf(
                    ScheduleEntry(id = "sc1", planningDate = "2026-03-05", title = "写稿", estimatedMinutes = 90),
                    ScheduleEntry(id = "sc2", planningDate = "2026-03-05", title = "复盘", startMinute = 600, endMinute = 660),
                    ScheduleEntry(id = "sc3", planningDate = "2026-03-04", title = "昨天的", estimatedMinutes = 300),
                ),
            ),
        )

        assertEquals("已安排 150 / 120 分钟（超载）", screenState.capacitySummaryLabel)
    }

    @Test
    fun the_capacity_summary_says_so_when_no_capacity_is_set() {
        val screenState = project(
            AttentionState(schedules = listOf(ScheduleEntry(id = "sc1", planningDate = "2026-03-05", title = "写稿", estimatedMinutes = 60))),
        )

        assertEquals("已安排 60 分钟（每日容量未设置）", screenState.capacitySummaryLabel)
    }

    @Test
    fun only_the_records_of_the_projected_planning_day_are_listed() {
        // 时间记录列表只含规划日当天的记录，别的规划日不出现
        val screenState = project(
            AttentionState(
                targets = listOf(Target(id = "t1", title = "写书")),
                timeEntries = listOf(
                    TimeEntry(id = "e1", planningDate = "2026-03-05", durationMinutes = 45, targetId = "t1"),
                    TimeEntry(id = "e2", planningDate = "2026-03-04", durationMinutes = 30, targetId = "t1"),
                ),
            ),
        )

        assertEquals(listOf("e1"), screenState.records.map { it.entryId })
        assertEquals(listOf("t1"), screenState.records.map { it.targetId })
    }

    @Test
    fun a_record_keeps_its_duration_target_note_and_source_for_editing() {
        // 编辑表单的初值全部来自投影，页面不需要回到领域状态里取
        val screenState = project(
            AttentionState(
                targets = listOf(Target(id = "t1", title = "写书")),
                timeEntries = listOf(
                    TimeEntry(
                        id = "e1",
                        planningDate = "2026-03-05",
                        durationMinutes = 45,
                        targetId = "t1",
                        source = TimeEntrySource.SCHEDULE_CONFIRMED,
                        note = "第三章",
                    ),
                ),
            ),
        )

        val record = screenState.records.single()
        assertEquals(45, record.durationMinutes)
        assertEquals("第三章", record.note)
        assertEquals(TimeEntrySource.SCHEDULE_CONFIRMED, record.source)
        assertEquals("2026-03-05", record.planningDate)
    }

    @Test
    fun a_record_keeps_showing_its_target_after_that_target_is_archived() {
        // 归档目标不再接收新记录，但已经存在的记录仍然显示那个目标的名字
        val screenState = project(
            AttentionState(
                targets = listOf(Target(id = "t1", title = "写书", archived = true)),
                timeEntries = listOf(
                    TimeEntry(id = "e1", planningDate = "2026-03-05", durationMinutes = 45, targetId = "t1"),
                ),
            ),
        )

        assertEquals("写书", screenState.records.single().targetLabel)
        assertEquals(emptyList<String>(), screenState.targetOptions.map { it.title })
    }

    @Test
    fun an_unowned_record_is_labelled_as_unowned_activity() {
        val screenState = project(
            AttentionState(
                targets = listOf(Target(id = "t1", title = "写书")),
                timeEntries = listOf(TimeEntry(id = "e1", planningDate = "2026-03-05", durationMinutes = 20)),
            ),
        )

        assertEquals("未归属活动", screenState.records.single().targetLabel)
    }

    @Test
    fun archived_targets_are_not_offered_as_receiving_targets() {
        // 归档目标不能接收新时间记录，因此页面根本拿不到它的选项
        val screenState = project(
            AttentionState(
                targets = listOf(
                    Target(id = "t1", title = "写书"),
                    Target(id = "t2", title = "旧计划", archived = true),
                ),
            ),
        )

        assertEquals(listOf("写书"), screenState.targetOptions.map { it.title })
        assertEquals(listOf("写书"), screenState.quickRecordTargets.map { it.title })
    }

    @Test
    fun the_quick_record_row_offers_only_the_first_four_active_targets() {
        val screenState = project(
            AttentionState(targets = (1..6).map { Target(id = "t$it", title = "目标$it") }),
        )

        assertEquals(listOf("t1", "t2", "t3", "t4"), screenState.quickRecordTargets.map { it.targetId })
        assertEquals(6, screenState.targetOptions.size)
    }

    @Test
    fun unowned_records_stay_visible_and_count_towards_the_planning_day() {
        // 未归属活动参与当天的分钟合计，但在归入计划前不推进任何目标
        val screenState = project(
            AttentionState(
                targets = listOf(Target(id = "t1", title = "写书")),
                timeEntries = listOf(
                    TimeEntry(id = "e1", planningDate = "2026-03-05", durationMinutes = 60, targetId = "t1"),
                    TimeEntry(id = "e2", planningDate = "2026-03-05", durationMinutes = 20, targetId = null),
                ),
            ),
        )

        assertEquals(2, screenState.records.size)
        assertNull(screenState.records.single { it.entryId == "e2" }.targetId)
        assertEquals("本规划日已记录：80 分钟", screenState.recordedMinutesLabel)
    }

    @Test
    fun an_empty_planning_day_projects_zero_minutes() {
        val screenState = project()

        assertTrue(screenState.records.isEmpty())
        assertEquals("本规划日已记录：0 分钟", screenState.recordedMinutesLabel)
    }

    @Test
    fun the_onboarding_flow_is_still_signalled_by_the_projection() {
        assertEquals(false, project().onboardingCompleted)
        assertEquals(true, project(AttentionState(onboardingCompleted = true)).onboardingCompleted)
    }

    @Test
    fun only_the_schedules_of_the_projected_planning_day_become_today_rows() {
        // 别的规划日的条目不出现在今天工作台的「今日安排」里
        val screenState = project(
            AttentionState(
                schedules = listOf(
                    ScheduleEntry(id = "sc1", planningDate = "2026-03-05", title = "写稿", estimatedMinutes = 90),
                    ScheduleEntry(id = "sc2", planningDate = "2026-03-04", title = "昨天的", estimatedMinutes = 300),
                ),
            ),
        )

        assertEquals(listOf("sc1"), screenState.scheduleRows.map { it.entry.id })
    }

    @Test
    fun a_schedule_row_shows_its_time_target_estimate_and_completion() {
        val screenState = project(
            AttentionState(
                targets = listOf(Target(id = "t1", title = "写书")),
                schedules = listOf(
                    ScheduleEntry(
                        id = "sc1",
                        planningDate = "2026-03-05",
                        title = "写稿",
                        startMinute = 600,
                        endMinute = 660,
                        estimatedMinutes = 60,
                        targetId = "t1",
                        note = "第一章",
                    ),
                ),
            ),
        )

        val row = screenState.scheduleRows.single()
        assertEquals("2026-03-05 · 10:00-11:00 · 60 分钟 · 写书 · 无提醒", row.detailLabel)
        assertEquals("10:00", row.startText)
        assertEquals("11:00", row.endText)
        assertEquals("60", row.estimateText)
        assertEquals("", row.reminderText)
        assertEquals("第一章", row.note)
    }

    @Test
    fun a_reminder_is_formatted_with_the_injected_zone_not_the_system_default() {
        // 2026-03-05T00:30Z 在上海是 08:30，在洛杉矶是前一天 16:30
        val entry = ScheduleEntry(
            id = "sc1",
            planningDate = "2026-03-05",
            title = "写稿",
            reminderEpochMillis = Instant.parse("2026-03-05T00:30:00Z").toEpochMilli(),
        )

        assertEquals(
            "· 提醒 08:30",
            project(AttentionState(schedules = listOf(entry))).scheduleRows.single().detailLabel.substringAfter("分钟 "),
        )
        assertEquals(
            "· 提醒 16:30",
            todayScreenState(
                AttentionState(schedules = listOf(entry)),
                beforeBoundary,
                ZoneId.of("America/Los_Angeles"),
            ).scheduleRows.single().detailLabel.substringAfter("分钟 "),
        )
    }

    @Test
    fun a_schedule_row_can_only_start_a_timer_while_none_is_running() {
        val schedules = listOf(ScheduleEntry(id = "sc1", planningDate = "2026-03-05", title = "写稿"))
        val running = ActiveTimer(
            targetId = null,
            startedAtEpochMillis = beforeBoundary.minusSeconds(20 * 60).toEpochMilli(),
            lastPlanningDate = "2026-03-05",
        )

        assertEquals(true, project(AttentionState(schedules = schedules)).scheduleRows.single().canStartTimer)
        assertEquals(
            false,
            project(AttentionState(schedules = schedules, activeTimer = running)).scheduleRows.single().canStartTimer,
        )
    }
}
