package com.attention.app

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.attention.app.data.AttentionStateRepository
import com.attention.app.data.RoomAttentionStateRepository
import com.attention.app.data.backup.AttentionBackupCodec
import com.attention.app.data.backup.FileImportBackupStore
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.createSettingsStore
import com.attention.app.timer.AttentionTimerService
import com.attention.app.reminder.ReminderScheduler
import com.attention.domain.AttentionState
import com.attention.domain.GoalCadence
import com.attention.domain.FutureGoalRule
import com.attention.domain.GoalStage
import com.attention.domain.GoalStageSummary
import com.attention.domain.GoalSummary
import com.attention.domain.LaunchDestination
import com.attention.domain.Migration
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.domain.RecurrenceRule
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import com.attention.domain.capacitySummary
import com.attention.domain.directMinutes
import com.attention.domain.descendantIds
import com.attention.domain.effectiveStage
import com.attention.domain.goalStagesForTarget
import com.attention.domain.goalSummary
import com.attention.domain.levelForExperience
import com.attention.domain.planningDate
import com.attention.domain.periodStats
import com.attention.domain.progress
import com.attention.domain.progressRange
import com.attention.domain.subtreeMinutes
import com.attention.domain.targetChildren
import java.time.Instant
import java.time.LocalDate

private class WorkspaceViewModelFactory(
    private val repository: AttentionStateRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AttentionViewModel(repository) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceApp() {
    val context = LocalContext.current
    val database = remember { AttentionDatabase.create(context) }
    val repository = remember {
        RoomAttentionStateRepository(
            business = RoomBusinessDataRepository(database),
            settings = context.createSettingsStore(),
            backupStore = FileImportBackupStore(context),
        )
    }
    val viewModel: AttentionViewModel = viewModel(factory = WorkspaceViewModelFactory(repository))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var destination by remember {
        mutableStateOf(resolveLaunchDestination(state.launchDestination, state.lastOpenedDestination))
    }
    var jsonPreview by remember { mutableStateOf<String?>(null) }
    var csvPreview by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val encoded = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!encoded.isNullOrBlank()) {
                pendingImport = encoded
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(destination) { viewModel.saveLastOpened(destination) }

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
                LaunchDestination.STATISTICS -> StatisticsScreen(
                    state = state,
                    viewModel = viewModel,
                    onExportJson = { viewModel.exportJson { jsonPreview = it.take(800) } },
                    onExportCsv = { viewModel.exportCsv { time, schedules -> csvPreview = "时间记录\n$time\n日程\n$schedules" } },
                    onImportJson = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                )
            }
            jsonPreview?.let { Text("JSON 预览：\n$it", modifier = Modifier.padding(16.dp)) }
            csvPreview?.let { Text("CSV 预览：\n${it.take(800)}", modifier = Modifier.padding(16.dp)) }
        }
    }
    pendingImport?.let { encoded ->
        val preview = runCatching { AttentionBackupCodec.decode(encoded) }.getOrNull()
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入预览") },
            text = {
                Text(
                    preview?.let { "目标 ${it.targets.size} 个，阶段 ${it.goalStages.size} 个，时间记录 ${it.timeEntries.size} 条，日程 ${it.schedules.size} 条。导入前会自动备份当前数据。" }
                        ?: "JSON 无法解析，现有数据不会改变。",
                )
            },
            confirmButton = {
                TextButton(onClick = { if (preview != null) viewModel.importJson(encoded, false); pendingImport = null }) { Text("合并导入") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { if (preview != null) viewModel.importJson(encoded, true); pendingImport = null }) { Text("清空后恢复") }
                    TextButton(onClick = { pendingImport = null }) { Text("取消") }
                }
            },
        )
    }
}

