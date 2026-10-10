package com.attention.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun AttentionState.planningDate(
    instant: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): LocalDate {
    val local = instant.atZone(zone)
    return local.toLocalDate().let {
        if (local.toLocalTime().toSecondOfDay() / 60 < settings.planningDayBoundaryMinutes) it.minusDays(1) else it
    }
}

fun AttentionState.targetChildren(parentId: String?, onDate: LocalDate? = null): List<Target> = targets
    .filter { target ->
        !target.archived && (onDate?.let { parentAt(target.id, it) == parentId } ?: (target.parentId == parentId))
    }
    .sortedBy { it.sortOrder }

fun AttentionState.descendantIds(targetId: String): Set<String> {
    val result = mutableSetOf(targetId)
    var changed = true
    while (changed) {
        changed = false
        targets.filter { it.parentId in result }.forEach { changed = result.add(it.id) || changed }
    }
    return result
}

fun AttentionState.descendantIdsAt(targetId: String, onDate: LocalDate): Set<String> = targets
    .filter { isDescendantAt(it.id, targetId, onDate) }
    .mapTo(mutableSetOf()) { it.id }

private fun AttentionState.isDescendantAt(candidateId: String, ancestorId: String, date: LocalDate): Boolean {
    var current: String? = candidateId
    val seen = mutableSetOf<String>()
    while (current != null && seen.add(current)) {
        if (current == ancestorId) return true
        current = parentAt(current, date)
    }
    return false
}

fun AttentionState.directMinutes(targetId: String, date: String? = null): Int = timeEntries
    .asSequence()
    .filter { it.targetId == targetId && (date == null || it.planningDate == date) }
    .sumOf { it.durationMinutes }

fun AttentionState.subtreeMinutes(targetId: String, date: String? = null): Int {
    return if (date == null) {
        timeEntries
            .filter { it.targetId != null }
            .filter { isDescendantAt(it.targetId!!, targetId, LocalDate.parse(it.planningDate)) }
            .sumOf { it.durationMinutes }
    } else {
        val parsed = LocalDate.parse(date)
        timeEntries.filter { it.planningDate == date && it.targetId != null && isDescendantAt(it.targetId, targetId, parsed) }
            .sumOf { it.durationMinutes }
    }
}

fun AttentionState.periodRange(
    date: LocalDate,
    cadence: GoalCadence,
): ClosedRange<LocalDate> = when (cadence) {
    GoalCadence.DAILY -> date..date
    GoalCadence.WEEKLY -> {
        val start = date.minusDays(((date.dayOfWeek.value - settings.weekStartDay + 7) % 7).toLong())
        start..start.plusDays(6)
    }
    GoalCadence.MONTHLY -> date.withDayOfMonth(1)..date.withDayOfMonth(date.lengthOfMonth())
    GoalCadence.ONE_TIME -> date..date
}

fun AttentionState.progressRange(stage: GoalStage, onDate: LocalDate): ClosedRange<LocalDate> {
    val effective = effectiveStage(stage, onDate)
    val start = LocalDate.parse(effective.startDate)
    if (onDate.isBefore(start)) return start..start
    if (effective.cadence != GoalCadence.ONE_TIME) return periodRange(onDate, effective.cadence)
    val due = effective.dueDate?.let(LocalDate::parse)
    val nextStageStart = goalStagesForTarget(stage.targetId)
        .asSequence()
        .filter { it.cadence == GoalCadence.ONE_TIME && it.id != stage.id }
        .map { LocalDate.parse(it.startDate) }
        .filter { it.isAfter(start) }
        .minOrNull()
    val naturalEnd = listOfNotNull(due, nextStageStart?.minusDays(1)).minOrNull()
    val end = minOf(onDate, naturalEnd ?: onDate).let { maxOf(start, it) }
    return start..end
}

fun AttentionState.progress(stage: GoalStage, onDate: LocalDate): GoalProgress {
    val effective = effectiveStage(stage, onDate)
    if (onDate.isBefore(LocalDate.parse(effective.startDate))) return GoalProgress(0, 0, 0, 0, false)
    val actual = entriesForProgress(stage, onDate).sumOf { it.durationMinutes }
    val gap = (effective.targetMinutes - actual).coerceAtLeast(0)
    return GoalProgress(effective.targetMinutes, actual, gap, (actual - effective.targetMinutes).coerceAtLeast(0), actual >= effective.targetMinutes)
}

private fun AttentionState.entriesForProgress(
    stage: GoalStage,
    onDate: LocalDate,
    excludedEntryIds: Set<String> = emptySet(),
): List<TimeEntry> {
    val effective = effectiveStage(stage, onDate)
    if (onDate.isBefore(LocalDate.parse(effective.startDate))) return emptyList()
    val range = progressRange(stage, onDate)
    return timeEntries.filter { it.id !in excludedEntryIds && it.targetId != null }
        .filter { LocalDate.parse(it.planningDate) in range }
        .filter { isDescendantAt(it.targetId!!, effective.targetId, LocalDate.parse(it.planningDate)) }
}

private fun AttentionState.progressForSummary(
    stage: GoalStage,
    onDate: LocalDate,
    claimedEntryIds: MutableSet<String>,
): GoalProgress {
    val effective = effectiveStage(stage, onDate)
    val targetMinutes = effective.targetMinutes
    val start = LocalDate.parse(effective.startDate)
    if (onDate.isBefore(start)) return GoalProgress(targetMinutes, 0, targetMinutes, 0, false)

    val matchingEntries = entriesForProgress(stage, onDate, claimedEntryIds)
    claimedEntryIds += matchingEntries.map { it.id }
    val actual = matchingEntries.sumOf { it.durationMinutes }
    val gap = (targetMinutes - actual).coerceAtLeast(0)
    return GoalProgress(targetMinutes, actual, gap, (actual - targetMinutes).coerceAtLeast(0), actual >= targetMinutes)
}

