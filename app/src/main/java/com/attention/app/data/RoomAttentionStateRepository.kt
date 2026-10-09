package com.attention.app.data

import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.backup.AttentionBackupCodec
import com.attention.app.data.backup.ImportBackupStore
import com.attention.app.data.backup.InMemoryImportBackupStore
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.SettingsStore
import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
import com.attention.domain.StoredSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
) : AttentionStateRepository {
    private val mutex = Mutex()

    override val state: Flow<AttentionState> = AttentionStateReader(business, settings).state

    override suspend fun updateBusiness(transform: (AttentionState) -> AttentionState) {
        mutex.withLock {
            val plannerSettings = settings.settings.first().toStoredSettings()
            business.update { current ->
                transform(current.copy(settings = plannerSettings))
            }
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

    override suspend fun exportJson(): String = AttentionBackupCodec.encode(state.first())

    override suspend fun importJson(encoded: String, clearExisting: Boolean): AttentionState {
        val incoming = AttentionBackupCodec.decode(encoded)
        return mutex.withLock {
            val current = state.first()
            backupStore.write(AttentionBackupCodec.encode(current))
            val result = if (clearExisting) incoming else AttentionBackupCodec.merge(current, incoming)
            replaceWithRollback(current, result)
            result
        }
    }

    override suspend fun lastImportBackup(): String? = backupStore.read()

    private suspend fun replaceWithRollback(current: AttentionState, next: AttentionState) {
        try {
            business.replace(next)
            settings.setSnapshot(next.toSettingsSnapshot())
        } catch (error: Throwable) {
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
