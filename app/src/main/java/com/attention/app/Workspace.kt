package com.attention.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.attention.app.data.DataStoreAttentionStateRepository
import com.attention.domain.AttentionState
import com.attention.domain.GoalCadence
import com.attention.domain.LaunchDestination
import com.attention.domain.ScheduleEntry
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.capacitySummary
import com.attention.domain.directMinutes
import com.attention.domain.levelForExperience
import com.attention.domain.planningDate
import com.attention.domain.progress
import com.attention.domain.subtreeMinutes
import com.attention.domain.targetChildren
import java.time.Instant
import java.time.LocalDate

private val Context.attentionStateDataStore by preferencesDataStore(name = "attention_state")

private class WorkspaceViewModelFactory(
    private val repository: DataStoreAttentionStateRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AttentionViewModel(repository) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceApp() {
    val context = LocalContext.current
    val repository = remember { DataStoreAttentionStateRepository(context.attentionStateDataStore) }
    val viewModel: AttentionViewModel = viewModel(factory = WorkspaceViewModelFactory(repository))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(LaunchDestination.TODAY) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Attention") }) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(
                    LaunchDestination.TODAY to "今天",
                    LaunchDestination.TARGETS to "目标树",
                    LaunchDestination.ALL_DATES to "所有日期",
                    LaunchDestination.STATISTICS to "统计",
                ).forEach { (item, label) ->
                    if (destination == item) Button(onClick = { destination = item }) { Text(label) }
                    else OutlinedButton(onClick = { destination = item }) { Text(label) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            error?.let {
                Text("$it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                TextButton(onClick = viewModel::clearError) { Text("关闭提示") }
            }
            when (destination) {
                LaunchDestination.TODAY, LaunchDestination.LAST_OPENED -> WorkspaceTodayScreen(state, viewModel)
                LaunchDestination.TARGETS -> TargetsScreen(state, viewModel)
                LaunchDestination.ALL_DATES -> AgendaScreen(state, viewModel)
                LaunchDestination.STATISTICS -> StatisticsScreen(state)
            }
        }
    }
}

@Composable
private fun WorkspaceTodayScreen(state: AttentionState, viewModel: AttentionViewModel) {
    val date = state.planningDate(Instant.now()).toString()
    val capacity = state.capacitySummary(date)
    var minutes by remember { mutableStateOf("15") }
    var boundary by remember(state.settings.planningDayBoundaryMinutes) {
        mutableStateOf(state.settings.planningDayBoundaryMinutes.toString())
    }
    var dailyCapacity by remember(state.settings.dailyCapacityMinutes) {
        mutableStateOf(state.settings.dailyCapacityMinutes?.toString() ?: "")
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("今天工作台", style = MaterialTheme.typography.headlineMedium)
        Text("规划日界线 ${formatMinutes(state.settings.planningDayBoundaryMinutes)} · 每周从${weekName(state.settings.weekStartDay)}开始")
        Text("规划日：$date")
        if (!state.onboardingCompleted) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("欢迎使用 Attention", style = MaterialTheme.typography.titleMedium)
                    Text("先创建一个计划，再记录实际投入。所有数据只保存在本机。")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (state.targets.isEmpty()) viewModel.addTarget("我的第一个计划")
                            viewModel.finishOnboarding()
                        }) { Text("创建第一个计划") }
                        OutlinedButton(onClick = viewModel::finishOnboarding) { Text("跳过") }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("快速记录实际投入", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60).forEach { amount ->
                        OutlinedButton(onClick = { viewModel.addTime(date, amount, null) }) { Text("+$amount") }
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("分钟") }, modifier = Modifier.width(120.dp), singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { minutes.toIntOrNull()?.takeIf { it > 0 }?.let { viewModel.addTime(date, it, null) } }) { Text("记录未归属活动") }
                }
                Text("本规划日已记录：${state.timeEntries.filter { it.planningDate == date }.sumOf { it.durationMinutes }} 分钟")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("计时器", style = MaterialTheme.typography.titleMedium)
                val timer = state.activeTimer
                if (timer == null) {
                    Text("计时结束时会按规划日界线自动切分时间记录。")
                    Button(onClick = { viewModel.startTimer(state.targets.firstOrNull()?.id) }) { Text("开始计时") }
                } else {
                    Text(if (timer.paused) "计时已暂停" else "正在计时")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (timer.paused) Button(onClick = viewModel::resumeTimer) { Text("继续") }
                        else Button(onClick = viewModel::pauseTimer) { Text("暂停") }
                        OutlinedButton(onClick = viewModel::stopTimer) { Text("结束并保存") }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("每日容量", style = MaterialTheme.typography.titleMedium)
                Text(if (capacity.capacityMinutes == null) "未设置" else "已安排 ${capacity.usedMinutes} / ${capacity.capacityMinutes} 分钟${if (capacity.overloaded) "（超载）" else ""}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(boundary, { boundary = it.filter(Char::isDigit) }, label = { Text("界线分钟") }, modifier = Modifier.width(150.dp), singleLine = true)
                    OutlinedTextField(dailyCapacity, { dailyCapacity = it.filter(Char::isDigit) }, label = { Text("容量分钟") }, modifier = Modifier.width(150.dp), singleLine = true)
                }
                Button(onClick = {
                    val b = boundary.toIntOrNull()?.takeIf { it in 0 until 1440 } ?: state.settings.planningDayBoundaryMinutes
                    val c = dailyCapacity.toIntOrNull()?.takeIf { it > 0 }
                    viewModel.setSettings(StoredSettings(b, state.settings.weekStartDay, c))
                }) { Text("保存设置") }
            }
        }
        Text("今日安排", style = MaterialTheme.typography.titleMedium)
        state.schedules.filter { it.planningDate == date }.forEach { entry ->
            ScheduleRow(entry, viewModel)
        }
    }
}

