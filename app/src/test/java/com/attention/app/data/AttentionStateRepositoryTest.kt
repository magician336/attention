package com.attention.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.attention.domain.AttentionState
import com.attention.domain.StoredSettings
import com.attention.domain.addTarget
import com.attention.domain.addTimeEntry
import com.attention.domain.reorderTarget
import com.attention.domain.setTargetExpanded
import com.attention.domain.targetChildren
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AttentionStateRepositoryTest {
    @Test
    fun settings_survive_a_new_repository_instance() = runBlocking {
        val file = Files.createTempFile("attention-state-settings", ".preferences_pb").toFile()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstRepository = DataStoreAttentionStateRepository(
            PreferenceDataStoreFactory.create(scope = firstScope) { file },
        )

        firstRepository.update {
            it.copy(settings = StoredSettings(
                planningDayBoundaryMinutes = 90,
                weekStartDay = 7,
                dailyCapacityMinutes = 420,
            ))
        }
        firstScope.cancel()
        firstScope.coroutineContext[kotlinx.coroutines.Job]?.join()

        val afterRestart = DataStoreAttentionStateRepository(
            PreferenceDataStoreFactory.create { file },
        ).state.first()

        assertEquals(90, afterRestart.settings.planningDayBoundaryMinutes)
        assertEquals(7, afterRestart.settings.weekStartDay)
        assertEquals(420, afterRestart.settings.dailyCapacityMinutes)
    }

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

    @Test
    fun json_round_trip_preserves_nested_target_tree_order_and_expansion() = runBlocking {
        val file = Files.createTempFile("attention-target-tree", ".preferences_pb").toFile()
        val repository = DataStoreAttentionStateRepository(PreferenceDataStoreFactory.create { file })
        val initial = AttentionState()
            .addTarget("阅读")
            .let { it.addTarget("中国文学", it.targets.single().id) }
            .let { it.addTarget("红楼梦", it.targets.single { target -> target.title == "中国文学" }.id) }
        val rootId = initial.targets.single { it.title == "阅读" }.id
        val childId = initial.targets.single { it.title == "中国文学" }.id
        val prepared = initial
            .setTargetExpanded(rootId, false)
            .reorderTarget(childId, 0)

        repository.replace(prepared)
        val encoded = repository.exportJson()
        repository.importJson(encoded, clearExisting = true)

        val restored = repository.state.first()
        assertEquals(prepared.targets, restored.targets)
        assertEquals(listOf("中国文学"), restored.targetChildren(rootId).map { it.title })
        assertEquals(rootId, restored.targets.single { it.title == "阅读" }.id)
        assertEquals(false, restored.targets.single { it.id == rootId }.expanded)
        assertEquals(childId, restored.targets.single { it.title == "中国文学" }.id)
    }
}
