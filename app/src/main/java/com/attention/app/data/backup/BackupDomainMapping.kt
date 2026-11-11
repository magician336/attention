package com.attention.app.data.backup

import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.FutureGoalRule
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.LaunchDestination
import com.attention.domain.Migration
import com.attention.domain.Milestone
import com.attention.domain.PeriodSnapshot
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.TargetMove
import com.attention.domain.TimeEntry
import com.attention.domain.TimeEntrySource
import com.attention.domain.TimerSegment
import com.attention.domain.recalculateExperience

/*
 * 领域状态 ↔ version 1 备份协议模型之间**唯一**的转换处。
 *
 * 两个方向在这里成对出现：每个类型的 `toProtocol()` 紧挨着它的 `toDomain()`，字段顺序也按同一
 * 顺序写。改协议字段只需要改这里，不必去动 [AttentionBackupCodec] 的编解码流程——编解码只负责
 * 读字节、校验、归一，不负责翻译类型。
 *
 * 顶层集合之间只通过字符串外键互相引用，没有类型直接嵌套另一个，所以每个集合都是一个扁平行列，
 * 映射也只是逐行转换。协议内部唯一的对象嵌套是活动计时器里的计时片段列表。
 *
 * 达成里程碑的实例键去重仍然只发生在导入边界（[toDomainState]）与导出边界
 * （[AttentionBackup.fromDomain]），不在这里——状态指纹要靠 [AttentionBackup.fromDomainForFingerprint]
 * 保留重复行，才能区分「多了一行重复」与「什么都没变」。
 */

/** 领域状态 → 协议模型。[deduplicateMilestones] 见 [AttentionBackup.fromDomain]。 */
internal fun AttentionState.toProtocol(deduplicateMilestones: Boolean): AttentionBackup {
    // 先归一里程碑台账，再推导投入经验，让写出去的行与写出去的经验来自同一份集合。
    val canonical = copy(
        milestones = if (deduplicateMilestones) {
            milestones.distinctBy { it.instanceKey }
        } else {
            milestones
        },
    ).recalculateExperience()
    return AttentionBackup(
        settings = canonical.settings.toProtocol(),
        targets = canonical.targets.map(Target::toProtocol),
        goalStages = canonical.goalStages.map(GoalStage::toProtocol),
        timeEntries = canonical.timeEntries.map(TimeEntry::toProtocol),
        schedules = canonical.schedules.map(ScheduleEntry::toProtocol),
        recurrenceRules = canonical.recurrenceRules.map(RecurrenceRule::toProtocol),
        migrations = canonical.migrations.map(Migration::toProtocol),
        futureGoalRules = canonical.futureGoalRules.map(FutureGoalRule::toProtocol),
        periodSnapshots = canonical.periodSnapshots.map(PeriodSnapshot::toProtocol),
        targetMoves = canonical.targetMoves.map(TargetMove::toProtocol),
        milestones = canonical.milestones.map(Milestone::toProtocol),
        experience = canonical.experience,
        activeTimer = canonical.activeTimer?.toProtocol(),
        onboardingCompleted = canonical.onboardingCompleted,
        launchDestination = canonical.launchDestination.toProtocol(),
        lastOpenedDestination = canonical.lastOpenedDestination.toProtocol(),
        notificationsEnabled = canonical.notificationsEnabled,
    )
}

/** 协议模型 → 领域状态。[AttentionBackup] 的字段名与本函数参数名一一对应。 */
fun AttentionBackup.toDomainState(): AttentionState = AttentionState(
    settings = settings.toDomain(),
    targets = targets.map(BackupTarget::toDomain),
    goalStages = goalStages.map(BackupGoalStage::toDomain),
    timeEntries = timeEntries.map(BackupTimeEntry::toDomain),
    schedules = schedules.map(BackupScheduleEntry::toDomain),
    recurrenceRules = recurrenceRules.map(BackupRecurrenceRule::toDomain),
    migrations = migrations.map(BackupMigration::toDomain),
    futureGoalRules = futureGoalRules.map(BackupFutureGoalRule::toDomain),
    periodSnapshots = periodSnapshots.map(BackupPeriodSnapshot::toDomain),
    targetMoves = targetMoves.map(BackupTargetMove::toDomain),
    // 实例键是领域台账的身份。旧版本备份可能保留了重复行，所以在这里——导入边界——归一，
    // 而不是等到推导投入经验之后。
    milestones = milestones.map(BackupMilestone::toDomain).distinctBy { it.instanceKey },
    experience = experience,
    activeTimer = activeTimer?.toDomain(),
    onboardingCompleted = onboardingCompleted,
    launchDestination = launchDestination.toDomain(),
    lastOpenedDestination = lastOpenedDestination.toDomain(),
    notificationsEnabled = notificationsEnabled,
).recalculateExperience()

