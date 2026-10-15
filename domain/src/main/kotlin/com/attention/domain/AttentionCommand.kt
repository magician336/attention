package com.attention.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Typed seam for domain intents that change actual investment.
 *
 * A command is pure with respect to its inputs: callers provide the current state,
 * execution time, and zone explicitly, and receive an immutable result containing
 * the next state plus facts produced by the command.
 */
/**
 * 保存日程条目时顺带补记的一段已经发生的时间。
 *
 * [targetId] 是用户选择归属的计划；null 表示这笔补记先保持为未归属活动，之后可以再归入计划。
 */
data class ScheduleBackfill(
    val durationMinutes: Int,
    val targetId: String? = null,
    val note: String = "",
)

sealed interface AttentionCommand {
    fun execute(
        state: AttentionState,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): AttentionCommandResult

    /**
     * 保存一条日程条目，并可选地顺带补记一笔事后补记来源的实际投入。
     *
     * 两件事在同一个 command 结果里完成：日程条目与那笔补记要么一起落库，要么都不落库，
     * 不存在只有一半的中间状态。补记记录的来源是 [TimeEntrySource.MANUAL]，并带上日程关联；
     * 它与 [TimeEntrySource.SCHEDULE_CONFIRMED]、[TimeEntrySource.TIMER] 三者不混。
     *
     * 这条日程条目已经有一笔按关联认领的实际投入时（日程完成确认、计时结算或更早的补记），
     * 只保存日程条目本身，不再凭空多出第二笔投入，投入经验与达成里程碑也不重复发放。
     */
    data class SaveSchedule(
        val entry: ScheduleEntry,
        val backfill: ScheduleBackfill? = null,
    ) : AttentionCommand {
        override fun execute(state: AttentionState, now: Instant, zone: ZoneId): AttentionCommandResult {
            val saved = if (state.schedules.any { it.id == entry.id }) state.updateSchedule(entry) else state.addSchedule(entry)
            if (backfill == null || state.hasActualInvestmentFor(entry.id)) {
                return saved.withMilestonesFor(emptyList(), now.toEpochMilli())
            }
            val recorded = saved.addTimeEntry(
                planningDate = entry.planningDate,
                durationMinutes = backfill.durationMinutes,
                targetId = backfill.targetId,
                source = TimeEntrySource.MANUAL,
                note = backfill.note,
                scheduleEntryId = entry.id,
            )
            return recorded.withMilestonesFor(
                affectedPlanningDates = setOf(LocalDate.parse(entry.planningDate)),
                achievedAtEpochMillis = now.toEpochMilli(),
                generatedTimeEntries = listOf(recorded.timeEntries.last()),
            )
        }
    }

    /** Adds one manually entered actual-investment record. */
    data class AddManualTimeEntry(
        val planningDate: String,
        val durationMinutes: Int,
        val targetId: String? = null,
        val note: String = "",
    ) : AttentionCommand {
        override fun execute(state: AttentionState, now: Instant, zone: ZoneId): AttentionCommandResult {
            val nextState = state.addTimeEntry(
                planningDate = planningDate,
                durationMinutes = durationMinutes,
                targetId = targetId,
                source = TimeEntrySource.MANUAL,
                note = note,
            )
            val entry = nextState.timeEntries.last()
            return nextState.withMilestonesFor(
                affectedPlanningDates = setOf(LocalDate.parse(entry.planningDate)),
                achievedAtEpochMillis = now.toEpochMilli(),
                generatedTimeEntries = listOf(entry),
            )
        }
    }

