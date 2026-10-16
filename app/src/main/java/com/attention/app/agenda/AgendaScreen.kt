package com.attention.app.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionConfirmDialog
import com.attention.app.ui.components.AttentionErrorState
import com.attention.app.ui.components.AttentionInlineError
import com.attention.app.ui.components.AttentionLoadingState
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.components.AttentionSelectedIndicator
import com.attention.app.ui.components.AttentionTextButton
import com.attention.app.ui.format.DurationFormat
import com.attention.app.ui.format.TimeEntrySourceFormat
import com.attention.app.ui.theme.attentionTokens
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.workspace.state.AgendaActions
import com.attention.workspace.state.AgendaActiveTimer
import com.attention.workspace.state.AgendaFreeTimerOption
import com.attention.workspace.state.AgendaQuickAction
import com.attention.workspace.state.AgendaQuickActionOption
import com.attention.workspace.state.AgendaRangeScope
import com.attention.workspace.state.AgendaScheduleDraft
import com.attention.workspace.state.AgendaScreenState
import com.attention.workspace.state.AgendaScheduleRow
import com.attention.workspace.state.AgendaStopResult
import com.attention.workspace.state.AgendaTargetOption
import com.attention.workspace.state.AgendaTimeRecord
import com.attention.workspace.state.AgendaWorkbenchSection
import com.attention.workspace.state.AGENDA_RETRY_LABEL
import com.attention.workspace.state.agendaLabel
import com.attention.workspace.state.agendaWeekdayLabel
import java.time.LocalDate

/**
 * 选中明天时「添加日程」入口旁的说明文案。
 *
 * 它回答的是「为什么突出」而不是「做什么」：做与为哪一天由按钮标题（投影里的
 * `addScheduleHeadingLabel`）说出，因此这一行不重复那一句。
 */
private const val AGENDA_ADD_HIGHLIGHT_HINT = "切换规划日只换这一天：今天已有的日程不会带过来"

/**
 * 日程页。它只接收自己的 [AgendaScreenState] 与 [AgendaActions]：
 * 不读完整领域状态，不读 ViewModel，也不读 Android API 与系统时间——
 * 规划日、日程条目、每日容量与提醒时间都由组合根注入的投影算好。
 *
 * 原来的今天工作台与日程页在这一页合并：今天、明天、所有日期与任意规划日
 * 是同一页上的日期范围，共享同一套列表、编辑、完成、提醒与从条目开始计时的交互。
 *
 * ## 首屏顺序
 *
 * 页面按投影给出的 [AgendaScreenState.workbenchSections] 自上而下渲染：
 * 规划日与每日容量摘要、时间轴、进行中的计时状态、添加日程入口、事后补记，
 * 重复日程作为折叠的次级入口排在最后。页面因此没有自己的顺序副本，
 * 「首屏固定为这个顺序」是一条由投影与测试共同钉住的约定。
 *
 * 提醒权限不在这里申请：页面只发出 [AgendaActions.requestReminderPermission] intent，
 * 权限检查与申请由组合根 adapter 完成（ADR-0005 第 4 节「Android adapter 归属组合根」）。
 */
