package com.attention.app.targets

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionInlineError
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.components.AttentionTextButton
import com.attention.app.ui.theme.attentionTokens
import com.attention.domain.GoalCadence
import com.attention.workspace.state.TargetsActions
import com.attention.workspace.state.TargetsArchivedRow
import com.attention.workspace.state.TargetsFutureRuleRow
import com.attention.workspace.state.TargetsMigrationEditor
import com.attention.workspace.state.TargetsMigrationRow
import com.attention.workspace.state.TargetsMoveSheet
import com.attention.workspace.state.TargetsParentDetail
import com.attention.workspace.state.TargetsScreenState
import com.attention.workspace.state.TargetsTreeNode

/**
 * 目标树页。它只接收自己的 [TargetsScreenState] 与 [TargetsActions]，
 * 以及页面 module 自己拥有的 [TargetsSelection]：不读完整领域状态，不读 ViewModel，
 * 也不读系统时间——当前规划日由组合根注入投影算好。
 */
@Composable
internal fun TargetsScreen(
    state: TargetsScreenState,
    actions: TargetsActions,
    selection: TargetsSelection,
) {
    val tokens = attentionTokens()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(tokens.spacing.large),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("目标树", style = tokens.typography.display)
        Text("父目标显示整个子树的实际投入；归档目标会从默认列表隐藏。")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                selection.title,
                { selection.title = it },
                label = { Text(if (selection.parentId == null) "新计划" else "新子计划") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            AttentionButton(onClick = {
                if (selection.title.isNotBlank()) {
                    actions.addTarget(selection.title, selection.parentId)
                    selection.targetAdded()
                }
            }) { Text("添加") }
        }
        state.parentDetail?.let { detail ->
            ParentTargetDetail(
                state = state,
                detail = detail,
                selection = selection,
                actions = actions,
            )
        }
        state.renameRow?.let { rename ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    selection.renameText,
                    { selection.renameText = it },
                    label = { Text(rename.fieldLabel) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                AttentionButton(onClick = {
                    if (selection.renameText.isNotBlank()) {
                        actions.renameTarget(rename.targetId, selection.renameText)
                    }
                    selection.renameClosed()
                }) { Text("保存") }
            }
        }
        state.moveSheet?.let { sheet ->
            FutureTargetMoveForm(
                state = state,
                sheet = sheet,
                selection = selection,
                actions = actions,
            )
        }
        TargetTree(state.treeRoots, depth = 0, selection = selection, actions = actions)
        if (state.treeEmpty) Text("还没有计划。")
        if (state.archivedRows.isNotEmpty()) {
            Text("已归档目标", style = tokens.typography.title)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(state.archivedSelectedCountLabel, Modifier.weight(1f))
                AttentionTextButton(onClick = { selection.selectAllArchived(state.archivedRows.map { it.targetId }.toSet()) }) { Text("全选") }
                AttentionTextButton(onClick = { selection.clearArchivedSelection() }) { Text("清除选择") }
                AttentionButton(
                    modifier = Modifier.testTag("restore-archived-selected"),
                    enabled = selection.selectedArchivedIds.isNotEmpty(),
                    onClick = {
                        actions.restoreTargets(selection.selectedArchivedIds)
                        selection.archivedRestored()
                    },
                ) { Text("批量恢复") }
                AttentionButton(
                    modifier = Modifier.testTag("delete-archived-selected"),
                    enabled = selection.selectedArchivedIds.isNotEmpty(),
                    onClick = { selection.requestBatchRemoval(selection.selectedArchivedIds) },
                ) { Text("批量安全删除") }
            }
            state.archivedRows.forEach { row ->
                ArchivedTargetRow(row = row, selection = selection, actions = actions)
            }
        } else {
            Text("暂无归档目标", modifier = Modifier.testTag("archived-empty-state"))
        }
    }
    state.deletionConfirmation?.let { confirmation ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = selection::dismissRemoval,
            title = { Text(confirmation.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    confirmation.lines.forEach { line -> Text(line) }
                }
            },
            confirmButton = {
                AttentionTextButton(onClick = {
                    actions.deleteTargetRelations(confirmation.targetIds)
                    selection.removalConfirmed()
                }) { Text("确认删除") }
            },
            dismissButton = { AttentionTextButton(onClick = selection::dismissRemoval) { Text("取消") } },
        )
    }
}

