package com.attention.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工作台时间轴的领域规则：有具体开始时间的日程按开始/结束时间排列，
 * 没有具体时间的日程进入独立的「未安排时间」，正在计时的那条固定在时间轴顶部，
 * 以及单条日程自己是否超出这一天的每日容量。
 *
 * 这些都是「哪条记录属于哪一类、放在哪一位、算不算超载」的规则，因此留在 `:domain`，
 * 页面投影只把它们读成可显示的行与标签（ADR-0005「后续页面不得重复」）。
 */
class AgendaTimelineTest {

    private val day: LocalDate = LocalDate.of(2026, 3, 5)

    private fun entry(
        id: String,
        date: String = "2026-03-05",
        title: String = id,
        startMinute: Int? = null,
        endMinute: Int? = null,
        estimatedMinutes: Int? = null,
    ) = ScheduleEntry(
        id = id,
        planningDate = date,
        title = title,
        startMinute = startMinute,
        endMinute = endMinute,
        estimatedMinutes = estimatedMinutes,
    )

    @Test
    fun entries_with_a_start_time_form_the_timeline_in_start_end_order() {
        val state = AttentionState(
            schedules = listOf(
                entry("late", startMinute = 14 * 60, endMinute = 15 * 60),
                entry("early", startMinute = 9 * 60, endMinute = 10 * 60),
                entry("middle", startMinute = 12 * 60, endMinute = 12 * 60 + 30),
            ),
        )

        val timeline = state.agendaTimeline(day)

        assertEquals(listOf("early", "middle", "late"), timeline.timed.map { it.id })
        assertTrue("没有未安排时间时该分组为空", timeline.unscheduled.isEmpty())
    }

    @Test
    fun entries_without_a_start_time_move_to_the_unscheduled_group() {
        // 没有具体时间的日程仍然是今天的安排，只是它没有落在时间轴上的时刻
        val state = AttentionState(
            schedules = listOf(
                entry("planned", estimatedMinutes = 30),
                entry("timed", startMinute = 9 * 60, endMinute = 10 * 60),
            ),
        )

        val timeline = state.agendaTimeline(day)

        assertEquals(listOf("timed"), timeline.timed.map { it.id })
        assertEquals(listOf("planned"), timeline.unscheduled.map { it.id })
    }

    @Test
    fun only_the_requested_planning_day_reaches_the_timeline() {
        val state = AttentionState(
            schedules = listOf(
                entry("today", startMinute = 9 * 60, endMinute = 10 * 60),
                entry("tomorrow", date = "2026-03-06", startMinute = 9 * 60, endMinute = 10 * 60),
            ),
        )

        assertEquals(listOf("today"), state.agendaTimeline(day).timed.map { it.id })
    }

    @Test
    fun the_entry_being_timed_is_pinned_to_the_top_of_the_timeline() {
        // 正在计时的那条固定在最前，其余仍按开始/结束时间排列
        val state = AttentionState(
            schedules = listOf(
                entry("early", startMinute = 8 * 60, endMinute = 9 * 60),
                entry("timing", startMinute = 11 * 60, endMinute = 12 * 60),
                entry("late", startMinute = 15 * 60, endMinute = 16 * 60),
            ),
            activeTimer = ActiveTimer(
                targetId = null,
                startedAtEpochMillis = 0L,
                lastPlanningDate = "2026-03-05",
                scheduleEntryId = "timing",
            ),
        )

        val timeline = state.agendaTimeline(day)

        assertEquals(listOf("timing", "early", "late"), timeline.timed.map { it.id })
        assertEquals("timing", state.timingScheduleEntryId())
    }

