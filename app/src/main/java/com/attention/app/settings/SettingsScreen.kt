package com.attention.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionInlineError
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.components.AttentionTextButton
import com.attention.app.ui.theme.attentionTokens
import com.attention.workspace.state.SettingsActions
import com.attention.workspace.state.SettingsScreenState

/**
 * 设置页。它只接收自己的 [SettingsScreenState] 与 [SettingsActions]：
 * 不读完整领域状态，不读 ViewModel，也不读系统时间——当前时间与通知可用性由组合根注入投影。
 * 页面本地状态（输入草稿、展开态）留在页面内，不上浮到组合根。
 *
 * 这里是启动入口、规划日界线、周起始日、每日容量、通知与备份数据的唯一编辑入口：
 * #172 下线今天页的每日容量卡片之后，规划日语义相关的编辑能力全部集中到了这一页。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    state: SettingsScreenState,
    actions: SettingsActions,
) {
    val tokens = attentionTokens()
    val draft = rememberSettingsDraft()
    draft.syncDefaults(state)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(tokens.spacing.large),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = tokens.typography.display)
        // 返回入口放在页首而不是页尾：设置很长，页尾的返回按钮要一路滚到底才看得见。
        AttentionOutlinedButton(
            onClick = actions::close,
            modifier = Modifier.fillMaxWidth().testTag("settings-back"),
        ) { Text("返回统计页") }
        Text("启动入口", style = tokens.typography.title)
        Text("应用下次启动时打开的页面")
        state.options.forEach { option ->
            if (option.selected) {
                AttentionButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().testTag("launch-destination-${option.destination}"),
                ) { Text("${option.label}（当前）") }
            } else {
                AttentionOutlinedButton(
                    onClick = { actions.selectLaunchDestination(option.destination) },
                    modifier = Modifier.fillMaxWidth().testTag("launch-destination-${option.destination}"),
                ) { Text(option.label) }
            }
        }
        PlanningSettingsSection(state = state, actions = actions, draft = draft)
        NotificationSection(state = state, actions = actions)
        BackupDataSection(actions = actions)
    }
}

/**
 * 规划日与容量：规划日界线、周起始日与每日容量三项设置。
 *
 * 界线与容量共用一次保存 intent（它们一起写进同一份规划设置），周起始日是一条独立的公开设置命令，
 * 因此点选即生效。三项的语义仍然由领域层负责，页面只负责输入与提交。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanningSettingsSection(
    state: SettingsScreenState,
    actions: SettingsActions,
    draft: SettingsDraft,
) {
    val tokens = attentionTokens()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("规划日与容量", style = tokens.typography.title)
            Text("规划日界线", style = tokens.typography.title)
            Text("规划日按这个界线切换，默认 03:00；填 0–1439 的分钟。")
            OutlinedTextField(
                value = draft.boundary,
                onValueChange = draft::typeBoundary,
                label = { Text("规划日界线（分钟）") },
                modifier = Modifier.width(150.dp).testTag("planning-boundary-input"),
                singleLine = true,
            )
            Text("周起始日", style = tokens.typography.title)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.weekDayOptions.forEach { option ->
                    if (option.selected) {
                        AttentionButton(
                            onClick = {},
                            modifier = Modifier.testTag("settings-week-${option.day}"),
                        ) { Text(option.label) }
                    } else {
                        AttentionOutlinedButton(
                            onClick = { actions.selectWeekStartDay(option.day) },
                            modifier = Modifier.testTag("settings-week-${option.day}"),
                        ) { Text(option.label) }
                    }
                }
            }
            Text("每日容量", style = tokens.typography.title)
            Text("每天可安排时间的上限，可留空；填写时必须是正整数分钟。")
            OutlinedTextField(
                value = draft.capacity,
                onValueChange = draft::typeCapacity,
                label = { Text("每日容量（分钟，可选）") },
                modifier = Modifier.width(150.dp).testTag("daily-capacity-input"),
                singleLine = true,
                isError = draft.error != null,
            )
            draft.error?.let { AttentionInlineError(message = it, messageTag = "daily-capacity-error") }
            AttentionButton(
                modifier = Modifier.testTag("planning-settings-save"),
                onClick = {
                    when (val save = draft.resolveSave(state)) {
                        is PlanningSettingsSave.Invalid -> Unit
                        is PlanningSettingsSave.Valid ->
                            actions.savePlanningDaySettings(save.boundaryMinutes, save.dailyCapacityMinutes)
                    }
                },
            ) { Text("保存规划设置") }
        }
    }
}

/**
 * 通知：是否可用是平台事实，由组合根注入；申请权限的动作也由组合根执行，页面不触达系统 API。
 */
@Composable
private fun NotificationSection(
    state: SettingsScreenState,
    actions: SettingsActions,
) {
    val tokens = attentionTokens()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("通知", style = tokens.typography.title)
            if (state.notificationsEnabled) {
                Text("通知已开启。")
            } else {
                Text("通知未开启，日程提醒不会弹出。")
                AttentionOutlinedButton(
                    onClick = actions::requestNotificationPermission,
                    modifier = Modifier.testTag("settings-request-notification"),
                ) { Text("开启通知") }
            }
        }
    }
}

/**
 * 备份数据：导入导出与恢复的入口集中在设置页。
 *
 * 按钮只发出 intent，SAF 文件选择器、导入预览与恢复确认仍由组合根完成，workflow 与迁移前一致。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupDataSection(actions: SettingsActions) {
    val tokens = attentionTokens()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("备份数据", style = tokens.typography.title)
            Text("备份与恢复会经过系统文件选择器；导入前自动保存一次保护性备份。")
            // 换行排列而不是横向滚动：横向滚动条里排在屏幕外的按钮在窄屏上点不到，
            // 每一项都必须能直接点到。
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AttentionButton(onClick = actions::previewJsonBackup) { Text("预览 JSON 备份") }
                AttentionOutlinedButton(onClick = actions::importJson) { Text("导入 JSON") }
                AttentionOutlinedButton(onClick = actions::exportTimeEntriesCsv) { Text("导出时间记录 CSV") }
                AttentionOutlinedButton(onClick = actions::previewCsv) { Text("预览 CSV") }
                AttentionOutlinedButton(onClick = actions::exportSchedulesCsv) { Text("导出日程 CSV") }
                AttentionOutlinedButton(onClick = actions::exportCsvBundle) { Text("导出两个 CSV 文件") }
            }
            AttentionTextButton(onClick = actions::restoreLastImportBackup) { Text("恢复最近一次导入前备份") }
        }
    }
}
