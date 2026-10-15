package com.attention.app.data

import com.attention.app.data.backup.AttentionBackupCodec
import com.attention.app.data.backup.BackupWorkflow
import com.attention.app.data.backup.InMemoryImportBackupStore
import com.attention.app.reminder.ReminderCoordinator
import com.attention.app.data.room.BusinessStateStore
import com.attention.app.data.room.BusinessStateTransition
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.SettingsStore
import com.attention.domain.AttentionCommand
import com.attention.domain.AttentionCommandResult
import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
import com.attention.domain.StoredSettings
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateCommitCoordinatorTest {
    @Test
    fun business_transition_rolls_back_room_when_reminder_sync_fails() = runBlocking {
        val before = AttentionState()
        val business = FakeBusinessStore(before)
        val reminders = RecordingReminders(failNext = true)
        val coordinator = coordinator(business, reminders)

        val failure = runCatching {
            coordinator.updateBusiness(StoredSettings()) { current ->
                current.copy(targets = listOf(com.attention.domain.Target("new", title = "新目标")))
            }
        }.exceptionOrNull()

        assertEquals("reminder failed", failure?.message)
        assertEquals(before, business.current)
        assertEquals(1, business.replaceCalls)
        assertEquals(listOf("before->after", "after->before"), reminders.transitions)
    }

    @Test
    fun submit_rotates_the_pre_submit_backup_after_fingerprint_check() = runBlocking {
        val before = AttentionState()
        val after = before.copy(targets = listOf(com.attention.domain.Target("next", title = "下一目标")))
        val business = FakeBusinessStore(before)
        val backupStore = InMemoryImportBackupStore()
        val coordinator = coordinator(business, RecordingReminders(), backupStore)

        val result = coordinator.submit(BackupWorkflow.stateFingerprint(before), after)

        assertTrue(result.isCommitted)
        assertEquals(after, business.current)
        assertEquals(AttentionBackupCodec.encode(before), backupStore.read())
    }

    private fun coordinator(
        business: FakeBusinessStore,
        reminders: RecordingReminders,
        backupStore: InMemoryImportBackupStore = InMemoryImportBackupStore(),
    ) = StateCommitCoordinator(
        business = business,
        settings = FakeSettingsStore(),
        backupStore = backupStore,
        reminderCoordinator = reminders,
        readState = { business.current },
    )
}

private class FakeBusinessStore(initial: AttentionState) : BusinessStateStore {
    var current: AttentionState = initial
    var replaceCalls: Int = 0

    override suspend fun replace(state: AttentionState) {
        replaceCalls += 1
        current = state
    }

    override suspend fun updateTransition(
        plannerSettings: StoredSettings,
        transform: (AttentionState) -> AttentionState,
    ): BusinessStateTransition<AttentionState> {
        val previous = current.copy(settings = plannerSettings)
        val next = transform(previous)
        current = next
        return BusinessStateTransition(previous, next, next)
    }

    override suspend fun executeTransition(
        command: AttentionCommand,
        now: Instant,
        zone: ZoneId,
        plannerSettings: StoredSettings,
    ): BusinessStateTransition<AttentionCommandResult> = error("unused in this test")

    override suspend fun toggleScheduleCompletedTransition(
        entryId: String,
        plannerSettings: StoredSettings,
    ): BusinessStateTransition<AttentionState> = error("unused in this test")
}

private class RecordingReminders(
    var failNext: Boolean = false,
) : ReminderCoordinator {
    val transitions = mutableListOf<String>()

    override fun sync(previous: AttentionState, next: AttentionState) {
        transitions += "${label(previous)}->${label(next)}"
        if (failNext) {
            failNext = false
            error("reminder failed")
        }
    }

    private fun label(state: AttentionState): String =
        if (state.targets.isEmpty()) "before" else "after"
}

private class FakeSettingsStore : SettingsStore {
    private val values = MutableStateFlow(SettingsSnapshot())
    override val settings: Flow<SettingsSnapshot> = values

    override suspend fun setSnapshot(snapshot: SettingsSnapshot) {
        values.value = snapshot
    }

    override suspend fun setPlannerSettings(settings: StoredSettings) = Unit
    override suspend fun setPlanningDayBoundaryMinutes(minutes: Int) = Unit
    override suspend fun setWeekStartDay(day: Int) = Unit
    override suspend fun setDailyCapacityMinutes(minutes: Int?) = Unit
    override suspend fun setLaunchDestination(destination: LaunchDestination) = Unit
    override suspend fun setLastOpenedDestination(destination: LaunchDestination) = Unit
    override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
    override suspend fun setNotificationsEnabled(enabled: Boolean) = Unit
    override suspend fun reset() = Unit
}
