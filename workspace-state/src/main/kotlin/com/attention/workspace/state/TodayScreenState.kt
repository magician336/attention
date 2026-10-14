package com.attention.workspace.state

import com.attention.domain.AttentionState
import com.attention.domain.CapacitySummary
import com.attention.domain.ScheduleEntry
import com.attention.domain.TimeEntrySource
import com.attention.domain.capacitySummary
import com.attention.domain.elapsedMillis
import com.attention.domain.formatMinuteOfDay
import com.attention.domain.planningDate
import com.attention.domain.timerSettlementPreview
import com.attention.domain.timerTargetLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 今天工作台上的一个可选目标。归档目标不出现：归档目标不能接收新的时间记录，
 * 因此页面拿不到它的选项，也就没有把它发给动作路径的机会。
 */
data class TodayTargetOption(
    val targetId: String,
    val title: String,
)

/**
 * 今天工作台上的一个周起始日选项。标签由投影给出，页面只负责渲染与发出选择 intent。
 */
data class TodayWeekDayOption(
    val day: Int,
    val label: String,
    val selected: Boolean,
)

/**
 * 计时结束时会产生的一个规划日分桶。跨过规划日界线的那次计时会拆成多个分桶，
 * 因此这里是一个列表而不是单个规划日。
 */
data class TodaySettlementSegment(
    val planningDate: String,
    val durationMinutes: Int,
)

/**
 * 结束计时会产出哪些时间记录的只读预览。它由 [com.attention.domain.timerSettlementPreview]
 * 按注入的时刻算出：页面不自己推算「这次会记到哪个规划日」。
 */
data class TodaySettlementPreview(
    val hasCompleteMinutes: Boolean,
    val segments: List<TodaySettlementSegment>,
    val totalMinutes: Int,
)

/**
 * 正在计时的那个计时器。目标名、实际投入与结束范围预览都由注入的时间决定，
 * 页面因此不需要读系统时钟，也不需要知道「实际投入」是怎么算出来的。
 */
data class TodayActiveTimer(
    val targetId: String?,
    val targetLabel: String,
    val paused: Boolean,
    val completedSegmentCount: Int,
    /** 到注入时刻为止的整分钟实际投入；不足 1 分钟为 0。 */
    val elapsedMinutes: Int,
    val settlementPreview: TodaySettlementPreview,
)

/**
 * 今天工作台「今日安排」里的一条日程条目行。展示文本与编辑表单的初值都由投影给出，
 * [entry] 只带这条条目自身的字段，不带整个领域状态。
 */
data class TodayScheduleRow(
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
 * 今天工作台上的一条时间记录。
 *
 * [planningDate] 与 [source] 随记录一起给出，是为了让「编辑只调整时长、目标归属与备注，
 * 不改规划日与来源」这条约束在投影层面就能被断言，而不必回到领域状态去查。
 *
 * [targetLabel] 按记录自己的归属解析，包含已归档目标：记录可以在目标归档之后继续存在，
 * 那时它仍然显示那个目标的名字，而不是变成「未归属活动」。
 */
data class TodayTimeRecord(
    val entryId: String,
    val planningDate: String,
    val durationMinutes: Int,
    val targetId: String?,
    val targetLabel: String,
    val source: TimeEntrySource,
    val note: String,
)

/**
 * 今天工作台专有的不可变状态。它只带页面需要的信息，不携带完整的 [AttentionState]，
 * 当前时间与时区由组合根注入，投影与页面都不读系统时钟。
 */
data class TodayScreenState(
    val planningDate: LocalDate,
    val planningDayBoundaryMinutes: Int,
    val planningDayBoundaryLabel: String,
    val weekDayOptions: List<TodayWeekDayOption>,
    val dailyCapacityMinutes: Int?,
    val onboardingCompleted: Boolean,
    val quickRecordTargets: List<TodayTargetOption>,
    val targetOptions: List<TodayTargetOption>,
    val recordedMinutesLabel: String,
    val capacitySummaryLabel: String,
    val records: List<TodayTimeRecord>,
    /** 正在计时的计时器；null 表示没有计时在进行，页面改为渲染开始计时的入口。 */
    val activeTimer: TodayActiveTimer?,
    /**
     * 通知是否可用。这是平台事实，由组合根查好后注入，投影与页面都不自己判断
     * （页面不触达任何 Android API，见 ADR-0005 第 4 节「Android adapter 归属组合根」）。
     */
    val notificationsEnabled: Boolean,
    val scheduleRows: List<TodayScheduleRow>,
)

/** 规划日界线的定点表达，例如 180 分钟显示为「03:00」。这是展示约定，不是领域规则。 */
fun planningDayBoundaryLabel(minutes: Int): String = formatMinuteOfDay(minutes)

/** 周起始日的名字。名字取自 `CONTEXT.md` 已有词汇，不新造领域词。 */
fun weekDayLabel(day: Int): String = when (day) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}

/**
 * 每日容量的可见标签。容量怎么占用仍然由领域层的 [capacitySummary] 判定，
 * 这里只把它读成一句话。
 */
fun CapacitySummary.capacityLabel(): String = when {
    capacityMinutes == null -> "已安排 $usedMinutes 分钟（每日容量未设置）"
    overloaded -> "已安排 $usedMinutes / $capacityMinutes 分钟（超载）"
    else -> "已安排 $usedMinutes / $capacityMinutes 分钟"
}

