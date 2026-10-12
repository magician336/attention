package com.attention.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionEngineTest {
    private val utc = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 10, 8)

    @Test
    fun capacity_summary_uses_schedule_planning_minutes_only_and_marks_strict_overload() {
        val state = AttentionState(
            settings = StoredSettings(dailyCapacityMinutes = 60),
            schedules = listOf(
                ScheduleEntry(
                    id = "estimated-first",
                    planningDate = date.toString(),
                    title = "预计优先",
                    startMinute = 60,
                    endMinute = 120,
                    estimatedMinutes = 30,
                ),
                ScheduleEntry(
                    id = "time-range",
                    planningDate = date.toString(),
                    title = "时间段",
                    startMinute = 120,
                    endMinute = 150,
                ),
                ScheduleEntry(id = "no-duration", planningDate = date.toString(), title = "无时长"),
                ScheduleEntry(id = "other-day", planningDate = date.plusDays(1).toString(), title = "其他日", estimatedMinutes = 90),
            ),
            timeEntries = listOf(TimeEntry("actual", date.toString(), 300)),
        )

        assertEquals(CapacitySummary(60, 60, false), state.capacitySummary(date.toString()))
        assertEquals(CapacitySummary(60, 60, false), state.copy(schedules = state.schedules.map { it.copy(completed = true) }).capacitySummary(date.toString()))
        val overloaded = state.copy(settings = StoredSettings(dailyCapacityMinutes = 59))
        assertEquals(CapacitySummary(59, 60, true), overloaded.capacitySummary(date.toString()))
        val added = overloaded.addSchedule(ScheduleEntry(id = "added", planningDate = date.toString(), title = "继续安排", estimatedMinutes = 15))
        val edited = added.updateSchedule(added.schedules.single { it.id == "added" }.copy(title = "继续编辑", estimatedMinutes = 20))
        assertEquals("继续编辑", edited.schedules.single { it.id == "added" }.title)
        assertTrue(edited.toggleScheduleCompleted("added").schedules.single { it.id == "added" }.completed)
        assertEquals(CapacitySummary(null, 60, false), state.copy(settings = StoredSettings()).capacitySummary(date.toString()))
    }

    @Test
    fun capacity_summary_handles_partial_ranges_and_ignores_non_schedule_work() {
        val day = date.toString()
        val state = AttentionState(
            settings = StoredSettings(dailyCapacityMinutes = 90),
            goalStages = listOf(GoalStage("stage", "target", GoalCadence.DAILY, 600, day)),
            timeEntries = listOf(TimeEntry("actual", day, 500)),
            schedules = listOf(
                ScheduleEntry("estimate", day, "预计优先", startMinute = 60, endMinute = 180, estimatedMinutes = 30),
                ScheduleEntry("range", day, "完整时间段", startMinute = 180, endMinute = 240),
                ScheduleEntry("only-start", day, "只有开始", startMinute = 300),
                ScheduleEntry("only-end", day, "只有结束", endMinute = 360),
                ScheduleEntry("none", day, "没有时长"),
                // Imported data can bypass add/update validation; it must not
                // make a day look less occupied through a negative duration.
                ScheduleEntry("malformed", day, "逆序时间段", startMinute = 500, endMinute = 400),
                ScheduleEntry("other-day", date.plusDays(1).toString(), "其他日", estimatedMinutes = 15),
            ),
            recurrenceRules = listOf(
                RecurrenceRule("rule", "重复规则", day, ScheduleFrequency.DAILY, estimatedMinutes = 999),
            ),
            migrations = listOf(Migration("migration", "target", "stage", 300, day)),
            activeTimer = ActiveTimer(null, 1L, lastPlanningDate = day),
            milestones = listOf(Milestone("milestone", "reward", "instance", 999, 2L)),
        )

        assertEquals(CapacitySummary(90, 90, false), state.capacitySummary(day))
        assertEquals(CapacitySummary(90, 15, false), state.capacitySummary(date.plusDays(1).toString()))
        assertEquals(CapacitySummary(89, 90, true), state.copy(settings = StoredSettings(dailyCapacityMinutes = 89)).capacitySummary(day))
    }

    @Test
    fun capacity_summary_falls_back_from_invalid_estimates_without_reducing_used_minutes() {
        val day = date.toString()
        val state = AttentionState(
            settings = StoredSettings(dailyCapacityMinutes = 115),
            schedules = listOf(
                ScheduleEntry("negative-estimate", day, "负数预计", startMinute = 60, endMinute = 120, estimatedMinutes = -15),
                ScheduleEntry("zero-estimate", day, "零预计", startMinute = 120, endMinute = 150, estimatedMinutes = 0),
                ScheduleEntry("negative-no-duration", day, "负数无时长", estimatedMinutes = -20),
                ScheduleEntry("negative-invalid-range", day, "负数逆序", startMinute = 500, endMinute = 400, estimatedMinutes = -20),
                ScheduleEntry("valid-estimate", day, "有效预计", startMinute = 150, endMinute = 240, estimatedMinutes = 25),
            ),
        )

        assertEquals(CapacitySummary(115, 115, false), state.capacitySummary(day))
    }

    @Test
    fun recurring_occurrences_are_counted_once_on_their_own_planning_days() {
        val rule = RecurrenceRule(
            id = "daily-capacity-rule",
            title = "每日复盘",
            startDate = date.toString(),
            frequency = ScheduleFrequency.DAILY,
            estimatedMinutes = 25,
        )
        val materialized = AttentionState(settings = StoredSettings(dailyCapacityMinutes = 25))
            .addRecurrenceAndMaterialize(rule, date.plusDays(2))

        assertEquals(3, materialized.schedules.size)
        assertEquals(CapacitySummary(25, 25, false), materialized.capacitySummary(date.toString()))
        assertEquals(CapacitySummary(25, 25, false), materialized.capacitySummary(date.plusDays(1).toString()))
        assertEquals(CapacitySummary(25, 25, false), materialized.capacitySummary(date.plusDays(2).toString()))
        assertEquals(CapacitySummary(25, 0, false), materialized.capacitySummary(date.plusDays(3).toString()))
        assertEquals(
            CapacitySummary(25, 25, false),
            materialized.toggleScheduleCompleted(materialized.schedules.first().id).capacitySummary(date.toString()),
        )
    }

    @Test
    fun planning_boundary_changes_current_date_without_rewriting_saved_schedule_dates() {
        val previousDate = date.minusDays(1)
        val state = AttentionState(
            settings = StoredSettings(planningDayBoundaryMinutes = 180, dailyCapacityMinutes = 30),
            schedules = listOf(
                ScheduleEntry("previous", previousDate.toString(), "上一规划日", estimatedMinutes = 20),
                ScheduleEntry("current", date.toString(), "当前规划日", estimatedMinutes = 40),
            ),
        )
        val instant = Instant.parse("2026-10-08T02:30:00Z")

        assertEquals(previousDate, state.planningDate(instant, utc))
        assertEquals(CapacitySummary(30, 20, false), state.capacitySummary(previousDate.toString()))

        val movedBoundary = state.copy(settings = state.settings.copy(planningDayBoundaryMinutes = 120))
        assertEquals(date, movedBoundary.planningDate(instant, utc))
        assertEquals(CapacitySummary(30, 40, true), movedBoundary.capacitySummary(date.toString()))
        assertEquals(listOf(previousDate.toString(), date.toString()), movedBoundary.schedules.map { it.planningDate })
    }

    @Test
    fun agenda_entries_filter_by_planning_date_and_sort_all_dates_stably() {
        val state = AttentionState()
            .addSchedule(ScheduleEntry(id = "later", planningDate = "2026-10-09", title = "Later", startMinute = 5, endMinute = 15))
            .addSchedule(ScheduleEntry(id = "same-b", planningDate = "2026-10-08", title = "Same B", startMinute = 30, endMinute = 40))
            .addSchedule(ScheduleEntry(id = "same-a", planningDate = "2026-10-08", title = "Same A", startMinute = 30, endMinute = 40))
            .addSchedule(ScheduleEntry(id = "earlier", planningDate = "2026-10-07", title = "Earlier"))

        assertEquals(listOf("earlier", "same-a", "same-b", "later"), state.agendaEntries().map { it.id })
        assertEquals(listOf("same-a", "same-b"), state.agendaEntries(LocalDate.of(2026, 10, 8)).map { it.id })
        assertTrue(state.agendaEntries(LocalDate.of(2026, 10, 6)).isEmpty())
    }

    @Test
    fun schedule_entry_edits_validate_every_field_and_keep_stable_id() {
        val target = AttentionState().addTarget("学习").targets.single()
        val original = ScheduleEntry(
            id = "schedule-1",
            planningDate = "2026-10-08",
            title = "阅读",
            startMinute = 60,
            endMinute = 90,
            estimatedMinutes = 30,
            note = "原备注",
            targetId = target.id,
        )
        val state = AttentionState(targets = listOf(target)).addSchedule(original)
        val edited = state.updateSchedule(
            original.copy(
                planningDate = "2026-10-09",
                title = "复习",
                startMinute = 120,
                endMinute = 180,
                estimatedMinutes = 60,
                note = "新备注",
            ),
        )

        assertEquals("schedule-1", edited.schedules.single().id)
        assertEquals("2026-10-09", edited.schedules.single().planningDate)
        assertEquals("新备注", edited.schedules.single().note)
        assertThrows(java.time.format.DateTimeParseException::class.java) {
            state.updateSchedule(original.copy(planningDate = "2026-02-30"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            state.updateSchedule(original.copy(startMinute = 100, endMinute = 90))
        }
        assertThrows(IllegalArgumentException::class.java) {
            state.updateSchedule(original.copy(startMinute = 100, endMinute = null))
        }
        assertThrows(IllegalArgumentException::class.java) {
            state.updateSchedule(original.copy(startMinute = null, endMinute = 90))
        }
        assertThrows(IllegalArgumentException::class.java) {
            state.updateSchedule(original.copy(estimatedMinutes = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            state.updateSchedule(original.copy(title = "   "))
        }
    }

    @Test
    fun toggling_schedule_completion_changes_only_completion_state() {
        val target = AttentionState().addTarget("学习").targets.single()
        val schedule = ScheduleEntry(
            id = "completion-schedule",
            planningDate = "2026-10-08",
            title = "阅读",
            startMinute = 60,
            endMinute = 90,
            estimatedMinutes = 30,
            note = "章节 2",
            targetId = target.id,
            reminderEpochMillis = 123L,
            recurrenceRuleId = "rule",
        )
        val timeEntry = TimeEntry("actual-entry", "2026-10-08", 20, target.id)
        val state = AttentionState(
            targets = listOf(target),
            schedules = listOf(schedule),
            timeEntries = listOf(timeEntry),
            experience = 500L,
            milestones = listOf(Milestone("milestone", "goal_period", "period", 500L, 1L)),
        )

        val completed = state.toggleScheduleCompleted(schedule.id)

        assertEquals(schedule.copy(completed = true), completed.schedules.single())
        assertEquals(state.timeEntries, completed.timeEntries)
        assertEquals(state.experience, completed.experience)
        assertEquals(state.milestones, completed.milestones)
        assertFalse(completed.toggleScheduleCompleted(schedule.id).schedules.single().completed)
    }

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
    fun migrations_are_limited_by_current_gap_and_source_period_boundary() {
        val targetState = AttentionState().addTarget("英语")
        val target = targetState.targets.single()
        val withStage = targetState.addGoalStage(
            target.id,
            GoalCadence.WEEKLY,
            100,
            LocalDate.of(2026, 10, 5).toString(),
        )
        val stage = withStage.goalStages.single()
        val current = withStage.addTimeEntry(date.toString(), 40, target.id)
        val migration = Migration(
            id = "migration-1",
            targetId = target.id,
            sourceStageId = stage.id,
            minutes = 30,
            destinationStartDate = "2026-10-12",
        )

        val migrated = current.addMigration(migration, date)

        assertEquals(30, migrated.availableMigrationMinutes(stage, date))
        assertThrows(IllegalArgumentException::class.java) {
            migrated.addMigration(migration.copy(id = "migration-2", minutes = 31), date)
        }
        assertThrows(IllegalArgumentException::class.java) {
            current.addMigration(migration.copy(destinationStartDate = "2026-10-11"), date)
        }
    }

    @Test
    fun cancelling_a_migration_releases_capacity_without_changing_time_or_experience() {
        val targetState = AttentionState().addTarget("英语")
        val target = targetState.targets.single()
        val withStage = targetState.addGoalStage(
            target.id,
            GoalCadence.WEEKLY,
            100,
            LocalDate.of(2026, 10, 5).toString(),
        )
        val stage = withStage.goalStages.single()
        val current = withStage.addTimeEntry(date.toString(), 40, target.id)
        val migration = Migration("migration-1", target.id, stage.id, 30, "2026-10-12")
        val migrated = current.addMigration(migration, date)
        val cancelled = migrated.cancelMigration(migration.id)

        assertEquals(60, cancelled.availableMigrationMinutes(stage, date))
        assertEquals(40, cancelled.activeTargetMinutes(date.toString()))
        assertEquals(migrated.experience, cancelled.experience)
        assertTrue(cancelled.migrations.single().cancelled)
        assertThrows(IllegalArgumentException::class.java) {
            cancelled.updateMigration(migration.copy(minutes = 20), date)
        }
    }

    @Test
    fun parent_and_child_goals_roll_up_arbitrary_depth_once_across_sources() {
        val rootState = AttentionState().addTarget("根目标")
        val root = rootState.targets.single()
        val childState = rootState.addTarget("子目标", root.id)
        val child = childState.targets.single { it.title == "子目标" }
        val tree = childState.addTarget("孙目标", child.id)
        val grandchild = tree.targets.single { it.title == "孙目标" }
        val withGoals = tree
            .addGoalStage(root.id, GoalCadence.DAILY, 50, date.toString())
            .addGoalStage(child.id, GoalCadence.WEEKLY, 20, date.toString())
            .addGoalStage(grandchild.id, GoalCadence.MONTHLY, 10, date.withDayOfMonth(1).toString())
        val records = withGoals
            .addTimeEntry(date.toString(), 20, root.id, TimeEntrySource.MANUAL)
            .addTimeEntry(date.toString(), 15, child.id, TimeEntrySource.TIMER)
            .addTimeEntry(date.toString(), 10, grandchild.id, TimeEntrySource.IMPORT)

        val rootProgress = records.progress(records.goalStages.single { it.targetId == root.id }, date)
        val childProgress = records.progress(records.goalStages.single { it.targetId == child.id }, date)
        val grandchildProgress = records.progress(records.goalStages.single { it.targetId == grandchild.id }, date)

        assertEquals(45, rootProgress.actualMinutes)
        assertEquals(5, rootProgress.gapMinutes)
        assertFalse(rootProgress.completed)
        assertEquals(25, childProgress.actualMinutes)
        assertEquals(5, childProgress.excessMinutes)
        assertTrue(childProgress.completed)
        assertEquals(10, grandchildProgress.actualMinutes)
        assertTrue(grandchildProgress.completed)
    }

    @Test
    fun editing_deleting_and_batch_assignment_refresh_parent_and_child_progress() {
        val rootState = AttentionState().addTarget("根目标")
        val root = rootState.targets.single()
        val withChild = rootState.addTarget("子目标", root.id)
        val child = withChild.targets.single { it.title == "子目标" }
        val withGoals = withChild
            .addGoalStage(root.id, GoalCadence.DAILY, 40, date.toString())
            .addGoalStage(child.id, GoalCadence.DAILY, 20, date.toString())
        val withRecords = withGoals
            .addTimeEntry(date.toString(), 10, root.id)
            .addTimeEntry(date.toString(), 15, child.id)
            .addTimeEntry(date.toString(), 5)
        val childEntry = withRecords.timeEntries.single { it.targetId == child.id }
        val unownedEntry = withRecords.timeEntries.single { it.targetId == null }

        val edited = withRecords.editTimeEntry(childEntry.id, 30, child.id)
        assertEquals(40, edited.progress(edited.goalStages.single { it.targetId == root.id }, date).actualMinutes)
        assertEquals(30, edited.progress(edited.goalStages.single { it.targetId == child.id }, date).actualMinutes)
        assertEquals(10, edited.progress(edited.goalStages.single { it.targetId == child.id }, date).excessMinutes)

        val reassigned = edited.assignUnowned(setOf(unownedEntry.id), child.id)
        assertEquals(45, reassigned.progress(reassigned.goalStages.single { it.targetId == root.id }, date).actualMinutes)
        assertEquals(35, reassigned.progress(reassigned.goalStages.single { it.targetId == child.id }, date).actualMinutes)

        val deleted = reassigned.deleteTimeEntry(childEntry.id)
        val rootProgress = deleted.progress(deleted.goalStages.single { it.targetId == root.id }, date)
        val childProgress = deleted.progress(deleted.goalStages.single { it.targetId == child.id }, date)
        assertEquals(15, rootProgress.actualMinutes)
        assertEquals(25, rootProgress.gapMinutes)
        assertEquals(5, childProgress.actualMinutes)
        assertEquals(15, childProgress.gapMinutes)
    }

    @Test
    fun future_target_move_keeps_historical_goal_progress_with_the_original_parent() {
        val state = AttentionState().addTarget("旧父目标").addTarget("新父目标")
        val oldParent = state.targets.first()
        val newParent = state.targets.last()
        val withChild = state.addTarget("子计划", oldParent.id)
        val child = withChild.targets.single { it.title == "子计划" }
        val withGoals = withChild
            .addGoalStage(oldParent.id, GoalCadence.WEEKLY, 100, date.toString())
            .addGoalStage(newParent.id, GoalCadence.DAILY, 100, date.toString())
        val history = withGoals
            .addTimeEntry(date.toString(), 20, child.id)
            .addTimeEntry(date.plusDays(2).toString(), 15, child.id)
            .addFutureTargetMove(child.id, newParent.id, date.plusDays(1).toString(), date)
        val oldStage = history.goalStages.single { it.targetId == oldParent.id }
        val newStage = history.goalStages.single { it.targetId == newParent.id }

        assertEquals(20, history.progress(oldStage, date.plusDays(2)).actualMinutes)
        assertEquals(15, history.progress(newStage, date.plusDays(2)).actualMinutes)
        assertEquals(0, history.progress(newStage, date).actualMinutes)
    }

    @Test
    fun archived_child_keeps_parent_and_child_history_but_rejects_new_assignment() {
        val rootState = AttentionState().addTarget("根目标")
        val root = rootState.targets.single()
        val withChild = rootState.addTarget("子目标", root.id)
        val child = withChild.targets.single { it.title == "子目标" }
        val withGoals = withChild
            .addGoalStage(root.id, GoalCadence.DAILY, 30, date.toString())
            .addGoalStage(child.id, GoalCadence.DAILY, 20, date.toString())
        val withHistory = withGoals.addTimeEntry(date.toString(), 20, child.id)
        val archived = withHistory.archiveTarget(child.id)
        val childStage = archived.goalStages.single { it.targetId == child.id }
        val rootStage = archived.goalStages.single { it.targetId == root.id }

        assertEquals(20, archived.progress(childStage, date).actualMinutes)
        assertEquals(20, archived.progress(rootStage, date).actualMinutes)
        assertThrows(IllegalArgumentException::class.java) {
            archived.addTimeEntry(date.toString(), 5, child.id)
        }
        val unowned = archived.addTimeEntry(date.toString(), 5).timeEntries.last()
        assertThrows(IllegalArgumentException::class.java) {
            archived.assignUnowned(setOf(unowned.id), child.id)
        }
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
    fun one_time_goal_includes_start_and_due_dates_but_excludes_late_records() {
        val state = AttentionState().addTarget("考试准备")
        val target = state.targets.single()
        val withGoal = state.addGoalStage(
            target.id,
            GoalCadence.ONE_TIME,
            60,
            "2026-10-10",
            dueDate = "2026-10-12",
        )
        val stage = withGoal.goalStages.single()
        val records = withGoal
            .addTimeEntry("2026-10-09", 100, target.id)
            .addTimeEntry("2026-10-10", 20, target.id)
            .addTimeEntry("2026-10-12", 45, target.id)
            .addTimeEntry("2026-10-13", 90, target.id)

        val before = records.progress(stage, LocalDate.of(2026, 10, 9))
        val due = records.progress(stage, LocalDate.of(2026, 10, 12))
        val after = records.progress(stage, LocalDate.of(2026, 10, 13))

        assertEquals(0, before.actualMinutes)
        assertEquals(0, before.targetMinutes)
        assertEquals(0, before.gapMinutes)
        assertFalse(before.completed)
        assertEquals(LocalDate.of(2026, 10, 10), records.progressRange(stage, LocalDate.of(2026, 10, 10)).start)
        assertEquals(LocalDate.of(2026, 10, 10), records.progressRange(stage, LocalDate.of(2026, 10, 10)).endInclusive)
        assertEquals(65, due.actualMinutes)
        assertEquals(0, due.gapMinutes)
        assertEquals(5, due.excessMinutes)
        assertTrue(due.completed)
        assertEquals(65, after.actualMinutes)
        assertEquals(5, after.excessMinutes)
        assertTrue(after.completed)
        assertEquals(4, records.timeEntries.size)
    }

    @Test
    fun open_one_time_goal_uses_query_date_as_the_right_boundary() {
        val state = AttentionState().addTarget("项目")
        val target = state.targets.single()
        val stage = state.addGoalStage(target.id, GoalCadence.ONE_TIME, 60, "2026-10-10").goalStages.single()
        val records = state
            .addTimeEntry("2026-10-09", 100, target.id)
            .addTimeEntry("2026-10-10", 20, target.id)
            .addTimeEntry("2026-10-12", 40, target.id)

        val firstDay = records.progress(stage, LocalDate.of(2026, 10, 10))
        val later = records.progress(stage, LocalDate.of(2026, 10, 12))

        assertEquals(20, firstDay.actualMinutes)
        assertEquals(40, firstDay.gapMinutes)
        assertEquals(LocalDate.of(2026, 10, 10), records.progressRange(stage, LocalDate.of(2026, 10, 10)).endInclusive)
        assertEquals(60, later.actualMinutes)
        assertEquals(0, later.gapMinutes)
        assertTrue(later.completed)
        assertEquals(null, stage.dueDate)
    }

    @Test
    fun one_time_stages_use_inclusive_disjoint_ranges_and_ignore_the_gap() {
        val state = AttentionState().addTarget("项目")
        val target = state.targets.single()
        val first = state
            .addGoalStage(target.id, GoalCadence.ONE_TIME, 60, "2026-10-10", dueDate = "2026-10-12")
            .addTimeEntry("2026-10-10", 20, target.id, TimeEntrySource.MANUAL)
            .addTimeEntry("2026-10-12", 40, target.id, TimeEntrySource.TIMER)
        val second = first
            .appendOneTimeGoalStage(target.id, 90, "2026-10-15")
            .addTimeEntry("2026-10-13", 100, target.id, TimeEntrySource.IMPORT)
            .addTimeEntry("2026-10-14", 100, target.id, TimeEntrySource.MANUAL)
            .addTimeEntry("2026-10-15", 40, target.id, TimeEntrySource.TIMER)
            .addTimeEntry("2026-10-16", 50, target.id, TimeEntrySource.IMPORT)
        val stages = second.goalStagesForTarget(target.id)

        assertEquals(LocalDate.of(2026, 10, 10), second.progressRange(stages[0], LocalDate.of(2026, 10, 20)).start)
        assertEquals(LocalDate.of(2026, 10, 12), second.progressRange(stages[0], LocalDate.of(2026, 10, 20)).endInclusive)
        assertEquals(LocalDate.of(2026, 10, 15), second.progressRange(stages[1], LocalDate.of(2026, 10, 16)).start)
        assertEquals(LocalDate.of(2026, 10, 16), second.progressRange(stages[1], LocalDate.of(2026, 10, 16)).endInclusive)

        val firstProgress = second.progress(stages[0], LocalDate.of(2026, 10, 20))
        assertEquals(60, firstProgress.actualMinutes)
        assertEquals(0, firstProgress.gapMinutes)
        assertEquals(0, firstProgress.excessMinutes)
        assertTrue(firstProgress.completed)

        val secondProgress = second.progress(stages[1], LocalDate.of(2026, 10, 16))
        assertEquals(90, secondProgress.actualMinutes)
        assertEquals(0, secondProgress.gapMinutes)
        assertEquals(0, secondProgress.excessMinutes)
        assertTrue(secondProgress.completed)
    }

    @Test
    fun open_one_time_stage_closes_on_the_day_before_the_next_stage() {
        val state = AttentionState().addTarget("项目")
        val target = state.targets.single()
        val first = state
            .addGoalStage(target.id, GoalCadence.ONE_TIME, 30, "2026-10-10")
            .addTimeEntry("2026-10-10", 30, target.id)
        val second = first
            .appendOneTimeGoalStage(target.id, 45, "2026-10-15")
            .addTimeEntry("2026-10-14", 15, target.id)
            .addTimeEntry("2026-10-15", 20, target.id)
        val stages = second.goalStagesForTarget(target.id)

        assertEquals(LocalDate.of(2026, 10, 14), second.progressRange(stages[0], LocalDate.of(2026, 10, 20)).endInclusive)
        val firstProgress = second.progress(stages[0], LocalDate.of(2026, 10, 20))
        assertEquals(45, firstProgress.actualMinutes)
        assertEquals(15, firstProgress.excessMinutes)
        assertTrue(firstProgress.completed)
        assertEquals(20, second.progress(stages[1], LocalDate.of(2026, 10, 15)).actualMinutes)
    }

    @Test
    fun one_time_stage_before_start_has_zero_progress_and_is_not_completed() {
        val state = AttentionState().addTarget("项目")
        val target = state.targets.single()
        val stage = state.addGoalStage(target.id, GoalCadence.ONE_TIME, 60, "2026-10-15").goalStages.single()

        val progress = state.progress(stage, LocalDate.of(2026, 10, 14))

        assertEquals(0, progress.actualMinutes)
        assertEquals(0, progress.gapMinutes)
        assertEquals(0, progress.excessMinutes)
        assertFalse(progress.completed)
    }

    @Test
    fun one_time_goal_summary_rolls_up_all_stages_and_deduplicates_hierarchy_entries() {
        val rootState = AttentionState().addTarget("root")
        val root = rootState.targets.single()
        val withChild = rootState.addTarget("child", root.id)
        val child = withChild.targets.single { it.parentId == root.id }
        val withGrandchild = withChild.addTarget("grandchild", child.id)
        val grandchild = withGrandchild.targets.single { it.parentId == child.id }
        val first = withGrandchild
            .addGoalStage(root.id, GoalCadence.ONE_TIME, 60, "2026-10-01", dueDate = "2026-10-04")
            .addTimeEntry("2026-10-01", 20, root.id)
            .addTimeEntry("2026-10-02", 15, child.id)
            .addTimeEntry("2026-10-04", 25, grandchild.id)
        val withSecond = first.appendOneTimeGoalStage(root.id, 90, "2026-10-06")
            .addTimeEntry("2026-10-06", 40, grandchild.id)
            .addTimeEntry("2026-10-07", 50, child.id)
        val state = withSecond.addGoalStage(child.id, GoalCadence.ONE_TIME, 30, "2026-10-01")

        val rootSummary = state.goalSummary(root.id, LocalDate.of(2026, 10, 10))
        assertEquals(150, rootSummary.targetMinutes)
        assertEquals(150, rootSummary.actualMinutes)
        assertEquals(0, rootSummary.gapMinutes)
        assertEquals(0, rootSummary.excessMinutes)
        assertTrue(rootSummary.completed)
        assertEquals(listOf(60, 90), rootSummary.stageProgresses.map { it.progress.actualMinutes })
        assertEquals(listOf(60, 90), rootSummary.stageProgresses.map { it.progress.targetMinutes })

        val childSummary = state.goalSummary(child.id, LocalDate.of(2026, 10, 10))
        assertEquals(30, childSummary.targetMinutes)
        assertEquals(130, childSummary.actualMinutes)
        assertEquals(100, childSummary.excessMinutes)
        assertTrue(childSummary.completed)
    }

    @Test
    fun one_time_goal_summary_updates_after_edit_delete_and_assignment() {
        val initial = AttentionState().addTarget("target")
        val target = initial.targets.single()
        val withStage = initial.addGoalStage(target.id, GoalCadence.ONE_TIME, 60, "2026-10-01")
        val unowned = withStage.addTimeEntry("2026-10-01", 20)
        assertEquals(0, unowned.goalSummary(target.id, LocalDate.of(2026, 10, 2)).actualMinutes)

        val entryId = unowned.timeEntries.single().id
        val assigned = unowned.assignUnowned(setOf(entryId), target.id)
        assertEquals(20, assigned.goalSummary(target.id, LocalDate.of(2026, 10, 2)).actualMinutes)

        val edited = assigned.editTimeEntry(entryId, 45, target.id, "corrected")
        assertEquals(45, edited.goalSummary(target.id, LocalDate.of(2026, 10, 2)).actualMinutes)

        val deleted = edited.deleteTimeEntry(entryId)
        assertEquals(0, deleted.goalSummary(target.id, LocalDate.of(2026, 10, 2)).actualMinutes)
    }

    @Test
    fun one_time_goal_summary_uses_the_target_tree_at_each_entry_date_after_a_move() {
        val state = AttentionState().addTarget("old parent").addTarget("new parent")
        val oldParent = state.targets.first()
        val newParent = state.targets.last()
        val withChild = state.addTarget("child", oldParent.id)
        val child = withChild.targets.single { it.parentId == oldParent.id }
        val withStages = withChild
            .addGoalStage(oldParent.id, GoalCadence.ONE_TIME, 20, "2026-10-01")
            .addGoalStage(newParent.id, GoalCadence.ONE_TIME, 15, "2026-10-01")
        val moved = withStages
            .addTimeEntry("2026-10-01", 20, child.id)
            .addTimeEntry("2026-10-03", 15, child.id)
            .addFutureTargetMove(child.id, newParent.id, "2026-10-02", LocalDate.parse("2026-10-01"))

        assertEquals(20, moved.goalSummary(oldParent.id, LocalDate.of(2026, 10, 3)).actualMinutes)
        assertEquals(15, moved.goalSummary(newParent.id, LocalDate.of(2026, 10, 3)).actualMinutes)
    }

    @Test
    fun one_time_goal_summary_preserves_stage_gaps_and_excess_when_the_stages_differ() {
        val initial = AttentionState().addTarget("target")
        val target = initial.targets.single()
        val first = initial
            .addGoalStage(target.id, GoalCadence.ONE_TIME, 60, "2026-10-01")
            .addTimeEntry("2026-10-01", 150, target.id)
        val state = first.appendOneTimeGoalStage(target.id, 90, "2026-10-02")

        val summary = state.goalSummary(target.id, LocalDate.of(2026, 10, 2))
        assertEquals(150, summary.targetMinutes)
        assertEquals(150, summary.actualMinutes)
        assertEquals(90, summary.stageProgresses[1].progress.gapMinutes)
        assertEquals(90, summary.stageProgresses[0].progress.excessMinutes)
        assertEquals(90, summary.gapMinutes)
        assertEquals(90, summary.excessMinutes)
        assertFalse(summary.completed)
    }

    @Test
    fun appending_one_time_stage_preserves_history_and_assigns_a_new_stable_id() {
        val initial = AttentionState().addTarget("项目")
        val target = initial.targets.single()
        val first = initial
            .addGoalStage(target.id, GoalCadence.ONE_TIME, 60, "2026-10-10", "2026-10-12")
            .addTimeEntry("2026-10-11", 60, target.id)
        val previous = first.goalStages.single()

        val appended = first.appendOneTimeGoalStage(
            targetId = target.id,
            targetMinutes = 90,
            startDate = "2026-10-13",
        )

        assertEquals(listOf(previous), appended.goalStagesForTarget(target.id).take(1))
        assertEquals(previous, appended.goalStagesForTarget(target.id).first())
        assertEquals(2, appended.goalStagesForTarget(target.id).size)
        assertTrue(appended.goalStagesForTarget(target.id).last().id != previous.id)
        assertEquals(90, appended.goalStagesForTarget(target.id).last().targetMinutes)
        assertEquals("2026-10-13", appended.goalStagesForTarget(target.id).last().startDate)
        assertEquals(null, appended.goalStagesForTarget(target.id).last().dueDate)
        assertEquals(first.timeEntries, appended.timeEntries)
    }

    @Test
    fun appending_one_time_stage_rejects_invalid_state_without_mutation() {
        val initial = AttentionState().addTarget("项目")
        val target = initial.targets.single()
        val initialBefore = initial
        assertThrows(IllegalArgumentException::class.java) {
            initial.appendOneTimeGoalStage(target.id, 30, "2026-10-11")
        }
        assertThrows(IllegalArgumentException::class.java) {
            initial.appendOneTimeGoalStage("missing", 30, "2026-10-11")
        }
        assertEquals(initialBefore, initial)

        val oneTime = initial.addGoalStage(target.id, GoalCadence.ONE_TIME, 60, "2026-10-10")
        val incomplete = oneTime
        val before = incomplete

        assertThrows(IllegalArgumentException::class.java) {
            incomplete.appendOneTimeGoalStage(target.id, 30, "2026-10-11")
        }
        assertEquals(before, incomplete)

        val completed = oneTime.addTimeEntry("2026-10-10", 60, target.id)
        val completedBefore = completed
        assertThrows(IllegalArgumentException::class.java) {
            completed.appendOneTimeGoalStage(target.id, 0, "2026-10-11")
        }
        assertThrows(IllegalArgumentException::class.java) {
            completed.appendOneTimeGoalStage(target.id, 30, "2026-10-11", dueDate = "2026-10-10")
        }
        assertThrows(IllegalArgumentException::class.java) {
            completed.appendOneTimeGoalStage(target.id, 30, "2026-10-10")
        }
        assertEquals(completedBefore, completed)

        assertThrows(IllegalArgumentException::class.java) {
            completed.addGoalStage(target.id, GoalCadence.ONE_TIME, 30, "2026-10-11")
        }
        assertEquals(completedBefore, completed)

        val weekly = initial.addGoalStage(target.id, GoalCadence.WEEKLY, 30, "2026-10-01")
        val nonOneTimeAndOneTime = weekly.copy(
            goalStages = weekly.goalStages + GoalStage(
                targetId = target.id,
                cadence = GoalCadence.ONE_TIME,
                targetMinutes = 30,
                startDate = "2026-10-10",
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            nonOneTimeAndOneTime.appendOneTimeGoalStage(target.id, 30, "2026-10-11")
        }

        val archived = completed.archiveTarget(target.id)
        val archivedBefore = archived
        assertThrows(IllegalArgumentException::class.java) {
            archived.appendOneTimeGoalStage(target.id, 30, "2026-10-11")
        }
        assertEquals(archivedBefore, archived)

        val periodic = initial.addGoalStage(target.id, GoalCadence.WEEKLY, 60, "2026-10-10")
        val periodicBefore = periodic
        assertThrows(IllegalArgumentException::class.java) {
            periodic.appendOneTimeGoalStage(target.id, 30, "2026-10-11")
        }
        assertEquals(periodicBefore, periodic)
    }

    @Test
    fun one_time_goal_rejects_a_due_date_before_start_without_changing_state() {
        val state = AttentionState().addTarget("论文")
        val target = state.targets.single()

        assertThrows(IllegalArgumentException::class.java) {
            state.addGoalStage(target.id, GoalCadence.ONE_TIME, 60, "2026-10-10", dueDate = "2026-10-09")
        }
        assertEquals(emptyList<GoalStage>(), state.goalStages)
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
    fun recurrence_materialization_supports_daily_weekly_and_monthly_occurrences() {
        val daily = AttentionState().addRecurrence(RecurrenceRule("d", "每日", date.toString(), ScheduleFrequency.DAILY))
        assertEquals(3, daily.occurrences(daily.recurrenceRules.single(), date.plusDays(2)).size)
        val weekly = AttentionState().addRecurrence(RecurrenceRule("w", "周一三", date.toString(), ScheduleFrequency.WEEKLY, weekdays = listOf(1, 3)))
        assertEquals(listOf(1, 3), weekly.occurrences(weekly.recurrenceRules.single(), date.plusDays(7)).map { LocalDate.parse(it.planningDate).dayOfWeek.value })
        val monthly = AttentionState().addRecurrence(RecurrenceRule("m", "月度", date.withDayOfMonth(1).toString(), ScheduleFrequency.MONTHLY, dayOfMonth = 8))
        assertEquals(listOf("2026-10-08", "2026-11-08"), monthly.occurrences(monthly.recurrenceRules.single(), LocalDate.of(2026, 11, 30)).map { it.planningDate })
    }

    @Test
    fun recurrence_materialization_is_idempotent_and_occurrences_edit_independently() {
        val rule = RecurrenceRule("r", "复盘", date.toString(), ScheduleFrequency.DAILY)
        val materialized = AttentionState().addRecurrenceAndMaterialize(rule, date.plusDays(2))
        val retried = materialized.materializeOccurrences(rule, date.plusDays(2))
        assertEquals(3, retried.schedules.size)
        val first = retried.schedules.first()
        val edited = retried.updateSchedule(first.copy(title = "已改", completed = true))
        assertEquals("已改", edited.schedules.first { it.id == first.id }.title)
        assertTrue(edited.schedules.first { it.id == first.id }.completed)
        assertTrue(edited.schedules.drop(1).all { it.title == "复盘" && !it.completed })
        assertEquals(3, edited.schedules.map { it.id }.toSet().size)
    }

    @Test
    fun stopped_rule_rejects_generation_from_a_stale_rule_but_keeps_existing_occurrences() {
        val rule = RecurrenceRule("stale", "复盘", date.toString(), ScheduleFrequency.DAILY)
        val materialized = AttentionState().addRecurrenceAndMaterialize(rule, date.plusDays(1))
        val stopped = materialized.stopRecurrence(rule.id)
        val retried = stopped.materializeOccurrences(rule, date.plusDays(4))

        assertFalse(retried.recurrenceRules.single().active)
        assertEquals(listOf(date.toString(), date.plusDays(1).toString()), retried.schedules.map { it.planningDate })
        assertEquals(stopped.schedules, retried.schedules)
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
    fun recurring_reminder_uses_each_occurrence_planning_date() {
        val rule = RecurrenceRule("reminders", "提醒", date.toString(), ScheduleFrequency.DAILY, reminderMinuteOfDay = 9 * 60)
        val occurrences = AttentionState().occurrences(rule, date.plusDays(1))
        assertEquals(2, occurrences.size)
        assertEquals(24 * 60 * 60 * 1000L, occurrences[1].reminderEpochMillis!! - occurrences[0].reminderEpochMillis!!)
    }

    @Test
    fun recurring_reminder_uses_the_planning_zone_at_each_occurrence() {
        val rule = RecurrenceRule(
            id = "zone-rule",
            title = "跨时区提醒",
            startDate = "2026-10-11",
            frequency = ScheduleFrequency.DAILY,
            reminderMinuteOfDay = 90,
        )

        val occurrence = AttentionState().occurrences(
            rule,
            LocalDate.parse("2026-10-11"),
            ZoneId.of("UTC+08:00"),
        ).single()

        assertEquals(
            LocalDate.parse("2026-10-11")
                .atStartOfDay(ZoneId.of("UTC+08:00"))
                .plusMinutes(90)
                .toInstant()
                .toEpochMilli(),
            occurrence.reminderEpochMillis,
        )
    }

    @Test
    fun recurrence_rule_accepts_daily_weekly_and_monthly_parameters() {
        val target = AttentionState().addTarget("学习").targets.single()

        val daily = AttentionState(targets = listOf(target)).addRecurrence(
            RecurrenceRule(
                id = "daily-rule",
                title = "每日复习",
                startDate = date.toString(),
                frequency = ScheduleFrequency.DAILY,
                estimatedMinutes = 25,
                targetId = target.id,
                reminderMinuteOfDay = 9 * 60,
            ),
        ).recurrenceRules.single()
        assertEquals("每日复习", daily.title)

        val weekly = AttentionState(targets = listOf(target)).addRecurrence(
            RecurrenceRule(
                id = "weekly-rule",
                title = "工作日复盘",
                startDate = date.toString(),
                frequency = ScheduleFrequency.WEEKLY,
                weekdays = listOf(1, 3, 5),
                untilDate = date.plusMonths(1).toString(),
            ),
        ).recurrenceRules.single()
        assertEquals(listOf(1, 3, 5), weekly.weekdays)

        val monthly = AttentionState(targets = listOf(target)).addRecurrence(
            RecurrenceRule(
                id = "monthly-rule",
                title = "月度复盘",
                startDate = date.toString(),
                frequency = ScheduleFrequency.MONTHLY,
                dayOfMonth = 31,
            ),
        ).recurrenceRules.single()
        assertEquals(31, monthly.dayOfMonth)
    }

    @Test
    fun recurrence_rule_rejects_invalid_parameters_and_archived_targets() {
        val target = AttentionState().addTarget("学习").targets.single()
        val archived = Target("archived-target", title = "已归档", archived = true)
        val state = AttentionState(targets = listOf(target, archived))

        fun assertInvalid(rule: RecurrenceRule) {
            assertThrows(IllegalArgumentException::class.java) { state.addRecurrence(rule) }
        }

        assertInvalid(RecurrenceRule(title = "", startDate = date.toString(), frequency = ScheduleFrequency.DAILY))
        assertInvalid(RecurrenceRule(title = "每日", startDate = date.toString(), frequency = ScheduleFrequency.DAILY, weekdays = listOf(1)))
        assertInvalid(RecurrenceRule(title = "每周", startDate = date.toString(), frequency = ScheduleFrequency.WEEKLY, weekdays = emptyList()))
        val duplicateWeekdays = state.addRecurrence(
            RecurrenceRule(title = "每周", startDate = date.toString(), frequency = ScheduleFrequency.WEEKLY, weekdays = listOf(1, 1, 3)),
        ).recurrenceRules.single()
        assertEquals(listOf(1, 3), duplicateWeekdays.weekdays)
        assertInvalid(RecurrenceRule(title = "每周", startDate = date.toString(), frequency = ScheduleFrequency.WEEKLY, weekdays = listOf(0)))
        assertInvalid(RecurrenceRule(title = "每月", startDate = date.toString(), frequency = ScheduleFrequency.MONTHLY, dayOfMonth = null))
        assertInvalid(RecurrenceRule(title = "每月", startDate = date.toString(), frequency = ScheduleFrequency.MONTHLY, dayOfMonth = 32))
        assertInvalid(RecurrenceRule(title = "范围", startDate = date.toString(), frequency = ScheduleFrequency.DAILY, untilDate = date.minusDays(1).toString()))
        assertInvalid(RecurrenceRule(title = "时长", startDate = date.toString(), frequency = ScheduleFrequency.DAILY, estimatedMinutes = 0))
        assertInvalid(RecurrenceRule(title = "提醒", startDate = date.toString(), frequency = ScheduleFrequency.DAILY, reminderMinuteOfDay = 1440))
        assertInvalid(RecurrenceRule(title = "计划", startDate = date.toString(), frequency = ScheduleFrequency.DAILY, targetId = archived.id))
    }

    @Test
    fun recurrence_rule_ids_must_be_unique_and_dates_must_be_valid() {
        val first = RecurrenceRule(id = "same-rule", title = "第一次", startDate = date.toString(), frequency = ScheduleFrequency.DAILY)
        val state = AttentionState().addRecurrence(first)

        assertThrows(IllegalArgumentException::class.java) {
            state.addRecurrence(first.copy(title = "第二次"))
        }
        assertThrows(java.time.format.DateTimeParseException::class.java) {
            AttentionState().addRecurrence(first.copy(startDate = "2026-02-30"))
        }
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
    fun level_progress_reports_boundaries_and_remaining_experience() {
        assertEquals(LevelProgress(1, 0, 1000, 0, 1000), levelProgressForExperience(0))
        assertEquals(LevelProgress(1, 0, 1000, 999, 1), levelProgressForExperience(999))
        assertEquals(LevelProgress(2, 1000, 3000, 0, 2000), levelProgressForExperience(1000))
        assertEquals(LevelProgress(2, 1000, 3000, 1999, 1), levelProgressForExperience(2999))
        assertEquals(LevelProgress(3, 3000, 6000, 0, 3000), levelProgressForExperience(3000))
    }

    @Test
    fun level_progress_handles_negative_experience_and_multiple_thresholds() {
        assertEquals(LevelProgress(1, 0, 1000, 0, 1000), levelProgressForExperience(-1))
        val progress = levelProgressForExperience(10_000)
        assertEquals(5, progress.level)
        assertEquals(10_000, progress.currentLevelThreshold)
        assertEquals(15_000, progress.nextLevelThreshold)
        assertEquals(0, progress.progressExperience)
        assertEquals(5_000, progress.remainingExperience)
    }

    @Test
    fun future_rules_and_snapshots_preserve_previous_period() {
        val state = AttentionState().addTarget("项目")
        val target = state.targets.single()
        val stage = state.addGoalStage(target.id, GoalCadence.WEEKLY, 120, date.toString()).goalStages.single()
        val snapshot = state.addTimeEntry(date.toString(), 90, target.id)
            .snapshot(stage, date.minusDays(3), date.plusDays(3))
        val future = state.addFutureGoalRule(FutureGoalRule(targetId = target.id, cadence = GoalCadence.WEEKLY, targetMinutes = 180, effectiveFrom = date.plusDays(7).toString()), date)
            .addPeriodSnapshot(snapshot)

        assertEquals(180, future.goalRulesAt(target.id, date.plusDays(8)).single().targetMinutes)
        assertEquals(1, future.periodSnapshots.size)
        assertEquals(30, future.periodSnapshots.single().gapMinutes)
        val futureRule = future.futureGoalRules.single()
        assertTrue(futureRule.isPending(date))
        assertFalse(futureRule.isPending(date.plusDays(7)))
    }

    @Test
    fun settling_finished_periods_supports_all_cadences_and_is_idempotent() {
        val start = LocalDate.of(2026, 10, 1)
        val base = AttentionState()
            .addTarget("每日")
            .addTarget("每周")
            .addTarget("每月")
            .addTarget("一次性")
        val daily = base.targets[0]
        val weekly = base.targets[1]
        val monthly = base.targets[2]
        val oneTime = base.targets[3]
        val state = base
            .addGoalStage(daily.id, GoalCadence.DAILY, 60, start.toString())
            .addGoalStage(weekly.id, GoalCadence.WEEKLY, 120, start.toString())
            .addGoalStage(monthly.id, GoalCadence.MONTHLY, 300, start.toString())
            .addGoalStage(oneTime.id, GoalCadence.ONE_TIME, 90, start.toString(), start.plusDays(2).toString())
            .addTimeEntry(start.toString(), 75, daily.id, TimeEntrySource.TIMER, 123L, "计时")
            .addTimeEntry(start.plusDays(1).toString(), 90, oneTime.id)
        val asOf = LocalDate.of(2026, 11, 2)

        val settled = state.settlePeriodSnapshots(asOf)
        val dailySnapshot = settled.periodSnapshots.first { it.stageId == settled.goalStages.first { stage -> stage.targetId == daily.id }.id && it.periodStart == start.toString() }
        assertEquals(60, dailySnapshot.targetMinutes)
        assertEquals(75, dailySnapshot.actualMinutes)
        assertEquals(15, dailySnapshot.excessMinutes)
        assertTrue(dailySnapshot.completed)
        assertTrue(settled.periodSnapshots.any { it.targetId == weekly.id && it.cadence == GoalCadence.WEEKLY })
        assertTrue(settled.periodSnapshots.any { it.targetId == monthly.id && it.cadence == GoalCadence.MONTHLY })
        assertTrue(settled.periodSnapshots.any { it.targetId == oneTime.id && it.cadence == GoalCadence.ONE_TIME })
        assertEquals(settled.periodSnapshots.size, settled.settlePeriodSnapshots(asOf).periodSnapshots.size)
        assertEquals(state.timeEntries, settled.timeEntries)
        assertEquals(state.experience, settled.experience)
        assertEquals(state.milestones, settled.milestones)

        val changedFutureRule = settled.addFutureGoalRule(
            FutureGoalRule(targetId = daily.id, cadence = GoalCadence.MONTHLY, targetMinutes = 600, effectiveFrom = "2026-12-01"),
            asOf,
        )
        assertEquals(settled.periodSnapshots, changedFutureRule.periodSnapshots)
    }

    @Test
    fun snapshots_are_idempotent_per_stage_and_period() {
        val target = AttentionState().addTarget("阶段目标").targets.single()
        val withStages = AttentionState(targets = listOf(target))
            .addGoalStage(target.id, GoalCadence.WEEKLY, 60, date.minusDays(7).toString())
            .addGoalStage(target.id, GoalCadence.WEEKLY, 120, date.minusDays(7).toString())

        val settled = withStages.settlePeriodSnapshots(date)
        assertEquals(2, settled.periodSnapshots.size)
        assertEquals(2, settled.settlePeriodSnapshots(date).periodSnapshots.size)
        assertEquals(2, settled.periodSnapshots.map { it.stageId }.toSet().size)
    }

    @Test
    fun a_future_rule_does_not_rewrite_a_cycle_that_started_before_it() {
        val start = LocalDate.of(2026, 10, 1)
        val target = AttentionState().addTarget("跨周期规则").targets.single()
        val state = AttentionState(targets = listOf(target))
            .addGoalStage(target.id, GoalCadence.WEEKLY, 120, start.toString())
            .addTimeEntry(start.plusDays(1).toString(), 60, target.id)
            .addTimeEntry(start.plusDays(5).toString(), 30, target.id)
            .addFutureGoalRule(
                FutureGoalRule(
                    targetId = target.id,
                    cadence = GoalCadence.DAILY,
                    targetMinutes = 30,
                    effectiveFrom = start.plusDays(4).toString(),
                ),
                start,
            )

        val settled = state.settlePeriodSnapshots(start.plusDays(7))
        val snapshot = settled.periodSnapshots.first { it.cadence == GoalCadence.WEEKLY }
        assertEquals(GoalCadence.WEEKLY, snapshot.cadence)
        assertEquals(start.toString(), snapshot.periodStart)
        assertEquals(start.plusDays(3).toString(), snapshot.periodEnd)
        assertEquals(120, snapshot.targetMinutes)
        assertEquals(60, snapshot.actualMinutes)
        assertEquals(60, snapshot.gapMinutes)
    }

    @Test
    fun future_goal_rules_require_a_valid_future_commitment_and_are_selected_by_scope() {
        val state = AttentionState().addTarget("项目")
        val target = state.targets.single()
        val withStage = state.addGoalStage(target.id, GoalCadence.WEEKLY, 120, date.toString())
        val stage = withStage.goalStages.single()
        val effectiveFrom = date.plusDays(7)
        val targetRule = FutureGoalRule(
            targetId = target.id,
            cadence = GoalCadence.MONTHLY,
            targetMinutes = 180,
            effectiveFrom = effectiveFrom.toString(),
        )
        val stageRule = targetRule.copy(
            id = "stage-rule",
            stageId = stage.id,
            targetMinutes = 240,
        )

        val scheduled = withStage
            .addFutureGoalRule(targetRule, date)
            .addFutureGoalRule(stageRule, date)

        assertEquals(stageRule, scheduled.goalRuleAt(target.id, stage.id, effectiveFrom))
        assertEquals(240, scheduled.progress(stage, effectiveFrom).targetMinutes)
        assertThrows(IllegalArgumentException::class.java) {
            withStage.addFutureGoalRule(targetRule.copy(effectiveFrom = date.toString()), date)
        }
        assertThrows(IllegalArgumentException::class.java) {
            withStage.addFutureGoalRule(targetRule.copy(dueDate = date.plusDays(6).toString()), date)
        }
    }

    @Test
    fun future_goal_rule_updates_and_cancellation_preserve_pending_only_boundary() {
        val state = AttentionState().addTarget("项目")
        val target = state.targets.single()
        val rule = FutureGoalRule(
            targetId = target.id,
            cadence = GoalCadence.DAILY,
            targetMinutes = 30,
            effectiveFrom = date.plusDays(3).toString(),
        )
        val scheduled = state.addFutureGoalRule(rule, date)
        val updated = scheduled.updateFutureGoalRule(rule.copy(targetMinutes = 45), date)

        assertEquals(rule.id, updated.futureGoalRules.single().id)
        assertEquals(45, updated.futureGoalRules.single().targetMinutes)
        assertEquals(0, updated.cancelFutureGoalRule(rule.id, date).futureGoalRules.size)
        assertThrows(IllegalArgumentException::class.java) {
            updated.updateFutureGoalRule(rule.copy(targetMinutes = 60), date.plusDays(3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            updated.cancelFutureGoalRule(rule.id, date.plusDays(3))
        }
    }

    @Test
    fun future_goal_rules_reject_archived_targets_and_same_scope_dates() {
        val state = AttentionState().addTarget("项目")
        val target = state.targets.single()
        val rule = FutureGoalRule(
            targetId = target.id,
            cadence = GoalCadence.DAILY,
            targetMinutes = 30,
            effectiveFrom = date.plusDays(1).toString(),
        )
        assertThrows(IllegalArgumentException::class.java) {
            state.archiveTarget(target.id).addFutureGoalRule(rule, date)
        }
        val scheduled = state.addFutureGoalRule(rule, date)
        assertThrows(IllegalArgumentException::class.java) {
            scheduled.addFutureGoalRule(rule.copy(id = "another"), date)
        }
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
    fun actual_time_commands_recalculate_experience_without_parent_duplication() {
        val withRoot = AttentionState().addTarget("父目标")
        val root = withRoot.targets.single()
        val withChild = withRoot.addTarget("子目标", root.id)
        val child = withChild.targets.single { it.parentId == root.id }

        val recorded = withChild
            .addTimeEntry(date.toString(), 10, child.id, TimeEntrySource.TIMER)
            .addTimeEntry(date.toString(), 7, root.id, TimeEntrySource.IMPORT)

        assertEquals(17L, recorded.experience)
        assertEquals(17, recorded.subtreeMinutes(root.id))

        val childEntry = recorded.timeEntries.single { it.targetId == child.id }
        val edited = recorded.editTimeEntry(childEntry.id, 20, null)
        assertEquals(7L, edited.experience)

        val reassigned = edited.assignUnowned(setOf(childEntry.id), root.id)
        assertEquals(27L, reassigned.experience)

        val deleted = reassigned.deleteTimeEntry(childEntry.id)
        assertEquals(7L, deleted.experience)
    }

    @Test
    fun stopping_a_timer_recalculates_experience_from_its_actual_minutes() {
        val state = AttentionState().addTarget("计时目标")
        val target = state.targets.single()
        val stopped = state
            .startTimer(target.id, Instant.ofEpochMilli(0), utc)
            .stopTimer(Instant.ofEpochMilli(121_000L), utc)

        assertEquals(2, stopped.entries.single().durationMinutes)
        assertEquals(2L, stopped.state.experience)
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
    fun stage_streak_and_cumulative_rewards_are_added_to_experience() {
        val state = AttentionState().addTarget("长期投入")
        val target = state.targets.single()
        val withEntries = (0..99).fold(state) { current, offset ->
            current.addTimeEntry(date.plusDays(offset.toLong()).toString(), 60, target.id)
        }

        val awarded = withEntries.awardEligibleMilestones(date.plusDays(99))

        assertEquals(setOf("streak", "cumulative"), awarded.milestones.map { it.kind }.toSet())
        assertEquals(4, awarded.milestones.size)
        assertEquals(6_000L + 500L + 1_000L + 10_000L + 10_000L, awarded.experience)
    }

    @Test
    fun awarding_milestones_reconciles_experience_when_all_instances_are_existing() {
        val existing = Milestone("existing", "streak", "streak:target:7", 500L, 1L)
        val duplicate = existing.copy(id = "duplicate", achievedAtEpochMillis = 2L)
        val state = AttentionState(milestones = listOf(existing, duplicate), experience = 0L)

        val reconciled = state.awardEligibleMilestones(date)

        assertEquals(listOf(existing, duplicate), reconciled.milestones)
        assertEquals(500L, reconciled.experience)
    }

    @Test
    fun parent_and_child_milestones_share_base_minutes_without_double_counting_experience() {
        val withParent = AttentionState().addTarget("父目标")
        val parent = withParent.targets.single()
        val withChild = withParent.addTarget("子目标", parent.id)
        val child = withChild.targets.single { it.title == "子目标" }
        val state = withChild
            .addGoalStage(parent.id, GoalCadence.ONE_TIME, 20, date.toString())
            .addGoalStage(child.id, GoalCadence.ONE_TIME, 20, date.toString())
            .addTimeEntry(date.toString(), 20, child.id)

        val awarded = state.awardEligibleMilestones(date)

        assertEquals(2, awarded.milestones.count { it.kind == "one_time_goal" })
        assertEquals(20L + 20_000L, awarded.experience)
    }

    @Test
    fun awarded_milestone_survives_time_correction_and_target_relation_removal() {
        val state = AttentionState().addTarget("可更正目标")
        val target = state.targets.single()
        val awarded = state
            .addGoalStage(target.id, GoalCadence.ONE_TIME, 30, date.toString())
            .addTimeEntry(date.toString(), 30, target.id)
            .awardEligibleMilestones(date)
        val entryId = awarded.timeEntries.single().id

        val corrected = awarded.editTimeEntry(entryId, 5, null)
        assertEquals(10_000L, corrected.experience)
        assertEquals(1, corrected.milestones.size)

        val removed = awarded.deleteTargetRelations(setOf(target.id))
        assertTrue(removed.timeEntries.single().targetId == null)
        assertEquals(10_000L, removed.experience)
        assertEquals(1, removed.milestones.size)
    }

    @Test
    fun recent_milestones_are_sorted_and_limited_without_duplicates() {
        val state = AttentionState(
            milestones = listOf(
                Milestone("old", "streak", "streak:target:7", 500L, 10L),
                Milestone("newer", "cumulative", "cumulative:100", 10_000L, 30L),
                Milestone("middle", "goal_period", "goal_period:stage:2026-10-08", 500L, 20L),
                Milestone("duplicate", "streak", "streak:target:7", 500L, 40L),
            ),
        )

        assertEquals(listOf("middle", "newer"), state.recentMilestones(2).map { it.id })
        assertEquals(3, state.recentMilestones(8).size)
    }

    @Test
    fun one_time_stage_feedback_is_distinct_idempotent_and_preserved_after_append() {
        val state = AttentionState().addTarget("阶段目标")
        val target = state.targets.single()
        val firstStage = state
            .addGoalStage(target.id, GoalCadence.ONE_TIME, 30, date.toString())
            .addTimeEntry(date.toString(), 30, target.id)
            .awardEligibleMilestones(date)

        val firstFeedback = firstStage.oneTimeStageMilestones(firstStage.goalStages.single().id)
        assertEquals(1, firstFeedback.size)
        assertEquals(10_000L, firstFeedback.single().reward)

        val secondStage = firstStage
            .appendOneTimeGoalStage(target.id, 45, date.plusDays(1).toString())
            .addTimeEntry(date.plusDays(1).toString(), 45, target.id)
        val awarded = secondStage.awardEligibleMilestones(date.plusDays(1))
        val stageIds = awarded.goalStages.map { it.id }

        assertEquals(2, awarded.milestones.count { it.kind == "one_time_goal" })
        assertEquals(1, awarded.oneTimeStageMilestones(stageIds[0]).size)
        assertEquals(1, awarded.oneTimeStageMilestones(stageIds[1]).size)
        assertEquals(2, awarded.awardEligibleMilestones(date.plusDays(1)).milestones.count { it.kind == "one_time_goal" })
    }

    @Test
    fun one_time_stage_feedback_follows_stage_identity_across_parent_and_child_goals() {
        val withParent = AttentionState().addTarget("父目标")
        val parent = withParent.targets.single()
        val withChild = withParent.addTarget("子目标", parent.id)
        val child = withChild.targets.single { it.title == "子目标" }
        val state = withChild
            .addGoalStage(parent.id, GoalCadence.ONE_TIME, 20, date.toString())
            .addGoalStage(child.id, GoalCadence.ONE_TIME, 20, date.toString())
            .addTimeEntry(date.toString(), 20, child.id)

        val awarded = state.awardEligibleMilestones(date)

        assertEquals(2, awarded.milestones.count { it.kind == "one_time_goal" })
        assertEquals(1, awarded.oneTimeStageMilestones(awarded.goalStages.single { it.targetId == parent.id }.id).size)
        assertEquals(1, awarded.oneTimeStageMilestones(awarded.goalStages.single { it.targetId == child.id }.id).size)
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
        val stageState = moved
            .addGoalStage(child.id, GoalCadence.WEEKLY, 60, date.toString())
            .addTimeEntry(date.toString(), 20, child.id)
            .addTimeEntry(date.plusDays(2).toString(), 15, child.id, TimeEntrySource.TIMER, 123L, "计时")
        val snapshot = stageState.snapshot(stageState.goalStages.single(), date, date.plusDays(6))
        val withHistory = stageState.addPeriodSnapshot(snapshot)
        val historicalStages = withHistory.goalStages
        val historicalEntries = withHistory.timeEntries
        val historicalSnapshots = withHistory.periodSnapshots
        val scheduled = withHistory.addFutureTargetMove(child.id, newParent.id, date.plusDays(1).toString(), date)

        assertEquals(historicalStages, scheduled.goalStages)
        assertEquals(historicalEntries, scheduled.timeEntries)
        assertEquals(historicalSnapshots, scheduled.periodSnapshots)
        assertEquals(20, scheduled.subtreeMinutes(oldParent.id))
        assertEquals(15, scheduled.subtreeMinutes(newParent.id))
        assertEquals(20, scheduled.subtreeMinutes(oldParent.id, date.toString()))
        assertEquals(0, scheduled.subtreeMinutes(oldParent.id, date.plusDays(2).toString()))
        assertEquals(0, scheduled.subtreeMinutes(newParent.id, date.toString()))
        assertEquals(15, scheduled.subtreeMinutes(newParent.id, date.plusDays(2).toString()))
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

    @Test
    fun future_target_moves_select_the_latest_effective_parent_by_planning_date() {
        val state = AttentionState().addTarget("旧父目标").addTarget("新父目标").addTarget("最终父目标")
        val oldParent = state.targets[0]
        val newParent = state.targets[1]
        val finalParent = state.targets[2]
        val withChild = state.addTarget("子计划", oldParent.id)
        val child = withChild.targets.single { it.title == "子计划" }
        val moved = withChild
            .addFutureTargetMove(child.id, newParent.id, date.plusDays(1).toString(), date)
            .addFutureTargetMove(child.id, finalParent.id, date.plusDays(3).toString(), date)

        assertEquals(oldParent.id, moved.parentAt(child.id, date))
        assertEquals(newParent.id, moved.parentAt(child.id, date.plusDays(1)))
        assertEquals(finalParent.id, moved.parentAt(child.id, date.plusDays(4)))
        assertEquals(listOf(child.id), moved.targetChildren(newParent.id, date.plusDays(1)).map { it.id })
        assertEquals(listOf(child.id), moved.targetChildren(finalParent.id, date.plusDays(4)).map { it.id })
    }

    @Test
    fun future_target_move_rejects_invalid_parent_dates_and_cycles() {
        val state = AttentionState().addTarget("父目标").addTarget("另一个父目标")
        val parent = state.targets[0]
        val otherParent = state.targets[1]
        val withChild = state.addTarget("子计划", parent.id)
        val child = withChild.targets.single { it.title == "子计划" }

        assertThrows(IllegalArgumentException::class.java) {
            withChild.addFutureTargetMove(child.id, child.id, date.plusDays(1).toString(), date)
        }
        assertThrows(IllegalArgumentException::class.java) {
            withChild.addFutureTargetMove(parent.id, child.id, date.plusDays(1).toString(), date)
        }
        assertThrows(IllegalArgumentException::class.java) {
            withChild.addFutureTargetMove(child.id, "missing", date.plusDays(1).toString(), date)
        }
        assertThrows(IllegalArgumentException::class.java) {
            withChild.addFutureTargetMove(child.id, otherParent.id, date.toString(), date)
        }
        assertThrows(IllegalArgumentException::class.java) {
            withChild.addFutureTargetMove(child.id, otherParent.id, "not-a-date", date)
        }

        val scheduled = withChild.addFutureTargetMove(parent.id, otherParent.id, date.plusDays(1).toString(), date)
        assertThrows(IllegalArgumentException::class.java) {
            scheduled.addFutureTargetMove(otherParent.id, parent.id, date.plusDays(2).toString(), date)
        }

        val laterMove = withChild.addFutureTargetMove(parent.id, otherParent.id, date.plusDays(5).toString(), date)
        assertThrows(IllegalArgumentException::class.java) {
            laterMove.addFutureTargetMove(otherParent.id, parent.id, date.plusDays(1).toString(), date)
        }
    }

    @Test
    fun pending_target_moves_can_be_updated_or_cancelled_without_touching_stable_id() {
        val state = AttentionState().addTarget("旧父目标").addTarget("新父目标")
        val oldParent = state.targets[0]
        val newParent = state.targets[1]
        val withChild = state.addTarget("子计划", oldParent.id)
        val child = withChild.targets.single { it.title == "子计划" }
        val scheduled = withChild.addFutureTargetMove(child.id, newParent.id, date.plusDays(1).toString(), date)
        val move = scheduled.targetMoves.single()

        val updated = scheduled.updateFutureTargetMove(
            move.copy(parentId = null, effectiveFrom = date.plusDays(2).toString()),
            date,
        )
        assertEquals(move.id, updated.targetMoves.single().id)
        assertEquals(null, updated.parentAt(child.id, date.plusDays(2)))

        val cancelled = updated.cancelFutureTargetMove(move.id, date)
        assertTrue(cancelled.targetMoves.isEmpty())
        assertEquals(oldParent.id, cancelled.parentAt(child.id, date.plusDays(3)))

        assertThrows(IllegalArgumentException::class.java) {
            scheduled.cancelFutureTargetMove(move.id, date.plusDays(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            scheduled.updateFutureTargetMove(move.copy(parentId = null), date.plusDays(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            scheduled.updateFutureTargetMove(move.copy(effectiveFrom = date.plusDays(2).toString()), date.plusDays(1))
        }
    }
}
