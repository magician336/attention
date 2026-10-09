package com.attention.app.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.attention.app.data.room.dao.ActiveTimerDao
import com.attention.app.data.room.dao.ExperienceDao
import com.attention.app.data.room.dao.FutureGoalRuleDao
import com.attention.app.data.room.dao.GoalStageDao
import com.attention.app.data.room.dao.MigrationDao
import com.attention.app.data.room.dao.MilestoneDao
import com.attention.app.data.room.dao.PeriodSnapshotDao
import com.attention.app.data.room.dao.RecurrenceRuleDao
import com.attention.app.data.room.dao.RoomMetadataDao
import com.attention.app.data.room.dao.ScheduleEntryDao
import com.attention.app.data.room.dao.TargetDao
import com.attention.app.data.room.dao.TargetMoveDao
import com.attention.app.data.room.dao.TimeEntryDao
import com.attention.app.data.room.entity.ActiveTimerEntity
import com.attention.app.data.room.entity.ExperienceEntity
import com.attention.app.data.room.entity.FutureGoalRuleEntity
import com.attention.app.data.room.entity.GoalStageEntity
import com.attention.app.data.room.entity.MigrationEntity
import com.attention.app.data.room.entity.MilestoneEntity
import com.attention.app.data.room.entity.PeriodSnapshotEntity
import com.attention.app.data.room.entity.RecurrenceRuleEntity
import com.attention.app.data.room.entity.RoomMetadataEntity
import com.attention.app.data.room.entity.ScheduleEntryEntity
import com.attention.app.data.room.entity.TargetEntity
import com.attention.app.data.room.entity.TargetMoveEntity
import com.attention.app.data.room.entity.TimeEntryEntity

@Database(
    entities = [
        RoomMetadataEntity::class,
        TargetEntity::class,
        GoalStageEntity::class,
        TimeEntryEntity::class,
        ScheduleEntryEntity::class,
        RecurrenceRuleEntity::class,
        MigrationEntity::class,
        FutureGoalRuleEntity::class,
        PeriodSnapshotEntity::class,
        TargetMoveEntity::class,
        MilestoneEntity::class,
        ExperienceEntity::class,
        ActiveTimerEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AttentionDatabase : RoomDatabase() {
    abstract fun metadataDao(): RoomMetadataDao
    abstract fun targetDao(): TargetDao
    abstract fun goalStageDao(): GoalStageDao
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun scheduleEntryDao(): ScheduleEntryDao
    abstract fun recurrenceRuleDao(): RecurrenceRuleDao
    abstract fun migrationDao(): MigrationDao
    abstract fun futureGoalRuleDao(): FutureGoalRuleDao
    abstract fun periodSnapshotDao(): PeriodSnapshotDao
    abstract fun targetMoveDao(): TargetMoveDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun experienceDao(): ExperienceDao
    abstract fun activeTimerDao(): ActiveTimerDao

    companion object {
        const val DATABASE_NAME = "attention.db"
        const val DATABASE_VERSION = 3

        fun create(context: Context): AttentionDatabase = Room.databaseBuilder(
            context.applicationContext,
            AttentionDatabase::class.java,
            DATABASE_NAME,
        // Existing installations are explicitly outside this release's migration
        // contract. Restrict the destructive fallback to the schema versions
        // created by the pre-release Room foundation instead of silently applying
        // it to an unknown future version.
        ).fallbackToDestructiveMigrationFrom(true, 1, 2).build()
    }
}
