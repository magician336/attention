package com.attention.app

import androidx.lifecycle.ViewModel
import com.attention.app.data.AttentionStateRepository
import com.attention.app.data.backup.BackupCommitResult
import com.attention.app.data.backup.BackupWorkflow
import com.attention.app.data.backup.ExportBundle
import com.attention.app.data.backup.ExportDocument
import com.attention.app.data.backup.ImportMode
import com.attention.app.data.backup.ImportPlan
import com.attention.domain.AttentionState
import com.attention.domain.AttentionCommand
import com.attention.domain.FutureGoalRule
import com.attention.domain.GoalCadence
import com.attention.domain.LaunchDestination
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleBackfill
import com.attention.domain.ScheduleEntry
import com.attention.domain.StoredSettings
import com.attention.domain.TargetMove
import com.attention.domain.TimeEntry
import com.attention.domain.addGoalStage
import com.attention.domain.appendOneTimeGoalStage
import com.attention.domain.addFutureGoalRule
import com.attention.domain.addMigration
import com.attention.domain.addFutureTargetMove
import com.attention.domain.cancelFutureTargetMove
import com.attention.domain.addRecurrence
import com.attention.domain.addRecurrenceAndMaterialize
import com.attention.domain.addSchedule
import com.attention.domain.addTarget
import com.attention.domain.addTimeEntry
import com.attention.domain.assignUnowned
import com.attention.domain.archiveTarget
import com.attention.domain.restoreTargets
import com.attention.domain.deleteTargetRelations
import com.attention.domain.editTimeEntry
import com.attention.domain.pauseTimer
import com.attention.domain.renameTarget
import com.attention.domain.recoverTimer
import com.attention.domain.resumeTimer
import com.attention.domain.startTimer
import com.attention.domain.stopRecurrence
import com.attention.domain.updateSchedule
import com.attention.domain.toggleScheduleCompleted
import com.attention.domain.updateFutureGoalRule
import com.attention.domain.updateMigration
import com.attention.domain.cancelMigration
import com.attention.domain.cancelFutureGoalRule
import com.attention.domain.Migration
import com.attention.domain.setTargetExpanded
import com.attention.domain.moveTarget
import com.attention.domain.reorderTarget
import com.attention.domain.settlePeriodSnapshots
import com.attention.domain.awardEligibleMilestones
import com.attention.domain.materializeOccurrences
import com.attention.domain.planningDate
import com.attention.domain.updateFutureTargetMove
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Typed errors exposed by backup commands for UI adapters. */
sealed interface BackupViewModelError {
    data class InvalidImport(val cause: Throwable) : BackupViewModelError
    data class StalePlan(val expectedFingerprint: String, val actualFingerprint: String) : BackupViewModelError
    data object NoImportBackup : BackupViewModelError
    data class OperationFailed(val cause: Throwable) : BackupViewModelError
}

