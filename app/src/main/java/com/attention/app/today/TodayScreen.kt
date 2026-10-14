package com.attention.app.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.attention.app.ui.format.TimeRecordTags
import com.attention.app.ui.theme.attentionTokens
import com.attention.workspace.state.TodayActions
import com.attention.workspace.state.TodayScreenState
import com.attention.workspace.state.TodayTimeRecord

/**
 * 今天工作台的呈现：手动时间账本、计时卡片、每日容量、今日安排与首次设置。
 *
 * 它只接收自己的 [TodayScreenState] 与 [TodayActions]：不读完整领域状态，不读 ViewModel，
 * 也不读系统时间——规划日、每日容量、活动计时器的已用时与结束范围预览都由组合根
 * 注入的投影算好。
 *
 * 计时与前台计时服务都经 intent 过 seam，页面 module 因此不触达任何 Android API
 * （ADR-0005 §45-47）。首次设置的表单状态属于本 module（[TodaySelection]、
 * [OnboardingDraft]），组合根既不声明它们，也不改写它们的字段（ADR-0005 §24）。
 */
@Composable
internal fun TodayScreen(
    state: TodayScreenState,
    actions: TodayActions,
) {
    val tokens = attentionTokens()
    val selection = rememberTodaySelection()
    val planningDate = state.planningDate.toString()
    // 两个设置输入框的默认值跟着设置走；来源没变时不重写用户正在输入的内容。
    selection.syncDefaults(state)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(tokens.spacing.large),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("今天工作台", style = tokens.typography.display)
        Text(
            "规划日界线 ${state.planningDayBoundaryLabel} · " +
                "每周从${state.weekDayOptions.single { it.selected }.label}开始",
        )
        Text("规划日：${state.planningDate}")
        // 首次设置刻意放在工作台之上：它只走公开设置命令，因此在离线与 Activity 重建后都一致。
        if (!state.onboardingCompleted) TodayOnboardingCard(state = state, actions = actions)

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
            Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("快速记录实际投入", style = tokens.typography.title)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60).forEach { amount ->
                        AttentionOutlinedButton(onClick = { actions.recordTime(planningDate, amount, null) }) { Text("+$amount") }
                    }
                }
                state.quickRecordTargets.forEach { target ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(target.title, Modifier.weight(1f))
                        listOf(15, 30).forEach { amount ->
                            AttentionTextButton(onClick = { actions.recordTime(planningDate, amount, target.targetId) }) { Text("+$amount") }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        selection.minutes,
                        { selection.typeMinutes(it) },
                        label = { Text("分钟") },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    AttentionButton(onClick = {
                        selection.minutes.toIntOrNull()?.takeIf { it > 0 }
                            ?.let { actions.recordTime(planningDate, it, selection.manualTargetId) }
                    }) {
                        Text(if (selection.manualTargetId == null) "记录未归属活动" else "记录到目标")
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 选中项用共用的选中表达：颜色之外同时有勾选图标与“已选中：…”文案。
                    val targets = listOf<Pair<String?, String>>(null to "未归属") +
                        state.targetOptions.map { it.targetId to it.title }
                    targets.forEach { (id, title) ->
                        if (selection.manualTargetId == id) {
                            Box(Modifier.clickable { selection.selectManualTarget(id) }) {
                                AttentionSelectedIndicator(selected = true, label = title)
                            }
                        } else {
                            AttentionTextButton(onClick = { selection.selectManualTarget(id) }) { Text(title) }
                        }
                    }
                }
                Text(state.recordedMinutesLabel)
            }
        }

        TodayTimerCard(state = state, actions = actions)

        DailyCapacityCard(state = state, actions = actions, selection = selection)

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
            Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("周起始日", style = tokens.typography.title)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.weekDayOptions.forEach { option ->
                        if (option.selected) {
                            AttentionButton(onClick = {}) { Text(option.label) }
                        } else {
                            AttentionOutlinedButton(onClick = { actions.selectWeekStartDay(option.day) }) { Text(option.label) }
                        }
                    }
                }
            }
        }

        Text("今日安排", style = tokens.typography.title)
        Text("实际投入记录", style = tokens.typography.title)
        state.records.forEach { record ->
            TimeRecordCard(
                record = record,
                state = state,
                actions = actions,
                selection = selection,
            )
        }
        TodayScheduleSection(state = state, actions = actions)
    }

    val pendingDelete = state.records.firstOrNull { it.entryId == selection.pendingDeleteEntryId }
    if (pendingDelete != null) {
        AttentionConfirmDialog(
            visible = true,
            title = "删除这条实际投入",
            // 影响说明是必填的：用户要知道自己到底会失去什么，而不是只被问一句是否确定。
            impact = "删除后这 ${DurationFormat.ofMinutes(pendingDelete.durationMinutes)} 的实际投入不再计入目标进度、投入经验与统计。" +
                "删除后仍可以在底部提示里撤销一次。",
            confirmLabel = "删除",
            onConfirm = {
                selection.dismissDelete()
                actions.deleteTime(pendingDelete.entryId)
            },
            onDismiss = { selection.dismissDelete() },
        )
    }
}

