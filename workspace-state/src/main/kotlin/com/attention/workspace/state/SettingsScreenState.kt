package com.attention.workspace.state

import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
import com.attention.domain.asAgendaEntry
import com.attention.domain.planningDate
import com.attention.domain.resolveLaunchDestination
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
 * 设置页上的一个周起始日选项。标签沿用 [weekDayLabel]，页面只负责渲染与发出选择 intent。
 */
data class SettingsWeekDayOption(
    val day: Int,
    val label: String,
    val selected: Boolean,
)

/**
 * 设置页专有的不可变状态。它只带页面需要的信息，不携带完整的 [AttentionState]，
 * 当前时间与时区由组合根注入，投影不读系统时钟。
 *
 * 启动入口、规划日界线、周起始日、每日容量、通知与备份数据集中在这里：
 * 设置页是这些设置唯一的编辑入口，因此每一种可改设置都要有一个对应的字段。
 */
data class SettingsScreenState(
    val options: List<LaunchDestinationOption>,
    val selectedDestination: LaunchDestination,
    val resolvedDestination: LaunchDestination,
    val planningDay: LocalDate,
    /** 规划日界线，单位为 0–1439 的分钟；语义与校验仍然由 `:domain` 的 StoredSettings 负责。 */
    val planningDayBoundaryMinutes: Int,
    val weekDayOptions: List<SettingsWeekDayOption>,
    /** 每日容量（分钟）。未设置时为 null，页面据此把输入框留空。 */
    val dailyCapacityMinutes: Int?,
    /**
     * 通知是否可用。这是平台事实，由组合根查好后注入，投影与页面都不触达通知管理器
     * （ADR-0005 第 4 节「Android adapter 归属组合根」）。
     */
    val notificationsEnabled: Boolean,
)

/**
 * 从领域状态投影出设置页状态。领域规则（规划日、入口解析等）仍然只在 `:domain`。
 */
fun settingsScreenState(
    state: AttentionState,
    now: Instant,
    zone: ZoneId,
    notificationsEnabled: Boolean = true,
): SettingsScreenState {
    // 旧版本持久化的 TODAY / ALL_DATES 在页面上就是「日程」这一项：选中判定走领域兼容映射，
    // 页面因此不需要知道历史入口值。
    val selected = state.launchDestination.asAgendaEntry()
    return SettingsScreenState(
        selectedDestination = state.launchDestination,
        resolvedDestination = resolveLaunchDestination(state.launchDestination, state.lastOpenedDestination),
        planningDay = state.planningDate(now, zone),
        options = listOf(
            LaunchDestinationOption(LaunchDestination.AGENDA, "日程", selected == LaunchDestination.AGENDA),
            LaunchDestinationOption(LaunchDestination.TARGETS, "目标树", selected == LaunchDestination.TARGETS),
            LaunchDestinationOption(LaunchDestination.STATISTICS, "统计", selected == LaunchDestination.STATISTICS),
            LaunchDestinationOption(LaunchDestination.LAST_OPENED, "上次打开", selected == LaunchDestination.LAST_OPENED),
        ),
        planningDayBoundaryMinutes = state.settings.planningDayBoundaryMinutes,
        weekDayOptions = (1..7).map { day ->
            SettingsWeekDayOption(day, weekDayLabel(day), state.settings.weekStartDay == day)
        },
        dailyCapacityMinutes = state.settings.dailyCapacityMinutes,
        notificationsEnabled = notificationsEnabled,
    )
}