@Composable
private fun WorkspaceTodayScreen(state: AttentionState, viewModel: AttentionViewModel) {
    val context = LocalContext.current
    fun startTimer(targetId: String?) {
        viewModel.startTimer(targetId)
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "通知权限未开启，计时仍会保存；锁屏控制需要开启通知权限。", Toast.LENGTH_LONG).show()
        }
        ContextCompat.startForegroundService(context, AttentionTimerService.intent(context))
    }
    val date = state.planningDate(Instant.now()).toString()
    val capacity = state.capacitySummary(date)
    var minutes by remember { mutableStateOf("15") }
    var manualTargetId by remember { mutableStateOf<String?>(null) }
    var boundary by remember(state.settings.planningDayBoundaryMinutes) {
        mutableStateOf(state.settings.planningDayBoundaryMinutes.toString())
    }
    var dailyCapacity by remember(state.settings.dailyCapacityMinutes) {
        mutableStateOf(state.settings.dailyCapacityMinutes?.toString() ?: "")
    }
    var editingEntry by remember { mutableStateOf<TimeEntry?>(null) }
    var editMinutes by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf("") }
    var editTargetId by remember { mutableStateOf<String?>(null) }
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
                state.targets.filter { !it.archived }.take(4).forEach { target ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(target.title, Modifier.weight(1f))
                        listOf(15, 30).forEach { amount ->
                            TextButton(onClick = { viewModel.addTime(date, amount, target.id) }) { Text("+$amount") }
                        }
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("分钟") }, modifier = Modifier.width(120.dp), singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { minutes.toIntOrNull()?.takeIf { it > 0 }?.let { viewModel.addTime(date, it, manualTargetId) } }) {
                        Text(if (manualTargetId == null) "记录未归属活动" else "记录到目标")
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { manualTargetId = null }) { Text("未归属") }
                    state.targets.filter { !it.archived }.forEach { target ->
                        TextButton(onClick = { manualTargetId = target.id }) { Text(target.title) }
                    }
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
                    Button(onClick = {
                        startTimer(null)
                    }) { Text("开始未归属计时") }
                    state.targets.filter { !it.archived }.take(4).forEach { target ->
                        OutlinedButton(onClick = {
                            startTimer(target.id)
                        }) { Text("计时：${target.title}") }
                    }
                } else {
                    Text(if (timer.paused) "计时已暂停" else "正在计时")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (timer.paused) Button(onClick = viewModel::resumeTimer) { Text("继续") }
                        else Button(onClick = viewModel::pauseTimer) { Text("暂停") }
                        OutlinedButton(onClick = {
                            viewModel.recoverTimer()
                            ContextCompat.startForegroundService(context, AttentionTimerService.intent(context))
                        }) { Text("从此刻继续") }
                        OutlinedButton(onClick = {
                            viewModel.stopTimer()
                            context.stopService(AttentionTimerService.intent(context))
                        }) { Text("结束并保存") }
                    }
                    state.targets.filter { !it.archived && it.id != timer.targetId }.take(3).forEach { target ->
                        OutlinedButton(onClick = { viewModel.switchTimer(target.id) }) { Text("切换到 ${target.title}") }
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
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("周起始日", style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..7).forEach { day ->
                        if (day == state.settings.weekStartDay) {
                            Button(onClick = {}) { Text(weekName(day)) }
                        } else {
                            OutlinedButton(onClick = { viewModel.setWeekStart(day) }) { Text(weekName(day)) }
                        }
                    }
                }
            }
        }
        Text("今日安排", style = MaterialTheme.typography.titleMedium)
        Text("实际投入记录", style = MaterialTheme.typography.titleMedium)
        state.timeEntries.filter { it.planningDate == date }.forEach { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${entry.durationMinutes} 分钟 · ${state.targets.firstOrNull { it.id == entry.targetId }?.title ?: "未归属活动"} · ${entry.source.name}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            editingEntry = entry
                            editMinutes = entry.durationMinutes.toString()
                            editNote = entry.note
                            editTargetId = entry.targetId
                        }, modifier = Modifier.testTag("edit-time-${entry.id}")) { Text("编辑") }
                        TextButton(onClick = { viewModel.deleteTime(entry.id) }, modifier = Modifier.testTag("delete-time-${entry.id}")) { Text("删除") }
                    }
                    if (editingEntry?.id == entry.id) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { editTargetId = null }) { Text("未归属") }
                            state.targets.filter { !it.archived }.forEach { target ->
                                TextButton(onClick = { editTargetId = target.id }) { Text(target.title) }
                            }
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            OutlinedTextField(editMinutes, { editMinutes = it.filter(Char::isDigit) }, label = { Text("分钟") }, modifier = Modifier.testTag("edit-time-minutes-${entry.id}").width(120.dp), singleLine = true)
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(editNote, { editNote = it }, label = { Text("备注") }, modifier = Modifier.width(180.dp), singleLine = true)
                            Spacer(Modifier.width(8.dp))
                            Button(modifier = Modifier.testTag("save-time-${entry.id}"), onClick = { editMinutes.toIntOrNull()?.takeIf { it > 0 }?.let { viewModel.editTime(entry.id, it, editTargetId, editNote); editingEntry = null } }) { Text("保存") }
                        }
                    }
                }
            }
        }
        state.schedules.filter { it.planningDate == date }.forEach { entry ->
            ScheduleRow(entry, viewModel)
        }
    }
}

