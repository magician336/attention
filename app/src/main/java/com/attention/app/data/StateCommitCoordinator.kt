package com.attention.app.data

import com.attention.app.data.backup.AttentionBackupCodec
import com.attention.app.data.backup.BackupCommitResult
import com.attention.app.data.backup.ImportBackupStore
import com.attention.app.data.backup.BackupWorkflow
import com.attention.app.data.room.BusinessStateStore
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.SettingsStore
import com.attention.app.reminder.ReminderCoordinator
import com.attention.domain.AttentionCommand
import com.attention.domain.AttentionCommandResult
import com.attention.domain.AttentionState
import com.attention.domain.StoredSettings
import com.attention.domain.recalculateExperience
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes runtime state transitions and keeps the reminder projection in
 * step with the state that was actually persisted.
 *
 * Business transitions only touch Room. Full replacements also write the
 * settings snapshot and rotate the import backup, so the two operations have
 * explicit entry points instead of one shallow function with optional steps.
 */
internal class StateCommitCoordinator(
    private val business: BusinessStateStore,
    private val settings: SettingsStore,
    private val backupStore: ImportBackupStore,
    private val reminderCoordinator: ReminderCoordinator,
    private val readState: suspend () -> AttentionState,
) {
    private val mutex = Mutex()

    suspend fun updateBusiness(
        plannerSettings: StoredSettings,
        transform: (AttentionState) -> AttentionState,
    ): AttentionState = mutex.withLock {
        val transition = business.updateTransition(plannerSettings, transform)
        commitBusinessTransition(transition.previous, transition.next)
        transition.next
    }

    suspend fun executeCommand(
        command: AttentionCommand,
        now: Instant,
        zone: ZoneId,
        plannerSettings: StoredSettings,
    ): AttentionCommandResult = mutex.withLock {
        val transition = business.executeTransition(command, now, zone, plannerSettings)
        commitBusinessTransition(transition.previous, transition.next)
        transition.result
    }

    suspend fun toggleScheduleCompleted(
        entryId: String,
        plannerSettings: StoredSettings,
    ) = mutex.withLock {
        val transition = business.toggleScheduleCompletedTransition(entryId, plannerSettings)
        commitBusinessTransition(transition.previous, transition.next)
        transition.next
    }

    suspend fun replace(next: AttentionState): AttentionState = mutex.withLock {
        replaceLocked(readState(), next)
    }

    suspend fun submit(
        expectedFingerprint: String,
        next: AttentionState,
    ): BackupCommitResult = mutex.withLock {
        val current = readState()
        val actualFingerprint = BackupWorkflow.stateFingerprint(current)
        if (actualFingerprint != expectedFingerprint) {
            return@withLock BackupCommitResult.StalePlan(
                expectedFingerprint = expectedFingerprint,
                actualFingerprint = actualFingerprint,
            )
        }

        val previousBackup = backupStore.read()
        val currentBackup = AttentionBackupCodec.encode(current)
        try {
            backupStore.write(currentBackup)
            BackupCommitResult.Committed(replaceLocked(current, next))
        } catch (error: Throwable) {
            runCatching {
                backupStore.write(previousBackup ?: currentBackup)
            }.onFailure(error::addSuppressed)
            throw error
        }
    }

    suspend fun restoreReminders() = mutex.withLock {
        reminderCoordinator.restore(readState())
    }

    private suspend fun commitBusinessTransition(previous: AttentionState, next: AttentionState) {
        try {
            // The alarm adapter only observes a pair that Room has committed.
            reminderCoordinator.sync(previous, next)
        } catch (error: Throwable) {
            compensateBusinessTransition(previous, next, error)
            throw error
        }
    }

    private suspend fun compensateBusinessTransition(
        previous: AttentionState,
        next: AttentionState,
        error: Throwable,
    ) {
        runCatching { reminderCoordinator.sync(next, previous) }
            .onFailure(error::addSuppressed)
        runCatching { business.replace(previous) }
            .onFailure(error::addSuppressed)
    }

    private suspend fun replaceLocked(current: AttentionState, next: AttentionState): AttentionState {
        val canonicalNext = next.recalculateExperience()
        try {
            business.replace(canonicalNext)
            settings.setSnapshot(canonicalNext.toSettingsSnapshot())
            reminderCoordinator.sync(current, canonicalNext)
            return canonicalNext
        } catch (error: Throwable) {
            runCatching { reminderCoordinator.sync(canonicalNext, current) }
                .onFailure(error::addSuppressed)
            runCatching { settings.setSnapshot(current.toSettingsSnapshot()) }
                .onFailure(error::addSuppressed)
            runCatching { business.replace(current) }
                .onFailure(error::addSuppressed)
            throw error
        }
    }

    private fun AttentionState.toSettingsSnapshot(): SettingsSnapshot = SettingsSnapshot(
        planningDayBoundaryMinutes = settings.planningDayBoundaryMinutes,
        weekStartDay = settings.weekStartDay,
        dailyCapacityMinutes = settings.dailyCapacityMinutes,
        launchDestination = launchDestination,
        lastOpenedDestination = lastOpenedDestination,
        onboardingCompleted = onboardingCompleted,
        notificationsEnabled = notificationsEnabled,
    )
}
