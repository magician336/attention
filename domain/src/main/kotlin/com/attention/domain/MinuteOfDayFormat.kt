package com.attention.domain

private const val MINUTES_PER_HOUR = 60

/**
 * 一天之内第几分钟的定点表达，例如 180 分钟显示为「03:00」。
 *
 * 它是展示约定而不是领域规则：调用方自己决定要不要先把取值收敛到一天之内
 * （页面投影按领域校验过的分钟数直接调用，前台计时服务用已截断的整分钟调用）。
 *
 * 放在 `:domain` 是因为这是三方唯一都已经依赖的层：页面投影 module 只依赖 `:domain`，
 * 而 `:app`（页面与前台计时服务）同时依赖 `:domain` 与页面投影 module。放在任何一侧都会让
 * 另外一侧要么反向依赖、要么把同一条算式再写一遍——同一条分钟到定点算术散在多处正是
 * ADR-0004 要避免的第二份规则来源。与 [PlannerSettings.planningDayBoundary]、
 * [AttentionState] 的导出文本一样，这里只负责把已经算好的数字排成可显示的文本。
 */
fun formatMinuteOfDay(minutes: Int): String =
    "%02d:%02d".format(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
