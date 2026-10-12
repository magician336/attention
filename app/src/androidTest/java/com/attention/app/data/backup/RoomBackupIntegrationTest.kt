package com.attention.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.RoomAttentionStateRepository
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.SettingsStore
import com.attention.domain.AttentionState
import com.attention.domain.ActiveTimer
import com.attention.domain.CapacitySummary
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.LaunchDestination
import com.attention.domain.Migration
import com.attention.domain.Milestone
import com.attention.domain.ScheduleEntry
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleFrequency
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import com.attention.domain.TimeEntrySource
import com.attention.domain.TimerSegment
import com.attention.domain.addMigration
import com.attention.domain.cancelMigration
import com.attention.domain.capacitySummary
import com.attention.domain.updateMigration
import com.attention.domain.stopTimer
import com.attention.domain.recalculateExperience
import java.time.LocalDate
import java.time.Instant
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomBackupIntegrationTest {
    private lateinit var database: AttentionDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AttentionDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun versioned_backup_supports_merge_clear_and_pre_import_backup() = runBlocking {
        val backupStore = InMemoryImportBackupStore()
        val repository = RoomAttentionStateRepository(
            RoomBusinessDataRepository(database),
            settingsStore(),
            backupStore,
        )
        repository.updateBusiness { it.copy(targets = listOf(Target("old", title = "旧"))) }
        val exported = repository.exportJson()
        assertTrue(exported.contains("\"version\":1"))

        repository.updateBusiness { it.copy(targets = it.targets + Target("extra", title = "额外")) }
        val preImport = repository.exportJson()
        repository.importJson(exported, clearExisting = false)
        assertEquals(setOf("old", "extra"), repository.state.first().targets.map { it.id }.toSet())
        assertEquals(preImport, backupStore.read())

        repository.importJson(exported, clearExisting = true)
        assertEquals(listOf("old"), repository.state.first().targets.map { it.id })
    }

    @Test
    fun restoring_backup_recomputes_capacity_from_settings_and_schedules() = runBlocking {
        val day = "2026-10-08"
        val repository = RoomAttentionStateRepository(
            RoomBusinessDataRepository(database),
            settingsStore(),
        )
        val expected = AttentionState(
            settings = StoredSettings(planningDayBoundaryMinutes = 120, dailyCapacityMinutes = 60),
            schedules = listOf(
                ScheduleEntry(
                    id = "estimated",
                    planningDate = day,
                    title = "预计优先",
                    startMinute = 60,
                    endMinute = 180,
                    estimatedMinutes = 20,
                    completed = true,
                ),
                ScheduleEntry(
                    id = "range",
                    planningDate = day,
                    title = "完整时间段",
                    startMinute = 180,
                    endMinute = 220,
                ),
            ),
        )

        repository.replace(expected)
        val exported = repository.exportJson()
        repository.replace(AttentionState())
        val restored = repository.importJson(exported, clearExisting = true)

        assertEquals(expected.settings, restored.settings)
        assertEquals(expected.schedules, restored.schedules)
        assertEquals(CapacitySummary(60, 60, false), restored.capacitySummary(day))
        assertEquals(CapacitySummary(60, 0, false), restored.capacitySummary("2026-10-09"))
    }

    @Test
    fun active_timer_backup_round_trip_preserves_segments_and_timer_entries() = runBlocking {
        val target = Target("timer-target", title = "计时目标")
        val expected = AttentionState(
            targets = listOf(target),
            timeEntries = listOf(
                TimeEntry("timer-entry-1", "2026-10-08", 15, target.id, TimeEntrySource.TIMER, 100L, "计时"),
                TimeEntry("timer-entry-2", "2026-10-08", 20, null, TimeEntrySource.TIMER, 200L, "未归属计时"),
            ),
            activeTimer = ActiveTimer(
                targetId = target.id,
                startedAtEpochMillis = 300L,
                accumulatedMillis = 1_200_000L,
                paused = true,
                lastPlanningDate = "2026-10-08",
                completedSegments = listOf(
                    TimerSegment(100L, 400L),
                    TimerSegment(500L, 800L),
                ),
            ),
        )
        val repository = RoomAttentionStateRepository(
            RoomBusinessDataRepository(database),
            settingsStore(),
        )

        repository.replace(expected)
        val exported = repository.exportJson()
        repository.replace(AttentionState())

        val restored = repository.importJson(exported, clearExisting = true)

        assertEquals(expected.timeEntries, restored.timeEntries)
        assertEquals(expected.activeTimer, restored.activeTimer)
        assertEquals(expected.activeTimer, repository.state.first().activeTimer)
    }

    @Test
    fun restored_active_timer_can_be_stopped_without_duplicate_timer_entries() = runBlocking {
        val target = Target("restored-target", title = "恢复目标")
        val expected = AttentionState(
            targets = listOf(target),
            timeEntries = listOf(
                TimeEntry("existing-timer-entry", "2026-10-08", 10, target.id, TimeEntrySource.TIMER, 100L, "计时"),
            ),
            activeTimer = ActiveTimer(
                targetId = target.id,
                startedAtEpochMillis = 100_000L,
                paused = true,
                lastPlanningDate = "2026-10-08",
                completedSegments = listOf(TimerSegment(100L, 600_100L)),
            ),
        )
        val repository = RoomAttentionStateRepository(
            RoomBusinessDataRepository(database),
            settingsStore(),
        )
        repository.replace(expected)
        val exported = repository.exportJson()
        repository.replace(AttentionState())
        repository.importJson(exported, clearExisting = true)

        repository.updateBusiness {
            it.stopTimer(Instant.ofEpochMilli(660_100L)).state
        }

        val stopped = repository.state.first()
        assertEquals(null, stopped.activeTimer)
        assertEquals(2, stopped.timeEntries.size)
        assertTrue(stopped.timeEntries.any { it.id == "existing-timer-entry" })
        assertTrue(stopped.timeEntries.all { it.source == TimeEntrySource.TIMER })
    }

    @Test
    fun timer_entry_merge_and_clear_restore_preserve_stable_ids_without_duplicates() = runBlocking {
        val target = Target("stable-timer-target", title = "稳定计时目标")
        val existingEntry = TimeEntry(
            id = "stable-timer-entry",
            planningDate = "2026-10-08",
            durationMinutes = 10,
            targetId = target.id,
            source = TimeEntrySource.TIMER,
            occurredAtEpochMillis = 100L,
        )
        val incomingEntry = existingEntry.copy(durationMinutes = 15)
        val newEntry = existingEntry.copy(id = "new-timer-entry", durationMinutes = 20)
        val repository = RoomAttentionStateRepository(
            RoomBusinessDataRepository(database),
            settingsStore(),
        )
        repository.replace(
            AttentionState(
                targets = listOf(target),
                timeEntries = listOf(existingEntry),
            ),
        )
        val incoming = AttentionBackupCodec.encode(
            AttentionState(
                targets = listOf(target),
                timeEntries = listOf(incomingEntry, newEntry),
            ),
        )

        repository.importJson(incoming, clearExisting = false)
        assertEquals(
            setOf(existingEntry.id, newEntry.id),
            repository.state.first().timeEntries.map { it.id }.toSet(),
        )

        repository.importJson(incoming, clearExisting = true)
        assertEquals(
            listOf(incomingEntry, newEntry).sortedBy { it.id },
            repository.state.first().timeEntries.sortedBy { it.id },
        )
    }

    @Test
    fun invalid_import_timer_target_rolls_back_timer_records_experience_and_milestones() = runBlocking {
        val target = Target("current-target", title = "当前目标")
        val current = AttentionState(
            targets = listOf(target),
            timeEntries = listOf(TimeEntry("current-timer-entry", "2026-10-08", 25, target.id, TimeEntrySource.TIMER, 100L, "计时")),
            milestones = listOf(com.attention.domain.Milestone("current-milestone", "goal_period", "current", 20L, 200L)),
            experience = 45L,
            activeTimer = ActiveTimer(target.id, 300L, paused = true, lastPlanningDate = "2026-10-08"),
        )
        val incoming = AttentionBackupCodec.encode(
            AttentionState(
                targets = listOf(Target("incoming-target", title = "导入目标")),
                timeEntries = listOf(TimeEntry("incoming-timer-entry", "2026-10-09", 30, "incoming-target", TimeEntrySource.TIMER, 400L, "导入计时")),
                experience = 100L,
                activeTimer = ActiveTimer("missing-target", 500L, lastPlanningDate = "2026-10-09"),
            ),
        )
        val backupStore = InMemoryImportBackupStore()
        val repository = RoomAttentionStateRepository(
            RoomBusinessDataRepository(database),
            settingsStore(),
            backupStore,
        )
        repository.replace(current)

        assertFalse(runCatching { repository.importJson(incoming, clearExisting = true) }.isSuccess)
        assertEquals(current, repository.state.first())
        assertEquals(AttentionBackupCodec.encode(current), backupStore.read())
    }

    @Test
    fun archived_import_timer_target_is_rejected_without_changing_current_timer() = runBlocking {
        val currentTarget = Target("current-target", title = "当前目标")
        val current = AttentionState(
            targets = listOf(currentTarget),
            activeTimer = ActiveTimer(currentTarget.id, 100L, lastPlanningDate = "2026-10-08"),
        )
        val archivedTarget = Target("archived-target", title = "归档目标", archived = true)
        val incoming = AttentionBackupCodec.encode(
            AttentionState(
                targets = listOf(archivedTarget),
                activeTimer = ActiveTimer(archivedTarget.id, 200L, lastPlanningDate = "2026-10-09"),
            ),
        )
        val repository = RoomAttentionStateRepository(
            RoomBusinessDataRepository(database),
            settingsStore(),
        )
        repository.replace(current)

        assertFalse(runCatching { repository.importJson(incoming, clearExisting = true) }.isSuccess)
        assertEquals(current, repository.state.first())
    }

    @Test
    fun settings_failure_rolls_back_room_and_retains_pre_import_backup() = runBlocking {
        val backupStore = InMemoryImportBackupStore()
        val settings = FailingSettingsStore()
        val repository = RoomAttentionStateRepository(
            RoomBusinessDataRepository(database),
            settings,
            backupStore,
        )
        repository.updateBusiness { it.copy(targets = listOf(Target("old", title = "旧"))) }
        val current = repository.state.first()
        val incoming = AttentionBackupCodec.encode(
            AttentionState(
                settings = StoredSettings(planningDayBoundaryMinutes = 240),
                targets = listOf(Target("new", title = "新")),
            ),
        )

        assertFalse(runCatching { repository.importJson(incoming, clearExisting = true) }.isSuccess)
        assertEquals(current.targets, repository.state.first().targets)
        assertEquals(current.settings, repository.state.first().settings)
        assertEquals(AttentionBackupCodec.encode(current), backupStore.read())
        assertTrue(settings.rollbackSucceeded)
    }

    @Test
    fun file_backup_survives_a_new_store_instance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileName = "attention-backup-test-${System.nanoTime()}.json"
        try {
            FileImportBackupStore(context, fileName).write("{\"version\":1}")
            assertEquals("{\"version\":1}", FileImportBackupStore(context, fileName).read())
        } finally {
            context.deleteFile(fileName)
        }
    }

    @Test
    fun migration_lifecycle_is_persisted_by_room() = runBlocking {
        val target = Target("migration-target", title = "项目")
        val stage = GoalStage("migration-stage", target.id, GoalCadence.WEEKLY, 240, "2026-10-01")
        val migration = Migration(
            id = "migration-id",
            targetId = target.id,
            sourceStageId = stage.id,
            minutes = 60,
            destinationStartDate = "2026-10-08",
        )
        val repository = RoomAttentionStateRepository(
            RoomBusinessDataRepository(database),
            settingsStore(),
        )

        repository.updateBusiness { it.copy(targets = listOf(target), goalStages = listOf(stage)) }
        repository.updateBusiness { it.addMigration(migration, LocalDate.parse("2026-10-02")) }
        repository.updateBusiness {
            it.updateMigration(migration.copy(minutes = 90, destinationEndDate = "2026-10-09"), LocalDate.parse("2026-10-02"))
        }
        repository.updateBusiness { it.cancelMigration(migration.id) }

        assertEquals(
            migration.copy(minutes = 90, destinationEndDate = "2026-10-09", cancelled = true),
            repository.state.first().migrations.single(),
        )
    }

    @Test
    fun migration_backup_merge_and_clear_preserve_stable_ids_and_cancelled_state() = runBlocking {
        val target = Target("migration-target", title = "项目")
        val stage = GoalStage("migration-stage", target.id, GoalCadence.WEEKLY, 240, "2026-10-01")
        val cancelled = Migration(
            id = "cancelled-migration",
            targetId = target.id,
            sourceStageId = stage.id,
            minutes = 60,
            destinationStartDate = "2026-10-08",
            cancelled = true,
        )
        val incomingOnly = Migration(
            id = "incoming-migration",
            targetId = target.id,
            sourceStageId = stage.id,
            minutes = 30,
            destinationStartDate = "2026-10-10",
        )
        val repository = RoomAttentionStateRepository(
            RoomBusinessDataRepository(database),
            settingsStore(),
        )
        repository.replace(
            AttentionState(
                targets = listOf(target),
                goalStages = listOf(stage),
                migrations = listOf(cancelled),
            ),
        )
        val incoming = AttentionBackupCodec.encode(
            AttentionState(
                targets = listOf(target),
                goalStages = listOf(stage),
                migrations = listOf(cancelled.copy(cancelled = false), incomingOnly),
            ),
        )

        repository.importJson(incoming, clearExisting = false)
        val merged = repository.state.first()
        assertEquals(listOf("cancelled-migration", "incoming-migration"), merged.migrations.map { it.id })
        assertTrue(merged.migrations.first().cancelled)

        repository.importJson(incoming, clearExisting = true)
        assertEquals(listOf(cancelled.copy(cancelled = false), incomingOnly), repository.state.first().migrations)
    }

    @Test
    fun backup_merge_and_clear_restore_recomputed_experience() = runBlocking {
        val target = Target("experience-target", title = "项目")
        val repository = RoomAttentionStateRepository(
            RoomBusinessDataRepository(database),
            settingsStore(),
        )
        repository.replace(
            AttentionState(
                targets = listOf(target),
                timeEntries = listOf(TimeEntry("existing", "2026-10-08", 10, target.id)),
                milestones = listOf(Milestone("existing-milestone", "streak", "target:7", 20, 1L)),
                experience = 10_000L,
            ),
        )
        val incoming = AttentionBackupCodec.encode(
            AttentionState(
                targets = listOf(target),
                timeEntries = listOf(TimeEntry("incoming", "2026-10-09", 5, target.id)),
                milestones = listOf(Milestone("incoming-milestone", "goal_period", "stage:1", 30, 2L)),
                experience = 1L,
            ),
        )

        val merged = repository.importJson(incoming, clearExisting = false)
        assertEquals(65L, merged.experience)
        assertEquals(65L, repository.state.first().experience)

        val restored = repository.importJson(incoming, clearExisting = true)
        assertEquals(35L, restored.experience)
        assertEquals(35L, repository.state.first().experience)
        assertEquals(5, restored.timeEntries.single().durationMinutes)
    }

    @Test
    fun deleted_target_relations_round_trip_as_unowned_history() = runBlocking {
        val target = Target("deleted-target", title = "已移除目标", archived = true)
        val state = AttentionState(
            targets = listOf(target),
            timeEntries = listOf(
                TimeEntry("orphaned-time", "2026-10-10", 40, target.id, note = "历史投入"),
            ),
            schedules = listOf(
                ScheduleEntry(
                    id = "orphaned-schedule",
                    planningDate = "2026-10-10",
                    title = "保留日程",
                    targetId = target.id,
                    recurrenceRuleId = "orphaned-rule",
                ),
            ),
            recurrenceRules = listOf(
                RecurrenceRule(
                    id = "orphaned-rule",
                    title = "保留重复日程",
                    startDate = "2026-10-10",
                    frequency = ScheduleFrequency.DAILY,
                    targetId = target.id,
                ),
            ),
            milestones = listOf(Milestone("deleted-milestone", "goal_period", "deleted:period", 25L, 10L)),
        )
        val repository = RoomAttentionStateRepository(
            RoomBusinessDataRepository(database),
            settingsStore(),
        )
        repository.replace(state)
        RoomBusinessDataRepository(database).deleteTargetRelations(setOf(target.id))

        val afterDelete = repository.state.first()
        assertTrue(afterDelete.targets.isEmpty())
        assertEquals(null, afterDelete.timeEntries.single().targetId)
        assertEquals(null, afterDelete.schedules.single().targetId)
        assertEquals(null, afterDelete.recurrenceRules.single().targetId)
        assertEquals(25L, afterDelete.experience)

        val exported = repository.exportJson()
        repository.replace(AttentionState())
        val restored = repository.importJson(exported, clearExisting = true)

        assertEquals(afterDelete.timeEntries, restored.timeEntries)
        assertEquals(afterDelete.schedules, restored.schedules)
        assertEquals(afterDelete.recurrenceRules, restored.recurrenceRules)
        assertEquals(afterDelete.milestones, restored.milestones)
        assertEquals(afterDelete.experience, restored.experience)
    }

    @Test
    fun archived_history_and_rewards_survive_room_restart_after_backup_restore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "attention-backup-restart-${System.nanoTime()}.db"
        val target = Target("restart-target", title = "重启归档目标", archived = true)
        val expected = AttentionState(
            targets = listOf(target),
            timeEntries = listOf(TimeEntry("restart-time", "2026-10-11", 60, null)),
            schedules = listOf(ScheduleEntry("restart-schedule", "2026-10-11", "未归属日程")),
            recurrenceRules = listOf(
                RecurrenceRule("restart-rule", "未归属重复日程", "2026-10-11", ScheduleFrequency.WEEKLY),
            ),
            milestones = listOf(Milestone("restart-milestone", "cumulative", "total:1", 30L, 11L)),
        )
        try {
            val firstDatabase = Room.databaseBuilder(context, AttentionDatabase::class.java, databaseName).build()
            val first = RoomAttentionStateRepository(
                RoomBusinessDataRepository(firstDatabase),
                settingsStore(),
            )
            first.replace(expected)
            val encoded = first.exportJson()
            firstDatabase.close()

            val secondDatabase = Room.databaseBuilder(context, AttentionDatabase::class.java, databaseName).build()
            val second = RoomAttentionStateRepository(
                RoomBusinessDataRepository(secondDatabase),
                settingsStore(),
            )
            val restored = second.importJson(encoded, clearExisting = true)
            secondDatabase.close()

            val thirdDatabase = Room.databaseBuilder(context, AttentionDatabase::class.java, databaseName).build()
            val afterRestart = RoomBusinessDataRepository(thirdDatabase).read()
            assertEquals(restored.targets, afterRestart.targets)
            assertEquals(restored.timeEntries, afterRestart.timeEntries)
            assertEquals(restored.schedules, afterRestart.schedules)
            assertEquals(restored.recurrenceRules, afterRestart.recurrenceRules)
            assertEquals(restored.milestones, afterRestart.milestones)
            assertEquals(restored.experience, afterRestart.experience)
            thirdDatabase.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    private fun settingsStore(): com.attention.app.data.settings.DataStoreSettingsStore =
        com.attention.app.data.settings.DataStoreSettingsStore(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create {
                File.createTempFile("attention-backup-settings", ".preferences_pb")
            },
        )
}

