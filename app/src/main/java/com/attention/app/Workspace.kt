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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.attention.app.data.backup.BackupPreview
import com.attention.app.data.backup.BackupCommitResult
import com.attention.app.data.backup.ExportBundle
import com.attention.app.data.backup.ExportDocument
import com.attention.app.data.backup.ImportMode
import com.attention.app.data.backup.ImportPlan
import com.attention.app.data.backup.FileImportBackupStore
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.createSettingsStore
import com.attention.app.timer.AttentionTimerService
import com.attention.app.timer.AttentionBootReceiver
import com.attention.app.reminder.AlarmReminderCoordinator
import com.attention.app.reminder.ReminderScheduler
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionInlineError
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.components.AttentionSnackbarHost
import com.attention.app.ui.components.AttentionTextButton
import com.attention.app.ui.components.rememberAttentionFeedbackHostState
import com.attention.app.ui.theme.attentionTokens
import com.attention.app.agenda.AgendaScreen
import com.attention.app.agenda.AgendaSelection
import com.attention.app.agenda.rememberAgendaSelection
import com.attention.app.settings.SettingsScreen
import com.attention.app.statistics.StatisticsScreen
import com.attention.app.statistics.StatisticsSelection
import com.attention.app.statistics.rememberStatisticsSelection
import com.attention.app.targets.TargetsScreen
import com.attention.app.targets.rememberTargetsSelection
import com.attention.app.today.TodayScreen
import com.attention.workspace.state.AgendaActions
import com.attention.workspace.state.AgendaRecurrenceDraft
import com.attention.workspace.state.AgendaScheduleDraft
import com.attention.workspace.state.OnboardingScheduleDraft
import com.attention.workspace.state.SettingsActions
import com.attention.workspace.state.StatisticsActions
import com.attention.workspace.state.TargetsActions
import com.attention.workspace.state.TodayActions
import com.attention.workspace.state.agendaScreenState
import com.attention.workspace.state.settingsScreenState
import com.attention.workspace.state.statisticsScreenState
import com.attention.workspace.state.targetsScreenState
import com.attention.workspace.state.todayScreenState
import com.attention.workspace.state.toRecurrenceRule
import com.attention.domain.AttentionState
import com.attention.domain.CapacitySummary
import com.attention.domain.GoalCadence
import com.attention.domain.FutureGoalRule
import com.attention.domain.LaunchDestination
import com.attention.domain.Migration
import com.attention.domain.newId
import com.attention.domain.ScheduleEntry
import com.attention.domain.StoredSettings
import com.attention.domain.TimeEntry
import com.attention.domain.capacitySummary
import com.attention.domain.planningDate
import com.attention.domain.reminderEpochMillis
import com.attention.domain.resolveLaunchDestination
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 今天工作台的 actions adapter，在组合根一侧组装：页面发出 intent，这里落到 ViewModel、
 * 设置存储、可撤销提示与所有 Android 平台能力。ViewModel、Room、`Context` 与前台计时服务
 * 因此只出现在组合根侧，页面 module 一个都拿不到（ADR-0005 第 1 节、第 4 节「Android adapter 归属组合根」）。
 */
