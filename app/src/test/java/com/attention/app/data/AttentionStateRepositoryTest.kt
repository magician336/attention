package com.attention.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.attention.domain.AttentionState
import com.attention.domain.StoredSettings
import com.attention.domain.addTarget
import com.attention.domain.addTimeEntry
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AttentionStateRepositoryTest {
    @Test
    fun json_round_trip_merge_deduplicates_and_creates_backup() = runBlocking {
        val file = Files.createTempFile("attention-state", ".preferences_pb").toFile()
        val repository = DataStoreAttentionStateRepository(PreferenceDataStoreFactory.create { file })
        val today = LocalDate.of(2026, 10, 9).toString()
        repository.update { AttentionState(settings = StoredSettings(dailyCapacityMinutes = 300)).addTarget("学习").let { state ->
            state.addTimeEntry(today, 25, state.targets.single().id)
        } }
        val backup = repository.exportJson()
        repository.importJson(backup, clearExisting = false)

        val merged = repository.state.first()
        assertEquals(1, merged.targets.size)
        assertEquals(1, merged.timeEntries.size)
        assertEquals(300, merged.settings.dailyCapacityMinutes)
        assertNotNull(repository.lastImportBackup())
    }
}