@Composable
private fun ParentTargetDetail(
    state: TargetsScreenState,
    detail: TargetsParentDetail,
    selection: TargetsSelection,
    actions: TargetsActions,
) {
    val tokens = attentionTokens()
    Text("当前父目标：${detail.targetTitle}")
    AttentionTextButton(onClick = { selection.selectParent(null) }) { Text("改为根计划") }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
        Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("为这个计划设置时间目标", style = tokens.typography.body)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                detail.goalCadenceOptions.forEach { option ->
                    if (option.selected) AttentionButton(onClick = {}) { Text(option.label) }
                    else AttentionOutlinedButton(onClick = { selection.selectGoalCadence(option.cadence) }) { Text(option.label) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    selection.goalStartDate,
                    { selection.goalStartDate = it },
                    label = { Text("开始规划日") },
                    modifier = Modifier.width(145.dp),
                    singleLine = true,
                )
                if (detail.goalDueDateVisible) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        selection.goalDueDate,
                        { selection.goalDueDate = it },
                        label = { Text("截止规划日（可选）") },
                        modifier = Modifier.width(145.dp),
                        singleLine = true,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    selection.goalMinutes,
                    { selection.goalMinutes = it.filter(Char::isDigit) },
                    label = { Text("目标分钟") },
                    modifier = Modifier.width(140.dp),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                AttentionButton(onClick = {
                    selection.goalMinutes.toIntOrNull()?.takeIf { it > 0 }?.let {
                        val dueDate = if (selection.goalCadence == GoalCadence.ONE_TIME) {
                            selection.goalDueDate.trim().takeIf(String::isNotEmpty)
                        } else {
                            null
                        }
                        actions.addGoal(detail.targetId, selection.goalCadence, it, selection.goalStartDate, dueDate)
                        selection.goalSaved()
                    }
                }) { Text("保存目标") }
            }
            detail.unowned?.let { unowned ->
                Text(unowned.summaryLabel)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttentionOutlinedButton(onClick = { selection.selectAllUnowned(unowned.entries.map { it.timeEntryId }.toSet()) }) { Text("全选") }
                    AttentionTextButton(onClick = { selection.clearUnownedSelection() }) { Text("清除选择") }
                    AttentionButton(
                        enabled = unowned.entries.any { it.selected },
                        onClick = {
                            actions.assignUnowned(selection.selectedUnownedIds, detail.targetId)
                            selection.unownedAssigned()
                        },
                    ) { Text("归入当前计划") }
                }
                unowned.entries.forEach { entry ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = entry.selected,
                            onCheckedChange = { checked -> selection.selectUnowned(entry.timeEntryId, checked) },
                        )
                        Text(entry.label)
                    }
                }
            }
            Text("未来生效的目标规则", style = tokens.typography.body)
            Text(detail.currentRuleLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    selection.futureDate,
                    { selection.futureDate = it },
                    label = { Text("生效日") },
                    modifier = Modifier.width(145.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    selection.futureMinutes,
                    { selection.futureMinutes = it.filter(Char::isDigit) },
                    label = { Text("未来分钟") },
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                )
                if (detail.futureDueDateVisible) {
                    OutlinedTextField(
                        selection.futureDueDate,
                        { selection.futureDueDate = it },
                        label = { Text("截止日（可选）") },
                        modifier = Modifier.width(145.dp),
                        singleLine = true,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                detail.futureCadenceOptions.forEach { option ->
                    if (option.selected) AttentionButton(onClick = {}) { Text(option.label) }
                    else AttentionOutlinedButton(onClick = { selection.selectFutureCadence(option.cadence) }) { Text(option.label) }
                }
            }
            AttentionButton(onClick = {
                selection.futureMinutes.toIntOrNull()?.takeIf { it > 0 }?.let {
                    actions.saveFutureGoal(
                        targetId = detail.targetId,
                        ruleId = selection.editingFutureRuleId,
                        cadence = selection.futureCadence,
                        targetMinutes = it,
                        effectiveFrom = selection.futureDate,
                        dueDate = selection.futureDueDate.trim()
                            .takeIf { value -> selection.futureCadence == GoalCadence.ONE_TIME && value.isNotEmpty() },
                        onDate = state.planningDate,
                    )
                    selection.futureRuleSaved()
                }
            }) { Text(detail.futureRuleSubmitLabel) }
            detail.futureRules.forEach { rule ->
                FutureGoalRuleRow(rule = rule, selection = selection, planningDate = state.planningDate, actions = actions)
            }
            if (detail.hasStages) {
                Text("当前目标进度", style = tokens.typography.body)
                OutlinedTextField(
                    selection.selectedPlanningDate,
                    { selection.selectedPlanningDate = it },
                    label = { Text("当前规划日 YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (detail.planningDateInvalid) {
                    AttentionInlineError(message = "规划日格式无效，请使用 YYYY-MM-DD")
                }
                detail.summaryLabel?.let { label -> Text(label) }
                detail.stages.forEach { stage ->
                    Text(stage.progressLabel)
                    stage.feedbackLabels.forEach { label ->
                        Text(label, modifier = Modifier.padding(start = 16.dp))
                    }
                    stage.migration?.let { migration ->
                        MigrationEditor(
                            editor = migration,
                            selection = selection,
                            actions = actions,
                        )
                    }
                }
                detail.appendStage?.let { append ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
                        Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("追加一次性阶段", style = tokens.typography.body)
                            Text(append.startDateLabel)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    selection.appendGoalMinutes,
                                    { selection.appendGoalMinutes = it.filter(Char::isDigit) },
                                    label = { Text("目标分钟") },
                                    modifier = Modifier.testTag("append-goal-minutes").width(140.dp),
                                    singleLine = true,
                                )
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    selection.appendGoalDueDate,
                                    { selection.appendGoalDueDate = it },
                                    label = { Text("截止规划日（可选）") },
                                    modifier = Modifier.width(155.dp),
                                    singleLine = true,
                                )
                                Spacer(Modifier.width(8.dp))
                                AttentionButton(
                                    modifier = Modifier.testTag("append-goal-submit"),
                                    onClick = {
                                        actions.appendOneTimeGoal(
                                            targetId = append.targetId,
                                            targetMinutes = selection.appendGoalMinutes.toIntOrNull() ?: 0,
                                            startDate = append.startDate,
                                            dueDate = selection.appendGoalDueDate.trim().takeIf(String::isNotEmpty),
                                            onSuccess = selection::appendStageSaved,
                                        )
                                    },
                                ) { Text("追加阶段") }
                            }
                        }
                    }
                }
            }
            detail.futureMove?.let { move ->
                Text(move.label)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttentionOutlinedButton(onClick = { selection.beginEditFutureMove(move) }) { Text("编辑移动") }
                    AttentionTextButton(onClick = { actions.cancelFutureTargetMove(move.moveId) }) { Text("取消移动") }
                }
            }
            if (detail.showMoveToRootAction) {
                AttentionOutlinedButton(onClick = {
                    actions.moveTargetFrom(detail.targetId, null, detail.moveToRootEffectiveDate)
                }) { Text("从明日起移到根计划") }
            }
        }
    }
}

