package com.attention.workspace.state

import com.attention.domain.AttentionState
import com.attention.domain.CapacitySummary
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.domain.TimeEntrySource
import com.attention.domain.agendaDayTimelines
import com.attention.domain.agendaTimeline
import com.attention.domain.capacitySummary
import com.attention.domain.elapsedMillis
import com.attention.domain.exceedsDailyCapacity
import com.attention.domain.formatMinuteOfDay
import com.attention.domain.isScheduleInvestmentMet
import com.attention.domain.planningDate
import com.attention.domain.plannedMinutes
import com.attention.domain.scheduleInvestedMinutes
import com.attention.domain.timerTargetLabel
import com.attention.domain.timingScheduleEntryId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日程页的一个可选规划日。日期值与标签都由投影给出，标签用于下拉项与 testTag。
 */
data class AgendaDateOption(
    val date: LocalDate,
    val label: String,
)

/**
 * 日程页上的一条日程条目行。展示文本与编辑表单的初值都由投影给出，
 * [entry] 只带这条条目自身的字段，不带整个领域状态。
 *
 * 计划投入、实际投入、完成状态与「已超出容量」都是领域判定的结果：页面不自己比较分钟数，
 * 也不把预计时长当成实际投入展示（[investmentLabel] 是唯一给这两种投入的口径）。
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
    /** 计划投入、实际投入与完成状态，页面原样展示。 */
    val investmentLabel: String = "",
    /** 关联计划的名字；没有关联计划时为 null。 */
    val targetLabel: String? = null,
    /** 单条日程自己超出每日容量时的提示；没超载或未设置每日容量时为 null。 */
    val capacityNotice: String? = null,
    /** 这条日程就是正在计时的那一条：它固定在时间轴顶部，并由页面用文字与图标标明。 */
    val timing: Boolean = false,
    /** 完成状态的事实。判定来自领域的 `isScheduleInvestmentMet` 与条目自身的完成标记。 */
    val completed: Boolean = false,
    /**
     * 行上同时提供可见的完成勾选框与更多菜单。
     *
     * 两者都是任何用户一次点击就能到达的入口，因此长按只能作为额外的熟练操作快捷方式，
     * 不能成为完成、编辑或计时控制的唯一路径。页面按这两个开关决定画哪些控件。
     */
    val offersCompletionCheck: Boolean = true,
    val offersMoreMenu: Boolean = true,
)

/**
 * 停止计时后落下的**一笔**记录。跨规划日界线的那次停止会被领域切成多笔，
 * 面板因此持有的是一个列表，而不是自行合成的一笔。
 */
data class AgendaStoppedRecord(
    val entryId: String,
    val planningDate: String,
    val minutes: Int,
    /** 「规划日 · N 分钟」：规划日归属是用户核对投入时最需要看到的一层。 */
    val label: String,
)

/**
 * 最近一次停止计时的结果面板内容。四个要素全部来自领域读数：
 *
 * - 实际投入：这次停止产出的那几笔记录之和（跨规划日按笔呈现，不归并）
 * - 计划时长：`ScheduleEntry.plannedMinutes()`；没有承诺时为 null
 * - 完成结果：条目自身的完成标记与领域的 `isScheduleInvestmentMet`
 * - 记录归属：这笔投入所属的计划名字，没有计划时是「未归属活动」
 *
 * [needsCompletionDecision] 只在「有计划且尚未达标」时为真：那正是用户必须自己决定的
 * 时刻。页面据此摆出「结束并保留未完成」与「标记为完成」，并复用既有的完成路径。
 */
data class AgendaStopResult(
    /** 这次停止关联的日程条目；自由计时没有来源日程时为 null。 */
    val scheduleEntryId: String?,
    /** 这次停止落下的每一笔记录，按领域给出的顺序如实呈现。 */
    val records: List<AgendaStoppedRecord>,
    /** 这次停止的实际投入合计，单位分钟。 */
    val actualMinutes: Int,
    val actualLabel: String,
    /** 计划时长；日程没有承诺时长时为 null。 */
    val plannedMinutes: Int?,
    val plannedLabel: String,
    val completed: Boolean,
    val completionLabel: String,
    /** 有计划且实际投入尚未达标：完成与否由用户决定，面板摆出两个动作。 */
    val needsCompletionDecision: Boolean,
    /** 记录归属：所属计划的名字，没有计划时是「未归属活动」。 */
    val attributionLabel: String,
)

