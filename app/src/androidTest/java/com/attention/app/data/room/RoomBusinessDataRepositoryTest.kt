package com.attention.app.data.room

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.mapper.toEntity
import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
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
import com.attention.domain.addFutureTargetMove
import com.attention.domain.addGoalStage
import com.attention.domain.addTarget
import com.attention.domain.addTimeEntry
import com.attention.domain.parentAt
import com.attention.domain.settlePeriodSnapshots
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RoomBusinessDataRepositoryTest {
    private lateinit var database: AttentionDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AttentionDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replace_and_read_round_trip_all_business_data() = runBlocking {
        val state = sampleState()
        val repository = RoomBusinessDataRepository(database)

        repository.replace(state)
        val loaded = repository.read()

        assertEquals(state.targets.sortedBy { it.id }, loaded.targets.sortedBy { it.id })
        assertEquals(state.goalStages.sortedBy { it.id }, loaded.goalStages.sortedBy { it.id })
        assertEquals(state.timeEntries.sortedBy { it.id }, loaded.timeEntries.sortedBy { it.id })
        assertEquals(state.schedules.sortedBy { it.id }, loaded.schedules.sortedBy { it.id })
        assertEquals(state.recurrenceRules.sortedBy { it.id }, loaded.recurrenceRules.sortedBy { it.id })
        assertEquals(state.migrations.sortedBy { it.id }, loaded.migrations.sortedBy { it.id })
        assertEquals(state.futureGoalRules.sortedBy { it.id }, loaded.futureGoalRules.sortedBy { it.id })
        assertEquals(state.periodSnapshots.sortedBy { it.id }, loaded.periodSnapshots.sortedBy { it.id })
        assertEquals(state.targetMoves.sortedBy { it.id }, loaded.targetMoves.sortedBy { it.id })
        assertEquals(state.milestones.sortedBy { it.id }, loaded.milestones.sortedBy { it.id })
        assertEquals(state.experience, loaded.experience)
        assertEquals(state.activeTimer, loaded.activeTimer)
    }

    @Test
    fun one_time_goal_survives_room_round_trip_with_deadline() = runBlocking {
        val target = Target("one-time-target", title = "项目")
        val stage = GoalStage("one-time-stage", target.id, GoalCadence.ONE_TIME, 240, "2026-10-10", "2026-10-12")
        val repository = RoomBusinessDataRepository(database)

        repository.replace(AttentionState(targets = listOf(target), goalStages = listOf(stage)))

        assertEquals(stage, repository.read().goalStages.single())
    }

    @Test
    fun dao_queries_use_planning_date_indexes() = runBlocking {
        val state = sampleState()
        RoomBusinessDataRepository(database).replace(state)

        assertEquals(
            listOf(state.timeEntries.first().toEntity()),
            database.timeEntryDao().observeByPlanningDate("2026-10-02").first(),
        )
        assertEquals(
            listOf(state.schedules.first().toEntity()),
            database.scheduleEntryDao().observeByPlanningDate("2026-10-02").first(),
        )
        assertEquals(
            listOf(state.recurrenceRules.first().toEntity()),
            database.recurrenceRuleDao().findActiveThrough("2026-10-02"),
        )

        val child = Target("child", parentId = "target", title = "子目标")
        database.targetDao().upsert(child.toEntity())
        assertEquals(listOf(child.toEntity()), database.targetDao().observeByParent("target").first())
    }

    @Test
    fun deleting_target_relations_detaches_entries_and_removes_dependent_rows() = runBlocking {
        val state = sampleState()
        val repository = RoomBusinessDataRepository(database)
        repository.replace(state)

        repository.deleteTargetRelations(setOf("target"))
        val loaded = repository.read()

        assertEquals(emptyList<Target>(), loaded.targets)
        assertEquals(emptyList<GoalStage>(), loaded.goalStages)
        assertEquals(listOf(state.timeEntries.first().copy(targetId = null)), loaded.timeEntries)
        assertEquals(listOf(state.schedules.first().copy(targetId = null)), loaded.schedules)
        assertEquals(listOf(state.recurrenceRules.first().copy(targetId = null)), loaded.recurrenceRules)
        assertEquals(emptyList<Migration>(), loaded.migrations)
        assertEquals(emptyList<FutureGoalRule>(), loaded.futureGoalRules)
        assertEquals(state.periodSnapshots, loaded.periodSnapshots)
        assertEquals(emptyList<TargetMove>(), loaded.targetMoves)
        assertEquals(state.activeTimer?.copy(targetId = null), loaded.activeTimer)
    }

    @Test
    fun deleting_target_detaches_target_move_parent_reference() = runBlocking {
        val parent = Target("parent", title = "父目标")
        val other = Target("other", title = "其他目标")
        val move = TargetMove("move-other", other.id, parent.id, "2026-11-01")
        val repository = RoomBusinessDataRepository(database)

        repository.replace(AttentionState(targets = listOf(parent, other), targetMoves = listOf(move)))
        repository.deleteTargetRelations(setOf(parent.id))

        val loaded = repository.read()
        assertEquals(listOf(other), loaded.targets)
        assertEquals(listOf(move.copy(parentId = null)), loaded.targetMoves)
    }

    @Test
    fun target_move_round_trip_preserves_stable_id_parent_and_effective_date() = runBlocking {
        val oldParent = Target("old-parent", title = "旧父目标")
        val newParent = Target("new-parent", title = "新父目标")
        val child = Target("child", parentId = oldParent.id, title = "子计划")
        val move = TargetMove("move-child", child.id, newParent.id, "2026-11-01")
        val repository = RoomBusinessDataRepository(database)

        repository.replace(AttentionState(targets = listOf(oldParent, newParent, child), targetMoves = listOf(move)))

        val loaded = repository.read()
        assertEquals(listOf(move), loaded.targetMoves)
        assertEquals(oldParent.id, loaded.parentAt(child.id, LocalDate.parse("2026-10-31")))
        assertEquals(newParent.id, loaded.parentAt(child.id, LocalDate.parse("2026-11-01")))
    }

    @Test
    fun future_target_move_rejects_past_dates_and_scheduled_cycles_on_android() {
        val state = AttentionState().addTarget("父目标").addTarget("另一个父目标")
        val parent = state.targets[0]
        val otherParent = state.targets[1]
        val withChild = state.addTarget("子计划", parent.id)
        val child = withChild.targets.single { it.title == "子计划" }

        assertThrows(IllegalArgumentException::class.java) {
            withChild.addFutureTargetMove(child.id, otherParent.id, "2026-10-08", LocalDate.parse("2026-10-08"))
        }
        val scheduled = withChild.addFutureTargetMove(parent.id, otherParent.id, "2026-10-09", LocalDate.parse("2026-10-08"))
        assertThrows(IllegalArgumentException::class.java) {
            scheduled.addFutureTargetMove(otherParent.id, parent.id, "2026-10-10", LocalDate.parse("2026-10-08"))
        }
    }

    @Test
    fun settled_snapshots_are_observed_and_remain_idempotent_in_room() = runBlocking {
        val target = Target("snapshot-target", title = "周期目标")
        val state = AttentionState(targets = listOf(target))
            .addGoalStage(target.id, GoalCadence.WEEKLY, 120, "2026-10-01")
            .addTimeEntry("2026-10-02", 90, target.id)
        val settled = state.settlePeriodSnapshots(LocalDate.parse("2026-10-08"))
        val repository = RoomBusinessDataRepository(database)

        repository.replace(settled)

        val loaded = repository.read()
        assertEquals(settled.periodSnapshots, loaded.periodSnapshots)
        assertEquals(1, loaded.settlePeriodSnapshots(LocalDate.parse("2026-10-08")).periodSnapshots.size)
    }

    @Test
    fun deleting_target_relations_removes_descendants_leaf_first() = runBlocking {
        val parent = Target("parent", title = "父目标")
        val child = Target("child", parentId = parent.id, title = "子目标")
        val repository = RoomBusinessDataRepository(database)

        repository.replace(AttentionState(targets = listOf(parent, child)))
        repository.deleteTargetRelations(setOf(parent.id))

        assertEquals(emptyList<Target>(), repository.read().targets)
    }

    @Test
    fun failed_transaction_rolls_back_preceding_target_insert() = runBlocking {
        val state = sampleState()
        val repository = RoomBusinessDataRepository(database)
        try {
            repository.replace(state.copy(timeEntries = listOf(state.timeEntries.first().copy(targetId = "missing"))))
            fail("Expected foreign-key constraint failure")
        } catch (_: SQLiteConstraintException) {
            // Expected: the repository transaction must roll back all preceding writes.
        }

        assertNull(database.targetDao().findById("target"))
    }

    @Test
    fun file_database_survives_close_and_reopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "room-business-restart-test.db"
        context.deleteDatabase(name)
        val first = Room.databaseBuilder(context, AttentionDatabase::class.java, name).build()
        RoomBusinessDataRepository(first).replace(sampleState())
        first.close()

        val second = Room.databaseBuilder(context, AttentionDatabase::class.java, name).build()
        try {
            assertEquals("target", RoomBusinessDataRepository(second).read().targets.single().id)
        } finally {
            second.close()
            context.deleteDatabase(name)
        }
    }

    private fun sampleState(): AttentionState {
        val target = Target("target", title = "学习")
        return AttentionState(
            targets = listOf(target),
            goalStages = listOf(GoalStage("stage", target.id, GoalCadence.WEEKLY, 120, "2026-10-01")),
            timeEntries = listOf(TimeEntry("entry", "2026-10-02", 45, target.id, TimeEntrySource.TIMER, 123L, "timer")),
            schedules = listOf(ScheduleEntry("schedule", "2026-10-02", "阅读", targetId = target.id, recurrenceRuleId = "rule")),
            recurrenceRules = listOf(RecurrenceRule("rule", "阅读", "2026-10-01", ScheduleFrequency.DAILY, targetId = target.id)),
            migrations = listOf(Migration("migration", target.id, "stage", 20, "2026-10-08")),
            futureGoalRules = listOf(FutureGoalRule("future", target.id, "stage", GoalCadence.MONTHLY, 300, "2026-11-01")),
            periodSnapshots = listOf(PeriodSnapshot("snapshot", target.id, GoalCadence.WEEKLY, "2026-10-01", "2026-10-07", 120, 90, 30, 0, false, stageId = "stage")),
            targetMoves = listOf(TargetMove("move", target.id, null, "2026-11-01")),
            milestones = listOf(Milestone("milestone", "goal_period", "target:2026-10-01", 20, 789L)),
            experience = 1234L,
            activeTimer = ActiveTimer(target.id, 100L, 200L, paused = true, "2026-10-02", listOf(TimerSegment(100L, 200L))),
        )
    }
}
