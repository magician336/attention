package com.attention.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataStoreSettingsRepositoryTest {
    @Test
    fun settings_survive_a_new_repository_instance() = runBlocking {
        val file = Files.createTempFile("attention-settings", ".preferences_pb").toFile()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstRepository = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = firstScope) { file },
        )

        firstRepository.setPlanningDayBoundary(90)
        firstRepository.setWeekStartDay(DayOfWeek.SUNDAY)
        firstRepository.setDailyCapacity(420)
        firstScope.cancel()

        val afterRestart = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create { file },
        ).settings.first()

        assertEquals(90, afterRestart.planningDayBoundaryMinutes)
        assertEquals(DayOfWeek.SUNDAY, afterRestart.weekStartDay)
        assertEquals(420, afterRestart.dailyCapacityMinutes)
    }

    @Test
    fun clearing_capacity_is_persisted() = runBlocking {
        val file = Files.createTempFile("attention-settings", ".preferences_pb").toFile()
        val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = repositoryScope) { file },
        )

        repository.setDailyCapacity(60)
        repository.setDailyCapacity(null)

        assertNull(repository.settings.first().dailyCapacityMinutes)
        repositoryScope.cancel()
    }
}