/**
 * Returns the target-level roll-up for all one-time stages owned by a target.
 * Each matching time entry is claimed by at most one stage in this summary,
 * while hierarchy membership remains date-aware so historical target moves are
 * evaluated against the tree that existed on the entry's planning date.
 */
fun AttentionState.goalSummary(targetId: String, onDate: LocalDate): GoalSummary {
    val claimedEntryIds = mutableSetOf<String>()
    val stageProgresses = goalStagesForTarget(targetId)
        .filter { it.cadence == GoalCadence.ONE_TIME }
        .map { stage ->
            GoalStageSummary(stage, progressForSummary(stage, onDate, claimedEntryIds))
        }
    val targetMinutes = stageProgresses.sumOf { it.progress.targetMinutes }
    val actualMinutes = stageProgresses.sumOf { it.progress.actualMinutes }
    val gapMinutes = stageProgresses.sumOf { it.progress.gapMinutes }
    val excessMinutes = stageProgresses.sumOf { it.progress.excessMinutes }
    return GoalSummary(
        targetId = targetId,
        stageProgresses = stageProgresses,
        targetMinutes = targetMinutes,
        actualMinutes = actualMinutes,
        gapMinutes = gapMinutes,
        excessMinutes = excessMinutes,
        completed = stageProgresses.isNotEmpty() && stageProgresses.all { it.progress.completed },
    )
}

fun AttentionState.effectiveStage(stage: GoalStage, onDate: LocalDate): GoalStage {
    val rule = goalRuleAt(stage.targetId, stage.id, onDate)
        ?: return stage
    return stage.copy(
        cadence = rule.cadence,
        targetMinutes = rule.targetMinutes,
        startDate = rule.effectiveFrom,
        dueDate = rule.dueDate,
    )
}

fun AttentionState.capacitySummary(date: String): CapacitySummary {
    val capacity = settings.dailyCapacityMinutes
    val used = schedules.filter { it.planningDate == date }.sumOf { schedule ->
        schedule.estimatedMinutes ?: schedule.startMinute?.let { start -> schedule.endMinute?.minus(start) } ?: 0
    }
    return CapacitySummary(capacity, used, capacity != null && used > capacity)
}

fun AttentionState.periodStats(date: LocalDate, cadence: GoalCadence): PeriodStats {
    val range = periodRange(date, cadence)
    val entries = timeEntries.filter { LocalDate.parse(it.planningDate) in range }
    val actual = entries.sumOf { it.durationMinutes }
    val unowned = entries.filter { it.targetId == null }.sumOf { it.durationMinutes }
    val byTarget = entries.filter { it.targetId != null }
        .groupBy { it.targetId!! }
        .mapValues { (_, values) -> values.sumOf { it.durationMinutes } }
    val targetMinutes = goalStages
        .filter { it.cadence == cadence && LocalDate.parse(it.startDate) <= range.endInclusive }
        .sumOf { stage ->
            when (stage.cadence) {
                GoalCadence.DAILY -> stage.targetMinutes * (range.endInclusive.toEpochDay() - maxOf(range.start.toEpochDay(), LocalDate.parse(stage.startDate).toEpochDay()) + 1).toInt()
                GoalCadence.ONE_TIME -> if (stage.dueDate == null || LocalDate.parse(stage.dueDate) >= range.start) stage.targetMinutes else 0
                else -> stage.targetMinutes
            }
        }
    val migrationMinutes = migrations.filterNot { it.cancelled }.filter {
        val start = LocalDate.parse(it.destinationStartDate)
        val end = it.destinationEndDate?.let(LocalDate::parse) ?: start
        start <= range.endInclusive && end >= range.start
    }.sumOf { it.minutes }
    return PeriodStats(range, targetMinutes, actual, unowned, migrationMinutes, (actual - targetMinutes).coerceAtLeast(0), byTarget)
}

fun AttentionState.validTarget(targetId: String?): Boolean = targetId == null || targets.any { it.id == targetId }

private fun AttentionState.validRecordTarget(targetId: String?): Boolean =
    targetId == null || targets.any { it.id == targetId && !it.archived }

fun AttentionState.addTarget(title: String, parentId: String? = null): AttentionState {
    require(title.isNotBlank())
    require(parentId == null || targets.any { it.id == parentId })
    val siblingOrder = targets.filter { it.parentId == parentId }.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
    return copy(targets = targets + Target(title = title.trim(), parentId = parentId, sortOrder = siblingOrder))
}

fun AttentionState.renameTarget(targetId: String, title: String): AttentionState {
    require(title.isNotBlank())
    require(targets.any { it.id == targetId })
    return copy(targets = targets.map { if (it.id == targetId) it.copy(title = title.trim()) else it })
}

fun AttentionState.setTargetExpanded(targetId: String, expanded: Boolean): AttentionState = copy(
    targets = targets.map { if (it.id == targetId) it.copy(expanded = expanded) else it },
)

fun AttentionState.moveTarget(targetId: String, newParentId: String?): AttentionState {
    require(targets.any { it.id == targetId })
    require(newParentId == null || targets.any { it.id == newParentId })
    require(newParentId == null || newParentId !in descendantIds(targetId))
    val target = targets.first { it.id == targetId }
    if (target.parentId == newParentId) return this
    val nextOrder = targets.filter { it.parentId == newParentId && it.id != targetId }
        .maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
    return copy(targets = targets.map { if (it.id == targetId) it.copy(parentId = newParentId, sortOrder = nextOrder) else it })
}