internal fun StoredSettings.toProtocol(): BackupStoredSettings = BackupStoredSettings(
    planningDayBoundaryMinutes = planningDayBoundaryMinutes,
    weekStartDay = weekStartDay,
    dailyCapacityMinutes = dailyCapacityMinutes,
)

internal fun BackupStoredSettings.toDomain(): StoredSettings = StoredSettings(
    planningDayBoundaryMinutes = planningDayBoundaryMinutes,
    weekStartDay = weekStartDay,
    dailyCapacityMinutes = dailyCapacityMinutes,
)

internal fun Target.toProtocol(): BackupTarget = BackupTarget(
    id = id,
    parentId = parentId,
    title = title,
    note = note,
    icon = icon,
    colorHex = colorHex,
    archived = archived,
    sortOrder = sortOrder,
    expanded = expanded,
)

internal fun BackupTarget.toDomain(): Target = Target(
    id = id,
    parentId = parentId,
    title = title,
    note = note,
    icon = icon,
    colorHex = colorHex,
    archived = archived,
    sortOrder = sortOrder,
    expanded = expanded,
)

internal fun GoalStage.toProtocol(): BackupGoalStage = BackupGoalStage(
    id = id,
    targetId = targetId,
    cadence = cadence.toProtocol(),
    targetMinutes = targetMinutes,
    startDate = startDate,
    dueDate = dueDate,
    completed = completed,
)

internal fun BackupGoalStage.toDomain(): GoalStage = GoalStage(
    id = id,
    targetId = targetId,
    cadence = cadence.toDomain(),
    targetMinutes = targetMinutes,
    startDate = startDate,
    dueDate = dueDate,
    completed = completed,
)

internal fun TimeEntry.toProtocol(): BackupTimeEntry = BackupTimeEntry(
    id = id,
    planningDate = planningDate,
    durationMinutes = durationMinutes,
    targetId = targetId,
    source = source.toProtocol(),
    occurredAtEpochMillis = occurredAtEpochMillis,
    note = note,
    scheduleEntryId = scheduleEntryId,
)

internal fun BackupTimeEntry.toDomain(): TimeEntry = TimeEntry(
    id = id,
    planningDate = planningDate,
    durationMinutes = durationMinutes,
    targetId = targetId,
    source = source.toDomain(),
    occurredAtEpochMillis = occurredAtEpochMillis,
    note = note,
    scheduleEntryId = scheduleEntryId,
)

internal fun ScheduleEntry.toProtocol(): BackupScheduleEntry = BackupScheduleEntry(
    id = id,
    planningDate = planningDate,
    title = title,
    startMinute = startMinute,
    endMinute = endMinute,
    estimatedMinutes = estimatedMinutes,
    note = note,
    targetId = targetId,
    completed = completed,
    reminderEpochMillis = reminderEpochMillis,
    recurrenceRuleId = recurrenceRuleId,
)

internal fun BackupScheduleEntry.toDomain(): ScheduleEntry = ScheduleEntry(
    id = id,
    planningDate = planningDate,
    title = title,
    startMinute = startMinute,
    endMinute = endMinute,
    estimatedMinutes = estimatedMinutes,
    note = note,
    targetId = targetId,
    completed = completed,
    reminderEpochMillis = reminderEpochMillis,
    recurrenceRuleId = recurrenceRuleId,
)

internal fun RecurrenceRule.toProtocol(): BackupRecurrenceRule = BackupRecurrenceRule(
    id = id,
    title = title,
    startDate = startDate,
    frequency = frequency.toProtocol(),
    weekdays = weekdays,
    dayOfMonth = dayOfMonth,
    untilDate = untilDate,
    active = active,
    estimatedMinutes = estimatedMinutes,
    targetId = targetId,
    note = note,
    reminderMinuteOfDay = reminderMinuteOfDay,
)

internal fun BackupRecurrenceRule.toDomain(): RecurrenceRule = RecurrenceRule(
    id = id,
    title = title,
    startDate = startDate,
    frequency = frequency.toDomain(),
    weekdays = weekdays,
    dayOfMonth = dayOfMonth,
    untilDate = untilDate,
    active = active,
    estimatedMinutes = estimatedMinutes,
    targetId = targetId,
    note = note,
    reminderMinuteOfDay = reminderMinuteOfDay,
)

