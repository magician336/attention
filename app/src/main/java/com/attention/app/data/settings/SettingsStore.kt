package com.attention.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.attention.domain.LaunchDestination
import com.attention.domain.PlannerSettings
import com.attention.domain.StoredSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Settings persisted independently from Room business data. */
data class SettingsSnapshot(
    val planningDayBoundaryMinutes: Int = PlannerSettings.DEFAULT_BOUNDARY_MINUTES,
    val weekStartDay: Int = DEFAULT_WEEK_START_DAY,
    val dailyCapacityMinutes: Int? = null,
    val launchDestination: LaunchDestination = LaunchDestination.TODAY,
    val lastOpenedDestination: LaunchDestination = LaunchDestination.TODAY,
    val onboardingCompleted: Boolean = false,
    val notificationsEnabled: Boolean = true,
) {
    init {
        require(planningDayBoundaryMinutes in 0 until PlannerSettings.MINUTES_PER_DAY) {
            "planning day boundary must be between 00:00 and 23:59"
        }
        require(weekStartDay in 1..7) { "week start day must be between 1 and 7" }
        require(dailyCapacityMinutes == null || dailyCapacityMinutes > 0) {
            "daily capacity must be positive when set"
        }
    }

    fun toStoredSettings(): StoredSettings = StoredSettings(
        planningDayBoundaryMinutes = planningDayBoundaryMinutes,
        weekStartDay = weekStartDay,
        dailyCapacityMinutes = dailyCapacityMinutes,
    )

    companion object {
        const val DEFAULT_WEEK_START_DAY = 1
    }
}

interface SettingsStore {
    val settings: Flow<SettingsSnapshot>

    suspend fun setPlannerSettings(settings: StoredSettings)
    suspend fun setPlanningDayBoundaryMinutes(minutes: Int)
    suspend fun setWeekStartDay(day: Int)
    suspend fun setDailyCapacityMinutes(minutes: Int?)
    suspend fun setLaunchDestination(destination: LaunchDestination)
    suspend fun setLastOpenedDestination(destination: LaunchDestination)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun reset()
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "attention_settings")

class DataStoreSettingsStore(
    private val dataStore: DataStore<Preferences>,
) : SettingsStore {
    override val settings: Flow<SettingsSnapshot> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.toSettingsSnapshot() }

    override suspend fun setPlannerSettings(settings: StoredSettings) {
        validatePlannerSettings(settings)
        val dailyCapacityMinutes = settings.dailyCapacityMinutes
        dataStore.edit {
            it[Keys.PLANNING_DAY_BOUNDARY_MINUTES] = settings.planningDayBoundaryMinutes
            it[Keys.WEEK_START_DAY] = settings.weekStartDay
            if (dailyCapacityMinutes == null) it.remove(Keys.DAILY_CAPACITY_MINUTES)
            else it[Keys.DAILY_CAPACITY_MINUTES] = dailyCapacityMinutes
        }
    }

    override suspend fun setPlanningDayBoundaryMinutes(minutes: Int) {
        require(minutes in 0 until PlannerSettings.MINUTES_PER_DAY) {
            "planning day boundary must be between 00:00 and 23:59"
        }
        dataStore.edit { it[Keys.PLANNING_DAY_BOUNDARY_MINUTES] = minutes }
    }

    override suspend fun setWeekStartDay(day: Int) {
        require(day in 1..7) { "week start day must be between 1 and 7" }
        dataStore.edit { it[Keys.WEEK_START_DAY] = day }
    }

    override suspend fun setDailyCapacityMinutes(minutes: Int?) {
        require(minutes == null || minutes > 0) { "daily capacity must be positive when set" }
        dataStore.edit { preferences ->
            if (minutes == null) preferences.remove(Keys.DAILY_CAPACITY_MINUTES)
            else preferences[Keys.DAILY_CAPACITY_MINUTES] = minutes
        }
    }

    override suspend fun setLaunchDestination(destination: LaunchDestination) {
        dataStore.edit { it[Keys.LAUNCH_DESTINATION] = destination.name }
    }

    override suspend fun setLastOpenedDestination(destination: LaunchDestination) {
        dataStore.edit { it[Keys.LAST_OPENED_DESTINATION] = destination.name }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    override suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private fun validatePlannerSettings(settings: StoredSettings) {
        val dailyCapacityMinutes = settings.dailyCapacityMinutes
        require(settings.planningDayBoundaryMinutes in 0 until PlannerSettings.MINUTES_PER_DAY) {
            "planning day boundary must be between 00:00 and 23:59"
        }
        require(settings.weekStartDay in 1..7) { "week start day must be between 1 and 7" }
        require(dailyCapacityMinutes == null || dailyCapacityMinutes > 0) {
            "daily capacity must be positive when set"
        }
    }
}

internal fun Context.createSettingsStore(): SettingsStore = DataStoreSettingsStore(settingsDataStore)

private object Keys {
    val PLANNING_DAY_BOUNDARY_MINUTES = intPreferencesKey("planning_day_boundary_minutes")
    val WEEK_START_DAY = intPreferencesKey("week_start_day")
    val DAILY_CAPACITY_MINUTES = intPreferencesKey("daily_capacity_minutes")
    val LAUNCH_DESTINATION = stringPreferencesKey("launch_destination")
    val LAST_OPENED_DESTINATION = stringPreferencesKey("last_opened_destination")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
}

private fun Preferences.toSettingsSnapshot(): SettingsSnapshot = SettingsSnapshot(
    planningDayBoundaryMinutes = get(Keys.PLANNING_DAY_BOUNDARY_MINUTES)
        ?.takeIf { it in 0 until PlannerSettings.MINUTES_PER_DAY }
        ?: PlannerSettings.DEFAULT_BOUNDARY_MINUTES,
    weekStartDay = get(Keys.WEEK_START_DAY)?.takeIf { it in 1..7 }
        ?: SettingsSnapshot.DEFAULT_WEEK_START_DAY,
    dailyCapacityMinutes = get(Keys.DAILY_CAPACITY_MINUTES)?.takeIf { it > 0 },
    launchDestination = get(Keys.LAUNCH_DESTINATION).toLaunchDestination(),
    lastOpenedDestination = get(Keys.LAST_OPENED_DESTINATION).toLaunchDestination(),
    onboardingCompleted = get(Keys.ONBOARDING_COMPLETED) ?: false,
    notificationsEnabled = get(Keys.NOTIFICATIONS_ENABLED) ?: true,
)

private fun String?.toLaunchDestination(): LaunchDestination = runCatching {
    this?.let(LaunchDestination::valueOf) ?: LaunchDestination.TODAY
}.getOrDefault(LaunchDestination.TODAY)
