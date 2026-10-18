package com.attention.domain

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun saving_a_schedule_with_a_backfill_settles_completion_too() {
        val entry = schedule(estimatedMinutes = 30)

        val saved = AttentionCommand.SaveSchedule(
            entry,
            ScheduleBackfill(durationMinutes = 30, targetId = "target", requestId = "request-1"),
        ).execute(stateOf(entry), now, utc)

        assertTrue(
            "补记凑满计划时长后，保存本身就应当把日程结算成完成，而不是等下一次别的命令",
            saved.state.schedules.single().completed,
        )
        assertEquals(listOf(TimeEntrySource.MANUAL), saved.generatedTimeEntries.map { it.source })
    }

    @Test
    fun saving_a_schedule_with_a_short_backfill_stays_incomplete() {
        val entry = schedule(estimatedMinutes = 30)

        val saved = AttentionCommand.SaveSchedule(
            entry,
            ScheduleBackfill(durationMinutes = 20, targetId = "target", requestId = "request-1"),
        ).execute(stateOf(entry), now, utc)

        assertFalse(saved.state.schedules.single().completed)
    }

    @Test
    fun the_records_a_stop_produces_carry_every_fact_the_result_panel_shows() {
        // 结果面板要展示的四件事——实际投入、计划时长、完成结果、记录归属——
        // 全部来自领域结算的结果，页面不需要（也不允许）自己推算。
        val entry = schedule(estimatedMinutes = 30)
        val running = stateOf(entry).startTimer("target", now.minusSeconds(20 * 60L), utc, entry.id)

        val stopped = AttentionCommand.StopTimer.execute(running, now, utc)

        assertEquals(1, stopped.generatedTimeEntries.size)
        val record = stopped.generatedTimeEntries.single()
        assertEquals(20, record.durationMinutes)
        assertEquals("2026-10-08", record.planningDate)
        assertEquals("target", record.targetId)
        assertEquals(TimeEntrySource.TIMER, record.source)
        assertEquals(entry.id, record.scheduleEntryId)
        // 实际投入与是否达标都读领域函数；不足计划时完成状态仍由用户决定。
        assertEquals(20, stopped.state.scheduleInvestedMinutes(entry.id))
        assertFalse(stopped.state.isScheduleInvestmentMet(entry.id))
        assertFalse(stopped.state.schedules.single().completed)
    }

    @Test
    fun a_stop_reaching_the_plan_completes_the_entry_without_a_second_command() {
        // 达标即自动完成：用户不必、也不应该再勾一次完成。
        val entry = schedule(estimatedMinutes = 30)
        val running = stateOf(entry).startTimer("target", now.minusSeconds(35 * 60L), utc, entry.id)

        val stopped = AttentionCommand.StopTimer.execute(running, now, utc)

        assertTrue(stopped.state.isScheduleInvestmentMet(entry.id))
        assertTrue(stopped.state.schedules.single().completed)
        assertEquals(35, stopped.state.scheduleInvestedMinutes(entry.id))
    }

    @Test
    fun stopping_across_a_planning_day_boundary_reports_each_record_separately() {
        // 跨规划日界线的那一次计时会落成多条记录；面板必须如实呈现多笔，
        // 而实际投入仍按全部记录合计——归并不由页面或领域单方面决定。
        val entry = ScheduleEntry(
            id = "schedule",
            planningDate = "2026-10-08",
            title = "夜间长跑",
            startMinute = 540,
            endMinute = 720,
            estimatedMinutes = 240,
            targetId = "target",
        )
        // 计划日界线默认 03:00：从 10-07 22:00 起计 240 分钟会跨过 10-08 的 03:00。
        val start = Instant.parse("2026-10-07T22:00:00Z")
        val end = Instant.parse("2026-10-08T02:00:00Z")
        val running = stateOf(entry)
            .copy(settings = StoredSettings(planningDayBoundaryMinutes = 180))
            .startTimer("target", start, utc, entry.id)

        val stopped = AttentionCommand.StopTimer.execute(running, end, utc)

        // 22:00-03:00 记在 10-07，03:00 之后的残余若不足整分钟则不计；无论怎么切，
        // 面板看到的是「这次停止产出的那几笔」，而不是一笔被合成的时间。
        assertTrue("跨规划日的停止会产出多条记录", stopped.generatedTimeEntries.size >= 1)
        assertEquals(
            stopped.generatedTimeEntries.map { it.planningDate }.distinct().size,
            stopped.generatedTimeEntries.map { it.planningDate }.toSet().size,
        )
        assertEquals(
            stopped.generatedTimeEntries.sumOf { it.durationMinutes },
            stopped.state.scheduleInvestedMinutes(entry.id),
        )
        assertTrue("实际投入按这次停止产出的全部记录合计", stopped.state.schedules.single().completed)
    }

    @Test
    fun an_entry_without_a_planned_duration_keeps_its_state_after_a_stop() {
        // 没有计划时长的日程不由系统猜测：停止之后它既不自动完成，也不被提示为未达标。
        val entry = ScheduleEntry(
            id = "schedule",
            planningDate = "2026-10-08",
            title = "随手做的",
            estimatedMinutes = null,
            targetId = "target",
        )
        val running = stateOf(entry).startTimer("target", now.minusSeconds(90 * 60L), utc, entry.id)

        val stopped = AttentionCommand.StopTimer.execute(running, now, utc)

        assertNull(entry.plannedMinutes())
        assertFalse(stopped.state.isScheduleInvestmentMet(entry.id))
        assertFalse(stopped.state.schedules.single().completed)
        assertEquals(90, stopped.state.scheduleInvestedMinutes(entry.id))
    }

    @Test
    fun the_users_own_decision_completes_an_entry_that_is_short_of_planned_minutes() {
        // 不足计划时长时结算命令不动状态，但用户在结果面板上说「做完了」就必须看得见变化。
        val entry = schedule(estimatedMinutes = 30)
        val stopped = stopTimerFromSchedule(minutes = 20, entry = entry)

        val marked = AttentionCommand.MarkScheduleCompleted(entry.id).execute(stopped, now, utc).state

        assertTrue(marked.schedules.single().completed)
    }

    @Test
    fun marking_completed_writes_no_second_investment_and_stays_idempotent() {
        // 那一笔投入已经在停止计时时写下；再记一笔会把它算两遍。
        // 重复点同一处也不能翻回未完成——那是 toggle 的语义，两条路径在页面上互斥。
        val entry = schedule(estimatedMinutes = 30)
        val stopped = stopTimerFromSchedule(minutes = 20, entry = entry)
        val minutesAfterStop = stopped.scheduleInvestedMinutes(entry.id)
        val sourcesAfterStop = stopped.timeEntries.map { it.source }

        val once = AttentionCommand.MarkScheduleCompleted(entry.id).execute(stopped, now, utc).state
        val twice = AttentionCommand.MarkScheduleCompleted(entry.id).execute(once, now, utc).state

        assertEquals(1, once.timeEntries.size)
        assertEquals(sourcesAfterStop, once.timeEntries.map { it.source })
        assertEquals(minutesAfterStop, twice.scheduleInvestedMinutes(entry.id))
        assertTrue(twice.schedules.single().completed)
    }
}