@Composable
private fun FutureGoalRuleRow(
    rule: TargetsFutureRuleRow,
    selection: TargetsSelection,
    planningDate: java.time.LocalDate,
    actions: TargetsActions,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(rule.label, Modifier.weight(1f))
        if (rule.pending) {
            AttentionTextButton(onClick = { selection.beginEditFutureRule(rule) }) { Text("编辑") }
            AttentionTextButton(onClick = { actions.cancelFutureGoal(rule.ruleId, planningDate) }) { Text("取消") }
        }
    }
}

@Composable
private fun MigrationEditor(
    editor: TargetsMigrationEditor,
    selection: TargetsSelection,
    actions: TargetsActions,
) {
    val tokens = attentionTokens()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(editor.headerLabel)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    selection.migrationMinutes,
                    { selection.migrationMinutes = it.filter(Char::isDigit) },
                    label = { Text("迁移分钟") },
                    modifier = Modifier.width(110.dp).testTag("migration-minutes-${editor.stageId}"),
                    singleLine = true,
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    selection.migrationStartDate.ifBlank { editor.defaultStartDate },
                    { selection.migrationStartDate = it },
                    label = { Text("目的开始日") },
                    modifier = Modifier.width(145.dp),
                    singleLine = true,
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    selection.migrationEndDate,
                    { selection.migrationEndDate = it },
                    label = { Text("目的结束日（可选）") },
                    modifier = Modifier.width(155.dp),
                    singleLine = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AttentionButton(
                    enabled = editor.submitEnabled,
                    modifier = Modifier.testTag("migration-submit-${editor.stageId}"),
                    onClick = {
                        val minutes = selection.migrationMinutes.toIntOrNull() ?: return@AttentionButton
                        actions.saveMigration(
                            targetId = editor.targetId,
                            migrationId = selection.editingMigrationId,
                            sourceStageId = editor.stageId,
                            minutes = minutes,
                            destinationStartDate = selection.migrationStartDate.ifBlank { editor.defaultStartDate },
                            destinationEndDate = selection.migrationEndDate.trim().takeIf(String::isNotEmpty),
                            onDate = editor.onDate,
                        )
                        selection.migrationDraftCleared()
                    },
                ) { Text(editor.submitLabel) }
                if (editor.editing) {
                    AttentionTextButton(onClick = { selection.migrationDraftCleared() }) { Text("取消编辑") }
                }
            }
            editor.migrations.forEach { migration ->
                MigrationRow(migration = migration, selection = selection, actions = actions)
            }
        }
    }
}