internal fun Migration.toProtocol(): BackupMigration = BackupMigration(
    id = id,
    targetId = targetId,
    sourceStageId = sourceStageId,
    minutes = minutes,
    destinationStartDate = destinationStartDate,
    destinationEndDate = destinationEndDate,
    cancelled = cancelled,
)

internal fun BackupMigration.toDomain(): Migration = Migration(
    id = id,
    targetId = targetId,
    sourceStageId = sourceStageId,
    minutes = minutes,
    destinationStartDate = destinationStartDate,
    destinationEndDate = destinationEndDate,
    cancelled = cancelled,
)

internal fun FutureGoalRule.toProtocol(): BackupFutureGoalRule = BackupFutureGoalRule(
    id = id,
    targetId = targetId,
    stageId = stageId,
    cadence = cadence.toProtocol(),
    targetMinutes = targetMinutes,
    effectiveFrom = effectiveFrom,
    dueDate = dueDate,
)

internal fun BackupFutureGoalRule.toDomain(): FutureGoalRule = FutureGoalRule(
    id = id,
    targetId = targetId,
    stageId = stageId,
    cadence = cadence.toDomain(),
    targetMinutes = targetMinutes,
    effectiveFrom = effectiveFrom,
    dueDate = dueDate,
)

internal fun PeriodSnapshot.toProtocol(): BackupPeriodSnapshot = BackupPeriodSnapshot(
    id = id,
    targetId = targetId,
    cadence = cadence.toProtocol(),
    periodStart = periodStart,
    periodEnd = periodEnd,
    targetMinutes = targetMinutes,
    actualMinutes = actualMinutes,
    gapMinutes = gapMinutes,
    excessMinutes = excessMinutes,
    completed = completed,
    stageId = stageId,
)

internal fun BackupPeriodSnapshot.toDomain(): PeriodSnapshot = PeriodSnapshot(
    id = id,
    targetId = targetId,
    cadence = cadence.toDomain(),
    periodStart = periodStart,
    periodEnd = periodEnd,
    targetMinutes = targetMinutes,
    actualMinutes = actualMinutes,
    gapMinutes = gapMinutes,
    excessMinutes = excessMinutes,
    completed = completed,
    stageId = stageId,
)

internal fun TargetMove.toProtocol(): BackupTargetMove = BackupTargetMove(
    id = id,
    targetId = targetId,
    parentId = parentId,
    effectiveFrom = effectiveFrom,
)

internal fun BackupTargetMove.toDomain(): TargetMove = TargetMove(
    id = id,
    targetId = targetId,
    parentId = parentId,
    effectiveFrom = effectiveFrom,
)

internal fun Milestone.toProtocol(): BackupMilestone = BackupMilestone(
    id = id,
    kind = kind,
    instanceKey = instanceKey,
    reward = reward,
    achievedAtEpochMillis = achievedAtEpochMillis,
)

internal fun BackupMilestone.toDomain(): Milestone = Milestone(
    id = id,
    kind = kind,
    instanceKey = instanceKey,
    reward = reward,
    achievedAtEpochMillis = achievedAtEpochMillis,
)

internal fun ActiveTimer.toProtocol(): BackupActiveTimer = BackupActiveTimer(
    targetId = targetId,
    startedAtEpochMillis = startedAtEpochMillis,
    accumulatedMillis = accumulatedMillis,
    paused = paused,
    lastPlanningDate = lastPlanningDate,
    completedSegments = completedSegments.map(TimerSegment::toProtocol),
    scheduleEntryId = scheduleEntryId,
)

internal fun BackupActiveTimer.toDomain(): ActiveTimer = ActiveTimer(
    targetId = targetId,
    startedAtEpochMillis = startedAtEpochMillis,
    accumulatedMillis = accumulatedMillis,
    paused = paused,
    lastPlanningDate = lastPlanningDate,
    completedSegments = completedSegments.map(BackupTimerSegment::toDomain),
    scheduleEntryId = scheduleEntryId,
)

internal fun TimerSegment.toProtocol(): BackupTimerSegment = BackupTimerSegment(
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
)

internal fun BackupTimerSegment.toDomain(): TimerSegment = TimerSegment(
    startedAtEpochMillis = startedAtEpochMillis,
    endedAtEpochMillis = endedAtEpochMillis,
)

