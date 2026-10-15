package com.attention.app.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionConfirmDialog
import com.attention.app.ui.components.AttentionInlineError
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
import com.attention.workspace.state.AgendaRangeScope
import com.attention.workspace.state.AgendaScreenState
import com.attention.workspace.state.AgendaScheduleRow
import com.attention.workspace.state.AgendaTargetOption
import com.attention.workspace.state.AgendaTimeRecord
import com.attention.workspace.state.AgendaWorkbenchSection
import com.attention.workspace.state.agendaLabel
import com.attention.workspace.state.agendaWeekdayLabel
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    val scope = rememberCoroutineScope()
    var addScheduleTop by remember { mutableStateOf(0f) }
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
    fun selectDate(value: LocalDate?) {
        actions.selectPlanningDate(value)
        date = value?.toString() ?: today
        dateError = false
    }
    fun openPlanningDay() {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
        if (parsed == null) dateError = true else selectDate(parsed)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(tokens.spacing.large),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
                    onAddFirstSchedule = {
                        // 空态直接给出添加日程入口：把添加表单滚进视野，而不是让用户自己找
                        scope.launch { scrollState.animateScrollTo(addScheduleTop.toInt()) }
                    },
                )

                AgendaWorkbenchSection.RUNNING_TIMER -> state.activeTimer?.let { timer ->
                    AgendaRunningTimerSection(timer = timer, targets = state.targets, actions = actions)
                }

                AgendaWorkbenchSection.ADD_SCHEDULE -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates -> addScheduleTop = coordinates.positionInParent().y },
                    ) {
                        AgendaAddScheduleSection(state = state, scheduleForm = scheduleForm, date = date, actions = actions)
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
}

/**
 * 首屏第一块：规划日与每日容量的只读摘要，外加切换查看范围的导航。
 *
 * 它只读不写——完整设置继续走统计页底部的「设置与数据」，因此这里没有任何设置表单。
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
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
            Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("规划日与每日容量", style = tokens.typography.body)
                Text(summary.planningDateLabel)
                Text(summary.boundaryLabel, modifier = Modifier.testTag("agenda-planning-day-boundary"))
                Text(summary.weekStartLabel, modifier = Modifier.testTag("agenda-week-start"))
                Text(summary.capacitySettingLabel, modifier = Modifier.testTag("agenda-capacity-setting"))
                summary.capacityUsageLabel?.let { capacityLabel ->
                    Text(capacityLabel, modifier = Modifier.testTag("agenda-capacity-summary"))
                }
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
                        .testTag("agenda-running-timer-tint")
                        .padding(tokens.spacing.small),
                ) {
                    Icon(
                        imageVector = if (timer.paused) Icons.Filled.PauseCircle else Icons.Filled.Timer,
                        // 图标本身也带语义：读屏用户不靠颜色也知道现在是暂停还是运行。
                        contentDescription = timer.statusLabel,
                        tint = rust.onContainer,
                        modifier = Modifier.size(tokens.spacing.medium),
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

/** 首屏第四块：添加日程入口。表单本身仍是页面本地状态，提交时只发一个草稿 intent。 */
@Composable
private fun AgendaAddScheduleSection(
    state: AgendaScreenState,
    scheduleForm: AgendaScheduleForm,
    date: String,
    actions: AgendaActions,
) {
    val tokens = attentionTokens()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
        Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 选中明天时突出添加日程：颜色之外还有图标与文案，说明在为哪一天安排。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (state.highlightAddSchedule) Modifier.testTag("agenda-add-highlight") else Modifier,
            ) {
                if (state.highlightAddSchedule) {
                    Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = "为明天安排日程", modifier = Modifier.size(tokens.spacing.medium))
                }
                Text(state.addScheduleHeadingLabel, style = tokens.typography.body)
            }
            OutlinedTextField(scheduleForm.title, scheduleForm::changeTitle, label = { Text("日程标题") }, modifier = Modifier.fillMaxWidth().testTag("agenda-title-input"), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(scheduleForm.startTime, scheduleForm::changeStartTime, label = { Text("开始时间 HH:mm（可选）") }, modifier = Modifier.weight(1f).testTag("agenda-start-input"), singleLine = true)
                OutlinedTextField(scheduleForm.endTime, scheduleForm::changeEndTime, label = { Text("结束时间 HH:mm（可选）") }, modifier = Modifier.weight(1f).testTag("agenda-end-input"), singleLine = true)
                OutlinedTextField(scheduleForm.estimate, scheduleForm::changeEstimate, label = { Text("预计分钟") }, modifier = Modifier.width(110.dp).testTag("agenda-estimate-input"), singleLine = true)
            }
            OutlinedTextField(scheduleForm.reminderTime, scheduleForm::changeReminderTime, label = { Text("提醒 HH:mm（可选）") }, modifier = Modifier.fillMaxWidth().testTag("agenda-reminder-input"), singleLine = true)
            OutlinedTextField(scheduleForm.note, scheduleForm::changeNote, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Box {
                AttentionOutlinedButton(onClick = { scheduleForm.toggleTargetMenu(true) }) {
                    Text(state.targets.firstOrNull { it.targetId == scheduleForm.targetId }?.title ?: "关联计划（可选）")
                }
                DropdownMenu(
                    expanded = scheduleForm.targetMenuExpanded,
                    onDismissRequest = { scheduleForm.toggleTargetMenu(false) },
                ) {
                    DropdownMenuItem(text = { Text("不关联计划") }, onClick = { scheduleForm.selectTarget(null) })
                    state.targets.forEach { target ->
                        DropdownMenuItem(text = { Text(target.title) }, onClick = { scheduleForm.selectTarget(target.targetId) })
                    }
                }
            }
            scheduleForm.error?.let { AttentionInlineError(message = it) }
            AttentionButton(modifier = Modifier.testTag("agenda-add-submit"), onClick = {
                val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                val parsedStart = parseScheduleMinute(scheduleForm.startTime)
                val parsedEnd = parseScheduleMinute(scheduleForm.endTime, allowEndOfDay = true)
                val estimated = scheduleForm.estimate.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                val parsedReminder = parseScheduleMinute(scheduleForm.reminderTime)
                val error = validateScheduleForm(
                    ScheduleFormInput(
                        title = scheduleForm.title,
                        date = parsedDate,
                        startText = scheduleForm.startTime,
                        endText = scheduleForm.endTime,
                        estimateText = scheduleForm.estimate,
                        startMinute = parsedStart,
                        endMinute = parsedEnd,
                        estimateMinutes = estimated,
                        reminderText = scheduleForm.reminderTime,
                        reminderMinute = parsedReminder,
                    ),
                )
                if (error != null) {
                    scheduleForm.showError(error)
                } else {
                    actions.addSchedule(scheduleForm.toDraft(parsedDate!!))
                    if (parsedReminder != null) actions.requestReminderPermission()
                    scheduleForm.reset()
                }
            }) { Text("添加日程") }
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
                        OutlinedTextField(form.title, form::changeTitle, label = { Text("重复标题") }, modifier = Modifier.weight(1f), singleLine = true)
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
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                (1..7).forEach { weekday ->
                                    Row {
                                        Checkbox(
                                            checked = weekday in form.weekdays,
                                            onCheckedChange = { checked -> form.toggleWeekday(weekday, checked) },
                                        )
                                        Text(agendaWeekdayLabel(weekday), modifier = Modifier.padding(top = 12.dp))
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