@Composable
private fun MigrationRow(
    migration: TargetsMigrationRow,
    selection: TargetsSelection,
    actions: TargetsActions,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(migration.label, Modifier.weight(1f))
        if (migration.cancellable) {
            AttentionTextButton(onClick = { selection.beginEditMigration(migration) }) { Text("编辑") }
            AttentionTextButton(onClick = { actions.cancelMigration(migration.migrationId) }) { Text("取消迁移") }
        }
    }
}

@Composable
private fun FutureTargetMoveForm(
    state: TargetsScreenState,
    sheet: TargetsMoveSheet,
    selection: TargetsSelection,
    actions: TargetsActions,
) {
    val tokens = attentionTokens()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
        Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("移动 ${sheet.targetTitle}：选择新的父目标", style = tokens.typography.body)
            OutlinedTextField(
                selection.moveEffectiveDate,
                { selection.moveEffectiveDate = it },
                label = { Text("生效规划日") },
                modifier = Modifier.width(160.dp),
                singleLine = true,
            )
            if (!sheet.effectiveDateValid) {
                AttentionInlineError(message = sheet.effectiveDateError)
            }
            if (sheet.rootSelected) AttentionButton(onClick = {}) { Text("移到根计划") }
            else AttentionOutlinedButton(onClick = { selection.selectMoveParent(null) }) { Text("移到根计划") }
            sheet.parents.forEach { candidate ->
                val label = "移到：${candidate.label}"
                if (candidate.selected) AttentionButton(onClick = {}) { Text(label) }
                else AttentionOutlinedButton(onClick = { selection.selectMoveParent(candidate.targetId) }) { Text(label) }
            }
            AttentionButton(
                enabled = sheet.submitEnabled,
                onClick = {
                    if (!sheet.effectiveDateValid) return@AttentionButton
                    val editingMoveId = selection.editingMoveId
                    if (editingMoveId == null) {
                        actions.moveTargetFrom(sheet.targetId, selection.moveParentId, selection.moveEffectiveDate)
                    } else {
                        actions.updateFutureTargetMove(editingMoveId, selection.moveParentId, selection.moveEffectiveDate)
                    }
                    selection.moveSaved()
                },
            ) { Text(sheet.submitLabel) }
            if (sheet.pendingMoves.isNotEmpty()) {
                Text("待生效移动", style = tokens.typography.body)
                sheet.pendingMoves.forEach { move ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(move.label, Modifier.weight(1f))
                        AttentionTextButton(onClick = { selection.beginEditPendingMove(move) }) { Text("编辑") }
                        AttentionTextButton(onClick = { actions.cancelFutureTargetMove(move.moveId) }) { Text("取消") }
                    }
                }
            }
            AttentionTextButton(onClick = { selection.cancelMove() }) { Text("取消移动") }
        }
    }
}

