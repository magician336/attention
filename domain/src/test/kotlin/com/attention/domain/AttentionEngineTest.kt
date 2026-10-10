package com.attention.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionEngineTest {
    private val utc = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 10, 8)

    @Test
    fun target_tree_supports_arbitrary_depth_and_subtree_rollup() {
        val state = AttentionState()
            .addTarget("阅读")
        val root = state.targets.single()
        val childState = state.addTarget("中国文学", root.id)
        val child = childState.targets.single { it.title == "中国文学" }
        val nested = childState.addTarget("红楼梦", child.id)
        val leaf = nested.targets.single { it.title == "红楼梦" }
            .let { nested.addTimeEntry(date.toString(), 25, it.id) }
            .addTimeEntry(date.toString(), 10, root.id)

        assertEquals(35, leaf.subtreeMinutes(root.id, date.toString()))
        assertEquals(10, leaf.directMinutes(root.id, date.toString()))
    }

    @Test
    fun planner_boundary_and_week_start_are_applied_to_periods() {
        val settings = StoredSettings(planningDayBoundaryMinutes = 240, weekStartDay = 7)
        val state = AttentionState(settings = settings)
        val planningDate = state.planningDate(Instant.parse("2026-10-08T02:30:00Z"), utc)
        assertEquals(LocalDate.of(2026, 10, 7), planningDate)

        val week = state.periodRange(LocalDate.of(2026, 10, 8), GoalCadence.WEEKLY)
        assertEquals(LocalDate.of(2026, 10, 4), week.start)
        assertEquals(LocalDate.of(2026, 10, 10), week.endInclusive)
    }

    @Test
    fun stage_progress_reports_completion_gap_and_excess() {
        val withTarget = AttentionState().addTarget("英语")
        val target = withTarget.targets.single()
        val state = withTarget
            .addGoalStage(target.id, GoalCadence.DAILY, 30, date.toString())
            .addTimeEntry(date.toString(), 40, target.id)
        val progress = state.progress(state.goalStages.single(), date)

        assertTrue(progress.completed)
        assertEquals(0, progress.gapMinutes)
        assertEquals(10, progress.excessMinutes)
    }

    @Test
    fun daily_goal_progress_uses_only_the_current_planning_date_and_all_entry_sources() {
        val withTarget = AttentionState().addTarget("英语")
        val target = withTarget.targets.single()
        val withGoal = withTarget.addGoalStage(target.id, GoalCadence.DAILY, 30, date.toString())
        val state = withGoal
            .addTimeEntry(date.minusDays(1).toString(), 20, target.id, TimeEntrySource.TIMER)
            .addTimeEntry(date.toString(), 10, target.id, TimeEntrySource.MANUAL)
            .addTimeEntry(date.toString(), 20, target.id, TimeEntrySource.IMPORT)

        val progress = state.progress(state.goalStages.single(), date)

        assertEquals(30, progress.actualMinutes)
        assertEquals(30, progress.targetMinutes)
        assertEquals(0, progress.gapMinutes)
        assertEquals(0, progress.excessMinutes)
        assertTrue(progress.completed)

        val excess = state.addTimeEntry(date.toString(), 5, target.id).progress(state.goalStages.single(), date)
        assertEquals(35, excess.actualMinutes)
        assertEquals(5, excess.excessMinutes)
        assertEquals(0, excess.gapMinutes)
        assertTrue(excess.completed)
    }

    @Test
    fun weekly_goal_uses_configured_seven_day_boundaries_and_excludes_adjacent_weeks() {
        val mondayStart = AttentionState().addTarget("英语").copy(
            settings = StoredSettings(weekStartDay = 1),
        )
        val mondayTarget = mondayStart.targets.single()
        val stage = mondayStart.addGoalStage(mondayTarget.id, GoalCadence.WEEKLY, 60, date.toString()).goalStages.single()
        val records = mondayStart
            .addTimeEntry("2026-10-04", 100, mondayTarget.id)
            .addTimeEntry("2026-10-05", 30, mondayTarget.id)
            .addTimeEntry("2026-10-11", 40, mondayTarget.id)
            .addTimeEntry("2026-10-12", 200, mondayTarget.id)

        val mondayRange = records.periodRange(date, GoalCadence.WEEKLY)
        val mondayProgress = records.progress(stage, date)
        val sundayStart = records.copy(settings = StoredSettings(weekStartDay = 7))
        val sundayRange = sundayStart.periodRange(date, GoalCadence.WEEKLY)
        val sundayProgress = sundayStart.progress(stage, date)

        assertEquals(LocalDate.of(2026, 10, 5), mondayRange.start)
        assertEquals(LocalDate.of(2026, 10, 11), mondayRange.endInclusive)
        assertEquals(70, mondayProgress.actualMinutes)
        assertTrue(mondayProgress.completed)
        assertEquals(10, mondayProgress.excessMinutes)
        assertEquals(LocalDate.of(2026, 10, 4), sundayRange.start)
        assertEquals(LocalDate.of(2026, 10, 10), sundayRange.endInclusive)
        assertEquals(130, sundayProgress.actualMinutes)
        assertTrue(sundayProgress.completed)
        assertEquals(70, sundayProgress.excessMinutes)
    }

    @Test
    fun monthly_goal_uses_calendar_month_boundaries_including_february_leap_day() {
        val state = AttentionState().addTarget("阅读")
        val target = state.targets.single()
        val stage = state.addGoalStage(target.id, GoalCadence.MONTHLY, 60, "2024-02-01").goalStages.single()
        val records = state
            .addTimeEntry("2024-01-31", 90, target.id)
            .addTimeEntry("2024-02-01", 20, target.id)
            .addTimeEntry("2024-02-29", 25, target.id)
            .addTimeEntry("2024-03-01", 120, target.id)

        val leapFebruary = records.periodRange(LocalDate.of(2024, 2, 14), GoalCadence.MONTHLY)
        val regularFebruary = records.periodRange(LocalDate.of(2025, 2, 14), GoalCadence.MONTHLY)
        val sundaySettingsFebruary = records.copy(settings = StoredSettings(weekStartDay = 7))
        val progress = records.progress(stage, LocalDate.of(2024, 2, 14))
        val excess = records.addTimeEntry("2024-02-29", 20, target.id)
            .progress(stage, LocalDate.of(2024, 2, 29))

        assertEquals(LocalDate.of(2024, 2, 1), leapFebruary.start)
        assertEquals(LocalDate.of(2024, 2, 29), leapFebruary.endInclusive)
        assertEquals(leapFebruary, sundaySettingsFebruary.periodRange(LocalDate.of(2024, 2, 14), GoalCadence.MONTHLY))
        assertEquals(LocalDate.of(2025, 2, 1), regularFebruary.start)
        assertEquals(LocalDate.of(2025, 2, 28), regularFebruary.endInclusive)
        assertEquals(45, progress.actualMinutes)
        assertEquals(15, progress.gapMinutes)
        assertEquals(0, progress.excessMinutes)
        assertEquals(65, excess.actualMinutes)
        assertEquals(0, excess.gapMinutes)
        assertEquals(5, excess.excessMinutes)
        assertTrue(excess.completed)
    }

    @Test
    fun adding_a_goal_to_an_archived_target_is_rejected_without_changing_state() {
        val state = AttentionState().addTarget("旧计划")
        val target = state.targets.single()
        val archived = state.archiveTarget(target.id)
        val before = archived

        assertThrows(IllegalArgumentException::class.java) {
            archived.addGoalStage(target.id, GoalCadence.DAILY, 30, date.toString())
        }
        assertEquals(before, archived)
    }

    @Test
    fun daily_goal_rejects_non_positive_minutes_and_invalid_start_dates() {
        val state = AttentionState().addTarget("学习")
        val target = state.targets.single()

        assertThrows(IllegalArgumentException::class.java) {
            state.addGoalStage(target.id, GoalCadence.DAILY, 0, date.toString())
        }
        assertThrows(java.time.format.DateTimeParseException::class.java) {
            state.addGoalStage(target.id, GoalCadence.DAILY, 30, "not-a-date")
        }
    }

    @Test
    fun timer_crossing_planning_boundary_creates_two_entries() {
        val withTarget = AttentionState().addTarget("专注")
        val target = withTarget.targets.single()
        val start = Instant.parse("2026-10-08T02:50:00Z")
        val stop = Instant.parse("2026-10-08T03:10:00Z")
        val result = withTarget.startTimer(target.id, start, utc).stopTimer(stop, utc)

        assertEquals(2, result.entries.size)
        assertEquals(20, result.entries.sumOf { it.durationMinutes })
        assertEquals(LocalDate.of(2026, 10, 7).toString(), result.entries[0].planningDate)
        assertEquals(LocalDate.of(2026, 10, 8).toString(), result.entries[1].planningDate)
    }

    @Test
    fun paused_timer_only_counts_running_segments() {
        val state = AttentionState().addTarget("专注")
        val target = state.targets.single()
        val started = state.startTimer(target.id, Instant.parse("2026-10-08T00:00:00Z"), utc)
        val paused = started.pauseTimer(Instant.parse("2026-10-08T00:05:00Z"))
        val resumed = paused.resumeTimer(Instant.parse("2026-10-08T00:10:00Z"))
        val result = resumed.stopTimer(Instant.parse("2026-10-08T00:15:00Z"), utc)
        assertEquals(1, result.entries.size)
        assertEquals(10, result.entries.single().durationMinutes)
    }

    @Test
    fun recurrence_generates_independent_occurrences_and_can_stop() {
        val state = AttentionState().addRecurrence(
            RecurrenceRule(
                title = "复盘",
                startDate = date.toString(),
                frequency = ScheduleFrequency.WEEKLY,
                weekdays = listOf(4),
            ),
        )
        val rule = state.recurrenceRules.single()
        assertEquals(2, state.occurrences(rule, date.plusDays(8)).size)
        assertEquals(false, state.stopRecurrence(rule.id).recurrenceRules.single().active)
    }

    @Test
    fun recurring_reminder_is_calculated_for_each_occurrence() {
        val rule = RecurrenceRule(
            title = "提醒",
            startDate = date.toString(),
            frequency = ScheduleFrequency.DAILY,
            reminderMinuteOfDay = 9 * 60,
        )
        val occurrence = AttentionState().occurrences(rule, date).single()
        assertTrue(occurrence.reminderEpochMillis != null)
    }

    @Test
    fun experience_and_cumulative_rewards_follow_spec() {
        assertEquals(1, levelForExperience(0))
        assertEquals(2, levelForExperience(1000))
        assertEquals(3, levelForExperience(3000))
        assertEquals(10_000L, cumulativeMilestoneReward(100))
        assertEquals(20_000L, cumulativeMilestoneReward(500))
        assertEquals(30_000L, cumulativeMilestoneReward(1000))
    }

    @Test
    fun future_rules_and_snapshots_preserve_previous_period() {
        val state = AttentionState().addTarget("项目")
        val target = state.targets.single()
        val stage = state.addGoalStage(target.id, GoalCadence.WEEKLY, 120, date.toString()).goalStages.single()
        val snapshot = state.addTimeEntry(date.toString(), 90, target.id)
            .snapshot(stage, date.minusDays(3), date.plusDays(3))
        val future = state.addFutureGoalRule(FutureGoalRule(targetId = target.id, cadence = GoalCadence.WEEKLY, targetMinutes = 180, effectiveFrom = date.plusDays(7).toString()))
            .addPeriodSnapshot(snapshot)

        assertEquals(180, future.goalRulesAt(target.id, date.plusDays(8)).single().targetMinutes)
        assertEquals(1, future.periodSnapshots.size)
        assertEquals(30, future.periodSnapshots.single().gapMinutes)
    }

    @Test
    fun unowned_batch_assignment_updates_experience_once() {
        val state = AttentionState().addTarget("学习")
        val target = state.targets.single()
        val withEntries = state.addTimeEntry(date.toString(), 15)
            .addTimeEntry(date.toString(), 10)
        assertEquals(0L, withEntries.recalculateExperience().experience)
        val assigned = withEntries.assignUnowned(withEntries.timeEntries.map { it.id }.toSet(), target.id)
        assertEquals(25L, assigned.experience)
        assertEquals(25, assigned.subtreeMinutes(target.id))
    }

    @Test
    fun editing_time_entry_preserves_planning_date_and_source() {
        val state = AttentionState().addTarget("学习")
        val target = state.targets.single()
        val withEntry = state.addTimeEntry(
            date.toString(),
            25,
            target.id,
            TimeEntrySource.IMPORT,
            occurredAtEpochMillis = 1234L,
            note = "原备注",
        )
        val entry = withEntry.timeEntries.single()

        val edited = withEntry.editTimeEntry(entry.id, 40, null, "新备注")
        val result = edited.timeEntries.single()
        assertEquals(date.toString(), result.planningDate)
        assertEquals(TimeEntrySource.IMPORT, result.source)
        assertEquals(1234L, result.occurredAtEpochMillis)
        assertEquals(40, result.durationMinutes)
        assertEquals(null, result.targetId)
        assertEquals("新备注", result.note)
    }

    @Test
    fun archived_targets_reject_new_or_reassigned_time_entries() {
        val state = AttentionState().addTarget("旧计划")
        val target = state.targets.single()
        val archived = state.archiveTarget(target.id)

        assertThrows(IllegalArgumentException::class.java) {
            archived.addTimeEntry(date.toString(), 10, target.id)
        }
        val unowned = archived.addTimeEntry(date.toString(), 10).timeEntries.single()
        assertThrows(IllegalArgumentException::class.java) {
            archived.assignUnowned(setOf(unowned.id), target.id)
        }
        assertThrows(IllegalArgumentException::class.java) {
            archived.startTimer(target.id, Instant.parse("2026-10-08T00:00:00Z"), utc)
        }
    }

    @Test
    fun batch_assignment_is_atomic_for_stale_or_already_owned_ids() {
        val state = AttentionState().addTarget("学习")
        val target = state.targets.single()
        val withEntries = state.addTimeEntry(date.toString(), 10).addTimeEntry(date.toString(), 15, target.id)
        val before = withEntries

        assertThrows(IllegalArgumentException::class.java) {
            withEntries.assignUnowned(setOf(withEntries.timeEntries[0].id, "missing"), target.id)
        }
        assertEquals(before, withEntries)

        assertThrows(IllegalArgumentException::class.java) {
            withEntries.assignUnowned(withEntries.timeEntries.map { it.id }.toSet(), target.id)
        }
        assertEquals(before, withEntries)
    }

    @Test
    fun deleting_time_entry_updates_goal_progress_and_period_stats() {
        val state = AttentionState().addTarget("学习")
        val target = state.targets.single()
        val withGoal = state.addGoalStage(target.id, GoalCadence.DAILY, 30, date.toString())
        val withEntries = withGoal
            .addTimeEntry(date.toString(), 20, target.id)
            .addTimeEntry(date.toString(), 15, target.id)
        val firstEntry = withEntries.timeEntries.first()

        val deleted = withEntries.deleteTimeEntry(firstEntry.id)
        val progress = deleted.progress(deleted.goalStages.single(), date)
        val stats = deleted.periodStats(date, GoalCadence.DAILY)

        assertEquals(15, progress.actualMinutes)
        assertEquals(15, progress.gapMinutes)
        assertEquals(15, stats.actualMinutes)
        assertEquals(15, deleted.subtreeMinutes(target.id))
    }

    @Test
    fun milestone_instance_is_awarded_once() {
        val state = AttentionState().addTarget("每日投入")
        val target = state.targets.single()
        val dayState = (0..6).fold(state) { current, offset ->
            current.addTimeEntry(date.plusDays(offset.toLong()).toString(), 1, target.id)
        }
        val awarded = dayState.awardEligibleMilestones(date.plusDays(6))
        assertTrue(awarded.milestones.any { it.instanceKey == "streak:${target.id}:7" })
        assertEquals(500L, awarded.milestones.single().reward)
        assertEquals(1, awarded.awardEligibleMilestones(date.plusDays(6)).milestones.size)
    }

    @Test
    fun csv_exports_keep_analysis_columns_and_escape_values() {
        val state = AttentionState().addTarget("学习,英语")
        val target = state.targets.single()
        val exported = state
            .addTimeEntry(date.toString(), 20, target.id, note = "带,逗号")
            .addSchedule(ScheduleEntry(planningDate = date.toString(), title = "复习\"章节", estimatedMinutes = 30, targetId = target.id))
        assertTrue(exported.timeEntriesCsv().contains("\"带,逗号\""))
        assertTrue(exported.timeEntriesCsv().contains("target_title"))
        assertTrue(exported.schedulesCsv().contains("planning_date,title,completed"))
        assertTrue(exported.schedulesCsv().contains("\"复习\"\"章节\""))
    }

    @Test
    fun future_target_move_does_not_rewrite_historical_parent_rollup() {
        val state = AttentionState().addTarget("旧父目标").addTarget("新父目标")
        val oldParent = state.targets.first()
        val newParent = state.targets.last()
        val moved = state.addTarget("子计划", oldParent.id)
        val child = moved.targets.single { it.title == "子计划" }
        val withHistory = moved
            .addTimeEntry(date.toString(), 20, child.id)
            .addTimeEntry(date.plusDays(2).toString(), 15, child.id)
            .addFutureTargetMove(child.id, newParent.id, date.plusDays(1).toString())
        assertEquals(20, withHistory.subtreeMinutes(oldParent.id, date.toString()))
        assertEquals(0, withHistory.subtreeMinutes(oldParent.id, date.plusDays(2).toString()))
        assertEquals(0, withHistory.subtreeMinutes(newParent.id, date.toString()))
        assertEquals(15, withHistory.subtreeMinutes(newParent.id, date.plusDays(2).toString()))
    }

    @Test
    fun siblings_can_be_reordered_without_changing_stable_ids() {
        val state = AttentionState().addTarget("一").addTarget("二").addTarget("三")
        val third = state.targets.single { it.title == "三" }
        val reordered = state.reorderTarget(third.id, 0)
        assertEquals(listOf("三", "一", "二"), reordered.targetChildren(null).map { it.title })
        assertEquals(third.id, reordered.targetChildren(null).first().id)
    }

    @Test
    fun moving_target_to_another_parent_keeps_id_and_places_it_after_existing_siblings() {
        val state = AttentionState().addTarget("旧父目标").addTarget("新父目标")
        val oldParent = state.targets.first()
        val newParent = state.targets.last()
        val withChild = state.addTarget("子计划", oldParent.id)
        val child = withChild.targets.single { it.title == "子计划" }
        val existingSibling = withChild.addTarget("已有子计划", newParent.id)
        val moved = existingSibling.moveTarget(child.id, newParent.id)

        assertEquals(newParent.id, moved.targets.single { it.id == child.id }.parentId)
        assertEquals(child.id, moved.targetChildren(newParent.id).last().id)
        assertEquals(child.id, moved.targets.single { it.id == child.id }.id)
        assertEquals(listOf("已有子计划", "子计划"), moved.targetChildren(newParent.id).map { it.title })
    }
}