fun AttentionState.reorderTarget(targetId: String, newIndex: Int): AttentionState {
    val target = targets.firstOrNull { it.id == targetId } ?: error("目标不存在")
    val siblings = targets.filter { it.parentId == target.parentId }.sortedBy { it.sortOrder }.toMutableList()
    require(newIndex in siblings.indices)
    siblings.remove(target)
    siblings.add(newIndex, target)
    val order = siblings.mapIndexed { index, item -> item.id to index }.toMap()
    return copy(targets = targets.map { item -> order[item.id]?.let { item.copy(sortOrder = it) } ?: item })
}

fun AttentionState.archiveTarget(targetId: String, archived: Boolean = true): AttentionState = copy(
    targets = targets.map { if (it.id == targetId) it.copy(archived = archived) else it },
)

fun AttentionState.deleteTargetRelations(targetIds: Set<String>): AttentionState {
    val ids = targetIds.flatMap(::descendantIds).toSet()
    return copy(
        targets = targets.filterNot { it.id in ids },
        goalStages = goalStages.filterNot { it.targetId in ids },
        timeEntries = timeEntries.map { if (it.targetId in ids) it.copy(targetId = null) else it },
        schedules = schedules.map { if (it.targetId in ids) it.copy(targetId = null) else it },
        migrations = migrations.filterNot { it.targetId in ids },
        futureGoalRules = futureGoalRules.filterNot { it.targetId in ids },
        targetMoves = targetMoves
            .filterNot { it.targetId in ids }
            .map { move -> if (move.parentId != null && move.parentId in ids) move.copy(parentId = null) else move },
    )
}

fun AttentionState.addGoalStage(
    targetId: String,
    cadence: GoalCadence,
    targetMinutes: Int,
    startDate: String,
    dueDate: String? = null,
): AttentionState {
    require(targets.any { it.id == targetId && !it.archived }) { "目标必须未归档" }
    require(targetMinutes > 0)
    require(cadence != GoalCadence.ONE_TIME || goalStages.none { it.targetId == targetId && it.cadence == GoalCadence.ONE_TIME }) {
        "已有一次性阶段，请使用追加阶段"
    }
    val parsedStart = LocalDate.parse(startDate)
    val parsedDue = dueDate?.let(LocalDate::parse)
    require(parsedDue == null || !parsedDue.isBefore(parsedStart)) { "截止日期不能早于开始日期" }
    return copy(goalStages = goalStages + GoalStage(targetId = targetId, cadence = cadence, targetMinutes = targetMinutes, startDate = startDate, dueDate = dueDate))
}

/**
 * Returns a target's stages in the order in which their planning dates begin.
 * The stable stage ID breaks ties so the result remains deterministic after a
 * backup restore or a Room query.
 */
fun AttentionState.goalStagesForTarget(targetId: String): List<GoalStage> = goalStages
    .filter { it.targetId == targetId }
    .sortedWith(compareBy<GoalStage> { LocalDate.parse(it.startDate) }.thenBy { it.id })

/**
 * Appends a new one-time stage after the target's latest stage is achieved.
 * Validation happens before creating the new stage, so a rejected command
 * leaves every existing collection unchanged.
 */
fun AttentionState.appendOneTimeGoalStage(
    targetId: String,
    targetMinutes: Int,
    startDate: String,
    dueDate: String? = null,
): AttentionState {
    require(targets.any { it.id == targetId && !it.archived }) { "目标必须未归档" }
    require(targetMinutes > 0) { "目标分钟必须为正整数" }

    val parsedStart = LocalDate.parse(startDate)
    val parsedDue = dueDate?.let(LocalDate::parse)
    require(parsedDue == null || !parsedDue.isBefore(parsedStart)) { "截止日期不能早于开始日期" }

    val previous = goalStagesForTarget(targetId).lastOrNull()
    require(previous != null) { "目标没有可追加的一次性阶段" }
    require(previous.cadence == GoalCadence.ONE_TIME) { "只能在一次性阶段后追加" }
    require(parsedStart.isAfter(LocalDate.parse(previous.startDate))) { "新阶段开始日期必须晚于上一阶段" }
    require(progress(previous, parsedStart).completed) { "上一阶段尚未达成" }

    return copy(
        goalStages = goalStages + GoalStage(
            targetId = targetId,
            cadence = GoalCadence.ONE_TIME,
            targetMinutes = targetMinutes,
            startDate = startDate,
            dueDate = dueDate,
        ),
    )
}

fun AttentionState.addTimeEntry(
    planningDate: String,
    durationMinutes: Int,
    targetId: String? = null,
    source: TimeEntrySource = TimeEntrySource.MANUAL,
    occurredAtEpochMillis: Long? = null,
    note: String = "",
): AttentionState {
    LocalDate.parse(planningDate)
    require(durationMinutes > 0)
    require(validRecordTarget(targetId)) { "时间记录只能归入未归档目标" }
    return copy(timeEntries = timeEntries + TimeEntry(planningDate = planningDate, durationMinutes = durationMinutes, targetId = targetId, source = source, occurredAtEpochMillis = occurredAtEpochMillis, note = note))
}

fun AttentionState.editTimeEntry(entryId: String, durationMinutes: Int, targetId: String?, note: String = ""): AttentionState {
    require(durationMinutes > 0 && validRecordTarget(targetId)) { "时间记录只能归入未归档目标" }
    require(timeEntries.any { it.id == entryId }) { "时间记录不存在" }
    return copy(timeEntries = timeEntries.map { if (it.id == entryId) it.copy(durationMinutes = durationMinutes, targetId = targetId, note = note) else it })
}

