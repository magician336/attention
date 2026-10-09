package com.attention.app.data

import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.SettingsStore
import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
import com.attention.domain.StoredSettings
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Production repository for the split storage model.
 *
 * Room owns business collections and timer state. Preferences DataStore owns
 * planner and navigation settings. The JSON methods are a transitional facade
 * for the existing UI; the versioned backup adapter is delivered by Issue #29.
 */
class RoomAttentionStateRepository(
    private val business: RoomBusinessDataRepository,
    private val settings: SettingsStore,
    private val json: Json = defaultJson,
) : AttentionStateRepository {
    private val mutex = Mutex()
    private val importBackup = AtomicReference<String?>(null)

    override val state: Flow<AttentionState> = AttentionStateReader(business, settings).state

    override suspend fun updateBusiness(transform: (AttentionState) -> AttentionState) {
        mutex.withLock {
            val plannerSettings = settings.settings.first().toStoredSettings()
            business.update { current ->
                transform(current.copy(settings = plannerSettings))
            }
        }
    }

    override suspend fun update(transform: (AttentionState) -> AttentionState) {
        mutex.withLock {
            val current = state.first()
            val next = transform(current)
            replaceWithRollback(current, next)
        }
    }

    override suspend fun setPlannerSettings(settings: StoredSettings) {
        this.settings.setPlannerSettings(settings)
    }

    override suspend fun setPlanningDayBoundaryMinutes(minutes: Int) {
        this.settings.setPlanningDayBoundaryMinutes(minutes)
    }

    override suspend fun setWeekStartDay(day: Int) {
        this.settings.setWeekStartDay(day)
    }

    override suspend fun setDailyCapacityMinutes(minutes: Int?) {
        this.settings.setDailyCapacityMinutes(minutes)
    }

    override suspend fun setLaunchDestination(destination: LaunchDestination) {
        this.settings.setLaunchDestination(destination)
    }

    override suspend fun setLastOpenedDestination(destination: LaunchDestination) {
        this.settings.setLastOpenedDestination(destination)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        this.settings.setOnboardingCompleted(completed)
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        this.settings.setNotificationsEnabled(enabled)
    }

    override suspend fun replace(state: AttentionState) {
        mutex.withLock {
            replaceWithRollback(this@RoomAttentionStateRepository.state.first(), state)
        }
    }

    override suspend fun exportJson(): String = json.encodeToString(state.first())

    override suspend fun importJson(encoded: String, clearExisting: Boolean): AttentionState {
        val incoming = json.decodeFromString<AttentionState>(encoded)
        return mutex.withLock {
            val current = state.first()
            importBackup.set(json.encodeToString(current))
            val result = if (clearExisting) incoming else merge(current, incoming)
            replaceWithRollback(current, result)
            result
        }
    }

    // Issue #29 moves this backup to the versioned backup coordinator.
    override suspend fun lastImportBackup(): String? = importBackup.get()

    private suspend fun replaceWithRollback(current: AttentionState, next: AttentionState) {
        try {
            business.replace(next)
            settings.setSnapshot(next.toSettingsSnapshot())
        } catch (error: Throwable) {
            runCatching { business.replace(current) }
                .onFailure(error::addSuppressed)
            throw error
        }
    }

    private fun AttentionState.toSettingsSnapshot(): SettingsSnapshot = SettingsSnapshot(
        planningDayBoundaryMinutes = settings.planningDayBoundaryMinutes,
        weekStartDay = settings.weekStartDay,
        dailyCapacityMinutes = settings.dailyCapacityMinutes,
        launchDestination = launchDestination,
        lastOpenedDestination = lastOpenedDestination,
        onboardingCompleted = onboardingCompleted,
        notificationsEnabled = notificationsEnabled,
    )

    private companion object {
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
                futureGoalRules = mergeById(current.futureGoalRules, incoming.futureGoalRules) { it.id },
                periodSnapshots = mergeById(current.periodSnapshots, incoming.periodSnapshots) { it.id },
                targetMoves = mergeById(current.targetMoves, incoming.targetMoves) { it.id },
                milestones = mergeById(current.milestones, incoming.milestones) { it.id },
                experience = maxOf(current.experience, incoming.experience),
            )
        }
    }
}
