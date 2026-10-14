package com.attention.workspace.state

import com.attention.domain.AttentionState
import com.attention.domain.CapacitySummary
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.domain.agendaEntries
import com.attention.domain.capacitySummary
import com.attention.domain.planningDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 所有日期页的一个可选规划日。日期值与标签都由投影给出，标签用于下拉项与 testTag。
 */
data class AgendaDateOption(
    val date: LocalDate,
    val label: String,
)

/**
 * 所有日期页上的一条日程条目行。展示文本与编辑表单的初值都由投影给出，
 * [entry] 只带这条条目自身的字段，不带整个领域状态。
 */
data class AgendaScheduleRow(
    val entry: ScheduleEntry,
    val title: String,
    val detailLabel: String,
    val note: String,
    val startText: String,
    val endText: String,
    val estimateText: String,
    val reminderText: String,
    val targetId: String?,
    /** 没有计时在进行时才允许从日程条目起计时。 */
    val canStartTimer: Boolean,
)

/**
 * 所有日期特殊视图下的一个规划日分组：一天的条目与这一天的每日容量。
 */
data class AgendaDateSection(
    val planningDate: String,
    val capacityLabel: String,
    val rows: List<AgendaScheduleRow>,
)

/**
 * 所有日期页的一条重复日程规则行。停止未来生成的 intent 需要 id，展示只需要标签。
 */
data class AgendaRecurrenceRuleLine(
    val ruleId: String,
    val label: String,
    val active: Boolean,
)

/**
 * 可以关联到日程条目的一个计划（只含未归档计划）。
 */
data class AgendaTargetOption(
    val targetId: String,
    val title: String,
)

/**
 * 所有日期页专有的不可变状态。它不携带完整的 [AttentionState]，
 * 当前时间与时区由组合根注入，投影与页面都不读系统时钟。
 *
 * 所有日期是特殊视图而不是日期值，因此 [selectedDate] 为 null 时页面按 [dateSections] 分组展示。
 */
data class AgendaScreenState(
    val planningDate: LocalDate,
    val selectedDate: LocalDate?,
    val headingLabel: String,
    val filterLabel: String,
    val availableDates: List<AgendaDateOption>,
    /** 选中规划日时的每日容量标签；所有日期视图为 null。 */
    val selectedCapacityLabel: String?,
    /** 选中规划日时的条目。 */
    val selectedRows: List<AgendaScheduleRow>,
    /** 所有日期视图按规划日分组的条目。 */
    val dateSections: List<AgendaDateSection>,
    val emptyLabel: String?,
    val recurrenceRules: List<AgendaRecurrenceRuleLine>,
    val targets: List<AgendaTargetOption>,
    /** 通知点名的日程条目要直接打开编辑对话框，它不受当前筛选影响。 */
    val pendingEditRow: AgendaScheduleRow?,
)

/**
 * 从领域状态投影出所有日期页状态。领域规则（规划日、条目筛选、重复日程展开、每日容量）
 * 仍然只在 `:domain`，这里只把它们读成页面要显示的行与标签。
 *
 * @param selectedDate 用户选中的规划日；null 表示所有日期特殊视图。
 * @param openScheduleId 从提醒通知点名的日程条目 id；用于直接打开编辑对话框。
 */
fun agendaScreenState(
    state: AttentionState,
    now: Instant,
    zone: ZoneId,
    selectedDate: LocalDate?,
    openScheduleId: String? = null,
): AgendaScreenState {
    val planningDay = state.planningDate(now, zone)
    val entries = state.agendaEntries(selectedDate)
    val availableDates = (state.schedules.mapNotNull { runCatching { LocalDate.parse(it.planningDate) }.getOrNull() } + planningDay)
        .distinct()
        .sorted()
        .map { date -> AgendaDateOption(date, "规划日 $date") }
    val targetTitles = state.targets.associate { it.id to it.title }
    val canStartTimer = state.activeTimer == null
    fun row(entry: ScheduleEntry) = agendaRow(entry, targetTitles, zone, canStartTimer)
    return AgendaScreenState(
        planningDate = planningDay,
        selectedDate = selectedDate,
        headingLabel = if (selectedDate == null) "所有日期" else "规划日 $selectedDate",
        filterLabel = if (selectedDate == null) "筛选：所有日期" else "筛选：规划日 $selectedDate",
        availableDates = availableDates,
        selectedCapacityLabel = selectedDate?.let { date ->
            capacitySummaryLabel(state.capacitySummary(date.toString()))
        },
        selectedRows = if (selectedDate == null) emptyList() else entries.map(::row),
        dateSections = if (selectedDate != null) {
            emptyList()
        } else {
            entries.groupBy { it.planningDate }.map { (date, dayEntries) ->
                AgendaDateSection(
                    planningDate = date,
                    capacityLabel = capacitySummaryLabel(state.capacitySummary(date)),
                    rows = dayEntries.map(::row),
                )
            }
        },
        emptyLabel = when {
            entries.isNotEmpty() -> null
            selectedDate == null -> "所有日期暂无日程条目。"
            else -> "规划日 $selectedDate 暂无日程条目。"
        },
        recurrenceRules = state.recurrenceRules.map { rule -> recurrenceRuleLine(rule) },
        targets = state.targets.filterNot { it.archived }.map { target ->
            AgendaTargetOption(target.id, target.title)
        },
        pendingEditRow = openScheduleId
            ?.let { id -> state.schedules.firstOrNull { it.id == id } }
            ?.let(::row),
    )
}