/**
 * 所有日期特殊视图下的一个规划日分组：一天的时间轴与这一天的每日容量。
 *
 * [timelineRows] 是有具体时间、按开始/结束时间排列的条目（正在计时的固定在最前），
 * [unscheduledRows] 是这一天里没有具体时间的条目。
 */
data class AgendaDateSection(
    val planningDate: String,
    val capacityLabel: String,
    val timelineRows: List<AgendaScheduleRow>,
    val unscheduledRows: List<AgendaScheduleRow>,
)

/**
 * 工作台首屏的一个区块。顺序由投影给出，页面按这个顺序渲染，因此
 * 「首屏自上而下固定为……」是一条可以被测试钉住的约定，而不是页面里的硬编码顺序。
 */
enum class AgendaWorkbenchSection {
    PLANNING_DAY_SUMMARY,
    TIMELINE,
    RUNNING_TIMER,
    ADD_SCHEDULE,
    BACKFILL,
    /** 重复日程折叠后的次级入口：它不占首屏主路径，但创建与停止未来生成仍然可达。 */
    RECURRENCE,
}

/**
 * 工作台顶部的只读摘要：规划日、规划日界线、周起始日与每日容量。
 *
 * 这里只读不写：完整设置继续走统计页底部的「设置与数据」，工作台不提供设置表单。
 */
data class AgendaWorkbenchSummary(
    val planningDateLabel: String,
    val boundaryLabel: String,
    val weekStartLabel: String,
    val capacitySettingLabel: String,
    /** 所选规划日的已安排与容量读数；所有日期视图为 null。 */
    val capacityUsageLabel: String?,
)

/**
 * 日程页的一条重复日程规则行。停止未来生成的 intent 需要 id，展示只需要标签。
 */
data class AgendaRecurrenceRuleLine(
    val ruleId: String,
    val label: String,
    val active: Boolean,
)

/**
 * 日程页顶部的日期范围。它们是同一页上的三个范围，不是三个顶级工作区入口。
 */
enum class AgendaRangeScope { TODAY, TOMORROW, ALL_DATES }

/**
 * 一个日期范围选项。[date] 为 null 只在「所有日期」上出现：所有日期是特殊视图，
 * 不是一个可以写进日程条目的日期值。
 */
data class AgendaRangeOption(
    val scope: AgendaRangeScope,
    val label: String,
    val date: LocalDate?,
    val selected: Boolean,
)

/**
 * 运行中的计时。计时卡下线之后，运行中的计时仍然必须在日程页看得见并能停止，
 * 否则用户会失去停掉一次计时的唯一入口。
 *
 * 状态不只靠颜色表达：锈橙色由页面用品牌语义色画出，[statusLabel] 与页面上的图标
 * 一起把「计时中 / 已暂停」讲清楚；[pendingHintLabel] 在暂停时告诉用户这还没保存，
 * 以及下一步该走恢复还是结束并保存。
 */
data class AgendaActiveTimer(
    val targetLabel: String,
    /** 当前计时目标；「切换计时目标」菜单用它标出当前选中的那一个，null 是未归属活动。 */
    val targetId: String?,
    val paused: Boolean,
    /** 到注入时刻为止的整分钟实际投入；不足 1 分钟为 0。 */
    val elapsedMinutes: Int,
    /** 已投入时长的可读文本。 */
    val elapsedLabel: String,
    /** 计时中 / 已暂停：颜色之外的第二重状态语义。 */
    val statusLabel: String,
    /** 暂停时才有的下一步提示；运行中为 null。 */
    val pendingHintLabel: String?,
)

