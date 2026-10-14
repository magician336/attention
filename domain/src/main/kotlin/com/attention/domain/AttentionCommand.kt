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

    /** Settles the active timer and evaluates every planning day it touched. */
    data object StopTimer : AttentionCommand {
        override fun execute(
            state: AttentionState,
            now: Instant,
            zone: ZoneId,
        ): AttentionCommandResult {
            // Preserve the existing no-op semantics when no timer is active.
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
    data class SwitchTimer(
        val targetId: String?,
        val scheduleEntryId: String? = null,
    ) : AttentionCommand {
        override fun execute(
            state: AttentionState,
            now: Instant,
            zone: ZoneId,
        ): AttentionCommandResult {
            require(state.validRecordTargetForCommand(targetId)) { "计时只能归入未归档目标" }
            val active = state.activeTimer
            if (active == null) {
                return AttentionCommandResult(state.startTimer(targetId, now, zone, scheduleEntryId))
            }
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

/** Command-local access to the timer target invariant. */
private fun AttentionState.validRecordTargetForCommand(targetId: String?): Boolean =
    targetId == null || targets.any { it.id == targetId && !it.archived }

/** Stable aliases for callers that want a top-level command type name. */
typealias AddManualTimeEntryCommand = AttentionCommand.AddManualTimeEntry
typealias ManualTimeEntryCommand = AttentionCommand.AddManualTimeEntry
