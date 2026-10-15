package com.attention.app.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.attention.app.ui.components.AttentionInlineError
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.components.AttentionTextButton
import com.attention.app.ui.theme.attentionTokens
import com.attention.domain.ScheduleEntry
import com.attention.workspace.state.TodayActions
import com.attention.workspace.state.TodayScheduleRow
import com.attention.workspace.state.TodayScreenState
import java.time.LocalDate

/**
 * 今天工作台「今日安排」里的日程行与它的编辑对话框。
 *
 * 行上的展示文本与表单初值由投影给出，提醒时刻也由投影按注入的时区换算好，
 * 页面因此既不读系统时钟，也不解析纪元毫秒（ADR-0005 §3）。
 *
 * 编辑表单里的提醒只是「一天内的第几分钟」，换算成绝对时刻由组合根 adapter 完成，
 * 权限申请同样只以 intent 过 seam（ADR-0005 第 4 节「Android adapter 归属组合根」）。
 */
@Composable
internal fun TodayScheduleSection(state: TodayScreenState, actions: TodayActions) {
    var editingRow by remember { mutableStateOf<TodayScheduleRow?>(null) }
    state.scheduleRows.forEach { row ->
        TodayScheduleRowCard(row, actions = actions, onEdit = { editingRow = it })
    }
    editingRow?.let { row ->
        TodayScheduleEditDialog(
            row = row,
            targets = state.targetOptions,
            onDismiss = { editingRow = null },
            onSave = { updated, reminderMinute, reminderRequested ->
                if (reminderRequested) actions.requestNotificationPermission()
                actions.updateTodaySchedule(updated, reminderMinute)
                editingRow = null
            },
        )
    }
}

/** 一条日程条目行。展示与可点击的动作都来自投影，页面只发 intent。 */
@Composable
private fun TodayScheduleRowCard(
    row: TodayScheduleRow,
    actions: TodayActions,
    onEdit: (TodayScheduleRow) -> Unit,
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
                // 从日程条目起计时必须带上整条条目，产出的时间记录才保留日程来源关联，
                // 因此这里走 startTimerFromEntry 而不是只传 targetId 的 startTimer。
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
private fun TodayScheduleEditDialog(
    row: TodayScheduleRow,
    targets: List<com.attention.workspace.state.TodayTargetOption>,
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
                    AttentionOutlinedButton(onClick = { targetMenuExpanded = true }) { Text(targets.firstOrNull { it.targetId == targetId }?.title ?: "关联计划（可选）") }
                    DropdownMenu(expanded = targetMenuExpanded, onDismissRequest = { targetMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("不关联计划") }, onClick = { targetId = null; targetMenuExpanded = false })
                        targets.forEach { target -> DropdownMenuItem(text = { Text(target.title) }, onClick = { targetId = target.targetId; targetMenuExpanded = false }) }
                    }
                }
                error?.let { AttentionInlineError(message = it) }
            }
        },
        confirmButton = {
            AttentionTextButton(modifier = Modifier.testTag("schedule-edit-save-${entry.id}"), onClick = {
                val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                val parsedStart = parseTodayScheduleMinute(startTime)
                val parsedEnd = parseTodayScheduleMinute(endTime, allowEndOfDay = true)
                val estimated = estimate.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                val parsedReminder = parseTodayScheduleMinute(reminderTime)
                val validationError = validateTodayScheduleForm(
                    TodayScheduleFormInput(
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
