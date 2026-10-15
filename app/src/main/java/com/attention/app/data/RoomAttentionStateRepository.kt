package com.attention.app.data

import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.backup.AttentionBackupCodec
import com.attention.app.data.backup.BackupCommitResult
import com.attention.app.data.backup.BackupStateSubmissionPort
import com.attention.app.data.backup.BackupWorkflow
import com.attention.app.data.backup.ImportMode
import com.attention.app.data.backup.ImportBackupStore
import com.attention.app.data.backup.InMemoryImportBackupStore
import com.attention.app.data.settings.SettingsStore
import com.attention.app.reminder.ReminderCoordinator
import com.attention.domain.AttentionState
import com.attention.domain.AttentionCommand
import com.attention.domain.AttentionCommandResult
import com.attention.domain.LaunchDestination
import com.attention.domain.StoredSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
) : AttentionStateRepository, BackupStateSubmissionPort {
    /** Typed workflow used by ViewModel and other non-Android callers. */
    override val backupWorkflow: BackupWorkflow by lazy {
        BackupWorkflow(this, backupStore)
    }

    override val state: Flow<AttentionState> = AttentionStateReader(business, settings).state

    private val commitCoordinator = StateCommitCoordinator(
        business = business,
        settings = settings,
        backupStore = backupStore,
        reminderCoordinator = reminderCoordinator,
        readState = { state.first() },
    )

    override suspend fun updateBusiness(transform: (AttentionState) -> AttentionState) {
        val plannerSettings = settings.settings.first().toStoredSettings()
        commitCoordinator.updateBusiness(plannerSettings, transform)
    }

    override suspend fun executeCommand(
        command: AttentionCommand,
        now: Instant,
        zone: ZoneId,
    ): AttentionCommandResult {
        val previous = state.first()
        val result = commitCoordinator.executeCommand(command, now, zone, plannerSettings = previous.settings)
        return result.copy(state = result.state.copy(
            settings = previous.settings,
            launchDestination = previous.launchDestination,
            lastOpenedDestination = previous.lastOpenedDestination,
            onboardingCompleted = previous.onboardingCompleted,
            notificationsEnabled = previous.notificationsEnabled,
        ))
    }

    override suspend fun toggleScheduleCompleted(entryId: String) {
        val plannerSettings = settings.settings.first().toStoredSettings()
        commitCoordinator.toggleScheduleCompleted(entryId, plannerSettings)
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
        commitCoordinator.replace(state)
    }

    /**
     * Reads the complete composed state for [BackupStateSubmissionPort]. The
     * submission implementation performs its own read again while holding the
     * coordinator lock, so this method is safe for workflow preview and
     * fingerprinting.
     */
    override suspend fun read(): AttentionState = state.first()

    /**
     * Re-checks a plan fingerprint and, when current, rotates the import
     * snapshot before replacing Room, DataStore and reminders. The entire
     * operation is serialized by the coordinator; any failure compensates all
     * three stores and restores the previous snapshot value.
     */
    override suspend fun submit(
        expectedFingerprint: String,
        next: AttentionState,
    ): BackupCommitResult = commitCoordinator.submit(expectedFingerprint, next)

    /**
     * Compatibility import preparation can fail before a typed plan exists.
     * Keep its historical recovery guarantee inside the same mutex and port
     * boundary used by successful submissions.
     */
    override suspend fun preserveImportBackupIfAbsent() = commitCoordinator.preserveImportBackupIfAbsent()

    override suspend fun restoreReminders() {
        commitCoordinator.restoreReminders()
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

}
