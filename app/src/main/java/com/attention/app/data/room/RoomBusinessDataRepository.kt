package com.attention.app.data.room

import androidx.room.withTransaction
import com.attention.app.data.room.entity.ExperienceEntity
import com.attention.app.data.room.mapper.toDomain
import com.attention.app.data.room.mapper.toEntity
import com.attention.domain.AttentionState
import com.attention.domain.descendantIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Room-only business data boundary; settings are composed by AttentionStateReader. */
class RoomBusinessDataRepository(
    private val database: AttentionDatabase,
) {
    suspend fun replace(state: AttentionState) = database.withTransaction {
        clearAll()
        database.targetDao().upsertAll(state.targets.map { it.toEntity() })
        database.goalStageDao().upsertAll(state.goalStages.map { it.toEntity() })
        database.recurrenceRuleDao().upsertAll(state.recurrenceRules.map { it.toEntity() })
        database.timeEntryDao().upsertAll(state.timeEntries.map { it.toEntity() })
        database.scheduleEntryDao().upsertAll(state.schedules.map { it.toEntity() })
        database.migrationDao().upsertAll(state.migrations.map { it.toEntity() })
        database.futureGoalRuleDao().upsertAll(state.futureGoalRules.map { it.toEntity() })
        database.periodSnapshotDao().upsertAll(state.periodSnapshots.map { it.toEntity() })
        database.targetMoveDao().upsertAll(state.targetMoves.map { it.toEntity() })
        database.milestoneDao().upsertAll(state.milestones.map { it.toEntity() })
        database.experienceDao().upsert(ExperienceEntity(points = state.experience))
        state.activeTimer?.let { database.activeTimerDao().upsert(it.toEntity()) }
    }

    suspend fun read(): AttentionState = database.withTransaction {
        AttentionState(
            targets = database.targetDao().getAll().map { it.toDomain() },
            goalStages = database.goalStageDao().getAll().map { it.toDomain() },
            timeEntries = database.timeEntryDao().getAll().map { it.toDomain() },
            schedules = database.scheduleEntryDao().getAll().map { it.toDomain() },
            recurrenceRules = database.recurrenceRuleDao().getAll().map { it.toDomain() },
            migrations = database.migrationDao().getAll().map { it.toDomain() },
            futureGoalRules = database.futureGoalRuleDao().getAll().map { it.toDomain() },
            periodSnapshots = database.periodSnapshotDao().getAll().map { it.toDomain() },
            targetMoves = database.targetMoveDao().getAll().map { it.toDomain() },
            milestones = database.milestoneDao().getAll().map { it.toDomain() },
            experience = database.experienceDao().find()?.points ?: 0,
            activeTimer = database.activeTimerDao().find()?.toDomain(),
        )
    }

    /**
     * Emits the complete Room-backed business read model whenever any business
     * table changes. Settings are deliberately absent and are composed by
     * [com.attention.app.data.AttentionStateReader].
     */
    fun observe(): Flow<AttentionState> {
        val targets = database.targetDao().observeAll().map { rows -> rows.map { it.toDomain() } }
        val goalStages = database.goalStageDao().observeAll().map { rows -> rows.map { it.toDomain() } }
        val timeEntries = database.timeEntryDao().observeAll().map { rows -> rows.map { it.toDomain() } }
        val schedules = database.scheduleEntryDao().observeAll().map { rows -> rows.map { it.toDomain() } }
        val recurrenceRules = database.recurrenceRuleDao().observeAll().map { rows -> rows.map { it.toDomain() } }
        val migrations = database.migrationDao().observeAll().map { rows -> rows.map { it.toDomain() } }
        val futureGoalRules = database.futureGoalRuleDao().observeAll().map { rows -> rows.map { it.toDomain() } }
        val periodSnapshots = database.periodSnapshotDao().observeAll().map { rows -> rows.map { it.toDomain() } }
        val targetMoves = database.targetMoveDao().observeAll().map { rows -> rows.map { it.toDomain() } }
        val milestones = database.milestoneDao().observeAll().map { rows -> rows.map { it.toDomain() } }
        val experience = database.experienceDao().observe().map { it?.points ?: 0 }
        val activeTimer = database.activeTimerDao().observe().map { it?.toDomain() }

        val targetData = combine(targets, goalStages) { loadedTargets, loadedStages ->
            TargetData(loadedTargets, loadedStages)
        }
        val scheduleData = combine(timeEntries, schedules, recurrenceRules) { loadedEntries, loadedSchedules, loadedRules ->
            ScheduleData(loadedEntries, loadedSchedules, loadedRules)
        }
        val planningData = combine(migrations, futureGoalRules, periodSnapshots, targetMoves) { loadedMigrations, loadedFutureRules, loadedSnapshots, loadedMoves ->
            PlanningData(loadedMigrations, loadedFutureRules, loadedSnapshots, loadedMoves)
        }
        val rewardData = combine(milestones, experience, activeTimer) { loadedMilestones, loadedExperience, loadedTimer ->
            RewardData(loadedMilestones, loadedExperience, loadedTimer)
        }
        return combine(targetData, scheduleData, planningData, rewardData) { targetsAndStages, schedulesAndEntries, planning, rewards ->
            AttentionState(
                targets = targetsAndStages.targets,
                goalStages = targetsAndStages.goalStages,
                timeEntries = schedulesAndEntries.timeEntries,
                schedules = schedulesAndEntries.schedules,
                recurrenceRules = schedulesAndEntries.recurrenceRules,
                migrations = planning.migrations,
                futureGoalRules = planning.futureGoalRules,
                periodSnapshots = planning.periodSnapshots,
                targetMoves = planning.targetMoves,
                milestones = rewards.milestones,
                experience = rewards.experience,
                activeTimer = rewards.activeTimer,
            )
        }
    }

    suspend fun deleteTargetRelations(targetIds: Set<String>) = database.withTransaction {
        if (targetIds.isEmpty()) return@withTransaction
        val existingTargets = database.targetDao().getAll().map { it.toDomain() }
        val existingState = existingTargetsState(existingTargets)
        val existingTargetIds = existingTargets.map { it.id }.toSet()
        val ids = targetIds.flatMap(existingState::descendantIds)
            .filter(existingTargetIds::contains)
            .toSet()
        if (ids.isEmpty()) return@withTransaction
        val idList = ids.toList()
        database.timeEntryDao().clearTargetReferences(idList)
        database.scheduleEntryDao().clearTargetReferences(idList)
        database.recurrenceRuleDao().clearTargetReferences(idList)
        database.activeTimerDao().clearTargetReferences(idList)
        database.migrationDao().deleteByTargetIds(idList)
        database.futureGoalRuleDao().deleteByTargetIds(idList)
        database.targetMoveDao().clearParentReferences(idList)
        database.targetMoveDao().deleteByTargetIds(idList)
        val deletedStageIds = database.goalStageDao().getAll()
            .filter { it.targetId in ids }
            .map { it.id }
        if (deletedStageIds.isNotEmpty()) {
            database.migrationDao().deleteBySourceStageIds(deletedStageIds)
            database.futureGoalRuleDao().deleteByStageIds(deletedStageIds)
        }
        database.goalStageDao().deleteByTargetIds(idList)
        deleteTargetsLeafFirst(existingTargets, ids)
    }

    private suspend fun deleteTargetsLeafFirst(
        targets: List<com.attention.domain.Target>,
        ids: Set<String>,
    ) {
        val remaining = ids.toMutableSet()
        while (remaining.isNotEmpty()) {
            val leafIds = targets
                .filter { candidate ->
                    candidate.id in remaining && targets.none { child ->
                        child.id in remaining && child.parentId == candidate.id
                    }
                }
                .map { it.id }
            check(leafIds.isNotEmpty()) { "目标树存在循环，无法删除目标关系" }
            database.targetDao().deleteByIds(leafIds)
            remaining.removeAll(leafIds.toSet())
        }
    }

    private suspend fun clearAll() {
        database.activeTimerDao().deleteAll()
        database.milestoneDao().deleteAll()
        database.experienceDao().deleteAll()
        database.targetMoveDao().deleteAll()
        database.periodSnapshotDao().deleteAll()
        database.futureGoalRuleDao().deleteAll()
        database.migrationDao().deleteAll()
        database.scheduleEntryDao().deleteAll()
        database.timeEntryDao().deleteAll()
        database.recurrenceRuleDao().deleteAll()
        database.goalStageDao().deleteAll()
        database.targetDao().deleteAll()
    }
}

private data class TargetData(
    val targets: List<com.attention.domain.Target>,
    val goalStages: List<com.attention.domain.GoalStage>,
)

private data class ScheduleData(
    val timeEntries: List<com.attention.domain.TimeEntry>,
    val schedules: List<com.attention.domain.ScheduleEntry>,
    val recurrenceRules: List<com.attention.domain.RecurrenceRule>,
)

private data class PlanningData(
    val migrations: List<com.attention.domain.Migration>,
    val futureGoalRules: List<com.attention.domain.FutureGoalRule>,
    val periodSnapshots: List<com.attention.domain.PeriodSnapshot>,
    val targetMoves: List<com.attention.domain.TargetMove>,
)

private data class RewardData(
    val milestones: List<com.attention.domain.Milestone>,
    val experience: Long,
    val activeTimer: com.attention.domain.ActiveTimer?,
)

private fun existingTargetsState(targets: List<com.attention.domain.Target>): AttentionState =
    AttentionState(targets = targets)