@Composable
internal fun AgendaScreen(
    state: AgendaScreenState,
    actions: AgendaActions,
    openScheduleId: String? = null,
) {
    val tokens = attentionTokens()
    val today = state.planningDate.toString()
    var date by remember(state.selectedDate) { mutableStateOf(state.selectedDate?.toString() ?: today) }
    var dateMenuExpanded by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    val scheduleForm = rememberAgendaScheduleForm()
    val recurrenceForm = rememberAgendaRecurrenceForm()
    // 重复日程折叠在页面 module 内：它是次级入口，能力没有删除，只是收在一次点击之后。
    val disclosure = rememberAgendaDisclosureState()
    val scrollState = rememberScrollState()
    var quickMenuExpanded by remember { mutableStateOf(false) }
    var addSheetOpen by remember { mutableStateOf(false) }
    var freeTimerSheetOpen by remember { mutableStateOf(false) }
    var addMoreExpanded by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<AgendaScheduleRow?>(null) }
    var editingRecord by remember { mutableStateOf<AgendaTimeRecord?>(null) }
    var pendingDeleteRecordId by remember { mutableStateOf<String?>(null) }
    var notificationOpenHandled by remember(openScheduleId) { mutableStateOf(false) }
    LaunchedEffect(openScheduleId, state.pendingEditRow) {
        if (!notificationOpenHandled && state.pendingEditRow != null) {
            editingRow = state.pendingEditRow
            notificationOpenHandled = true
        }
    }
    // 面板里的规划日默认落在正在看的那一天：投影已经算好，页面不自己判断「哪一天」。
    val sheetDate = state.addScheduleDefaultDate.ifBlank { date }
    fun selectDate(value: LocalDate?) {
        actions.selectPlanningDate(value)
        date = value?.toString() ?: today
        dateError = false
    }
    fun openPlanningDay() {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
        if (parsed == null) dateError = true else selectDate(parsed)
    }
    fun closeAddSheet() {
        addSheetOpen = false
        addMoreExpanded = false
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(tokens.spacing.large),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 「+」菜单是首屏的主入口，它不依赖本地数据，因此读取期间也照样在。
        AgendaQuickAddMenu(
            options = state.quickActions,
            expanded = quickMenuExpanded,
            onExpandedChange = { quickMenuExpanded = it },
            onAddSchedule = { quickMenuExpanded = false; addMoreExpanded = false; addSheetOpen = true },
            onStartFreeTimer = { quickMenuExpanded = false; addSheetOpen = false; freeTimerSheetOpen = true },
        )
        // 读取本地数据的两件事都是组合根注入的事实，页面只按它们渲染（ADR-0005 第 1 节）：
        // 还没读到给轻量时间轴骨架，读失败给带「重试」的错误状态。两者都不重排区块——
        // 骨架复用同一批区块容器标识，因此首屏结构与顺序在三种状态下始终一致。
        val loadError = state.loadError
        if (loadError != null) {
            AgendaLoadFailure(loadError = loadError, onRetry = actions::retryLoad)
        } else if (state.loading) {
            AgendaLoadingSkeleton()
        } else {
            state.workbenchSections.forEach { section ->
                when (section) {
                AgendaWorkbenchSection.PLANNING_DAY_SUMMARY -> AgendaSummarySection(
                    state = state,
                    date = date,
                    dateError = dateError,
                    onDateChange = { value -> date = value; dateError = false },
                    onOpenPlanningDay = ::openPlanningDay,
                    onSelectDate = ::selectDate,
                    dateMenuExpanded = dateMenuExpanded,
                    onDateMenuChange = { dateMenuExpanded = it },
                    actions = actions,
                )

                AgendaWorkbenchSection.TIMELINE -> AgendaTimelineSection(
                    state = state,
                    actions = actions,
                    onEditRow = { editingRow = it },
                    // 空态直接给出添加日程入口：表单住在底部面板里，这里是把它打开。
                    onAddFirstSchedule = { addMoreExpanded = false; addSheetOpen = true },
                )

                AgendaWorkbenchSection.RUNNING_TIMER -> state.activeTimer?.let { timer ->
                    Column(Modifier.fillMaxWidth().testTag("agenda-section-timer")) {
                        AgendaRunningTimerSection(timer = timer, targets = state.targets, actions = actions)
                    }
                }

                AgendaWorkbenchSection.ADD_SCHEDULE -> {
                    Column(Modifier.fillMaxWidth().testTag("agenda-section-add")) {
                        AgendaAddScheduleSection(state = state, onOpen = { addMoreExpanded = false; addSheetOpen = true })
                    }
                }

                AgendaWorkbenchSection.BACKFILL -> AgendaBackfillSection(
                    state = state,
                    editingRecord = editingRecord,
                    onEditRecord = { editingRecord = it },
                    onStopEditingRecord = { editingRecord = null },
                    onDeleteRecord = { pendingDeleteRecordId = it },
                    actions = actions,
                )

                AgendaWorkbenchSection.RECURRENCE -> AgendaRecurrenceSection(
                    state = state,
                    form = recurrenceForm,
                    expanded = disclosure.recurrenceVisible,
                    onToggle = disclosure::toggleRecurrence,
                    date = date,
                    actions = actions,
                )
                }
            }
        }
        // 停止计时的结果面板：只在刚刚停止之后出现，四个要素与两个决定都来自投影。
        state.stopResult?.let { result ->
            AgendaStopResultSection(result = result, actions = actions)
        }
    }
    state.timeRecords.firstOrNull { it.entryId == pendingDeleteRecordId }?.let { pendingDelete ->
        // 删除实际投入是破坏性操作，先确认影响；撤销入口由组合根的提示宿主提供。
        AttentionConfirmDialog(
            visible = true,
            title = "删除这条实际投入",
            impact = "删除后这 ${DurationFormat.ofMinutes(pendingDelete.durationMinutes)} 的实际投入不再计入目标进度、投入经验与统计。" +
                "删除后仍可以在底部提示里撤销一次。",
            confirmLabel = "删除",
            onConfirm = {
                actions.deleteTime(pendingDelete.entryId)
                pendingDeleteRecordId = null
                if (editingRecord?.entryId == pendingDelete.entryId) editingRecord = null
            },
            onDismiss = { pendingDeleteRecordId = null },
        )
    }
    editingRow?.let { row ->
        AgendaScheduleEditDialog(
            row = row,
            targets = state.targets.map { it.targetId to it.title },
            onDismiss = { editingRow = null },
            onSave = { updated, reminderMinute, reminderRequested ->
                if (reminderRequested) actions.requestReminderPermission()
                actions.updateSchedule(updated, reminderMinute)
                editingRow = null
            },
        )
    }
    // 两块底部面板都是 Material 3 的 sheet：表单控件与选择器都在页面 module 内，
    // 页面因此不需要任何 Android API（ADR-0005 第 4 节）。
    AgendaAddScheduleSheet(
        state = state,
        form = scheduleForm,
        visible = addSheetOpen,
        initialPlanningDay = sheetDate,
        moreExpanded = addMoreExpanded,
        onToggleMore = { addMoreExpanded = !addMoreExpanded },
        onDismiss = ::closeAddSheet,
        onSubmit = { draft, reminderRequested ->
            actions.addSchedule(draft)
            if (reminderRequested) actions.requestReminderPermission()
            closeAddSheet()
        },
    )
    AgendaFreeTimerSheet(
        options = state.freeTimerOptions,
        visible = freeTimerSheetOpen,
        onStart = { targetId ->
            actions.startFreeTimer(targetId)
            freeTimerSheetOpen = false
        },
        onDismiss = { freeTimerSheetOpen = false },
    )
}

/**
 * 工作台统一的 `+` 入口。菜单里的动作由投影给出，页面只按动作打开对应的那一块面板。
 *
 * 入口本身带可读标签（按钮文字与图标说明），因此读屏用户不靠图标也知道这里能做什么。
 */
@Composable
private fun AgendaQuickAddMenu(
    options: List<AgendaQuickActionOption>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddSchedule: () -> Unit,
    onStartFreeTimer: () -> Unit,
) {
    val tokens = attentionTokens()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        AttentionOutlinedButton(
            onClick = { onExpandedChange(true) },
            modifier = Modifier.testTag("agenda-quick-add"),
        ) { Text("＋") }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            options.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = when (action.action) {
                        AgendaQuickAction.ADD_SCHEDULE -> onAddSchedule
                        AgendaQuickAction.START_FREE_TIMER -> onStartFreeTimer
                    },
                    modifier = Modifier.testTag(
                        when (action.action) {
                            AgendaQuickAction.ADD_SCHEDULE -> "agenda-quick-add-schedule"
                            AgendaQuickAction.START_FREE_TIMER -> "agenda-quick-add-free-timer"
                        },
                    ),
                )
            }
        }
    }
}

/**
 * 首次读取本地数据时的轻量时间轴骨架。
 *
 * 读取期间 [AgendaScreenState] 里的行与标签都还是空投影，因此这里画的不是「提前渲染真实区块」，
 * 而是复用同一批区块容器标识的占位：用户看到的位置与读上来之后一致，页面的高度不会在
 * 加载结束时突然跳一下。首屏区块的顺序语义没有被改动，骨架只是同一结构的一种填充状态。
 */
