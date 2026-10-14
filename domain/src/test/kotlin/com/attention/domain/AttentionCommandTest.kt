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
}