@Composable
private fun TargetsScreen(state: AttentionState, viewModel: AttentionViewModel) {
    var title by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("目标树", style = MaterialTheme.typography.headlineMedium)
        Text("父目标显示整个子树的实际投入；归档目标会从默认列表隐藏。")
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(title, { title = it }, label = { Text(if (parentId == null) "新计划" else "新子计划") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (title.isNotBlank()) { viewModel.addTarget(title, parentId); title = "" } }) { Text("添加") }
        }
        if (parentId != null) {
            Text("当前父目标：${state.targets.firstOrNull { it.id == parentId }?.title ?: ""}")
            TextButton(onClick = { parentId = null }) { Text("改为根计划") }
        }
        TargetTree(state, viewModel, null, 0) { parentId = it }
        if (state.targets.none { !it.archived }) Text("还没有计划。")
    }
}

@Composable
private fun TargetTree(state: AttentionState, viewModel: AttentionViewModel, parentId: String?, depth: Int, chooseParent: (String) -> Unit) {
    state.targetChildren(parentId).forEach { target ->
        val children = state.targetChildren(target.id)
        Column(Modifier.padding(start = (depth * 16).dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(if (children.isEmpty()) "•" else if (target.expanded) "▾" else "▸")
                Spacer(Modifier.width(6.dp))
                Text(target.title, modifier = Modifier.weight(1f))
                Text("直接 ${state.directMinutes(target.id)} / 汇总 ${state.subtreeMinutes(target.id)} 分钟")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { viewModel.toggleTarget(target.id, !target.expanded) }) { Text(if (target.expanded) "折叠" else "展开") }
                TextButton(onClick = { chooseParent(target.id) }) { Text("添加子计划") }
                TextButton(onClick = { viewModel.archiveTarget(target.id) }) { Text("归档") }
            }
            if (target.expanded) TargetTree(state, viewModel, target.id, depth + 1, chooseParent)
        }
    }
}

@Composable
private fun AgendaScreen(state: AttentionState, viewModel: AttentionViewModel) {
    var date by remember { mutableStateOf(state.planningDate(Instant.now()).toString()) }
    var title by remember { mutableStateOf("") }
    var estimate by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("所有日期", style = MaterialTheme.typography.headlineMedium)
        Text("日程条目可带预计时长；它不会自动变成实际投入。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(date, { date = it }, label = { Text("规划日 YYYY-MM-DD") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(estimate, { estimate = it.filter(Char::isDigit) }, label = { Text("预计分钟") }, modifier = Modifier.width(110.dp), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("日程标题") }, modifier = Modifier.weight(1f), singleLine = true)
            Button(onClick = {
                if (title.isNotBlank()) {
                    viewModel.addSchedule(ScheduleEntry(planningDate = date, title = title, estimatedMinutes = estimate.toIntOrNull()))
                    title = ""; estimate = ""
                }
            }) { Text("添加日程") }
        }
        state.schedules.sortedBy { it.planningDate }.forEach { ScheduleRow(it, viewModel) }
        if (state.schedules.isEmpty()) Text("还没有日程条目。")
    }
}

@Composable
private fun ScheduleRow(entry: ScheduleEntry, viewModel: AttentionViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall)
                Text("${entry.planningDate} · ${entry.estimatedMinutes ?: 0} 分钟${if (entry.completed) " · 已完成" else ""}")
            }
            TextButton(onClick = { viewModel.updateSchedule(entry.copy(completed = !entry.completed)) }) { Text(if (entry.completed) "取消完成" else "完成") }
        }
    }
}

@Composable
private fun StatisticsScreen(state: AttentionState) {
    val date = state.planningDate(Instant.now()).toString()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("统计与成长", style = MaterialTheme.typography.headlineMedium)
        Text("规划日 $date：${state.timeEntries.filter { it.planningDate == date }.sumOf { it.durationMinutes }} 分钟")
        Text("未归属活动：${state.unownedMinutesForUi()} 分钟")
        Text("投入经验：${state.experience} · 等级 ${levelForExperience(state.experience)}")
        state.targets.filter { !it.archived }.forEach { target ->
            val total = state.subtreeMinutes(target.id)
            Text("${target.title}：$total 分钟")
            state.goalStages.filter { it.targetId == target.id }.forEach { stage ->
                val progress = state.progress(stage, LocalDate.parse(date))
                Text("  ${stage.cadence.label()} ${progress.actualMinutes}/${progress.targetMinutes} 分钟${if (progress.completed) " · 达成" else " · 缺口 ${progress.gapMinutes}"}")
            }
        }
        if (state.targets.isEmpty()) Text("创建计划后，这里会显示目标投入和阶段达成情况。")
    }
}

private fun AttentionState.unownedMinutesForUi(): Int = timeEntries.filter { it.targetId == null }.sumOf { it.durationMinutes }

private fun GoalCadence.label(): String = when (this) {
    GoalCadence.DAILY -> "每日"
    GoalCadence.WEEKLY -> "每周"
    GoalCadence.MONTHLY -> "每月"
    GoalCadence.ONE_TIME -> "一次性"
}

private fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun weekName(value: Int): String = when (value) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}
