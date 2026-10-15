package com.attention.domain

import java.time.LocalDate

/**
 * 「日程」是启动入口与「上次打开」解析中的正式语义。
 *
 * 这是启动入口的唯一规则来源（ADR-0005）：组合根导航与设置页投影都从 :domain 取，
 * 页面投影与组合根不各自实现一份解析。
 */

/** 「上次打开」能够恢复的顶级工作区：日程、目标树、统计。 */
private val restorableWorkspaces = setOf(
    LaunchDestination.AGENDA,
    LaunchDestination.TARGETS,
    LaunchDestination.STATISTICS,
)

/**
 * 旧入口值到「日程」的兼容映射：旧版本持久化的 [LaunchDestination.TODAY] 与
 * [LaunchDestination.ALL_DATES] 读取后等价于日程入口，其余值保持自身。
 */
fun LaunchDestination.asAgendaEntry(): LaunchDestination = when (this) {
    LaunchDestination.TODAY, LaunchDestination.ALL_DATES -> LaunchDestination.AGENDA
    else -> this
}

/**
 * 读取持久化的入口名称（DataStore 与备份 JSON 共用同一套枚举名称协议）。
 *
 * 旧版本的值经兼容映射等价于日程入口；无法解析的值没有有效历史，回退到日程。
 */
fun launchDestinationFromPersistedName(name: String?): LaunchDestination =
    name?.let { runCatching { LaunchDestination.valueOf(it) }.getOrNull() }
        ?.asAgendaEntry()
        ?: LaunchDestination.AGENDA

/**
 * 「上次打开」只解析出日程、目标树、统计三者之一；解析到自身、旧入口值或无效历史时
 * 回退到日程。其它入口解析为自身，旧入口值仍按旧页面直达。
 */
fun resolveLaunchDestination(
    requested: LaunchDestination,
    lastOpened: LaunchDestination,
): LaunchDestination = when (requested) {
    LaunchDestination.LAST_OPENED ->
        if (lastOpened in restorableWorkspaces) lastOpened else LaunchDestination.AGENDA
    else -> requested
}

/**
 * 启动位置的统一优先级：外部日程请求 > 保存的启动入口 > 上次打开 > 当前规划日的日程。
 *
 * 外部日程请求是提醒或通知带来的打开定位：它只决定这一次落在哪里，不被保存的启动入口拦截，
 * 也不改写保存的启动入口或产生任何业务数据。没有外部请求时按保存的入口解析，
 * 其中「上次打开」只解析出日程、目标树、统计；历史无效时回退到日程，由调用方打开当前规划日。
 */
fun resolveInitialLaunchDestination(
    hasExternalScheduleRequest: Boolean,
    requested: LaunchDestination,
    lastOpened: LaunchDestination,
): LaunchDestination = if (hasExternalScheduleRequest) {
    LaunchDestination.AGENDA
} else {
    resolveLaunchDestination(requested, lastOpened)
}

/**
 * 外部日程请求要定位的规划日。
 *
 * 请求点名的规划日优先；它缺失或无法解析时退回被点名条目自身的规划日，页面因此总是落在
 * 条目所在的那一天而不是当前规划日。没有任何可定位的目标时返回 null，由调用方回退到当前规划日。
 */
fun externalSchedulePlanningDate(
    state: AttentionState,
    requestedPlanningDate: String?,
    requestedEntryId: String?,
): LocalDate? {
    val requested = requestedPlanningDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (requested != null) return requested
    val entryPlanningDate = requestedEntryId
        ?.let { id -> state.schedules.firstOrNull { it.id == id }?.planningDate }
        ?: return null
    return runCatching { LocalDate.parse(entryPlanningDate) }.getOrNull()
}
