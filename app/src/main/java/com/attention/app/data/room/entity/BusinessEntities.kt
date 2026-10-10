package com.attention.app.data.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "targets",
    indices = [Index(value = ["parentId"])],
    foreignKeys = [
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class TargetEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val title: String,
    val note: String,
    val icon: String?,
    val colorHex: String?,
    val archived: Boolean,
    val sortOrder: Int,
    val expanded: Boolean,
)

@Entity(
    tableName = "goal_stages",
    indices = [Index(value = ["targetId"])],
    foreignKeys = [
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class GoalStageEntity(
    @PrimaryKey val id: String,
    val targetId: String,
    val cadence: String,
    val targetMinutes: Int,
    val startDate: String,
    val dueDate: String?,
    val completed: Boolean,
)

@Entity(
    tableName = "time_entries",
    indices = [Index(value = ["planningDate"]), Index(value = ["targetId"])],
    foreignKeys = [
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class TimeEntryEntity(
    @PrimaryKey val id: String,
    val planningDate: String,
    val durationMinutes: Int,
    val targetId: String?,
    val source: String,
    val occurredAtEpochMillis: Long?,
    val note: String,
)

@Entity(
    tableName = "recurrence_rules",
    indices = [Index(value = ["targetId"]), Index(value = ["startDate"])],
    foreignKeys = [
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class RecurrenceRuleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startDate: String,
    val frequency: String,
    val weekdays: String,
    val dayOfMonth: Int?,
    val untilDate: String?,
    val active: Boolean,
    val estimatedMinutes: Int?,
    val targetId: String?,
    val note: String,
    val reminderMinuteOfDay: Int?,
)

@Entity(
    tableName = "schedule_entries",
    indices = [
        Index(value = ["planningDate"]),
        Index(value = ["targetId"]),
        Index(value = ["recurrenceRuleId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecurrenceRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurrenceRuleId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class ScheduleEntryEntity(
    @PrimaryKey val id: String,
    val planningDate: String,
    val title: String,
    val startMinute: Int?,
    val endMinute: Int?,
    val estimatedMinutes: Int?,
    val note: String,
    val targetId: String?,
    val completed: Boolean,
    val reminderEpochMillis: Long?,
    val recurrenceRuleId: String?,
)

@Entity(
    tableName = "migrations",
    indices = [Index(value = ["targetId"]), Index(value = ["sourceStageId"])],
    foreignKeys = [
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GoalStageEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceStageId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class MigrationEntity(
    @PrimaryKey val id: String,
    val targetId: String,
    val sourceStageId: String,
    val minutes: Int,
    val destinationStartDate: String,
    val destinationEndDate: String?,
    val cancelled: Boolean,
)

@Entity(
    tableName = "future_goal_rules",
    indices = [Index(value = ["targetId"]), Index(value = ["stageId"])],
    foreignKeys = [
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GoalStageEntity::class,
            parentColumns = ["id"],
            childColumns = ["stageId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class FutureGoalRuleEntity(
    @PrimaryKey val id: String,
    val targetId: String,
    val stageId: String?,
    val cadence: String,
    val targetMinutes: Int,
    val effectiveFrom: String,
    val dueDate: String?,
)

@Entity(
    tableName = "period_snapshots",
    indices = [Index(value = ["targetId"]), Index(value = ["periodStart"])],
)
data class PeriodSnapshotEntity(
    @PrimaryKey val id: String,
    val targetId: String,
    val cadence: String,
    val periodStart: String,
    val periodEnd: String,
    val targetMinutes: Int,
    val actualMinutes: Int,
    val gapMinutes: Int,
    val excessMinutes: Int,
    val completed: Boolean,
    val stageId: String = "",
)

@Entity(
    tableName = "target_moves",
    indices = [Index(value = ["targetId"]), Index(value = ["parentId"])],
    foreignKeys = [
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class TargetMoveEntity(
    @PrimaryKey val id: String,
    val targetId: String,
    val parentId: String?,
    val effectiveFrom: String,
)

@Entity(tableName = "milestones")
data class MilestoneEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val instanceKey: String,
    val reward: Long,
    val achievedAtEpochMillis: Long,
)

@Entity(tableName = "experience")
data class ExperienceEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val points: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(
    tableName = "active_timer",
    indices = [Index(value = ["targetId"])],
    foreignKeys = [
        ForeignKey(
            entity = TargetEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class ActiveTimerEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val targetId: String?,
    val startedAtEpochMillis: Long,
    val accumulatedMillis: Long,
    val paused: Boolean,
    val lastPlanningDate: String,
    val completedSegmentsJson: String,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
