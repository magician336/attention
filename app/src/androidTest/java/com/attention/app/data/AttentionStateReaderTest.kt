package com.attention.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.DataStoreSettingsStore
import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
import com.attention.domain.Target
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttentionStateReaderTest {
    private lateinit var database: AttentionDatabase
    private lateinit var settingsFile: File
    private lateinit var settingsScope: CoroutineScope

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AttentionDatabase::class.java,
        ).build()
        settingsFile = File.createTempFile("attention-reader-settings", ".preferences_pb")
        settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        settingsScope.cancel()
        runBlocking { settingsScope.coroutineContext[kotlinx.coroutines.Job]?.join() }
        database.close()
        settingsFile.delete()
    }

    private fun settingsStore(): DataStoreSettingsStore = DataStoreSettingsStore(
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = settingsScope) { settingsFile },
    )

    @Test
    fun reader_starts_with_defaults_and_composes_room_business_data() = runBlocking {
        val settings = settingsStore()
        val business = RoomBusinessDataRepository(database)
        val reader = AttentionStateReader(business, settings)

        val initial = reader.state.first()
        assertEquals(180, initial.settings.planningDayBoundaryMinutes)
        assertEquals(1, initial.settings.weekStartDay)
        assertEquals(LaunchDestination.TODAY, initial.launchDestination)
        assertTrue(initial.targets.isEmpty())

        business.replace(AttentionState(targets = listOf(Target("target", title = "学习"))))
        val withTarget = reader.state.first { it.targets.any { target -> target.id == "target" } }
        assertEquals("学习", withTarget.targets.single().title)
        assertEquals(180, withTarget.settings.planningDayBoundaryMinutes)
    }

    @Test
    fun settings_and_room_changes_each_refresh_the_combined_model() = runBlocking {
        val settings = settingsStore()
        val business = RoomBusinessDataRepository(database)
        val reader = AttentionStateReader(business, settings)

        settings.setPlanningDayBoundaryMinutes(90)
        settings.setLaunchDestination(LaunchDestination.TARGETS)
        settings.setNotificationsEnabled(false)
        val changedSettings = reader.state.first {
            it.settings.planningDayBoundaryMinutes == 90 &&
                it.launchDestination == LaunchDestination.TARGETS &&
                !it.notificationsEnabled
        }
        assertEquals(90, changedSettings.settings.planningDayBoundaryMinutes)

        business.replace(AttentionState(targets = listOf(Target("target", title = "阅读"))))
        val changedRoom = reader.state.first { it.targets.any { target -> target.title == "阅读" } }
        assertEquals(90, changedRoom.settings.planningDayBoundaryMinutes)
        assertEquals(LaunchDestination.TARGETS, changedRoom.launchDestination)
        assertEquals(false, changedRoom.notificationsEnabled)
    }
}
