package com.attention.workspace.state

import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
import com.attention.domain.planningDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 设置页的一个启动入口选项。标签由投影给出，页面只负责渲染。
 */
data class LaunchDestinationOption(
    val destination: LaunchDestination,
    val label: String,
    val selected: Boolean,
)

/**
 * 设置页专有的不可变状态。它只带页面需要的信息，不携带完整的 [AttentionState]，
 * 当前时间与时区由组合根注入，投影不读系统时钟。
 */
data class SettingsScreenState(
    val options: List<LaunchDestinationOption>,
    val selectedDestination: LaunchDestination,
    val resolvedDestination: LaunchDestination,
    val planningDay: LocalDate,
)

/**
 * 「上次打开」解析为具体页面；其它入口解析为自身。这条规则属于启动入口语义，
 * 组合根导航与页面投影共用同一份实现，不在两侧各写一遍。
 */
fun resolveLaunchDestination(requested: LaunchDestination, lastOpened: LaunchDestination): LaunchDestination = when (requested) {
    LaunchDestination.LAST_OPENED -> when (lastOpened) {
        LaunchDestination.TODAY,
        LaunchDestination.TARGETS,
        LaunchDestination.ALL_DATES,
        LaunchDestination.STATISTICS,
        -> lastOpened
        LaunchDestination.LAST_OPENED -> LaunchDestination.TODAY
    }
    else -> requested
}

/**
 * 从领域状态投影出设置页状态。领域规则（规划日等）仍然只在 `:domain`。
 */
fun settingsScreenState(
    state: AttentionState,
    now: Instant,
    zone: ZoneId,
): SettingsScreenState {
    val selected = state.launchDestination
    return SettingsScreenState(
        selectedDestination = selected,
        resolvedDestination = resolveLaunchDestination(selected, state.lastOpenedDestination),
        planningDay = state.planningDate(now, zone),
        options = listOf(
            LaunchDestinationOption(LaunchDestination.TODAY, "今天", selected == LaunchDestination.TODAY),
            LaunchDestinationOption(LaunchDestination.TARGETS, "目标树", selected == LaunchDestination.TARGETS),
            LaunchDestinationOption(LaunchDestination.ALL_DATES, "所有日期", selected == LaunchDestination.ALL_DATES),
            LaunchDestinationOption(LaunchDestination.STATISTICS, "统计", selected == LaunchDestination.STATISTICS),
            LaunchDestinationOption(LaunchDestination.LAST_OPENED, "上次打开", selected == LaunchDestination.LAST_OPENED),
        ),
    )
}