private class FailingSettingsStore : SettingsStore {
    private val current = MutableStateFlow(SettingsSnapshot())
    private var failNextSnapshot = true
    var rollbackSucceeded = false
    override val settings: Flow<SettingsSnapshot> = current

    override suspend fun setSnapshot(snapshot: SettingsSnapshot) {
        current.value = snapshot
        if (failNextSnapshot) {
            failNextSnapshot = false
            error("设置写入失败")
        }
        rollbackSucceeded = true
    }

    override suspend fun setPlannerSettings(settings: StoredSettings) {
        current.value = current.value.copy(
            planningDayBoundaryMinutes = settings.planningDayBoundaryMinutes,
            weekStartDay = settings.weekStartDay,
            dailyCapacityMinutes = settings.dailyCapacityMinutes,
        )
    }

    override suspend fun setPlanningDayBoundaryMinutes(minutes: Int) {
        current.value = current.value.copy(planningDayBoundaryMinutes = minutes)
    }

    override suspend fun setWeekStartDay(day: Int) {
        current.value = current.value.copy(weekStartDay = day)
    }

    override suspend fun setDailyCapacityMinutes(minutes: Int?) {
        current.value = current.value.copy(dailyCapacityMinutes = minutes)
    }

    override suspend fun setLaunchDestination(destination: LaunchDestination) {
        current.value = current.value.copy(launchDestination = destination)
    }

    override suspend fun setLastOpenedDestination(destination: LaunchDestination) {
        current.value = current.value.copy(lastOpenedDestination = destination)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        current.value = current.value.copy(onboardingCompleted = completed)
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        current.value = current.value.copy(notificationsEnabled = enabled)
    }

    override suspend fun reset() {
        current.value = SettingsSnapshot()
    }
}