fun AttentionState.deleteTimeEntry(entryId: String): AttentionState = copy(timeEntries = timeEntries.filterNot { it.id == entryId })

fun AttentionState.assignUnowned(entryIds: Set<String>, targetId: String): AttentionState {
    if (entryIds.isEmpty()) return this
    require(targets.any { it.id == targetId && !it.archived }) { "时间记录只能归入未归档目标" }
    require(entryIds.all { id -> timeEntries.any { it.id == id && it.targetId == null } }) {
        "只能归入存在且未归属的时间记录"
    }
    return copy(timeEntries = timeEntries.map {
        if (it.id in entryIds && it.targetId == null) it.copy(targetId = targetId) else it
    }).recalculateExperience()
}

fun AttentionState.addSchedule(entry: ScheduleEntry): AttentionState {
    require(entry.title.isNotBlank())
    LocalDate.parse(entry.planningDate)
    require(entry.startMinute == null || entry.startMinute in 0 until 1440)
    require(entry.endMinute == null || entry.endMinute in 1..1440)
    require(entry.estimatedMinutes == null || entry.estimatedMinutes > 0)
    require(validTarget(entry.targetId))
    return copy(schedules = schedules + entry.copy(title = entry.title.trim()))
}

fun AttentionState.updateSchedule(entry: ScheduleEntry): AttentionState = copy(
    schedules = schedules.map { if (it.id == entry.id) entry else it },
)

fun AttentionState.addRecurrence(rule: RecurrenceRule): AttentionState {
    require(rule.title.isNotBlank())
    LocalDate.parse(rule.startDate)
    rule.untilDate?.let(LocalDate::parse)
    require(rule.reminderMinuteOfDay == null || rule.reminderMinuteOfDay in 0 until 1440)
    require(validTarget(rule.targetId))
    return copy(recurrenceRules = recurrenceRules + rule.copy(title = rule.title.trim()))
}

fun AttentionState.stopRecurrence(ruleId: String): AttentionState = copy(
    recurrenceRules = recurrenceRules.map { if (it.id == ruleId) it.copy(active = false) else it },
)

fun AttentionState.occurrences(rule: RecurrenceRule, through: LocalDate): List<ScheduleEntry> {
    val start = LocalDate.parse(rule.startDate)
    val end = minOf(through, rule.untilDate?.let(LocalDate::parse) ?: through)
    if (!rule.active || start.isAfter(end)) return emptyList()
    return generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(end) }
        .filter { date ->
            when (rule.frequency) {
                ScheduleFrequency.DAILY -> true
                ScheduleFrequency.WEEKLY -> date.dayOfWeek.value in rule.weekdays
                ScheduleFrequency.MONTHLY -> date.dayOfMonth == (rule.dayOfMonth ?: start.dayOfMonth)
            }
        }
        .map { date ->
            ScheduleEntry(
                planningDate = date.toString(),
                title = rule.title,
                estimatedMinutes = rule.estimatedMinutes,
                targetId = rule.targetId,
                note = rule.note,
                recurrenceRuleId = rule.id,
                reminderEpochMillis = rule.reminderMinuteOfDay?.let { minute ->
                    date.atStartOfDay(ZoneId.systemDefault()).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
                },
            )
        }
        .toList()
}

fun AttentionState.availableMigrationMinutes(stage: GoalStage, onDate: LocalDate): Int {
    require(goalStages.any { it.id == stage.id && it.targetId == stage.targetId })
    val reserved = migrations
        .filter { it.sourceStageId == stage.id && !it.cancelled }
        .sumOf { it.minutes }
    return (progress(stage, onDate).gapMinutes - reserved).coerceAtLeast(0)
}

fun AttentionState.addMigration(migration: Migration, onDate: LocalDate): AttentionState {
    require(migration.minutes > 0)
    require(!migration.cancelled)
    require(targets.any { it.id == migration.targetId && !it.archived })
    val sourceStage = goalStages.firstOrNull { it.id == migration.sourceStageId && it.targetId == migration.targetId }
        ?: error("来源目标阶段不存在")
    val destinationStart = LocalDate.parse(migration.destinationStartDate)
    val destinationEnd = migration.destinationEndDate?.let(LocalDate::parse)
    require(destinationEnd == null || !destinationEnd.isBefore(destinationStart))
    require(destinationStart.isAfter(progressRange(sourceStage, onDate).endInclusive))
    require(migration.minutes <= availableMigrationMinutes(sourceStage, onDate))
    return copy(migrations = migrations + migration)
}

fun AttentionState.updateMigration(migration: Migration, onDate: LocalDate): AttentionState {
    val existing = migrations.firstOrNull { it.id == migration.id } ?: error("迁移记录不存在")
    require(!existing.cancelled)
    val withoutExisting = copy(migrations = migrations - existing)
    return withoutExisting.addMigration(migration, onDate)
}

fun AttentionState.cancelMigration(migrationId: String): AttentionState {
    require(migrations.any { it.id == migrationId })
    return copy(migrations = migrations.map { if (it.id == migrationId) it.copy(cancelled = true) else it })
}

fun AttentionState.gapFor(stage: GoalStage, onDate: LocalDate): Int = progress(stage, onDate).gapMinutes

/**
 * Adds a future target commitment. [notBefore] is supplied by the caller's
 * current planning day so tests and backup replay do not read the system clock.
 */