private class TodayActionsAdapter(
    private val viewModel: AttentionViewModel,
    private val currentWeekStartDay: () -> Int,
    private val context: Context,
    private val zone: ZoneId,
    private val onTimeRecordDeleted: (TimeEntry) -> Unit,
    private val onFeedbackMessage: (String) -> Unit,
) : TodayActions {
    override fun recordTime(planningDate: String, minutes: Int, targetId: String?) {
        viewModel.addTime(planningDate, minutes, targetId)
    }

    // 只调整时长、目标归属与备注：记录的规划日与来源由领域层保持不变。
    override fun editTime(entryId: String, minutes: Int, targetId: String?, note: String) {
        viewModel.editTime(entryId, minutes, targetId, note)
    }

    override fun deleteTime(entryId: String) {
        viewModel.deleteTimeWithUndo(entryId, onTimeRecordDeleted)
    }

    override fun saveDailyPlanningSettings(boundaryMinutes: Int, dailyCapacityMinutes: Int?) {
        viewModel.setSettings(StoredSettings(boundaryMinutes, currentWeekStartDay(), dailyCapacityMinutes))
    }

    override fun selectWeekStartDay(day: Int) {
        viewModel.setWeekStart(day)
    }

    override fun startTimer(targetId: String?) {
        viewModel.startTimer(targetId, onSuccess = ::startForegroundCounting)
    }

    override fun pauseTimer() {
        viewModel.pauseTimer()
    }

    override fun resumeTimer() {
        viewModel.resumeTimer()
    }

    override fun recoverTimer() {
        viewModel.recoverTimer(onSuccess = ::startForegroundCounting)
    }

    override fun stopTimer() {
        viewModel.stopTimer(onSuccess = {
            AttentionBootReceiver.cancelRecoveryPrompt(context)
            context.stopService(AttentionTimerService.intent(context))
        })
    }

    override fun switchTimer(targetId: String?) {
        viewModel.switchTimer(targetId)
    }

    override fun requestNotificationPermission() = requestReminderPermission(context)

    override fun finishOnboarding() {
        viewModel.finishOnboarding()
    }

    override fun saveOnboardingPlanningSettings(
        boundaryMinutes: Int,
        weekStartDay: Int,
        dailyCapacityMinutes: Int?,
    ) {
        viewModel.setSettings(StoredSettings(boundaryMinutes, weekStartDay, dailyCapacityMinutes))
    }

    override fun createOnboardingTarget(title: String, onSuccess: () -> Unit) {
        viewModel.addTarget(title) { onSuccess() }
    }

    override fun createOnboardingSchedule(draft: OnboardingScheduleDraft) {
        viewModel.addSchedule(
            ScheduleEntry(
                planningDate = draft.planningDate,
                title = draft.title,
                startMinute = draft.startMinute,
                endMinute = draft.endMinute,
                estimatedMinutes = draft.estimatedMinutes,
                note = draft.note,
                targetId = draft.targetId,
            ),
        )
    }

    override fun updateTodaySchedule(entry: ScheduleEntry, reminderMinuteOfDay: Int?) {
        viewModel.updateSchedule(
            entry.copy(
                reminderEpochMillis = reminderMinuteOfDay?.let { minute ->
                    reminderEpochMillis(LocalDate.parse(entry.planningDate), minute, zone)
                },
            ),
        )
    }

    override fun confirmScheduleCompleted(entryId: String) {
        viewModel.confirmScheduleCompleted(entryId)
    }

    override fun toggleScheduleCompleted(entryId: String) {
        viewModel.toggleScheduleCompleted(entryId)
    }

    /**
     * 计时开始或「从此刻继续」成功之后的平台动作：撤掉恢复提示、在通知不可用时给出提示，
     * 再启动前台计时服务。前台计时必须仍由可见 UI 触发（ADR-0001），因此这一步只在这条路径上。
     */
    private fun startForegroundCounting() {
        AttentionBootReceiver.cancelRecoveryPrompt(context)
        if (!notificationsEnabled(context)) {
            onFeedbackMessage("通知权限未开启，计时仍会保存；通知栏控制不可用。")
        }
        ContextCompat.startForegroundService(context, AttentionTimerService.intent(context))
    }
}

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

/**
 * 所有日期页的 actions adapter，在组合根一侧组装：页面发出 intent，这里落到 ViewModel、
 * 提醒的绝对时刻换算与通知权限申请。ViewModel、`Activity`/`Context` 与平台能力
 * 因此只出现在组合根侧（ADR-0005 第 4 节「Android adapter 归属组合根」）。
 */
