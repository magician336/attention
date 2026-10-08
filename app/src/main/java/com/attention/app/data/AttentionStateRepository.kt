package com.attention.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.attention.domain.AttentionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

interface AttentionStateRepository {
    val state: Flow<AttentionState>
    suspend fun update(transform: (AttentionState) -> AttentionState)
    suspend fun replace(state: AttentionState)
    suspend fun exportJson(): String
    suspend fun importJson(json: String, clearExisting: Boolean): AttentionState
}

class DataStoreAttentionStateRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = defaultJson,
) : AttentionStateRepository {
    private val mutex = Mutex()

    override val state: Flow<AttentionState> = dataStore.data
        .catch { error ->
            if (error is IOException || error is SerializationException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            preferences[STATE_KEY]?.let { encoded ->
                runCatching { json.decodeFromString<AttentionState>(encoded) }.getOrElse { AttentionState() }
            } ?: AttentionState()
        }

    override suspend fun update(transform: (AttentionState) -> AttentionState) {
        mutex.withLock {
            dataStore.edit { preferences ->
                val current = preferences[STATE_KEY]?.let { encoded ->
                    runCatching { json.decodeFromString<AttentionState>(encoded) }.getOrDefault(AttentionState())
                } ?: AttentionState()
                preferences[STATE_KEY] = json.encodeToString(transform(current))
            }
        }
    }

    override suspend fun replace(state: AttentionState) {
        mutex.withLock {
            dataStore.edit { it[STATE_KEY] = json.encodeToString(state) }
        }
    }

    override suspend fun exportJson(): String {
        return stateFirst().let(json::encodeToString)
    }

    override suspend fun importJson(encoded: String, clearExisting: Boolean): AttentionState {
        val incoming = json.decodeFromString<AttentionState>(encoded)
        if (clearExisting) {
            replace(incoming)
            return incoming
        }
        var result = AttentionState()
        mutex.withLock {
            dataStore.edit { preferences ->
                val current = preferences[STATE_KEY]?.let { value ->
                    runCatching { json.decodeFromString<AttentionState>(value) }.getOrDefault(AttentionState())
                } ?: AttentionState()
                result = merge(current, incoming)
                preferences[STATE_KEY] = json.encodeToString(result)
            }
        }
        return result
    }

    private suspend fun stateFirst(): AttentionState = state.first()

    private companion object {
        val STATE_KEY = stringPreferencesKey("attention_state_json")
        val defaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun merge(current: AttentionState, incoming: AttentionState): AttentionState {
            fun <T> mergeById(existing: List<T>, added: List<T>, id: (T) -> String): List<T> {
                val result = existing.toMutableList()
                val ids = existing.mapTo(mutableSetOf(), id)
                added.forEach { if (ids.add(id(it))) result += it }
                return result
            }
            return current.copy(
                settings = incoming.settings,
                targets = mergeById(current.targets, incoming.targets) { it.id },
                goalStages = mergeById(current.goalStages, incoming.goalStages) { it.id },
                timeEntries = mergeById(current.timeEntries, incoming.timeEntries) { it.id },
                schedules = mergeById(current.schedules, incoming.schedules) { it.id },
                recurrenceRules = mergeById(current.recurrenceRules, incoming.recurrenceRules) { it.id },
                migrations = mergeById(current.migrations, incoming.migrations) { it.id },
                milestones = mergeById(current.milestones, incoming.milestones) { it.id },
                experience = maxOf(current.experience, incoming.experience),
            )
        }
    }
}

private fun emptyPreferences(): Preferences = androidx.datastore.preferences.core.emptyPreferences()
