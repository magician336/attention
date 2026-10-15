package com.attention.domain

import java.time.DayOfWeek

/** Small, serializable settings used by the planning-day calculations. */
data class PlannerSettings(
    val planningDayBoundaryMinutes: Int = DEFAULT_BOUNDARY_MINUTES,
    val weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
    val dailyCapacityMinutes: Int? = null,
) {
    init {
        require(isValidPlanningDayBoundary(planningDayBoundaryMinutes)) {
            "planning day boundary must be between 00:00 and 23:59"
        }
        require(isValidDailyCapacity(dailyCapacityMinutes)) {
            "daily capacity must be positive when set"
        }
    }

    val planningDayBoundary: String
        get() = formatMinuteOfDay(planningDayBoundaryMinutes)

    companion object {
        const val DEFAULT_BOUNDARY_MINUTES = 3 * 60
        const val MINUTES_PER_DAY = 24 * 60

        /**
         * 规划日界线是否落在这之内。上面两条 [require] 与设置页输入框的即时提示是同一条规则：
         * 页面要先问一次「这个输入合法吗」才能给出可读的报错，因此判定必须留在这里，
         * 由调用方复用，而不是页面复述一份边界。
         */
        fun isValidPlanningDayBoundary(minutes: Int): Boolean = minutes in 0 until MINUTES_PER_DAY

        /** 每日容量是否合法；null 表示不设上限，因此合法。 */
        fun isValidDailyCapacity(minutes: Int?): Boolean = minutes == null || minutes > 0
    }
}
