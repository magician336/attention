package com.attention.domain

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 完成状态由「计划时长」与「这条日程关联的实际投入」对比决定。
 *
 * 四条路径分别钉住：达到计划时长、不足、超过、没有计划时长。
 * 实际投入只统计 `scheduleEntryId` 指向这条日程的时间记录，
 * 完成本身不再新增任何时间记录，投入来源保持计时结算与日程确认各自的事实。
 */
class ScheduleCompletionByInvestmentCommandTest {
    private val utc = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-10-08T12:00:00Z")

    private fun schedule(estimatedMinutes: Int? = null) = ScheduleEntry(
        id = "schedule",
        planningDate = "2026-10-08",
        title = "章节",
        startMinute = 540,
        endMinute = 720,
        estimatedMinutes = estimatedMinutes,
        targetId = "target",
    )

    private fun stateOf(entry: ScheduleEntry, extraEntries: List<TimeEntry> = emptyList()) = AttentionState(
        targets = listOf(Target(id = "target", title = "阅读")),
        schedules = listOf(entry),
        timeEntries = extraEntries,
    )

    private fun stopTimerFromSchedule(minutes: Long, entry: ScheduleEntry): AttentionState {
        val running = stateOf(entry).startTimer("target", now.minusSeconds(minutes * 60L), utc, entry.id)
        return AttentionCommand.StopTimer.execute(running, now, utc).state
    }

    @Test
    fun schedule_completes_when_linked_investment_reaches_planned_minutes() {
        val entry = schedule(estimatedMinutes = 30)

        val stopped = stopTimerFromSchedule(minutes = 30, entry = entry)

        assertTrue(stopped.schedules.single().completed)
        assertEquals(listOf(TimeEntrySource.TIMER), stopped.timeEntries.map { it.source })
        assertEquals(entry.id, stopped.timeEntries.single().scheduleEntryId)
    }

    @Test
    fun schedule_stays_incomplete_when_linked_investment_is_short_of_planned_minutes() {
        val entry = schedule(estimatedMinutes = 30)

        val stopped = stopTimerFromSchedule(minutes = 20, entry = entry)

        assertFalse(stopped.schedules.single().completed)
    }

    @Test
    fun schedule_completes_when_linked_investment_exceeds_planned_minutes() {
        val entry = schedule(estimatedMinutes = 30)

        val stopped = stopTimerFromSchedule(minutes = 45, entry = entry)

        assertTrue(stopped.schedules.single().completed)
        assertEquals(45, stopped.timeEntries.single().durationMinutes)
    }

    @Test
    fun schedule_without_planned_minutes_keeps_its_completion_state() {
        val entry = schedule(estimatedMinutes = null).copy(startMinute = null, endMinute = null)

        val stopped = stopTimerFromSchedule(minutes = 90, entry = entry)
        val settled = AttentionCommand.SettleScheduleCompletion(entry.id).execute(stopped, now, utc).state

        assertFalse(settled.schedules.single().completed)
    }

    @Test
    fun planned_minutes_falls_back_to_the_time_range_when_no_estimate_exists() {
        val entry = schedule(estimatedMinutes = null)

        val settled = AttentionCommand.SettleScheduleCompletion(entry.id)
            .execute(stateOf(entry), now, utc)
            .state

        assertFalse(settled.schedules.single().completed)
        assertEquals(180, entry.plannedMinutes())

        val stopped = stopTimerFromSchedule(minutes = 180, entry = entry)
        assertTrue(stopped.schedules.single().completed)
    }

    @Test
    fun investment_on_other_schedules_never_counts_towards_this_schedule() {
        val entry = schedule(estimatedMinutes = 30)
        val unrelated = TimeEntry(
            planningDate = "2026-10-08",
            durationMinutes = 120,
            targetId = "target",
            source = TimeEntrySource.MANUAL,
        )
        val otherSchedule = TimeEntry(
            planningDate = "2026-10-08",
            durationMinutes = 60,
            targetId = "target",
            source = TimeEntrySource.TIMER,
            scheduleEntryId = "other-schedule",
        )

        val settled = AttentionCommand.SettleScheduleCompletion(entry.id)
            .execute(stateOf(entry, listOf(unrelated, otherSchedule)), now, utc)
            .state

        assertFalse(settled.schedules.single().completed)
    }

    @Test
    fun repeated_settlement_completes_once_and_settles_experience_and_milestones_once() {
        val entry = schedule(estimatedMinutes = 30)
        val base = AttentionState(
            targets = listOf(Target(id = "target", title = "阅读")),
            schedules = listOf(entry),
            timeEntries = listOf(
                TimeEntry(
                    planningDate = "2026-10-08",
                    durationMinutes = 30,
                    targetId = "target",
                    source = TimeEntrySource.TIMER,
                    scheduleEntryId = entry.id,
                ),
            ),
            goalStages = listOf(GoalStage("stage", "target", GoalCadence.DAILY, 30, "2026-10-08")),
        )

        val first = AttentionCommand.SettleScheduleCompletion(entry.id).execute(base, now, utc)
        val second = AttentionCommand.SettleScheduleCompletion(entry.id).execute(first.state, now, utc)

        assertTrue(first.state.schedules.single().completed)
        assertEquals("goal_period:stage:2026-10-08", first.newMilestones.single().instanceKey)
        assertEquals(first.state, second.state)
        // 30 分钟基础经验，加上这次每日目标里程碑的 500 奖励，只结算一次。
        assertEquals(530L, second.state.experience)
        assertEquals(first.state.experience, second.state.experience)
        assertTrue(second.newMilestones.isEmpty())
        assertEquals(1, second.state.milestones.size)
        assertEquals(base.timeEntries.size, second.state.timeEntries.size)
    }

    @Test
    fun completing_a_short_schedule_stays_the_user_decision_and_keeps_the_confirmed_source() {
        val entry = schedule(estimatedMinutes = 30)
        val base = AttentionState(targets = listOf(Target(id = "target", title = "阅读")), schedules = listOf(entry))

        val stopped = stopTimerFromSchedule(minutes = 20, entry = entry)
        assertFalse(stopped.schedules.single().completed)

        val confirmed = AttentionCommand.ConfirmSchedule(entry.id, durationMinutes = 20).execute(stopped, now, utc)
        assertTrue(confirmed.state.schedules.single().completed)
        assertEquals(
            listOf(TimeEntrySource.TIMER, TimeEntrySource.SCHEDULE_CONFIRMED),
            confirmed.state.timeEntries.map { it.source },
        )
        assertEquals(listOf(TimeEntrySource.SCHEDULE_CONFIRMED), confirmed.generatedTimeEntries.map { it.source })
        assertEquals(entry.id, confirmed.generatedTimeEntries.single().scheduleEntryId)
    }
}