    /**
     * Confirms one planned entry as actual invested time.
     *
     * 幂等：该日程条目已有按关联认领的实际投入时只保证完成标记，不产生第二笔，
     * 因此重试与重复点击都不会重复发放投入经验与达成里程碑。
     */
    data class ConfirmSchedule(val entryId: String, val durationMinutes: Int? = null) : AttentionCommand {
        override fun execute(state: AttentionState, now: Instant, zone: ZoneId): AttentionCommandResult {
            if (state.hasActualInvestmentFor(entryId)) {
                return AttentionCommandResult(state.confirmScheduleCompleted(entryId, durationMinutes))
            }
            val nextState = state.confirmScheduleCompleted(entryId, durationMinutes)
            val entry = nextState.timeEntries.last()
            return nextState.withMilestonesFor(
                affectedPlanningDates = setOf(LocalDate.parse(entry.planningDate)),
                achievedAtEpochMillis = now.toEpochMilli(),
                generatedTimeEntries = listOf(entry),
            )
        }
    }

    /** Settles the active timer and evaluates every planning day it touched. */
    data object StopTimer : AttentionCommand {
        override fun execute(state: AttentionState, now: Instant, zone: ZoneId): AttentionCommandResult {
            if (state.activeTimer == null) return AttentionCommandResult(state)
            val stopped = state.stopTimer(now, zone)
            return stopped.state.withMilestonesFor(
                affectedPlanningDates = stopped.entries.map { LocalDate.parse(it.planningDate) },
                achievedAtEpochMillis = now.toEpochMilli(),
                generatedTimeEntries = stopped.entries,
            )
        }
    }

    /** Settles the previous timer, then starts a timer for a different target. */
    data class SwitchTimer(val targetId: String?, val scheduleEntryId: String? = null) : AttentionCommand {
        override fun execute(state: AttentionState, now: Instant, zone: ZoneId): AttentionCommandResult {
            require(state.validRecordTargetForCommand(targetId)) { "计时只能归入未归档目标" }
            val active = state.activeTimer
            if (active == null) return AttentionCommandResult(state.startTimer(targetId, now, zone, scheduleEntryId))
            if (active.targetId == targetId) return AttentionCommandResult(state)

            val stopped = state.stopTimer(now, zone)
            val settled = stopped.state.withMilestonesFor(
                affectedPlanningDates = stopped.entries.map { LocalDate.parse(it.planningDate) },
                achievedAtEpochMillis = now.toEpochMilli(),
                generatedTimeEntries = stopped.entries,
            )
            return settled.copy(state = settled.state.startTimer(targetId, now, zone, scheduleEntryId))
        }
    }

    /** Edits only mutable ledger fields and preserves the record's recorded facts. */
    data class EditTimeEntry(
        val entryId: String,
        val durationMinutes: Int,
        val targetId: String?,
        val note: String = "",
    ) : AttentionCommand {
        override fun execute(state: AttentionState, now: Instant, zone: ZoneId): AttentionCommandResult {
            val existing = state.timeEntries.firstOrNull { it.id == entryId }
                ?: throw IllegalArgumentException("时间记录不存在")
            val nextState = state.editTimeEntry(entryId, durationMinutes, targetId, note)
            val edited = nextState.timeEntries.first { it.id == entryId }
            return nextState.withMilestonesFor(
                affectedPlanningDates = setOf(LocalDate.parse(existing.planningDate)),
                achievedAtEpochMillis = now.toEpochMilli(),
                generatedTimeEntries = listOf(edited),
            )
        }
    }

    /** Assigns a validated, all-or-nothing set of unowned records to one target. */
    data class AssignUnowned(val entryIds: Set<String>, val targetId: String) : AttentionCommand {
        override fun execute(state: AttentionState, now: Instant, zone: ZoneId): AttentionCommandResult {
            if (entryIds.isEmpty()) return AttentionCommandResult(state)
            val selected = state.timeEntries.filter { it.id in entryIds }
            val affectedDates = selected.map { LocalDate.parse(it.planningDate) }.toSet()
            val nextState = state.assignUnowned(entryIds, targetId)
            val assigned = nextState.timeEntries.filter { it.id in entryIds }
            return nextState.withMilestonesFor(
                affectedPlanningDates = affectedDates,
                achievedAtEpochMillis = now.toEpochMilli(),
                generatedTimeEntries = assigned,
            )
        }
    }

