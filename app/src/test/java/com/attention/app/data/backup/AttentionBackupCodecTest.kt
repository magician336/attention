package com.attention.app.data.backup

import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.LaunchDestination
import com.attention.domain.Migration
import com.attention.domain.Milestone
import com.attention.domain.PeriodSnapshot
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.TargetMove
import com.attention.domain.TimeEntry
import com.attention.domain.TimeEntrySource
import com.attention.domain.TimerSegment
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionBackupCodecTest {
    @Test
    fun versioned_backup_round_trip_preserves_every_domain_field() {
        val target = Target("target", title = "学习")
        val expected = AttentionState(
            settings = StoredSettings(90, 7, 420),
            targets = listOf(target),
            goalStages = listOf(GoalStage("stage", target.id, GoalCadence.WEEKLY, 120, "2026-10-01")),
            timeEntries = listOf(TimeEntry("entry", "2026-10-02", 45, target.id, TimeEntrySource.TIMER, 123L, "计时")),
            schedules = listOf(ScheduleEntry("schedule", "2026-10-02", "阅读", estimatedMinutes = 30, targetId = target.id)),
            recurrenceRules = listOf(RecurrenceRule("rule", "阅读", "2026-10-01", ScheduleFrequency.DAILY, targetId = target.id)),
            migrations = listOf(Migration("migration", target.id, "stage", 20, "2026-10-08")),
            futureGoalRules = listOf(com.attention.domain.FutureGoalRule("future", target.id, "stage", GoalCadence.MONTHLY, 300, "2026-11-01")),
            periodSnapshots = listOf(PeriodSnapshot("snapshot", target.id, GoalCadence.WEEKLY, "2026-10-01", "2026-10-07", 120, 90, 30, 0, false)),
            targetMoves = listOf(TargetMove("move", target.id, null, "2026-11-01")),
            milestones = listOf(Milestone("milestone", "goal_period", "target:2026-10-01", 20, 789L)),
            experience = 1234L,
            activeTimer = ActiveTimer(target.id, 100L, 200L, paused = true, "2026-10-02", listOf(TimerSegment(100L, 200L))),
            onboardingCompleted = true,
            launchDestination = LaunchDestination.TARGETS,
            lastOpenedDestination = LaunchDestination.STATISTICS,
            notificationsEnabled = false,
        )

        val encoded = AttentionBackupCodec.encode(expected)
        val restored = AttentionBackupCodec.decode(encoded)

        assertTrue(encoded.contains("\"version\":1"))
        assertEquals(expected, restored)
    }

    @Test
    fun decoder_requires_explicit_supported_version() {
        val legacy = Json.Default.encodeToString(AttentionState())
        val unsupported = legacy.replaceFirst("{", "{\"version\":99,")

        assertFalse(runCatching { AttentionBackupCodec.decode(legacy) }.isSuccess)
        assertFalse(runCatching { AttentionBackupCodec.decode(unsupported) }.isSuccess)
    }

    @Test
    fun merge_deduplicates_stable_ids_and_applies_incoming_settings() {
        val existing = AttentionState(
            settings = StoredSettings(dailyCapacityMinutes = 120),
            targets = listOf(Target("same", title = "旧")),
        )
        val incoming = AttentionState(
            settings = StoredSettings(dailyCapacityMinutes = 300),
            targets = listOf(Target("same", title = "重复"), Target("new", title = "新增")),
            launchDestination = LaunchDestination.STATISTICS,
            activeTimer = ActiveTimer("new", 100L, lastPlanningDate = "2026-10-10"),
        )

        val merged = AttentionBackupCodec.merge(existing, incoming)

        assertEquals(listOf("same", "new"), merged.targets.map { it.id })
        assertEquals("旧", merged.targets.first().title)
        assertEquals(300, merged.settings.dailyCapacityMinutes)
        assertEquals(LaunchDestination.STATISTICS, merged.launchDestination)
        assertEquals(incoming.activeTimer, merged.activeTimer)
    }

    @Test
    fun backup_round_trip_preserves_one_time_goal_deadline() {
        val target = Target("target", title = "项目")
        val expected = AttentionState(
            targets = listOf(target),
            goalStages = listOf(GoalStage("one-time", target.id, GoalCadence.ONE_TIME, 240, "2026-10-10", "2026-10-12")),
        )

        assertEquals(expected, AttentionBackupCodec.decode(AttentionBackupCodec.encode(expected)))
    }

    @Test
    fun backup_round_trip_preserves_multiple_one_time_stages_and_their_ids() {
        val target = Target("target", title = "项目")
        val first = GoalStage("first-stage", target.id, GoalCadence.ONE_TIME, 60, "2026-10-10", "2026-10-12", completed = true)
        val second = GoalStage("second-stage", target.id, GoalCadence.ONE_TIME, 90, "2026-10-13")
        val expected = AttentionState(targets = listOf(target), goalStages = listOf(first, second))

        val restored = AttentionBackupCodec.decode(AttentionBackupCodec.encode(expected))

        assertEquals(listOf(first, second), restored.goalStages)
        assertEquals(listOf("first-stage", "second-stage"), restored.goalStages.map { it.id })
    }
}