private class AgendaActionsAdapter(
    private val viewModel: AttentionViewModel,
    private val selection: AgendaSelection,
    private val currentDate: () -> LocalDate,
    private val zone: ZoneId,
    private val onStartTimerFromEntry: (ScheduleEntry) -> Unit,
    private val onRequestReminderPermission: () -> Unit,
) : AgendaActions {
    override fun selectPlanningDate(date: LocalDate?) {
        selection.selectDate(date)
    }

    override fun selectAllDates() {
        selection.selectDate(null)
    }

    override fun shiftPlanningDay(days: Long) {
        selection.shiftDays(days, currentDate())
    }

    override fun addSchedule(draft: AgendaScheduleDraft) {
        val date = LocalDate.parse(draft.planningDate)
        viewModel.addSchedule(
            ScheduleEntry(
                planningDate = draft.planningDate,
                title = draft.title,
                startMinute = draft.startMinute,
                endMinute = draft.endMinute,
                estimatedMinutes = draft.estimatedMinutes,
                note = draft.note,
                targetId = draft.targetId,
                reminderEpochMillis = draft.reminderMinuteOfDay?.let { minute ->
                    reminderEpochMillis(date, minute, zone)
                },
            ),
        )
    }

    override fun updateSchedule(entry: ScheduleEntry, reminderMinuteOfDay: Int?) {
        viewModel.updateSchedule(
            entry.copy(
                reminderEpochMillis = reminderMinuteOfDay?.let { minute ->
                    reminderEpochMillis(LocalDate.parse(entry.planningDate), minute, zone)
                },
            ),
        )
    }

    override fun confirmScheduleCompleted(entryId: String) {
        viewModel.confirmScheduleCompleted(entryId)
    }

    override fun toggleScheduleCompleted(entryId: String) {
        viewModel.toggleScheduleCompleted(entryId)
    }

    // 条目一起带出，因此产出的时间记录保留同一个来源关联。
    override fun startTimerFromEntry(entry: ScheduleEntry) = onStartTimerFromEntry(entry)

    override fun addRecurrenceAndMaterialize(draft: AgendaRecurrenceDraft) {
        val rule = draft.toRecurrenceRule()
        viewModel.addRecurrenceAndMaterialize(rule, LocalDate.parse(rule.startDate).plusDays(30))
    }

    override fun stopRecurrence(ruleId: String) {
        viewModel.stopRecurrence(ruleId)
    }

    override fun requestReminderPermission() = onRequestReminderPermission()
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
    private val onRequestRestoreImportBackup: () -> Unit,
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

    // 恢复导入前备份需要用户显式确认：页面只发出 intent，由组合根打开确认对话框，
    // 真正的恢复由对话框确认后调用 ViewModel 的结果化命令完成。
    override fun restoreLastImportBackup() {
        onRequestRestoreImportBackup()
    }

    override fun previewJsonBackup() = onPreviewJsonBackup()

    override fun importJson() = onImportJson()

    override fun exportTimeEntriesCsv() = onExportTimeEntriesCsv()

    override fun previewCsv() = onPreviewCsv()

    override fun exportSchedulesCsv() = onExportSchedulesCsv()

    override fun exportCsvBundle() = onExportCsvBundle()
}

/**
 * 目标树页的 actions adapter，在组合根一侧组装：页面发出 intent，这里落到 ViewModel，
 * 并在这里构造领域对象（`FutureGoalRule`、`Migration`）与生成 id。
 * ViewModel、Room 与 id 生成器因此只出现在组合根侧。
 */
