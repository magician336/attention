package com.attention.workspace.state

import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
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
    fun launch_destination_options_are_listed_in_a_fixed_order() {
        // 启动入口的可见标签与顺序是设置页的既有行为
        val screenState = settingsScreenState(
            state = AttentionState(),
            now = now,
            zone = zone,
        )

        assertEquals(
            listOf(
                LaunchDestination.TODAY to "今天",
                LaunchDestination.TARGETS to "目标树",
                LaunchDestination.ALL_DATES to "所有日期",
                LaunchDestination.STATISTICS to "统计",
                LaunchDestination.LAST_OPENED to "上次打开",
            ),
            screenState.options.map { it.destination to it.label },
        )
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
