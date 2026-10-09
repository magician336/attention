package com.attention.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.attention.app.data.room.entity.ActiveTimerEntity
import com.attention.app.data.room.entity.ExperienceEntity
import com.attention.app.data.room.entity.FutureGoalRuleEntity
import com.attention.app.data.room.entity.GoalStageEntity
import com.attention.app.data.room.entity.MigrationEntity
import com.attention.app.data.room.entity.MilestoneEntity
import com.attention.app.data.room.entity.PeriodSnapshotEntity
import com.attention.app.data.room.entity.RecurrenceRuleEntity
import com.attention.app.data.room.entity.ScheduleEntryEntity
import com.attention.app.data.room.entity.TargetEntity
import com.attention.app.data.room.entity.TargetMoveEntity
import com.attention.app.data.room.entity.TimeEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetDao {
    @Query("SELECT * FROM targets ORDER BY parentId, sortOrder, id")
    suspend fun getAll(): List<TargetEntity>

    @Query("SELECT * FROM targets ORDER BY parentId, sortOrder, id")
    fun observeAll(): Flow<List<TargetEntity>>

    @Query("SELECT * FROM targets WHERE parentId IS :parentId ORDER BY sortOrder, id")
    fun observeByParent(parentId: String?): Flow<List<TargetEntity>>

    @Query("SELECT * FROM targets WHERE id = :id")
    suspend fun findById(id: String): TargetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TargetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TargetEntity>)

    @Query("DELETE FROM targets WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM targets")
    suspend fun deleteAll()
}

@Dao
interface GoalStageDao {
    @Query("SELECT * FROM goal_stages ORDER BY startDate, id")
    suspend fun getAll(): List<GoalStageEntity>

    @Query("SELECT * FROM goal_stages ORDER BY startDate, id")
    fun observeAll(): Flow<List<GoalStageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<GoalStageEntity>)

    @Query("DELETE FROM goal_stages WHERE targetId IN (:targetIds)")
    suspend fun deleteByTargetIds(targetIds: List<String>)

    @Query("DELETE FROM goal_stages")
    suspend fun deleteAll()
}

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entries ORDER BY planningDate, occurredAtEpochMillis, id")
    suspend fun getAll(): List<TimeEntryEntity>

    @Query("SELECT * FROM time_entries WHERE planningDate = :planningDate ORDER BY occurredAtEpochMillis, id")
    fun observeByPlanningDate(planningDate: String): Flow<List<TimeEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TimeEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TimeEntryEntity>)

    @Query("UPDATE time_entries SET targetId = NULL WHERE targetId IN (:targetIds)")
    suspend fun clearTargetReferences(targetIds: List<String>)

    @Query("DELETE FROM time_entries")
    suspend fun deleteAll()
}

@Dao
interface ScheduleEntryDao {
    @Query("SELECT * FROM schedule_entries ORDER BY planningDate, startMinute, id")
    suspend fun getAll(): List<ScheduleEntryEntity>

    @Query("SELECT * FROM schedule_entries WHERE planningDate = :planningDate ORDER BY startMinute, id")
    fun observeByPlanningDate(planningDate: String): Flow<List<ScheduleEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScheduleEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ScheduleEntryEntity>)

    @Query("UPDATE schedule_entries SET targetId = NULL WHERE targetId IN (:targetIds)")
    suspend fun clearTargetReferences(targetIds: List<String>)

    @Query("DELETE FROM schedule_entries")
    suspend fun deleteAll()
}

@Dao
interface RecurrenceRuleDao {
    @Query("SELECT * FROM recurrence_rules ORDER BY startDate, id")
    suspend fun getAll(): List<RecurrenceRuleEntity>

