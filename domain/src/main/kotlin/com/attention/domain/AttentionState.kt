package com.attention.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class StoredSettings(
    val planningDayBoundaryMinutes: Int = PlannerSettings.DEFAULT_BOUNDARY_MINUTES,
    val weekStartDay: Int = DayOfWeek.MONDAY.value,
    val dailyCapacityMinutes: Int? = null,
) {
    fun toPlannerSettings(): PlannerSettings = PlannerSettings(
        planningDayBoundaryMinutes = planningDayBoundaryMinutes,
        weekStartDay = DayOfWeek.of(weekStartDay.coerceIn(1, 7)),
        dailyCapacityMinutes = dailyCapacityMinutes,
    )
}

@Serializable
enum class GoalCadence { DAILY, WEEKLY, MONTHLY, ONE_TIME }

@Serializable
enum class TimeEntrySource { MANUAL, TIMER, SCHEDULE_CONFIRMED, IMPORT }

@Serializable
enum class ScheduleFrequency { DAILY, WEEKLY, MONTHLY }

@Serializable
/**
 * 启动入口。[AGENDA] 是「日程」的正式语义；[TODAY] 与 [ALL_DATES] 是旧版本持久化的入口值，
 * 它们仍然存在并按旧页面打开，删除它们属于后续的收缩工作。
 */
enum class LaunchDestination { TODAY, TARGETS, ALL_DATES, STATISTICS, LAST_OPENED, AGENDA }

@Serializable
data class Target(
    val id: String = newId(),
    val parentId: String? = null,
    val title: String,
    val note: String = "",
    val icon: String? = null,
    val colorHex: String? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val expanded: Boolean = true,
)

@Serializable
data class GoalStage(
    val id: String = newId(),
    val targetId: String,
    val cadence: GoalCadence,
    val targetMinutes: Int,
    val startDate: String,
    val dueDate: String? = null,
    val completed: Boolean = false,
)

@Serializable
data class TimeEntry(
    val id: String = newId(),
    val planningDate: String,
    val durationMinutes: Int,
    val targetId: String? = null,
    val source: TimeEntrySource = TimeEntrySource.MANUAL,
    val occurredAtEpochMillis: Long? = null,
    val note: String = "",
    val scheduleEntryId: String? = null,
)

@Serializable
data class ScheduleEntry(
    val id: String = newId(),
    val planningDate: String,
    val title: String,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val estimatedMinutes: Int? = null,
    val note: String = "",
    val targetId: String? = null,
    val completed: Boolean = false,
    val reminderEpochMillis: Long? = null,
    val recurrenceRuleId: String? = null,
)

@Serializable
data class RecurrenceRule(
    val id: String = newId(),
    val title: String,
    val startDate: String,
    val frequency: ScheduleFrequency,
    val weekdays: List<Int> = emptyList(),
    val dayOfMonth: Int? = null,
    val untilDate: String? = null,
    val active: Boolean = true,
    val estimatedMinutes: Int? = null,
    val targetId: String? = null,
    val note: String = "",
    val reminderMinuteOfDay: Int? = null,
)

@Serializable
data class Migration(
    val id: String = newId(),
    val targetId: String,
    val sourceStageId: String,
    val minutes: Int,
    val destinationStartDate: String,
    val destinationEndDate: String? = null,
    val cancelled: Boolean = false,
)

@Serializable
data class FutureGoalRule(
    val id: String = newId(),
    val targetId: String,
    val stageId: String = "",
    val cadence: GoalCadence,
    val targetMinutes: Int,
    val effectiveFrom: String,
    val dueDate: String? = null,
)

@Serializable
data class PeriodSnapshot(
    val id: String = newId(),
    val targetId: String,
    val cadence: GoalCadence,
    val periodStart: String,
    val periodEnd: String,
    val targetMinutes: Int,
    val actualMinutes: Int,
    val gapMinutes: Int,
    val excessMinutes: Int,
    val completed: Boolean,
    val stageId: String = "",
)

@Serializable
data class TargetMove(
    val id: String = newId(),
    val targetId: String,
    val parentId: String?,
    val effectiveFrom: String,
)

@Serializable
data class ActiveTimer(
    val targetId: String?,
    val startedAtEpochMillis: Long,
    val accumulatedMillis: Long = 0,
    val paused: Boolean = false,
    val lastPlanningDate: String,
    val completedSegments: List<TimerSegment> = emptyList(),
    val scheduleEntryId: String? = null,
)