@Composable
private fun TargetsScreen(state: AttentionState, viewModel: AttentionViewModel) {
    var title by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    var goalMinutes by remember { mutableStateOf("") }
    var goalCadence by remember { mutableStateOf(GoalCadence.DAILY) }
    var goalStartDate by remember { mutableStateOf(state.planningDate(Instant.now()).toString()) }
    var goalDueDate by remember { mutableStateOf("") }
    var selectedPlanningDate by remember { mutableStateOf(state.planningDate(Instant.now()).toString()) }
    var futureDate by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var futureMinutes by remember { mutableStateOf("") }
    var futureCadence by remember { mutableStateOf(GoalCadence.WEEKLY) }
    var appendGoalMinutes by remember { mutableStateOf("") }
    var appendGoalDueDate by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Target?>(null) }
    var renameTarget by remember { mutableStateOf<Target?>(null) }
    var renameText by remember { mutableStateOf("") }
    var movingTarget by remember { mutableStateOf<Target?>(null) }
    var selectedUnownedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("目标树", style = MaterialTheme.typography.headlineMedium)
        Text("父目标显示整个子树的实际投入；归档目标会从默认列表隐藏。")
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(title, { title = it }, label = { Text(if (parentId == null) "新计划" else "新子计划") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (title.isNotBlank()) { viewModel.addTarget(title, parentId); title = "" } }) { Text("添加") }
        }
        if (parentId != null) {
            androidx.compose.runtime.LaunchedEffect(parentId) {
                selectedPlanningDate = state.planningDate(Instant.now()).toString()
            }
            Text("当前父目标：${state.targets.firstOrNull { it.id == parentId }?.title ?: ""}")
            TextButton(onClick = { parentId = null }) { Text("改为根计划") }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("为这个计划设置时间目标", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GoalCadence.entries.forEach { value ->
                            if (goalCadence == value) Button(onClick = {}) { Text(value.label()) }
                            else OutlinedButton(onClick = { goalCadence = value }) { Text(value.label()) }
                        }
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(goalStartDate, { goalStartDate = it }, label = { Text("开始规划日") }, modifier = Modifier.width(145.dp), singleLine = true)
                        if (goalCadence == GoalCadence.ONE_TIME) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(goalDueDate, { goalDueDate = it }, label = { Text("截止规划日（可选）") }, modifier = Modifier.width(145.dp), singleLine = true)
                        }
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(goalMinutes, { goalMinutes = it.filter(Char::isDigit) }, label = { Text("目标分钟") }, modifier = Modifier.width(140.dp), singleLine = true)
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            goalMinutes.toIntOrNull()?.takeIf { it > 0 }?.let {
                                val dueDate = if (goalCadence == GoalCadence.ONE_TIME) {
                                    goalDueDate.trim().takeIf(String::isNotEmpty)
                                } else {
                                    null
                                }
                                viewModel.addGoal(parentId!!, goalCadence, it, goalStartDate, dueDate)
                                goalMinutes = ""
                            }
                        }) { Text("保存目标") }
                    }
                    val unownedEntries = state.timeEntries.filter { it.targetId == null }
                    if (unownedEntries.isNotEmpty()) {
                        val visibleIds = unownedEntries.map { it.id }.toSet()
                        val selected = selectedUnownedIds intersect visibleIds
                        Text("未归属活动 ${unownedEntries.size} 条，已选 ${selected.size} 条")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { selectedUnownedIds = visibleIds }) { Text("全选") }
                            TextButton(onClick = { selectedUnownedIds = emptySet() }) { Text("清除选择") }
                            Button(
                                enabled = selected.isNotEmpty(),
                                onClick = {
                                    viewModel.assignUnowned(selectedUnownedIds, parentId!!)
                                    selectedUnownedIds = emptySet()
                                },
                            ) { Text("归入当前计划") }
                        }
                        unownedEntries.forEach { entry ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(
                                    checked = entry.id in selected,
                                    onCheckedChange = { checked ->
                                        selectedUnownedIds = if (checked) selectedUnownedIds + entry.id else selectedUnownedIds - entry.id
                                    },
                                )
                                Text("${entry.planningDate} · ${entry.durationMinutes} 分钟")
                            }
                        }
                    }
                    Text("未来生效的目标规则", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(futureDate, { futureDate = it }, label = { Text("生效日") }, modifier = Modifier.width(145.dp), singleLine = true)
                        OutlinedTextField(futureMinutes, { futureMinutes = it.filter(Char::isDigit) }, label = { Text("未来分钟") }, modifier = Modifier.width(120.dp), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GoalCadence.entries.forEach { value ->
                            if (futureCadence == value) Button(onClick = {}) { Text(value.label()) }
                            else OutlinedButton(onClick = { futureCadence = value }) { Text(value.label()) }
                        }
                    }
                    Button(onClick = {
                        futureMinutes.toIntOrNull()?.takeIf { it > 0 }?.let {
                            viewModel.addFutureGoal(FutureGoalRule(targetId = parentId!!, cadence = futureCadence, targetMinutes = it, effectiveFrom = futureDate))
                            futureMinutes = ""
                        }
                    }) { Text("保存未来规则") }
                    state.futureGoalRules.filter { it.targetId == parentId }.forEach { rule ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("${rule.effectiveFrom} · ${rule.cadence.label()} ${rule.targetMinutes} 分钟", Modifier.weight(1f))
                            TextButton(onClick = { viewModel.cancelFutureGoal(rule.id) }) { Text("取消") }
                        }
                    }
                    val parsedPlanningDate = runCatching { LocalDate.parse(selectedPlanningDate) }.getOrNull()
                    val planningDate = parsedPlanningDate ?: state.planningDate(Instant.now())
                    val target = state.targets.firstOrNull { it.id == parentId }
                    val stages = state.goalStagesForTarget(parentId!!)
                    val oneTimeSummary = state.goalSummary(parentId!!, planningDate)
                    if (stages.isNotEmpty()) {
                        Text("当前目标进度", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            selectedPlanningDate,
                            { selectedPlanningDate = it },
                            label = { Text("当前规划日 YYYY-MM-DD") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        if (parsedPlanningDate == null) {
                            Text("规划日格式无效，请使用 YYYY-MM-DD", color = MaterialTheme.colorScheme.error)
                        }
                        if (oneTimeSummary.stageProgresses.isNotEmpty()) {
                            Text(oneTimeSummary.summaryLabel())
                        }
                        stages.forEach { stage ->
                            val detail = oneTimeSummary.stageProgresses.firstOrNull { it.stage.id == stage.id }
                            val progress = state.progress(stage, planningDate)
                            Text(detail?.let { state.goalProgressLabel(it, planningDate) } ?: state.goalProgressLabel(stage, planningDate))
                            if (progress.gapMinutes > 0) {
                                OutlinedButton(onClick = {
                                    viewModel.addMigration(Migration(targetId = parentId!!, sourceStageId = stage.id, minutes = progress.gapMinutes, destinationStartDate = planningDate.plusDays(7).toString()))
                                }) { Text("将当前缺口 ${progress.gapMinutes} 分钟迁移到下周") }
                            }
                        }
                        val latest = stages.last()
                        val latestProgress = state.progress(latest, planningDate)
                        if (parsedPlanningDate != null && target?.archived == false && latest.cadence == GoalCadence.ONE_TIME && latestProgress.completed) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("追加一次性阶段", style = MaterialTheme.typography.titleSmall)
                                    Text("起始规划日：$planningDate")
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            appendGoalMinutes,
                                            { appendGoalMinutes = it.filter(Char::isDigit) },
                                            label = { Text("目标分钟") },
                                            modifier = Modifier.testTag("append-goal-minutes").width(140.dp),
                                            singleLine = true,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        OutlinedTextField(
                                            appendGoalDueDate,
                                            { appendGoalDueDate = it },
                                            label = { Text("截止规划日（可选）") },
                                            modifier = Modifier.width(155.dp),
                                            singleLine = true,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Button(
                                            modifier = Modifier.testTag("append-goal-submit"),
                                            onClick = {
                                            viewModel.appendOneTimeGoal(
                                                targetId = parentId!!,
                                                minutes = appendGoalMinutes.toIntOrNull() ?: 0,
                                                startDate = planningDate.toString(),
                                                dueDate = appendGoalDueDate.trim().takeIf(String::isNotEmpty),
                                                onSuccess = {
                                                    appendGoalMinutes = ""
                                                    appendGoalDueDate = ""
                                                },
                                            )
                                            },
                                        ) { Text("追加阶段") }
                                    }
                                }
                            }
                        }
                    }
                    if (target?.parentId != null) {
                        OutlinedButton(onClick = { viewModel.moveTargetFrom(target.id, null, state.planningDate(Instant.now()).plusDays(1).toString()) }) { Text("从明日起移到根计划") }
                    }
                }
            }
        }
        renameTarget?.let { target ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(renameText, { renameText = it }, label = { Text("重命名 ${target.title}") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (renameText.isNotBlank()) viewModel.renameTarget(target.id, renameText); renameTarget = null }) { Text("保存") }
            }
        }
        movingTarget?.let { target ->
            val excludedIds = state.descendantIds(target.id)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("移动 ${target.title}：选择新的父目标", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = { viewModel.moveTarget(target.id, null); movingTarget = null }) { Text("移到根计划") }
                    state.targets
                        .filter { !it.archived && it.id !in excludedIds }
                        .sortedBy { state.targetPath(it.id) }
                        .forEach { candidate ->
                            OutlinedButton(onClick = { viewModel.moveTarget(target.id, candidate.id); movingTarget = null }) {
                                Text("移到：${state.targetPath(candidate.id)}")
                            }
                        }
                    TextButton(onClick = { movingTarget = null }) { Text("取消移动") }
                }
            }
        }
        TargetTree(
            state,
            viewModel,
            null,
            0,
            chooseParent = { parentId = it },
            rename = { target -> renameTarget = target; renameText = target.title },
            move = { movingTarget = it },
        )
        if (state.targets.none { !it.archived }) Text("还没有计划。")
        if (state.targets.any { it.archived }) {
            Text("已归档目标", style = MaterialTheme.typography.titleMedium)
            state.targets.filter { it.archived }.forEach { target ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(target.title, Modifier.weight(1f))
                        TextButton(onClick = { viewModel.restoreTarget(target.id) }) { Text("恢复") }
                        TextButton(onClick = { pendingDelete = target }) { Text("安全删除") }
                    }
                    val planningDate = state.planningDate(Instant.now())
                    val stages = state.goalStagesForTarget(target.id)
                    val oneTimeSummary = state.goalSummary(target.id, planningDate)
                    if (oneTimeSummary.stageProgresses.isNotEmpty()) {
                        Text("历史进度：${oneTimeSummary.summaryLabel()}", modifier = Modifier.padding(start = 16.dp))
                    }
                    stages.forEach { stage ->
                        val detail = oneTimeSummary.stageProgresses.firstOrNull { it.stage.id == stage.id }
                        Text(
                            "历史进度：${detail?.let { state.goalProgressLabel(it, planningDate) } ?: state.goalProgressLabel(stage, planningDate)}",
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        }
    }
    pendingDelete?.let { target ->
        val affectedTime = state.timeEntries.count { it.targetId in state.descendantIds(target.id) }
        val affectedSchedules = state.schedules.count { it.targetId in state.descendantIds(target.id) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除目标关系？") },
            text = { Text("将移除目标树关系，保留 $affectedTime 条时间记录并解除 $affectedSchedules 条日程的目标关联。历史经验和里程碑保留。") },
            confirmButton = { TextButton(onClick = { viewModel.deleteTargetRelations(setOf(target.id)); pendingDelete = null }) { Text("确认删除") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun TargetTree(
    state: AttentionState,
    viewModel: AttentionViewModel,
    parentId: String?,
    depth: Int,
    chooseParent: (String) -> Unit,
    rename: (Target) -> Unit,
    move: (Target) -> Unit,
) {
    state.targetChildren(parentId).forEach { target ->
        val children = state.targetChildren(target.id)
        val planningDate = state.planningDate(Instant.now())
        Column(Modifier.padding(start = (depth * 16).dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(if (children.isEmpty()) "•" else if (target.expanded) "▾" else "▸")
                Spacer(Modifier.width(6.dp))
                Text(target.title, modifier = Modifier.weight(1f))
                Text("直接 ${state.directMinutes(target.id)} / 汇总 ${state.subtreeMinutes(target.id)} 分钟")
            }
            val stages = state.goalStagesForTarget(target.id)
            val oneTimeSummary = state.goalSummary(target.id, planningDate)
            if (oneTimeSummary.stageProgresses.isNotEmpty()) {
                Text(oneTimeSummary.summaryLabel(), modifier = Modifier.padding(start = 22.dp))
            }
            stages.forEach { stage ->
                val detail = oneTimeSummary.stageProgresses.firstOrNull { it.stage.id == stage.id }
                Text(
                    detail?.let { state.goalProgressLabel(it, planningDate) } ?: state.goalProgressLabel(stage, planningDate),
                    modifier = Modifier.padding(start = 22.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { viewModel.toggleTarget(target.id, !target.expanded) }) { Text(if (target.expanded) "折叠" else "展开") }
                TextButton(onClick = { chooseParent(target.id) }) { Text("添加子计划") }
                TextButton(onClick = { rename(target) }) { Text("重命名") }
                TextButton(onClick = { move(target) }) { Text("移动") }
                val siblings = state.targetChildren(parentId)
                val index = siblings.indexOf(target)
                if (index > 0) TextButton(onClick = { viewModel.reorderTarget(target.id, index - 1) }) { Text("上移") }
                if (index < siblings.lastIndex) TextButton(onClick = { viewModel.reorderTarget(target.id, index + 1) }) { Text("下移") }
                TextButton(onClick = { viewModel.archiveTarget(target.id) }) { Text("归档") }
            }
            if (target.expanded) TargetTree(state, viewModel, target.id, depth + 1, chooseParent, rename, move)
        }
    }
}

@Composable
private fun AgendaScreen(state: AttentionState, viewModel: AttentionViewModel) {
    var date by remember { mutableStateOf(state.planningDate(Instant.now()).toString()) }
    var title by remember { mutableStateOf("") }
    var estimate by remember { mutableStateOf("") }
    var recurrenceTitle by remember { mutableStateOf("") }
    var recurrenceFrequency by remember { mutableStateOf(ScheduleFrequency.DAILY) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("所有日期", style = MaterialTheme.typography.headlineMedium)
        Text("日程条目可带预计时长；它不会自动变成实际投入。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(date, { date = it }, label = { Text("规划日 YYYY-MM-DD") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(estimate, { estimate = it.filter(Char::isDigit) }, label = { Text("预计分钟") }, modifier = Modifier.width(110.dp), singleLine = true)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("重复日程", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(recurrenceTitle, { recurrenceTitle = it }, label = { Text("重复标题") }, modifier = Modifier.weight(1f), singleLine = true)
                    ScheduleFrequency.entries.forEach { value ->
                        if (recurrenceFrequency == value) Button(onClick = {}) { Text(value.label()) }
                        else OutlinedButton(onClick = { recurrenceFrequency = value }) { Text(value.label()) }
                    }
                }
                Button(onClick = {
                    if (recurrenceTitle.isNotBlank()) {
                        val rule = RecurrenceRule(title = recurrenceTitle, startDate = date, frequency = recurrenceFrequency, weekdays = if (recurrenceFrequency == ScheduleFrequency.WEEKLY) listOf(LocalDate.parse(date).dayOfWeek.value) else emptyList())
                        viewModel.addRecurrence(rule)
                        viewModel.materializeRecurrence(rule, LocalDate.parse(date).plusDays(30))
                        recurrenceTitle = ""
                    }
                }) { Text("创建并生成未来 30 天") }
                state.recurrenceRules.forEach { rule ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("${rule.title} · ${rule.frequency.label()}", Modifier.weight(1f))
                        if (rule.active) TextButton(onClick = { viewModel.stopRecurrence(rule.id) }) { Text("停止未来生成") }
                        else Text("已停止")
                    }
                }
            }
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
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall)
                Text("${entry.planningDate} · ${entry.estimatedMinutes ?: 0} 分钟${if (entry.completed) " · 已完成" else ""}")
            }
            TextButton(onClick = { viewModel.updateSchedule(entry.copy(completed = !entry.completed)) }) { Text(if (entry.completed) "取消完成" else "完成") }
            TextButton(onClick = {
                val updated = entry.copy(reminderEpochMillis = if (entry.reminderEpochMillis == null) System.currentTimeMillis() + 3_600_000 else null)
                viewModel.updateSchedule(updated)
                if (updated.reminderEpochMillis == null) ReminderScheduler(context).cancel(entry.id) else ReminderScheduler(context).schedule(updated)
            }) { Text(if (entry.reminderEpochMillis == null) "提醒" else "取消提醒") }
        }
    }
}

@Composable
private fun StatisticsScreen(
    state: AttentionState,
    viewModel: AttentionViewModel,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImportJson: () -> Unit,
) {
    val date = state.planningDate(Instant.now()).toString()
    var cadence by remember { mutableStateOf(GoalCadence.DAILY) }
    val stats = state.periodStats(LocalDate.parse(date), cadence)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("统计与成长", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalCadence.entries.forEach { value ->
                if (cadence == value) Button(onClick = {}) { Text(value.label()) }
                else OutlinedButton(onClick = { cadence = value }) { Text(value.label()) }
            }
        }
        Text("${stats.range.start} 至 ${stats.range.endInclusive}：实际 ${stats.actualMinutes} 分钟 · 目标 ${stats.targetMinutes} 分钟 · 缺口 ${stats.targetMinutes.minus(stats.actualMinutes).coerceAtLeast(0)} 分钟 · 超额 ${stats.excessMinutes} 分钟")
        Text("未归属活动：${state.unownedMinutesForUi()} 分钟")
        Text("迁移目标：${stats.migrationMinutes} 分钟")
        state.migrations.filterNot { it.cancelled }.forEach { migration ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("主动迁移 ${migration.minutes} 分钟 · ${migration.destinationStartDate}", Modifier.weight(1f))
                TextButton(onClick = { viewModel.cancelMigration(migration.id) }) { Text("取消") }
            }
        }
        state.periodSnapshots.takeLast(5).forEach { snapshot ->
            Text("周期快照 ${snapshot.periodStart}～${snapshot.periodEnd}：${snapshot.actualMinutes}/${snapshot.targetMinutes} 分钟${if (snapshot.completed) " · 达成" else " · 缺口 ${snapshot.gapMinutes}"}")
        }
        Text("投入经验：${state.experience} · 等级 ${levelForExperience(state.experience)}")
        Button(onClick = viewModel::awardMilestones) { Text("结算可达成里程碑") }
        state.milestones.takeLast(8).forEach { milestone ->
            Text("里程碑 ${milestone.kind}：+${milestone.reward}（${milestone.instanceKey}）")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExportJson) { Text("预览 JSON 备份") }
            OutlinedButton(onClick = onImportJson) { Text("导入 JSON") }
            OutlinedButton(onClick = onExportCsv) { Text("预览 CSV") }
        }
        Text("首屏入口", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(LaunchDestination.TODAY to "今天", LaunchDestination.TARGETS to "目标树", LaunchDestination.ALL_DATES to "所有日期", LaunchDestination.STATISTICS to "统计", LaunchDestination.LAST_OPENED to "上次打开").forEach { (value, label) ->
                OutlinedButton(onClick = { viewModel.setLaunchDestination(value) }) { Text(label) }
            }
        }
        state.targets.filter { !it.archived }.forEach { target ->
            val total = state.subtreeMinutes(target.id)
            Text("${target.title}：$total 分钟")
            val planningDate = LocalDate.parse(date)
            val stages = state.goalStagesForTarget(target.id)
            val oneTimeSummary = state.goalSummary(target.id, planningDate)
            if (oneTimeSummary.stageProgresses.isNotEmpty()) {
                Text(oneTimeSummary.summaryLabel())
            }
            stages.forEach { stage ->
                val detail = oneTimeSummary.stageProgresses.firstOrNull { it.stage.id == stage.id }
                Text(detail?.let { state.goalProgressLabel(it, planningDate) } ?: state.goalProgressLabel(stage, planningDate))
            }
        }
        if (state.targets.isEmpty()) Text("创建计划后，这里会显示目标投入和阶段达成情况。")
    }
}

