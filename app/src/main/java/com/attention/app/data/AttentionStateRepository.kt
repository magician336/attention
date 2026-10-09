package com.attention.app.data

import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
import com.attention.domain.StoredSettings
import kotlinx.coroutines.flow.Flow

/** Runtime data boundary: Room business data, DataStore settings and versioned backups. */
interface AttentionStateRepository {
    val state: Flow<AttentionState>

    suspend fun updateBusiness(transform: (AttentionState) -> AttentionState)
    suspend fun setPlannerSettings(settings: StoredSettings)
    suspend fun setPlanningDayBoundaryMinutes(minutes: Int)
    suspend fun setWeekStartDay(day: Int)
    suspend fun setDailyCapacityMinutes(minutes: Int?)
    suspend fun setLaunchDestination(destination: LaunchDestination)
    suspend fun setLastOpenedDestination(destination: LaunchDestination)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setNotificationsEnabled(enabled: Boolean)

    /** Explicit full-state replacement used by the backup coordinator. */
    suspend fun replace(state: AttentionState)

    suspend fun exportJson(): String
    suspend fun importJson(encoded: String, clearExisting: Boolean): AttentionState
    suspend fun lastImportBackup(): String?
}