    @Query(
        "SELECT * FROM recurrence_rules " +
            "WHERE active = 1 AND startDate <= :throughDate " +
            "AND (untilDate IS NULL OR untilDate >= :throughDate) " +
            "ORDER BY startDate, id",
    )
    suspend fun findActiveThrough(throughDate: String): List<RecurrenceRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RecurrenceRuleEntity>)

    @Query("UPDATE recurrence_rules SET targetId = NULL WHERE targetId IN (:targetIds)")
    suspend fun clearTargetReferences(targetIds: List<String>)

    @Query("DELETE FROM recurrence_rules")
    suspend fun deleteAll()
}

@Dao
interface MigrationDao {
    @Query("SELECT * FROM migrations ORDER BY destinationStartDate, id")
    suspend fun getAll(): List<MigrationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MigrationEntity>)

    @Query("DELETE FROM migrations WHERE targetId IN (:targetIds)")
    suspend fun deleteByTargetIds(targetIds: List<String>)

    @Query("DELETE FROM migrations WHERE sourceStageId IN (:stageIds)")
    suspend fun deleteBySourceStageIds(stageIds: List<String>)

    @Query("DELETE FROM migrations")
    suspend fun deleteAll()
}

@Dao
interface FutureGoalRuleDao {
    @Query("SELECT * FROM future_goal_rules ORDER BY effectiveFrom, id")
    suspend fun getAll(): List<FutureGoalRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FutureGoalRuleEntity>)

    @Query("DELETE FROM future_goal_rules WHERE targetId IN (:targetIds)")
    suspend fun deleteByTargetIds(targetIds: List<String>)

    @Query("DELETE FROM future_goal_rules WHERE stageId IN (:stageIds)")
    suspend fun deleteByStageIds(stageIds: List<String>)

    @Query("DELETE FROM future_goal_rules")
    suspend fun deleteAll()
}

@Dao
interface PeriodSnapshotDao {
    @Query("SELECT * FROM period_snapshots ORDER BY periodStart, id")
    suspend fun getAll(): List<PeriodSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PeriodSnapshotEntity>)

    @Query("DELETE FROM period_snapshots WHERE targetId IN (:targetIds)")
    suspend fun deleteByTargetIds(targetIds: List<String>)

    @Query("DELETE FROM period_snapshots")
    suspend fun deleteAll()
}

@Dao
interface TargetMoveDao {
    @Query("SELECT * FROM target_moves ORDER BY effectiveFrom, id")
    suspend fun getAll(): List<TargetMoveEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TargetMoveEntity>)

    @Query("DELETE FROM target_moves WHERE targetId IN (:targetIds)")
    suspend fun deleteByTargetIds(targetIds: List<String>)

    @Query("UPDATE target_moves SET parentId = NULL WHERE parentId IN (:targetIds)")
    suspend fun clearParentReferences(targetIds: List<String>)

    @Query("DELETE FROM target_moves")
    suspend fun deleteAll()
}

@Dao
interface MilestoneDao {
    @Query("SELECT * FROM milestones ORDER BY achievedAtEpochMillis, id")
    suspend fun getAll(): List<MilestoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MilestoneEntity>)

    @Query("DELETE FROM milestones")
    suspend fun deleteAll()
}

@Dao
interface ExperienceDao {
    @Query("SELECT * FROM experience WHERE id = :id")
    suspend fun find(id: Int = ExperienceEntity.SINGLETON_ID): ExperienceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ExperienceEntity)

    @Query("DELETE FROM experience")
    suspend fun deleteAll()
}

@Dao
interface ActiveTimerDao {
    @Query("SELECT * FROM active_timer WHERE id = :id")
    suspend fun find(id: Int = ActiveTimerEntity.SINGLETON_ID): ActiveTimerEntity?

    @Query("SELECT * FROM active_timer WHERE id = :id")
    fun observe(id: Int = ActiveTimerEntity.SINGLETON_ID): Flow<ActiveTimerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ActiveTimerEntity)

    @Query("UPDATE active_timer SET targetId = NULL WHERE targetId IN (:targetIds)")
    suspend fun clearTargetReferences(targetIds: List<String>)

    @Query("DELETE FROM active_timer")
    suspend fun deleteAll()
}
