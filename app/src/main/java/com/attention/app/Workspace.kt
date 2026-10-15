package com.attention.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import java.io.OutputStreamWriter
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
import com.attention.app.onboarding.OnboardingScreen
import com.attention.app.settings.SettingsScreen
import com.attention.app.statistics.StatisticsScreen
import com.attention.app.statistics.StatisticsSelection
import com.attention.app.statistics.rememberStatisticsSelection
import com.attention.app.targets.TargetsScreen
import com.attention.app.targets.rememberTargetsSelection
import com.attention.workspace.state.AgendaActions
import com.attention.workspace.state.AgendaRecurrenceDraft
import com.attention.workspace.state.AgendaScheduleDraft
import com.attention.workspace.state.OnboardingActions
import com.attention.workspace.state.SettingsActions
import com.attention.workspace.state.StatisticsActions
import com.attention.workspace.state.TargetsActions
import com.attention.workspace.state.agendaScreenState
import com.attention.workspace.state.onboardingScreenState
import com.attention.workspace.state.settingsScreenState
import com.attention.workspace.state.statisticsScreenState
import com.attention.workspace.state.targetsScreenState
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
import com.attention.domain.asAgendaEntry
import com.attention.domain.capacitySummary
import com.attention.domain.externalSchedulePlanningDate
import com.attention.domain.planningDate
import com.attention.domain.reminderEpochMillis
import com.attention.domain.resolveInitialLaunchDestination
import com.attention.domain.resolveLaunchDestination
import com.attention.domain.toTopLevelWorkspace
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 设置页的 actions adapter，在组合根一侧组装：页面发出 intent，这里落到 ViewModel、
 * 通知权限申请，以及导入导出与恢复所依赖的 SAF 文件选择器和确认对话框。
 * ViewModel、Room、DataStore 与平台能力因此只出现在组合根侧，页面拿不到它们（ADR-0005 第 1、4 节）。
 */
private class SettingsActionsAdapter(
    private val viewModel: AttentionViewModel,
    private val currentWeekStartDay: () -> Int,
    private val onRequestReminderPermission: () -> Unit,
    private val onPreviewJsonBackup: () -> Unit,
    private val onImportJson: () -> Unit,
    private val onExportTimeEntriesCsv: () -> Unit,
    private val onPreviewCsv: () -> Unit,
    private val onExportSchedulesCsv: () -> Unit,
    private val onExportCsvBundle: () -> Unit,
    private val onRequestRestoreImportBackup: () -> Unit,
    private val onOpenOnboarding: () -> Unit,
    private val onClose: () -> Unit,
) : SettingsActions {
    override fun selectLaunchDestination(destination: LaunchDestination) {
        viewModel.setLaunchDestination(destination)
    }

    // 界线与容量一起写进同一份规划设置；周起始日当前的值由组合根读出来一起提交。
    override fun savePlanningDaySettings(boundaryMinutes: Int, dailyCapacityMinutes: Int?) {
        viewModel.setSettings(StoredSettings(boundaryMinutes, currentWeekStartDay(), dailyCapacityMinutes))
    }

    override fun selectWeekStartDay(day: Int) {
        viewModel.setWeekStart(day)
    }

    override fun requestNotificationPermission() = onRequestReminderPermission()

    override fun previewJsonBackup() = onPreviewJsonBackup()

    override fun importJson() = onImportJson()

    override fun exportTimeEntriesCsv() = onExportTimeEntriesCsv()

    override fun previewCsv() = onPreviewCsv()

    override fun exportSchedulesCsv() = onExportSchedulesCsv()

    override fun exportCsvBundle() = onExportCsvBundle()

    // 恢复导入前备份需要用户显式确认：页面只发出 intent，由组合根打开确认对话框，
    // 真正的恢复由对话框确认后调用 ViewModel 的结果化命令完成。workflow 与迁移前一致。
    override fun restoreLastImportBackup() = onRequestRestoreImportBackup()

    override fun close() = onClose()

    // 重新打开引导：组合根负责把独立的全屏引导举到工作区之上，页面只发出这一条意图。
    override fun openOnboarding() = onOpenOnboarding()
}

/**
 * 首次使用引导的 actions adapter，在组合根一侧组装。
 *
 * 引导只有一个落地：更新引导状态（`SettingsSnapshot.onboardingCompleted`），并把用户送到
 * 当前规划日的日程。它**不**创建目标、日程、设置或时间记录——adapter 里除了
 * `viewModel.finishOnboarding()` 之外没有任何写业务数据的调用，这正是本票与旧引导卡片的区别。
 */