fun AttentionState.addFutureGoalRule(
    rule: FutureGoalRule,
    notBefore: LocalDate,
): AttentionState {
    validateFutureGoalRule(rule, notBefore)
    require(futureGoalRules.none { it.id == rule.id }) { "未来目标规则 ID 已存在" }
    require(futureGoalRules.none { it.sameScopeAs(rule) && it.effectiveFrom == rule.effectiveFrom }) {
        "同一目标和阶段在同一生效日只能有一条未来规则"
    }
    return copy(futureGoalRules = futureGoalRules + rule)
}

fun AttentionState.updateFutureGoalRule(
    rule: FutureGoalRule,
    notBefore: LocalDate,
): AttentionState {
    val existing = futureGoalRules.firstOrNull { it.id == rule.id } ?: error("未来目标规则不存在")
    require(existing.targetId == rule.targetId && existing.stageId == rule.stageId) {
        "未来目标规则的目标和阶段不能修改"
    }
    require(LocalDate.parse(existing.effectiveFrom).isAfter(notBefore)) {
        "已经生效的未来目标规则不能修改"
    }
    validateFutureGoalRule(rule, notBefore)
    require(futureGoalRules.none { it.id != rule.id && it.sameScopeAs(rule) && it.effectiveFrom == rule.effectiveFrom }) {
        "同一目标和阶段在同一生效日只能有一条未来规则"
    }
    return copy(futureGoalRules = futureGoalRules.map { if (it.id == rule.id) rule else it })
}

fun AttentionState.cancelFutureGoalRule(ruleId: String, notBefore: LocalDate): AttentionState {
    val existing = futureGoalRules.firstOrNull { it.id == ruleId } ?: error("未来目标规则不存在")
    require(LocalDate.parse(existing.effectiveFrom).isAfter(notBefore)) {
        "已经生效的未来目标规则不能取消"
    }
    return copy(futureGoalRules = futureGoalRules.filterNot { it.id == ruleId })
}

fun AttentionState.goalRulesAt(targetId: String, onDate: LocalDate): List<FutureGoalRule> = futureGoalRules
    .filter { it.targetId == targetId && !LocalDate.parse(it.effectiveFrom).isAfter(onDate) }
    .sortedWith(compareBy<FutureGoalRule> { LocalDate.parse(it.effectiveFrom) }.thenBy { it.id })

fun FutureGoalRule.isPending(onDate: LocalDate): Boolean =
    runCatching { LocalDate.parse(effectiveFrom).isAfter(onDate) }.getOrDefault(false)

fun AttentionState.goalRuleAt(targetId: String, stageId: String?, onDate: LocalDate): FutureGoalRule? = futureGoalRules
    .asSequence()
    .filter { it.targetId == targetId && (it.stageId.isBlank() || it.stageId == stageId) }
    .filter { !LocalDate.parse(it.effectiveFrom).isAfter(onDate) }
    .maxWithOrNull(
        compareBy<FutureGoalRule> { LocalDate.parse(it.effectiveFrom) }
            .thenBy { it.stageId.isNotBlank() }
            .thenBy { it.id },
    )

private fun FutureGoalRule.sameScopeAs(other: FutureGoalRule): Boolean =
    targetId == other.targetId && stageId.trim().ifBlank { "" } == other.stageId.trim().ifBlank { "" }

private fun AttentionState.validateFutureGoalRule(rule: FutureGoalRule, notBefore: LocalDate) {
    require(targets.any { it.id == rule.targetId && !it.archived }) { "未来目标规则只能用于未归档目标" }
    require(rule.stageId.isBlank() || goalStages.any { it.id == rule.stageId && it.targetId == rule.targetId }) {
        "未来目标规则的阶段不存在或不属于目标"
    }
    require(rule.targetMinutes > 0) { "目标分钟必须为正整数" }
    val effectiveFrom = runCatching { LocalDate.parse(rule.effectiveFrom) }
        .getOrElse { throw IllegalArgumentException("生效规划日无效", it) }
    require(effectiveFrom.isAfter(notBefore)) {
        "未来目标规则必须从未来规划日生效"
    }
    val dueDate = rule.dueDate?.let {
        runCatching { LocalDate.parse(it) }
            .getOrElse { error -> throw IllegalArgumentException("截止规划日无效", error) }
    }
    require(dueDate == null || !dueDate.isBefore(effectiveFrom)) {
        "截止规划日不能早于生效规划日"
    }
}

private fun PeriodSnapshot.samePeriodAs(other: PeriodSnapshot): Boolean {
    val sameStage = stageId == other.stageId || stageId.isBlank() || other.stageId.isBlank()
    return sameStage && targetId == other.targetId && cadence == other.cadence &&
        periodStart == other.periodStart && periodEnd == other.periodEnd
}

fun AttentionState.addPeriodSnapshot(snapshot: PeriodSnapshot): AttentionState {
    if (periodSnapshots.any { it.samePeriodAs(snapshot) }) return this
    require(!LocalDate.parse(snapshot.periodEnd).isBefore(LocalDate.parse(snapshot.periodStart))) {
        "周期结束日不能早于开始日"
    }
    return copy(periodSnapshots = periodSnapshots + snapshot)
}

