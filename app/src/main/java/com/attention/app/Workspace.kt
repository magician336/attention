package com.attention.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import java.io.OutputStreamWriter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.attention.app.data.backup.BackupPreview
import com.attention.app.data.backup.FileImportBackupStore
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.createSettingsStore
import com.attention.app.timer.AttentionTimerService
import com.attention.app.timer.AttentionBootReceiver
import com.attention.app.reminder.AlarmReminderCoordinator
import com.attention.app.reminder.ReminderScheduler
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionConfirmDialog
import com.attention.app.ui.components.AttentionFeedbackHostState
import com.attention.app.ui.components.AttentionInlineError
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.components.AttentionPermissionState
import com.attention.app.ui.components.AttentionSelectedIndicator
import com.attention.app.ui.components.AttentionSnackbarHost
import com.attention.app.ui.components.AttentionTextButton
import com.attention.app.ui.components.rememberAttentionFeedbackHostState
import com.attention.app.ui.format.DurationFormat
import com.attention.app.ui.format.TimeEntrySourceFormat
import com.attention.app.ui.format.TimeRecordTags
import com.attention.app.ui.theme.attentionTokens
import com.attention.app.settings.SettingsScreen
import com.attention.app.statistics.StatisticsScreen
import com.attention.app.statistics.StatisticsSelection
import com.attention.app.statistics.rememberStatisticsSelection
import com.attention.workspace.state.SettingsActions
import com.attention.workspace.state.StatisticsActions
import com.attention.workspace.state.goalProgressLabel
import com.attention.workspace.state.label
import com.attention.workspace.state.settingsScreenState
import com.attention.workspace.state.stageFeedbackLabels
import com.attention.workspace.state.statisticsScreenState
import com.attention.workspace.state.summaryLabel
import com.attention.domain.AttentionState
import com.attention.domain.CapacitySummary
import com.attention.domain.availableMigrationMinutes
import com.attention.domain.GoalCadence
import com.attention.domain.FutureGoalRule
import com.attention.domain.GoalStage
import com.attention.domain.LaunchDestination
import com.attention.domain.Migration
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.domain.RecurrenceRule
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.targetDeletionImpact
import com.attention.domain.TargetMove
import com.attention.domain.TimeEntry
import com.attention.domain.capacitySummary
import com.attention.domain.agendaEntries
import com.attention.domain.directMinutes
import com.attention.domain.descendantIds
import com.attention.domain.futureTargetMoveParents
import com.attention.domain.goalRulesAt
import com.attention.domain.isFutureTargetMoveDate
import com.attention.domain.isPending
import com.attention.domain.goalStagesForTarget
import com.attention.domain.goalSummary
import com.attention.domain.parseTargetMoveDate
import com.attention.domain.pendingTargetMoves
import com.attention.domain.planningDate
import com.attention.domain.parentAt
import com.attention.domain.progress
import com.attention.domain.progressRange
import com.attention.domain.reminderEpochMillis
import com.attention.domain.resolveLaunchDestination
import com.attention.domain.subtreeMinutes
import com.attention.domain.targetChildren
import com.attention.domain.timerSettlementPreview
import com.attention.domain.timerTargetLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 设置页的 actions adapter，在组合根一侧组装：页面发出 intent，这里落到 ViewModel。
 * ViewModel、Room 与 DataStore 因此只出现在组合根侧，页面拿不到它们。
 */
private class SettingsActionsAdapter(
    private val viewModel: AttentionViewModel,
) : SettingsActions {
    override fun selectLaunchDestination(destination: LaunchDestination) {
        viewModel.setLaunchDestination(destination)
    }
}

private class WorkspaceViewModelFactory(
    private val repository: AttentionStateRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AttentionViewModel(repository) as T
}

/**
 * 统计页的 actions adapter，在组合根一侧组装：页面发出 intent，这里落到 ViewModel、
 * 周期位移与 SAF 文件选择器。ViewModel 与平台能力因此只出现在组合根侧。
 */
