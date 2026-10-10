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
enum class TimeEntrySource { MANUAL, TIMER, IMPORT }

@Serializable
enum class ScheduleFrequency { DAILY, WEEKLY, MONTHLY }

@Serializable
enum class LaunchDestination { TODAY, TARGETS, ALL_DATES, STATISTICS, LAST_OPENED }

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
    val launchDestination: LaunchDestination = LaunchDestination.TODAY,
    val lastOpenedDestination: LaunchDestination = LaunchDestination.TODAY,
    val notificationsEnabled: Boolean = true,
)

fun newId(): String = UUID.randomUUID().toString()

data class GoalProgress(
    val targetMinutes: Int,
    val actualMinutes: Int,
    val gapMinutes: Int,
    val excessMinutes: Int,
    val completed: Boolean,
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
)

data class TimerResult(val state: AttentionState, val entries: List<TimeEntry>)
