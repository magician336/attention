package com.attention.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
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
    fun edit_time_command_preserves_record_facts_and_evaluates_its_planning_date() {
        val target = Target("edit-target", title = "编辑目标")
        val entry = TimeEntry(
            id = "historical-entry",
            planningDate = date.toString(),
            durationMinutes = 10,
            targetId = null,
            source = TimeEntrySource.TIMER,
            occurredAtEpochMillis = 1234L,
            note = "旧备注",
            scheduleEntryId = "schedule-1",
        )
        val state = AttentionState(
            targets = listOf(target),
            goalStages = listOf(GoalStage("stage", target.id, GoalCadence.ONE_TIME, 20, date.toString())),
            timeEntries = listOf(entry),
        )
        val now = Instant.parse("2026-10-14T12:00:00Z")

        val result = AttentionCommand.EditTimeEntry(
            entryId = entry.id,
            durationMinutes = 20,
            targetId = target.id,
            note = "新备注",
        ).execute(state, now, utc)

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

        val result = AttentionCommand.AssignUnowned(setOf(first.id, second.id), target.id)
            .execute(state, now, utc)

        assertEquals(setOf(target.id), result.state.timeEntries.mapNotNull { it.targetId }.toSet())
        assertEquals(setOf(first.id, second.id), result.timeEntries.map { it.id }.toSet())
        assertEquals(
            listOf(
                "one_time_goal:first-stage:2026-10-08",
                "one_time_goal:second-stage:2026-10-09",
            ),
            result.milestones.map { it.instanceKey },
        )
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
        val milestone = Milestone(
            id = "kept-milestone",
            kind = "one_time_goal",
            instanceKey = "one_time_goal:stage:${date}",
            reward = 10_000,
            achievedAtEpochMillis = 1L,
        )
        val state = AttentionState(
            targets = listOf(target),
            timeEntries = listOf(entry),
            milestones = listOf(milestone),
            experience = 10_030,
        )
        val now = Instant.parse("2026-10-14T12:00:00Z")

        val result = AttentionCommand.DeleteTimeEntry(entry.id).execute(state, now, utc)

        assertTrue(result.state.timeEntries.isEmpty())
        assertEquals(listOf(milestone), result.state.milestones)
        assertEquals(10_000L, result.state.experience)
        assertTrue(result.timeEntries.isEmpty())
        assertTrue(result.milestones.isEmpty())
    }

    @Test
    fun restore_time_command_reuses_original_record_and_does_not_duplicate_milestones() {
        val target = Target("restore-target", title = "恢复目标", archived = true)
        val entry = TimeEntry(
            id = "restored-entry",
            planningDate = date.toString(),
            durationMinutes = 30,
            targetId = target.id,
            source = TimeEntrySource.TIMER,
            occurredAtEpochMillis = 5678L,
            note = "原始计时",
            scheduleEntryId = "schedule-1",
        )
        val milestone = Milestone(
            id = "restored-milestone",
            kind = "one_time_goal",
            instanceKey = "one_time_goal:stage:${date}",
            reward = 10_000,
            achievedAtEpochMillis = 1L,
        )
        val state = AttentionState(
            targets = listOf(target),
            milestones = listOf(milestone),
            experience = milestone.reward,
        )
        val command = AttentionCommand.RestoreTimeEntry(entry)
        val now = Instant.parse("2026-10-14T12:00:00Z")

        val first = command.execute(state, now, utc)
        val restored = first.state.timeEntries.single()
        assertEquals(entry, restored)
        assertEquals(listOf(entry), first.timeEntries)
        assertTrue(first.milestones.isEmpty())
        assertEquals(10_030L, first.state.experience)

        val retry = command.execute(first.state, now, utc)
        assertEquals(listOf(entry), retry.state.timeEntries)
        assertTrue(retry.timeEntries.isEmpty())
        assertTrue(retry.milestones.isEmpty())
        assertEquals(listOf(milestone), retry.state.milestones)
    }
}
