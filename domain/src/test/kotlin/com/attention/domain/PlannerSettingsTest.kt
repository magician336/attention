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
}