private class StatisticsActionsAdapter(
    private val viewModel: AttentionViewModel,
    private val selection: StatisticsSelection,
    private val currentDate: () -> LocalDate,
    private val onPreviewJsonBackup: () -> Unit,
    private val onImportJson: () -> Unit,
    private val onExportTimeEntriesCsv: () -> Unit,
    private val onPreviewCsv: () -> Unit,
    private val onExportSchedulesCsv: () -> Unit,
    private val onExportCsvBundle: () -> Unit,
) : StatisticsActions {
    override fun selectPreviousPeriod() = shift(-1)

    override fun selectNextPeriod() = shift(1)

    private fun shift(amount: Long) {
        selection.shiftPeriod(amount, currentDate())
    }

    override fun selectCadence(cadence: GoalCadence) {
        selection.selectCadence(cadence)
    }

    override fun settlePeriodSnapshots() {
        viewModel.settlePeriodSnapshots()
    }

    override fun cancelMigration(migrationId: String) {
        viewModel.cancelMigration(migrationId)
    }

    override fun awardMilestones() {
        viewModel.awardMilestones()
    }

    override fun selectLaunchDestination(destination: LaunchDestination) {
        viewModel.setLaunchDestination(destination)
    }

    override fun restoreLastImportBackup() {
        viewModel.restoreLastImportBackup()
    }

    override fun previewJsonBackup() = onPreviewJsonBackup()

    override fun importJson() = onImportJson()

    override fun exportTimeEntriesCsv() = onExportTimeEntriesCsv()

    override fun previewCsv() = onPreviewCsv()

    override fun exportSchedulesCsv() = onExportSchedulesCsv()

    override fun exportCsvBundle() = onExportCsvBundle()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceApp(openScheduleDate: String? = null, openScheduleId: String? = null) {
    val context = LocalContext.current
    val database = remember { AttentionDatabase.create(context) }
    val repository = remember {
        RoomAttentionStateRepository(
            business = RoomBusinessDataRepository(database),
            settings = context.createSettingsStore(),
            backupStore = FileImportBackupStore(context),
            reminderCoordinator = AlarmReminderCoordinator(ReminderScheduler(context)),
        )
    }
    val viewModel: AttentionViewModel = viewModel(factory = WorkspaceViewModelFactory(repository))
    val settingsActions: SettingsActions = remember(viewModel) { SettingsActionsAdapter(viewModel) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val tokens = attentionTokens()
    // 短时提示的宿主状态只创建一次，挂在页面切换之上，因此五个页面共用同一个提示出口。
    val feedback = rememberAttentionFeedbackHostState()
    val feedbackScope = rememberCoroutineScope()
    val hasExternalScheduleRequest = openScheduleDate != null || openScheduleId != null
    var destination by remember {
        mutableStateOf(initialLaunchDestination(hasExternalScheduleRequest, state.launchDestination, state.lastOpenedDestination))
    }
    var destinationChosen by remember { mutableStateOf(hasExternalScheduleRequest) }
    var settingsOpen by remember { mutableStateOf(false) }
    var persistedSettingsLoaded by remember { mutableStateOf(false) }
    var selectedAgendaDate by remember { mutableStateOf(openScheduleDate) }
    var jsonPreview by remember { mutableStateOf<String?>(null) }
    var pendingJsonBackup by remember { mutableStateOf<String?>(null) }
    var jsonExportMessage by remember { mutableStateOf<String?>(null) }
    var pendingTimeEntriesCsv by remember { mutableStateOf<String?>(null) }
    var pendingCsvBundle by remember { mutableStateOf<AttentionViewModel.CsvExportBundle?>(null) }
    var csvBundleMessage by remember { mutableStateOf<String?>(null) }
    val timeEntriesCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        val csv = pendingTimeEntriesCsv
        pendingTimeEntriesCsv = null
        if (uri == null) {
            feedbackScope.launch { feedback.showMessage("已取消保存时间记录 CSV") }
        } else if (csv == null) {
            feedbackScope.launch { feedback.showMessage("时间记录 CSV 导出失败") }
        } else {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    writeUtf8Csv(csv, output)
                } ?: error("无法打开保存位置")
            }.onSuccess {
                feedbackScope.launch { feedback.showMessage("时间记录 CSV 已保存") }
            }.onFailure {
                feedbackScope.launch { feedback.showMessage("时间记录 CSV 保存失败：${it.message ?: "未知错误"}") }
            }
        }
    }
    val jsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val json = pendingJsonBackup
        pendingJsonBackup = null
        if (uri == null) {
            jsonExportMessage = "已取消保存 JSON 备份"
        } else if (json == null) {
            jsonExportMessage = "JSON 备份导出失败：没有可保存的数据"
        } else {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.let { output ->
                    writeUtf8Json(json, output)
                } ?: error("无法打开保存位置")
            }.onSuccess {
                jsonExportMessage = "JSON 备份已保存"
            }.onFailure {
                jsonExportMessage = "JSON 备份保存失败：${it.message ?: "无法写入文件"}"
            }
        }
    }
    var csvPreview by remember { mutableStateOf<String?>(null) }
    var pendingScheduleCsv by remember { mutableStateOf<String?>(null) }
    var scheduleExportMessage by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var pendingClearImport by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("无法读取所选文件")
            }
            result.onSuccess { encoded ->
                if (encoded.isNotBlank()) pendingImport = encoded else jsonExportMessage = "导入失败：文件为空"
            }.onFailure {
                jsonExportMessage = "导入失败：${it.message ?: "无法读取文件"}"
            }
        }
    }
    val scheduleExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = pendingScheduleCsv
        pendingScheduleCsv = null
        if (uri == null) {
            scheduleExportMessage = "已取消日程 CSV 导出"
        } else if (csv == null) {
            scheduleExportMessage = "日程 CSV 导出失败：没有可保存的数据"
        } else {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("无法打开保存位置")
                output.use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).use { writer -> writer.write(csv) }
                }
            }.onSuccess {
                scheduleExportMessage = "日程 CSV 已保存"
            }.onFailure {
                scheduleExportMessage = "日程 CSV 保存失败：${it.message ?: "无法写入文件"}"
            }
        }
    }
    val combinedScheduleExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val bundle = pendingCsvBundle
        pendingCsvBundle = null
        if (uri == null) {
            csvBundleMessage = "时间记录 CSV 已保存；已取消保存日程 CSV"
        } else if (bundle == null) {
            csvBundleMessage = "时间记录 CSV 已保存；日程 CSV 导出失败"
        } else {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output -> writeUtf8Csv(bundle.schedules, output) }
                    ?: error("无法打开保存位置")
            }.onSuccess {
                csvBundleMessage = "时间记录 CSV 和日程 CSV 已保存"
            }.onFailure {
                csvBundleMessage = "时间记录 CSV 已保存；日程 CSV 保存失败：${it.message ?: "未知错误"}"
            }
        }
    }
    val combinedTimeExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val bundle = pendingCsvBundle
        if (uri == null) {
            csvBundleMessage = "已取消保存时间记录 CSV；未开始保存日程 CSV"
            pendingCsvBundle = null
        } else if (bundle == null) {
            csvBundleMessage = "时间记录 CSV 导出失败；未开始保存日程 CSV"
            pendingCsvBundle = null
        } else {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output -> writeUtf8Csv(bundle.timeEntries, output) }
                    ?: error("无法打开保存位置")
            }.onSuccess {
                csvBundleMessage = "时间记录 CSV 已保存；请选择日程 CSV 的保存位置"
                combinedScheduleExportLauncher.launch("attention-schedules.csv")
            }.onFailure {
                csvBundleMessage = "时间记录 CSV 保存失败：${it.message ?: "未知错误"}；未开始保存日程 CSV"
                pendingCsvBundle = null
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(state.launchDestination, state.lastOpenedDestination) {
        if (!destinationChosen && !hasExternalScheduleRequest) {
            destination = resolveLaunchDestination(state.launchDestination, state.lastOpenedDestination)
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        context.createSettingsStore().settings.first()
        persistedSettingsLoaded = true
    }
    androidx.compose.runtime.LaunchedEffect(destination, settingsOpen, persistedSettingsLoaded) {
        if (shouldPersistLastOpened(persistedSettingsLoaded, settingsOpen)) viewModel.saveLastOpened(destination)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.restoreReminders() }

    // 系统时钟只在组合根读一次，所有页面投影共用这一份 now 与 zone（ADR-0005 第 3 节）。
    val injectedNow = Instant.now()
    val injectedZone = ZoneId.systemDefault()

    // 统计页的周期选择由统计页 module 提供类型与迁移语义，组合根只持有一个实例来喂投影。
    val statisticsSelection = rememberStatisticsSelection()
    val statisticsState = statisticsScreenState(
        state = state,
        now = injectedNow,
        zone = injectedZone,
        selectedDate = statisticsSelection.selectedDate,
        cadence = statisticsSelection.cadence,
    )
    val currentStatisticsDate by androidx.compose.runtime.rememberUpdatedState(statisticsState.selectedDate)
    val statisticsActions: StatisticsActions = remember(viewModel) {
        StatisticsActionsAdapter(
            viewModel = viewModel,
            selection = statisticsSelection,
            currentDate = { currentStatisticsDate },
            onPreviewJsonBackup = {
                jsonExportMessage = null
                viewModel.exportJson {
                    jsonPreview = it.take(800)
                    pendingJsonBackup = it
                    jsonExportLauncher.launch("attention-backup.json")
                }
            },
            onImportJson = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
            onExportTimeEntriesCsv = {
                viewModel.exportTimeEntriesCsv { csv ->
                    pendingTimeEntriesCsv = csv
                    timeEntriesCsvLauncher.launch("time_entries.csv")
                }
            },
            onPreviewCsv = { viewModel.exportCsv { time, schedules -> csvPreview = "时间记录\n$time\n日程\n$schedules" } },
            onExportSchedulesCsv = {
                scheduleExportMessage = null
                viewModel.exportSchedulesCsv { csv ->
                    pendingScheduleCsv = csv
                    scheduleExportLauncher.launch("attention-schedules.csv")
                }
            },
            onExportCsvBundle = {
                csvBundleMessage = null
                viewModel.exportCsvBundle { bundle ->
                    pendingCsvBundle = bundle
                    combinedTimeExportLauncher.launch("time_entries.csv")
                }
            },
        )
    }

    fun startTimerFromSchedule(entry: ScheduleEntry) {
        viewModel.startTimer(
            targetId = entry.targetId,
            onSuccess = {
                AttentionBootReceiver.cancelRecoveryPrompt(context)
                if (!notificationsEnabled(context)) {
                    feedbackScope.launch {
                        feedback.showMessage("通知权限未开启，计时仍会保存；通知栏控制不可用。")
                    }
                }
                ContextCompat.startForegroundService(context, AttentionTimerService.intent(context))
            },
            scheduleEntryId = entry.id,
        )
    }

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
                    if (destination == item && !settingsOpen) AttentionButton(onClick = { settingsOpen = false; destinationChosen = true }) { Text(label) }
                    else AttentionOutlinedButton(onClick = { settingsOpen = false; destinationChosen = true; destination = item }) { Text(label) }
                }
                if (settingsOpen) AttentionButton(onClick = {}) { Text("设置") }
                else AttentionOutlinedButton(onClick = { settingsOpen = true }) { Text("设置") }
            }
        },
    // 短时提示宿主挂在 Scaffold 上，因此五个页面共用同一个出口，流程不被打断。
        snackbarHost = { AttentionSnackbarHost(state = feedback) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            error?.let {
                AttentionInlineError(
                    message = it,
                    retryLabel = "关闭提示",
                    onRetry = viewModel::clearError,
                    modifier = Modifier.padding(horizontal = tokens.spacing.large),
                )
            }
            if (settingsOpen) {
                SettingsScreen(
                    state = settingsScreenState(
                        state = state,
                        now = injectedNow,
                        zone = injectedZone,
                    ),
                    actions = settingsActions,
                )
            } else when (destination) {
                LaunchDestination.TODAY, LaunchDestination.LAST_OPENED -> WorkspaceTodayScreen(
                    state = state,
                    viewModel = viewModel,
                    feedback = feedback,
                )
                LaunchDestination.TARGETS -> TargetsScreen(state, viewModel)
                LaunchDestination.ALL_DATES -> AgendaScreen(
                    state = state,
                    viewModel = viewModel,
                    selectedDate = selectedAgendaDate,
                    onSelectedDateChange = { selectedAgendaDate = it },
                    openScheduleId = openScheduleId,
                    onStartTimer = ::startTimerFromSchedule,
                )
                LaunchDestination.STATISTICS -> StatisticsScreen(
                    state = statisticsState,
                    actions = statisticsActions,
                )
            }
            jsonPreview?.let { Text("JSON 预览：\n$it", modifier = Modifier.padding(16.dp)) }
            jsonExportMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
            csvPreview?.let { Text("CSV 预览：\n${it.take(800)}", modifier = Modifier.padding(16.dp)) }
            scheduleExportMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
            csvBundleMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
    }
    pendingImport?.let { encoded ->
        val preview = runCatching { AttentionBackupCodec.preview(encoded) }.getOrNull()
        val incoming = runCatching { AttentionBackupCodec.decode(encoded) }.getOrNull()
        val merged = incoming?.let { AttentionBackupCodec.merge(state, it) }
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入预览") },
            text = {
                Text(
                    preview?.let { backupPreviewText(it, state, merged) }
                        ?: "JSON 无法解析，现有数据不会改变。",
                )
            },
            confirmButton = {
                AttentionTextButton(onClick = { if (preview != null) viewModel.importJson(encoded, false); pendingImport = null }) { Text("合并导入") }
            },
            dismissButton = {
                Row {
                    AttentionTextButton(onClick = { if (preview != null) { pendingClearImport = encoded; pendingImport = null } }) { Text("清空后恢复") }
                    AttentionTextButton(onClick = { pendingImport = null }) { Text("取消") }
                }
            },
        )
    }
    pendingClearImport?.let { encoded ->
        AlertDialog(
            onDismissRequest = { pendingClearImport = null },
            title = { Text("确认清空后恢复") },
            text = { Text("这会清空当前全部数据，并在导入前保存保护性备份。确定继续吗？") },
            confirmButton = {
                AttentionTextButton(onClick = {
                    viewModel.importJson(encoded, clearExisting = true)
                    pendingClearImport = null
                }) { Text("确认清空并恢复") }
            },
            dismissButton = { AttentionTextButton(onClick = { pendingClearImport = null }) { Text("取消") } },
        )
    }
}

private fun backupPreviewText(preview: BackupPreview, current: AttentionState, merged: AttentionState?): String = buildString {
    append("版本 ${preview.version}，共 ${preview.totalRecords} 条记录\n")
    append("目标 ${preview.targets}，阶段 ${preview.goalStages}，时间记录 ${preview.timeEntries}，日程 ${preview.schedules}\n")
    append("重复规则 ${preview.recurrenceRules}，迁移 ${preview.migrations}，未来目标规则 ${preview.futureGoalRules}\n")
    append("周期快照 ${preview.periodSnapshots}，目标移动 ${preview.targetMoves}，里程碑 ${preview.milestones}\n")
    append(if (preview.hasSettings) "包含设置。" else "不包含设置。")
    append(" ")
    append(if (preview.hasActiveTimer) "包含活动计时器。" else "不包含活动计时器。")
    if (merged != null) {
        val currentCount = current.targets.size + current.goalStages.size + current.timeEntries.size + current.schedules.size +
            current.recurrenceRules.size + current.migrations.size + current.futureGoalRules.size + current.periodSnapshots.size +
            current.targetMoves.size + current.milestones.size
        val mergedCount = merged.targets.size + merged.goalStages.size + merged.timeEntries.size + merged.schedules.size +
            merged.recurrenceRules.size + merged.migrations.size + merged.futureGoalRules.size + merged.periodSnapshots.size +
            merged.targetMoves.size + merged.milestones.size
        append("\n合并后预计 ${mergedCount} 条，可能新增约 ${mergedCount - currentCount} 条。")
    }
    append("\n导入前会自动备份当前数据。")
}

