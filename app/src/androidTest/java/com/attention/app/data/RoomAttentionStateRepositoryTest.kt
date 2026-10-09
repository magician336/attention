package com.attention.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.DataStoreSettingsStore
import com.attention.domain.LaunchDestination
import com.attention.domain.Target
import com.attention.domain.addTarget
import com.attention.domain.pauseTimer
import com.attention.domain.recalculateExperience
import com.attention.domain.recoverTimer
import com.attention.domain.resumeTimer
import com.attention.domain.startTimer
import com.attention.domain.stopTimer
import com.attention.domain.switchTimer
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomAttentionStateRepositoryTest {
    private lateinit var database: AttentionDatabase
    private lateinit var settingsFile: File
    private lateinit var settingsScope: CoroutineScope
    private lateinit var settings: DataStoreSettingsStore
    private lateinit var rawSettingsDataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AttentionDatabase::class.java,
        ).build()
        settingsFile = File.createTempFile("attention-room-repository", ".preferences_pb")
        settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        rawSettingsDataStore = PreferenceDataStoreFactory.create(scope = settingsScope) { settingsFile }
        settings = DataStoreSettingsStore(rawSettingsDataStore)
    }

    @After
    fun tearDown() {
        settingsScope.cancel()
        database.close()
        settingsFile.delete()
    }

    @Test
    fun business_commands_write_room_and_settings_commands_write_datastore() = runBlocking {
        val repository = repository()

        repository.updateBusiness { it.copy(targets = listOf(Target("target", title = "学习"))) }
        repository.setPlannerSettings(repository.state.first().settings.copy(planningDayBoundaryMinutes = 90))
        repository.setLaunchDestination(LaunchDestination.TARGETS)

        val state = repository.state.first()
        assertEquals("学习", state.targets.single().title)
        assertEquals(90, state.settings.planningDayBoundaryMinutes)
        assertEquals(LaunchDestination.TARGETS, state.launchDestination)
        assertEquals("学习", database.targetDao().findById("target")?.title)

        val storedKeys = rawSettingsDataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertFalse(storedKeys.contains("attention_state_json"))
        assertFalse(storedKeys.any { it.contains("target") || it.contains("time") || it.contains("schedule") || it.contains("timer") })
    }

    @Test
    fun timer_state_and_entries_survive_repository_restart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "attention-room-repository-restart.db"
        context.deleteDatabase(databaseName)
        val firstDatabase = Room.databaseBuilder(context, AttentionDatabase::class.java, databaseName).build()
        try {
            val first = RoomAttentionStateRepository(RoomBusinessDataRepository(firstDatabase), settings)
            first.updateBusiness { it.copy(targets = listOf(Target("target", title = "学习"))) }
            first.updateBusiness {
                it.startTimer("target", Instant.ofEpochMilli(1_000L), ZoneId.of("UTC"))
            }
            assertNotNull(first.state.first().activeTimer)
        } finally {
            firstDatabase.close()
        }

        val secondDatabase = Room.databaseBuilder(context, AttentionDatabase::class.java, databaseName).build()
        try {
            val second = RoomAttentionStateRepository(RoomBusinessDataRepository(secondDatabase), settings)
            assertEquals("target", second.state.first().activeTimer?.targetId)
            second.updateBusiness {
                it.stopTimer(Instant.ofEpochMilli(121_000L), ZoneId.of("UTC")).state.recalculateExperience()
            }
            val stopped = second.state.first()
            assertNull(stopped.activeTimer)
            assertEquals(2, stopped.timeEntries.single().durationMinutes)
            assertEquals("target", stopped.timeEntries.single().targetId)
        } finally {
            secondDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun business_update_does_not_reset_existing_settings() = runBlocking {
        val repository = repository()
        repository.setPlannerSettings(repository.state.first().settings.copy(planningDayBoundaryMinutes = 75))
        repository.setLaunchDestination(LaunchDestination.STATISTICS)

        repository.updateBusiness { it.copy(targets = listOf(Target("target", title = "统计"))) }

        val state = repository.state.first()
        assertEquals(75, state.settings.planningDayBoundaryMinutes)
        assertEquals(LaunchDestination.STATISTICS, state.launchDestination)
        assertTrue(state.targets.isNotEmpty())
    }

    @Test
    fun timer_pause_resume_switch_and_recover_use_room_transactions() = runBlocking {
        val repository = repository()
        repository.updateBusiness { it.copy(targets = listOf(Target("a", title = "A"), Target("b", title = "B"))) }

        repository.updateBusiness { it.startTimer("a", Instant.ofEpochMilli(0), ZoneId.of("UTC")) }
        repository.updateBusiness { it.pauseTimer(Instant.ofEpochMilli(60_000)) }
        assertTrue(repository.state.first().activeTimer?.paused == true)
        repository.updateBusiness { it.resumeTimer(Instant.ofEpochMilli(120_000)) }
        repository.updateBusiness { it.switchTimer("b", Instant.ofEpochMilli(180_000), ZoneId.of("UTC")).state }
        repository.updateBusiness { it.recoverTimer(Instant.ofEpochMilli(300_000)) }
        repository.updateBusiness {
            it.stopTimer(Instant.ofEpochMilli(360_000), ZoneId.of("UTC")).state.recalculateExperience()
        }

        val state = repository.state.first()
        assertNull(state.activeTimer)
        assertEquals(2, state.timeEntries.single { it.targetId == "a" }.durationMinutes)
        assertEquals(1, state.timeEntries.single { it.targetId == "b" }.durationMinutes)
    }

    @Test
    fun timer_stop_splits_entries_at_planning_day_boundary() = runBlocking {
        val repository = repository()
        repository.updateBusiness { it.copy(targets = listOf(Target("target", title = "跨规划日"))) }
        val start = Instant.parse("2026-10-10T02:59:00Z")
        val end = Instant.parse("2026-10-10T03:01:00Z")

        repository.updateBusiness { it.startTimer("target", start, ZoneId.of("UTC")) }
        repository.updateBusiness { it.stopTimer(end, ZoneId.of("UTC")).state }

        val entries = repository.state.first().timeEntries.sortedBy { it.planningDate }
        assertEquals(2, entries.size)
        assertEquals("2026-10-09", entries[0].planningDate)
        assertEquals("2026-10-10", entries[1].planningDate)
        assertEquals(listOf(1, 1), entries.map { it.durationMinutes })
    }

    @Test
    fun concurrent_room_commands_are_serialized_without_losing_targets() = runBlocking {
        val first = repository()
        val second = RoomAttentionStateRepository(RoomBusinessDataRepository(database), settings)

        val firstCommand = async(Dispatchers.IO) { first.updateBusiness { it.addTarget("并发 A") } }
        val secondCommand = async(Dispatchers.IO) { second.updateBusiness { it.addTarget("并发 B") } }
        firstCommand.await()
        secondCommand.await()

        assertEquals(setOf("并发 A", "并发 B"), repository().state.first().targets.map { it.title }.toSet())
    }

    private fun repository(): RoomAttentionStateRepository = RoomAttentionStateRepository(
        RoomBusinessDataRepository(database),
        settings,
    )

}
