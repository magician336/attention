package com.attention.app

import com.attention.domain.LaunchDestination
import com.attention.domain.resolveLaunchDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceNavigationTest {
    @Test
    fun fixed_launch_destinations_are_opened_directly() {
        assertEquals(
            LaunchDestination.TARGETS,
            resolveLaunchDestination(LaunchDestination.TARGETS, LaunchDestination.STATISTICS),
        )
        assertEquals(
            LaunchDestination.ALL_DATES,
            resolveLaunchDestination(LaunchDestination.ALL_DATES, LaunchDestination.TODAY),
        )
    }

    @Test
    fun last_opened_restores_only_an_actual_page_and_self_reference_falls_back() {
        assertEquals(
            LaunchDestination.STATISTICS,
            resolveLaunchDestination(LaunchDestination.LAST_OPENED, LaunchDestination.STATISTICS),
        )
        assertEquals(
            LaunchDestination.TODAY,
            resolveLaunchDestination(LaunchDestination.LAST_OPENED, LaunchDestination.LAST_OPENED),
        )
    }

    @Test
    fun external_schedule_request_takes_precedence_over_saved_launch_page() {
        assertEquals(
            LaunchDestination.ALL_DATES,
            initialLaunchDestination(true, LaunchDestination.STATISTICS, LaunchDestination.TARGETS),
        )
        assertEquals(
            LaunchDestination.STATISTICS,
            initialLaunchDestination(false, LaunchDestination.STATISTICS, LaunchDestination.TARGETS),
        )
    }

    @Test
    fun last_opened_is_not_written_until_persisted_settings_are_loaded() {
        assertEquals(false, shouldPersistLastOpened(false, false))
        assertEquals(false, shouldPersistLastOpened(true, true))
        assertEquals(true, shouldPersistLastOpened(true, false))
    }
}
