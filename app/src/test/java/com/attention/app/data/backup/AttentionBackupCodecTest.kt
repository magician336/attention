package com.attention.app.data.backup

import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.CapacitySummary
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
import com.attention.domain.capacitySummary
import com.attention.domain.recalculateExperience
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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
            schedules = listOf(ScheduleEntry("schedule", "2026-10-02", "阅读", estimatedMinutes = 30, targetId = target.id, recurrenceRuleId = "rule", completed = true)),
            recurrenceRules = listOf(RecurrenceRule("rule", "阅读", "2026-10-01", ScheduleFrequency.DAILY, targetId = target.id, active = false)),
            migrations = listOf(Migration("migration", target.id, "stage", 20, "2026-10-08")),
            futureGoalRules = listOf(com.attention.domain.FutureGoalRule("future", target.id, "stage", GoalCadence.MONTHLY, 300, "2026-11-01")),
            periodSnapshots = listOf(PeriodSnapshot("snapshot", target.id, GoalCadence.WEEKLY, "2026-10-01", "2026-10-07", 120, 90, 30, 0, false, stageId = "stage")),
            targetMoves = listOf(TargetMove("move", target.id, null, "2026-11-01")),
            milestones = listOf(Milestone("milestone", "goal_period", "target:2026-10-01", 20, 789L)),
            experience = 65L,
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
    fun backup_restore_recomputes_capacity_from_settings_and_saved_schedule_fields() {
        val day = "2026-10-08"
        val expected = AttentionState(
            settings = StoredSettings(planningDayBoundaryMinutes = 120, dailyCapacityMinutes = 60),
            schedules = listOf(
                ScheduleEntry(
                    id = "estimated",
                    planningDate = day,
                    title = "预计优先",
                    startMinute = 60,
                    endMinute = 180,
                    estimatedMinutes = 20,
                    completed = true,
                    recurrenceRuleId = "rule",
                ),
                ScheduleEntry(
                    id = "range",
                    planningDate = day,
                    title = "完整时间段",
                    startMinute = 180,
                    endMinute = 220,
                ),
            ),
            recurrenceRules = listOf(
                RecurrenceRule(
                    id = "rule",
                    title = "重复规则",
                    startDate = day,
                    frequency = ScheduleFrequency.DAILY,
                    estimatedMinutes = 999,
                ),
            ),
        )

        val encoded = AttentionBackupCodec.encode(expected)
        val restored = AttentionBackupCodec.decode(encoded)

        assertFalse(encoded.contains("usedMinutes"))
        assertFalse(encoded.contains("overloaded"))
        assertEquals(expected, restored)
        assertEquals(CapacitySummary(60, 60, false), restored.capacitySummary(day))
        assertEquals(CapacitySummary(60, 0, false), restored.capacitySummary("2026-10-09"))
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
    fun merge_recomputes_experience_from_time_entries_and_milestones() {
        val target = Target("target", title = "学习")
        val existing = AttentionState(
            targets = listOf(target),
            timeEntries = listOf(TimeEntry("existing-entry", "2026-10-08", 10, target.id)),
            milestones = listOf(Milestone("existing-milestone", "goal_period", "stage:1", 20, 1L)),
            experience = 999L,
        )
        val incoming = AttentionState(
            targets = listOf(target),
            timeEntries = listOf(TimeEntry("incoming-entry", "2026-10-09", 5, target.id)),
            milestones = listOf(Milestone("incoming-milestone", "streak", "target:7", 30, 2L)),
            experience = 1L,
        )

        val merged = AttentionBackupCodec.merge(existing, incoming)

        assertEquals(65L, merged.experience)
        assertEquals(merged.recalculateExperience(), merged)
    }

    @Test
    fun merge_deduplicates_milestones_by_instance_key_and_keeps_existing_award() {
        val existing = AttentionState(
            milestones = listOf(Milestone("old-id", "cumulative", "cumulative:100", 10_000L, 100L)),
        )
        val incoming = AttentionState(
            milestones = listOf(
                Milestone("new-id", "cumulative", "cumulative:100", 10_000L, 200L),
                Milestone("another-id", "cumulative", "cumulative:200", 10_000L, 300L),
            ),
            experience = 999_999L,
        )

        val merged = AttentionBackupCodec.merge(existing, incoming)

        assertEquals(listOf("cumulative:100", "cumulative:200"), merged.milestones.map { it.instanceKey })
        assertEquals("old-id", merged.milestones.first().id)
        assertEquals(20_000L, merged.experience)
    }

    @Test
    fun decode_normalizes_duplicate_milestone_instances_and_ignores_saved_experience() {
        val target = Target("target", title = "学习")
        val encoded = AttentionBackupCodec.encode(
            AttentionState(
                targets = listOf(target),
                timeEntries = listOf(TimeEntry("entry", "2026-10-08", 25, target.id)),
                milestones = listOf(
                    Milestone("first", "cumulative", "cumulative:100", 10_000L, 100L),
                    Milestone("duplicate", "cumulative", "cumulative:100", 10_000L, 200L),
                ),
                experience = 1L,
            ),
        )

        val restored = AttentionBackupCodec.decode(encoded)

        assertEquals(listOf("first"), restored.milestones.map { it.id })
        assertEquals(10_025L, restored.experience)
    }

    @Test
    fun from_domain_normalizes_duplicate_milestone_instances_before_recomputing_experience() {
        val state = AttentionState(
            milestones = listOf(
                Milestone("first", "cumulative", "cumulative:100", 10_000L, 100L),
                Milestone("duplicate", "cumulative", "cumulative:100", 20_000L, 200L),
            ),
            experience = 999_999L,
        )

        val backup = AttentionBackup.fromDomain(state)

        assertEquals(listOf("first"), backup.milestones.map { it.id })
        assertEquals(10_000L, backup.experience)
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
    fun backup_round_trip_preserves_multiple_target_moves_and_their_ordering_fields() {
        val oldParent = Target("old-parent", title = "旧父目标")
        val newParent = Target("new-parent", title = "新父目标")
        val child = Target("child", parentId = oldParent.id, title = "子计划")
        val expected = AttentionState(
            targets = listOf(oldParent, newParent, child),
            targetMoves = listOf(
                TargetMove("move-1", child.id, newParent.id, "2026-11-01"),
                TargetMove("move-2", child.id, null, "2026-12-01"),
            ),
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

    @Test
    fun backup_round_trip_preserves_multiple_migrations_and_cancelled_state() {
        val target = Target("target", title = "项目")
        val stage = GoalStage("stage", target.id, GoalCadence.WEEKLY, 240, "2026-10-01")
        val active = Migration(
            id = "active-migration",
            targetId = target.id,
            sourceStageId = stage.id,
            minutes = 90,
            destinationStartDate = "2026-10-08",
            destinationEndDate = "2026-10-09",
        )
        val cancelled = Migration(
            id = "cancelled-migration",
            targetId = target.id,
            sourceStageId = stage.id,
            minutes = 45,
            destinationStartDate = "2026-10-10",
            cancelled = true,
        )
        val expected = AttentionState(
            targets = listOf(target),
            goalStages = listOf(stage),
            migrations = listOf(active, cancelled),
        )

        val restored = AttentionBackupCodec.decode(AttentionBackupCodec.encode(expected))

        assertEquals(expected.migrations, restored.migrations)
        assertEquals(listOf("active-migration", "cancelled-migration"), restored.migrations.map { it.id })
        assertTrue(restored.migrations.last().cancelled)
    }

    @Test
    fun merge_deduplicates_migrations_by_stable_id_without_losing_cancelled_record() {
        val existing = AttentionState(
            migrations = listOf(Migration("same", "target", "stage", 60, "2026-10-08", cancelled = true)),
        )
        val incoming = AttentionState(
            migrations = listOf(
                Migration("same", "target", "stage", 60, "2026-10-08"),
                Migration("new", "target", "stage", 30, "2026-10-09"),
            ),
        )

        val merged = AttentionBackupCodec.merge(existing, incoming)

        assertEquals(listOf("same", "new"), merged.migrations.map { it.id })
        assertTrue(merged.migrations.first().cancelled)
    }

    @Test
    fun decoder_rejects_invalid_active_timer_segments() {
        val invalid = AttentionState(
            activeTimer = ActiveTimer(
                targetId = null,
                startedAtEpochMillis = 200L,
                accumulatedMillis = -1L,
                lastPlanningDate = "2026-10-02",
                completedSegments = listOf(TimerSegment(200L, 100L)),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            AttentionBackupCodec.decode(AttentionBackupCodec.encode(invalid))
        }
    }

    @Test
    fun backup_round_trip_preserves_running_timer_context() {
        val expected = AttentionState(
            activeTimer = ActiveTimer(
                targetId = "target",
                startedAtEpochMillis = 2_000L,
                accumulatedMillis = 60_000L,
                paused = false,
                lastPlanningDate = "2026-10-08",
                completedSegments = listOf(TimerSegment(1_000L, 1_500L)),
            ),
        )

        assertEquals(expected, AttentionBackupCodec.decode(AttentionBackupCodec.encode(expected)))
    }
}