/*
 * 枚举的转换：协议枚举与领域枚举名字一一对应，写法是穷举 `when` 而不是查表或 `valueOf`。
 *
 * 穷举有两个好处：协议或领域新增一个枚举常量，这里**编译不过**，而不是把新常量悄悄读成 null；
 * 并且两侧常量名的对应关系一眼可见，不会被一张藏在字符串里的表掩盖。
 *
 * 注意：这里搬的是**已经被解析成枚举常量**的值，不是线上字符串。线上名字的解析发生在协议解码时
 * （`BackupProtocolEnums`），领域侧的解析发生在 Room 映射与 DataStore 设置读取时，两边对同一个
 * 无法识别的名字必须给出同一个常量——这件事由单元测试钉住，不靠这里的转换保证。
 */

internal fun GoalCadence.toProtocol(): BackupGoalCadence = when (this) {
    GoalCadence.DAILY -> BackupGoalCadence.DAILY
    GoalCadence.WEEKLY -> BackupGoalCadence.WEEKLY
    GoalCadence.MONTHLY -> BackupGoalCadence.MONTHLY
    GoalCadence.ONE_TIME -> BackupGoalCadence.ONE_TIME
}

internal fun BackupGoalCadence.toDomain(): GoalCadence = when (this) {
    BackupGoalCadence.DAILY -> GoalCadence.DAILY
    BackupGoalCadence.WEEKLY -> GoalCadence.WEEKLY
    BackupGoalCadence.MONTHLY -> GoalCadence.MONTHLY
    BackupGoalCadence.ONE_TIME -> GoalCadence.ONE_TIME
}

internal fun TimeEntrySource.toProtocol(): BackupTimeEntrySource = when (this) {
    TimeEntrySource.MANUAL -> BackupTimeEntrySource.MANUAL
    TimeEntrySource.TIMER -> BackupTimeEntrySource.TIMER
    TimeEntrySource.SCHEDULE_CONFIRMED -> BackupTimeEntrySource.SCHEDULE_CONFIRMED
    TimeEntrySource.IMPORT -> BackupTimeEntrySource.IMPORT
}

internal fun BackupTimeEntrySource.toDomain(): TimeEntrySource = when (this) {
    BackupTimeEntrySource.MANUAL -> TimeEntrySource.MANUAL
    BackupTimeEntrySource.TIMER -> TimeEntrySource.TIMER
    BackupTimeEntrySource.SCHEDULE_CONFIRMED -> TimeEntrySource.SCHEDULE_CONFIRMED
    BackupTimeEntrySource.IMPORT -> TimeEntrySource.IMPORT
}

internal fun ScheduleFrequency.toProtocol(): BackupScheduleFrequency = when (this) {
    ScheduleFrequency.DAILY -> BackupScheduleFrequency.DAILY
    ScheduleFrequency.WEEKLY -> BackupScheduleFrequency.WEEKLY
    ScheduleFrequency.MONTHLY -> BackupScheduleFrequency.MONTHLY
}

internal fun BackupScheduleFrequency.toDomain(): ScheduleFrequency = when (this) {
    BackupScheduleFrequency.DAILY -> ScheduleFrequency.DAILY
    BackupScheduleFrequency.WEEKLY -> ScheduleFrequency.WEEKLY
    BackupScheduleFrequency.MONTHLY -> ScheduleFrequency.MONTHLY
}

/**
 * 启动入口的转换是纯按名的：协议保留 `TODAY` 与 `ALL_DATES` 这两个旧入口值（它们仍然能被解码出来），
 * 折成「日程」由 [asAgendaEntry] 在需要顶级工作区时做，两件事仍然分开。
 */
internal fun LaunchDestination.toProtocol(): BackupLaunchDestination = when (this) {
    LaunchDestination.TODAY -> BackupLaunchDestination.TODAY
    LaunchDestination.TARGETS -> BackupLaunchDestination.TARGETS
    LaunchDestination.ALL_DATES -> BackupLaunchDestination.ALL_DATES
    LaunchDestination.STATISTICS -> BackupLaunchDestination.STATISTICS
    LaunchDestination.LAST_OPENED -> BackupLaunchDestination.LAST_OPENED
    LaunchDestination.AGENDA -> BackupLaunchDestination.AGENDA
}

internal fun BackupLaunchDestination.toDomain(): LaunchDestination = when (this) {
    BackupLaunchDestination.TODAY -> LaunchDestination.TODAY
    BackupLaunchDestination.TARGETS -> LaunchDestination.TARGETS
    BackupLaunchDestination.ALL_DATES -> LaunchDestination.ALL_DATES
    BackupLaunchDestination.STATISTICS -> LaunchDestination.STATISTICS
    BackupLaunchDestination.LAST_OPENED -> LaunchDestination.LAST_OPENED
    BackupLaunchDestination.AGENDA -> LaunchDestination.AGENDA
}
