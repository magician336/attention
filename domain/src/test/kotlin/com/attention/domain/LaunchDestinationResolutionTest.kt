package com.attention.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun an_external_schedule_request_wins_over_every_saved_entry() {
        // 外部日程请求优先级最高：保存的入口与上次打开都不拦截它
        listOf(
            LaunchDestination.AGENDA,
            LaunchDestination.TARGETS,
            LaunchDestination.STATISTICS,
            LaunchDestination.LAST_OPENED,
            LaunchDestination.TODAY,
        ).forEach { requested ->
            assertEquals(
                "外部请求必须压过保存的入口 $requested",
                LaunchDestination.AGENDA,
                resolveInitialLaunchDestination(true, requested, LaunchDestination.STATISTICS),
            )
        }
    }

    @Test
    fun the_saved_entry_lands_on_its_own_page_when_there_is_no_external_request() {
        // 日程、目标树、统计、上次打开四种启动入口各自直达对应页面
        assertEquals(
            LaunchDestination.AGENDA,
            resolveInitialLaunchDestination(false, LaunchDestination.AGENDA, LaunchDestination.STATISTICS),
        )
        assertEquals(
            LaunchDestination.TARGETS,
            resolveInitialLaunchDestination(false, LaunchDestination.TARGETS, LaunchDestination.STATISTICS),
        )
        assertEquals(
            LaunchDestination.STATISTICS,
            resolveInitialLaunchDestination(false, LaunchDestination.STATISTICS, LaunchDestination.TARGETS),
        )
        assertEquals(
            LaunchDestination.TARGETS,
            resolveInitialLaunchDestination(false, LaunchDestination.LAST_OPENED, LaunchDestination.TARGETS),
        )
    }

    @Test
    fun no_usable_history_falls_back_to_the_schedule_of_the_current_planning_day() {
        // 没有有效历史时回退到日程，组合根据此打开当前规划日的日程
        assertEquals(
            LaunchDestination.AGENDA,
            resolveInitialLaunchDestination(false, LaunchDestination.LAST_OPENED, LaunchDestination.LAST_OPENED),
        )
        assertEquals(
            LaunchDestination.AGENDA,
            resolveInitialLaunchDestination(false, LaunchDestination.LAST_OPENED, LaunchDestination.TODAY),
        )
        // 旧入口值仍按旧页面直达，再由领域兼容映射落到同一页日程
        assertEquals(
            LaunchDestination.AGENDA,
            resolveInitialLaunchDestination(false, LaunchDestination.ALL_DATES, LaunchDestination.ALL_DATES).asAgendaEntry(),
        )
    }

    @Test
    fun an_external_request_locates_the_planning_day_it_names() {
        val state = AttentionState().addSchedule(
            ScheduleEntry(id = "wanted", planningDate = "2026-10-20", title = "被点名的条目"),
        )
        assertEquals(
            LocalDate.of(2026, 10, 20),
            externalSchedulePlanningDate(state, "2026-10-20", "wanted"),
        )
        // 请求带的规划日优先于条目自身的规划日
        assertEquals(
            LocalDate.of(2026, 10, 18),
            externalSchedulePlanningDate(state, "2026-10-18", "wanted"),
        )
    }

    @Test
    fun an_external_request_falls_back_to_the_entries_own_planning_day() {
        val state = AttentionState().addSchedule(
            ScheduleEntry(id = "wanted", planningDate = "2026-10-20", title = "被点名的条目"),
        )
        // 只有条目 id（通知没带规划日）时定位到条目所在的日子，而不是当前规划日
        assertEquals(LocalDate.of(2026, 10, 20), externalSchedulePlanningDate(state, null, "wanted"))
        // 规划日无法解析时同样退回条目自身的规划日
        assertEquals(LocalDate.of(2026, 10, 20), externalSchedulePlanningDate(state, "不是日期", "wanted"))
        // 没有任何可定位的目标时没有定位日，由调用方回退到当前规划日
        assertEquals(null, externalSchedulePlanningDate(state, null, null))
        assertEquals(null, externalSchedulePlanningDate(state, "不是日期", "不存在"))
    }

    @Test
    fun every_value_resolves_to_a_top_level_workspace_and_falls_back_to_the_schedule() {
        // 顶级工作区只有日程、目标树、统计三个。任何不是这三者的值（旧入口、LAST_OPENED）
        // 都必须解析到日程，否则组合根就会落到一个渲染不出任何页面的分支上。
        LaunchDestination.entries.forEach { destination ->
            assertTrue(
                "解析结果必须是顶级工作区：$destination",
                destination.toTopLevelWorkspace() in topLevelWorkspaces,
            )
        }
        assertEquals(LaunchDestination.AGENDA, LaunchDestination.LAST_OPENED.toTopLevelWorkspace())
        assertEquals(LaunchDestination.AGENDA, LaunchDestination.TODAY.toTopLevelWorkspace())
        assertEquals(LaunchDestination.AGENDA, LaunchDestination.ALL_DATES.toTopLevelWorkspace())
        assertEquals(LaunchDestination.AGENDA, LaunchDestination.AGENDA.toTopLevelWorkspace())
        assertEquals(LaunchDestination.TARGETS, LaunchDestination.TARGETS.toTopLevelWorkspace())
        assertEquals(LaunchDestination.STATISTICS, LaunchDestination.STATISTICS.toTopLevelWorkspace())
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
