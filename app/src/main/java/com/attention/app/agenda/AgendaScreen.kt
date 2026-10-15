package com.attention.app.agenda

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionInlineError
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.components.AttentionTextButton
import com.attention.app.ui.theme.attentionTokens
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.workspace.state.AgendaActions
import com.attention.workspace.state.AgendaScreenState
import com.attention.workspace.state.AgendaScheduleRow
import com.attention.workspace.state.agendaLabel
import com.attention.workspace.state.agendaWeekdayLabel
import java.time.LocalDate

/**
 * 所有日期页。它只接收自己的 [AgendaScreenState] 与 [AgendaActions]：
 * 不读完整领域状态，不读 ViewModel，也不读 Android API 与系统时间——
 * 规划日、日程条目、每日容量与提醒时间都由组合根注入的投影算好。
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
    var editingRow by remember { mutableStateOf<AgendaScheduleRow?>(null) }
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(tokens.spacing.large),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(state.headingLabel, style = tokens.typography.display, modifier = Modifier.testTag("agenda-filter"))
        Text("日程条目可带预计时长；它不会自动变成实际投入。")
        Box {
            AttentionOutlinedButton(onClick = { dateMenuExpanded = true }, modifier = Modifier.testTag("agenda-date-menu")) {
                Text(state.filterLabel)
            }
            DropdownMenu(expanded = dateMenuExpanded, onDismissRequest = { dateMenuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("所有日期") },
                    onClick = { selectDate(null); dateMenuExpanded = false },
                    modifier = Modifier.testTag("agenda-all-dates"),
                )
                state.availableDates.forEach { availableDate ->
                    DropdownMenuItem(
                        text = { Text(availableDate.label) },
                        onClick = { selectDate(availableDate.date); dateMenuExpanded = false },
                        modifier = Modifier.testTag("agenda-date-${availableDate.date}"),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(date, { date = it; dateError = false }, label = { Text("新增日程规划日 YYYY-MM-DD") }, modifier = Modifier.weight(1f), singleLine = true, isError = dateError, supportingText = if (dateError) ({ Text("请输入有效的规划日") }) else null)
            AttentionOutlinedButton(onClick = {
                val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
                if (parsed == null) dateError = true else selectDate(parsed)
            }) { Text("打开规划日") }
        }
        state.selectedCapacityLabel?.let { capacityLabel ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
                Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("每日容量", style = tokens.typography.body)
                    Text(capacityLabel, modifier = Modifier.testTag("agenda-capacity-summary"))
                }
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
            Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("重复日程", style = tokens.typography.body)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(recurrenceForm.title, recurrenceForm::changeTitle, label = { Text("重复标题") }, modifier = Modifier.weight(1f), singleLine = true)
                    ScheduleFrequency.entries.forEach { value ->
                        if (recurrenceForm.frequency == value) AttentionButton(onClick = {}) { Text(value.agendaLabel()) }
                        else AttentionOutlinedButton(onClick = { recurrenceForm.selectFrequency(value) }) { Text(value.agendaLabel()) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(recurrenceForm.untilDate, recurrenceForm::changeUntilDate, label = { Text("结束规划日（可选）") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(recurrenceForm.estimatedMinutes, recurrenceForm::changeEstimatedMinutes, label = { Text("预计分钟（可选）") }, modifier = Modifier.width(140.dp), singleLine = true)
                    OutlinedTextField(recurrenceForm.reminderTime, recurrenceForm::changeReminderTime, label = { Text("提醒 HH:mm（可选）") }, modifier = Modifier.width(150.dp).testTag("recurrence-reminder-input"), singleLine = true)
                }
                when (recurrenceForm.frequency) {
                    ScheduleFrequency.WEEKLY -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            (1..7).forEach { weekday ->
                                Row {
                                    Checkbox(
                                        checked = weekday in recurrenceForm.weekdays,
                                        onCheckedChange = { checked -> recurrenceForm.toggleWeekday(weekday, checked) },
                                    )
                                    Text(agendaWeekdayLabel(weekday), modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                    }
                    ScheduleFrequency.MONTHLY -> {
                        OutlinedTextField(recurrenceForm.dayOfMonth, recurrenceForm::changeDayOfMonth, label = { Text("每月日期 1-31") }, modifier = Modifier.width(160.dp), singleLine = true)
                    }
                    ScheduleFrequency.DAILY -> Unit
                }
                Box {
                    AttentionOutlinedButton(onClick = { recurrenceForm.toggleTargetMenu(true) }) {
                        Text(state.targets.firstOrNull { it.targetId == recurrenceForm.targetId }?.title ?: "关联计划（可选）")
                    }
                    DropdownMenu(
                        expanded = recurrenceForm.targetMenuExpanded,
                        onDismissRequest = { recurrenceForm.toggleTargetMenu(false) },
                    ) {
                        DropdownMenuItem(text = { Text("不关联计划") }, onClick = { recurrenceForm.selectTarget(null) })
                        state.targets.forEach { target ->
                            DropdownMenuItem(text = { Text(target.title) }, onClick = { recurrenceForm.selectTarget(target.targetId) })
                        }
                    }
                }
                recurrenceForm.error?.let { AttentionInlineError(message = it) }
                AttentionButton(onClick = {
                    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                    val untilDateText = recurrenceForm.untilDate.trim()
                    val parsedUntilDate = untilDateText.takeIf(String::isNotEmpty)?.let { value ->
                        runCatching { LocalDate.parse(value) }.getOrNull()
                    }
                    val estimatedMinutesText = recurrenceForm.estimatedMinutes.trim()
                    val parsedEstimatedMinutes = estimatedMinutesText.takeIf(String::isNotEmpty)?.toIntOrNull()
                    val parsedReminderMinute = parseScheduleMinute(recurrenceForm.reminderTime)
                    val parsedDayOfMonth = recurrenceForm.dayOfMonth.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                    val error = validateRecurrenceForm(
                        recurrenceForm.title,
                        parsedDate,
                        untilDateText,
                        parsedUntilDate,
                        recurrenceForm.frequency,
                        recurrenceForm.weekdays,
                        parsedDayOfMonth,
                        estimatedMinutesText,
                        parsedEstimatedMinutes,
                        recurrenceForm.reminderTime,
                        parsedReminderMinute,
                    )
                    if (error != null) {
                        recurrenceForm.showError(error)
                    } else {
                        actions.addRecurrenceAndMaterialize(recurrenceForm.toDraft(parsedDate!!))
                        if (parsedReminderMinute != null) actions.requestReminderPermission()
                        recurrenceForm.reset()
                    }
                }) { Text("创建并生成未来 30 天") }
                state.recurrenceRules.forEach { rule ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(rule.label, Modifier.weight(1f))
                        if (rule.active) AttentionTextButton(onClick = { actions.stopRecurrence(rule.ruleId) }) { Text("停止未来生成") }
                        else Text("已停止")
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
            Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("新增日程", style = tokens.typography.body)
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
        state.emptyLabel?.let { emptyLabel ->
            Text(emptyLabel, modifier = Modifier.testTag("agenda-empty"))
        }
        state.dateSections.forEach { section ->
            Text("规划日 ${section.planningDate}", style = tokens.typography.title)
            Text(section.capacityLabel, modifier = Modifier.testTag("agenda-capacity-summary-${section.planningDate}"))
            section.rows.forEach { row ->
                AgendaScheduleRowCard(row, actions = actions, onEdit = { editingRow = it })
            }
        }
        state.selectedRows.forEach { row ->
            AgendaScheduleRowCard(row, actions = actions, onEdit = { editingRow = it })
        }
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

/** 一条日程条目行。展示与可点击的动作都来自投影，页面只发 intent。 */
@Composable
private fun AgendaScheduleRowCard(
    row: AgendaScheduleRow,
    actions: AgendaActions,
    onEdit: (AgendaScheduleRow) -> Unit,
) {
    val tokens = attentionTokens()
    val entry: ScheduleEntry = row.entry
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.title, style = tokens.typography.body)
                Text(row.detailLabel)
                if (row.note.isNotBlank()) Text(row.note)
            }
            AttentionTextButton(onClick = { onEdit(row) }, modifier = Modifier.testTag("schedule-edit-${entry.id}")) { Text("编辑") }
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