fun AttentionState.snapshot(stage: GoalStage, periodStart: LocalDate, periodEnd: LocalDate): PeriodSnapshot {
    require(!periodEnd.isBefore(periodStart)) { "周期结束日不能早于开始日" }
    // A cycle is governed by the rule that was active when that cycle began.
    // This keeps a future rule from rewriting an already-started weekly or
    // monthly cycle when it becomes effective in the middle of that cycle.
    val effective = effectiveStage(stage, periodStart)
    val effectiveStart = LocalDate.parse(effective.startDate)
    require(!periodEnd.isBefore(effectiveStart)) { "目标阶段尚未开始" }
    val actualStart = maxOf(periodStart, effectiveStart)
    val actual = timeEntries
        .filter { LocalDate.parse(it.planningDate) in actualStart..periodEnd }
        .filter { it.targetId != null }
        .filter { isDescendantAt(it.targetId!!, effective.targetId, LocalDate.parse(it.planningDate)) }
        .sumOf { it.durationMinutes }
    val days = (periodEnd.toEpochDay() - actualStart.toEpochDay() + 1).toInt()
    val targetMinutes = if (effective.cadence == GoalCadence.DAILY) effective.targetMinutes * days else effective.targetMinutes
    val gap = (targetMinutes - actual).coerceAtLeast(0)
    return PeriodSnapshot(
        targetId = stage.targetId,
        cadence = effective.cadence,
        periodStart = periodStart.toString(),
        periodEnd = periodEnd.toString(),
        targetMinutes = targetMinutes,
        actualMinutes = actual,
        gapMinutes = gap,
        excessMinutes = (actual - targetMinutes).coerceAtLeast(0),
        completed = actual >= targetMinutes,
        stageId = stage.id,
    )
}

fun AttentionState.settlePeriodSnapshots(asOf: LocalDate): AttentionState =
    goalStages.fold(this) { state, stage -> state.settleStageSnapshots(stage, asOf) }

private fun AttentionState.settleStageSnapshots(stage: GoalStage, asOf: LocalDate): AttentionState {
    var state = this
    var cursor = LocalDate.parse(stage.startDate)
    while (true) {
        val effective = state.effectiveStage(stage, cursor)
        val calendarRange = state.periodRange(cursor, effective.cadence)
        val periodStart = maxOf(cursor, calendarRange.start)
        val periodEnd = when (effective.cadence) {
            GoalCadence.ONE_TIME -> effective.dueDate?.let(LocalDate::parse) ?: periodStart
            else -> calendarRange.endInclusive
        }
        if (!periodEnd.isBefore(asOf)) break
        state = state.addPeriodSnapshot(state.snapshot(stage, periodStart, periodEnd))
        if (effective.cadence == GoalCadence.ONE_TIME) break
        cursor = periodEnd.plusDays(1)
    }
    return state
}

fun AttentionState.addFutureTargetMove(
    targetId: String,
    parentId: String?,
    effectiveFrom: String,
    notBefore: LocalDate,
): AttentionState {
    val move = TargetMove(targetId = targetId, parentId = parentId, effectiveFrom = effectiveFrom)
    validateFutureTargetMove(move, notBefore)
    return copy(targetMoves = targetMoves + move)
}

fun parseTargetMoveDate(effectiveFrom: String): LocalDate? = runCatching { LocalDate.parse(effectiveFrom) }.getOrNull()

fun isFutureTargetMoveDate(effectiveFrom: String, notBefore: LocalDate): Boolean =
    parseTargetMoveDate(effectiveFrom)?.isAfter(notBefore) == true

fun AttentionState.pendingTargetMoves(targetId: String, onDate: LocalDate): List<TargetMove> = targetMoves
    .asSequence()
    .filter { it.targetId == targetId }
    .filter { isFutureTargetMoveDate(it.effectiveFrom, onDate) }
    .sortedBy { it.effectiveFrom }
    .toList()

fun AttentionState.futureTargetMoveParents(
    targetId: String,
    effectiveFrom: String,
    notBefore: LocalDate,
): List<Target> {
    val effectiveDate = parseTargetMoveDate(effectiveFrom) ?: return emptyList()
    if (!effectiveDate.isAfter(notBefore)) return emptyList()
    val excludedIds = descendantIds(targetId) + descendantIdsAt(targetId, effectiveDate)
    return targets.filter { !it.archived && it.id !in excludedIds }
}

fun AttentionState.updateFutureTargetMove(move: TargetMove, notBefore: LocalDate): AttentionState {
    val existing = targetMoves.firstOrNull { it.id == move.id } ?: error("未来目标移动不存在")
    require(existing.targetId == move.targetId) { "不能修改未来目标移动的目标" }
    require(LocalDate.parse(existing.effectiveFrom).isAfter(notBefore)) { "只能修改尚未生效的目标移动" }
    validateFutureTargetMove(move, notBefore, replacingId = move.id)
    return copy(targetMoves = targetMoves.map { if (it.id == move.id) move else it })
}

fun AttentionState.cancelFutureTargetMove(moveId: String, notBefore: LocalDate): AttentionState {
    val existing = targetMoves.firstOrNull { it.id == moveId } ?: error("未来目标移动不存在")
    val effectiveFrom = LocalDate.parse(existing.effectiveFrom)
    require(effectiveFrom.isAfter(notBefore)) { "只能取消尚未生效的目标移动" }
    return copy(targetMoves = targetMoves.filterNot { it.id == moveId })
}

private fun AttentionState.validateFutureTargetMove(
    move: TargetMove,
    notBefore: LocalDate,
    replacingId: String? = null,
) {
    require(targets.any { it.id == move.targetId }) { "目标不存在" }
    require(move.parentId == null || targets.any { it.id == move.parentId && !it.archived }) {
        "新的父目标不存在或已归档"
    }
    require(move.parentId == null || move.parentId !in descendantIds(move.targetId)) {
        "不能移动到自身或后代目标"
    }
    val effectiveFrom = runCatching { LocalDate.parse(move.effectiveFrom) }
        .getOrElse { throw IllegalArgumentException("生效日无效", it) }
    require(effectiveFrom.isAfter(notBefore)) { "生效日必须晚于当前规划日" }
    require(targetMoves.none {
        it.id != replacingId && it.targetId == move.targetId && it.effectiveFrom == move.effectiveFrom
    }) { "同一目标在同一生效日只能有一条移动记录" }

    val movesWithCandidate = targetMoves.filterNot { it.id == replacingId } + move
    val validationDates = movesWithCandidate
        .map { LocalDate.parse(it.effectiveFrom) }
        .filterNot { it.isBefore(effectiveFrom) }
        .distinct()
    validationDates.forEach { validationDate ->
        var current = move.parentId
        val seen = mutableSetOf<String>()
        while (current != null) {
            require(current != move.targetId) { "目标移动会形成循环" }
            require(seen.add(current)) { "目标移动会形成循环" }
            current = parentAt(current, validationDate, movesWithCandidate)
        }
    }
}