    /** Removes one ledger record and leaves the milestone history untouched. */
    data class DeleteTimeEntry(val entryId: String) : AttentionCommand {
        override fun execute(state: AttentionState, now: Instant, zone: ZoneId): AttentionCommandResult {
            val removed = state.timeEntries.firstOrNull { it.id == entryId }
            return AttentionCommandResult(
                state = state.deleteTimeEntry(entryId),
                removedTimeEntries = removed?.let(::listOf).orEmpty(),
            )
        }
    }

    /** Restores a previously deleted record without rewriting any of its recorded facts. */
    data class RestoreTimeEntry(val entry: TimeEntry) : AttentionCommand {
        override fun execute(state: AttentionState, now: Instant, zone: ZoneId): AttentionCommandResult {
            require(entry.durationMinutes > 0) { "时间记录时长必须为正数" }
            if (state.timeEntries.any { it.id == entry.id }) return AttentionCommandResult(state.recalculateExperience())
            val restored = state.copy(timeEntries = state.timeEntries + entry).recalculateExperience()
            return AttentionCommandResult(restored, timeEntries = listOf(entry))
        }
    }
}

/** Result returned by every [AttentionCommand]. */
data class AttentionCommandResult(
    val state: AttentionState,
    val timeEntries: List<TimeEntry> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val removedTimeEntries: List<TimeEntry> = emptyList(),
) {
    val generatedTimeEntries: List<TimeEntry> get() = timeEntries
    val newMilestones: List<Milestone> get() = milestones
    val newState: AttentionState get() = state
}

fun AttentionState.execute(command: AttentionCommand, now: Instant, zone: ZoneId = ZoneId.systemDefault()): AttentionCommandResult =
    command.execute(this, now, zone)

fun AttentionCommand.run(state: AttentionState, now: Instant, zone: ZoneId = ZoneId.systemDefault()): AttentionCommandResult =
    execute(state, now, zone)

/** Applies the one milestone policy shared by all actual-investment commands. */
internal fun AttentionState.withMilestonesFor(
    affectedPlanningDates: Collection<LocalDate>,
    achievedAtEpochMillis: Long,
    generatedTimeEntries: List<TimeEntry> = emptyList(),
): AttentionCommandResult {
    val existingKeys = milestones.map { it.instanceKey }.toMutableSet()
    val candidates = affectedPlanningDates
        .distinct()
        .sorted()
        .flatMap { date -> eligibleMilestonesAt(date, achievedAtEpochMillis) }
        .filter { existingKeys.add(it.instanceKey) }
        .distinctBy { it.instanceKey }
        .sortedWith(compareBy<Milestone> { it.kind }.thenBy { it.instanceKey })
    val nextState = copy(milestones = milestones + candidates).recalculateExperience()
    return AttentionCommandResult(nextState, generatedTimeEntries.toList(), candidates.toList())
}

private fun AttentionState.validRecordTargetForCommand(targetId: String?): Boolean =
    targetId == null || targets.any { it.id == targetId && !it.archived }

typealias AddManualTimeEntryCommand = AttentionCommand.AddManualTimeEntry
typealias ManualTimeEntryCommand = AttentionCommand.AddManualTimeEntry
typealias SaveScheduleCommand = AttentionCommand.SaveSchedule
typealias ConfirmScheduleCommand = AttentionCommand.ConfirmSchedule
typealias ConfirmScheduleCompletedCommand = AttentionCommand.ConfirmSchedule
typealias EditTimeEntryCommand = AttentionCommand.EditTimeEntry
typealias AssignUnownedCommand = AttentionCommand.AssignUnowned
typealias AssignUnownedTimeEntriesCommand = AttentionCommand.AssignUnowned
typealias DeleteTimeEntryCommand = AttentionCommand.DeleteTimeEntry
typealias RestoreTimeEntryCommand = AttentionCommand.RestoreTimeEntry