/**
 * 所选规划日上的一条实际投入。手动时间账本的编辑与删除跟着合并后的日程页走，
 * 因此记录按所选规划日给出，页面不自己筛选。
 */
data class AgendaTimeRecord(
    val entryId: String,
    val durationMinutes: Int,
    val targetLabel: String,
    val targetId: String?,
    val source: TimeEntrySource,
    val note: String,
)

/**
 * 可以关联到日程条目的一个计划（只含未归档计划）。
 */
data class AgendaTargetOption(
    val targetId: String,
    val title: String,
)

/**
 * 工作台统一 `+` 菜单里的一个主要动作。
 *
 * 两个动作都是「用户想做点什么的」入口：安排一件未来的事，或为正在发生的事开始计时。
 * 它们是一个集合而不是两个各自散落的按钮，因此新增动作只需在这里加一项。
 */
enum class AgendaQuickAction {
    /** 打开添加日程的底部表单面板。 */
    ADD_SCHEDULE,

    /** 就地挑一个未归属活动或未归档计划，开始一次自由计时。 */
    START_FREE_TIMER,
}

/**
 * `+` 菜单里的一个动作。[label] 是可见文案与读屏读到的名字，
 * 页面按 [action] 决定打开哪一块面板，因此页面不需要自己翻译动作语义。
 */
data class AgendaQuickActionOption(
    val action: AgendaQuickAction,
    val label: String,
)

/**
 * 「开始自由计时」里一个可以选的计时目标。
 *
 * [targetId] 为 null 表示未归属活动——自由计时不必先有日程，也不必先有计划，
 * 所以它永远排在第一个。[selected] 由投影标出当前正在计时的那个目标，
 * 页面用文字表达它，不靠颜色单独传达选中态。
 */
data class AgendaFreeTimerOption(
    val targetId: String?,
    val label: String,
    val selected: Boolean = false,
)

/**
 * 日程页专有的不可变状态。它不携带完整的 [AttentionState]，
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
    /** 首屏顶部的只读规划日与每日容量摘要。 */
    val workbenchSummary: AgendaWorkbenchSummary,
    /** 首屏区块的自上而下顺序。页面按它渲染，不自己排顺序。 */
    val workbenchSections: List<AgendaWorkbenchSection> = AgendaWorkbenchSection.entries.toList(),
    /** 所选规划日没有任何日程条目时的空态引导；页面据此直接给出添加日程入口。 */
    val workbenchEmptyLabel: String? = null,
    /** 选中规划日时有具体时间、按开始/结束时间排列的条目，正在计时的固定在最前。 */
    val timelineRows: List<AgendaScheduleRow> = emptyList(),
    /** 选中规划日里没有具体时间的条目。 */
    val unscheduledRows: List<AgendaScheduleRow> = emptyList(),
    /** 所有日期视图按规划日分组的条目。 */
    val dateSections: List<AgendaDateSection>,
    val emptyLabel: String?,
    val recurrenceRules: List<AgendaRecurrenceRuleLine>,
    val targets: List<AgendaTargetOption>,
    /** 通知点名的日程条目要直接打开编辑对话框，它不受当前筛选影响。 */
    val pendingEditRow: AgendaScheduleRow?,
    /** 顶部日期范围（今天、明天、所有日期）与当前选中的那一个。 */
    val ranges: List<AgendaRangeOption> = emptyList(),
    /** 选中明天时突出添加日程：只切换所选规划日，不复制今天的日程。 */
    val highlightAddSchedule: Boolean = false,
    /** 添加日程卡片的标题，选中明天时说明在为哪一天安排。 */
    val addScheduleHeadingLabel: String = "新增日程",
    /**
     * 添加日程底部面板打开时的规划日。没有显式选过日期时它就是当前规划日，
     * 选了哪一天就用那一天——面板因此不需要用户先输一遍日期。
     */
    val addScheduleDefaultDate: String = "",
    /** 同一条默认规划日的可读标签，用作面板上的字段说明。 */
    val addScheduleDefaultDateLabel: String = "",
    /** 工作台 `+` 菜单里的主要动作，顺序与标签都由投影给出。 */
    val quickActions: List<AgendaQuickActionOption> = quickActionOptions(),
    /**
     * 「开始自由计时」可以选的目标：未归属活动加未归档计划。
     * 自由计时不要求先建日程，因此这里不是日程条目。
     */
    val freeTimerOptions: List<AgendaFreeTimerOption> = listOf(AgendaFreeTimerOption(null, "未归属活动")),
    /** 运行中的计时；null 表示没有计时在进行。 */
    val activeTimer: AgendaActiveTimer? = null,
    /**
     * 最近一次停止计时的结果面板；null 表示此刻没有待核对的那一笔。
     *
     * 它由 [stoppedEntryIds] 决定，而不是页面自己缓存的一段历史：组合根在停止命令成功后
     * 交出这次产出的记录 id，投影按 id 从领域状态读回事实。因此「消费掉」（清空 id 列表）
     * 就等于面板消失，陈旧结果不会被反复展示。
     */
    val stopResult: AgendaStopResult? = null,
    /** 所选规划日上的实际投入；所有日期视图为空。 */
    val timeRecords: List<AgendaTimeRecord> = emptyList(),
    val recordedMinutesLabel: String = "",
    /**
     * 组合根注入的事实：本地数据仍在第一次读取中。
     *
     * 它**不是**页面自己猜的，也不来自领域状态：[agendaScreenState] 在读到之前拿到的是一份空的
     * 默认状态，页面无从区分「用户真的没有数据」与「还没读到」。因此组合根把持久化层是否读上来
     * 这一事实注入进来（ADR-0005 第 1 节），页面只按它渲染轻量时间轴骨架。
     */
    val loading: Boolean = false,
    /**
     * 组合根注入的事实：本地数据读取失败时的说明；null 表示读取正常。
     *
     * 与 [loading] 一样是组合根的事实而不是页面判断。页面据此渲染带「重试」的错误状态，
     * 而不是把一个空的工作台留给用户（#195 验收标准 2）。
     */
    val loadError: String? = null,
)

