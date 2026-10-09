package com.attention.app.data.room.mapper

import com.attention.app.data.room.entity.ActiveTimerEntity
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
import com.attention.domain.ActiveTimer
import com.attention.domain.FutureGoalRule
import com.attention.domain.GoalStage
import com.attention.domain.Migration
import com.attention.domain.Milestone
import com.attention.domain.PeriodSnapshot
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.Target
import com.attention.domain.TargetMove
import com.attention.domain.TimeEntry
import com.attention.domain.TimerSegment
import com.attention.domain.GoalCadence
import com.attention.domain.ScheduleFrequency
import com.attention.domain.TimeEntrySource
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val roomJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }
private val intListSerializer = ListSerializer(Int.serializer())
private val timerSegmentListSerializer = ListSerializer(TimerSegment.serializer())

fun Target.toEntity() = TargetEntity(id, parentId, title, note, icon, colorHex, archived, sortOrder, expanded)

fun TargetEntity.toDomain() = Target(id, parentId, title, note, icon, colorHex, archived, sortOrder, expanded)

fun GoalStage.toEntity() = GoalStageEntity(id, targetId, cadence.name, targetMinutes, startDate, dueDate, completed)

fun GoalStageEntity.toDomain() = GoalStage(id, targetId, GoalCadence.valueOf(cadence), targetMinutes, startDate, dueDate, completed)

fun TimeEntry.toEntity() = TimeEntryEntity(id, planningDate, durationMinutes, targetId, source.name, occurredAtEpochMillis, note)

fun TimeEntryEntity.toDomain() = TimeEntry(id, planningDate, durationMinutes, targetId, TimeEntrySource.valueOf(source), occurredAtEpochMillis, note)

fun ScheduleEntry.toEntity() = ScheduleEntryEntity(id, planningDate, title, startMinute, endMinute, estimatedMinutes, note, targetId, completed, reminderEpochMillis, recurrenceRuleId)

fun ScheduleEntryEntity.toDomain() = ScheduleEntry(id, planningDate, title, startMinute, endMinute, estimatedMinutes, note, targetId, completed, reminderEpochMillis, recurrenceRuleId)

fun RecurrenceRule.toEntity() = RecurrenceRuleEntity(
    id = id,
    title = title,
    startDate = startDate,
    frequency = frequency.name,
    weekdays = roomJson.encodeToString(intListSerializer, weekdays),
    dayOfMonth = dayOfMonth,
    untilDate = untilDate,
    active = active,
    estimatedMinutes = estimatedMinutes,
    targetId = targetId,
    note = note,
    reminderMinuteOfDay = reminderMinuteOfDay,
)

fun RecurrenceRuleEntity.toDomain() = RecurrenceRule(
    id = id,
    title = title,
    startDate = startDate,
    frequency = ScheduleFrequency.valueOf(frequency),
    weekdays = roomJson.decodeFromString(intListSerializer, weekdays),
    dayOfMonth = dayOfMonth,
    untilDate = untilDate,
    active = active,
    estimatedMinutes = estimatedMinutes,
    targetId = targetId,
    note = note,
    reminderMinuteOfDay = reminderMinuteOfDay,
)

fun Migration.toEntity() = MigrationEntity(id, targetId, sourceStageId, minutes, destinationStartDate, destinationEndDate, cancelled)

fun MigrationEntity.toDomain() = Migration(id, targetId, sourceStageId, minutes, destinationStartDate, destinationEndDate, cancelled)

fun FutureGoalRule.toEntity() = FutureGoalRuleEntity(id, targetId, stageId.ifBlank { null }, cadence.name, targetMinutes, effectiveFrom, dueDate)

fun FutureGoalRuleEntity.toDomain() = FutureGoalRule(id, targetId, stageId.orEmpty(), GoalCadence.valueOf(cadence), targetMinutes, effectiveFrom, dueDate)

fun PeriodSnapshot.toEntity() = PeriodSnapshotEntity(id, targetId, cadence.name, periodStart, periodEnd, targetMinutes, actualMinutes, gapMinutes, excessMinutes, completed)

fun PeriodSnapshotEntity.toDomain() = PeriodSnapshot(id, targetId, GoalCadence.valueOf(cadence), periodStart, periodEnd, targetMinutes, actualMinutes, gapMinutes, excessMinutes, completed)

fun TargetMove.toEntity() = TargetMoveEntity(id, targetId, parentId, effectiveFrom)

fun TargetMoveEntity.toDomain() = TargetMove(id, targetId, parentId, effectiveFrom)

fun Milestone.toEntity() = MilestoneEntity(id, kind, instanceKey, reward, achievedAtEpochMillis)

fun MilestoneEntity.toDomain() = Milestone(id, kind, instanceKey, reward, achievedAtEpochMillis)

fun ActiveTimer.toEntity() = ActiveTimerEntity(
    targetId = targetId,
    startedAtEpochMillis = startedAtEpochMillis,
    accumulatedMillis = accumulatedMillis,
    paused = paused,
    lastPlanningDate = lastPlanningDate,
    completedSegmentsJson = roomJson.encodeToString(timerSegmentListSerializer, completedSegments),
)

fun ActiveTimerEntity.toDomain() = ActiveTimer(
    targetId = targetId,
    startedAtEpochMillis = startedAtEpochMillis,
    accumulatedMillis = accumulatedMillis,
    paused = paused,
    lastPlanningDate = lastPlanningDate,
    completedSegments = roomJson.decodeFromString(timerSegmentListSerializer, completedSegmentsJson),
)
