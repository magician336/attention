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

fun AttentionState.directMinutes(targetId: String, date: String? = null): Int = timeEntries
    .asSequence()
    .filter { it.targetId == targetId && (date == null || it.planningDate == date) }
    .sumOf { it.durationMinutes }

fun AttentionState.subtreeMinutes(targetId: String, date: String? = null): Int {
    val ids = descendantIds(targetId)
    return timeEntries.filter { it.targetId in ids && (date == null || it.planningDate == date) }
        .sumOf { it.durationMinutes }
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

fun AttentionState.progress(stage: GoalStage, onDate: LocalDate): GoalProgress {
    val range = if (stage.cadence == GoalCadence.ONE_TIME) {
        val end = stage.dueDate?.let(LocalDate::parse) ?: onDate
        LocalDate.parse(stage.startDate)..end
    } else periodRange(onDate, stage.cadence)
    val ids = descendantIds(stage.targetId)
    val actual = timeEntries.filter { it.targetId in ids }
        .filter { LocalDate.parse(it.planningDate) in range }
        .sumOf { it.durationMinutes }
    val gap = (stage.targetMinutes - actual).coerceAtLeast(0)
    return GoalProgress(stage.targetMinutes, actual, gap, (actual - stage.targetMinutes).coerceAtLeast(0), actual >= stage.targetMinutes)
}

fun AttentionState.capacitySummary(date: String): CapacitySummary {
    val capacity = settings.dailyCapacityMinutes
    val used = schedules.filter { it.planningDate == date }.sumOf { schedule ->
        schedule.estimatedMinutes ?: schedule.startMinute?.let { start -> schedule.endMinute?.minus(start) } ?: 0
    }
    return CapacitySummary(capacity, used, capacity != null && used > capacity)
}

fun AttentionState.validTarget(targetId: String?): Boolean = targetId == null || targets.any { it.id == targetId }

fun AttentionState.addTarget(title: String, parentId: String? = null): AttentionState {
    require(title.isNotBlank())
    require(parentId == null || targets.any { it.id == parentId })
    val siblingOrder = targets.count { it.parentId == parentId }
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
    return copy(targets = targets.map { if (it.id == targetId) it.copy(parentId = newParentId) else it })
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
    )
}

fun AttentionState.addGoalStage(
    targetId: String,
    cadence: GoalCadence,
    targetMinutes: Int,
    startDate: String,
    dueDate: String? = null,
): AttentionState {
    require(targets.any { it.id == targetId })
    require(targetMinutes > 0)
    LocalDate.parse(startDate)
    dueDate?.let(LocalDate::parse)
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
    require(validTarget(targetId))
    return copy(timeEntries = timeEntries + TimeEntry(planningDate = planningDate, durationMinutes = durationMinutes, targetId = targetId, source = source, occurredAtEpochMillis = occurredAtEpochMillis, note = note))
}

fun AttentionState.editTimeEntry(entryId: String, durationMinutes: Int, targetId: String?, note: String = ""): AttentionState {
    require(durationMinutes > 0 && validTarget(targetId))
    require(timeEntries.any { it.id == entryId })
    return copy(timeEntries = timeEntries.map { if (it.id == entryId) it.copy(durationMinutes = durationMinutes, targetId = targetId, note = note) else it })
}

fun AttentionState.deleteTimeEntry(entryId: String): AttentionState = copy(timeEntries = timeEntries.filterNot { it.id == entryId })

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
        .map { date -> ScheduleEntry(planningDate = date.toString(), title = rule.title, estimatedMinutes = rule.estimatedMinutes, targetId = rule.targetId, note = rule.note, recurrenceRuleId = rule.id) }
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

fun AttentionState.startTimer(targetId: String?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): AttentionState {
    require(validTarget(targetId))
    require(activeTimer == null)
    return copy(activeTimer = ActiveTimer(targetId = targetId, startedAtEpochMillis = now.toEpochMilli(), lastPlanningDate = planningDate(now, zone).toString()))
}

fun AttentionState.pauseTimer(now: Instant): AttentionState {
    val timer = activeTimer ?: return this
    if (timer.paused) return this
    val additional = (now.toEpochMilli() - timer.startedAtEpochMillis).coerceAtLeast(0)
    return copy(activeTimer = timer.copy(accumulatedMillis = timer.accumulatedMillis + additional, paused = true))
}

fun AttentionState.resumeTimer(now: Instant): AttentionState {
    val timer = activeTimer ?: return this
    if (!timer.paused) return this
    return copy(activeTimer = timer.copy(startedAtEpochMillis = now.toEpochMilli(), paused = false))
}

fun AttentionState.stopTimer(now: Instant, zone: ZoneId = ZoneId.systemDefault()): TimerResult {
    val timer = activeTimer ?: return TimerResult(this, emptyList())
    val running = if (timer.paused) 0 else (now.toEpochMilli() - timer.startedAtEpochMillis).coerceAtLeast(0)
    val totalMillis = timer.accumulatedMillis + running
    val wholeMinutes = totalMillis / 60_000
    if (wholeMinutes <= 0) return TimerResult(copy(activeTimer = null), emptyList())
    val start = Instant.ofEpochMilli(timer.startedAtEpochMillis - timer.accumulatedMillis)
    val entries = splitMinutes(start, now, wholeMinutes.toInt(), timer.targetId, settings.planningDayBoundaryMinutes, zone)
    return TimerResult(copy(activeTimer = null, timeEntries = timeEntries + entries), entries)
}

private fun splitMinutes(start: Instant, end: Instant, wholeMinutes: Int, targetId: String?, boundaryMinutes: Int, zone: ZoneId): List<TimeEntry> {
    var cursor = start
    var remainingMillis = wholeMinutes * 60_000L
    val result = mutableListOf<TimeEntry>()
    while (remainingMillis > 0 && cursor.isBefore(end)) {
        val local = cursor.atZone(zone)
        val planningDate = local.toLocalDate().let { if (local.toLocalTime().toSecondOfDay() / 60 < boundaryMinutes) it.minusDays(1) else it }
        val nextBoundary = planningDate.plusDays(1).atStartOfDay(zone).plusMinutes(boundaryMinutes.toLong()).toInstant()
        val untilBoundary = (nextBoundary.toEpochMilli() - cursor.toEpochMilli()).coerceAtLeast(1)
        val chunkMillis = minOf(remainingMillis, untilBoundary, end.toEpochMilli() - cursor.toEpochMilli())
        val chunkMinutes = (chunkMillis / 60_000L).toInt()
        if (chunkMinutes > 0) result += TimeEntry(planningDate = planningDate.toString(), durationMinutes = chunkMinutes, targetId = targetId, source = TimeEntrySource.TIMER, occurredAtEpochMillis = cursor.toEpochMilli())
        cursor = cursor.plusMillis(chunkMinutes * 60_000L)
        remainingMillis -= chunkMinutes * 60_000L
        if (chunkMinutes == 0) break
    }
    return result
}

fun AttentionState.recalculateExperience(): AttentionState = copy(
    experience = timeEntries.filter { it.targetId != null }.sumOf { it.durationMinutes.toLong() },
)

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