/**
 * 从领域状态投影出日程页状态。领域规则（规划日、条目筛选、重复日程展开、每日容量）
 * 仍然只在 `:domain`，这里只把它们读成页面要显示的行与标签。
 *
 * @param selectedDate 用户选中的规划日；null 表示所有日期特殊视图。
 * @param selectedScope 用户选中的日期范围。它不从 [selectedDate] 与当前规划日反推：
 * 用户显式选过的范围要跨规划日界线继续可见，否则跨过界线的一帧里三个 chip 会同时失去高亮，
 * 页面停在用户明明选过的范围上却没有告诉他选的是哪一个。
 * @param openScheduleId 从提醒通知点名的日程条目 id；用于直接打开编辑对话框。
 * @param stoppedEntryIds 最近一次停止计时产出的记录 id。它是「刚刚停止的那一笔」的唯一来源：
 *  面板按 id 从 [state] 读回事实，因此它为空时面板消失，而不是留下一段陈旧历史。
 * @param loading 组合根注入的「本地数据仍在第一次读取中」事实。它不来自领域状态：
 * 读上来之前 [state] 是一份空的默认值，页面无法区分「没有数据」与「还没读到」。
 * @param loadError 组合根注入的读取失败说明；null 表示读取正常。
 */
fun agendaScreenState(
    state: AttentionState,
    now: Instant,
    zone: ZoneId,
    selectedDate: LocalDate?,
    selectedScope: AgendaRangeScope? = null,
    openScheduleId: String? = null,
    stoppedEntryIds: List<String>? = null,
    loading: Boolean = false,
    loadError: String? = null,
): AgendaScreenState {
    val planningDay = state.planningDate(now, zone)
    val tomorrow = planningDay.plusDays(1)
    val availableDates = (state.schedules.mapNotNull { runCatching { LocalDate.parse(it.planningDate) }.getOrNull() } + planningDay)
        .distinct()
        .sorted()
        .map { date -> AgendaDateOption(date, "规划日 $date") }
    val targetTitles = state.targets.associate { it.id to it.title }
    val canStartTimer = state.activeTimer == null
    val runningEntryId = state.timingScheduleEntryId()
    fun row(entry: ScheduleEntry) = agendaRow(state, entry, targetTitles, zone, canStartTimer, runningEntryId)
    // 时间轴分组、每天的容量与空态都来自领域结果；页面只把它们读成行与标签
    val selectedTimeline = if (selectedDate == null) null else state.agendaTimeline(selectedDate)
    // 所选规划日上的时间记录只筛一次：列表与合计读的是同一份，否则两处可能落在不同的数据上。
    val selectedTimeEntries = selectedDate
        ?.let { date -> state.timeEntries.filter { it.planningDate == date.toString() } }
        .orEmpty()
    return AgendaScreenState(
        planningDate = planningDay,
        selectedDate = selectedDate,
        headingLabel = if (selectedDate == null) "所有日期" else "规划日 $selectedDate",
        filterLabel = if (selectedDate == null) "筛选：所有日期" else "筛选：规划日 $selectedDate",
        availableDates = availableDates,
        selectedCapacityLabel = selectedDate?.let { date ->
            capacitySummaryLabel(state.capacitySummary(date.toString()))
        },
        workbenchSummary = AgendaWorkbenchSummary(
            planningDateLabel = if (selectedDate == null) "所有日期" else "规划日 $selectedDate",
            boundaryLabel = "规划日界线 " + formatAgendaMinute(state.settings.planningDayBoundaryMinutes),
            weekStartLabel = "周起始日 " + weekDayLabel(state.settings.weekStartDay),
            capacitySettingLabel = state.settings.dailyCapacityMinutes
                ?.let { "每日容量 $it 分钟" }
                ?: "每日容量未设置",
            capacityUsageLabel = selectedDate?.let { date ->
                capacitySummaryLabel(state.capacitySummary(date.toString()))
            },
        ),
        workbenchEmptyLabel = if (selectedTimeline != null && selectedTimeline.timed.isEmpty() && selectedTimeline.unscheduled.isEmpty()) {
            "安排今天第一件事"
        } else {
            null
        },
        timelineRows = selectedTimeline?.timed?.map(::row).orEmpty(),
        unscheduledRows = selectedTimeline?.unscheduled?.map(::row).orEmpty(),
        dateSections = if (selectedDate != null) {
            emptyList()
        } else {
            state.agendaDayTimelines().map { day ->
                AgendaDateSection(
                    planningDate = day.planningDate,
                    capacityLabel = capacitySummaryLabel(state.capacitySummary(day.planningDate)),
                    timelineRows = day.timeline.timed.map(::row),
                    unscheduledRows = day.timeline.unscheduled.map(::row),
                )
            }
        },
        emptyLabel = when {
            selectedTimeline != null && (selectedTimeline.timed.isNotEmpty() || selectedTimeline.unscheduled.isNotEmpty()) -> null
            selectedTimeline == null && state.schedules.isNotEmpty() -> null
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
        ranges = listOf(
            AgendaRangeOption(AgendaRangeScope.TODAY, "今天", planningDay, selectedScope == AgendaRangeScope.TODAY),
            AgendaRangeOption(AgendaRangeScope.TOMORROW, "明天", tomorrow, selectedScope == AgendaRangeScope.TOMORROW),
            AgendaRangeOption(AgendaRangeScope.ALL_DATES, "所有日期", null, selectedScope == AgendaRangeScope.ALL_DATES),
        ),
        highlightAddSchedule = selectedScope == AgendaRangeScope.TOMORROW,
        addScheduleHeadingLabel = if (selectedScope == AgendaRangeScope.TOMORROW) {
            // 标题里的日期取自真正在展示的那一天，跨过规划日界线后两者不会各说各话
            "为明天（$selectedDate）添加日程"
        } else {
            "新增日程"
        },
        addScheduleDefaultDate = (selectedDate ?: planningDay).toString(),
        addScheduleDefaultDateLabel = "规划日 ${selectedDate ?: planningDay}",
        quickActions = quickActionOptions(),
        freeTimerOptions = freeTimerOptions(
            targets = state.targets.filterNot { it.archived }.map { it.id to it.title },
            hasRunningTimer = state.activeTimer != null,
            activeTargetId = state.activeTimer?.targetId,
        ),
        activeTimer = state.activeTimer?.let { timer ->
            val elapsedMinutes = (timer.elapsedMillis(now) / 60_000L).toInt()
            AgendaActiveTimer(
                targetLabel = state.timerTargetLabel(timer.targetId),
                targetId = timer.targetId,
                paused = timer.paused,
                elapsedMinutes = elapsedMinutes,
                elapsedLabel = "已投入 $elapsedMinutes 分钟",
                statusLabel = if (timer.paused) "已暂停" else "计时中",
                // 暂停不是保存：这行提示必须同时说清「还没记入」与「下一步做什么」。
                pendingHintLabel = if (timer.paused) {
                    "已投入的 $elapsedMinutes 分钟尚未保存：恢复继续计时，或结束并保存后计入实际投入。"
                } else {
                    null
                },
            )
        },
        timeRecords = if (selectedDate == null) {
            emptyList()
        } else {
            selectedTimeEntries.map { entry ->
                AgendaTimeRecord(
                    entryId = entry.id,
                    durationMinutes = entry.durationMinutes,
                    targetLabel = state.targets.firstOrNull { it.id == entry.targetId }?.title ?: "未归属活动",
                    targetId = entry.targetId,
                    source = entry.source,
                    note = entry.note,
                )
            }
        },
        stopResult = stoppedEntryIds
            ?.takeIf { it.isNotEmpty() }
            ?.let { ids -> stopResult(state, ids) },
        recordedMinutesLabel = if (selectedDate == null) {
            ""
        } else {
            "本规划日已记录：" + "${selectedTimeEntries.sumOf { it.durationMinutes }} 分钟"
        },
        loading = loading,
        loadError = loadError,
    )
}

/**
 * 一条日程条目行。
 *
 * 计划投入来自领域的 `plannedMinutes()`（预计时长优先，回退到完整时间段），实际投入来自
 * `scheduleInvestedMinutes()`（所有 `scheduleEntryId` 指向它的记录之和），完成状态来自
 * `isScheduleInvestmentMet()` 与条目自身的完成标记，超载来自 `exceedsDailyCapacity()`。
 * 页面拿到的都是结论，不再自己比较分钟数——因此预计时长永远不会被当成实际投入展示。
 */
private fun agendaRow(
    state: AttentionState,
    entry: ScheduleEntry,
    targetTitles: Map<String, String>,
    zone: ZoneId,
    canStartTimer: Boolean,
    runningEntryId: String?,
): AgendaScheduleRow {
    val start = entry.startMinute
    val end = entry.endMinute
    val time = if (start != null && end != null) " · ${formatAgendaMinute(start)}-${formatAgendaMinute(end)}" else ""
    val targetTitle = entry.targetId?.let(targetTitles::get)
    val target = targetTitle?.let { " · $it" } ?: ""
    val reminder = entry.reminderEpochMillis?.let { " · 提醒 ${formatAgendaReminderEpoch(it, zone)}" } ?: " · 无提醒"
    val planned = entry.plannedMinutes()
    val invested = state.scheduleInvestedMinutes(entry.id)
    val investmentMet = state.isScheduleInvestmentMet(entry.id)
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
        investmentLabel = "计划投入 " + (planned?.let { "$it 分钟" } ?: "未设置") +
            " · 实际投入 $invested 分钟" +
            " · " + when {
                entry.completed || investmentMet -> "已完成"
                else -> "未完成"
            },
        targetLabel = targetTitle,
        capacityNotice = if (state.exceedsDailyCapacity(entry)) "已超出容量" else null,
        timing = entry.id == runningEntryId,
        completed = entry.completed || investmentMet,
    )
}

