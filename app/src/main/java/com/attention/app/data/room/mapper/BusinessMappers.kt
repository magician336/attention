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
import com.attention.domain.GoalCadenceSerializer
import com.attention.domain.ScheduleFrequencySerializer
import com.attention.domain.TimeEntrySourceSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val roomJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }
private val intListSerializer = ListSerializer(Int.serializer())
private val timerSegmentListSerializer = ListSerializer(TimerSegment.serializer())

/**
 * 这里的枚举列同样按名存进 Room，所以同样走 `PersistedEnumSerializer` 的解析，而不是
 * `valueOf`。应用升级后存量行还在本地库里：某个枚举值一旦改名或删除，`valueOf` 会抛
 * `IllegalArgumentException` 把整库读取打穿，一条行读不出来就等于整个应用打不开。
 */

fun Target.toEntity() = TargetEntity(id, parentId, title, note, icon, colorHex, archived, sortOrder, expanded)

fun TargetEntity.toDomain() = Target(id, parentId, title, note, icon, colorHex, archived, sortOrder, expanded)

fun GoalStage.toEntity() = GoalStageEntity(id, targetId, cadence.name, targetMinutes, startDate, dueDate, completed)

fun GoalStageEntity.toDomain() = GoalStage(id, targetId, GoalCadenceSerializer.fromPersistedName(cadence), targetMinutes, startDate, dueDate, completed)

fun TimeEntry.toEntity() = TimeEntryEntity(id, planningDate, durationMinutes, targetId, source.name, occurredAtEpochMillis, note, scheduleEntryId, backfillRequestId)

fun TimeEntryEntity.toDomain() = TimeEntry(id, planningDate, durationMinutes, targetId, TimeEntrySourceSerializer.fromPersistedName(source), occurredAtEpochMillis, note, scheduleEntryId, backfillRequestId)

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
    frequency = ScheduleFrequencySerializer.fromPersistedName(frequency),
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

fun FutureGoalRuleEntity.toDomain() = FutureGoalRule(id, targetId, stageId.orEmpty(), GoalCadenceSerializer.fromPersistedName(cadence), targetMinutes, effectiveFrom, dueDate)

fun PeriodSnapshot.toEntity() = PeriodSnapshotEntity(
    id = id,
    targetId = targetId,
    cadence = cadence.name,
    periodStart = periodStart,
    periodEnd = periodEnd,
    targetMinutes = targetMinutes,
    actualMinutes = actualMinutes,
    gapMinutes = gapMinutes,
    excessMinutes = excessMinutes,
    completed = completed,
    stageId = stageId,
)

fun PeriodSnapshotEntity.toDomain() = PeriodSnapshot(
    id = id,
    targetId = targetId,
    cadence = GoalCadenceSerializer.fromPersistedName(cadence),
    periodStart = periodStart,
    periodEnd = periodEnd,
    targetMinutes = targetMinutes,
    actualMinutes = actualMinutes,
    gapMinutes = gapMinutes,
    excessMinutes = excessMinutes,
    completed = completed,
    stageId = stageId,
)

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
    scheduleEntryId = scheduleEntryId,
)

fun ActiveTimerEntity.toDomain() = ActiveTimer(
    targetId = targetId,
    startedAtEpochMillis = startedAtEpochMillis,
    accumulatedMillis = accumulatedMillis,
    paused = paused,
    lastPlanningDate = lastPlanningDate,
    completedSegments = roomJson.decodeFromString(timerSegmentListSerializer, completedSegmentsJson),
    scheduleEntryId = scheduleEntryId,
)