@Composable
private fun WorkspaceTodayScreen(
    state: AttentionState,
    viewModel: AttentionViewModel,
    feedback: AttentionFeedbackHostState,
) {
    val context = LocalContext.current
    val tokens = attentionTokens()
    val feedbackScope = rememberCoroutineScope()
    fun startTimer(targetId: String?) {
        viewModel.startTimer(targetId) {
            AttentionBootReceiver.cancelRecoveryPrompt(context)
            if (!notificationsEnabled(context)) {
                feedbackScope.launch {
                    feedback.showMessage("通知权限未开启，计时仍会保存；通知栏控制不可用。")
                }
            }
            ContextCompat.startForegroundService(context, AttentionTimerService.intent(context))
        }
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
    var dailyCapacityError by remember { mutableStateOf<String?>(null) }
    var editingEntry by remember { mutableStateOf<TimeEntry?>(null) }
    var editingSchedule by remember { mutableStateOf<ScheduleEntry?>(null) }
    var editMinutes by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf("") }
    var editTargetId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<TimeEntry?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(tokens.spacing.large),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("今天工作台", style = tokens.typography.display)
        Text("规划日界线 ${formatMinutes(state.settings.planningDayBoundaryMinutes)} · 每周从${weekName(state.settings.weekStartDay)}开始")
        Text("规划日：$date")
        if (!state.onboardingCompleted) OnboardingCard(state, viewModel)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
            Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("快速记录实际投入", style = tokens.typography.title)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60).forEach { amount ->
                        AttentionOutlinedButton(onClick = { viewModel.addTime(date, amount, null) }) { Text("+$amount") }
                    }
                }
                state.targets.filter { !it.archived }.take(4).forEach { target ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(target.title, Modifier.weight(1f))
                        listOf(15, 30).forEach { amount ->
                            AttentionTextButton(onClick = { viewModel.addTime(date, amount, target.id) }) { Text("+$amount") }
                        }
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("分钟") }, modifier = Modifier.width(120.dp), singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    AttentionButton(onClick = { minutes.toIntOrNull()?.takeIf { it > 0 }?.let { viewModel.addTime(date, it, manualTargetId) } }) {
                        Text(if (manualTargetId == null) "记录未归属活动" else "记录到目标")
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 选中项用共用的选中表达：颜色之外同时有勾选图标与“已选中：…”文案。
                    val targets = listOf<Pair<String?, String>>(null to "未归属") +
                        state.targets.filter { !it.archived }.map { it.id to it.title }
                    targets.forEach { (id, title) ->
                        if (manualTargetId == id) {
                            Box(Modifier.clickable { manualTargetId = id }) {
                                AttentionSelectedIndicator(selected = true, label = title)
                            }
                        } else {
                            AttentionTextButton(onClick = { manualTargetId = id }) { Text(title) }
                        }
                    }
                }
                Text("本规划日已记录：${state.timeEntries.filter { it.planningDate == date }.sumOf { it.durationMinutes }} 分钟")
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
            Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("计时器", style = tokens.typography.title)
                val timer = state.activeTimer
                if (timer == null) {
                    Text("计时结束时会按规划日界线自动切分时间记录。")
                    AttentionButton(onClick = {
                        startTimer(null)
                    }) { Text("开始未归属计时") }
                    state.targets.filter { !it.archived }.forEach { target ->
                        AttentionOutlinedButton(onClick = {
                            startTimer(target.id)
                        }) { Text("计时：${target.title}") }
                    }
                } else {
                    Text(if (timer.paused) "计时已暂停" else "正在计时")
                    Text("当前目标：${state.timerTargetLabel(timer.targetId)}")
                    if (timer.completedSegments.isNotEmpty()) {
                        Text("已完成片段：${timer.completedSegments.size}")
                    }
                    val settlementPreview = state.timerSettlementPreview(Instant.now())
                    val settlementScope = settlementPreview.entries.joinToString("；") {
                        "${it.planningDate} ${it.durationMinutes} 分钟"
                    }
                    Text(
                        if (settlementPreview.hasCompleteMinutes) {
                            "结束范围预览：$settlementScope；共 ${settlementPreview.totalMinutes} 分钟"
                        } else {
                            "结束范围预览：当前片段不足 1 分钟，不会生成时间记录"
                        },
                        modifier = Modifier.testTag("timer-settlement-preview"),
                    )
                    if (!notificationsEnabled(context)) {
                        AttentionPermissionState(
                            label = "权限未开启",
                            description = "通知权限未开启，计时数据仍会保存；通知栏暂停、继续和结束控制不可用。",
                            required = true,
                            modifier = Modifier.testTag("timer-notification-permission-warning"),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (timer.paused) AttentionButton(onClick = viewModel::resumeTimer) { Text("继续") }
                        else AttentionButton(onClick = viewModel::pauseTimer) { Text("暂停") }
                        AttentionOutlinedButton(onClick = {
                            viewModel.recoverTimer {
                                AttentionBootReceiver.cancelRecoveryPrompt(context)
                                ContextCompat.startForegroundService(context, AttentionTimerService.intent(context))
                            }
                        }) { Text("从此刻继续") }
                        AttentionOutlinedButton(onClick = {
                            viewModel.stopTimer {
                                AttentionBootReceiver.cancelRecoveryPrompt(context)
                                context.stopService(AttentionTimerService.intent(context))
                            }
                        }) { Text("结束并保存") }
                    }
                    if (timer.targetId != null) {
                        AttentionOutlinedButton(
                            modifier = Modifier.testTag("switch-timer-unowned"),
                            onClick = { viewModel.switchTimer(null) },
                        ) { Text("切换到未归属活动") }
                    }
                    state.targets.filter { !it.archived && it.id != timer.targetId }.forEach { target ->
                        AttentionOutlinedButton(onClick = { viewModel.switchTimer(target.id) }) { Text("切换到 ${target.title}") }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
            Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("每日容量", style = tokens.typography.title)
                Text(capacitySummaryText(capacity), modifier = Modifier.testTag("daily-capacity-summary"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(boundary, { boundary = it.filter(Char::isDigit) }, label = { Text("界线分钟") }, modifier = Modifier.width(150.dp).testTag("planning-boundary-input"), singleLine = true)
                    OutlinedTextField(
                        dailyCapacity,
                        { dailyCapacity = it; dailyCapacityError = null },
                        label = { Text("容量分钟") },
                        modifier = Modifier.width(150.dp).testTag("daily-capacity-input"),
                        singleLine = true,
                        isError = dailyCapacityError != null,
                    )
                }
                dailyCapacityError?.let {
                    AttentionInlineError(message = it, messageTag = "daily-capacity-error")
                }
                AttentionButton(modifier = Modifier.testTag("daily-capacity-save"), onClick = {
                    val b = boundary.toIntOrNull()?.takeIf { it in 0 until 1440 } ?: state.settings.planningDayBoundaryMinutes
                    val input = dailyCapacity.trim()
                    val c = input.toIntOrNull()?.takeIf { it > 0 }
                    if (input.isNotEmpty() && c == null) {
                        dailyCapacityError = "每日容量必须是正整数，或留空清除"
                    } else {
                        dailyCapacityError = null
                        viewModel.setSettings(StoredSettings(b, state.settings.weekStartDay, c))
                    }
                }) { Text("保存设置") }
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
            Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("周起始日", style = tokens.typography.title)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..7).forEach { day ->
                        if (day == state.settings.weekStartDay) {
                            AttentionButton(onClick = {}) { Text(weekName(day)) }
                        } else {
                            AttentionOutlinedButton(onClick = { viewModel.setWeekStart(day) }) { Text(weekName(day)) }
                        }
                    }
                }
            }
        }
        Text("今日安排", style = tokens.typography.title)
        Text("实际投入记录", style = tokens.typography.title)
        state.timeEntries.filter { it.planningDate == date }.forEach { entry ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        DurationFormat.ofMinutes(entry.durationMinutes),
                        style = tokens.typography.title,
                        modifier = Modifier.testTag(TimeRecordTags.DURATION),
                    )
                    Text(
                        "${state.targets.firstOrNull { it.id == entry.targetId }?.title ?: "未归属活动"} · " +
                            TimeEntrySourceFormat.labelOf(entry.source),
                        style = tokens.typography.caption,
                        color = tokens.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(TimeRecordTags.SOURCE_MARKER),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AttentionTextButton(onClick = {
                            editingEntry = entry
                            editMinutes = entry.durationMinutes.toString()
                            editNote = entry.note
                            editTargetId = entry.targetId
                        }, modifier = Modifier.testTag("edit-time-${entry.id}")) { Text("编辑") }
                        AttentionTextButton(onClick = { pendingDelete = entry }, modifier = Modifier.testTag("delete-time-${entry.id}")) { Text("删除") }
                    }
                    if (editingEntry?.id == entry.id) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AttentionTextButton(onClick = { editTargetId = null }) { Text("未归属") }
                            state.targets.filter { !it.archived }.forEach { target ->
                                AttentionTextButton(onClick = { editTargetId = target.id }) { Text(target.title) }
                            }
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            OutlinedTextField(editMinutes, { editMinutes = it.filter(Char::isDigit) }, label = { Text("分钟") }, modifier = Modifier.testTag("edit-time-minutes-${entry.id}").width(120.dp), singleLine = true)
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(editNote, { editNote = it }, label = { Text("备注") }, modifier = Modifier.width(180.dp), singleLine = true)
                            Spacer(Modifier.width(8.dp))
                            AttentionButton(modifier = Modifier.testTag("save-time-${entry.id}"), onClick = { editMinutes.toIntOrNull()?.takeIf { it > 0 }?.let { viewModel.editTime(entry.id, it, editTargetId, editNote); editingEntry = null } }) { Text("保存") }
                        }
                    }
                }
            }
        }
        state.schedules.filter { it.planningDate == date }.forEach { entry ->
            ScheduleRow(entry, state, viewModel, onEdit = { editingSchedule = it })
        }
    }
    editingSchedule?.let { entry ->
        ScheduleEditDialog(
            entry = entry,
            targets = state.targets.filterNot { it.archived },
            onDismiss = { editingSchedule = null },
            onSave = { updated -> viewModel.updateSchedule(updated); editingSchedule = null },
        )
    }
    pendingDelete?.let { entry ->
        AttentionConfirmDialog(
            visible = true,
            title = "删除这条实际投入",
            // 影响说明是必填的：用户要知道自己到底会失去什么，而不是只被问一句是否确定。
            impact = "删除后这 ${DurationFormat.ofMinutes(entry.durationMinutes)} 的实际投入不再计入目标进度、投入经验与统计。" +
                "删除后仍可以在底部提示里撤销一次。",
            confirmLabel = "删除",
            onConfirm = {
                pendingDelete = null
                viewModel.deleteTimeWithUndo(entry.id) { removed ->
                    feedbackScope.launch {
                        feedback.showUndoableMessage(
                            message = "已删除这条实际投入，它不再计入目标进度与统计。",
                            onUndo = { viewModel.restoreTimeEntry(removed) },
                        )
                    }
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * The first-run settings flow deliberately lives above the workbench.  It only
 * writes through the public settings commands, so the same flow works offline
 * and is restored from DataStore after the activity is recreated.
 */
@Composable
private fun OnboardingCard(state: AttentionState, viewModel: AttentionViewModel) {
    val tokens = attentionTokens()
    var step by remember { mutableStateOf(0) }
    var boundary by remember(state.settings.planningDayBoundaryMinutes) {
        mutableStateOf(state.settings.planningDayBoundaryMinutes.toString())
    }
    var weekStart by remember(state.settings.weekStartDay) {
        mutableStateOf(state.settings.weekStartDay)
    }
    var capacity by remember(state.settings.dailyCapacityMinutes) {
        mutableStateOf(state.settings.dailyCapacityMinutes?.toString() ?: "")
    }
    var planTitle by remember { mutableStateOf("") }
    var scheduleTitle by remember { mutableStateOf("") }
    var scheduleDate by remember { mutableStateOf(state.planningDate(Instant.now()).toString()) }
    var scheduleEstimate by remember { mutableStateOf("") }
    var scheduleStart by remember { mutableStateOf("") }
    var scheduleEnd by remember { mutableStateOf("") }
    var scheduleNote by remember { mutableStateOf("") }
    var scheduleTargetId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun nextOrFinish() {
        error = null
        if (step == 4) viewModel.finishOnboarding() else step += 1
    }

    Card(Modifier.fillMaxWidth().testTag("onboarding-card")) {
        Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("首次设置", style = tokens.typography.title)
            Text("第 ${step + 1} 步，共 5 步")
            when (step) {
                0 -> {
                    Text("设置规划日界线")
                    Text("默认 03:00；请输入 0–1439 分钟。")
                    OutlinedTextField(
                        value = boundary,
                        onValueChange = { boundary = it.filter(Char::isDigit); error = null },
                        label = { Text("规划日界线（分钟）") },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding-boundary-input"),
                        singleLine = true,
                        isError = error != null,
                    )
                }
                1 -> {
                    Text("设置每周起始日")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { day ->
                            if (day == weekStart) AttentionButton(onClick = {}, modifier = Modifier.testTag("onboarding-week-$day")) {
                                Text(weekName(day))
                            } else AttentionOutlinedButton(onClick = { weekStart = day }, modifier = Modifier.testTag("onboarding-week-$day")) {
                                Text(weekName(day))
                            }
                        }
                    }
                }
                2 -> {
                    Text("设置每日容量")
                    Text("可留空；填写时必须是正整数分钟。")
                    OutlinedTextField(
                        value = capacity,
                        onValueChange = { capacity = it; error = null },
                        label = { Text("每日容量（分钟，可选）") },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding-capacity-input"),
                        singleLine = true,
                        isError = error != null,
                    )
                }
                3 -> {
                    Text("创建首个计划")
                    Text("可跳过；标题不能为空。")
                    OutlinedTextField(planTitle, { planTitle = it; error = null }, label = { Text("计划标题") }, modifier = Modifier.fillMaxWidth().testTag("onboarding-plan-title"), singleLine = true, isError = error != null)
                }
                else -> {
                    Text("创建第一条日程")
                    Text("可跳过；标题和规划日必填。")
                    OutlinedTextField(scheduleTitle, { scheduleTitle = it; error = null }, label = { Text("日程标题") }, modifier = Modifier.fillMaxWidth().testTag("onboarding-schedule-title"), singleLine = true, isError = error != null)
                    OutlinedTextField(scheduleDate, { scheduleDate = it; error = null }, label = { Text("规划日（YYYY-MM-DD）") }, modifier = Modifier.fillMaxWidth().testTag("onboarding-schedule-date"), singleLine = true, isError = error != null)
                    OutlinedTextField(scheduleEstimate, { scheduleEstimate = it.filter(Char::isDigit) }, label = { Text("预计分钟（可选）") }, modifier = Modifier.fillMaxWidth().testTag("onboarding-schedule-estimate"), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(scheduleStart, { scheduleStart = it.filter(Char::isDigit) }, label = { Text("开始分钟") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(scheduleEnd, { scheduleEnd = it.filter(Char::isDigit) }, label = { Text("结束分钟") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(scheduleNote, { scheduleNote = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    state.targets.filterNot { it.archived }.forEach { target ->
                        if (target.id == scheduleTargetId) AttentionButton(onClick = {}, modifier = Modifier.testTag("onboarding-schedule-target-${target.id}")) { Text("关联：${target.title}") }
                        else AttentionOutlinedButton(onClick = { scheduleTargetId = target.id }, modifier = Modifier.testTag("onboarding-schedule-target-${target.id}")) { Text("关联：${target.title}") }
                    }
                    AttentionTextButton(onClick = { scheduleTargetId = null }) { Text("不关联计划") }
                }
            }
            error?.let { AttentionInlineError(message = it, messageTag = "onboarding-error") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AttentionButton(
                    modifier = Modifier.testTag("onboarding-save-continue"),
                    onClick = {
                        when (step) {
                            0 -> {
                                val value = boundary.toIntOrNull()
                                if (value == null || value !in 0 until 1440) error = "规划日界线必须是 0–1439 分钟"
                                else {
                                    viewModel.setSettings(StoredSettings(value, state.settings.weekStartDay, state.settings.dailyCapacityMinutes))
                                    nextOrFinish()
                                }
                            }
                            1 -> {
                                viewModel.setSettings(StoredSettings(state.settings.planningDayBoundaryMinutes, weekStart, state.settings.dailyCapacityMinutes))
                                nextOrFinish()
                            }
                            2 -> {
                                val input = capacity.trim()
                                val value = input.toIntOrNull()
                                if (input.isNotEmpty() && (value == null || value <= 0)) error = "每日容量必须是正整数，或留空"
                                else {
                                    viewModel.setSettings(StoredSettings(state.settings.planningDayBoundaryMinutes, state.settings.weekStartDay, value))
                                    nextOrFinish()
                                }
                            }
                            3 -> {
                                if (planTitle.isBlank()) error = "计划标题不能为空"
                                else viewModel.addTarget(planTitle.trim()) { nextOrFinish() }
                            }
                            else -> {
                                val date = runCatching { LocalDate.parse(scheduleDate.trim()) }.getOrNull()
                                if (scheduleTitle.isBlank()) error = "日程标题不能为空"
                                else if (date == null) error = "请输入有效的规划日"
                                else {
                                    val start = scheduleStart.toIntOrNull()
                                    val end = scheduleEnd.toIntOrNull()
                                    val estimate = scheduleEstimate.toIntOrNull()
                                    val invalidRange = start != null && end != null && (start !in 0..1439 || end !in 1..1440 || end <= start)
                                    if ((start == null) != (end == null) || invalidRange) error = "时间段无效"
                                    else if (estimate != null && estimate <= 0) error = "预计分钟必须为正整数"
                                    else {
                                        viewModel.addSchedule(ScheduleEntry(
                                            planningDate = date.toString(), title = scheduleTitle.trim(),
                                            startMinute = start, endMinute = end, estimatedMinutes = estimate,
                                            note = scheduleNote, targetId = scheduleTargetId,
                                        ))
                                        nextOrFinish()
                                    }
                                }
                            }
                        }
                    },
                ) { Text("保存并继续") }
                AttentionOutlinedButton(
                    modifier = Modifier.testTag("onboarding-skip-step"),
                    onClick = { nextOrFinish() },
                ) { Text("跳过当前步骤") }
            }
            AttentionTextButton(
                modifier = Modifier.testTag("onboarding-skip-all"),
                onClick = viewModel::finishOnboarding,
            ) { Text("跳过剩余步骤") }
        }
    }
}

@Composable
private fun TargetsScreen(state: AttentionState, viewModel: AttentionViewModel) {
    val tokens = attentionTokens()
    val planningDate = state.planningDate(Instant.now())
    var title by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    var goalMinutes by remember { mutableStateOf("") }
    var goalCadence by remember { mutableStateOf(GoalCadence.DAILY) }
    var goalStartDate by remember { mutableStateOf(state.planningDate(Instant.now()).toString()) }
    var goalDueDate by remember { mutableStateOf("") }
    var futureDate by remember { mutableStateOf(planningDate.plusDays(7).toString()) }
    var selectedPlanningDate by remember { mutableStateOf(state.planningDate(Instant.now()).toString()) }
    var futureMinutes by remember { mutableStateOf("") }
    var futureDueDate by remember { mutableStateOf("") }
    var futureCadence by remember { mutableStateOf(GoalCadence.WEEKLY) }
    var editingFutureRuleId by remember { mutableStateOf<String?>(null) }
    var appendGoalMinutes by remember { mutableStateOf("") }
    var appendGoalDueDate by remember { mutableStateOf("") }
    var migrationMinutes by remember { mutableStateOf("") }
    var migrationStartDate by remember { mutableStateOf("") }
    var migrationEndDate by remember { mutableStateOf("") }
    var editingMigration by remember { mutableStateOf<Migration?>(null) }
    var pendingDelete by remember { mutableStateOf<Target?>(null) }
    var renameTarget by remember { mutableStateOf<Target?>(null) }
    var renameText by remember { mutableStateOf("") }
    var movingTarget by remember { mutableStateOf<Target?>(null) }
    var editingMove by remember { mutableStateOf<TargetMove?>(null) }
    var moveParentId by remember { mutableStateOf<String?>(null) }
    var moveEffectiveDate by remember { mutableStateOf("") }
    var selectedUnownedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedArchivedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(tokens.spacing.large), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("目标树", style = tokens.typography.display)
        Text("父目标显示整个子树的实际投入；归档目标会从默认列表隐藏。")
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(title, { title = it }, label = { Text(if (parentId == null) "新计划" else "新子计划") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(8.dp))
            AttentionButton(onClick = { if (title.isNotBlank()) { viewModel.addTarget(title, parentId); title = "" } }) { Text("添加") }
        }
        if (parentId != null) {
            androidx.compose.runtime.LaunchedEffect(parentId) {
                selectedPlanningDate = state.planningDate(Instant.now()).toString()
            }
            Text("当前父目标：${state.targets.firstOrNull { it.id == parentId }?.title ?: ""}")
            AttentionTextButton(onClick = { parentId = null }) { Text("改为根计划") }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
                Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("为这个计划设置时间目标", style = tokens.typography.body)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GoalCadence.entries.forEach { value ->
                            if (goalCadence == value) AttentionButton(onClick = {}) { Text(value.label()) }
                            else AttentionOutlinedButton(onClick = { goalCadence = value }) { Text(value.label()) }
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
                        AttentionButton(onClick = {
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
                            AttentionOutlinedButton(onClick = { selectedUnownedIds = visibleIds }) { Text("全选") }
                            AttentionTextButton(onClick = { selectedUnownedIds = emptySet() }) { Text("清除选择") }
                            AttentionButton(
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
                    Text("未来生效的目标规则", style = tokens.typography.body)
                    val currentRule = state.goalRulesAt(parentId!!, planningDate)
                        .lastOrNull { it.stageId.isBlank() }
                    Text(
                        currentRule?.let { "当前规则：${it.cadence.label()} ${it.targetMinutes} 分钟 · 生效于 ${it.effectiveFrom}" }
                            ?: "当前规则：使用目标阶段的承诺",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(futureDate, { futureDate = it }, label = { Text("生效日") }, modifier = Modifier.width(145.dp), singleLine = true)
                        OutlinedTextField(futureMinutes, { futureMinutes = it.filter(Char::isDigit) }, label = { Text("未来分钟") }, modifier = Modifier.width(120.dp), singleLine = true)
                        if (futureCadence == GoalCadence.ONE_TIME) {
                            OutlinedTextField(futureDueDate, { futureDueDate = it }, label = { Text("截止日（可选）") }, modifier = Modifier.width(145.dp), singleLine = true)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GoalCadence.entries.forEach { value ->
                            if (futureCadence == value) AttentionButton(onClick = {}) { Text(value.label()) }
                            else AttentionOutlinedButton(onClick = { futureCadence = value }) { Text(value.label()) }
                        }
                    }
                    AttentionButton(onClick = {
                        futureMinutes.toIntOrNull()?.takeIf { it > 0 }?.let {
                            val rule = FutureGoalRule(
                                id = editingFutureRuleId ?: com.attention.domain.newId(),
                                targetId = parentId!!,
                                cadence = futureCadence,
                                targetMinutes = it,
                                effectiveFrom = futureDate,
                                dueDate = futureDueDate.trim().takeIf { value -> futureCadence == GoalCadence.ONE_TIME && value.isNotEmpty() },
                            )
                            if (editingFutureRuleId == null) {
                                viewModel.addFutureGoal(rule, planningDate)
                            } else {
                                viewModel.updateFutureGoal(rule, planningDate)
                            }
                            futureMinutes = ""
                            futureDueDate = ""
                            editingFutureRuleId = null
                        }
                    }) { Text(if (editingFutureRuleId == null) "保存未来规则" else "更新未来规则") }
                    state.futureGoalRules.filter { it.targetId == parentId }.forEach { rule ->
                        val pending = rule.isPending(planningDate)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("${rule.effectiveFrom} · ${rule.cadence.label()} ${rule.targetMinutes} 分钟${rule.dueDate?.let { " · 截止 $it" } ?: ""}${if (pending) " · 待生效" else " · 当前/历史"}", Modifier.weight(1f))
                            if (pending) {
                                AttentionTextButton(onClick = {
                                    editingFutureRuleId = rule.id
                                    futureDate = rule.effectiveFrom
                                    futureMinutes = rule.targetMinutes.toString()
                                    futureCadence = rule.cadence
                                    futureDueDate = rule.dueDate.orEmpty()
                                }) { Text("编辑") }
                                AttentionTextButton(onClick = { viewModel.cancelFutureGoal(rule.id, planningDate) }) { Text("取消") }
                            }
                        }
                    }
                    val parsedPlanningDate = runCatching { LocalDate.parse(selectedPlanningDate) }.getOrNull()
                    val progressPlanningDate = parsedPlanningDate ?: planningDate
                    val target = state.targets.firstOrNull { it.id == parentId }
                    val stages = state.goalStagesForTarget(parentId!!)
                    val oneTimeSummary = state.goalSummary(parentId!!, progressPlanningDate)
                    if (stages.isNotEmpty()) {
                        Text("当前目标进度", style = tokens.typography.body)
                        OutlinedTextField(
                            selectedPlanningDate,
                            { selectedPlanningDate = it },
                            label = { Text("当前规划日 YYYY-MM-DD") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        if (parsedPlanningDate == null) {
                            AttentionInlineError(message = "规划日格式无效，请使用 YYYY-MM-DD")
                        }
                        if (oneTimeSummary.stageProgresses.isNotEmpty()) {
                            Text(oneTimeSummary.summaryLabel())
                        }
                        stages.forEach { stage ->
                            val detail = oneTimeSummary.stageProgresses.firstOrNull { it.stage.id == stage.id }
                            val progress = state.progress(stage, progressPlanningDate)
                            Text(detail?.let { state.goalProgressLabel(it, progressPlanningDate) } ?: state.goalProgressLabel(stage, progressPlanningDate))
                            state.stageFeedbackLabels(stage).forEach { label ->
                                Text(label, modifier = Modifier.padding(start = 16.dp))
                            }
                            val availableMigration = runCatching {
                                state.availableMigrationMinutes(stage, progressPlanningDate)
                            }.getOrDefault(0)
                            if (progress.gapMinutes > 0 || state.migrations.any { it.sourceStageId == stage.id }) {
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("目标迁移：缺口 ${progress.gapMinutes} 分钟 · 仍可迁移 $availableMigration 分钟")
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            OutlinedTextField(
                                                migrationMinutes,
                                                { migrationMinutes = it.filter(Char::isDigit) },
                                                label = { Text("迁移分钟") },
                                                modifier = Modifier.width(110.dp).testTag("migration-minutes-${stage.id}"),
                                                singleLine = true,
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            OutlinedTextField(
                                                migrationStartDate.ifBlank { state.progressRange(stage, progressPlanningDate).endInclusive.plusDays(1).toString() },
                                                { migrationStartDate = it },
                                                label = { Text("目的开始日") },
                                                modifier = Modifier.width(145.dp),
                                                singleLine = true,
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            OutlinedTextField(
                                                migrationEndDate,
                                                { migrationEndDate = it },
                                                label = { Text("目的结束日（可选）") },
                                                modifier = Modifier.width(155.dp),
                                                singleLine = true,
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            AttentionButton(
                                                enabled = migrationMinutes.toIntOrNull()?.let { it > 0 } == true,
                                                modifier = Modifier.testTag("migration-submit-${stage.id}"),
                                                onClick = {
                                                    val minutes = migrationMinutes.toIntOrNull() ?: return@AttentionButton
                                                    val start = migrationStartDate.ifBlank { state.progressRange(stage, progressPlanningDate).endInclusive.plusDays(1).toString() }
                                                    val end = migrationEndDate.trim().takeIf(String::isNotEmpty)
                                                    val current = editingMigration
                                                    val migration = Migration(
                                                        id = current?.id ?: com.attention.domain.newId(),
                                                        targetId = parentId!!,
                                                        sourceStageId = stage.id,
                                                        minutes = minutes,
                                                        destinationStartDate = start,
                                                        destinationEndDate = end,
                                                        cancelled = false,
                                                    )
                                                    if (current == null) viewModel.addMigration(migration, progressPlanningDate)
                                                    else viewModel.updateMigration(migration, progressPlanningDate)
                                                    migrationMinutes = ""
                                                    migrationStartDate = ""
                                                    migrationEndDate = ""
                                                    editingMigration = null
                                                },
                                            ) { Text(if (editingMigration == null) "添加迁移" else "保存迁移") }
                                            if (editingMigration != null) {
                                                AttentionTextButton(onClick = {
                                                    editingMigration = null
                                                    migrationMinutes = ""
                                                    migrationStartDate = ""
                                                    migrationEndDate = ""
                                                }) { Text("取消编辑") }
                                            }
                                        }
                                        state.migrations.filter { it.sourceStageId == stage.id }.forEach { migration ->
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                val range = migration.destinationEndDate?.let { "${migration.destinationStartDate} 至 $it" } ?: migration.destinationStartDate
                                                Text("${migration.minutes} 分钟 · $range${if (migration.cancelled) " · 已取消" else ""}", Modifier.weight(1f))
                                                if (!migration.cancelled) {
                                                    AttentionTextButton(onClick = {
                                                        editingMigration = migration
                                                        migrationMinutes = migration.minutes.toString()
                                                        migrationStartDate = migration.destinationStartDate
                                                        migrationEndDate = migration.destinationEndDate.orEmpty()
                                                    }) { Text("编辑") }
                                                    AttentionTextButton(onClick = { viewModel.cancelMigration(migration.id) }) { Text("取消迁移") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        val latest = stages.last()
                        val latestProgress = state.progress(latest, progressPlanningDate)
                        if (parsedPlanningDate != null && target?.archived == false && latest.cadence == GoalCadence.ONE_TIME && latestProgress.completed) {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
                                Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("追加一次性阶段", style = tokens.typography.body)
                                    Text("起始规划日：$progressPlanningDate")
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
                                        AttentionButton(
                                            modifier = Modifier.testTag("append-goal-submit"),
                                            onClick = {
                                            viewModel.appendOneTimeGoal(
                                                targetId = parentId!!,
                                                minutes = appendGoalMinutes.toIntOrNull() ?: 0,
                                                startDate = progressPlanningDate.toString(),
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
                    if (target != null) {
                        val futureMove = state.pendingTargetMoves(target.id, planningDate).firstOrNull()
                        if (futureMove != null) {
                            Text("待生效移动：${futureMove.effectiveFrom} → ${futureMove.parentId?.let { state.targetPath(it, parseTargetMoveDate(futureMove.effectiveFrom)) } ?: "根计划"}")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AttentionOutlinedButton(onClick = {
                                    movingTarget = target
                                    editingMove = futureMove
                                    moveParentId = futureMove.parentId
                                    moveEffectiveDate = futureMove.effectiveFrom
                                }) { Text("编辑移动") }
                                AttentionTextButton(onClick = { viewModel.cancelFutureTargetMove(futureMove.id) }) { Text("取消移动") }
                            }
                        }
                        if (target.parentId != null) {
                        AttentionOutlinedButton(onClick = { viewModel.moveTargetFrom(target.id, null, state.planningDate(Instant.now()).plusDays(1).toString()) }) { Text("从明日起移到根计划") }
                        }
                    }
                }
            }
        }
        renameTarget?.let { target ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(renameText, { renameText = it }, label = { Text("重命名 ${target.title}") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                AttentionButton(onClick = { if (renameText.isNotBlank()) viewModel.renameTarget(target.id, renameText); renameTarget = null }) { Text("保存") }
            }
        }
        movingTarget?.let { target ->
            val parsedEffectiveDate = parseTargetMoveDate(moveEffectiveDate)
            val validEffectiveDate = isFutureTargetMoveDate(moveEffectiveDate, planningDate)
            val candidateParents = state.futureTargetMoveParents(target.id, moveEffectiveDate, planningDate)
            val pendingMoves = state.pendingTargetMoves(target.id, planningDate)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
                Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("移动 ${target.title}：选择新的父目标", style = tokens.typography.body)
                    OutlinedTextField(
                        moveEffectiveDate,
                        { moveEffectiveDate = it },
                        label = { Text("生效规划日") },
                        modifier = Modifier.width(160.dp),
                        singleLine = true,
                    )
                    if (!validEffectiveDate) {
                        AttentionInlineError(message = "生效日必须晚于当前规划日 $planningDate")
                    }
                    if (moveParentId == null) AttentionButton(onClick = {}) { Text("移到根计划") }
                    else AttentionOutlinedButton(onClick = { moveParentId = null }) { Text("移到根计划") }
                    candidateParents
                        .sortedBy { state.targetPath(it.id, parsedEffectiveDate ?: planningDate) }
                        .forEach { candidate ->
                            if (moveParentId == candidate.id) {
                                AttentionButton(onClick = {}) { Text("移到：${state.targetPath(candidate.id, parsedEffectiveDate ?: planningDate)}") }
                            } else {
                                AttentionOutlinedButton(onClick = { moveParentId = candidate.id }) {
                                    Text("移到：${state.targetPath(candidate.id, parsedEffectiveDate ?: planningDate)}")
                                }
                            }
                        }
                    AttentionButton(
                        enabled = validEffectiveDate,
                        onClick = {
                            val effectiveFrom = parsedEffectiveDate?.toString() ?: return@AttentionButton
                            val existing = editingMove
                            if (existing == null) {
                                viewModel.moveTargetFrom(target.id, moveParentId, effectiveFrom)
                            } else {
                                viewModel.updateFutureTargetMove(existing.copy(parentId = moveParentId, effectiveFrom = effectiveFrom))
                            }
                            movingTarget = null
                            editingMove = null
                        },
                    ) { Text(if (editingMove == null) "保存未来移动" else "保存移动修改") }
                    if (pendingMoves.isNotEmpty()) {
                        Text("待生效移动", style = tokens.typography.body)
                        pendingMoves.forEach { move ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    "${move.effectiveFrom} → ${move.parentId?.let { state.targetPath(it, parseTargetMoveDate(move.effectiveFrom)) } ?: "根计划"}",
                                    Modifier.weight(1f),
                                )
                                AttentionTextButton(onClick = {
                                    editingMove = move
                                    moveParentId = move.parentId
                                    moveEffectiveDate = move.effectiveFrom
                                }) { Text("编辑") }
                                AttentionTextButton(onClick = { viewModel.cancelFutureTargetMove(move.id) }) { Text("取消") }
                            }
                        }
                    }
                    AttentionTextButton(onClick = { movingTarget = null }) { Text("取消移动") }
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
            move = {
                movingTarget = it
                editingMove = null
                moveParentId = state.parentAt(it.id, planningDate)
                moveEffectiveDate = planningDate.plusDays(1).toString()
            },
        )
        if (state.targets.none { !it.archived }) Text("还没有计划。")
        val archivedTargets = state.targets.filter { it.archived }
        if (archivedTargets.isNotEmpty()) {
            Text("已归档目标", style = tokens.typography.title)
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("已选 ${selectedArchivedIds.size} 个", Modifier.weight(1f))
                AttentionTextButton(onClick = { selectedArchivedIds = archivedTargets.map { it.id }.toSet() }) { Text("全选") }
                AttentionTextButton(onClick = { selectedArchivedIds = emptySet() }) { Text("清除选择") }
                AttentionButton(
                    modifier = Modifier.testTag("restore-archived-selected"),
                    enabled = selectedArchivedIds.isNotEmpty(),
                    onClick = { viewModel.restoreTargets(selectedArchivedIds); selectedArchivedIds = emptySet() },
                ) { Text("批量恢复") }
                AttentionButton(
                    modifier = Modifier.testTag("delete-archived-selected"),
                    enabled = selectedArchivedIds.isNotEmpty(),
                    onClick = { pendingDeleteIds = selectedArchivedIds },
                ) { Text("批量安全删除") }
            }
            archivedTargets.forEach { target ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = target.id in selectedArchivedIds,
                            onCheckedChange = { checked ->
                                selectedArchivedIds = if (checked) selectedArchivedIds + target.id else selectedArchivedIds - target.id
                            },
                        )
                        Text(target.title, Modifier.weight(1f))
                        AttentionTextButton(onClick = { viewModel.restoreTargets(setOf(target.id)) }) { Text("恢复") }
                        AttentionTextButton(onClick = { pendingDelete = target }) { Text("安全删除") }
                    }
                    Text("层级：${state.targetPath(target.id)}", modifier = Modifier.padding(start = 16.dp))
                    val historicalMinutes = state.timeEntries
                        .filter { it.targetId in state.descendantIds(target.id) }
                        .sumOf { it.durationMinutes }
                    Text("历史投入：$historicalMinutes 分钟", modifier = Modifier.padding(start = 16.dp))
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
                        state.stageFeedbackLabels(stage).forEach { label ->
                            Text(label, modifier = Modifier.padding(start = 32.dp))
                        }
                    }
                }
            }
        } else {
            Text("暂无归档目标", modifier = Modifier.testTag("archived-empty-state"))
        }
    }
    val deleteIds = pendingDeleteIds.ifEmpty { pendingDelete?.let { setOf(it.id) } ?: emptySet() }
    if (deleteIds.isNotEmpty()) {
        val impact = state.targetDeletionImpact(deleteIds)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null; pendingDeleteIds = emptySet() },
            title = { Text(if (deleteIds.size == 1) "删除目标关系？" else "批量删除目标关系？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("将删除所选目标及其后代，共 ${impact.targetCount} 个目标。")
                    Text("影响：时间记录 ${impact.timeEntryCount} 条，日程 ${impact.scheduleEntryCount} 条，重复日程 ${impact.recurrenceRuleCount} 条。")
                    Text("目标阶段 ${impact.goalStageCount} 个，未来规则 ${impact.futureGoalRuleCount} 条，迁移 ${impact.migrationCount} 条，未来移动 ${impact.targetMoveCount} 条。")
                    Text("活动计时器 ${impact.activeTimerCount} 个。时间记录会转为未归属活动，日程保留但解除目标关联。投入经验和里程碑保留。")
                }
            },
            confirmButton = {
                AttentionTextButton(onClick = {
                    viewModel.deleteTargetRelations(deleteIds)
                    pendingDelete = null
                    pendingDeleteIds = emptySet()
                    selectedArchivedIds = emptySet()
                }) { Text("确认删除") }
            },
            dismissButton = { AttentionTextButton(onClick = { pendingDelete = null; pendingDeleteIds = emptySet() }) { Text("取消") } },
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
    val planningDate = state.planningDate(Instant.now())
    state.targetChildren(parentId, planningDate).forEach { target ->
        val children = state.targetChildren(target.id, planningDate)
        val pendingMove = state.pendingTargetMoves(target.id, planningDate).firstOrNull()
        Column(Modifier.padding(start = (depth * 16).dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(if (children.isEmpty()) "•" else if (target.expanded) "▾" else "▸")
                Spacer(Modifier.width(6.dp))
                Text(target.title, modifier = Modifier.weight(1f))
                Text("直接 ${state.directMinutes(target.id)} / 汇总 ${state.subtreeMinutes(target.id)} 分钟")
            }
            pendingMove?.let { move ->
                Text(
                    "待移动：${move.effectiveFrom} → ${move.parentId?.let { state.targetPath(it, parseTargetMoveDate(move.effectiveFrom)) } ?: "根计划"}",
                    modifier = Modifier.padding(start = 22.dp),
                )
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
                state.stageFeedbackLabels(stage).forEach { label ->
                    Text(label, modifier = Modifier.padding(start = 38.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                AttentionTextButton(onClick = { viewModel.toggleTarget(target.id, !target.expanded) }) { Text(if (target.expanded) "折叠" else "展开") }
                AttentionTextButton(onClick = { chooseParent(target.id) }) { Text("添加子计划") }
                AttentionTextButton(onClick = { rename(target) }) { Text("重命名") }
                AttentionTextButton(onClick = { move(target) }) { Text("移动") }
                val siblings = state.targetChildren(parentId, planningDate)
                val index = siblings.indexOf(target)
                if (index > 0) AttentionTextButton(onClick = { viewModel.reorderTarget(target.id, index - 1) }) { Text("上移") }
                if (index < siblings.lastIndex) AttentionTextButton(onClick = { viewModel.reorderTarget(target.id, index + 1) }) { Text("下移") }
                AttentionTextButton(onClick = { viewModel.archiveTarget(target.id) }) { Text("归档") }
            }
            if (target.expanded) TargetTree(state, viewModel, target.id, depth + 1, chooseParent, rename, move)
        }
    }
}

@Composable
private fun AgendaScreen(
    state: AttentionState,
    viewModel: AttentionViewModel,
    selectedDate: String?,
    onSelectedDateChange: (String?) -> Unit,
    openScheduleId: String? = null,
    onStartTimer: (ScheduleEntry) -> Unit = {},
) {
    val context = LocalContext.current
    val tokens = attentionTokens()
    val today = state.planningDate(Instant.now())
    var date by remember(selectedDate) { mutableStateOf(selectedDate ?: today.toString()) }
    var title by remember { mutableStateOf("") }
    var estimate by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var reminderTime by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf<String?>(null) }
    var targetMenuExpanded by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var editingEntry by remember { mutableStateOf<ScheduleEntry?>(null) }
    var notificationOpenHandled by remember(openScheduleId) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(openScheduleId, state.schedules) {
        if (!notificationOpenHandled && openScheduleId != null) {
            state.schedules.firstOrNull { it.id == openScheduleId }?.let {
                editingEntry = it
                notificationOpenHandled = true
            }
        }
    }
    var recurrenceTitle by remember { mutableStateOf("") }
    var recurrenceFrequency by remember { mutableStateOf(ScheduleFrequency.DAILY) }
    var recurrenceUntilDate by remember { mutableStateOf("") }
    var recurrenceEstimatedMinutes by remember { mutableStateOf("") }
    var recurrenceReminderTime by remember { mutableStateOf("") }
    var recurrenceWeekdays by remember { mutableStateOf(setOf<Int>()) }
    var recurrenceDayOfMonth by remember { mutableStateOf("") }
    var recurrenceTargetId by remember { mutableStateOf<String?>(null) }
    var recurrenceTargetMenuExpanded by remember { mutableStateOf(false) }
    var recurrenceError by remember { mutableStateOf<String?>(null) }
    var dateMenuExpanded by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    val availableDates = remember(state.schedules, today) {
        (state.schedules.mapNotNull { runCatching { LocalDate.parse(it.planningDate) }.getOrNull() } + today)
            .distinct()
            .sorted()
    }
    val filteredDate = selectedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val agendaEntries = state.agendaEntries(filteredDate)
    fun selectDate(value: String?) {
        onSelectedDateChange(value)
        date = value ?: today.toString()
        dateError = false
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(tokens.spacing.large), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (selectedDate == null) "所有日期" else "规划日 $selectedDate", style = tokens.typography.display, modifier = Modifier.testTag("agenda-filter"))
        Text("日程条目可带预计时长；它不会自动变成实际投入。")
        Box {
            AttentionOutlinedButton(onClick = { dateMenuExpanded = true }, modifier = Modifier.testTag("agenda-date-menu")) {
                Text(if (selectedDate == null) "筛选：所有日期" else "筛选：规划日 $selectedDate")
            }
            DropdownMenu(expanded = dateMenuExpanded, onDismissRequest = { dateMenuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("所有日期") },
                    onClick = { selectDate(null); dateMenuExpanded = false },
                    modifier = Modifier.testTag("agenda-all-dates"),
                )
                availableDates.forEach { availableDate ->
                    DropdownMenuItem(
                        text = { Text("规划日 $availableDate") },
                        onClick = { selectDate(availableDate.toString()); dateMenuExpanded = false },
                        modifier = Modifier.testTag("agenda-date-$availableDate"),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(date, { date = it; dateError = false }, label = { Text("新增日程规划日 YYYY-MM-DD") }, modifier = Modifier.weight(1f), singleLine = true, isError = dateError, supportingText = if (dateError) ({ Text("请输入有效的规划日") }) else null)
            AttentionOutlinedButton(onClick = {
                val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
                if (parsed == null) dateError = true else selectDate(parsed.toString())
            }) { Text("打开规划日") }
        }
        if (selectedDate != null) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
                Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("每日容量", style = tokens.typography.body)
                    Text(
                        capacitySummaryText(state.capacitySummary(selectedDate)),
                        modifier = Modifier.testTag("agenda-capacity-summary"),
                    )
                }
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
            Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("重复日程", style = tokens.typography.body)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(recurrenceTitle, { recurrenceTitle = it; recurrenceError = null }, label = { Text("重复标题") }, modifier = Modifier.weight(1f), singleLine = true)
                    ScheduleFrequency.entries.forEach { value ->
                        if (recurrenceFrequency == value) AttentionButton(onClick = {}) { Text(value.label()) }
                        else AttentionOutlinedButton(onClick = {
                            recurrenceFrequency = value
                            recurrenceError = null
                        }) { Text(value.label()) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        recurrenceUntilDate,
                        { recurrenceUntilDate = it; recurrenceError = null },
                        label = { Text("结束规划日（可选）") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        recurrenceEstimatedMinutes,
                        { recurrenceEstimatedMinutes = it; recurrenceError = null },
                        label = { Text("预计分钟（可选）") },
                        modifier = Modifier.width(140.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        recurrenceReminderTime,
                        { recurrenceReminderTime = it; recurrenceError = null },
                        label = { Text("提醒 HH:mm（可选）") },
                        modifier = Modifier.width(150.dp),
                        singleLine = true,
                    )
                }
                when (recurrenceFrequency) {
                    ScheduleFrequency.WEEKLY -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            (1..7).forEach { weekday ->
                                Row {
                                    Checkbox(
                                        checked = weekday in recurrenceWeekdays,
                                        onCheckedChange = { checked ->
                                            recurrenceWeekdays = if (checked) recurrenceWeekdays + weekday else recurrenceWeekdays - weekday
                                            recurrenceError = null
                                        },
                                    )
                                    Text(weekdayLabel(weekday), modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                    }
                    ScheduleFrequency.MONTHLY -> {
                        OutlinedTextField(
                            recurrenceDayOfMonth,
                            { recurrenceDayOfMonth = it; recurrenceError = null },
                            label = { Text("每月日期 1-31") },
                            modifier = Modifier.width(160.dp),
                            singleLine = true,
                        )
                    }
                    ScheduleFrequency.DAILY -> Unit
                }
                Box {
                    AttentionOutlinedButton(onClick = { recurrenceTargetMenuExpanded = true }) {
                        Text(state.targets.firstOrNull { it.id == recurrenceTargetId }?.title ?: "关联计划（可选）")
                    }
                    DropdownMenu(expanded = recurrenceTargetMenuExpanded, onDismissRequest = { recurrenceTargetMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("不关联计划") },
                            onClick = { recurrenceTargetId = null; recurrenceTargetMenuExpanded = false },
                        )
                        state.targets.filterNot { it.archived }.forEach { target ->
                            DropdownMenuItem(
                                text = { Text(target.title) },
                                onClick = { recurrenceTargetId = target.id; recurrenceTargetMenuExpanded = false },
                            )
                        }
                    }
                }
                recurrenceError?.let { AttentionInlineError(message = it) }
                AttentionButton(onClick = {
                    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                    val parsedUntilDate = recurrenceUntilDate.trim().takeIf(String::isNotEmpty)?.let { value ->
                        runCatching { LocalDate.parse(value) }.getOrNull()
                    }
                    val parsedEstimatedMinutes = recurrenceEstimatedMinutes.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                    val parsedReminderMinute = parseScheduleMinute(recurrenceReminderTime)
                    val parsedDayOfMonth = recurrenceDayOfMonth.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                    recurrenceError = validateRecurrenceForm(
                        recurrenceTitle,
                        parsedDate,
                        recurrenceUntilDate,
                        parsedUntilDate,
                        recurrenceFrequency,
                        recurrenceWeekdays,
                        parsedDayOfMonth,
                        recurrenceEstimatedMinutes,
                        parsedEstimatedMinutes,
                        recurrenceReminderTime,
                        parsedReminderMinute,
                    )
                    if (recurrenceError == null) {
                        val rule = RecurrenceRule(
                            title = recurrenceTitle,
                            startDate = parsedDate.toString(),
                            frequency = recurrenceFrequency,
                            weekdays = recurrenceWeekdays.toList().sorted(),
                            dayOfMonth = parsedDayOfMonth,
                            untilDate = parsedUntilDate?.toString(),
                            estimatedMinutes = parsedEstimatedMinutes,
                            targetId = recurrenceTargetId,
                            reminderMinuteOfDay = parsedReminderMinute,
                        )
                        viewModel.addRecurrenceAndMaterialize(rule, LocalDate.parse(rule.startDate).plusDays(30))
                        if (parsedReminderMinute != null) requestReminderPermission(context)
                        recurrenceTitle = ""
                        recurrenceUntilDate = ""
                        recurrenceEstimatedMinutes = ""
                        recurrenceReminderTime = ""
                        recurrenceWeekdays = emptySet()
                        recurrenceDayOfMonth = ""
                        recurrenceTargetId = null
                    }
                }) { Text("创建并生成未来 30 天") }
                state.recurrenceRules.forEach { rule ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        val detail = when (rule.frequency) {
                            ScheduleFrequency.DAILY -> "每日"
                            ScheduleFrequency.WEEKLY -> "每周 ${rule.weekdays.joinToString(",") { weekdayLabel(it) }}"
                            ScheduleFrequency.MONTHLY -> "每月 ${rule.dayOfMonth} 日"
                        }
                        Text("${rule.title} · $detail · ${rule.startDate}${rule.untilDate?.let { " 至 $it" } ?: ""}", Modifier.weight(1f))
                        if (rule.active) AttentionTextButton(onClick = { viewModel.stopRecurrence(rule.id) }) { Text("停止未来生成") }
                        else Text("已停止")
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
            Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("新增日程", style = tokens.typography.body)
                OutlinedTextField(title, { title = it; formError = null }, label = { Text("日程标题") }, modifier = Modifier.fillMaxWidth().testTag("agenda-title-input"), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startTime, { startTime = it; formError = null }, label = { Text("开始时间 HH:mm（可选）") }, modifier = Modifier.weight(1f).testTag("agenda-start-input"), singleLine = true)
                    OutlinedTextField(endTime, { endTime = it; formError = null }, label = { Text("结束时间 HH:mm（可选）") }, modifier = Modifier.weight(1f).testTag("agenda-end-input"), singleLine = true)
                    OutlinedTextField(estimate, { estimate = it; formError = null }, label = { Text("预计分钟") }, modifier = Modifier.width(110.dp).testTag("agenda-estimate-input"), singleLine = true)
                }
                OutlinedTextField(reminderTime, { reminderTime = it; formError = null }, label = { Text("提醒 HH:mm（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Box {
                    AttentionOutlinedButton(onClick = { targetMenuExpanded = true }) {
                        Text(state.targets.firstOrNull { it.id == targetId }?.title ?: "关联计划（可选）")
                    }
                    DropdownMenu(expanded = targetMenuExpanded, onDismissRequest = { targetMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("不关联计划") }, onClick = { targetId = null; targetMenuExpanded = false })
                        state.targets.filterNot { it.archived }.forEach { target ->
                            DropdownMenuItem(text = { Text(target.title) }, onClick = { targetId = target.id; targetMenuExpanded = false })
                        }
                    }
                }
                formError?.let { AttentionInlineError(message = it) }
                AttentionButton(modifier = Modifier.testTag("agenda-add-submit"), onClick = {
                    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                    val parsedStart = parseScheduleMinute(startTime)
                    val parsedEnd = parseScheduleMinute(endTime, allowEndOfDay = true)
                    val estimated = estimate.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                    val parsedReminder = parseScheduleMinute(reminderTime)
                    formError = validateScheduleForm(
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
                    if (formError == null) {
                        viewModel.addSchedule(ScheduleEntry(
                            planningDate = parsedDate.toString(),
                            title = title,
                            startMinute = parsedStart,
                            endMinute = parsedEnd,
                            estimatedMinutes = estimated,
                            note = note,
                            targetId = targetId,
                            reminderEpochMillis = parsedReminder?.let { minute ->
                                reminderEpochMillis(parsedDate!!, minute)
                            },
                        ))
                        if (parsedReminder != null) requestReminderPermission(context)
                        title = ""; startTime = ""; endTime = ""; estimate = ""; reminderTime = ""; note = ""; targetId = null
                    }
                }) { Text("添加日程") }
            }
        }
        if (agendaEntries.isEmpty()) {
            Text(if (selectedDate == null) "所有日期暂无日程条目。" else "规划日 $selectedDate 暂无日程条目。", modifier = Modifier.testTag("agenda-empty"))
        } else if (selectedDate == null) {
            agendaEntries.groupBy { it.planningDate }.forEach { (planningDate, entries) ->
                Text("规划日 $planningDate", style = tokens.typography.title)
                Text(
                    capacitySummaryText(state.capacitySummary(planningDate)),
                    modifier = Modifier.testTag("agenda-capacity-summary-$planningDate"),
                )
                entries.forEach { entry ->
                    ScheduleRow(entry, state, viewModel, onEdit = { editingEntry = it }, onStartTimer = onStartTimer)
                }
            }
        } else {
            agendaEntries.forEach { entry ->
                ScheduleRow(entry, state, viewModel, onEdit = { editingEntry = it }, onStartTimer = onStartTimer)
            }
        }
    }
    editingEntry?.let { entry ->
        ScheduleEditDialog(
            entry = entry,
            targets = state.targets.filterNot { it.archived },
            onDismiss = { editingEntry = null },
            onSave = { updated -> viewModel.updateSchedule(updated); editingEntry = null },
        )
    }
}

@Composable
private fun ScheduleRow(
    entry: ScheduleEntry,
    state: AttentionState,
    viewModel: AttentionViewModel,
    onEdit: (ScheduleEntry) -> Unit = {},
    onStartTimer: (ScheduleEntry) -> Unit = {},
) {
    val tokens = attentionTokens()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = tokens.typography.body)
                val start = entry.startMinute
                val end = entry.endMinute
                val time = if (start != null && end != null) " · ${formatScheduleMinute(start)}-${formatScheduleMinute(end)}" else ""
                val target = entry.targetId?.let { id -> state.targets.firstOrNull { it.id == id }?.title }?.let { " · $it" } ?: ""
                val reminder = entry.reminderEpochMillis?.let { " · 提醒 ${formatReminderEpoch(it)}" } ?: " · 无提醒"
                Text("${entry.planningDate}$time · ${entry.estimatedMinutes ?: 0} 分钟$target$reminder${if (entry.completed) " · 已完成" else ""}")
                if (entry.note.isNotBlank()) Text(entry.note)
            }
            AttentionTextButton(onClick = { onEdit(entry) }, modifier = Modifier.testTag("schedule-edit-${entry.id}")) { Text("编辑") }
            AttentionTextButton(
                onClick = { onStartTimer(entry) },
                enabled = state.activeTimer == null,
                modifier = Modifier.testTag("schedule-start-timer-${entry.id}"),
            ) { Text("开始计时") }
            AttentionTextButton(
                onClick = {
                    if (entry.completed) viewModel.toggleScheduleCompleted(entry.id)
                    else viewModel.confirmScheduleCompleted(entry.id)
                },
                modifier = Modifier.testTag("schedule-completion-${entry.id}"),
            ) { Text(if (entry.completed) "取消完成" else "完成并记录") }
        }
    }
}

private fun capacitySummaryText(summary: CapacitySummary): String = when {
    summary.capacityMinutes == null -> "已安排 ${summary.usedMinutes} 分钟（每日容量未设置）"
    summary.overloaded -> "已安排 ${summary.usedMinutes} / ${summary.capacityMinutes} 分钟（超载）"
    else -> "已安排 ${summary.usedMinutes} / ${summary.capacityMinutes} 分钟"
}

@Composable
private fun ScheduleEditDialog(
    entry: ScheduleEntry,
    targets: List<Target>,
    onDismiss: () -> Unit,
    onSave: (ScheduleEntry) -> Unit,
) {
    var date by remember(entry.id) { mutableStateOf(entry.planningDate) }
    var title by remember(entry.id) { mutableStateOf(entry.title) }
    var startTime by remember(entry.id) { mutableStateOf(entry.startMinute?.let(::formatScheduleMinute) ?: "") }
    var endTime by remember(entry.id) { mutableStateOf(entry.endMinute?.let(::formatScheduleMinute) ?: "") }
    var estimate by remember(entry.id) { mutableStateOf(entry.estimatedMinutes?.toString() ?: "") }
    var note by remember(entry.id) { mutableStateOf(entry.note) }
    val context = LocalContext.current
    var reminderTime by remember(entry.id) { mutableStateOf(entry.reminderEpochMillis?.let(::formatReminderEpoch) ?: "") }
    var targetId by remember(entry.id) { mutableStateOf(entry.targetId) }
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
                    AttentionOutlinedButton(onClick = { targetMenuExpanded = true }) { Text(targets.firstOrNull { it.id == targetId }?.title ?: "关联计划（可选）") }
                    DropdownMenu(expanded = targetMenuExpanded, onDismissRequest = { targetMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("不关联计划") }, onClick = { targetId = null; targetMenuExpanded = false })
                        targets.forEach { target -> DropdownMenuItem(text = { Text(target.title) }, onClick = { targetId = target.id; targetMenuExpanded = false }) }
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
                error = validateScheduleForm(
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
                if (error == null) onSave(entry.copy(
                    planningDate = parsedDate.toString(),
                    title = title,
                    startMinute = parsedStart,
                    endMinute = parsedEnd,
                    estimatedMinutes = estimated,
                    note = note,
                    targetId = targetId,
                    reminderEpochMillis = parsedReminder?.let { minute ->
                        reminderEpochMillis(parsedDate!!, minute)
                    },
                ).also { if (parsedReminder != null) requestReminderPermission(context) })
            }) { Text("保存") }
        },
        dismissButton = { AttentionTextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun parseScheduleMinute(value: String, allowEndOfDay: Boolean = false): Int? {
    if (value.isBlank()) return null
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if ((hour in 0..23 && minute in 0..59) || (allowEndOfDay && hour == 24 && minute == 0)) hour * 60 + minute else null
}

private fun formatScheduleMinute(value: Int): String = "%02d:%02d".format(value / 60, value % 60)

private fun formatReminderEpoch(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).let { formatScheduleMinute(it.hour * 60 + it.minute) }

private fun requestReminderPermission(context: Context) {
    if (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) return
    context.findActivity()?.let {
        ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REMINDER_PERMISSION_REQUEST_CODE)
    }
}

private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val REMINDER_PERMISSION_REQUEST_CODE = 4101

private fun weekdayLabel(day: Int): String = when (day) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    7 -> "日"
    else -> day.toString()
}

private fun validateRecurrenceForm(
    title: String,
    startDate: LocalDate?,
    untilDateText: String,
    untilDate: LocalDate?,
    frequency: ScheduleFrequency,
    weekdays: Set<Int>,
    dayOfMonth: Int?,
    estimatedMinutesText: String,
    estimatedMinutes: Int?,
    reminderText: String,
    reminderMinute: Int?,
): String? = when {
    startDate == null -> "请输入有效的开始规划日"
    title.isBlank() -> "重复日程标题不能为空"
    untilDateText.isNotBlank() && untilDate == null -> "请输入有效的结束规划日"
    untilDate != null && untilDate.isBefore(startDate) -> "结束规划日不能早于开始规划日"
    estimatedMinutesText.isNotBlank() && estimatedMinutes == null -> "请输入有效的预计分钟"
    estimatedMinutes != null && estimatedMinutes <= 0 -> "预计时长必须为正数"
    reminderText.isNotBlank() && reminderMinute == null -> "请输入有效的提醒时间 HH:mm"
    frequency == ScheduleFrequency.WEEKLY && weekdays.isEmpty() -> "每周规则至少选择一个星期"
    frequency == ScheduleFrequency.MONTHLY && dayOfMonth !in 1..31 -> "月内日期必须在 1 到 31 之间"
    frequency != ScheduleFrequency.MONTHLY && dayOfMonth != null -> "只有每月规则可以设置月内日期"
    else -> null
}

private data class ScheduleFormInput(
    val title: String,
    val date: LocalDate?,
    val startText: String,
    val endText: String,
    val estimateText: String,
    val startMinute: Int?,
    val endMinute: Int?,
    val estimateMinutes: Int?,
    val reminderText: String = "",
    val reminderMinute: Int? = null,
)

private fun validateScheduleForm(input: ScheduleFormInput): String? = when {
    input.date == null -> "请输入有效的规划日"
    input.title.isBlank() -> "日程标题不能为空"
    input.startText.isNotBlank() && input.startMinute == null -> "请输入有效的开始时间 HH:mm"
    input.endText.isNotBlank() && input.endMinute == null -> "请输入有效的结束时间 HH:mm"
    (input.startMinute == null) != (input.endMinute == null) -> "时间段必须同时填写开始和结束时间"
    input.startMinute != null && input.endMinute != null && input.endMinute <= input.startMinute -> "结束时间必须晚于开始时间"
    input.estimateText.isNotBlank() && input.estimateMinutes == null -> "请输入有效的预计分钟"
    input.estimateMinutes != null && input.estimateMinutes <= 0 -> "预计时长必须为正数"
    input.reminderText.isNotBlank() && input.reminderMinute == null -> "请输入有效的提醒时间 HH:mm"
    else -> null
}

internal fun initialLaunchDestination(
    hasExternalScheduleRequest: Boolean,
    requested: LaunchDestination,
    lastOpened: LaunchDestination,
): LaunchDestination = if (hasExternalScheduleRequest) {
    LaunchDestination.ALL_DATES
} else {
    resolveLaunchDestination(requested, lastOpened)
}

internal fun shouldPersistLastOpened(settingsLoaded: Boolean, settingsOpen: Boolean): Boolean =
    settingsLoaded && !settingsOpen

private fun AttentionState.targetPath(targetId: String, onDate: LocalDate? = null): String {
    val names = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    var current = targets.firstOrNull { it.id == targetId }
    while (current != null && seen.add(current.id)) {
        names += current.title
        val parentId = onDate?.let { parentAt(current.id, it) } ?: current.parentId
        current = parentId?.let { id -> targets.firstOrNull { it.id == id } }
    }
    return names.asReversed().joinToString(" / ")
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