/**
 * 从领域状态读出「刚刚停止的那一笔」。
 *
 * 面板的每一项都是领域结论而不是页面推算：
 * 实际投入是这几笔记录之和（跨规划日按笔呈现，**不**自行归并日期），
 * 计划时长来自 `plannedMinutes()`，完成结果来自条目标记与 `isScheduleInvestmentMet`，
 * 记录归属来自这笔投入自己的 `targetId`。
 *
 * id 已经不在状态里（记录被删除）时返回 null：面板不凭一个失效的 id 重建已消失的事实。
 */
private fun stopResult(state: AttentionState, stoppedEntryIds: List<String>): AgendaStopResult? {
    val byId = state.timeEntries.associateBy { it.id }
    // 按调用方给出的顺序读取，而不是按状态里的存储顺序，用户看到的顺序与命令产出的顺序一致。
    val records = stoppedEntryIds.mapNotNull(byId::get)
    if (records.isEmpty()) return null
    // 计时可能被切成多笔，归属不同时（切换过目标）如实按第一笔呈现，不合并出第三个答案。
    val attribution = state.targets.firstOrNull { it.id == records.first().targetId }?.title ?: "未归属活动"
    val scheduleEntryId = records.first().scheduleEntryId
    val schedule = scheduleEntryId?.let { id -> state.schedules.firstOrNull { it.id == id } }
    val planned = schedule?.plannedMinutes()
    val completed = schedule?.completed == true ||
        (scheduleEntryId != null && state.isScheduleInvestmentMet(scheduleEntryId))
    val actualMinutes = records.sumOf { it.durationMinutes }
    return AgendaStopResult(
        scheduleEntryId = scheduleEntryId,
        records = records.map { record ->
            AgendaStoppedRecord(
                entryId = record.id,
                planningDate = record.planningDate,
                minutes = record.durationMinutes,
                label = "${record.planningDate} · ${record.durationMinutes} 分钟",
            )
        },
        actualMinutes = actualMinutes,
        actualLabel = "实际投入 $actualMinutes 分钟",
        plannedMinutes = planned,
        plannedLabel = planned?.let { "计划时长 $it 分钟" } ?: "未设置计划时长",
        completed = completed,
        completionLabel = if (completed) "已完成" else "未完成",
        // 只有「有承诺且尚未达标」才是用户必须决定的时刻；没有计划或已经完成都不是。
        needsCompletionDecision = planned != null && !completed,
        attributionLabel = attribution,
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

/**
 * 工作台 `+` 菜单的两个主要动作。它们是投影的一部分，因此页面上的菜单项、顺序与
 * 文案都由 [AgendaScreenState] 给出，页面没有自己的副本。
 */
fun quickActionOptions(): List<AgendaQuickActionOption> = listOf(
    AgendaQuickActionOption(AgendaQuickAction.ADD_SCHEDULE, "添加日程"),
    AgendaQuickActionOption(AgendaQuickAction.START_FREE_TIMER, "开始自由计时"),
)

/**
 * 自由计时的可选项：未归属活动排在最前，后面是未归档计划。
 *
 * 选中态由「是否真的有计时在进行」与 [activeTargetId] 一起得出：没有计时在跑时
 * 没有一个目标是当前目标，页面因此不用自己读运行中的计时，也不用颜色表达
 * 「现在计的是哪一个」。
 */
fun freeTimerOptions(
    targets: List<Pair<String, String>>,
    hasRunningTimer: Boolean,
    activeTargetId: String?,
): List<AgendaFreeTimerOption> = buildList {
    fun selected(targetId: String?): Boolean = hasRunningTimer && targetId == activeTargetId
    add(AgendaFreeTimerOption(null, "未归属活动", selected = selected(null)))
    targets.forEach { (targetId, title) ->
        add(AgendaFreeTimerOption(targetId, title, selected = selected(targetId)))
    }
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

fun formatAgendaMinute(value: Int): String = formatMinuteOfDay(value)

fun formatAgendaReminderEpoch(epochMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).let { formatAgendaMinute(it.hour * 60 + it.minute) }