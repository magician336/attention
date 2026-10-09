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
import com.attention.domain.LaunchDestination
import com.attention.domain.StoredSettings
import com.attention.domain.Target
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