private fun AttentionState.parentAt(targetId: String, date: LocalDate, moves: List<TargetMove>): String? {
    val applicable = moves
        .asSequence()
        .filter { it.targetId == targetId && !LocalDate.parse(it.effectiveFrom).isAfter(date) }
        .maxWithOrNull(compareBy<TargetMove> { LocalDate.parse(it.effectiveFrom) }.thenBy { it.id })
    return if (applicable != null) applicable.parentId else targets.firstOrNull { it.id == targetId }?.parentId
}

fun AttentionState.parentAt(targetId: String, date: LocalDate): String? = parentAt(targetId, date, targetMoves)

fun AttentionState.materializeOccurrences(rule: RecurrenceRule, through: LocalDate): AttentionState {
    val generated = occurrences(rule, through)
    val existing = schedules.map { it.recurrenceRuleId to it.planningDate }.toSet()
    val additions = generated.filterNot { (it.recurrenceRuleId to it.planningDate) in existing }
        .map { it.copy(id = newId()) }
    return copy(schedules = schedules + additions)
}

fun AttentionState.startTimer(targetId: String?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): AttentionState {
    require(validRecordTarget(targetId)) { "计时只能归入未归档目标" }
    require(activeTimer == null)
    return copy(activeTimer = ActiveTimer(targetId = targetId, startedAtEpochMillis = now.toEpochMilli(), lastPlanningDate = planningDate(now, zone).toString()))
}

fun AttentionState.switchTimer(targetId: String?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): TimerResult {
    if (activeTimer == null) return TimerResult(startTimer(targetId, now, zone), emptyList())
    val stopped = stopTimer(now, zone)
    return TimerResult(stopped.state.startTimer(targetId, now, zone), stopped.entries)
}

fun AttentionState.pauseTimer(now: Instant): AttentionState {
    val timer = activeTimer ?: return this
    if (timer.paused) return this
    val additional = (now.toEpochMilli() - timer.startedAtEpochMillis).coerceAtLeast(0)
    val endedAt = maxOf(timer.startedAtEpochMillis, now.toEpochMilli())
    return copy(activeTimer = timer.copy(
        accumulatedMillis = timer.accumulatedMillis + additional,
        paused = true,
        completedSegments = timer.completedSegments + TimerSegment(timer.startedAtEpochMillis, endedAt),
    ))
}

fun AttentionState.resumeTimer(now: Instant): AttentionState {
    val timer = activeTimer ?: return this
    if (!timer.paused) return this
    return copy(activeTimer = timer.copy(startedAtEpochMillis = now.toEpochMilli(), paused = false))
}

fun AttentionState.recoverTimer(now: Instant): AttentionState {
    val timer = activeTimer ?: return this
    return copy(activeTimer = timer.copy(startedAtEpochMillis = now.toEpochMilli(), paused = false))
}

fun AttentionState.stopTimer(now: Instant, zone: ZoneId = ZoneId.systemDefault()): TimerResult {
    val timer = activeTimer ?: return TimerResult(this, emptyList())
    val segments = timer.completedSegments.toMutableList()
    if (!timer.paused) {
        val endedAt = maxOf(timer.startedAtEpochMillis, now.toEpochMilli())
        segments += TimerSegment(timer.startedAtEpochMillis, endedAt)
    }
    val entries = splitTimerSegments(segments, timer.targetId, settings.planningDayBoundaryMinutes, zone)
    return TimerResult(copy(activeTimer = null, timeEntries = timeEntries + entries), entries)
}

