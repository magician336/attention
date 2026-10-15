package com.attention.domain

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlannerSettingsTest {
    @Test
    fun defaults_match_the_first_open_workbench_contract() {
        val settings = PlannerSettings()

        assertEquals(180, settings.planningDayBoundaryMinutes)
        assertEquals("03:00", settings.planningDayBoundary)
        assertEquals(DayOfWeek.MONDAY, settings.weekStartDay)
        assertNull(settings.dailyCapacityMinutes)
    }

    @Test
    fun capacity_can_be_set_and_cleared() {
        val configured = PlannerSettings(dailyCapacityMinutes = 480)
        val cleared = configured.copy(dailyCapacityMinutes = null)

        assertEquals(480, configured.dailyCapacityMinutes)
        assertNull(cleared.dailyCapacityMinutes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun boundary_cannot_escape_a_day() {
        PlannerSettings(planningDayBoundaryMinutes = 24 * 60)
    }

    @Test
    fun the_validation_predicates_agree_with_the_constructor() {
        // 设置页为了给出可读的报错会先问一次「这个输入合法吗」，因此这两个判定必须与构造函数的
        // require 是同一条规则，而不是页面自己复述的一份。
        listOf(-1, 0, 180, 1439, 1440, 24 * 60).forEach { minutes ->
            val accepted = runCatching { PlannerSettings(planningDayBoundaryMinutes = minutes) }.isSuccess
            assertEquals(
                "界线 $minutes 的判定必须与构造一致",
                accepted,
                PlannerSettings.isValidPlanningDayBoundary(minutes),
            )
        }
        listOf<Int?>(null, 1, 480).forEach { minutes ->
            val accepted = runCatching { PlannerSettings(dailyCapacityMinutes = minutes) }.isSuccess
            assertEquals(
                "容量 $minutes 的判定必须与构造一致",
                accepted,
                PlannerSettings.isValidDailyCapacity(minutes),
            )
        }
    }
}
