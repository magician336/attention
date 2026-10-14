package com.attention.app.data

import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.backup.AttentionBackupCodec
import com.attention.app.data.backup.BackupCommitResult
import com.attention.app.data.backup.BackupStateSubmissionPort
import com.attention.app.data.backup.BackupWorkflow
import com.attention.app.data.backup.ImportMode
import com.attention.app.data.backup.ImportBackupStore
import com.attention.app.data.backup.InMemoryImportBackupStore
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.SettingsStore
import com.attention.app.reminder.ReminderCoordinator
import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
import com.attention.domain.StoredSettings
import com.attention.domain.recalculateExperience
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
    private val reminderCoordinator: ReminderCoordinator = ReminderCoordinator { _, _ -> },
) : AttentionStateRepository, BackupStateSubmissionPort {
    private val mutex = Mutex()

    /** Typed workflow used by ViewModel and other non-Android callers. */
    override val backupWorkflow: BackupWorkflow by lazy {
        BackupWorkflow(this, backupStore)
    }

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

    /**
     * Reads the complete composed state for [BackupStateSubmissionPort]. The
     * submission implementation performs its own read again while holding
     * [mutex], so this method is safe for workflow preview and fingerprinting.
     */
    override suspend fun read(): AttentionState = state.first()

    /**
     * Re-checks a plan fingerprint and, when current, rotates the import
     * snapshot before replacing Room, DataStore and reminders. The entire
     * operation is serialized by one mutex; any failure compensates all three
     * stores and restores the previous snapshot value.
     */
    override suspend fun submit(
        expectedFingerprint: String,
        next: AttentionState,
    ): BackupCommitResult = mutex.withLock {
        val current = state.first()
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
            // Persist the pre-submit state before touching any runtime store.
            // The old value is retained and restored if writing or replacement
            // fails, so a failed import never destroys the last recovery point.
            backupStore.write(currentBackup)
            val committed = replaceWithRollback(current, next)
            BackupCommitResult.Committed(committed)
        } catch (error: Throwable) {
            runCatching {
                backupStore.write(previousBackup ?: currentBackup)
            }.onFailure(error::addSuppressed)
            throw error
        }
    }

    /**
     * Compatibility import preparation can fail before a typed plan exists.
     * Keep its historical recovery guarantee inside the same mutex and port
     * boundary used by successful submissions.
     */
    override suspend fun preserveImportBackupIfAbsent() = mutex.withLock {
        if (backupStore.read() == null) {
            backupStore.write(AttentionBackupCodec.encode(state.first()))
        }
    }

    override suspend fun restoreReminders() {
        mutex.withLock { reminderCoordinator.restore(state.first()) }
    }

    override suspend fun exportJson(): String = AttentionBackupCodec.encode(state.first())

    override suspend fun importJson(encoded: String, clearExisting: Boolean): AttentionState {
        val plan = backupWorkflow.prepareImport(
            encoded,
            if (clearExisting) ImportMode.REPLACE else ImportMode.MERGE,
        )
        return when (val result = backupWorkflow.commit(plan)) {
            is BackupCommitResult.Committed -> result.state
            is BackupCommitResult.StalePlan -> error("导入计划已过期，请重新预览")
            BackupCommitResult.NoImportBackup -> error("没有可恢复的导入前备份")
        }
    }

    /** Restores the latest snapshot and keeps one-step snapshot exchange. */
    suspend fun restoreLatestImportBackup(): BackupCommitResult =
        backupWorkflow.restoreLatestImportBackup()

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
