package com.attention.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.attention.domain.PlannerSettings
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    val settings: Flow<PlannerSettings>
    suspend fun setPlanningDayBoundary(minutes: Int)
    suspend fun setWeekStartDay(day: DayOfWeek)
    suspend fun setDailyCapacity(minutes: Int?)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<PlannerSettings> = dataStore.data.map { preferences ->
        PlannerSettings(
            planningDayBoundaryMinutes = preferences[Keys.PLANNING_DAY_BOUNDARY]
                ?: PlannerSettings.DEFAULT_BOUNDARY_MINUTES,
            weekStartDay = preferences[Keys.WEEK_START_DAY]
                ?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                ?: DayOfWeek.MONDAY,
            dailyCapacityMinutes = preferences[Keys.DAILY_CAPACITY_SET]?.let {
                if (it) preferences[Keys.DAILY_CAPACITY] else null
            },
        )
    }

    override suspend fun setPlanningDayBoundary(minutes: Int) {
        require(minutes in 0 until PlannerSettings.MINUTES_PER_DAY)
        dataStore.edit { it[Keys.PLANNING_DAY_BOUNDARY] = minutes }
    }

    override suspend fun setWeekStartDay(day: DayOfWeek) {
        dataStore.edit { it[Keys.WEEK_START_DAY] = day.name }
    }

    override suspend fun setDailyCapacity(minutes: Int?) {
        require(minutes == null || minutes > 0)
        dataStore.edit {
            it[Keys.DAILY_CAPACITY_SET] = minutes != null
            if (minutes == null) it.remove(Keys.DAILY_CAPACITY) else it[Keys.DAILY_CAPACITY] = minutes
        }
    }

    private object Keys {
        val PLANNING_DAY_BOUNDARY = intPreferencesKey("planning_day_boundary_minutes")
        val WEEK_START_DAY = stringPreferencesKey("week_start_day")
        val DAILY_CAPACITY_SET = booleanPreferencesKey("daily_capacity_set")
        val DAILY_CAPACITY = intPreferencesKey("daily_capacity_minutes")
    }
}
