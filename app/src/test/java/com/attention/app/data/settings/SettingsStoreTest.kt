package com.attention.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.attention.domain.LaunchDestination
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {
    @Test
    fun new_store_exposes_product_defaults_and_only_settings_keys() = runBlocking {
        val file = Files.createTempFile("attention-settings", ".preferences_pb").toFile()
        val dataStore = PreferenceDataStoreFactory.create { file }
        val store = DataStoreSettingsStore(dataStore)

        assertEquals(SettingsSnapshot(), store.settings.first())
        assertTrue(dataStore.data.first().asMap().isEmpty())
        file.delete()
        Unit
    }

    @Test
    fun typed_updates_and_clear_are_observable_and_survive_restart() = runBlocking {
        val file = Files.createTempFile("attention-settings", ".preferences_pb").toFile()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = firstScope) { file }
        val store = DataStoreSettingsStore(dataStore)

        store.setPlanningDayBoundaryMinutes(90)
        store.setWeekStartDay(7)
        store.setDailyCapacityMinutes(420)
        store.setLaunchDestination(LaunchDestination.TARGETS)
        store.setLastOpenedDestination(LaunchDestination.STATISTICS)
        store.setOnboardingCompleted(true)
        store.setNotificationsEnabled(false)
        assertEquals(
            SettingsSnapshot(90, 7, 420, LaunchDestination.TARGETS, LaunchDestination.STATISTICS, true, false),
            store.settings.first(),
        )
        val storedKeyNames = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertFalse(storedKeyNames.contains("attention_state_json"))
        assertFalse(storedKeyNames.any { it.contains("target") || it.contains("time") || it.contains("schedule") || it.contains("timer") })

        store.setDailyCapacityMinutes(null)
        assertNull(store.settings.first().dailyCapacityMinutes)

        firstScope.cancel()
        firstScope.coroutineContext[kotlinx.coroutines.Job]?.join()
        val afterRestart = DataStoreSettingsStore(PreferenceDataStoreFactory.create { file }).settings.first()
        assertEquals(90, afterRestart.planningDayBoundaryMinutes)
        assertEquals(7, afterRestart.weekStartDay)
        assertNull(afterRestart.dailyCapacityMinutes)
        assertEquals(LaunchDestination.TARGETS, afterRestart.launchDestination)
        assertEquals(LaunchDestination.STATISTICS, afterRestart.lastOpenedDestination)
        assertTrue(afterRestart.onboardingCompleted)
        assertFalse(afterRestart.notificationsEnabled)
        file.delete()
        Unit
    }

    @Test
    fun reset_restores_defaults_and_invalid_values_are_rejected() = runBlocking {
        val file = Files.createTempFile("attention-settings", ".preferences_pb").toFile()
        val store = DataStoreSettingsStore(PreferenceDataStoreFactory.create { file })

        runCatching { store.setPlanningDayBoundaryMinutes(1440) }.onSuccess { error("boundary accepted") }
        runCatching { store.setWeekStartDay(0) }.onSuccess { error("week start accepted") }
        runCatching { store.setDailyCapacityMinutes(0) }.onSuccess { error("capacity accepted") }

        store.setOnboardingCompleted(true)
        store.reset()
        assertEquals(SettingsSnapshot(), store.settings.first())
        file.delete()
        Unit
    }
}