/** 每日容量卡片。容量占用与超载由投影判定，这里只负责输入与保存 intent。 */
@Composable
private fun DailyCapacityCard(
    state: TodayScreenState,
    actions: TodayActions,
    selection: TodaySelection,
) {
    val tokens = attentionTokens()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
        Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("每日容量", style = tokens.typography.title)
            Text(state.capacitySummaryLabel, modifier = Modifier.testTag("daily-capacity-summary"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    selection.boundaryMinutes,
                    { selection.typeBoundaryMinutes(it) },
                    label = { Text("界线分钟") },
                    modifier = Modifier.width(150.dp).testTag("planning-boundary-input"),
                    singleLine = true,
                )
                OutlinedTextField(
                    selection.dailyCapacityMinutes,
                    { selection.typeDailyCapacityMinutes(it) },
                    label = { Text("容量分钟") },
                    modifier = Modifier.width(150.dp).testTag("daily-capacity-input"),
                    singleLine = true,
                    isError = selection.dailyCapacityError != null,
                )
            }
            selection.dailyCapacityError?.let {
                AttentionInlineError(message = it, messageTag = "daily-capacity-error")
            }
            AttentionButton(modifier = Modifier.testTag("daily-capacity-save"), onClick = {
                when (val save = selection.resolveCapacitySave()) {
                    is DailyPlanningSave.Invalid -> Unit
                    is DailyPlanningSave.Valid -> actions.saveDailyPlanningSettings(
                        // 界线输入不合法时沿用当前的界线，与迁移前的兜底一致。
                        boundaryMinutes = save.boundary ?: state.planningDayBoundaryMinutes,
                        dailyCapacityMinutes = save.capacity,
                    )
                }
            }) { Text("保存设置") }
        }
    }
}

/** 一条实际投入：时长、归属与来源，以及编辑与删除的入口。 */
@Composable
private fun TimeRecordCard(
    record: TodayTimeRecord,
    state: TodayScreenState,
    actions: TodayActions,
    selection: TodaySelection,
) {
    val tokens = attentionTokens()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                DurationFormat.ofMinutes(record.durationMinutes),
                style = tokens.typography.title,
                modifier = Modifier.testTag(TimeRecordTags.DURATION),
            )
            Text(
                "${record.targetLabel} · ${TimeEntrySourceFormat.labelOf(record.source)}",
                style = tokens.typography.caption,
                color = tokens.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TimeRecordTags.SOURCE_MARKER),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AttentionTextButton(onClick = { selection.startEditing(record) }, modifier = Modifier.testTag("edit-time-${record.entryId}")) { Text("编辑") }
                AttentionTextButton(onClick = { selection.requestDelete(record.entryId) }, modifier = Modifier.testTag("delete-time-${record.entryId}")) { Text("删除") }
            }
            if (selection.editingEntryId == record.entryId) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttentionTextButton(onClick = { selection.selectEditTarget(null) }) { Text("未归属") }
                    state.targetOptions.forEach { target ->
                        AttentionTextButton(onClick = { selection.selectEditTarget(target.targetId) }) { Text(target.title) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        selection.editMinutes,
                        { selection.typeEditMinutes(it) },
                        label = { Text("分钟") },
                        modifier = Modifier.testTag("edit-time-minutes-${record.entryId}").width(120.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        selection.editNote,
                        { selection.typeEditNote(it) },
                        label = { Text("备注") },
                        modifier = Modifier.width(180.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    AttentionButton(
                        modifier = Modifier.testTag("save-time-${record.entryId}"),
                        onClick = {
                            selection.editMinutes.toIntOrNull()?.takeIf { it > 0 }?.let { minutes ->
                                actions.editTime(record.entryId, minutes, selection.editTargetId, selection.editNote)
                                selection.stopEditing()
                            }
                        },
                    ) { Text("保存") }
                }
            }
        }
    }
}
