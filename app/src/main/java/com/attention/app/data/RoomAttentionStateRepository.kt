package com.attention.app.data

import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.backup.AttentionBackupCodec
import com.attention.app.data.backup.ImportBackupStore
import com.attention.app.data.backup.InMemoryImportBackupStore
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.SettingsStore
import com.attention.app.reminder.ReminderCoordinator
import com.attention.domain.AttentionState
import com.attention.domain.AttentionCommand
import com.attention.domain.AttentionCommandResult
import com.attention.domain.LaunchDestination
import com.attention.domain.StoredSettings
import com.attention.domain.validateActiveTimerTarget
import com.attention.domain.recalculateExperience
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * Production repository for the split storage model.
 *
 * Room owns business collections and timer state. Preferences DataStore owns
 * planner and navigation settings. JSON is handled only through the versioned
 * backup codec and an explicit import coordinator.
 */
class RoomAttentionStateRepository(
    private val business: RoomBusinessDataRepository,
    private val settings: SettingsStore,
    private val backupStore: ImportBackupStore = InMemoryImportBackupStore(),
    private val reminderCoordinator: ReminderCoordinator = ReminderCoordinator { _, _ -> },
) : AttentionStateRepository {
    private val mutex = Mutex()

    override val state: Flow<AttentionState> = AttentionStateReader(business, settings).state

    override suspend fun updateBusiness(transform: (AttentionState) -> AttentionState) {
        mutex.withLock {
            val previous = state.first()
            val plannerSettings = settings.settings.first().toStoredSettings()
            val next = business.update { current ->
                transform(current.copy(settings = plannerSettings))
            }
            reminderCoordinator.sync(previous, next)
        }
    }

    override suspend fun executeCommand(
        command: AttentionCommand,
        now: Instant,
        zone: ZoneId,
    ): AttentionCommandResult = mutex.withLock {
        val previous = state.first()
        val result = business.execute(command, now, zone)
        val next = result.state.copy(
            settings = previous.settings,
            launchDestination = previous.launchDestination,
            lastOpenedDestination = previous.lastOpenedDestination,
            onboardingCompleted = previous.onboardingCompleted,
            notificationsEnabled = previous.notificationsEnabled,
        )
        reminderCoordinator.sync(previous, next)
        result.copy(state = next)
    }

    override suspend fun toggleScheduleCompleted(entryId: String) {
        mutex.withLock {
            val previous = state.first()
            val next = business.toggleScheduleCompleted(entryId)
            reminderCoordinator.sync(previous, next)
        }
    }

    override suspend fun setPlannerSettings(settings: StoredSettings) {
        this.settings.setPlannerSettings(settings)
    }

    override suspend fun setPlanningDayBoundaryMinutes(minutes: Int) {
        this.settings.setPlanningDayBoundaryMinutes(minutes)
    }

    override suspend fun setWeekStartDay(day: Int) {
        this.settings.setWeekStartDay(day)
    }

    override suspend fun setDailyCapacityMinutes(minutes: Int?) {
        this.settings.setDailyCapacityMinutes(minutes)
    }

    override suspend fun setLaunchDestination(destination: LaunchDestination) {
        this.settings.setLaunchDestination(destination)
    }

    override suspend fun setLastOpenedDestination(destination: LaunchDestination) {
        this.settings.setLastOpenedDestination(destination)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        this.settings.setOnboardingCompleted(completed)
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        this.settings.setNotificationsEnabled(enabled)
    }

    override suspend fun replace(state: AttentionState) {
        mutex.withLock {
            replaceWithRollback(this@RoomAttentionStateRepository.state.first(), state)
        }
    }

    override suspend fun restoreReminders() {
        mutex.withLock { reminderCoordinator.restore(state.first()) }
    }

    override suspend fun exportJson(): String = AttentionBackupCodec.encode(state.first())

    override suspend fun importJson(encoded: String, clearExisting: Boolean): AttentionState {
        return mutex.withLock {
            val current = state.first()
            val previousBackup = backupStore.read()
            try {
                val incoming = AttentionBackupCodec.decode(encoded)
                val result = if (clearExisting) incoming else AttentionBackupCodec.merge(current, incoming)
                result.validateActiveTimerTarget()
                AttentionBackupCodec.validate(result)
                backupStore.write(AttentionBackupCodec.encode(current))
                replaceWithRollback(current, result)
            } catch (error: Throwable) {
                runCatching {
                    backupStore.write(previousBackup ?: AttentionBackupCodec.encode(current))
                }.onFailure(error::addSuppressed)
                throw error
            }
        }
    }

    override suspend fun lastImportBackup(): String? = backupStore.read()

    private suspend fun replaceWithRollback(current: AttentionState, next: AttentionState): AttentionState {
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