@Serializable
data class TimerSegment(val startedAtEpochMillis: Long, val endedAtEpochMillis: Long)

@Serializable
data class Milestone(
    val id: String = newId(),
    val kind: String,
    val instanceKey: String,
    val reward: Long,
    val achievedAtEpochMillis: Long,
)

@Serializable
data class AttentionState(
    val settings: StoredSettings = StoredSettings(),
    val targets: List<Target> = emptyList(),
    val goalStages: List<GoalStage> = emptyList(),
    val timeEntries: List<TimeEntry> = emptyList(),
    val schedules: List<ScheduleEntry> = emptyList(),
    val recurrenceRules: List<RecurrenceRule> = emptyList(),
    val migrations: List<Migration> = emptyList(),
    val futureGoalRules: List<FutureGoalRule> = emptyList(),
    val periodSnapshots: List<PeriodSnapshot> = emptyList(),
    val targetMoves: List<TargetMove> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val experience: Long = 0,
    val activeTimer: ActiveTimer? = null,
    val onboardingCompleted: Boolean = false,
    // 空状态的入口默认是日程：旧值 TODAY 语义等价于日程，但继续以它为默认
    // 会让新写出的备份持续带旧入口名。旧枚举值本身仍然保留（见 [LaunchDestination]）。
    val launchDestination: LaunchDestination = LaunchDestination.AGENDA,
    val lastOpenedDestination: LaunchDestination = LaunchDestination.AGENDA,
    val notificationsEnabled: Boolean = true,
)

/** Counts the records affected by removing a target subtree. */
data class TargetDeletionImpact(
    val targetCount: Int,
    val timeEntryCount: Int,
    val scheduleEntryCount: Int,
    val recurrenceRuleCount: Int,
    val goalStageCount: Int,
    val futureGoalRuleCount: Int,
    val migrationCount: Int,
    val targetMoveCount: Int,
    val activeTimerCount: Int,
)

fun newId(): String = UUID.randomUUID().toString()

data class GoalProgress(
    val targetMinutes: Int,
    val actualMinutes: Int,
    val gapMinutes: Int,
    val excessMinutes: Int,
    val completed: Boolean,
)

data class LevelProgress(
    val level: Int,
    val currentLevelThreshold: Long,
    val nextLevelThreshold: Long,
    val progressExperience: Long,
    val remainingExperience: Long,
)

data class GoalStageSummary(
    val stage: GoalStage,
    val progress: GoalProgress,
)

data class GoalSummary(
    val targetId: String,
    val stageProgresses: List<GoalStageSummary>,
    val targetMinutes: Int,
    val actualMinutes: Int,
    val gapMinutes: Int,
    val excessMinutes: Int,
    val completed: Boolean,
)

data class CapacitySummary(val capacityMinutes: Int?, val usedMinutes: Int, val overloaded: Boolean)

data class PeriodStats(
    val range: ClosedRange<LocalDate>,
    val targetMinutes: Int,
    val actualMinutes: Int,
    val unownedMinutes: Int,
    val migrationMinutes: Int,
    val excessMinutes: Int,
    val byTargetMinutes: Map<String, Int>,
    val subtreeByTargetMinutes: Map<String, Int> = emptyMap(),
)

data class TimerResult(val state: AttentionState, val entries: List<TimeEntry>)

/**
 * Read model for one time record of a planning day. A record whose linked schedule entry no
 * longer exists stays visible and says so instead of being dropped or throwing.
 */
data class TimeEntryRow(
    val entry: TimeEntry,
    val source: TimeEntrySource,
    val schedule: ScheduleEntry?,
) {
    val hasPreciseTiming: Boolean get() = source == TimeEntrySource.TIMER && entry.occurredAtEpochMillis != null
    val scheduleMissing: Boolean get() = entry.scheduleEntryId != null && schedule == null
    val scheduleLabel: String get() = when {
        entry.scheduleEntryId == null -> ""
        schedule != null -> schedule.title
        else -> "所属日程已删除"
    }
}

data class TimerSettlementPreview(val entries: List<TimeEntry>) {
    val totalMinutes: Int get() = entries.sumOf { it.durationMinutes }
    val hasCompleteMinutes: Boolean get() = entries.isNotEmpty()
}