@Composable
private fun TargetTree(
    nodes: List<TargetsTreeNode>,
    depth: Int,
    selection: TargetsSelection,
    actions: TargetsActions,
) {
    nodes.forEach { node ->
        Column(Modifier.padding(start = (depth * 16).dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (!node.hasChildren) "•" else if (node.expanded) "▾" else "▸")
                Spacer(Modifier.width(6.dp))
                Text(node.title, modifier = Modifier.weight(1f))
                Text(node.minutesLabel)
            }
            node.pendingMoveLabel?.let { label ->
                Text(label, modifier = Modifier.padding(start = 22.dp))
            }
            node.summaryLabel?.let { label ->
                Text(label, modifier = Modifier.padding(start = 22.dp))
            }
            node.stages.forEach { stage ->
                Text(stage.progressLabel, modifier = Modifier.padding(start = 22.dp))
                stage.feedbackLabels.forEach { label ->
                    Text(label, modifier = Modifier.padding(start = 38.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                AttentionTextButton(onClick = { actions.toggleTarget(node.targetId, !node.expanded) }) { Text(node.toggleLabel) }
                AttentionTextButton(onClick = { selection.selectParent(node.targetId) }) { Text("添加子计划") }
                AttentionTextButton(onClick = { selection.beginRename(node.targetId, node.title) }) { Text("重命名") }
                AttentionTextButton(onClick = { selection.beginMove(node.targetId, node.currentParentId) }) { Text("移动") }
                if (node.canMoveUp) {
                    AttentionTextButton(onClick = { actions.reorderTarget(node.targetId, node.siblingIndex - 1) }) { Text("上移") }
                }
                if (node.canMoveDown) {
                    AttentionTextButton(onClick = { actions.reorderTarget(node.targetId, node.siblingIndex + 1) }) { Text("下移") }
                }
                AttentionTextButton(onClick = { actions.archiveTarget(node.targetId) }) { Text("归档") }
            }
            if (node.expanded && node.children.isNotEmpty()) {
                TargetTree(node.children, depth + 1, selection, actions)
            }
        }
    }
}

@Composable
private fun ArchivedTargetRow(
    row: TargetsArchivedRow,
    selection: TargetsSelection,
    actions: TargetsActions,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = row.selected,
                onCheckedChange = { checked -> selection.selectArchived(row.targetId, checked) },
            )
            Text(row.title, Modifier.weight(1f))
            AttentionTextButton(onClick = { actions.restoreTargets(setOf(row.targetId)) }) { Text("恢复") }
            AttentionTextButton(onClick = { selection.requestRemoval(row.targetId) }) { Text("安全删除") }
        }
        Text(row.hierarchyLabel, modifier = Modifier.padding(start = 16.dp))
        Text(row.historicalMinutesLabel, modifier = Modifier.padding(start = 16.dp))
        row.summaryLabel?.let { label ->
            Text("历史进度：$label", modifier = Modifier.padding(start = 16.dp))
        }
        row.stages.forEach { stage ->
            Text("历史进度：${stage.progressLabel}", modifier = Modifier.padding(start = 16.dp))
            stage.feedbackLabels.forEach { label ->
                Text(label, modifier = Modifier.padding(start = 32.dp))
            }
        }
    }
}
