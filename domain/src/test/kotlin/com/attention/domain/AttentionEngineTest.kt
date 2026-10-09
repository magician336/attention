package com.attention.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
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
