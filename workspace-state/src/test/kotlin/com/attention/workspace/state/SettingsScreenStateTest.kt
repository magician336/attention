package com.attention.workspace.state

import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
import com.attention.domain.StoredSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 设置页投影的纯 Kotlin 测试：页面只读这份状态，不读完整领域状态，也不读系统时钟。
 */
class SettingsScreenStateTest {

    private val now: Instant = Instant.parse("2026-03-05T10:00:00Z")
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun launch_destination_options_are_the_four_top_level_workspaces() {
        // 启动入口对用户统一呈现为日程、目标树、统计、上次打开；今天与所有日期不再是入口
        val screenState = settingsScreenState(
            state = AttentionState(),
            now = now,
            zone = zone,
        )

        assertEquals(
            listOf(
                LaunchDestination.AGENDA to "日程",
                LaunchDestination.TARGETS to "目标树",
                LaunchDestination.STATISTICS to "统计",
                LaunchDestination.LAST_OPENED to "上次打开",
            ),
            screenState.options.map { it.destination to it.label },
        )
    }

    @Test
    fun a_legacy_persisted_entry_is_shown_as_the_agenda_option() {
        // 旧版本持久化的今天 / 所有日期都按领域兼容映射显示为「日程（当前）」
        listOf(LaunchDestination.TODAY, LaunchDestination.ALL_DATES).forEach { legacy ->
            val screenState = settingsScreenState(
                state = AttentionState(launchDestination = legacy),
                now = now,
                zone = zone,
            )

            assertEquals(
                listOf(LaunchDestination.AGENDA),
                screenState.options.filter { it.selected }.map { it.destination },
            )
        }
    }

    @Test
    fun selected_launch_destination_is_marked_and_the_others_are_not() {
        // 只有当前入口被标记为选中，其余入口都是可选项
        val screenState = settingsScreenState(
            state = AttentionState(launchDestination = LaunchDestination.TARGETS),
            now = now,
            zone = zone,
        )

        assertEquals(LaunchDestination.TARGETS, screenState.selectedDestination)
        assertEquals(
            listOf(LaunchDestination.TARGETS),
            screenState.options.filter { it.selected }.map { it.destination },
        )
    }

    @Test
    fun planning_day_boundary_capacity_and_week_start_come_from_the_stored_settings() {
        // 规划日界线、周起始日与每日容量是设置页要编辑的三项，值来自持久化设置
        val screenState = settingsScreenState(
            state = AttentionState(
                settings = StoredSettings(
                    planningDayBoundaryMinutes = 90,
                    weekStartDay = 3,
                    dailyCapacityMinutes = 240,
                ),
            ),
            now = now,
            zone = zone,
        )

        assertEquals(90, screenState.planningDayBoundaryMinutes)
        assertEquals(240, screenState.dailyCapacityMinutes)
        assertEquals(
            listOf(3),
            screenState.weekDayOptions.filter { it.selected }.map { it.day },
        )
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7),
            screenState.weekDayOptions.map { it.day },
        )
        assertEquals("周三", screenState.weekDayOptions.first { it.selected }.label)
    }

    @Test
    fun an_unset_daily_capacity_is_projected_as_null() {
        // 每日容量是可选项：没有设置时页面拿到的是空值而不是 0
        val screenState = settingsScreenState(AttentionState(), now, zone)

        assertEquals(null, screenState.dailyCapacityMinutes)
    }

    @Test
    fun notification_availability_is_injected_by_the_composition_root() {
        // 通知是否可用是平台事实，投影只透传注入值
        assertEquals(
            false,
            settingsScreenState(AttentionState(), now, zone, notificationsEnabled = false).notificationsEnabled,
        )
        assertEquals(
            true,
            settingsScreenState(AttentionState(), now, zone, notificationsEnabled = true).notificationsEnabled,
        )
    }

    @Test
    fun last_opened_resolves_to_the_page_that_was_actually_opened() {
        // 「上次打开」解析成上次真正打开过的页面
        val screenState = settingsScreenState(
            state = AttentionState(
                launchDestination = LaunchDestination.LAST_OPENED,
                lastOpenedDestination = LaunchDestination.TARGETS,
            ),
            now = now,
            zone = zone,
        )

        assertEquals(LaunchDestination.LAST_OPENED, screenState.selectedDestination)
        assertEquals(LaunchDestination.TARGETS, screenState.resolvedDestination)
    }

    @Test
    fun last_opened_falls_back_to_the_schedule_when_there_is_no_page_to_restore() {
        // 「上次打开」自身指向「上次打开」时回落到日程入口
        val screenState = settingsScreenState(
            state = AttentionState(
                launchDestination = LaunchDestination.LAST_OPENED,
                lastOpenedDestination = LaunchDestination.LAST_OPENED,
            ),
            now = now,
            zone = zone,
        )

        assertEquals(LaunchDestination.AGENDA, screenState.resolvedDestination)
    }

    @Test
    fun a_concrete_launch_destination_resolves_to_itself() {
        // 选定具体入口时解析结果就是该入口本身
        val screenState = settingsScreenState(
            state = AttentionState(
                launchDestination = LaunchDestination.STATISTICS,
                lastOpenedDestination = LaunchDestination.TARGETS,
            ),
            now = now,
            zone = zone,
        )

        assertEquals(LaunchDestination.STATISTICS, screenState.resolvedDestination)
    }

    @Test
    fun planning_day_follows_the_injected_instant_and_zone_instead_of_the_system_clock() {
        // 同一个瞬间在不同时区落在不同规划日：投影只认注入的时间与时区
        val instant = Instant.parse("2026-03-05T20:00:00Z")

        assertEquals(
            LocalDate.of(2026, 3, 6),
            settingsScreenState(AttentionState(), instant, ZoneId.of("Asia/Shanghai")).planningDay,
        )
        assertEquals(
            LocalDate.of(2026, 3, 5),
            settingsScreenState(AttentionState(), instant, ZoneId.of("America/Los_Angeles")).planningDay,
        )
    }
}