private class TargetsActionsAdapter(
    private val viewModel: AttentionViewModel,
    private val currentState: () -> AttentionState,
) : TargetsActions {
    override fun addTarget(title: String, parentId: String?) {
        viewModel.addTarget(title, parentId)
    }

    override fun renameTarget(targetId: String, title: String) {
        viewModel.renameTarget(targetId, title)
    }

    override fun addGoal(
        targetId: String,
        cadence: GoalCadence,
        targetMinutes: Int,
        startDate: String,
        dueDate: String?,
    ) {
        viewModel.addGoal(targetId, cadence, targetMinutes, startDate, dueDate)
    }

    override fun assignUnowned(timeEntryIds: Set<String>, targetId: String) {
        viewModel.assignUnowned(timeEntryIds, targetId)
    }

    override fun saveFutureGoal(
        targetId: String,
        ruleId: String?,
        cadence: GoalCadence,
        targetMinutes: Int,
        effectiveFrom: String,
        dueDate: String?,
        onDate: LocalDate,
    ) {
        val rule = FutureGoalRule(
            id = ruleId ?: newId(),
            targetId = targetId,
            cadence = cadence,
            targetMinutes = targetMinutes,
            effectiveFrom = effectiveFrom,
            dueDate = dueDate,
        )
        if (ruleId == null) viewModel.addFutureGoal(rule, onDate) else viewModel.updateFutureGoal(rule, onDate)
    }

    override fun cancelFutureGoal(ruleId: String, onDate: LocalDate) {
        viewModel.cancelFutureGoal(ruleId, onDate)
    }

    override fun appendOneTimeGoal(
        targetId: String,
        targetMinutes: Int,
        startDate: String,
        dueDate: String?,
        onSuccess: () -> Unit,
    ) {
        viewModel.appendOneTimeGoal(targetId, targetMinutes, startDate, dueDate, onSuccess)
    }

    override fun saveMigration(
        targetId: String,
        migrationId: String?,
        sourceStageId: String,
        minutes: Int,
        destinationStartDate: String,
        destinationEndDate: String?,
        onDate: LocalDate,
    ) {
        val migration = Migration(
            id = migrationId ?: newId(),
            targetId = targetId,
            sourceStageId = sourceStageId,
            minutes = minutes,
            destinationStartDate = destinationStartDate,
            destinationEndDate = destinationEndDate,
            cancelled = false,
        )
        if (migrationId == null) viewModel.addMigration(migration, onDate) else viewModel.updateMigration(migration, onDate)
    }

    override fun cancelMigration(migrationId: String) {
        viewModel.cancelMigration(migrationId)
    }

    override fun toggleTarget(targetId: String, expanded: Boolean) {
        viewModel.toggleTarget(targetId, expanded)
    }

    override fun reorderTarget(targetId: String, newIndex: Int) {
        viewModel.reorderTarget(targetId, newIndex)
    }

    override fun archiveTarget(targetId: String) {
        viewModel.archiveTarget(targetId)
    }

    override fun restoreTargets(targetIds: Set<String>) {
        viewModel.restoreTargets(targetIds)
    }

    override fun deleteTargetRelations(targetIds: Set<String>) {
        viewModel.deleteTargetRelations(targetIds)
    }

    override fun moveTargetFrom(targetId: String, parentId: String?, effectiveFrom: String) {
        viewModel.moveTargetFrom(targetId, parentId, effectiveFrom)
    }

    override fun updateFutureTargetMove(moveId: String, parentId: String?, effectiveFrom: String) {
        val existing = currentState().targetMoves.firstOrNull { it.id == moveId } ?: return
        viewModel.updateFutureTargetMove(existing.copy(parentId = parentId, effectiveFrom = effectiveFrom))
    }

    override fun cancelFutureTargetMove(moveId: String) {
        viewModel.cancelFutureTargetMove(moveId)
    }
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
    var jsonPreview by remember { mutableStateOf<String?>(null) }
    var pendingJsonBackup by remember { mutableStateOf<ExportDocument?>(null) }
    var jsonExportMessage by remember { mutableStateOf<String?>(null) }
    var pendingTimeEntriesCsv by remember { mutableStateOf<ExportDocument?>(null) }
    var pendingCsvBundle by remember { mutableStateOf<ExportBundle?>(null) }
    var csvBundleMessage by remember { mutableStateOf<String?>(null) }
    val timeEntriesCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        val document = pendingTimeEntriesCsv
        pendingTimeEntriesCsv = null
        if (uri == null) {
            feedbackScope.launch { feedback.showMessage("已取消保存时间记录 CSV") }
        } else if (document == null) {
            feedbackScope.launch { feedback.showMessage("时间记录 CSV 导出失败") }
        } else {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    writeUtf8Csv(document.content, output)
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
        val document = pendingJsonBackup
        pendingJsonBackup = null
        if (uri == null) {
            jsonExportMessage = "已取消保存 JSON 备份"
        } else if (document == null) {
            jsonExportMessage = "JSON 备份导出失败：没有可保存的数据"
        } else {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.let { output ->
                    writeUtf8Json(document.content, output)
                } ?: error("无法打开保存位置")
            }.onSuccess {
                jsonExportMessage = "JSON 备份已保存"
            }.onFailure {
                jsonExportMessage = "JSON 备份保存失败：${it.message ?: "无法写入文件"}"
            }
        }
    }
    var csvPreview by remember { mutableStateOf<String?>(null) }
    var pendingScheduleCsv by remember { mutableStateOf<ExportDocument?>(null) }
    var scheduleExportMessage by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<ImportPlan?>(null) }
    var pendingClearImport by remember { mutableStateOf<ImportPlan?>(null) }
    var restoreImportPromptVisible by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("无法读取所选文件")
            }
            result.onSuccess { encoded ->
                if (encoded.isNotBlank()) {
                    viewModel.prepareImport(
                        encoded = encoded,
                        clearExisting = false,
                        onResult = { plan -> pendingImport = plan },
                        onFailure = { error -> jsonExportMessage = "导入失败：${error.message ?: "无法解析文件"}" },
                    )
                } else {
                    jsonExportMessage = "导入失败：文件为空"
                }
            }.onFailure {
                jsonExportMessage = "导入失败：${it.message ?: "无法读取文件"}"
            }
        }
    }
    val scheduleExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val document = pendingScheduleCsv
        pendingScheduleCsv = null
        if (uri == null) {
            scheduleExportMessage = "已取消日程 CSV 导出"
        } else if (document == null) {
            scheduleExportMessage = "日程 CSV 导出失败：没有可保存的数据"
        } else {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("无法打开保存位置")
                output.use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).use { writer -> writer.write(document.content) }
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
                context.contentResolver.openOutputStream(uri)?.use { output -> writeUtf8Csv(bundle.schedules.content, output) }
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
                context.contentResolver.openOutputStream(uri)?.use { output -> writeUtf8Csv(bundle.timeEntries.content, output) }
                    ?: error("无法打开保存位置")
            }.onSuccess {
                csvBundleMessage = "时间记录 CSV 已保存；请选择日程 CSV 的保存位置"
                combinedScheduleExportLauncher.launch(bundle.schedules.suggestedFileName)
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

    // 今天工作台的选择状态属于页面 module（TodaySelection），组合根只喂投影，不碰它的字段。
    // 今天工作台不导航规划日，因此投影除注入的时间与时区外不需要页面选择参数。
    // 通知是否可用是平台事实，组合根查好后注入：页面与投影都不触达通知管理器。
    val todayState = todayScreenState(
        state = state,
        now = injectedNow,
        zone = injectedZone,
        notificationsEnabled = notificationsEnabled(context),
    )
    val currentWeekStartDay by androidx.compose.runtime.rememberUpdatedState(state.settings.weekStartDay)
    val todayActions: TodayActions = remember(viewModel) {
        TodayActionsAdapter(
            viewModel = viewModel,
            currentWeekStartDay = { currentWeekStartDay },
            context = context,
            zone = injectedZone,
            // 删除的可撤销提示由组合根展示：撤销要用回被删掉的那条记录本身，
            // 页面因此不需要认识时间记录这个领域类型。
            onTimeRecordDeleted = { removed ->
                feedbackScope.launch {
                    feedback.showUndoableMessage(
                        message = "已删除这条实际投入，它不再计入目标进度与统计。",
                        onUndo = { viewModel.restoreTimeEntry(removed) },
                    )
                }
            },
            // 计时开始时的通知不可用提示同样由组合根展示，页面只发 intent。
            onFeedbackMessage = { message -> feedbackScope.launch { feedback.showMessage(message) } },
        )
    }
    // 所有日期页的选中规划日由页面 module 提供类型与迁移语义，组合根只持有一个实例来喂投影。
    val agendaSelection = rememberAgendaSelection(
        openScheduleDate?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() },
    )
    val agendaState = agendaScreenState(
        state = state,
        now = injectedNow,
        zone = injectedZone,
        selectedDate = agendaSelection.selectedDate,
        openScheduleId = openScheduleId,
    )

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
                viewModel.exportJsonDocument { document ->
                    jsonPreview = document.content.take(800)
                    pendingJsonBackup = document
                    jsonExportLauncher.launch(document.suggestedFileName)
                }
            },
            onImportJson = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
            onExportTimeEntriesCsv = {
                viewModel.exportTimeEntriesCsvDocument { document ->
                    pendingTimeEntriesCsv = document
                    timeEntriesCsvLauncher.launch(document.suggestedFileName)
                }
            },
            onPreviewCsv = {
                viewModel.exportCsvBundleDocument { bundle ->
                    csvPreview = "时间记录\n${bundle.timeEntries.content}\n日程\n${bundle.schedules.content}"
                }
            },
            onExportSchedulesCsv = {
                scheduleExportMessage = null
                viewModel.exportSchedulesCsvDocument { document ->
                    pendingScheduleCsv = document
                    scheduleExportLauncher.launch(document.suggestedFileName)
                }
            },
            onExportCsvBundle = {
                csvBundleMessage = null
                viewModel.exportCsvBundleDocument { bundle ->
                    pendingCsvBundle = bundle
                    combinedTimeExportLauncher.launch(bundle.timeEntries.suggestedFileName)
                }
            },
            onRequestRestoreImportBackup = { restoreImportPromptVisible = true },
        )
    }

    // 目标树页的选择与表单草稿由目标树页 module 提供类型与迁移语义，
    // 组合根只持有一个实例来喂投影，不改写它的字段。
    val targetsSelection = rememberTargetsSelection(state.planningDate(injectedNow, injectedZone))
    val targetsState = targetsScreenState(
        state = state,
        now = injectedNow,
        zone = injectedZone,
        selection = targetsSelection.snapshot(),
    )
    val currentAttentionState by androidx.compose.runtime.rememberUpdatedState(state)
    val targetsActions: TargetsActions = remember(viewModel) {
        TargetsActionsAdapter(
            viewModel = viewModel,
            currentState = { currentAttentionState },
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

    // 所有日期页的 adapter 在组合根组装：计时服务启动与提醒权限申请都属于这里，页面只发 intent。
    val currentAgendaDate by androidx.compose.runtime.rememberUpdatedState(
        agendaState.selectedDate ?: agendaState.planningDate,
    )
    val agendaActions: AgendaActions = remember(viewModel) {
        AgendaActionsAdapter(
            viewModel = viewModel,
            selection = agendaSelection,
            currentDate = { currentAgendaDate },
            zone = injectedZone,
            onStartTimerFromEntry = ::startTimerFromSchedule,
            onRequestReminderPermission = { requestReminderPermission(context) },
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
                LaunchDestination.TODAY, LaunchDestination.LAST_OPENED -> TodayScreen(
                    state = todayState,
                    actions = todayActions,
                )
                LaunchDestination.TARGETS -> TargetsScreen(
                    state = targetsState,
                    actions = targetsActions,
                    selection = targetsSelection,
                )
                LaunchDestination.ALL_DATES -> AgendaScreen(
                    state = agendaState,
                    actions = agendaActions,
                    openScheduleId = openScheduleId,
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
    pendingImport?.let { plan ->
        AlertDialog(
            onDismissRequest = {
                pendingImport = null
            },
            title = { Text("导入预览") },
            text = {
                Text(
                    backupPreviewText(plan.preview, state, plan.candidateState, plan.mode),
                )
            },
            confirmButton = {
                AttentionTextButton(onClick = {
                    viewModel.commitImport(
                        plan,
                        onResult = { result ->
                            if (result is BackupCommitResult.Committed || result is BackupCommitResult.StalePlan) {
                                pendingImport = null
                            }
                        },
                    )
                }) { Text("合并导入") }
            },
            dismissButton = {
                Row {
                    AttentionTextButton(onClick = {
                        pendingClearImport = plan.forReplacement()
                        pendingImport = null
                    }) { Text("清空后恢复") }
                    AttentionTextButton(onClick = {
                        pendingImport = null
                    }) { Text("取消") }
                }
            },
        )
    }
    pendingClearImport?.let { plan ->
        AlertDialog(
            onDismissRequest = {
                pendingClearImport = null
            },
            title = { Text("确认清空后恢复") },
            text = {
                Text(
                    backupPreviewText(plan.preview, state, plan.candidateState, plan.mode) +
                        "\n这会清空当前全部数据，并在导入前保存保护性备份。确定继续吗？",
                )
            },
            confirmButton = {
                AttentionTextButton(onClick = {
                    viewModel.commitImport(
                        plan,
                        onResult = { result ->
                            if (result is BackupCommitResult.Committed || result is BackupCommitResult.StalePlan) {
                                pendingClearImport = null
                            }
                        },
                    )
                }) { Text("确认清空并恢复") }
            },
            dismissButton = {
                AttentionTextButton(onClick = {
                    pendingClearImport = null
                }) { Text("取消") }
            },
        )
    }
    if (restoreImportPromptVisible) {
        AlertDialog(
            onDismissRequest = { restoreImportPromptVisible = false },
            title = { Text("确认恢复导入前备份", modifier = Modifier.testTag("restore-import-dialog")) },
            text = { Text("将当前状态替换为最近一次导入前保存的备份，是否继续？") },
            confirmButton = {
                AttentionTextButton(
                    modifier = Modifier.testTag("restore-import-confirm"),
                    onClick = {
                        restoreImportPromptVisible = false
                        viewModel.restoreLastImportBackupResult(onResult = { result ->
                            if (result is BackupCommitResult.NoImportBackup) {
                                jsonExportMessage = "没有可恢复的导入前备份"
                            }
                        })
                    },
                ) { Text("确认恢复") }
            },
            dismissButton = {
                AttentionTextButton(
                    modifier = Modifier.testTag("restore-import-cancel"),
                    onClick = { restoreImportPromptVisible = false },
                ) { Text("取消") }
            },
        )
    }
}

private fun backupPreviewText(
    preview: BackupPreview,
    current: AttentionState,
    candidate: AttentionState?,
    mode: ImportMode,
): String = buildString {
    append("版本 ${preview.version}，共 ${preview.totalRecords} 条记录\n")
    append("目标 ${preview.targets}，阶段 ${preview.goalStages}，时间记录 ${preview.timeEntries}，日程 ${preview.schedules}\n")
    append("重复规则 ${preview.recurrenceRules}，迁移 ${preview.migrations}，未来目标规则 ${preview.futureGoalRules}\n")
    append("周期快照 ${preview.periodSnapshots}，目标移动 ${preview.targetMoves}，里程碑 ${preview.milestones}\n")
    append(if (preview.hasSettings) "包含设置。" else "不包含设置。")
    append(" ")
    append(if (preview.hasActiveTimer) "包含活动计时器。" else "不包含活动计时器。")
    if (candidate != null) {
        val currentCount = current.targets.size + current.goalStages.size + current.timeEntries.size + current.schedules.size +
            current.recurrenceRules.size + current.migrations.size + current.futureGoalRules.size + current.periodSnapshots.size +
            current.targetMoves.size + current.milestones.size
        val candidateCount = candidate.targets.size + candidate.goalStages.size + candidate.timeEntries.size + candidate.schedules.size +
            candidate.recurrenceRules.size + candidate.migrations.size + candidate.futureGoalRules.size + candidate.periodSnapshots.size +
            candidate.targetMoves.size + candidate.milestones.size
        val label = if (mode == ImportMode.REPLACE) "恢复后预计" else "合并后预计"
        append("\n${label} ${candidateCount} 条，可能新增约 ${candidateCount - currentCount} 条。")
    }
    append("\n导入前会自动备份当前数据。")
}

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
