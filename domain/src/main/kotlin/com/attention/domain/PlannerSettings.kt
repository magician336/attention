package com.attention.domain

import java.time.DayOfWeek

/** Small, serializable settings used by the planning-day calculations. */
data class PlannerSettings(
    val planningDayBoundaryMinutes: Int = DEFAULT_BOUNDARY_MINUTES,
    val weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
    val dailyCapacityMinutes: Int? = null,
) {
    init {
        require(planningDayBoundaryMinutes in 0 until MINUTES_PER_DAY) {
            "planning day boundary must be between 00:00 and 23:59"
        }
        require(dailyCapacityMinutes == null || dailyCapacityMinutes > 0) {
            "daily capacity must be positive when set"
        }
    }

    val planningDayBoundary: String
        get() = "%02d:%02d".format(planningDayBoundaryMinutes / 60, planningDayBoundaryMinutes % 60)

    companion object {
        const val DEFAULT_BOUNDARY_MINUTES = 3 * 60
        const val MINUTES_PER_DAY = 24 * 60
    }
}
