package com.attention.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
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

        val result = AttentionCommand.AddManualTimeEntry(
            planningDate = date.toString(),
            durationMinutes = 30,
            targetId = target.id,
            note = "手动补记",
        ).execute(state, now, utc)

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
    fun stop_timer_command_returns_timer_entries_and_milestones_for_each_affected_planning_date() {
        val base = AttentionState(
            targets = listOf(Target("timer-target", title = "计时目标")),
            goalStages = listOf(
                GoalStage("timer-stage", "timer-target", GoalCadence.DAILY, 10, date.minusDays(1).toString()),
            ),
        ).startTimer("timer-target", Instant.parse("2026-10-08T02:50:00Z"), utc)
        val now = Instant.parse("2026-10-08T03:20:00Z")

        val result = AttentionCommand.StopTimer.execute(base, now, utc)

        assertEquals(listOf("2026-10-07", "2026-10-08"), result.timeEntries.map { it.planningDate })
        assertEquals(listOf(10, 20), result.timeEntries.map { it.durationMinutes })
        assertTrue(result.timeEntries.all { it.source == TimeEntrySource.TIMER })
        assertEquals(
            listOf(
                "goal_period:timer-stage:2026-10-07",
                "goal_period:timer-stage:2026-10-08",
            ),
            result.milestones.map { it.instanceKey },
        )
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
        assertEquals(
            listOf(
                "goal_period:switch-stage:2026-10-07",
                "goal_period:switch-stage:2026-10-08",
            ),
            result.milestones.map { it.instanceKey },
        )
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
        val partial = AttentionState(targets = listOf(target)).startTimer(
            target.id,
            Instant.parse("2026-10-08T00:00:30Z"),
            utc,
        )
        val partialResult = AttentionCommand.StopTimer.execute(partial, now, utc)
        assertTrue(partialResult.timeEntries.isEmpty())
        assertTrue(partialResult.milestones.isEmpty())
        assertEquals(null, partialResult.state.activeTimer)
        assertTrue(partialResult.state.timeEntries.isEmpty())
    }
}
