package com.attention.app.data.room

import com.attention.app.data.room.mapper.toDomain
import com.attention.app.data.room.mapper.toEntity
import com.attention.domain.ActiveTimer
import com.attention.domain.FutureGoalRule
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.Migration
import com.attention.domain.Milestone
import com.attention.domain.PeriodSnapshot
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.domain.Target
import com.attention.domain.TargetMove
import com.attention.domain.TimeEntry
import com.attention.domain.TimeEntrySource
import com.attention.domain.TimerSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class BusinessMappersTest {
    @Test
    fun every_business_entity_round_trips_without_field_loss() {
        val target = Target("target", null, "学习", "note", "book", "#123456", archived = true, sortOrder = 4, expanded = false)
        val stage = GoalStage("stage", target.id, GoalCadence.WEEKLY, 120, "2026-10-01", "2026-10-07", completed = true)
        val timeEntry = TimeEntry("entry", "2026-10-02", 45, target.id, TimeEntrySource.TIMER, 123L, "timer")
        val schedule = ScheduleEntry("schedule", "2026-10-02", "阅读", 60, 90, 30, "note", target.id, completed = true, 456L, "rule")
        val recurrence = RecurrenceRule("rule", "阅读", "2026-10-01", ScheduleFrequency.WEEKLY, listOf(1, 3, 5), 2, "2026-12-31", active = false, 30, target.id, "note", 75)
        val migration = Migration("migration", target.id, stage.id, 20, "2026-10-08", "2026-10-09", cancelled = true)
        val futureRule = FutureGoalRule("future", target.id, stage.id, GoalCadence.MONTHLY, 300, "2026-11-01", "2026-11-30")
        val snapshot = PeriodSnapshot("snapshot", target.id, GoalCadence.WEEKLY, "2026-10-01", "2026-10-07", 120, 90, 30, 0, completed = false)
        val move = TargetMove("move", target.id, null, "2026-11-01")
        val milestone = Milestone("milestone", "goal_period", "target:2026-10-01", 20, 789L)
        val timer = ActiveTimer(target.id, 100L, 200L, paused = true, "2026-10-02", listOf(TimerSegment(100L, 200L)))

        assertEquals(target, target.toEntity().toDomain())
        assertEquals(stage, stage.toEntity().toDomain())
        assertEquals(timeEntry, timeEntry.toEntity().toDomain())
        assertEquals(schedule, schedule.toEntity().toDomain())
        assertEquals(recurrence, recurrence.toEntity().toDomain())
        assertEquals(migration, migration.toEntity().toDomain())
        assertEquals(futureRule, futureRule.toEntity().toDomain())
        assertEquals(snapshot, snapshot.toEntity().toDomain())
        assertEquals(move, move.toEntity().toDomain())
        assertEquals(milestone, milestone.toEntity().toDomain())
        assertEquals(timer, timer.toEntity().toDomain())
    }

    @Test
    fun target_scoped_future_rule_without_stage_keeps_blank_stage_id() {
        val rule = FutureGoalRule("future", "target", "", GoalCadence.DAILY, 30, "2026-11-01")

        assertEquals(rule, rule.toEntity().toDomain())
    }

    @Test
    fun one_time_goal_mapper_preserves_start_deadline_and_minutes() {
        val stage = GoalStage("one-time", "target", GoalCadence.ONE_TIME, 240, "2026-10-10", "2026-10-12")

        assertEquals(stage, stage.toEntity().toDomain())
    }
}
