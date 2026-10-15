package com.attention.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.attention.domain.LaunchDestination
import com.attention.domain.asAgendaEntry
import com.attention.domain.resolveLaunchDestination
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

    @Test
    fun invalid_capacity_update_keeps_the_previous_valid_value() = runBlocking {
        val file = Files.createTempFile("attention-settings", ".preferences_pb").toFile()
        val store = DataStoreSettingsStore(PreferenceDataStoreFactory.create { file })

        store.setDailyCapacityMinutes(420)
        runCatching { store.setDailyCapacityMinutes(0) }
        runCatching { store.setDailyCapacityMinutes(-15) }

        assertEquals(420, store.settings.first().dailyCapacityMinutes)
        file.delete()
        Unit
    }

    @Test
    fun unknown_launch_value_falls_back_to_the_agenda_entry_without_overwriting_other_settings() = runBlocking {
        val file = Files.createTempFile("attention-settings", ".preferences_pb").toFile()
        val dataStore = PreferenceDataStoreFactory.create { file }
        dataStore.edit {
            it[stringPreferencesKey("launch_destination")] = "REMOVED_DESTINATION"
            it[stringPreferencesKey("last_opened_destination")] = LaunchDestination.TARGETS.name
        }
        val store = DataStoreSettingsStore(dataStore)

        // 无法解析的启动偏好是无效值而不是有效历史：回退到日程入口，
        // 而不是把已删除的旧入口名 TODAY 当成默认值继续沿用。
        assertEquals(LaunchDestination.AGENDA, store.settings.first().launchDestination)
        assertEquals(LaunchDestination.TARGETS, store.settings.first().lastOpenedDestination)
        store.setLaunchDestination(LaunchDestination.STATISTICS)
        val updated = store.settings.first()
        assertEquals(LaunchDestination.STATISTICS, updated.launchDestination)
        assertEquals(LaunchDestination.TARGETS, updated.lastOpenedDestination)
        file.delete()
        Unit
    }

    @Test
    fun legacy_last_opened_history_is_read_as_the_schedule_entry() = runBlocking {
        // 旧版本写入的「上次打开」历史等价于日程入口，无法解析的历史回退到日程
        listOf("TODAY", "ALL_DATES", "REMOVED_DESTINATION").forEach { persisted ->
            val file = Files.createTempFile("attention-settings", ".preferences_pb").toFile()
            val dataStore = PreferenceDataStoreFactory.create { file }
            dataStore.edit {
                it[stringPreferencesKey("last_opened_destination")] = persisted
            }
            val store = DataStoreSettingsStore(dataStore)

            assertEquals(LaunchDestination.AGENDA, store.settings.first().lastOpenedDestination)
            // 旧枚举值本身仍然读得出来（它没有被删掉），但缺省值已经是 AGENDA
            assertEquals(LaunchDestination.AGENDA, store.settings.first().launchDestination)
            file.delete()
        }
        Unit
    }

    @Test
    fun a_legacy_launch_value_is_still_readable_and_still_opens_the_schedule_entry() = runBlocking {
        // 兼容红线：历史 DataStore 里的 TODAY / ALL_DATES 必须仍能读出来，
        // 并且仍然等价于日程入口。默认值改成 AGENDA 不等于删掉旧值。
        listOf("TODAY", "ALL_DATES").forEach { persisted ->
            val file = Files.createTempFile("attention-settings", ".preferences_pb").toFile()
            val dataStore = PreferenceDataStoreFactory.create { file }
            dataStore.edit {
                it[stringPreferencesKey("launch_destination")] = persisted
            }
            val store = DataStoreSettingsStore(dataStore)

            val launch = store.settings.first().launchDestination
            assertEquals(LaunchDestination.valueOf(persisted), launch)
            assertEquals(LaunchDestination.AGENDA, launch.asAgendaEntry())
            file.delete()
        }
        Unit
    }

    @Test
    fun the_product_default_entry_is_the_agenda_entry() = runBlocking {
        // 新的默认快照不再把旧入口名 TODAY 写回 DataStore
        val file = Files.createTempFile("attention-settings", ".preferences_pb").toFile()
        val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { file }
        val store = DataStoreSettingsStore(dataStore)

        assertEquals(LaunchDestination.AGENDA, SettingsSnapshot().launchDestination)
        store.setSnapshot(SettingsSnapshot())
        val persisted = dataStore.data.first().asMap()
            .mapKeys { it.key.name }["launch_destination"]
        assertEquals("AGENDA", persisted)
        file.delete()
        Unit
    }

    @Test
    fun a_self_referencing_last_opened_history_resolves_to_the_schedule_entry() = runBlocking {
        // 历史记录为「上次打开」本身时，读取保持原值，解析回退到日程入口
        val file = Files.createTempFile("attention-settings", ".preferences_pb").toFile()
        val dataStore = PreferenceDataStoreFactory.create { file }
        dataStore.edit {
            it[stringPreferencesKey("last_opened_destination")] = LaunchDestination.LAST_OPENED.name
        }
        val store = DataStoreSettingsStore(dataStore)

        val lastOpened = store.settings.first().lastOpenedDestination
        assertEquals(LaunchDestination.LAST_OPENED, lastOpened)
        assertEquals(
            LaunchDestination.AGENDA,
            resolveLaunchDestination(LaunchDestination.LAST_OPENED, lastOpened),
        )
        file.delete()
        Unit
    }
}