private fun splitTimerSegments(segments: List<TimerSegment>, targetId: String?, boundaryMinutes: Int, zone: ZoneId): List<TimeEntry> {
    val buckets = linkedMapOf<LocalDate, Long>()
    segments.sortedBy { it.startedAtEpochMillis }.forEach { segment ->
        var cursor = Instant.ofEpochMilli(segment.startedAtEpochMillis)
        val end = Instant.ofEpochMilli(maxOf(segment.startedAtEpochMillis, segment.endedAtEpochMillis))
        while (cursor.isBefore(end)) {
            val local = cursor.atZone(zone)
            val planningDate = local.toLocalDate().let {
                if (local.toLocalTime().toSecondOfDay() / 60 < boundaryMinutes) it.minusDays(1) else it
            }
            val nextBoundary = planningDate.plusDays(1).atStartOfDay(zone).plusMinutes(boundaryMinutes.toLong()).toInstant()
            val chunkEnd = minOf(end, nextBoundary)
            val duration = chunkEnd.toEpochMilli() - cursor.toEpochMilli()
            buckets[planningDate] = (buckets[planningDate] ?: 0L) + duration
            cursor = chunkEnd
        }
    }
    var carryMillis = 0L
    return buckets.mapNotNull { (date, durationMillis) ->
        val total = carryMillis + durationMillis
        val minutes = (total / 60_000L).toInt()
        carryMillis = total % 60_000L
        if (minutes == 0) null else TimeEntry(
            planningDate = date.toString(),
            durationMinutes = minutes,
            targetId = targetId,
            source = TimeEntrySource.TIMER,
            occurredAtEpochMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }
}

fun AttentionState.recalculateExperience(): AttentionState = copy(
    experience = timeEntries.filter { it.targetId != null }.sumOf { it.durationMinutes.toLong() } + milestones.sumOf { it.reward },
)

fun AttentionState.continuousPlanningDays(targetId: String? = null): Int {
    val dates = timeEntries.asSequence()
        .filter { it.targetId != null && (targetId == null || it.targetId in descendantIds(targetId)) }
        .map { LocalDate.parse(it.planningDate) }
        .toSet()
    if (dates.isEmpty()) return 0
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    dates.sorted().forEach { date ->
        run = if (previous?.plusDays(1) == date) run + 1 else 1
        best = maxOf(best, run)
        previous = date
    }
    return best
}

fun AttentionState.continuousPlanningDaysThrough(onDate: LocalDate, targetId: String? = null): Int {
    val dates = timeEntries.asSequence()
        .filter { it.targetId != null && LocalDate.parse(it.planningDate) <= onDate }
        .filter { targetId == null || it.targetId in descendantIds(targetId) }
        .map { LocalDate.parse(it.planningDate) }
        .toSet()
    var cursor = onDate
    var count = 0
    while (cursor in dates) {
        count++
        cursor = cursor.minusDays(1)
    }
    return count
}

private const val ONE_TIME_GOAL_MILESTONE_KIND = "one_time_goal"

fun oneTimeGoalMilestoneKey(stageId: String, startDate: String): String =
    "$ONE_TIME_GOAL_MILESTONE_KIND:$stageId:$startDate"

fun Milestone.isOneTimeStageFeedbackFor(stageId: String): Boolean {
    if (kind != ONE_TIME_GOAL_MILESTONE_KIND) return false
    val payload = instanceKey.removePrefix("$ONE_TIME_GOAL_MILESTONE_KIND:")
    return payload.substringBeforeLast(":", missingDelimiterValue = "") == stageId
}

fun AttentionState.oneTimeStageMilestones(stageId: String): List<Milestone> = milestones
    .filter { it.isOneTimeStageFeedbackFor(stageId) }
    .sortedWith(compareBy<Milestone> { it.achievedAtEpochMillis }.thenBy { it.id })

private fun MutableList<Milestone>.addMilestoneIfMissing(
    existingKeys: MutableSet<String>,
    kind: String,
    instanceKey: String,
    reward: Long,
) {
    if (existingKeys.add(instanceKey)) {
        add(Milestone(kind = kind, instanceKey = instanceKey, reward = reward, achievedAtEpochMillis = System.currentTimeMillis()))
    }
}

fun AttentionState.eligibleMilestones(onDate: LocalDate): List<Milestone> {
    val existing = milestones.map { it.instanceKey }.toMutableSet()
    val result = mutableListOf<Milestone>()
    goalStages.forEach { stage ->
        val progress = progress(stage, onDate)
        if (progress.completed) {
            val effective = effectiveStage(stage, onDate)
            val kind = if (effective.cadence == GoalCadence.ONE_TIME) ONE_TIME_GOAL_MILESTONE_KIND else "goal_period"
            val periodStart = if (effective.cadence == GoalCadence.ONE_TIME) effective.startDate else periodRange(onDate, effective.cadence).start.toString()
            val key = if (effective.cadence == GoalCadence.ONE_TIME) {
                oneTimeGoalMilestoneKey(stage.id, periodStart)
            } else {
                "$kind:${stage.id}:$periodStart"
            }
            val reward = when (effective.cadence) {
                GoalCadence.ONE_TIME -> 10_000L
                GoalCadence.DAILY -> 500L
                GoalCadence.WEEKLY, GoalCadence.MONTHLY -> 1_000L
            }
            result.addMilestoneIfMissing(existing, kind, key, reward)
        }
    }
    targets.filter { !it.archived }.forEach { target ->
        val days = continuousPlanningDaysThrough(onDate, target.id)
        listOf(7 to 500L, 30 to 1_000L, 100 to 10_000L).forEach { (threshold, reward) ->
            val key = "streak:${target.id}:$threshold"
            if (days >= threshold) result.addMilestoneIfMissing(existing, "streak", key, reward)
        }
    }
    val totalHours = timeEntries.filter { it.targetId != null }.sumOf { it.durationMinutes }.toLong() / 60
    (100L..totalHours step 100L).forEach { hours ->
        val key = "cumulative:$hours"
        val reward = cumulativeMilestoneReward(hours)
        if (reward != null) result.addMilestoneIfMissing(existing, "cumulative", key, reward)
    }
    return result
}

fun AttentionState.awardEligibleMilestones(onDate: LocalDate): AttentionState {
    val additions = eligibleMilestones(onDate)
    return if (additions.isEmpty()) this else copy(milestones = milestones + additions).recalculateExperience()
}

fun levelForExperience(experience: Long): Int {
    var level = 1
    while (500L * (level + 1) * level <= experience) level++
    return level
}

fun cumulativeMilestoneReward(hours: Long): Long? {
    if (hours < 100 || hours % 100 != 0L) return null
    val band = (hours / 500) + 1
    return 10_000L * band
}

fun AttentionState.unownedMinutes(): Int = timeEntries.filter { it.targetId == null }.sumOf { it.durationMinutes }

fun AttentionState.activeTargetMinutes(date: String): Int = timeEntries.filter { it.planningDate == date && it.targetId != null }.sumOf { it.durationMinutes }
