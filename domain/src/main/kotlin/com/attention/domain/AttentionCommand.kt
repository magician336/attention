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
sealed interface AttentionCommand {
    fun execute(
        state: AttentionState,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): AttentionCommandResult

    /** Adds one manually entered actual-investment record. */
    data class AddManualTimeEntry(
        val planningDate: String,
        val durationMinutes: Int,
        val targetId: String? = null,
        val note: String = "",
    ) : AttentionCommand {
        override fun execute(
            state: AttentionState,
            now: Instant,
            zone: ZoneId,
        ): AttentionCommandResult {
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

    /** Confirms one planned entry as actual invested time. */
    data class ConfirmSchedule(
        val entryId: String,
        val durationMinutes: Int? = null,
    ) : AttentionCommand {
        override fun execute(
            state: AttentionState,
            now: Instant,
            zone: ZoneId,
        ): AttentionCommandResult {
            val nextState = state.confirmScheduleCompleted(entryId, durationMinutes)
            val entry = nextState.timeEntries.last()
            return nextState.withMilestonesFor(
                affectedPlanningDates = setOf(LocalDate.parse(entry.planningDate)),
                achievedAtEpochMillis = now.toEpochMilli(),
                generatedTimeEntries = listOf(entry),
            )
        }
    }

    /** Edits only mutable ledger fields and preserves the record's recorded facts. */
    data class EditTimeEntry(
        val entryId: String,
        val durationMinutes: Int,
        val targetId: String?,
        val note: String = "",
    ) : AttentionCommand {
        override fun execute(
            state: AttentionState,
            now: Instant,
            zone: ZoneId,
        ): AttentionCommandResult {
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
    data class AssignUnowned(
        val entryIds: Set<String>,
        val targetId: String,
    ) : AttentionCommand {
        override fun execute(
            state: AttentionState,
            now: Instant,
            zone: ZoneId,
        ): AttentionCommandResult {
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
    data class DeleteTimeEntry(
        val entryId: String,
    ) : AttentionCommand {
        override fun execute(
            state: AttentionState,
            now: Instant,
            zone: ZoneId,
        ): AttentionCommandResult = AttentionCommandResult(state.deleteTimeEntry(entryId))
    }

    /** Restores a previously deleted record without rewriting any of its recorded facts. */
    data class RestoreTimeEntry(
        val entry: TimeEntry,
    ) : AttentionCommand {
        override fun execute(
            state: AttentionState,
            now: Instant,
            zone: ZoneId,
        ): AttentionCommandResult {
            val planningDate = LocalDate.parse(entry.planningDate)
            require(entry.durationMinutes > 0) { "时间记录时长必须为正数" }
            if (state.timeEntries.any { it.id == entry.id }) {
                return AttentionCommandResult(state.recalculateExperience())
            }
            val restored = state.copy(timeEntries = state.timeEntries + entry).recalculateExperience()
            return restored.withMilestonesFor(
                affectedPlanningDates = setOf(planningDate),
                achievedAtEpochMillis = now.toEpochMilli(),
                generatedTimeEntries = listOf(entry),
            )
        }
    }
}

/** Result returned by every [AttentionCommand]. */
data class AttentionCommandResult(
    val state: AttentionState,
    val timeEntries: List<TimeEntry> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
) {
    /** Alias that makes the generated fact explicit at adapter call sites. */
    val generatedTimeEntries: List<TimeEntry> get() = timeEntries

    /** Alias for callers that name newly issued feedback explicitly. */
    val newMilestones: List<Milestone> get() = milestones

    /** Alias used by adapters that prefer to call the output the next state. */
    val newState: AttentionState get() = state
}

/** Executes a typed command from the state side of the seam. */
fun AttentionState.execute(
    command: AttentionCommand,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): AttentionCommandResult = command.execute(this, now, zone)

/** Convenience name for command-oriented callers. */
fun AttentionCommand.run(
    state: AttentionState,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): AttentionCommandResult = execute(state, now, zone)

/**
 * Applies the one milestone policy shared by all actual-investment commands.
 * Dates are evaluated in ascending order; output is then normalized to a stable
 * kind/instance-key order and deduplicated against both prior and new keys.
 */
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
    return AttentionCommandResult(
        state = nextState,
        timeEntries = generatedTimeEntries.toList(),
        milestones = candidates.toList(),
    )
}

/** Stable aliases for callers that want a top-level command type name. */
typealias AddManualTimeEntryCommand = AttentionCommand.AddManualTimeEntry
typealias ManualTimeEntryCommand = AttentionCommand.AddManualTimeEntry
typealias ConfirmScheduleCommand = AttentionCommand.ConfirmSchedule
typealias ConfirmScheduleCompletedCommand = AttentionCommand.ConfirmSchedule
typealias EditTimeEntryCommand = AttentionCommand.EditTimeEntry
typealias AssignUnownedCommand = AttentionCommand.AssignUnowned
typealias AssignUnownedTimeEntriesCommand = AttentionCommand.AssignUnowned
typealias DeleteTimeEntryCommand = AttentionCommand.DeleteTimeEntry
typealias RestoreTimeEntryCommand = AttentionCommand.RestoreTimeEntry