/**
 * 从领域状态投影出今天工作台的状态。领域规则（规划日、每日容量占用、未归档目标、
 * 计时结算与实际投入）仍然只在 `:domain`，这里只把它们读成页面要显示的行。
 *
 * 今天工作台不导航规划日，因此除了注入的 [now] 与 [zone] 之外没有页面选择需要喂进来：
 * 页面自己的输入草稿与对话框开关属于页面 module，不参与投影。
 *
 * [notificationsEnabled] 是平台事实而不是领域结论，因此由组合根查好后注入：
 * 投影不触达 Android API，页面也因此拿不到通知管理器。
 */
fun todayScreenState(
    state: AttentionState,
    now: Instant,
    zone: ZoneId,
    notificationsEnabled: Boolean = true,
): TodayScreenState {
    val planningDay = state.planningDate(now, zone)
    val planningDate = planningDay.toString()
    val activeTargets = state.targets.filter { !it.archived }
    val capacity = state.capacitySummary(planningDate)
    val targetTitles = activeTargets.associate { it.id to it.title }
    return TodayScreenState(
        planningDate = planningDay,
        planningDayBoundaryMinutes = state.settings.planningDayBoundaryMinutes,
        planningDayBoundaryLabel = planningDayBoundaryLabel(state.settings.planningDayBoundaryMinutes),
        weekDayOptions = (1..7).map { day ->
            TodayWeekDayOption(day, weekDayLabel(day), state.settings.weekStartDay == day)
        },
        dailyCapacityMinutes = state.settings.dailyCapacityMinutes,
        onboardingCompleted = state.onboardingCompleted,
        quickRecordTargets = activeTargets.take(4).map { TodayTargetOption(it.id, it.title) },
        targetOptions = activeTargets.map { TodayTargetOption(it.id, it.title) },
        recordedMinutesLabel = "本规划日已记录：" +
            "${state.timeEntries.filter { it.planningDate == planningDate }.sumOf { it.durationMinutes }} 分钟",
        capacitySummaryLabel = capacity.capacityLabel(),
        records = state.timeEntries.filter { it.planningDate == planningDate }.map { entry ->
            TodayTimeRecord(
                entryId = entry.id,
                planningDate = entry.planningDate,
                durationMinutes = entry.durationMinutes,
                targetId = entry.targetId,
                targetLabel = state.targets.firstOrNull { it.id == entry.targetId }?.title ?: "未归属活动",
                source = entry.source,
                note = entry.note,
            )
        },
        activeTimer = state.activeTimer?.let { timer ->
            val preview = state.timerSettlementPreview(now, zone)
            TodayActiveTimer(
                targetId = timer.targetId,
                targetLabel = state.timerTargetLabel(timer.targetId),
                paused = timer.paused,
                completedSegmentCount = timer.completedSegments.size,
                elapsedMinutes = (timer.elapsedMillis(now) / 60_000L).toInt(),
                settlementPreview = TodaySettlementPreview(
                    hasCompleteMinutes = preview.hasCompleteMinutes,
                    segments = preview.entries.map {
                        TodaySettlementSegment(it.planningDate, it.durationMinutes)
                    },
                    totalMinutes = preview.totalMinutes,
                ),
            )
        },
        notificationsEnabled = notificationsEnabled,
        scheduleRows = state.schedules
            .filter { it.planningDate == planningDate }
            .map { todayScheduleRow(it, targetTitles, zone, canStartTimer = state.activeTimer == null) },
    )
}

/**
 * 一条日程条目的展示与表单初值。提醒时刻按注入的 [zone] 换算成 HH:mm，
 * 页面因此既不读系统默认时区，也不自己解析纪元毫秒。
 */
private fun todayScheduleRow(
    entry: ScheduleEntry,
    targetTitles: Map<String, String>,
    zone: ZoneId,
    canStartTimer: Boolean,
): TodayScheduleRow {
    val start = entry.startMinute
    val end = entry.endMinute
    val time = if (start != null && end != null) " · ${formatTodayMinute(start)}-${formatTodayMinute(end)}" else ""
    val target = entry.targetId?.let(targetTitles::get)?.let { " · $it" } ?: ""
    val reminder = entry.reminderEpochMillis?.let { " · 提醒 ${formatTodayReminderEpoch(it, zone)}" } ?: " · 无提醒"
    return TodayScheduleRow(
        entry = entry,
        title = entry.title,
        detailLabel = "${entry.planningDate}$time · ${entry.estimatedMinutes ?: 0} 分钟$target$reminder" +
            if (entry.completed) " · 已完成" else "",
        note = entry.note,
        startText = start?.let(::formatTodayMinute) ?: "",
        endText = end?.let(::formatTodayMinute) ?: "",
        estimateText = entry.estimatedMinutes?.toString() ?: "",
        reminderText = entry.reminderEpochMillis?.let { formatTodayReminderEpoch(it, zone) } ?: "",
        targetId = entry.targetId,
        canStartTimer = canStartTimer,
    )
}

/** 一天内的第几分钟显示为 HH:mm。这是展示约定，不是领域规则。 */
private fun formatTodayMinute(minuteOfDay: Int): String = formatMinuteOfDay(minuteOfDay)

/** 提醒的绝对时刻按注入的时区显示为一天内的 HH:mm。 */
private fun formatTodayReminderEpoch(epochMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).let { formatTodayMinute(it.hour * 60 + it.minute) }
