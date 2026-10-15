package com.attention.app

import com.attention.domain.LaunchDestination
import com.attention.domain.asAgendaEntry
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
            LaunchDestination.AGENDA,
            resolveLaunchDestination(LaunchDestination.LAST_OPENED, LaunchDestination.LAST_OPENED),
        )
    }

    @Test
    fun the_agenda_entry_lands_on_the_merged_agenda_page() {
        // 底部导航收敛为三项之后，「日程」就是它的真实入口：旧入口值仍然等价于日程
        assertEquals(
            LaunchDestination.AGENDA,
            initialLaunchDestination(false, LaunchDestination.AGENDA, LaunchDestination.TARGETS),
        )
        assertEquals(
            LaunchDestination.AGENDA,
            LaunchDestination.TODAY.asAgendaEntry(),
        )
        assertEquals(
            LaunchDestination.AGENDA,
            LaunchDestination.ALL_DATES.asAgendaEntry(),
        )
    }

    @Test
    fun external_schedule_request_takes_precedence_over_saved_launch_page() {
        // 外部日程请求直接打开合并后的日程页（深链优先级由 #174 继续细化）
        assertEquals(
            LaunchDestination.AGENDA,
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
