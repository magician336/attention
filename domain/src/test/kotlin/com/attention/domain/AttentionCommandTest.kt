package com.attention.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionCommandTest {
    private val date = LocalDate.of(2026, 10, 8)
    private val utc = ZoneOffset.UTC

    @Test
    fun manual_time_command_returns_entry_state_and_milestones_with_explicit_time() {
        val base = AttentionState().addTarget("阶段目标")
        val target = base.targets.single()
        val state = base.addGoalStage(target.id, GoalCadence.ONE_TIME, 30, date.toString())
        val now = Instant.parse("2026-10-08T12:34:56Z")
        val result = AttentionCommand.AddManualTimeEntry(date.toString(), 30, target.id, "手动补记")
            .execute(state, now, utc)
        assertEquals(1, result.timeEntries.size)
        assertEquals(TimeEntrySource.MANUAL, result.timeEntries.single().source)
        assertEquals("手动补记", result.timeEntries.single().note)
        assertEquals(1, result.milestones.size)
        assertEquals(now.toEpochMilli(), result.milestones.single().achievedAtEpochMillis)
        assertEquals(10_030L, result.state.experience)
        assertEquals(result.timeEntries, result.state.timeEntries.takeLast(1))
    }

    @Test
    fun manual_time_command_orders_all_affected_milestones_and_is_idempotent_by_instance_key() {
        val base = AttentionState().addTarget("连续累计目标")
        val target = base.targets.single()
        val stateWithStage = base.addGoalStage(target.id, GoalCadence.ONE_TIME, 1, date.toString())
        val populated = (0..5).fold(stateWithStage) { state, offset ->
            state.addTimeEntry(date.plusDays(offset.toLong()).toString(), 1, target.id)
        }.addTimeEntry(date.minusDays(6).toString(), 5_993, target.id)
        val command = AttentionCommand.AddManualTimeEntry(date.plusDays(6).toString(), 1, target.id)
        val now = Instant.parse("2026-10-14T12:00:00Z")
        val first = command.execute(populated, now, utc)
        val keys = first.milestones.map { it.kind to it.instanceKey }
        assertEquals(keys.sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second }), keys)
        assertTrue(keys.any { it.first == "one_time_goal" })
        assertTrue(keys.any { it.first == "streak" })
        assertTrue(keys.any { it.first == "cumulative" })
        val retry = command.execute(first.state, now, utc)
        assertTrue(retry.milestones.isEmpty())
        assertEquals(3, retry.state.milestones.distinctBy { it.instanceKey }.size)
    }

    @Test
    fun all_investment_entry_commands_share_experience_and_milestone_policy_but_keep_time_semantics() {
        val target = Target("shared-policy-target", title = "统一策略")
        val stage = GoalStage("shared-policy-stage", target.id, GoalCadence.ONE_TIME, 30, date.toString())
        val now = Instant.parse("2026-10-08T12:34:56Z")

        val manual = AttentionCommand.AddManualTimeEntry(date.toString(), 30, target.id)
            .execute(AttentionState(targets = listOf(target), goalStages = listOf(stage)), now, utc)
        val schedule = ScheduleEntry("shared-policy-schedule", date.toString(), "日程确认", estimatedMinutes = 30, targetId = target.id)
        val confirmed = AttentionCommand.ConfirmSchedule(schedule.id)
            .execute(AttentionState(targets = listOf(target), goalStages = listOf(stage), schedules = listOf(schedule)), now, utc)
        val timer = AttentionState(targets = listOf(target), goalStages = listOf(stage))
            .startTimer(target.id, now.minusSeconds(30 * 60L), utc)
            .let { AttentionCommand.StopTimer.execute(it, now, utc) }

        val results = listOf(manual, confirmed, timer)
        assertEquals(listOf(10_030L, 10_030L, 10_030L), results.map { it.state.experience })
        val expectedInstanceKey = "one_time_goal:shared-policy-stage:2026-10-08"
        assertEquals(listOf(expectedInstanceKey), results.map { it.milestones.single().instanceKey }.distinct())
        assertTrue(results.all { it.milestones.single().achievedAtEpochMillis == now.toEpochMilli() })

        assertEquals(TimeEntrySource.MANUAL, manual.generatedTimeEntries.single().source)
        assertNull(manual.generatedTimeEntries.single().occurredAtEpochMillis)
        assertEquals(TimeEntrySource.SCHEDULE_CONFIRMED, confirmed.generatedTimeEntries.single().source)
        assertNull(confirmed.generatedTimeEntries.single().occurredAtEpochMillis)
        assertEquals(schedule.id, confirmed.generatedTimeEntries.single().scheduleEntryId)
        assertEquals(TimeEntrySource.TIMER, timer.generatedTimeEntries.single().source)
        assertEquals(now.minusSeconds(30 * 60L).toEpochMilli(), timer.generatedTimeEntries.single().occurredAtEpochMillis)
    }

    @Test
    fun edit_time_command_preserves_record_facts_and_evaluates_its_planning_date() {
        val target = Target("edit-target", title = "编辑目标")
        val entry = TimeEntry("historical-entry", date.toString(), 10, null, TimeEntrySource.TIMER, 1234L, "旧备注", "schedule-1")
        val state = AttentionState(
            targets = listOf(target),
            goalStages = listOf(GoalStage("stage", target.id, GoalCadence.ONE_TIME, 20, date.toString())),
            timeEntries = listOf(entry),
        )
        val now = Instant.parse("2026-10-14T12:00:00Z")
        val result = AttentionCommand.EditTimeEntry(entry.id, 20, target.id, "新备注").execute(state, now, utc)
        val edited = result.state.timeEntries.single()
        assertEquals(entry.id, edited.id)
        assertEquals(entry.planningDate, edited.planningDate)
        assertEquals(entry.source, edited.source)
        assertEquals(entry.occurredAtEpochMillis, edited.occurredAtEpochMillis)
        assertEquals(entry.scheduleEntryId, edited.scheduleEntryId)
        assertEquals(20, edited.durationMinutes)
        assertEquals(target.id, edited.targetId)
        assertEquals("新备注", edited.note)
        assertEquals(listOf("one_time_goal:stage:2026-10-08"), result.milestones.map { it.instanceKey })
        assertEquals(now.toEpochMilli(), result.milestones.single().achievedAtEpochMillis)
    }

    @Test
    fun assign_unowned_command_updates_all_rows_atomically_and_evaluates_each_row_date() {
        val firstDate = date
        val secondDate = date.plusDays(1)
        val target = Target("assign-target", title = "归入目标")
        val first = TimeEntry("unowned-first", firstDate.toString(), 10)
        val second = TimeEntry("unowned-second", secondDate.toString(), 10)
        val state = AttentionState(
            targets = listOf(target),
            goalStages = listOf(
                GoalStage("first-stage", target.id, GoalCadence.ONE_TIME, 10, firstDate.toString()),
                GoalStage("second-stage", target.id, GoalCadence.ONE_TIME, 10, secondDate.toString()),
            ),
            timeEntries = listOf(first, second),
        )
        val now = Instant.parse("2026-10-14T12:00:00Z")
        val result = AttentionCommand.AssignUnowned(setOf(first.id, second.id), target.id).execute(state, now, utc)
        assertEquals(setOf(target.id), result.state.timeEntries.mapNotNull { it.targetId }.toSet())
        assertEquals(setOf(first.id, second.id), result.timeEntries.map { it.id }.toSet())
        assertEquals(listOf("one_time_goal:first-stage:2026-10-08", "one_time_goal:second-stage:2026-10-09"), result.milestones.map { it.instanceKey })
        assertTrue(result.milestones.all { it.achievedAtEpochMillis == now.toEpochMilli() })
        val owned = state.copy(timeEntries = state.timeEntries + TimeEntry("owned", firstDate.toString(), 1, target.id))
        assertThrows(IllegalArgumentException::class.java) {
            AttentionCommand.AssignUnowned(setOf(first.id, "owned"), target.id).execute(owned, now, utc)
        }
        assertEquals(null, AttentionCommand.AssignUnowned(emptySet(), target.id).execute(state, now, utc).state.timeEntries.first().targetId)
    }

    @Test
    fun delete_time_command_recomputes_experience_without_retracting_awarded_milestones() {
        val target = Target("delete-target", title = "删除目标")
        val entry = TimeEntry("delete-entry", date.toString(), 30, target.id)
        val milestone = Milestone("kept-milestone", "one_time_goal", "one_time_goal:stage:${date}", 10_000, 1L)
        val state = AttentionState(targets = listOf(target), timeEntries = listOf(entry), milestones = listOf(milestone), experience = 10_030)
        val result = AttentionCommand.DeleteTimeEntry(entry.id).execute(state, Instant.parse("2026-10-14T12:00:00Z"), utc)
        assertTrue(result.state.timeEntries.isEmpty())
        assertEquals(listOf(milestone), result.state.milestones)
        assertEquals(10_000L, result.state.experience)
        assertTrue(result.timeEntries.isEmpty())
        assertTrue(result.milestones.isEmpty())
    }

    @Test
    fun restore_time_command_reuses_original_record_and_does_not_duplicate_milestones() {
        val target = Target("restore-target", title = "恢复目标", archived = true)
        val entry = TimeEntry("restored-entry", date.toString(), 30, target.id, TimeEntrySource.TIMER, 5678L, "原始计时", "schedule-1")
        val milestone = Milestone("restored-milestone", "one_time_goal", "one_time_goal:stage:${date}", 10_000, 1L)
        val state = AttentionState(targets = listOf(target), milestones = listOf(milestone), experience = milestone.reward)
        val command = AttentionCommand.RestoreTimeEntry(entry)
        val now = Instant.parse("2026-10-14T12:00:00Z")
        val first = command.execute(state, now, utc)
        assertEquals(entry, first.state.timeEntries.single())
        assertEquals(listOf(entry), first.timeEntries)
        assertTrue(first.milestones.isEmpty())
        assertEquals(10_030L, first.state.experience)
        val retry = command.execute(first.state, now, utc)
        assertEquals(listOf(entry), retry.state.timeEntries)
        assertTrue(retry.timeEntries.isEmpty())
        assertTrue(retry.milestones.isEmpty())
        assertEquals(listOf(milestone), retry.state.milestones)
    }

    @Test
    fun stop_timer_command_returns_timer_entries_and_milestones_for_each_affected_planning_date() {
        val base = AttentionState(
            targets = listOf(Target("timer-target", title = "计时目标")),
            goalStages = listOf(GoalStage("timer-stage", "timer-target", GoalCadence.DAILY, 10, date.minusDays(1).toString())),
        ).startTimer("timer-target", Instant.parse("2026-10-08T02:50:00Z"), utc)
        val now = Instant.parse("2026-10-08T03:20:00Z")
        val result = AttentionCommand.StopTimer.execute(base, now, utc)
        assertEquals(listOf("2026-10-07", "2026-10-08"), result.timeEntries.map { it.planningDate })
        assertEquals(listOf(10, 20), result.timeEntries.map { it.durationMinutes })
        assertTrue(result.timeEntries.all { it.source == TimeEntrySource.TIMER })
        assertEquals(listOf("goal_period:timer-stage:2026-10-07", "goal_period:timer-stage:2026-10-08"), result.milestones.map { it.instanceKey })
        assertTrue(result.milestones.all { it.achievedAtEpochMillis == now.toEpochMilli() })
        assertEquals(null, result.state.activeTimer)
        assertEquals(result.timeEntries, result.state.timeEntries)
    }

    @Test
    fun switch_timer_command_settles_previous_timer_and_starts_the_new_target() {
        val first = Target("first", title = "第一个")
        val second = Target("second", title = "第二个")
        val started = AttentionState(
            targets = listOf(first, second),
            goalStages = listOf(GoalStage("switch-stage", first.id, GoalCadence.DAILY, 10, date.minusDays(1).toString())),
        ).startTimer(first.id, Instant.parse("2026-10-08T02:50:00Z"), utc)
        val now = Instant.parse("2026-10-08T03:20:00Z")
        val result = AttentionCommand.SwitchTimer(second.id).execute(started, now, utc)
        assertEquals(listOf("2026-10-07", "2026-10-08"), result.timeEntries.map { it.planningDate })
        assertEquals(listOf(10, 20), result.timeEntries.map { it.durationMinutes })
        assertTrue(result.timeEntries.all { it.source == TimeEntrySource.TIMER })
        assertEquals(listOf("goal_period:switch-stage:2026-10-07", "goal_period:switch-stage:2026-10-08"), result.milestones.map { it.instanceKey })
        assertEquals(second.id, result.state.activeTimer?.targetId)
        assertEquals(now.toEpochMilli(), result.state.activeTimer?.startedAtEpochMillis)
        assertEquals(2, result.state.timeEntries.size)
    }

    @Test
    fun stop_timer_command_preserves_no_op_and_partial_minute_semantics() {
        val idle = AttentionState()
        val now = Instant.parse("2026-10-08T00:01:00Z")
        val idleResult = AttentionCommand.StopTimer.execute(idle, now, utc)
        assertEquals(idle, idleResult.state)
        assertTrue(idleResult.timeEntries.isEmpty())
        assertTrue(idleResult.milestones.isEmpty())
        val target = Target("partial", title = "不足一分钟")
        val partial = AttentionState(targets = listOf(target)).startTimer(target.id, Instant.parse("2026-10-08T00:00:30Z"), utc)
        val partialResult = AttentionCommand.StopTimer.execute(partial, now, utc)
        assertTrue(partialResult.timeEntries.isEmpty())
        assertTrue(partialResult.milestones.isEmpty())
        assertEquals(null, partialResult.state.activeTimer)
        assertTrue(partialResult.state.timeEntries.isEmpty())
    }
}
