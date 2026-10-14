package com.attention.domain

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleConfirmationCommandTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-14T12:00:00Z")

    @Test
    fun confirmation_command_records_estimate_without_occurrence_time_and_awards_for_schedule_date() {
        val target = Target(id = "target", title = "阅读")
        val schedule = ScheduleEntry(
            id = "schedule",
            planningDate = "2026-10-08",
            title = "章节",
            estimatedMinutes = 30,
            targetId = target.id,
        )
        val state = AttentionState(
            targets = listOf(target),
            schedules = listOf(schedule),
            goalStages = listOf(GoalStage("stage", target.id, GoalCadence.DAILY, 30, "2026-10-08")),
        )

        val result = AttentionCommand.ConfirmSchedule(schedule.id).execute(state, now, zone)

        val entry = result.generatedTimeEntries.single()
        assertEquals(TimeEntrySource.SCHEDULE_CONFIRMED, entry.source)
        assertEquals(30, entry.durationMinutes)
        assertEquals(schedule.planningDate, entry.planningDate)
        assertEquals(schedule.id, entry.scheduleEntryId)
        assertNull(entry.occurredAtEpochMillis)
        assertEquals(now.toEpochMilli(), result.newMilestones.single().achievedAtEpochMillis)
        assertEquals("goal_period:stage:2026-10-08", result.newMilestones.single().instanceKey)
        assertTrue(result.state.schedules.single().completed)
    }

    @Test
    fun confirmation_command_uses_explicit_duration_before_estimate_and_time_range() {
        val target = Target(id = "target", title = "阅读")
        val schedule = ScheduleEntry(
            id = "schedule",
            planningDate = "2026-10-08",
            title = "章节",
            startMinute = 60,
            endMinute = 120,
            estimatedMinutes = 30,
            targetId = target.id,
        )
        val state = AttentionState(targets = listOf(target), schedules = listOf(schedule))

        val explicit = AttentionCommand.ConfirmSchedule(schedule.id, durationMinutes = 15)
            .execute(state, now, zone)
        assertEquals(15, explicit.generatedTimeEntries.single().durationMinutes)

        val rangeOnly = AttentionCommand.ConfirmSchedule(schedule.copy(estimatedMinutes = null).id)
            .execute(state.copy(schedules = listOf(schedule.copy(estimatedMinutes = null))), now, zone)
        assertEquals(60, rangeOnly.generatedTimeEntries.single().durationMinutes)
    }

    @Test
    fun confirmation_command_rejects_schedule_without_any_available_duration() {
        val schedule = ScheduleEntry(id = "schedule", planningDate = "2026-10-08", title = "无时长")
        val state = AttentionState(schedules = listOf(schedule))

        val error = runCatching {
            AttentionCommand.ConfirmSchedule(schedule.id).execute(state, now, zone)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("日程条目缺少预计时长，无法确认实际投入", error?.message)
    }
}