@Composable
private fun AgendaLoadingSkeleton() {
    val tokens = attentionTokens()
    Column(
        Modifier.fillMaxWidth().testTag("agenda-skeleton"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AttentionLoadingState(
            label = "正在读取本地数据",
            description = "马上就好",
        )
        // 容量摘要与时间轴两块在读上来之后就在这个位置，因此骨架占住同样的容器标识。
        Column(Modifier.fillMaxWidth().testTag("agenda-section-summary"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { AgendaSkeletonBar(tokens.spacing.medium) }
        }
        Column(Modifier.fillMaxWidth().testTag("agenda-section-timeline"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { AgendaSkeletonBar(tokens.spacing.large) }
        }
    }
}

/** 一条骨架占位行。它不带任何文案，因此读屏用户听到的是加载状态而不是一串假内容。 */
@Composable
private fun AgendaSkeletonBar(height: androidx.compose.ui.unit.Dp) {
    val tokens = attentionTokens()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .testTag("agenda-skeleton-row"),
        shape = RoundedCornerShape(tokens.radius.small),
        color = tokens.colorScheme.surfaceContainerHigh,
        content = {},
    )
}

/**
 * 本地数据读取失败时的错误状态。
 *
 * 它复用共享的 [AttentionErrorState]，因此错误在任意页面都是同一套标识与同一套表达；
 * 动作是「重试」而不是只有关闭——用户自己能救回来，不必退出应用（#195 验收标准 8）。
 */
@Composable
private fun AgendaLoadFailure(loadError: String, onRetry: () -> Unit) {
    AttentionErrorState(
        description = loadError,
        retryLabel = AGENDA_RETRY_LABEL,
        onRetry = onRetry,
    )
}

/**
 * 首屏第一块：规划日与每日容量的只读摘要，外加切换查看范围的导航。
 *
 * 它只读不写——完整设置继续走统计页底部的「设置与数据」，因此这里没有任何设置表单。
 * 容量摘要本身是那个入口：点它发出 [AgendaActions.openSettings] 意图，实际跳转由组合根完成。
 */
@Composable
private fun AgendaSummarySection(
    state: AgendaScreenState,
    date: String,
    dateError: Boolean,
    onDateChange: (String) -> Unit,
    onOpenPlanningDay: () -> Unit,
    onSelectDate: (LocalDate?) -> Unit,
    dateMenuExpanded: Boolean,
    onDateMenuChange: (Boolean) -> Unit,
    actions: AgendaActions,
) {
    val tokens = attentionTokens()
    val summary = state.workbenchSummary
    Column(Modifier.fillMaxWidth().testTag("agenda-section-summary"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(state.headingLabel, style = tokens.typography.display, modifier = Modifier.testTag("agenda-filter"))
        Text("日程条目可带预计时长；它不会自动变成实际投入。")
        // 日期范围切换：今天、明天与所有日期是同一页上的三个范围，不是三个顶级入口。
        // 选中态用填充色之外的图标与语义表达，不依赖颜色单独传达。
        if (state.ranges.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.ranges.forEach { range ->
                    val selected = range.selected
                    val tag = when (range.scope) {
                        AgendaRangeScope.TODAY -> "agenda-range-today"
                        AgendaRangeScope.TOMORROW -> "agenda-range-tomorrow"
                        AgendaRangeScope.ALL_DATES -> "agenda-range-all-dates"
                    }
                    if (selected) {
                        AttentionSelectedIndicator(
                            selected = true,
                            label = range.label,
                            modifier = Modifier.testTag(tag),
                        )
                    } else {
                        AttentionTextButton(
                            onClick = {
                                if (range.scope == AgendaRangeScope.ALL_DATES) actions.selectAllDates()
                                else range.date?.let(actions::selectPlanningDate)
                            },
                            modifier = Modifier.testTag(tag),
                        ) { Text(range.label) }
                    }
                }
            }
        }
        Box {
            AttentionOutlinedButton(onClick = { onDateMenuChange(true) }, modifier = Modifier.testTag("agenda-date-menu")) {
                Text(state.filterLabel)
            }
            DropdownMenu(expanded = dateMenuExpanded, onDismissRequest = { onDateMenuChange(false) }) {
                DropdownMenuItem(
                    text = { Text("所有日期") },
                    onClick = { onSelectDate(null); onDateMenuChange(false) },
                    modifier = Modifier.testTag("agenda-all-dates"),
                )
                state.availableDates.forEach { availableDate ->
                    DropdownMenuItem(
                        text = { Text(availableDate.label) },
                        onClick = { onSelectDate(availableDate.date); onDateMenuChange(false) },
                        modifier = Modifier.testTag("agenda-date-${availableDate.date}"),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                date,
                onDateChange,
                label = { Text("新增日程规划日 YYYY-MM-DD") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = dateError,
                supportingText = if (dateError) ({ Text("请输入有效的规划日") }) else null,
            )
            AttentionOutlinedButton(onClick = onOpenPlanningDay) { Text("打开规划日") }
        }
        Card(
            Modifier
                .fillMaxWidth()
                // 容量摘要只读，但它是通往完整设置的入口：设置住在统计页底部的「设置与数据」，
                // 工作台不复制一份设置表单，也不自己跳转，只发出这一条意图。
                .clickable(onClick = actions::openSettings)
                .testTag("agenda-open-settings"),
            shape = RoundedCornerShape(tokens.radius.medium),
        ) {
            Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 摘要不复述筛选器的标题：正在查看哪一天已经由页首标题与筛选按钮给出，
                // 这里只讲事实读数——容量怎么算、这一范围已经用掉多少。
                Text("容量与设置读数", style = tokens.typography.body)
                Text(summary.boundaryLabel, modifier = Modifier.testTag("agenda-planning-day-boundary"))
                Text(summary.weekStartLabel, modifier = Modifier.testTag("agenda-week-start"))
                Text(summary.capacitySettingLabel, modifier = Modifier.testTag("agenda-capacity-setting"))
                summary.capacityUsageLabel?.let { capacityLabel ->
                    Text(capacityLabel, modifier = Modifier.testTag("agenda-capacity-summary"))
                } ?: Text(
                    // 所有日期不是一个范围，不存在可加总的单日读数；按天读数在时间轴各分区内。
                    "按天读数见下方时间轴各规划日分区。",
                )
                // 完整设置在统计页底部的「设置与数据」；这里只给出路，不复制一份表单。
                Text("打开设置与数据", style = tokens.typography.caption)
            }
        }
    }
}

/**
 * 首屏第二块：时间轴。有具体时间的日程按开始/结束时间排列，正在计时的固定在最前；
 * 没有具体时间的日程进入独立的「未安排时间」区域。当天没有日程时给出「安排今天第一件事」。
 *
 * 顺序、行上的计划投入与实际投入、以及「已超出容量」提示都来自投影，页面只按给定顺序渲染。
 */
@Composable
private fun AgendaTimelineSection(
    state: AgendaScreenState,
    actions: AgendaActions,
    onEditRow: (AgendaScheduleRow) -> Unit,
    onAddFirstSchedule: () -> Unit,
) {
    val tokens = attentionTokens()
    Column(Modifier.fillMaxWidth().testTag("agenda-section-timeline"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("时间轴", style = tokens.typography.title, modifier = Modifier.testTag("agenda-timeline"))
        state.emptyLabel?.let { emptyLabel ->
            Text(emptyLabel, modifier = Modifier.testTag("agenda-empty"))
        }
        if (state.dateSections.isEmpty()) {
            if (state.timelineRows.isEmpty() && state.unscheduledRows.isEmpty()) {
                state.workbenchEmptyLabel?.let { emptyLabel ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("agenda-workbench-empty")) {
                        Text(emptyLabel)
                        AttentionButton(onClick = onAddFirstSchedule, modifier = Modifier.testTag("agenda-first-schedule")) {
                            Text("添加日程")
                        }
                    }
                }
            }
            AgendaTimelineRows(state.timelineRows, actions, onEditRow)
            AgendaUnscheduledGroup(state.unscheduledRows, actions, onEditRow)
        } else {
            state.dateSections.forEach { section ->
                Text("规划日 ${section.planningDate}", style = tokens.typography.title)
                Text(section.capacityLabel, modifier = Modifier.testTag("agenda-capacity-summary-${section.planningDate}"))
                AgendaTimelineRows(section.timelineRows, actions, onEditRow)
                AgendaUnscheduledGroup(section.unscheduledRows, actions, onEditRow)
            }
        }
    }
}

@Composable
private fun AgendaTimelineRows(
    rows: List<AgendaScheduleRow>,
    actions: AgendaActions,
    onEditRow: (AgendaScheduleRow) -> Unit,
) {
    rows.forEach { row ->
        AgendaScheduleRowCard(row, actions = actions, onEdit = { onEditRow(row) })
    }
}

/** 没有具体时间的日程。它们仍然属于这一天，只是不假装有一个固定时段。 */
@Composable
private fun AgendaUnscheduledGroup(
    rows: List<AgendaScheduleRow>,
    actions: AgendaActions,
    onEditRow: (AgendaScheduleRow) -> Unit,
) {
    if (rows.isEmpty()) return
    val tokens = attentionTokens()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("agenda-unscheduled")) {
        Text("未安排时间", style = tokens.typography.title, modifier = Modifier.testTag("agenda-unscheduled-heading"))
        rows.forEach { row ->
            AgendaScheduleRowCard(row, actions = actions, onEdit = { onEditRow(row) })
        }
    }
}

/**
 * 首屏第三块：进行中的计时状态。
 *
 * 计时卡下线之后，运行中的计时仍然必须看得见并停得掉；正在计时的那条日程由时间轴
 * 内部置顶强调（见 [AgendaTimelineSection]），这里给出的是状态与控制。
 * 暂停、恢复、结束并保存是首屏仅有的两个主控制；切换目标与从此刻继续收在更多菜单里，
 * 低频动作因此不占首屏。暂停不等于已保存，所以暂停态要明说下一步。
 */
@Composable
private fun AgendaRunningTimerSection(
    timer: AgendaActiveTimer,
    targets: List<AgendaTargetOption>,
    actions: AgendaActions,
) {
    var timerMoreExpanded by remember(timer.targetId) { mutableStateOf(false) }
    val tokens = attentionTokens()
    val rust = tokens.brand.timingInProgress
    Card(
        modifier = Modifier.fillMaxWidth().testTag("agenda-running-timer"),
        shape = RoundedCornerShape(tokens.radius.medium),
        colors = CardDefaults.cardColors(containerColor = rust.container),
    ) {
        Column(
            Modifier.padding(tokens.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 状态不只靠颜色：色块里的图标与旁边的文字都说出计时中 / 已暂停。
                Box(
                    Modifier
                        .background(rust.container, RoundedCornerShape(tokens.radius.small))
                        .padding(tokens.spacing.small),
                ) {
                    Icon(
                        imageVector = if (timer.paused) Icons.Filled.PauseCircle else Icons.Filled.Timer,
                        // 图标本身也带语义：读屏用户不靠颜色也知道现在是暂停还是运行。
                        contentDescription = timer.statusLabel,
                        tint = rust.onContainer,
                        // 强调标记与它的状态语义放在同一个节点上：非合并语义树里父节点
                        // 不继承子节点的 contentDescription，标记挂在容器上时读屏会丢掉状态。
                        modifier = Modifier.size(tokens.spacing.medium).testTag("agenda-running-timer-tint"),
                    )
                }
                Column(Modifier.weight(1f)) {
                    // 「正在计时：<目标>」是既有宿主验收用例与读屏都认的文案，保持原样。
                    Text("正在计时：${timer.targetLabel}", style = tokens.typography.title)
                    Text(timer.statusLabel, modifier = Modifier.testTag("agenda-running-timer-status"))
                }
            }
            Text(timer.elapsedLabel, modifier = Modifier.testTag("agenda-running-timer-elapsed"))
            // 暂停不等于已保存：这一行告诉用户下一步该走恢复还是结束并保存。
            timer.pendingHintLabel?.let { hint ->
                Text(hint, modifier = Modifier.testTag("agenda-running-timer-hint"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (timer.paused) {
                    AttentionButton(
                        onClick = actions::resumeRunningTimer,
                        modifier = Modifier.testTag("agenda-running-timer-resume"),
                    ) { Text("恢复计时") }
                } else {
                    AttentionOutlinedButton(
                        onClick = actions::pauseRunningTimer,
                        modifier = Modifier.testTag("agenda-running-timer-pause"),
                    ) { Text("暂停") }
                }
                AttentionOutlinedButton(
                    onClick = actions::stopRunningTimer,
                    modifier = Modifier.testTag("agenda-running-timer-stop"),
                ) { Text("结束并保存") }
                // 次级操作收在这里：首屏只留暂停 / 恢复与结束并保存。
                Box {
                    AttentionTextButton(
                        onClick = { timerMoreExpanded = true },
                        modifier = Modifier.testTag("agenda-running-timer-more"),
                    ) { Text("更多") }
                    DropdownMenu(
                        expanded = timerMoreExpanded,
                        onDismissRequest = { timerMoreExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("从此刻继续") },
                            onClick = { actions.recoverRunningTimerFromNow(); timerMoreExpanded = false },
                            modifier = Modifier.testTag("agenda-running-timer-recover"),
                        )
                        DropdownMenuItem(
                            text = { Text("切换到「未归属活动」") },
                            onClick = { actions.switchRunningTimerTarget(null); timerMoreExpanded = false },
                            modifier = Modifier.testTag("agenda-running-timer-switch-unowned"),
                        )
                        // 当前目标同样用文字标出，不靠高亮颜色单独表达。
                        targets.forEach { target ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "切换到「${target.title}」" +
                                            if (target.targetId == timer.targetId) "（当前）" else "",
                                    )
                                },
                                onClick = { actions.switchRunningTimerTarget(target.targetId); timerMoreExpanded = false },
                                modifier = Modifier.testTag("agenda-running-timer-switch-${target.targetId}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 停止计时的结果面板。
 *
 * 四个要素（实际投入、计划时长、完成结果、记录归属）与「是否达标」都已经由投影给出，
 * 页面不比较任何分钟数，也不自己判断该不该完成。它只做两件事：把事实讲清楚，
 * 以及在「有计划且未达标」时把两个决定摆出来。
 *
 * 跨规划日的那次停止会产出多笔，面板逐笔如实呈现，不自行合成一笔。
 * 完成状态同时用文字与图标表达，不依赖颜色单独传达。
 */
@Composable
private fun AgendaStopResultSection(
    result: AgendaStopResult,
    actions: AgendaActions,
) {
    val tokens = attentionTokens()
    Card(
        Modifier.fillMaxWidth().testTag("agenda-stop-result"),
        shape = RoundedCornerShape(tokens.radius.medium),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("本次计时结果", style = tokens.typography.title)
            Text(result.actualLabel, modifier = Modifier.testTag("agenda-stop-actual"))
            result.records.forEach { record ->
                Text(record.label, modifier = Modifier.testTag("agenda-stop-record-${record.entryId}"))
            }
            Text(result.plannedLabel, modifier = Modifier.testTag("agenda-stop-planned"))
            Text("记录归属：${result.attributionLabel}", modifier = Modifier.testTag("agenda-stop-attribution"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 完成状态不只靠颜色：图标与文字一起讲清楚是不是已经完成。
                Icon(
                    if (result.completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (result.completed) "已完成" else "未完成",
                    tint = if (result.completed) tokens.colorScheme.primary else tokens.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    result.completionLabel,
                    modifier = Modifier.padding(start = tokens.spacing.small).testTag("agenda-stop-completion"),
                )
            }
            if (result.needsCompletionDecision) {
                Text(
                    "实际投入没有达到计划时长：完成与否由你决定。",
                    modifier = Modifier.testTag("agenda-stop-decision-hint"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttentionOutlinedButton(
                        onClick = actions::dismissStopResult,
                        modifier = Modifier.testTag("agenda-stop-keep-incomplete"),
                    ) { Text("结束并保留未完成") }
                    AttentionButton(
                        onClick = {
                            result.scheduleEntryId?.let(actions::markStoppedScheduleCompleted)
                            actions.dismissStopResult()
                        },
                        modifier = Modifier.testTag("agenda-stop-mark-completed"),
                    ) { Text("标记为完成") }
                }
            } else {
                AttentionTextButton(
                    onClick = actions::dismissStopResult,
                    modifier = Modifier.testTag("agenda-stop-dismiss"),
                ) { Text("知道了") }
            }
        }
    }
}

/**
 * 首屏第四块：添加日程的入口。表单本身在底部面板里（[AgendaAddScheduleSheet]），
 * 这一块只负责告诉用户「新增日程」在哪里，并把它打开。
 *
 * 选中明天时入口被突出：颜色之外还有图标与文案，说明在为哪一天安排。
 */
@Composable
private fun AgendaAddScheduleSection(
    state: AgendaScreenState,
    onOpen: () -> Unit,
) {
    val tokens = attentionTokens()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 说明行只在突出时出现，它回答的是「为什么突出」；为哪一天安排由下面按钮的标题说出。
        // 两者不再逐字重复，否则同一句话会在这一块里出现两次，页面与读屏都是。
        if (state.highlightAddSchedule) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("agenda-add-highlight"),
            ) {
                Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = "为明天安排日程", modifier = Modifier.size(tokens.spacing.medium))
                Text(AGENDA_ADD_HIGHLIGHT_HINT, style = tokens.typography.body)
            }
        }
        AttentionButton(onClick = onOpen, modifier = Modifier.testTag("agenda-open-add-sheet")) {
            Text(state.addScheduleHeadingLabel)
        }
    }
}

/**
 * 添加日程的底部表单面板。高频的添加流程被压在这一层里：面板一打开，规划日已经填好，
 * 标题与时间段、预计投入摆在一屏，备注、提醒与关联计划收在「更多设置」后面。
 *
 * 表单状态仍是页面本地状态（[AgendaScheduleForm]），提交只发一个草稿 intent；
 * 提醒时刻的绝对换算由组合根按注入的时区完成，页面因此不读系统时钟与系统时区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaAddScheduleSheet(
    state: AgendaScreenState,
    form: AgendaScheduleForm,
    visible: Boolean,
    initialPlanningDay: String,
    moreExpanded: Boolean,
    onToggleMore: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (AgendaScheduleDraft, Boolean) -> Unit,
) {
    val tokens = attentionTokens()
    if (!visible) return
    // 规划日在面板打开时就填好：默认是正在看的那一天，用户仍然可以改成别的日子。
    var planningDay by remember(initialPlanningDay) { mutableStateOf(initialPlanningDay) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("agenda-add-sheet"),
        // 面板一次展开到可滚动高度：半展开状态下「更多设置」里的次要字段落在面板可视高度
        // 以下，面板内滚动也到不了，用户同样够不到。
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = tokens.spacing.large)
                .padding(bottom = tokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(state.addScheduleHeadingLabel, style = tokens.typography.title)
            OutlinedTextField(
                planningDay,
                { planningDay = it },
                label = { Text("规划日 YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth().testTag("agenda-add-planning-day"),
                singleLine = true,
            )
            OutlinedTextField(
                form.title,
                form::changeTitle,
                label = { Text("日程标题") },
                modifier = Modifier.fillMaxWidth().testTag("agenda-title-input"),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(form.startTime, form::changeStartTime, label = { Text("开始时间 HH:mm（可选）") }, modifier = Modifier.weight(1f).testTag("agenda-start-input"), singleLine = true)
                OutlinedTextField(form.endTime, form::changeEndTime, label = { Text("结束时间 HH:mm（可选）") }, modifier = Modifier.weight(1f).testTag("agenda-end-input"), singleLine = true)
            }
            OutlinedTextField(form.estimate, form::changeEstimate, label = { Text("预计分钟") }, modifier = Modifier.fillMaxWidth().testTag("agenda-estimate-input"), singleLine = true)
            // 低频字段收在「更多设置」后面：展开状态用文字说明，标签本身告诉读屏它是什么。
            AttentionTextButton(onClick = onToggleMore, modifier = Modifier.testTag("agenda-add-more-toggle")) {
                Text(if (moreExpanded) "收起更多设置" else "更多设置（备注、提醒与关联计划）")
            }
            if (moreExpanded) {
                OutlinedTextField(form.note, form::changeNote, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth().testTag("agenda-note-input"), singleLine = true)
                OutlinedTextField(form.reminderTime, form::changeReminderTime, label = { Text("提醒 HH:mm（可选）") }, modifier = Modifier.fillMaxWidth().testTag("agenda-reminder-input"), singleLine = true)
                Box {
                    AttentionOutlinedButton(onClick = { form.toggleTargetMenu(true) }, modifier = Modifier.testTag("agenda-add-target")) {
                        Text(state.targets.firstOrNull { it.targetId == form.targetId }?.title ?: "关联计划（可选）")
                    }
                    DropdownMenu(
                        expanded = form.targetMenuExpanded,
                        onDismissRequest = { form.toggleTargetMenu(false) },
                    ) {
                        DropdownMenuItem(text = { Text("不关联计划") }, onClick = { form.selectTarget(null) })
                        state.targets.forEach { target ->
                            DropdownMenuItem(text = { Text(target.title) }, onClick = { form.selectTarget(target.targetId) })
                        }
                    }
                }
            }
            form.error?.let { AttentionInlineError(message = it) }
            AttentionButton(modifier = Modifier.testTag("agenda-add-submit"), onClick = {
                val parsedDate = runCatching { LocalDate.parse(planningDay) }.getOrNull()
                val parsedStart = parseScheduleMinute(form.startTime)
                val parsedEnd = parseScheduleMinute(form.endTime, allowEndOfDay = true)
                val estimated = form.estimate.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                val parsedReminder = parseScheduleMinute(form.reminderTime)
                val error = validateScheduleForm(
                    ScheduleFormInput(
                        title = form.title,
                        date = parsedDate,
                        startText = form.startTime,
                        endText = form.endTime,
                        estimateText = form.estimate,
                        startMinute = parsedStart,
                        endMinute = parsedEnd,
                        estimateMinutes = estimated,
                        reminderText = form.reminderTime,
                        reminderMinute = parsedReminder,
                    ),
                )
                // 校验没过就只显示错误：不发保存意图，也没有任何数据被写入。
                if (error != null) {
                    form.showError(error)
                } else {
                    onSubmit(form.toDraft(parsedDate!!), parsedReminder != null)
                    form.reset()
                }
            }) { Text("添加日程") }
        }
    }
}

/**
 * 「开始自由计时」的目标选择面板。就地挑一个未归属活动或未归档计划就开始，
 * 不需要先建一条日程——自由计时的时间记录因此没有日程来源。
 *
 * 当前正在计时的目标用文字标出，不靠高亮颜色单独表达。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaFreeTimerSheet(
    options: List<AgendaFreeTimerOption>,
    visible: Boolean,
    onStart: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = attentionTokens()
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("agenda-free-timer-sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = tokens.spacing.large)
                .padding(bottom = tokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("开始自由计时", style = tokens.typography.title)
            Text("选一个目标就开始计时：不需要先添加日程。")
            options.forEach { option ->
                val tag = option.targetId
                    ?.let { "agenda-free-timer-target-$it" }
                    ?: "agenda-free-timer-unowned"
                AttentionOutlinedButton(
                    onClick = { onStart(option.targetId) },
                    modifier = Modifier.fillMaxWidth().testTag(tag),
                ) { Text(option.label + if (option.selected) "（当前）" else "") }
            }
        }
    }
}

/**
 * 首屏最后一块：事后补记——所选规划日上已经记录的实际投入。
 *
 * 手动时间账本的编辑与删除跟着合并后的日程页走，因此这里既能补上遗漏的投入，也能更正与删除。
 */
@Composable
private fun AgendaBackfillSection(
    state: AgendaScreenState,
    editingRecord: AgendaTimeRecord?,
    onEditRecord: (AgendaTimeRecord) -> Unit,
    onStopEditingRecord: () -> Unit,
    onDeleteRecord: (String) -> Unit,
    actions: AgendaActions,
) {
    val tokens = attentionTokens()
    Column(
        Modifier.fillMaxWidth().testTag("agenda-section-backfill"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("事后补记", style = tokens.typography.title, modifier = Modifier.testTag("agenda-backfill"))
        if (state.timeRecords.isNotEmpty() && state.recordedMinutesLabel.isNotBlank()) {
            Text(state.recordedMinutesLabel)
        }
        state.timeRecords.forEach { record ->
            AgendaTimeRecordCard(
                record = record,
                targets = state.targets,
                isEditing = editingRecord?.entryId == record.entryId,
                onEdit = { onEditRecord(record) },
                onStopEdit = onStopEditingRecord,
                onDelete = { onDeleteRecord(record.entryId) },
                actions = actions,
            )
        }
    }
}

/**
 * 重复日程的次级入口。
 *
 * 能力没有删除：展开之后，创建重复规则与「停止未来生成」都仍然可用。默认折叠只是让它
 * 不再占据首屏主路径；折叠状态属于页面 module，不写入任何持久化。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgendaRecurrenceSection(
    state: AgendaScreenState,
    form: AgendaRecurrenceForm,
    expanded: Boolean,
    onToggle: () -> Unit,
    date: String,
    actions: AgendaActions,
) {
    val tokens = attentionTokens()
    Column(
        Modifier.fillMaxWidth().testTag("agenda-section-recurrence"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AttentionOutlinedButton(onClick = onToggle, modifier = Modifier.testTag("agenda-recurrence-toggle")) {
            Text(if (expanded) "收起重复日程" else "重复日程（次级入口）")
        }
        if (expanded) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
                Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("重复日程", style = tokens.typography.body)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(form.title, form::changeTitle, label = { Text("重复标题") }, modifier = Modifier.weight(1f).testTag("agenda-recurrence-title-input"), singleLine = true)
                        ScheduleFrequency.entries.forEach { value ->
                            if (form.frequency == value) AttentionButton(onClick = {}) { Text(value.agendaLabel()) }
                            else AttentionOutlinedButton(onClick = { form.selectFrequency(value) }) { Text(value.agendaLabel()) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(form.untilDate, form::changeUntilDate, label = { Text("结束规划日（可选）") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(form.estimatedMinutes, form::changeEstimatedMinutes, label = { Text("预计分钟（可选）") }, modifier = Modifier.width(140.dp), singleLine = true)
                        OutlinedTextField(form.reminderTime, form::changeReminderTime, label = { Text("提醒 HH:mm（可选）") }, modifier = Modifier.width(150.dp).testTag("recurrence-reminder-input"), singleLine = true)
                    }
                    when (form.frequency) {
                        ScheduleFrequency.WEEKLY -> {
                            // 七个星期自动换行，不再横向滚动。横向滚动容器会成为复选框最近的滚动祖先：
                            // 重复日程是折叠的次级入口，整块被推到折叠线以下时，只滚动这一行的行为
                            // 滚不动页面，复选框也就既到不了视口里也点不到。换行后七个星期一直可达。
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                (1..7).forEach { weekday ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = weekday in form.weekdays,
                                            onCheckedChange = { checked -> form.toggleWeekday(weekday, checked) },
                                            // 星期复选框带稳定 test tag：页面上的时间轴行也有完成勾选框，
                                            // 测试必须能指名这一个，而不是「页面上第一个可切换的控件」。
                                            modifier = Modifier.testTag("agenda-recurrence-weekday-$weekday"),
                                        )
                                        Text(agendaWeekdayLabel(weekday))
                                    }
                                }
                            }
                        }
                        ScheduleFrequency.MONTHLY -> {
                            OutlinedTextField(form.dayOfMonth, form::changeDayOfMonth, label = { Text("每月日期 1-31") }, modifier = Modifier.width(160.dp), singleLine = true)
                        }
                        ScheduleFrequency.DAILY -> Unit
                    }
                    Box {
                        AttentionOutlinedButton(onClick = { form.toggleTargetMenu(true) }) {
                            Text(state.targets.firstOrNull { it.targetId == form.targetId }?.title ?: "关联计划（可选）")
                        }
                        DropdownMenu(
                            expanded = form.targetMenuExpanded,
                            onDismissRequest = { form.toggleTargetMenu(false) },
                        ) {
                            DropdownMenuItem(text = { Text("不关联计划") }, onClick = { form.selectTarget(null) })
                            state.targets.forEach { target ->
                                DropdownMenuItem(text = { Text(target.title) }, onClick = { form.selectTarget(target.targetId) })
                            }
                        }
                    }
                    form.error?.let { AttentionInlineError(message = it) }
                    AttentionButton(modifier = Modifier.testTag("agenda-recurrence-submit"), onClick = {
                        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                        val untilDateText = form.untilDate.trim()
                        val parsedUntilDate = untilDateText.takeIf(String::isNotEmpty)?.let { value ->
                            runCatching { LocalDate.parse(value) }.getOrNull()
                        }
                        val estimatedMinutesText = form.estimatedMinutes.trim()
                        val parsedEstimatedMinutes = estimatedMinutesText.takeIf(String::isNotEmpty)?.toIntOrNull()
                        val parsedReminderMinute = parseScheduleMinute(form.reminderTime)
                        val parsedDayOfMonth = form.dayOfMonth.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                        val error = validateRecurrenceForm(
                            form.title,
                            parsedDate,
                            untilDateText,
                            parsedUntilDate,
                            form.frequency,
                            form.weekdays,
                            parsedDayOfMonth,
                            estimatedMinutesText,
                            parsedEstimatedMinutes,
                            form.reminderTime,
                            parsedReminderMinute,
                        )
                        if (error != null) {
                            form.showError(error)
                        } else {
                            actions.addRecurrenceAndMaterialize(form.toDraft(parsedDate!!))
                            if (parsedReminderMinute != null) actions.requestReminderPermission()
                            form.reset()
                        }
                    }) { Text("创建并生成未来 30 天") }
                    state.recurrenceRules.forEach { rule ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(rule.label, Modifier.weight(1f))
                            if (rule.active) {
                                AttentionTextButton(
                                    onClick = { actions.stopRecurrence(rule.ruleId) },
                                    modifier = Modifier.testTag("agenda-recurrence-stop-${rule.ruleId}"),
                                ) { Text("停止未来生成") }
                            } else {
                                Text("已停止")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一条日程条目行。展示与可点击的动作都来自投影，页面只发 intent。
 *
 * 计划投入、实际投入、完成状态与「已超出容量」都由投影给出，页面不比较分钟数，
 * 因此预计时长永远不会被当成实际投入展示。正在计时的那条用文字与图标说明状态，
 * 锈橙色只是补充语义。
 */
@Composable
private fun AgendaScheduleRowCard(
    row: AgendaScheduleRow,
    actions: AgendaActions,
    onEdit: () -> Unit,
) {
    val tokens = attentionTokens()
    val entry: ScheduleEntry = row.entry
    Card(
        Modifier.fillMaxWidth().testTag("agenda-schedule-row-${entry.id}"),
        shape = RoundedCornerShape(tokens.radius.medium),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (row.timing) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("schedule-timing-cue")) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = "正在计时",
                        tint = tokens.brand.timingInProgress.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "正在计时",
                        style = tokens.typography.caption,
                        color = tokens.brand.timingInProgress.accent,
                        modifier = Modifier.padding(start = tokens.spacing.small),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.title, style = tokens.typography.body)
                    Text(row.detailLabel)
                    row.targetLabel?.let { Text("关联计划：$it") }
                    if (row.investmentLabel.isNotBlank()) Text(row.investmentLabel)
                    row.capacityNotice?.let { notice ->
                        Text(notice, color = tokens.brand.error.accent, modifier = Modifier.testTag("schedule-capacity-notice-${entry.id}"))
                    }
                    if (row.note.isNotBlank()) Text(row.note)
                }
                AttentionTextButton(onClick = onEdit, modifier = Modifier.testTag("schedule-edit-${entry.id}")) { Text("编辑") }
                AttentionTextButton(
                    onClick = { actions.startTimerFromEntry(entry) },
                    enabled = row.canStartTimer,
                    modifier = Modifier.testTag("schedule-start-timer-${entry.id}"),
                ) { Text("开始计时") }
                AttentionTextButton(
                    onClick = {
                        if (entry.completed) actions.toggleScheduleCompleted(entry.id)
                        else actions.confirmScheduleCompleted(entry.id)
                    },
                    modifier = Modifier.testTag("schedule-completion-${entry.id}"),
                ) { Text(if (entry.completed) "取消完成" else "完成并记录") }
                // 勾选框是完成这件事的可见主入口：完成状态同时由方框与文字表达，
                // 不依赖颜色。更多菜单把编辑与计时控制收在同一个可见按钮后面，
                // 因此长按至多是额外的熟练操作快捷方式，而不是唯一入口。
                if (row.offersCompletionCheck) {
                    Checkbox(
                        checked = row.completed,
                        onCheckedChange = {
                            if (row.completed) actions.toggleScheduleCompleted(entry.id)
                            else actions.confirmScheduleCompleted(entry.id)
                        },
                        modifier = Modifier
                            .testTag("schedule-completion-check-${entry.id}")
                            .semantics { contentDescription = "完成「${row.title}」" },
                    )
                }
                if (row.offersMoreMenu) {
                    var rowMoreExpanded by remember(entry.id) { mutableStateOf(false) }
                    Box {
                        AttentionTextButton(
                            onClick = { rowMoreExpanded = true },
                            modifier = Modifier.testTag("schedule-more-${entry.id}"),
                        ) { Text("更多") }
                        DropdownMenu(
                            expanded = rowMoreExpanded,
                            onDismissRequest = { rowMoreExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑") },
                                onClick = { rowMoreExpanded = false; onEdit() },
                                modifier = Modifier.testTag("schedule-more-edit-${entry.id}"),
                            )
                            DropdownMenuItem(
                                text = { Text("开始计时") },
                                enabled = row.canStartTimer,
                                onClick = { rowMoreExpanded = false; actions.startTimerFromEntry(entry) },
                                modifier = Modifier.testTag("schedule-more-start-timer-${entry.id}"),
                            )
                            DropdownMenuItem(
                                text = { Text(if (row.completed) "取消完成" else "完成并记录") },
                                onClick = {
                                    rowMoreExpanded = false
                                    if (row.completed) actions.toggleScheduleCompleted(entry.id)
                                    else actions.confirmScheduleCompleted(entry.id)
                                },
                                modifier = Modifier.testTag("schedule-more-completion-${entry.id}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一条实际投入：时长、归属与来源，以及编辑与删除的入口。
 *
 * 编辑表单是页面本地状态；保存时只把被改动的字段作为 intent 发出，
 * 记录的规划日与来源由领域层保持不变（ADR-0003 的手动时间账本边界）。
 */
@Composable
private fun AgendaTimeRecordCard(
    record: AgendaTimeRecord,
    targets: List<AgendaTargetOption>,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onStopEdit: () -> Unit,
    onDelete: () -> Unit,
    actions: AgendaActions,
) {
    val tokens = attentionTokens()
    var minutes by remember(record.entryId) { mutableStateOf(record.durationMinutes.toString()) }
    var note by remember(record.entryId) { mutableStateOf(record.note) }
    var targetId by remember(record.entryId) { mutableStateOf(record.targetId) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(DurationFormat.ofMinutes(record.durationMinutes), style = tokens.typography.title)
            Text(
                "${record.targetLabel} · ${TimeEntrySourceFormat.labelOf(record.source)}",
                style = tokens.typography.caption,
                color = tokens.colorScheme.onSurfaceVariant,
            )
            if (record.note.isNotBlank()) Text(record.note)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AttentionTextButton(onClick = onEdit, modifier = Modifier.testTag("edit-time-${record.entryId}")) { Text("编辑") }
                AttentionTextButton(onClick = onDelete, modifier = Modifier.testTag("delete-time-${record.entryId}")) { Text("删除") }
            }
            if (isEditing) {
                // 目标选择器在颜色之外还要说清楚当前选中了谁：选中项独占一行，
                // 候选项另起一行横向滚动，因此选中态既不会被滚出屏幕也不依赖颜色。
                val selectedLabel = targets.firstOrNull { it.targetId == targetId }?.title ?: "未归属"
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("edit-time-target-${record.entryId}")) {
                    AttentionSelectedIndicator(selected = true, label = selectedLabel)
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    AttentionTextButton(onClick = { targetId = null }) { Text("未归属") }
                    targets.forEach { target ->
                        AttentionTextButton(onClick = { targetId = target.targetId }) { Text(target.title) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        minutes,
                        { minutes = it },
                        label = { Text("分钟") },
                        modifier = Modifier.width(120.dp).testTag("edit-time-minutes-${record.entryId}"),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        note,
                        { note = it },
                        label = { Text("备注") },
                        modifier = Modifier.width(180.dp).testTag("edit-time-note-${record.entryId}"),
                        singleLine = true,
                    )
                    AttentionButton(
                        modifier = Modifier.testTag("save-time-${record.entryId}"),
                        onClick = {
                            minutes.toIntOrNull()?.takeIf { it > 0 }?.let { value ->
                                actions.editTime(record.entryId, value, targetId, note)
                                onStopEdit()
                            }
                        },
                    ) { Text("保存") }
                }
            }
        }
    }
}

/** 编辑日程对话框。表单状态是页面本地的；保存时把改好的条目作为 intent 发出。 */
@Composable
private fun AgendaScheduleEditDialog(
    row: AgendaScheduleRow,
    targets: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (ScheduleEntry, Int?, Boolean) -> Unit,
) {
    val entry = row.entry
    var date by remember(entry.id) { mutableStateOf(entry.planningDate) }
    var title by remember(entry.id) { mutableStateOf(row.title) }
    var startTime by remember(entry.id) { mutableStateOf(row.startText) }
    var endTime by remember(entry.id) { mutableStateOf(row.endText) }
    var estimate by remember(entry.id) { mutableStateOf(row.estimateText) }
    var note by remember(entry.id) { mutableStateOf(row.note) }
    var reminderTime by remember(entry.id) { mutableStateOf(row.reminderText) }
    var targetId by remember(entry.id) { mutableStateOf(row.targetId) }
    var targetMenuExpanded by remember { mutableStateOf(false) }
    var error by remember(entry.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑日程") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(date, { date = it }, label = { Text("规划日 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(title, { title = it }, label = { Text("日程标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startTime, { startTime = it }, label = { Text("开始 HH:mm") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(endTime, { endTime = it }, label = { Text("结束 HH:mm") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(estimate, { estimate = it }, label = { Text("预计分钟") }, modifier = Modifier.fillMaxWidth().testTag("schedule-edit-estimate-${entry.id}"), singleLine = true)
                OutlinedTextField(reminderTime, { reminderTime = it }, label = { Text("提醒 HH:mm（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Box {
                    AttentionOutlinedButton(onClick = { targetMenuExpanded = true }) { Text(targets.firstOrNull { it.first == targetId }?.second ?: "关联计划（可选）") }
                    DropdownMenu(expanded = targetMenuExpanded, onDismissRequest = { targetMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("不关联计划") }, onClick = { targetId = null; targetMenuExpanded = false })
                        targets.forEach { (id, titleText) -> DropdownMenuItem(text = { Text(titleText) }, onClick = { targetId = id; targetMenuExpanded = false }) }
                    }
                }
                error?.let { AttentionInlineError(message = it) }
            }
        },
        confirmButton = {
            AttentionTextButton(modifier = Modifier.testTag("schedule-edit-save-${entry.id}"), onClick = {
                val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                val parsedStart = parseScheduleMinute(startTime)
                val parsedEnd = parseScheduleMinute(endTime, allowEndOfDay = true)
                val estimated = estimate.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                val parsedReminder = parseScheduleMinute(reminderTime)
                val validationError = validateScheduleForm(
                    ScheduleFormInput(
                        title = title,
                        date = parsedDate,
                        startText = startTime,
                        endText = endTime,
                        estimateText = estimate,
                        startMinute = parsedStart,
                        endMinute = parsedEnd,
                        estimateMinutes = estimated,
                        reminderText = reminderTime,
                        reminderMinute = parsedReminder,
                    ),
                )
                if (validationError != null) {
                    error = validationError
                } else {
                    onSave(
                        entry.copy(
                            planningDate = parsedDate.toString(),
                            title = title,
                            startMinute = parsedStart,
                            endMinute = parsedEnd,
                            estimatedMinutes = estimated,
                            note = note,
                            targetId = targetId,
                        ),
                        parsedReminder,
                        parsedReminder != null,
                    )
                }
            }) { Text("保存") }
        },
        dismissButton = { AttentionTextButton(onClick = onDismiss) { Text("取消") } },
    )
}
