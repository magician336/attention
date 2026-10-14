package com.attention.workspace.state

import com.attention.domain.AttentionState
import com.attention.domain.CapacitySummary
import com.attention.domain.TimeEntrySource
import com.attention.domain.capacitySummary
import com.attention.domain.planningDate
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
)

/** 规划日界线的定点表达，例如 180 分钟显示为「03:00」。这是展示约定，不是领域规则。 */
fun planningDayBoundaryLabel(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

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
 * 从领域状态投影出今天工作台的状态。领域规则（规划日、每日容量占用、未归档目标）
 * 仍然只在 `:domain`，这里只把它们读成页面要显示的行。
 *
 * 今天工作台不导航规划日，因此除了注入的 [now] 与 [zone] 之外没有页面选择需要喂进来：
 * 页面自己的输入草稿与对话框开关属于页面 module，不参与投影。
 */
fun todayScreenState(
    state: AttentionState,
    now: Instant,
    zone: ZoneId,
): TodayScreenState {
    val planningDay = state.planningDate(now, zone)
    val planningDate = planningDay.toString()
    val activeTargets = state.targets.filter { !it.archived }
    val capacity = state.capacitySummary(planningDate)
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
    )
}