private fun resolveLaunchDestination(requested: LaunchDestination, lastOpened: LaunchDestination): LaunchDestination = when (requested) {
    LaunchDestination.LAST_OPENED -> if (lastOpened == LaunchDestination.LAST_OPENED) LaunchDestination.TODAY else lastOpened
    else -> requested
}

private fun AttentionState.unownedMinutesForUi(): Int = timeEntries.filter { it.targetId == null }.sumOf { it.durationMinutes }

private fun AttentionState.goalProgressLabel(stage: GoalStage, planningDate: LocalDate): String =
    goalProgressLabel(GoalStageSummary(stage, progress(stage, planningDate)), planningDate)

private fun AttentionState.goalProgressLabel(detail: GoalStageSummary, planningDate: LocalDate): String {
    val stage = detail.stage
    val effectiveStage = effectiveStage(stage, planningDate)
    val progress = detail.progress
    val progressRange = progressRange(stage, planningDate)
    val stageStart = LocalDate.parse(effectiveStage.startDate)
    val rangeLabel = when (effectiveStage.cadence) {
        GoalCadence.ONE_TIME -> {
            val boundaryLabel = when {
                effectiveStage.dueDate != null -> " · 截止 ${effectiveStage.dueDate}"
                progressRange.endInclusive.isBefore(planningDate) -> " · 阶段封闭于 ${progressRange.endInclusive}"
                else -> " · 开放"
            }
            "范围 ${progressRange.start} 至 ${progressRange.endInclusive}$boundaryLabel"
        }
        else -> "周期 ${progressRange.start} 至 ${progressRange.endInclusive}"
    }
    return if (planningDate.isBefore(stageStart)) {
        "${effectiveStage.cadence.label()}目标 0/${effectiveStage.targetMinutes} 分钟 · 尚未开始 · 生效日 $stageStart · $rangeLabel"
    } else {
        val status = when {
            progress.excessMinutes > 0 -> "达成 · 超额 ${progress.excessMinutes} 分钟"
            progress.completed -> "达成"
            else -> "缺口 ${progress.gapMinutes} 分钟"
        }
        "${effectiveStage.cadence.label()}目标 ${progress.actualMinutes}/${progress.targetMinutes} 分钟 · $rangeLabel · $status"
    }
}

private fun GoalSummary.summaryLabel(): String =
    "一次性阶段汇总：实际 ${actualMinutes}/${targetMinutes} 分钟 · 缺口 ${gapMinutes} 分钟 · 超额投入 ${excessMinutes} 分钟 · ${if (completed) "达成" else "未达成"}"

private fun AttentionState.targetPath(targetId: String): String {
    val names = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    var current = targets.firstOrNull { it.id == targetId }
    while (current != null && seen.add(current.id)) {
        names += current.title
        current = current.parentId?.let { parentId -> targets.firstOrNull { it.id == parentId } }
    }
    return names.asReversed().joinToString(" / ")
}

private fun GoalCadence.label(): String = when (this) {
    GoalCadence.DAILY -> "每日"
    GoalCadence.WEEKLY -> "每周"
    GoalCadence.MONTHLY -> "每月"
    GoalCadence.ONE_TIME -> "一次性"
}

private fun ScheduleFrequency.label(): String = when (this) {
    ScheduleFrequency.DAILY -> "每日"
    ScheduleFrequency.WEEKLY -> "每周"
    ScheduleFrequency.MONTHLY -> "每月"
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