private class OnboardingActionsAdapter(
    private val viewModel: AttentionViewModel,
    private val onCompleted: () -> Unit,
) : OnboardingActions {
    override fun completeOnboarding() {
        viewModel.finishOnboarding()
        onCompleted()
    }
}

/**
 * 日程页的 actions adapter，在组合根一侧组装：页面发出 intent，这里落到 ViewModel、
 * 提醒的绝对时刻换算与通知权限申请。ViewModel、`Activity`/`Context` 与平台能力
 * 因此只出现在组合根侧（ADR-0005 第 4 节「Android adapter 归属组合根」）。
 */
private class AgendaActionsAdapter(
    private val viewModel: AttentionViewModel,
    private val selection: AgendaSelection,
    private val currentDate: () -> LocalDate,
    private val currentPlanningDay: () -> LocalDate,
    private val zone: ZoneId,
    private val onStartTimerFromEntry: (ScheduleEntry) -> Unit,
    private val onRequestReminderPermission: () -> Unit,
    private val onStopTimer: () -> Unit,
    private val onRecoverTimerFromNow: () -> Unit,
    private val onTimeRecordDeleted: (TimeEntry) -> Unit,
) : AgendaActions {
    override fun selectPlanningDate(date: LocalDate?) {
        // 点击那一刻的规划日要一起交进去：范围高亮按它判定，之后跨过规划日界线也不会丢。
        selection.selectDate(date, currentPlanningDay())
    }

    override fun selectAllDates() {
        selection.selectAllDates()
    }

    override fun shiftPlanningDay(days: Long) {
        selection.shiftDays(days, currentDate(), currentPlanningDay())
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

    // 结果面板上的「标记为完成」与行上的完成是同一件事：走同一条既有完成路径，
    // 完成标记与投入经验、里程碑仍由领域计算，不新造命令。
    override fun markStoppedScheduleCompleted(entryId: String) {
        viewModel.markStoppedScheduleCompleted(entryId)
        viewModel.clearStoppedEntryIds()
    }

    // 「结束并保留未完成」只是关闭这一次结果面板：投入已经记下，完成状态保持原样。
    override fun dismissStopResult() {
        viewModel.clearStoppedEntryIds()
    }

    // 条目一起带出，因此产出的时间记录保留同一个来源关联。
    override fun startTimerFromEntry(entry: ScheduleEntry) = onStartTimerFromEntry(entry)

    // 运行中的计时只能从日程页停掉：平台动作（停前台计时服务、撤掉恢复提示）仍归组合根。
    override fun stopRunningTimer() = onStopTimer()

    // 暂停、恢复、切换与从此刻继续都只是领域命令：通知栏是持久化状态的投影，
    // 因此这些动作不需要页面自己去碰前台服务（ADR-0005 第 4 节）。
    override fun pauseRunningTimer() {
        viewModel.pauseTimer()
    }

    override fun resumeRunningTimer() {
        viewModel.resumeTimer()
    }

    // 从此刻继续意味着用户已经对上一次被打断的计时做了决定，恢复提示要一并撤掉。
    override fun recoverRunningTimerFromNow() = onRecoverTimerFromNow()

    override fun switchRunningTimerTarget(targetId: String?) {
        viewModel.switchTimer(targetId)
    }

    override fun editTime(entryId: String, minutes: Int, targetId: String?, note: String) {
        viewModel.editTime(entryId, minutes, targetId, note)
    }

    // 删除的可撤销提示由组合根展示：撤销要用回被删掉的那条记录本身。
    override fun deleteTime(entryId: String) {
        viewModel.deleteTimeWithUndo(entryId, onTimeRecordDeleted)
    }

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
 * 统计页的 actions adapter，在组合根一侧组装：页面发出 intent，这里落到 ViewModel 与周期位移。
 * ViewModel 与平台能力因此只出现在组合根侧。
 */
private class StatisticsActionsAdapter(
    private val viewModel: AttentionViewModel,
    private val selection: StatisticsSelection,
    private val currentDate: () -> LocalDate,
    private val onOpenSettings: () -> Unit,
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

    // 设置页从统计页进入：顶级页面仍然是统计，因此这里不改变 destination。
    override fun openSettings() = onOpenSettings()
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val tokens = attentionTokens()
    // 短时提示的宿主状态只创建一次，挂在页面切换之上，因此五个页面共用同一个提示出口。
    val feedback = rememberAttentionFeedbackHostState()
    val feedbackScope = rememberCoroutineScope()
    val hasExternalScheduleRequest = openScheduleDate != null || openScheduleId != null
    // 启动优先级（外部日程请求 > 保存的启动入口 > 上次打开 > 当前规划日的日程）只在 :domain 解析，
    // 组合根不再自己实现一份（ADR-0005 第 1 节与「后续页面不得重复」）。
    var destination by remember {
        mutableStateOf(resolveInitialLaunchDestination(hasExternalScheduleRequest, state.launchDestination, state.lastOpenedDestination))
    }
    var destinationChosen by remember { mutableStateOf(hasExternalScheduleRequest) }
    // 启动落点是否已经落到一个「真实」的取值上，而不是仍停在暂定值上。
    //
    // [destination] 的初值是由**空的默认状态**解析出来的暂定值；状态真的读上来之后，
    // 下一个 LaunchedEffect 才把它纠正成用户保存的入口。这个纠正与「上次打开」的写回
    // 发生在同一次重组里：写回那条 effect 捕获到的是纠正之前的暂定值，于是会把暂定值
    // 当成用户真正停留的页面写回去，把「上次打开」永久改成日程（#179 设备实跑间歇失败）。
    // 因此写回额外要求落点已经确定：要么已被状态纠正，要么用户自己点过。
    // 外部请求**不算**落点已确定：它只是把用户送到日程，用户并没有因此选择过它（#185）。
    // 这一次外部送达由 [LastOpenedWriteBackFacts.fromExternalRequest] 单独挡住；
    // 用户在外部打开之后亲手点别的入口，onSelect 会把它置为已确定，那次切换照常记录。
    var destinationSettled by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    // 引导是否被用户重新打开：设置页的「重新打开引导」把它举起来。
    // 自动出现的那一次由持久化的引导状态决定，不由这个变量决定。
    var onboardingRequested by remember { mutableStateOf(false) }
    // 引导状态直接读设置快照，并允许「还没读到」这个第三种状态：
    // 未读到时不显示引导，因此已完成引导的老用户不会先看到一帧全屏引导。
    val persistedSettings by remember { context.createSettingsStore().settings }
        .collectAsStateWithLifecycle(initialValue = null)
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
            // 落点此刻才是用户保存的那个；在这之前写回去的都是暂定值。
            destinationSettled = true
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        context.createSettingsStore().settings.first()
        persistedSettingsLoaded = true
    }
    // 「上次打开」写回之前必须等 ViewModel 真的把持久化状态读上来（[state] 在那之前是一份空的
    // 默认值），并且必须等启动落点已经确定：落点的初值也是由那份空默认值解析出来的暂定值，
    // 与状态纠正发生在同一次重组里，写回那条 effect 捕获到的正是纠正之前的暂定值。
    // 把它抢先写回去会永久覆盖用户上次真正停留的页面（#174、#179 设备实跑间歇失败）。
    val stateLoaded by viewModel.stateLoaded.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(destination, settingsOpen, persistedSettingsLoaded, stateLoaded, destinationSettled) {
        // 底部导航只有三项，因此「上次打开」写入的永远是它自己：旧入口值先按领域兼容映射落成日程。
        if (shouldPersistLastOpened(
                LastOpenedWriteBackFacts(
                    settingsLoaded = persistedSettingsLoaded,
                    settingsOpen = settingsOpen,
                    stateLoaded = stateLoaded,
                    destinationSettled = destinationSettled,
                    fromExternalRequest = hasExternalScheduleRequest,
                ),
            )
        ) {
            viewModel.saveLastOpened(destination.asAgendaEntry())
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.restoreReminders() }

    // 返回键只处理当前可关闭的层（ADR-0005 第 4 节：平台行为归组合根）。
    // 落点由纯函数决定：主页面之间没有返回栈，因此这里不看标签历史——
    // 在日程、目标树、统计之间来回切换之后按返回键不会逐页回放标签。
    // 设置页是唯一挂在主内容里、而不是对话框窗口里的可关闭层，必须由返回键关掉；
    // 日程详情对话框与日期选择器在自己的窗口里先消费掉返回键，优先级天然更高。
    when (resolveBackTarget(settingsOpen)) {
        WorkspaceBackTarget.CLOSE_SETTINGS_PAGE -> BackHandler(enabled = true) { settingsOpen = false }
        // 没有可关闭的层就不注册处理器：返回键原样交给系统，退出行为不变（#175）。
        WorkspaceBackTarget.SYSTEM -> Unit
    }

    // 系统时钟只在组合根读一次，所有页面投影共用这一份 now 与 zone（ADR-0005 第 3 节）。
    val injectedNow = Instant.now()
    val injectedZone = ZoneId.systemDefault()

    // 从日程条目起计时：日程页的「今日安排」与所有日期范围共用这一份实现，
    // 因此产出同样的时间记录与同样的平台行为（保留 scheduleEntryId 来源关联）。
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

    val currentWeekStartDay by androidx.compose.runtime.rememberUpdatedState(state.settings.weekStartDay)
    // 日程页的选中范围由页面 module 提供类型与迁移语义，组合根只持有一个实例来喂投影。
    // 这一个实例挂在组合根上、活过页面切换，因此会话内的日期筛选被保留；
    // 它不写入任何持久化，重新启动时组合根新建实例，页面回到当前规划日。
    // 组合根把当前规划日喂给选择状态问「你选的是什么」，不解释 null 与规划日之间的语义。
    //
    // 打开定位的优先级：外部日程请求点名的规划日 > 当前规划日（深链优先级由 #174 决定）。
    // 外部请求点名了规划日就定位到那一天（并由 openScheduleId 定位到条目本身）；
    // 没有外部请求时选择状态没有显式选择，因此跟随当前规划日。
    val externalPlanningDate = externalSchedulePlanningDate(state, openScheduleDate, openScheduleId)
    val agendaSelection = rememberAgendaSelection(externalPlanningDate)
    val agendaPlanningDay = state.planningDate(injectedNow, injectedZone)
    val stoppedEntryIds by viewModel.stoppedEntryIds.collectAsStateWithLifecycle()
    val agendaState = agendaScreenState(
        state = state,
        now = injectedNow,
        zone = injectedZone,
        selectedDate = agendaSelection.selectedDate(agendaPlanningDay),
        // 范围高亮来自选择状态自己记住的显式范围，跨规划日界线也不会丢。
        selectedScope = agendaSelection.selectedRangeScope(agendaPlanningDay),
        openScheduleId = openScheduleId,
        // 「刚刚停止的那一笔」由组合根交出：停止命令成功后 ViewModel 记下这次产出的记录 id，
        // 用户消费结果时清空。页面因此不缓存任何历史，也不会反复展示陈旧结果。
        stoppedEntryIds = stoppedEntryIds,
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
            // 设置页只是统计页底部的一个去处：顶级页面不变，因此「上次打开」不受影响。
            onOpenSettings = { settingsOpen = true },
        )
    }

    // 设置页的 adapter 在组合根组装：备份与恢复原本在统计页，现在挂在设置页上，
    // 但落地的 SAF 文件选择器、导入预览与恢复确认一个都没变，因此 workflow 与迁移前一致。
    val settingsActions: SettingsActions = remember(viewModel) {
        SettingsActionsAdapter(
            viewModel = viewModel,
            currentWeekStartDay = { currentWeekStartDay },
            onRequestReminderPermission = { requestReminderPermission(context) },
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
            // 重新打开引导：先把设置页让出去，再把全屏引导举起来。
            onOpenOnboarding = {
                settingsOpen = false
                onboardingRequested = true
            },
            // 返回时回到统计页：设置页从统计进入，所以 destination 一直是统计。
            onClose = { settingsOpen = false },
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

    // 日程页的 adapter 在组合根组装：计时服务启动与提醒权限申请都属于这里，页面只发 intent。
    val currentAgendaDate by androidx.compose.runtime.rememberUpdatedState(
        agendaState.selectedDate ?: agendaState.planningDate,
    )
    val currentAgendaPlanningDay by androidx.compose.runtime.rememberUpdatedState(agendaState.planningDate)
    val agendaActions: AgendaActions = remember(viewModel) {
        AgendaActionsAdapter(
            viewModel = viewModel,
            selection = agendaSelection,
            currentDate = { currentAgendaDate },
            currentPlanningDay = { currentAgendaPlanningDay },
            zone = injectedZone,
            onStartTimerFromEntry = ::startTimerFromSchedule,
            onRequestReminderPermission = { requestReminderPermission(context) },
            onStopTimer = {
                viewModel.stopTimer(onSuccess = {
                    AttentionBootReceiver.cancelRecoveryPrompt(context)
                    context.stopService(AttentionTimerService.intent(context))
                })
            },
            onRecoverTimerFromNow = {
                viewModel.recoverTimer(onSuccess = {
                    AttentionBootReceiver.cancelRecoveryPrompt(context)
                })
            },
            onTimeRecordDeleted = { removed ->
                feedbackScope.launch {
                    feedback.showUndoableMessage(
                        message = "已删除这条实际投入，它不再计入目标进度与统计。",
                        onUndo = { viewModel.restoreTimeEntry(removed) },
                    )
                }
            },
        )
    }

    // 引导是独立全屏的：它盖在整个工作区之上，因此顶栏、底部导航与页面内容都不渲染。
    // 自动出现的那一次只由持久化的引导状态决定（没读到设置之前不显示），
    // 从设置页重新打开的那一次由 [onboardingRequested] 决定；两种来源离开后都不再自动出现。
    val onboardingVisible = onboardingRequested || persistedSettings?.onboardingCompleted == false
    val onboardingState = onboardingScreenState()
    val onboardingActions: OnboardingActions = remember(viewModel) {
        OnboardingActionsAdapter(
            viewModel = viewModel,
            onCompleted = {
                // 完成或跳过都落到当前规划日的日程：离开引导、离开设置页，并把日程页的
                // 日期范围交回给当前规划日（页面侧的语义由页面 module 提供）。
                onboardingRequested = false
                settingsOpen = false
                destinationChosen = true
                destination = LaunchDestination.AGENDA
                agendaSelection.followPlanningDay()
            },
        )
    }

    if (onboardingVisible) {
        OnboardingScreen(state = onboardingState, actions = onboardingActions)
    }
    // 引导出现时工作区整体不渲染：顶栏、底部导航与页面内容都让位给这层独立全屏引导。
    if (!onboardingVisible) {
        Scaffold(
        topBar = { TopAppBar(title = { Text("Attention") }) },
        bottomBar = {
            // 主工作区收敛为日程、目标树、统计三个入口：今天/明天/所有日期是日程页里的范围，
            // 设置不再是顶级入口——它从统计页底部的「设置与数据」进入，返回时回到统计页。
            // 每一项同时有 Material 图标与中文标签，选中态带可访问语义。
            Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(
                    Triple(LaunchDestination.AGENDA, "日程", Icons.Filled.Event),
                    Triple(LaunchDestination.TARGETS, "目标树", Icons.Filled.AccountTree),
                    Triple(LaunchDestination.STATISTICS, "统计", Icons.Filled.Insights),
                ).forEach { (item, label, icon) ->
                    WorkspaceNavigationItem(
                        label = label,
                        icon = icon,
                        // 设置页挂在统计页底下，因此停留在设置页时统计仍然处于选中态。
                        selected = destination.asAgendaEntry() == item,
                        onSelect = {
                            // 从设置页切回主工作区：先离开设置，再换顶级页面。
                            settingsOpen = false
                            destinationChosen = true
                            // 用户亲手点的落点一定是真实的：这一次的写回不必再等状态纠正。
                            destinationSettled = true
                            destination = item
                        },
                        modifier = Modifier.testTag("nav-${item.name.lowercase()}"),
                    )
                }
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
                        // 通知是否可用是平台事实，由组合根查好后注入，页面自己不判断。
                        notificationsEnabled = notificationsEnabled(context),
                    ),
                    actions = settingsActions,
                )
            } else when (destination.toTopLevelWorkspace()) {
                // 「日程」是合并后的页面：它承载当前规划日、今天、明天、所有日期与任意规划日。
                // 旧入口值 TODAY / ALL_DATES 通过领域兼容映射落到同一页（枚举值本身保留）。
                LaunchDestination.TARGETS -> TargetsScreen(
                    state = targetsState,
                    actions = targetsActions,
                    selection = targetsSelection,
                )
                LaunchDestination.STATISTICS -> StatisticsScreen(
                    state = statisticsState,
                    actions = statisticsActions,
                )
                // 「日程」与兜底落在同一页：「日程」是合并后的页面，它承载当前规划日、今天、
                // 明天、所有日期与任意规划日；旧入口值 TODAY / ALL_DATES 由领域的兼容映射折进来
                // （枚举值本身保留），LAST_OPENED 这类不是顶级工作区的值由 [toTopLevelWorkspace]
                // 回退到日程。空白工作区对用户没有任何解释，是比落错页更差的失败方式。
                else -> AgendaScreen(
                    state = agendaState,
                    actions = agendaActions,
                    openScheduleId = openScheduleId,
                )
            }
            jsonPreview?.let { Text("JSON 预览：\n$it", modifier = Modifier.padding(16.dp)) }
            jsonExportMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
            csvPreview?.let { Text("CSV 预览：\n${it.take(800)}", modifier = Modifier.padding(16.dp)) }
            scheduleExportMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
            csvBundleMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
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

/**
 * 一个底部导航入口：Material 图标与中文标签同时可见，选中态用填充色之外的
 * 「已选中：…」文案、勾选图标与可访问的 selected 语义表达，因此不依赖颜色单独传达。
 */
@Composable
private fun WorkspaceNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(attentionTokens().radius.small),
        color = Color.Transparent,
        modifier = modifier.selectable(
            selected = selected,
            onClick = onSelect,
            role = Role.Tab,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "已选中", modifier = Modifier.size(16.dp))
            }
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Text(label)
        }
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

/**
 * 「上次打开」写回的事实。五个条件都成立才允许写回。
 *
 * 它们挡的是**同一个**后果：把用户上次真正停留的页面永久改成别的页面。因此用命名事实承载，
 * 而不是五个相邻的布尔——调用点必须逐个说清楚，否则「哪个 true 挡住了写回」无从读起。
 */
internal data class LastOpenedWriteBackFacts(
    /** 持久化设置确实读到了（不是首次组合时那份还没落地的快照）。 */
    val settingsLoaded: Boolean,
    /** 当前是否在设置页；设置页不是顶级工作区，不该被记成上次打开。 */
    val settingsOpen: Boolean,
    /** 领域状态是否真的加载完成；之前 [state] 只是一份空默认值。 */
    val stateLoaded: Boolean,
    /** 启动落点是否已经确定（已被状态纠正，或用户亲手点过）。 */
    val destinationSettled: Boolean,
    /** 这次打开是不是由外部日程请求送达的。 */
    val fromExternalRequest: Boolean,
)

/**
 * 「上次打开」能不能写回去。
 *
 * 前两个条件是边界：[settingsLoaded] 没成立说明设置还没读上来，[settingsOpen] 时用户人在设置页。
 *
 * 后三个条件挡的是同一种写回：[state] 在加载完成之前是一份空的默认值，启动落点 [WorkspaceApp] 里的
 * [destination] 初值又是由那份空默认值解析出来的暂定值，据此写回去会永久覆盖用户上次真正停留的页面。
 * 状态纠正与写回发生在同一次重组里，所以「状态已加载」并不蕴含「落点已纠正」——两者必须分别判断。
 *
 * [LastOpenedWriteBackFacts.fromExternalRequest] 挡的是另一件事：外部日程请求只负责把用户送到指定规划日
 * 与指定条目，用户因此被动看到日程页。若把这次送达记成上次打开，启动入口为「上次打开」的用户会在一次提醒
 * 之后被永久钉在日程页，「上次打开」也就不是上次打开了。外部请求打开之后用户亲手切换工作区不算外部送达，
 * 那一次切换照常记录。
 */
internal fun shouldPersistLastOpened(facts: LastOpenedWriteBackFacts): Boolean =
    facts.settingsLoaded && !facts.settingsOpen && facts.stateLoaded &&
        facts.destinationSettled && !facts.fromExternalRequest

/**
 * 一次返回键按下时它该落在哪里。返回键处理是平台行为，因此决策点留在组合根侧，
 * 并且写成纯函数：每种情形都能在 JVM 测试里钉住，不必依赖设备与窗口顺序。
 */
internal enum class WorkspaceBackTarget {
    /** 关掉当前打开的设置页。 */
    CLOSE_SETTINGS_PAGE,

    /** 没有可关闭的层：把返回键交回系统，应用退出行为与本票之前一致。 */
    SYSTEM,
}

/**
 * 只看「有没有可关闭的层」，不看标签历史——主页面之间的切换不形成逐页返回栈，
 * 因此标签切换多少次都不会让返回键去回放上一个标签页。
 */
internal fun resolveBackTarget(settingsOpen: Boolean): WorkspaceBackTarget =
    if (settingsOpen) WorkspaceBackTarget.CLOSE_SETTINGS_PAGE else WorkspaceBackTarget.SYSTEM
