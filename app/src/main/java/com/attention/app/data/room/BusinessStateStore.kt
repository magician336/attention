package com.attention.app.data.room

import com.attention.domain.AttentionCommand
import com.attention.domain.AttentionCommandResult
import com.attention.domain.AttentionState
import com.attention.domain.StoredSettings
import java.time.Instant
import java.time.ZoneId

/** The persistence seam needed by the runtime state commit coordinator. */
internal interface BusinessStateStore {
    suspend fun replace(state: AttentionState)

    suspend fun updateTransition(
        plannerSettings: StoredSettings,
        transform: (AttentionState) -> AttentionState,
    ): BusinessStateTransition<AttentionState>

    suspend fun executeTransition(
        command: AttentionCommand,
        now: Instant,
        zone: ZoneId,
        plannerSettings: StoredSettings,
    ): BusinessStateTransition<AttentionCommandResult>

    suspend fun toggleScheduleCompletedTransition(
        entryId: String,
        plannerSettings: StoredSettings,
    ): BusinessStateTransition<AttentionState>
}

/** A persistence transaction's exact before/after pair and its result. */
data class BusinessStateTransition<T>(
    val previous: AttentionState,
    val next: AttentionState,
    val result: T,
)