private fun agendaRow(
    entry: ScheduleEntry,
    targetTitles: Map<String, String>,
    zone: ZoneId,
    canStartTimer: Boolean,
): AgendaScheduleRow {
    val start = entry.startMinute
    val end = entry.endMinute
    val time = if (start != null && end != null) " · ${formatAgendaMinute(start)}-${formatAgendaMinute(end)}" else ""
    val target = entry.targetId?.let(targetTitles::get)?.let { " · $it" } ?: ""
    val reminder = entry.reminderEpochMillis?.let { " · 提醒 ${formatAgendaReminderEpoch(it, zone)}" } ?: " · 无提醒"
    return AgendaScheduleRow(
        entry = entry,
        title = entry.title,
        detailLabel = "${entry.planningDate}$time · ${entry.estimatedMinutes ?: 0} 分钟$target$reminder" +
            if (entry.completed) " · 已完成" else "",
        note = entry.note,
        startText = start?.let(::formatAgendaMinute) ?: "",
        endText = end?.let(::formatAgendaMinute) ?: "",
        estimateText = entry.estimatedMinutes?.toString() ?: "",
        reminderText = entry.reminderEpochMillis?.let { formatAgendaReminderEpoch(it, zone) } ?: "",
        targetId = entry.targetId,
        canStartTimer = canStartTimer,
    )
}

private fun recurrenceRuleLine(rule: RecurrenceRule): AgendaRecurrenceRuleLine = AgendaRecurrenceRuleLine(
    ruleId = rule.id,
    label = "${rule.title} · ${recurrenceDetailLabel(rule)} · ${rule.startDate}${rule.untilDate?.let { " 至 $it" } ?: ""}",
    active = rule.active,
)

private fun recurrenceDetailLabel(rule: RecurrenceRule): String = when (rule.frequency) {
    ScheduleFrequency.DAILY -> "每日"
    ScheduleFrequency.WEEKLY -> "每周 ${rule.weekdays.joinToString(",") { agendaWeekdayLabel(it) }}"
    ScheduleFrequency.MONTHLY -> "每月 ${rule.dayOfMonth} 日"
}

/** 每日容量的一行展示。容量是否超载由 `:domain` 的 [CapacitySummary] 判定。 */
fun capacitySummaryLabel(summary: CapacitySummary): String = when {
    summary.capacityMinutes == null -> "已安排 ${summary.usedMinutes} 分钟（每日容量未设置）"
    summary.overloaded -> "已安排 ${summary.usedMinutes} / ${summary.capacityMinutes} 分钟（超载）"
    else -> "已安排 ${summary.usedMinutes} / ${summary.capacityMinutes} 分钟"
}

/**
 * 重复日程频率的可见标签。它只改写展示文本、不改变领域结论，与统计页的 `GoalCadence.label()`
 * 同类，因此留在投影 module。
 */
fun ScheduleFrequency.agendaLabel(): String = when (this) {
    ScheduleFrequency.DAILY -> "每日"
    ScheduleFrequency.WEEKLY -> "每周"
    ScheduleFrequency.MONTHLY -> "每月"
}

fun agendaWeekdayLabel(day: Int): String = when (day) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    7 -> "日"
    else -> day.toString()
}

fun formatAgendaMinute(value: Int): String = "%02d:%02d".format(value / 60, value % 60)

fun formatAgendaReminderEpoch(epochMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).let { formatAgendaMinute(it.hour * 60 + it.minute) }