    @Test
    fun a_running_entry_without_a_start_time_is_still_pinned_to_the_top_of_the_timeline() {
        // 「未安排时间」里的日程也能起计时。它一旦正在计时就要出现在时间轴第一位，
        // 而不是留在未安排时间区域里假装那里没有正在发生的事。
        val state = AttentionState(
            schedules = listOf(
                entry("early", startMinute = 8 * 60, endMinute = 9 * 60),
                entry("open-ended", estimatedMinutes = 30),
            ),
            activeTimer = ActiveTimer(
                targetId = null,
                startedAtEpochMillis = 0L,
                lastPlanningDate = "2026-03-05",
                scheduleEntryId = "open-ended",
            ),
        )

        val timeline = state.agendaTimeline(day)

        assertEquals(listOf("open-ended", "early"), timeline.timed.map { it.id })
        assertTrue("正在计时的条目不再留在未安排时间区域", timeline.unscheduled.isEmpty())
    }

    @Test
    fun a_free_running_timer_names_no_schedule_entry() {        // 自由计时没有日程来源，因此没有条目可以置顶
        val state = AttentionState(activeTimer = ActiveTimer(targetId = null, startedAtEpochMillis = 0L, lastPlanningDate = "2026-03-05"))

        assertEquals(null, state.timingScheduleEntryId())
        assertEquals(null, AttentionState().timingScheduleEntryId())
    }

    @Test
    fun every_planning_day_gets_its_own_timeline_in_date_order() {
        // 所有日期视图按规划日分组，每天内部仍然把正在计时的条目置顶
        val state = AttentionState(
            schedules = listOf(
                entry("day2", date = "2026-03-06", startMinute = 9 * 60, endMinute = 10 * 60),
                entry("day1", startMinute = 9 * 60, endMinute = 10 * 60),
                entry("day1-later", startMinute = 16 * 60, endMinute = 17 * 60),
            ),
            activeTimer = ActiveTimer(
                targetId = null,
                startedAtEpochMillis = 0L,
                lastPlanningDate = "2026-03-05",
                scheduleEntryId = "day1-later",
            ),
        )

        val timelines = state.agendaDayTimelines()

        assertEquals(listOf("2026-03-05", "2026-03-06"), timelines.map { it.planningDate })
        assertEquals(listOf("day1-later", "day1"), timelines[0].timeline.timed.map { it.id })
        assertEquals(listOf("day2"), timelines[1].timeline.timed.map { it.id })
    }

    @Test
    fun an_entry_whose_planned_minutes_exceed_the_daily_capacity_is_flagged() {
        // 容量只是可解释的提示：它被判定为超载，但不会因此拦住保存
        val state = AttentionState(
            schedules = listOf(entry("huge", estimatedMinutes = 200), entry("fits", estimatedMinutes = 60)),
            settings = StoredSettings(dailyCapacityMinutes = 120),
        )

        assertTrue(state.exceedsDailyCapacity(state.schedules.first { it.id == "huge" }))
        assertFalse(state.exceedsDailyCapacity(state.schedules.first { it.id == "fits" }))
    }

    @Test
    fun a_time_range_without_an_estimate_also_counts_towards_the_capacity_check() {
        // 计划时长与每日容量是同一个口径：完整时间段回退成计划时长
        val state = AttentionState(
            schedules = listOf(entry("long-range", startMinute = 8 * 60, endMinute = 11 * 60)),
            settings = StoredSettings(dailyCapacityMinutes = 120),
        )

        assertTrue(state.exceedsDailyCapacity(state.schedules.single()))
    }

    @Test
    fun without_a_daily_capacity_no_entry_is_flagged_as_overloaded() {
        // 未设置每日容量时没有超载可言，页面也不显示超载提示
        val state = AttentionState(schedules = listOf(entry("huge", estimatedMinutes = 500)))

        assertFalse(state.exceedsDailyCapacity(state.schedules.single()))
    }

    @Test
    fun an_entry_with_no_planned_minutes_never_exceeds_the_capacity() {
        val state = AttentionState(
            schedules = listOf(entry("open-ended")),
            settings = StoredSettings(dailyCapacityMinutes = 30),
        )

        assertFalse(state.exceedsDailyCapacity(state.schedules.single()))
    }
}
