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
    fun experience_and_cumulative_rewards_follow_spec() {
        assertEquals(1, levelForExperience(0))
        assertEquals(2, levelForExperience(1000))
        assertEquals(3, levelForExperience(3000))
        assertEquals(10_000L, cumulativeMilestoneReward(100))
        assertEquals(20_000L, cumulativeMilestoneReward(500))
        assertEquals(30_000L, cumulativeMilestoneReward(1000))
    }
}
