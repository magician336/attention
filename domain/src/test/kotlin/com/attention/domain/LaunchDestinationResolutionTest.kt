package com.attention.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchDestinationResolutionTest {
    @Test
    fun a_fixed_entry_opens_itself_whatever_the_history_is() {
        // 固定入口直达：日程、目标树、统计以及仍然保留的旧入口都按自身打开
        assertEquals(
            LaunchDestination.AGENDA,
            resolveLaunchDestination(LaunchDestination.AGENDA, LaunchDestination.STATISTICS),
        )
        assertEquals(
            LaunchDestination.TARGETS,
            resolveLaunchDestination(LaunchDestination.TARGETS, LaunchDestination.LAST_OPENED),
        )
        assertEquals(
            LaunchDestination.ALL_DATES,
            resolveLaunchDestination(LaunchDestination.ALL_DATES, LaunchDestination.TODAY),
        )
    }

    @Test
    fun last_opened_restores_only_the_schedule_the_targets_tree_or_the_statistics() {
        // 「上次打开」只解析出日程、目标树、统计三者之一
        assertEquals(
            LaunchDestination.AGENDA,
            resolveLaunchDestination(LaunchDestination.LAST_OPENED, LaunchDestination.AGENDA),
        )
        assertEquals(
            LaunchDestination.TARGETS,
            resolveLaunchDestination(LaunchDestination.LAST_OPENED, LaunchDestination.TARGETS),
        )
        assertEquals(
            LaunchDestination.STATISTICS,
            resolveLaunchDestination(LaunchDestination.LAST_OPENED, LaunchDestination.STATISTICS),
        )
    }

    @Test
    fun a_self_referencing_or_unusable_history_falls_back_to_the_schedule() {
        // 解析到自身、旧入口值与非法历史都回退到日程
        assertEquals(
            LaunchDestination.AGENDA,
            resolveLaunchDestination(LaunchDestination.LAST_OPENED, LaunchDestination.LAST_OPENED),
        )
        assertEquals(
            LaunchDestination.AGENDA,
            resolveLaunchDestination(LaunchDestination.LAST_OPENED, LaunchDestination.TODAY),
        )
        assertEquals(
            LaunchDestination.AGENDA,
            resolveLaunchDestination(LaunchDestination.LAST_OPENED, LaunchDestination.ALL_DATES),
        )
    }

    @Test
    fun legacy_entries_map_onto_the_schedule_entry_semantics() {
        // 旧版本持久化的入口值读取后等价于日程入口，其余值保持自身
        assertEquals(LaunchDestination.AGENDA, LaunchDestination.TODAY.asAgendaEntry())
        assertEquals(LaunchDestination.AGENDA, LaunchDestination.ALL_DATES.asAgendaEntry())
        assertEquals(LaunchDestination.AGENDA, LaunchDestination.AGENDA.asAgendaEntry())
        assertEquals(LaunchDestination.TARGETS, LaunchDestination.TARGETS.asAgendaEntry())
        assertEquals(LaunchDestination.STATISTICS, LaunchDestination.STATISTICS.asAgendaEntry())
        assertEquals(LaunchDestination.LAST_OPENED, LaunchDestination.LAST_OPENED.asAgendaEntry())
    }

    @Test
    fun persisted_legacy_names_read_back_as_the_schedule_entry() {
        // DataStore 与备份 JSON 里的旧名称读取后等价于日程入口
        assertEquals(LaunchDestination.AGENDA, launchDestinationFromPersistedName("TODAY"))
        assertEquals(LaunchDestination.AGENDA, launchDestinationFromPersistedName("ALL_DATES"))
        assertEquals(LaunchDestination.TARGETS, launchDestinationFromPersistedName("TARGETS"))
        assertEquals(LaunchDestination.AGENDA, launchDestinationFromPersistedName("AGENDA"))
        assertEquals(LaunchDestination.AGENDA, launchDestinationFromPersistedName("SCHEDULE"))
        assertEquals(LaunchDestination.AGENDA, launchDestinationFromPersistedName(null))
    }
}