class AttentionViewModel(
    private val repository: AttentionStateRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    data class CsvExportBundle(
        val timeEntries: String,
        val schedules: String,
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 重新订阅本地数据的信号。
     *
     * 「重试」因此是一次真正的重新读取，而不是把同一份已经失败的数据再渲染一遍。
     * 它只影响读取这一侧：写命令走 [repository]，不经过这里。
     */
    private val reloadSignals = MutableStateFlow(0)

    private val _loadError = MutableStateFlow<String?>(null)

    /**
     * 本地数据**读取**失败时的说明；null 表示读取正常。
     *
     * 它与 [error] 分开：那条是某一次操作没保存成功（写失败），这条是「还没读上来」。
     * 页面据此渲染带「重试」的读取失败状态，而不是把一个空工作台留给用户。
     */
    val loadError: StateFlow<String?> = _loadError

    private val loadedState: StateFlow<AttentionState> = reloadSignals
        // 每次重试都重新订阅仓储的状态流；读取失败只作为事实记下来，不让它把整条流拆掉。
        .flatMapLatest { repository.state.onStart { _loadError.value = null } }
        .catch { cause -> _loadError.value = cause.message ?: "读取本地数据失败" }
        .stateIn(
            scope,
            SharingStarted.WhileSubscribed(5_000),
            AttentionState(),
        )
    val state: StateFlow<AttentionState> = loadedState

    /**
     * 持久化状态是否真的读上来了。
     *
     * [state] 在读到之前是一份**空的默认值**（[AttentionState]），而不是「用户的数据为空」。
     * 因此组合根必须等这个信号为真，才可以基于 [state] 做启动落点、写回「上次打开」这类
     * 会覆盖用户已保存偏好的决定——否则启动那一帧按默认值算出来的暂定落点会抢先写回去。
     *
     * 它派生自 [state] 本身而不是另开一次读取，因此与 [state] 必然来自同一次加载，不会分叉。
     */
    val stateLoaded: StateFlow<Boolean> = loadedState
        .map { true }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /**
     * 重新读取本地数据。
     *
     * [state] 派生自 `repository.state`，重读就是重新订阅它：先清掉读取失败的事实，
     * 让页面回到加载骨架，读上来之后 [stateLoaded] 与 [state] 自然跟上（#195 验收标准 2）。
     */
    fun retryLoad() {
        _loadError.value = null
        reloadSignals.value += 1
    }
    private val _backupResult = MutableStateFlow<BackupCommitResult?>(null)
    /** Last typed commit/restore result, including stale and no-backup outcomes. */
    val backupResult: StateFlow<BackupCommitResult?> = _backupResult
    private val _backupError = MutableStateFlow<BackupViewModelError?>(null)
    /**
     * 最近一次停止计时产出的记录 id。面板按它从领域状态读回「刚刚那一笔」，
     * 因此它必须来自命令结果，而不是页面临时记下的一段历史。
     */
    private val _stoppedEntryIds = MutableStateFlow<List<String>>(emptyList())
    /** Structured backup errors for callers that do not want to parse [error]. */
    val backupError: StateFlow<BackupViewModelError?> = _backupError

    private val backupWorkflow: BackupWorkflow
        get() = repository.backupWorkflow

    fun clearError() { _error.value = null }

    fun addTarget(title: String, parentId: String? = null, onSuccess: () -> Unit = {}) =
        runCommand(onSuccess = onSuccess) { it.addTarget(title, parentId) }
    fun renameTarget(id: String, title: String) = runCommand { it.renameTarget(id, title) }
    fun moveTarget(id: String, parentId: String?) = runCommand { it.moveTarget(id, parentId) }
    fun moveTargetFrom(id: String, parentId: String?, effectiveFrom: String) = runCommand {
        it.addFutureTargetMove(id, parentId, effectiveFrom, it.planningDate(Instant.now(), zone))
    }
    fun updateFutureTargetMove(move: TargetMove) = runCommand {
        it.updateFutureTargetMove(move, it.planningDate(Instant.now(), zone))
    }
    fun cancelFutureTargetMove(moveId: String) = runCommand {
        it.cancelFutureTargetMove(moveId, it.planningDate(Instant.now(), zone))
    }
    fun reorderTarget(id: String, newIndex: Int) = runCommand { it.reorderTarget(id, newIndex) }
    fun toggleTarget(id: String, expanded: Boolean) = runCommand { it.setTargetExpanded(id, expanded) }
    fun archiveTarget(id: String) = runCommand { it.archiveTarget(id) }
    fun restoreTarget(id: String) = runCommand { it.archiveTarget(id, archived = false) }
    fun restoreTargets(ids: Set<String>) = runCommand { it.restoreTargets(ids) }
    fun deleteTargetRelations(ids: Set<String>) = runCommand { it.deleteTargetRelations(ids) }

    fun addTime(date: String, minutes: Int, targetId: String?, note: String = "") =
        runTypedCommand(AttentionCommand.AddManualTimeEntry(date, minutes, targetId, note))

    fun editTime(id: String, minutes: Int, targetId: String?, note: String = "") =
        runTypedCommand(AttentionCommand.EditTimeEntry(id, minutes, targetId, note))

    fun deleteTime(id: String) = runTypedCommand(AttentionCommand.DeleteTimeEntry(id))

    /**
     * 删除一条实际投入，并把被删掉的原始记录交还给调用方。
     *
     * 交还的是原来的记录本身，而不是重新补记一笔：撤销必须把 id、来源、关联日程与
     * 精确时间戳原样还回去，否则“撤销”会悄悄改写事实。只有删除真的成功了才会回调，
     * 保存失败时 [onRemoved] 不会被调用。
     */
    fun deleteTimeWithUndo(id: String, onRemoved: (TimeEntry) -> Unit) {
        runTypedCommand(
            AttentionCommand.DeleteTimeEntry(id),
            onSuccess = { result -> result.removedTimeEntries.singleOrNull()?.let(onRemoved) },
        )
    }

    /**
     * 把一条被删除的实际投入原样放回去。撤销的真实恢复动作。
     *
     * 经验值随时间记录重新推导，因此恢复之后累计投入与目标汇总回到删除前的样子；
     * 已经存在的同 id 记录不会被重复写入。
     */
    fun restoreTimeEntry(entry: TimeEntry) = runTypedCommand(AttentionCommand.RestoreTimeEntry(entry))

    fun addGoal(targetId: String, cadence: GoalCadence, minutes: Int, startDate: String, dueDate: String? = null) =
        runCommand { it.addGoalStage(targetId, cadence, minutes, startDate, dueDate) }
    fun addFutureGoal(rule: FutureGoalRule, notBefore: LocalDate) =
        runCommand { it.addFutureGoalRule(rule, notBefore) }
    fun updateFutureGoal(rule: FutureGoalRule, notBefore: LocalDate) =
        runCommand { it.updateFutureGoalRule(rule, notBefore) }
    fun cancelFutureGoal(ruleId: String, notBefore: LocalDate) =
        runCommand { it.cancelFutureGoalRule(ruleId, notBefore) }
    fun appendOneTimeGoal(
        targetId: String,
        minutes: Int,
        startDate: String,
        dueDate: String? = null,
        onSuccess: () -> Unit = {},
    ) = runCommand(onSuccess = onSuccess) {
        it.appendOneTimeGoalStage(targetId, minutes, startDate, dueDate)
    }
    fun addMigration(migration: Migration, onDate: LocalDate) = runCommand { it.addMigration(migration, onDate) }
    fun updateMigration(migration: Migration, onDate: LocalDate) = runCommand { it.updateMigration(migration, onDate) }
    fun cancelMigration(migrationId: String) = runCommand { it.cancelMigration(migrationId) }
    fun assignUnowned(entryIds: Set<String>, targetId: String) =
        runTypedCommand(AttentionCommand.AssignUnowned(entryIds, targetId))

    fun addSchedule(entry: ScheduleEntry) = runCommand { it.addSchedule(entry) }
    fun updateSchedule(entry: ScheduleEntry) = runCommand { it.updateSchedule(entry) }

    /**
     * 保存一条日程条目，并可选地顺带补记一笔已经发生的时间。
     *
     * 日程条目与那笔事后补记由同一个 typed command 提交，因此要么都保存、要么都不保存；
     * 补记的来源、关联、投入经验与达成里程碑仍由 `:domain` 计算，ViewModel 只提交命令。
     */
    fun saveSchedule(entry: ScheduleEntry, backfill: ScheduleBackfill? = null) =
        runTypedCommand(AttentionCommand.SaveSchedule(entry, backfill))
    fun toggleScheduleCompleted(entryId: String) {
        scope.launch(Dispatchers.IO) {
            runCatching { repository.toggleScheduleCompleted(entryId) }
                .onFailure { _error.value = it.message ?: "操作未保存" }
        }
    }

    /**
     * 把用户「标记为完成」的决定落到领域命令上。
     *
     * 走 [AttentionCommand.MarkScheduleCompleted] 而不是结算命令：结算命令只回答
     * 「投入是否达到计划」，不足计划时它不动状态，于是用户明明说了「做完了」却看不到变化。
     * 这里由用户的决定驱动，幂等，且不新建第二笔投入——那一笔在停止计时时已经写过。
     */
    fun markStoppedScheduleCompleted(entryId: String) =
        runTypedCommand(
            AttentionCommand.MarkScheduleCompleted(entryId),
            errorMessage = "无法标记完成",
        )
    fun addRecurrence(rule: RecurrenceRule) = runCommand { it.addRecurrence(rule) }
    fun addRecurrenceAndMaterialize(rule: RecurrenceRule, through: LocalDate) = runCommand {
        it.addRecurrenceAndMaterialize(rule, through, zone)
    }
    fun materializeRecurrence(rule: RecurrenceRule, through: LocalDate) = runCommand { it.materializeOccurrences(rule, through, zone) }
    fun stopRecurrence(ruleId: String) = runCommand { it.stopRecurrence(ruleId) }

    fun setSettings(settings: StoredSettings) = runSettingsCommand { repository.setPlannerSettings(settings) }
    fun setWeekStart(day: Int) = runSettingsCommand {
        require(day in 1..7) { "周起始日必须在周一到周日之间" }
        repository.setWeekStartDay(day)
    }
    fun setLaunchDestination(destination: LaunchDestination) = runSettingsCommand {
        repository.setLaunchDestination(destination)
    }
    fun finishOnboarding() = runSettingsCommand { repository.setOnboardingCompleted(true) }
    fun saveLastOpened(destination: LaunchDestination) = runSettingsCommand {
        repository.setLastOpenedDestination(destination)
    }

    fun startTimer(
        targetId: String?,
        scheduleEntryId: String? = null,
        onSuccess: () -> Unit = {},
    ) = runCommand(onSuccess = onSuccess) {
        it.startTimer(targetId, Instant.now(), zone, scheduleEntryId)
    }
    fun confirmScheduleCompleted(entryId: String, durationMinutes: Int? = null) =
        runTypedCommand(AttentionCommand.ConfirmSchedule(entryId, durationMinutes))
    fun switchTimer(targetId: String?, onSuccess: () -> Unit = {}) =
        runTypedCommand(
            AttentionCommand.SwitchTimer(targetId),
            onSuccess = { onSuccess() },
            errorMessage = "无法切换计时目标",
        )
    fun pauseTimer(onSuccess: () -> Unit = {}) = runCommand(onSuccess = onSuccess) { it.pauseTimer(Instant.now()) }
    fun resumeTimer(onSuccess: () -> Unit = {}) = runCommand(onSuccess = onSuccess) { it.resumeTimer(Instant.now()) }
    fun recoverTimer(onSuccess: () -> Unit = {}) = runCommand(onSuccess = onSuccess) { it.recoverTimer(Instant.now()) }
    fun stopTimer(onSuccess: () -> Unit = {}) =
        runTypedCommand(
            AttentionCommand.StopTimer,
            onSuccess = { result ->
                // 结果面板只认这一次停止产出的记录 id：面板按 id 从状态读回事实，
                // 而不是页面自己缓存一段历史。新的停止会覆盖上一次的结果。
                _stoppedEntryIds.value = result.generatedTimeEntries.map { it.id }
                onSuccess()
            },
            errorMessage = "无法结束计时",
        )

    /**
     * 最近一次停止计时产出的记录 id；空列表表示此刻没有待核对的那一笔。
     *
     * 它的生命周期只有三段：停止成功时写入、用户消费结果时清空、下一次停止时覆盖。
     * 它不写入任何持久化，因此重新启动后不会有一块陈旧的结果面板等着被误读。
     */
    val stoppedEntryIds: StateFlow<List<String>> = _stoppedEntryIds

    /** 用户已经看过结果面板：清空 id，面板随之消失。 */
    fun clearStoppedEntryIds() {
        _stoppedEntryIds.value = emptyList()
    }

    /** Internal repair path for reconciling imported or legacy data. */
    internal fun awardMilestones() = runCommand { it.awardEligibleMilestones(java.time.LocalDate.now(zone)) }
    fun settlePeriodSnapshots() = runCommand { it.settlePeriodSnapshots(it.planningDate(Instant.now(), zone)) }

    /** Returns the typed JSON document produced by the backup workflow. */
    fun exportJsonDocument(onResult: (ExportDocument) -> Unit) = scope.launch {
        runCatching { backupWorkflow.exportJson() }
            .onSuccess(onResult)
            .onFailure { publishBackupFailure(it, "导出失败") }
    }

    /** Compatibility callback for the pre-workflow Workspace API. */
    fun exportJson(onResult: (String) -> Unit) = scope.launch {
        runCatching { backupWorkflow.exportJson().content }
            .onSuccess(onResult)
            .onFailure { publishBackupFailure(it, "导出失败") }
    }

    fun exportCsv(onResult: (timeEntries: String, schedules: String) -> Unit) = scope.launch {
        runCatching { backupWorkflow.exportCsvBundle() }
            .onSuccess { bundle -> onResult(bundle.timeEntries.content, bundle.schedules.content) }
            .onFailure { publishBackupFailure(it, "CSV 导出失败") }
    }

    /** Returns the analysis-only time-entry CSV from one consistent state snapshot. */
    fun exportTimeEntriesCsvDocument(onResult: (ExportDocument) -> Unit) = scope.launch {
        runCatching { backupWorkflow.exportTimeEntriesCsv() }
            .onSuccess(onResult)
            .onFailure { publishBackupFailure(it, "时间记录 CSV 导出失败") }
    }

    fun exportTimeEntriesCsv(onResult: (String) -> Unit) = scope.launch {
        runCatching { backupWorkflow.exportTimeEntriesCsv().content }
            .onSuccess(onResult)
            .onFailure { publishBackupFailure(it, "时间记录 CSV 导出失败") }
    }

    fun exportSchedulesCsvDocument(onResult: (ExportDocument) -> Unit) = scope.launch {
        runCatching { backupWorkflow.exportSchedulesCsv() }
            .onSuccess(onResult)
            .onFailure { publishBackupFailure(it, "日程 CSV 导出失败") }
    }

    fun exportSchedulesCsv(onResult: (String) -> Unit) = scope.launch {
        runCatching { backupWorkflow.exportSchedulesCsv().content }
            .onSuccess(onResult)
            .onFailure { publishBackupFailure(it, "日程 CSV 导出失败") }
    }

    /** Returns both CSV documents from the workflow's one immutable snapshot. */
    fun exportCsvBundleDocument(onResult: (ExportBundle) -> Unit) = scope.launch {
        runCatching { backupWorkflow.exportCsvBundle() }
            .onSuccess(onResult)
            .onFailure { publishBackupFailure(it, "CSV 导出失败") }
    }

    fun exportCsvBundle(onResult: (CsvExportBundle) -> Unit) = scope.launch {
        runCatching {
            backupWorkflow.exportCsvBundle().let { bundle ->
                CsvExportBundle(bundle.timeEntries.content, bundle.schedules.content)
            }
        }.onSuccess(onResult)
            .onFailure { publishBackupFailure(it, "CSV 导出失败") }
    }

    /** Builds an immutable import plan without changing persisted state. */
    fun prepareImport(
        encoded: String,
        clearExisting: Boolean,
        onResult: (ImportPlan) -> Unit,
        onFailure: (Throwable) -> Unit = {},
    ) = scope.launch {
        runCatching {
            backupWorkflow.prepareImport(
                encoded,
                if (clearExisting) ImportMode.REPLACE else ImportMode.MERGE,
            )
        }.onSuccess(onResult)
            .onFailure {
                _backupError.value = BackupViewModelError.InvalidImport(it)
                onFailure(it)
                _error.value = it.message ?: "导入失败，现有数据未改变"
            }
    }

    /** Commits exactly the plan returned by [prepareImport]. */
    fun commitImport(
        plan: ImportPlan,
        onResult: (BackupCommitResult) -> Unit = {},
        onComplete: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
    ) = scope.launch {
        runCatching { backupWorkflow.commit(plan) }
            .onSuccess { result ->
                publishBackupResult(result)
                onResult(result)
                if (result is BackupCommitResult.Committed) onComplete()
            }
            .onFailure {
                _backupError.value = BackupViewModelError.OperationFailed(it)
                onFailure(it)
                _error.value = it.message ?: "导入失败，现有数据未改变"
            }
    }

    fun importJson(encoded: String, clearExisting: Boolean, onComplete: () -> Unit = {}) = scope.launch {
        val plan = runCatching {
            backupWorkflow.prepareImport(
                encoded,
                if (clearExisting) ImportMode.REPLACE else ImportMode.MERGE,
            )
        }.getOrElse {
            _backupError.value = BackupViewModelError.InvalidImport(it)
            _error.value = it.message ?: "导入失败，现有数据未改变"
            return@launch
        }
        runCatching {
            backupWorkflow.commit(plan)
        }.onSuccess { result ->
            publishBackupResult(result)
            if (result is BackupCommitResult.Committed) onComplete()
        }.onFailure {
            _backupError.value = BackupViewModelError.OperationFailed(it)
            _error.value = it.message ?: "导入失败，现有数据未改变"
        }
    }

    /** Restores the snapshot captured immediately before the most recent import. */
    fun restoreLastImportBackup(onComplete: () -> Unit = {}) = scope.launch {
        runCatching { backupWorkflow.restoreLatestImportBackup() }
            .onSuccess { result ->
                publishBackupResult(result)
                if (result is BackupCommitResult.Committed) onComplete()
            }
            .onFailure {
                _backupError.value = BackupViewModelError.OperationFailed(it)
                _error.value = it.message ?: "导入前备份恢复失败，现有数据未改变"
            }
    }

    /** Typed restore command for pages that need to distinguish no-backup/stale outcomes. */
    fun restoreLastImportBackupResult(
        onResult: (BackupCommitResult) -> Unit,
        onFailure: (Throwable) -> Unit = {},
    ) = scope.launch {
        runCatching { backupWorkflow.restoreLatestImportBackup() }
            .onSuccess { result ->
                publishBackupResult(result)
                onResult(result)
            }
            .onFailure {
                _backupError.value = BackupViewModelError.OperationFailed(it)
                onFailure(it)
                _error.value = it.message ?: "导入前备份恢复失败，现有数据未改变"
            }
    }

    fun restoreReminders() = scope.launch {
        runCatching { repository.restoreReminders() }
            .onFailure { _error.value = it.message ?: "提醒恢复失败" }
    }

    private fun runCommand(
        onSuccess: () -> Unit = {},
        transform: (AttentionState) -> AttentionState,
    ) = scope.launch {
        runCatching { repository.updateBusiness(transform) }
            .onSuccess { onSuccess() }
            .onFailure { _error.value = it.message ?: "操作未保存" }
    }

    private fun runTypedCommand(
        command: AttentionCommand,
        now: Instant = Instant.now(),
        onSuccess: (com.attention.domain.AttentionCommandResult) -> Unit = {},
        errorMessage: String = "操作未保存",
    ) = scope.launch {
        runCatching { repository.executeCommand(command, now, zone) }
            .onSuccess(onSuccess)
            .onFailure { _error.value = it.message ?: errorMessage }
    }

    private fun runSettingsCommand(command: suspend () -> Unit) = scope.launch {
        runCatching { command() }
            .onFailure { _error.value = it.message ?: "设置未保存" }
    }

    private fun publishBackupResult(result: BackupCommitResult) {
        _backupResult.value = result
        _backupError.value = when (result) {
            is BackupCommitResult.StalePlan -> BackupViewModelError.StalePlan(
                result.expectedFingerprint,
                result.actualFingerprint,
            )
            BackupCommitResult.NoImportBackup -> BackupViewModelError.NoImportBackup
            is BackupCommitResult.Committed -> null
        }
        when (result) {
            is BackupCommitResult.StalePlan -> _error.value = "导入预览已过期，请重新预览"
            BackupCommitResult.NoImportBackup -> _error.value = "没有可恢复的导入前备份"
            is BackupCommitResult.Committed -> Unit
        }
    }

    private fun publishBackupFailure(error: Throwable, fallback: String) {
        _backupError.value = BackupViewModelError.OperationFailed(error)
        _error.value = error.message ?: fallback
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
