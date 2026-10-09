package com.attention.app.data.room

import androidx.room.withTransaction
import com.attention.app.data.room.entity.ExperienceEntity
import com.attention.app.data.room.mapper.toDomain
import com.attention.app.data.room.mapper.toEntity
import com.attention.domain.AttentionState
import com.attention.domain.descendantIds

/**
 * Room-only business data boundary. Settings and UI state remain outside this
 * repository until the following migration issues define their own stores.
 */
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

private fun existingTargetsState(targets: List<com.attention.domain.Target>): AttentionState =
    AttentionState(targets = targets)
