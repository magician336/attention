package com.attention.app.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.components.AttentionTextButton
import com.attention.app.ui.theme.attentionTokens
import com.attention.workspace.state.StatisticsActions
import com.attention.workspace.state.StatisticsScreenState

/**
 * 统计页。它只接收自己的 [StatisticsScreenState] 与 [StatisticsActions]：
 * 不读完整领域状态，不读 ViewModel，也不读系统时间——周期范围、标签与当前规划日
 * 都由组合根注入投影算好。
 */
@Composable
internal fun StatisticsScreen(
    state: StatisticsScreenState,
    actions: StatisticsActions,
) {
    val tokens = attentionTokens()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(tokens.spacing.large),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("统计与成长", style = tokens.typography.display)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AttentionTextButton(onClick = actions::selectPreviousPeriod) { Text("上一周期") }
            Text(state.periodRangeLabel)
            AttentionTextButton(onClick = actions::selectNextPeriod) { Text("下一周期") }
        }
        Text("历史周期快照", style = tokens.typography.title)
        Text("快照保存周期结束时的目标、实际投入、缺口和达成结果；重复结算不会新增记录。")
        AttentionButton(onClick = actions::settlePeriodSnapshots) { Text("结算已结束周期") }
        if (state.snapshotsEmpty) {
            Text("还没有已保存的周期快照。")
        } else {
            state.snapshotLines.forEach { line -> Text(line) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.cadenceOptions.forEach { option ->
                if (option.selected) AttentionButton(onClick = {}) { Text(option.label) }
                else AttentionOutlinedButton(onClick = { actions.selectCadence(option.cadence) }) { Text(option.label) }
            }
        }
        Text(state.totalsLabel)
        Text(state.unownedMinutesLabel)
        Text(state.migrationSummaryLabel)
        state.migrations.forEach { migration ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(migration.label, Modifier.weight(1f))
                AttentionTextButton(onClick = { actions.cancelMigration(migration.migrationId) }) { Text("取消") }
            }
        }
        Text("目标树投入", style = tokens.typography.title)
        state.targetInvestmentLines.forEach { line -> Text(line) }
        state.levelLines.forEach { line -> Text(line) }
        AttentionButton(onClick = actions::awardMilestones) { Text("结算可达成里程碑") }
        AttentionOutlinedButton(onClick = actions::restoreLastImportBackup) { Text("恢复最近一次导入前备份") }
        Text("累计投入里程碑", style = tokens.typography.title)
        if (state.cumulativeMilestonesEmpty) {
            Text("还没有累计投入里程碑。")
        } else {
            state.cumulativeMilestoneLines.forEach { line -> Text(line) }
        }
        state.recentMilestoneLines.forEach { line -> Text(line) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AttentionButton(onClick = actions::previewJsonBackup) { Text("预览 JSON 备份") }
            AttentionOutlinedButton(onClick = actions::importJson) { Text("导入 JSON") }
            AttentionOutlinedButton(onClick = actions::exportTimeEntriesCsv) { Text("导出时间记录 CSV") }
            AttentionOutlinedButton(onClick = actions::previewCsv) { Text("预览 CSV") }
            AttentionOutlinedButton(onClick = actions::exportSchedulesCsv) { Text("导出日程 CSV") }
            AttentionOutlinedButton(onClick = actions::exportCsvBundle) { Text("导出两个 CSV 文件") }
        }
        Text("首屏入口", style = tokens.typography.title)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            launchDestinationOptions().forEach { (value, label) ->
                AttentionOutlinedButton(onClick = { actions.selectLaunchDestination(value) }) { Text(label) }
            }
        }
        state.stageSections.forEach { section ->
            section.summaryLine?.let { line -> Text(line) }
            section.stages.forEach { stage ->
                Text(stage.label)
                stage.feedbackLabels.forEach { label ->
                    Text(label, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
        if (!state.hasTargets) Text("创建计划后，这里会显示目标投入和阶段达成情况。")
    }
}

/** 首屏入口的固定顺序与标签属于统计页自身的展示规则，页面拿得到，不需要再投影一遍。 */
private fun launchDestinationOptions() = listOf(
    com.attention.domain.LaunchDestination.TODAY to "今天",
    com.attention.domain.LaunchDestination.TARGETS to "目标树",
    com.attention.domain.LaunchDestination.ALL_DATES to "所有日期",
    com.attention.domain.LaunchDestination.STATISTICS to "统计",
    com.attention.domain.LaunchDestination.LAST_OPENED to "上次打开",
)