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

fun AttentionState.targetChildren(parentId: String?): List<Target> = targets
    .filter { it.parentId == parentId && !it.archived }
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
        val ids = descendantIds(targetId)
        timeEntries.filter { it.targetId in ids }.sumOf { it.durationMinutes }
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
    val end = due?.let { minOf(onDate, it) } ?: onDate
    return start..end
}

fun AttentionState.progress(stage: GoalStage, onDate: LocalDate): GoalProgress {
    val effective = effectiveStage(stage, onDate)
    if (onDate.isBefore(LocalDate.parse(effective.startDate))) return GoalProgress(0, 0, 0, 0, false)
    val range = progressRange(stage, onDate)
    val actual = timeEntries.filter { it.targetId != null }
        .filter { LocalDate.parse(it.planningDate) in range }
        .filter { isDescendantAt(it.targetId!!, effective.targetId, LocalDate.parse(it.planningDate)) }
        .sumOf { it.durationMinutes }
    val gap = (effective.targetMinutes - actual).coerceAtLeast(0)
    return GoalProgress(effective.targetMinutes, actual, gap, (actual - effective.targetMinutes).coerceAtLeast(0), actual >= effective.targetMinutes)
}

fun AttentionState.effectiveStage(stage: GoalStage, onDate: LocalDate): GoalStage {
    val rule = futureGoalRules.asSequence()
        .filter { it.targetId == stage.targetId && (it.stageId.isBlank() || it.stageId == stage.id) && !LocalDate.parse(it.effectiveFrom).isAfter(onDate) }
        .maxByOrNull { it.effectiveFrom }
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
        targetMoves = targetMoves.filterNot { it.targetId in ids },
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
    val parsedStart = LocalDate.parse(startDate)
    val parsedDue = dueDate?.let(LocalDate::parse)
    require(parsedDue == null || !parsedDue.isBefore(parsedStart)) { "截止日期不能早于开始日期" }
    return copy(goalStages = goalStages + GoalStage(targetId = targetId, cadence = cadence, targetMinutes = targetMinutes, startDate = startDate, dueDate = dueDate))
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

fun AttentionState.addMigration(migration: Migration): AttentionState {
    require(migration.minutes > 0)
    require(goalStages.any { it.id == migration.sourceStageId && it.targetId == migration.targetId })
    LocalDate.parse(migration.destinationStartDate)
    migration.destinationEndDate?.let(LocalDate::parse)
    return copy(migrations = migrations + migration)
}

fun AttentionState.updateMigration(migration: Migration): AttentionState = copy(
    migrations = migrations.map { if (it.id == migration.id) migration else it },
)

fun AttentionState.cancelMigration(migrationId: String): AttentionState = copy(
    migrations = migrations.map { if (it.id == migrationId) it.copy(cancelled = true) else it },
)

fun AttentionState.gapFor(stage: GoalStage, onDate: LocalDate): Int = progress(stage, onDate).gapMinutes

fun AttentionState.addFutureGoalRule(rule: FutureGoalRule): AttentionState {
    require(targets.any { it.id == rule.targetId })
    require(rule.stageId.isBlank() || goalStages.any { it.id == rule.stageId && it.targetId == rule.targetId })
    require(rule.targetMinutes > 0)
    LocalDate.parse(rule.effectiveFrom)
    rule.dueDate?.let(LocalDate::parse)
    return copy(futureGoalRules = futureGoalRules + rule)
}

fun AttentionState.updateFutureGoalRule(rule: FutureGoalRule): AttentionState = copy(
    futureGoalRules = futureGoalRules.map { if (it.id == rule.id) rule else it },
)

fun AttentionState.cancelFutureGoalRule(ruleId: String): AttentionState = copy(
    futureGoalRules = futureGoalRules.filterNot { it.id == ruleId },
)

fun AttentionState.goalRulesAt(targetId: String, onDate: LocalDate): List<FutureGoalRule> = futureGoalRules
    .filter { it.targetId == targetId && !LocalDate.parse(it.effectiveFrom).isAfter(onDate) }
    .sortedBy { it.effectiveFrom }

fun AttentionState.addPeriodSnapshot(snapshot: PeriodSnapshot): AttentionState {
    require(periodSnapshots.none { it.targetId == snapshot.targetId && it.periodStart == snapshot.periodStart && it.cadence == snapshot.cadence })
    return copy(periodSnapshots = periodSnapshots + snapshot)
}

fun AttentionState.snapshot(stage: GoalStage, periodStart: LocalDate, periodEnd: LocalDate): PeriodSnapshot {
    val result = progress(stage, periodEnd)
    return PeriodSnapshot(
        targetId = stage.targetId,
        cadence = stage.cadence,
        periodStart = periodStart.toString(),
        periodEnd = periodEnd.toString(),
        targetMinutes = result.targetMinutes,
        actualMinutes = result.actualMinutes,
        gapMinutes = result.gapMinutes,
        excessMinutes = result.excessMinutes,
        completed = result.completed,
    )
}

fun AttentionState.addFutureTargetMove(targetId: String, parentId: String?, effectiveFrom: String): AttentionState {
    require(targets.any { it.id == targetId })
    require(parentId == null || targets.any { it.id == parentId })
    require(parentId == null || parentId !in descendantIds(targetId))
    LocalDate.parse(effectiveFrom)
    return copy(targetMoves = targetMoves + TargetMove(targetId = targetId, parentId = parentId, effectiveFrom = effectiveFrom))
}

fun AttentionState.parentAt(targetId: String, date: LocalDate): String? = targetMoves
    .filter { it.targetId == targetId && !LocalDate.parse(it.effectiveFrom).isAfter(date) }
    .maxByOrNull { it.effectiveFrom }
    ?.parentId
    ?: targets.firstOrNull { it.id == targetId }?.parentId

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

fun AttentionState.eligibleMilestones(onDate: LocalDate): List<Milestone> {
    val existing = milestones.map { it.instanceKey }.toSet()
    val result = mutableListOf<Milestone>()
    goalStages.forEach { stage ->
        val progress = progress(stage, onDate)
        if (progress.completed) {
            val effective = effectiveStage(stage, onDate)
            val kind = if (effective.cadence == GoalCadence.ONE_TIME) "one_time_goal" else "goal_period"
            val periodStart = if (effective.cadence == GoalCadence.ONE_TIME) effective.startDate else periodRange(onDate, effective.cadence).start.toString()
            val key = "$kind:${stage.id}:$periodStart"
            val reward = when (effective.cadence) {
                GoalCadence.ONE_TIME -> 10_000L
                GoalCadence.DAILY -> 500L
                GoalCadence.WEEKLY, GoalCadence.MONTHLY -> 1_000L
            }
            if (key !in existing) result += Milestone(kind = kind, instanceKey = key, reward = reward, achievedAtEpochMillis = System.currentTimeMillis())
        }
    }
    targets.filter { !it.archived }.forEach { target ->
        val days = continuousPlanningDaysThrough(onDate, target.id)
        listOf(7 to 500L, 30 to 1_000L, 100 to 10_000L).forEach { (threshold, reward) ->
            val key = "streak:${target.id}:$threshold"
            if (days >= threshold && key !in existing) result += Milestone(kind = "streak", instanceKey = key, reward = reward, achievedAtEpochMillis = System.currentTimeMillis())
        }
    }
    val totalHours = timeEntries.filter { it.targetId != null }.sumOf { it.durationMinutes }.toLong() / 60
    (100L..totalHours step 100L).forEach { hours ->
        val key = "cumulative:$hours"
        val reward = cumulativeMilestoneReward(hours)
        if (reward != null && key !in existing) result += Milestone(kind = "cumulative", instanceKey = key, reward = reward, achievedAtEpochMillis = System.currentTimeMillis())
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
