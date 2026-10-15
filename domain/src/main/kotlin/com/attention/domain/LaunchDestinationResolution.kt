package com.attention.domain

/**
 * 「上次打开」解析为具体页面；其它入口解析为自身。
 *
 * 这是启动入口的领域语义：组合根导航与设置页投影都要用同一份解析，因此它属于
 * `:domain`（ADR-0004 的唯一规则来源），而不属于页面投影 module。
 */
fun resolveLaunchDestination(
    requested: LaunchDestination,
    lastOpened: LaunchDestination,
): LaunchDestination = when (requested) {
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