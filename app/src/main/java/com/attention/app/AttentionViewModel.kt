package com.attention.app

import androidx.lifecycle.ViewModel
import com.attention.app.data.AttentionStateRepository
import com.attention.domain.AttentionState
import com.attention.domain.FutureGoalRule
import com.attention.domain.GoalCadence
import com.attention.domain.LaunchDestination
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.StoredSettings
import com.attention.domain.TimeEntrySource
import com.attention.domain.addGoalStage
import com.attention.domain.addFutureGoalRule
import com.attention.domain.addMigration
import com.attention.domain.addFutureTargetMove
import com.attention.domain.addRecurrence
import com.attention.domain.addSchedule
import com.attention.domain.addTarget
import com.attention.domain.addTimeEntry
import com.attention.domain.assignUnowned
import com.attention.domain.archiveTarget
import com.attention.domain.deleteTargetRelations
import com.attention.domain.deleteTimeEntry
import com.attention.domain.editTimeEntry
import com.attention.domain.pauseTimer
import com.attention.domain.renameTarget
import com.attention.domain.recalculateExperience
import com.attention.domain.recoverTimer
import com.attention.domain.resumeTimer
import com.attention.domain.startTimer
import com.attention.domain.stopTimer
import com.attention.domain.stopRecurrence
import com.attention.domain.updateSchedule
import com.attention.domain.updateFutureGoalRule
import com.attention.domain.updateMigration
import com.attention.domain.cancelMigration
import com.attention.domain.cancelFutureGoalRule
import com.attention.domain.Migration
import com.attention.domain.awardEligibleMilestones
import com.attention.domain.switchTimer
import com.attention.domain.schedulesCsv
import com.attention.domain.timeEntriesCsv
import com.attention.domain.setTargetExpanded
import com.attention.domain.moveTarget
import com.attention.domain.reorderTarget
import com.attention.domain.materializeOccurrences
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AttentionViewModel(
    private val repository: AttentionStateRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val state: StateFlow<AttentionState> = repository.state.stateIn(
        scope,
        SharingStarted.WhileSubscribed(5_000),
        AttentionState(),
    )
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun clearError() { _error.value = null }

    fun addTarget(title: String, parentId: String? = null) = runCommand { it.addTarget(title, parentId) }
    fun renameTarget(id: String, title: String) = runCommand { it.renameTarget(id, title) }
    fun moveTarget(id: String, parentId: String?) = runCommand { it.moveTarget(id, parentId) }
    fun moveTargetFrom(id: String, parentId: String?, effectiveFrom: String) = runCommand { it.addFutureTargetMove(id, parentId, effectiveFrom) }
    fun reorderTarget(id: String, newIndex: Int) = runCommand { it.reorderTarget(id, newIndex) }
    fun toggleTarget(id: String, expanded: Boolean) = runCommand { it.setTargetExpanded(id, expanded) }
    fun archiveTarget(id: String) = runCommand { it.archiveTarget(id) }
    fun restoreTarget(id: String) = runCommand { it.archiveTarget(id, archived = false) }
    fun deleteTargetRelations(ids: Set<String>) = runCommand { it.deleteTargetRelations(ids) }

    fun addTime(date: String, minutes: Int, targetId: String?, note: String = "") =
        runCommand { it.addTimeEntry(date, minutes, targetId, TimeEntrySource.MANUAL, note = note).recalculateExperience().awardEligibleMilestones(LocalDate.parse(date)) }

    fun editTime(id: String, minutes: Int, targetId: String?, note: String = "") =
        runCommand { it.editTimeEntry(id, minutes, targetId, note).recalculateExperience() }

    fun deleteTime(id: String) = runCommand { it.deleteTimeEntry(id).recalculateExperience() }

    fun addGoal(targetId: String, cadence: GoalCadence, minutes: Int, startDate: String, dueDate: String? = null) =
        runCommand { it.addGoalStage(targetId, cadence, minutes, startDate, dueDate) }
    fun addFutureGoal(rule: FutureGoalRule) = runCommand { it.addFutureGoalRule(rule) }
    fun updateFutureGoal(rule: FutureGoalRule) = runCommand { it.updateFutureGoalRule(rule) }
    fun cancelFutureGoal(ruleId: String) = runCommand { it.cancelFutureGoalRule(ruleId) }
    fun addMigration(migration: Migration) = runCommand { it.addMigration(migration) }
    fun updateMigration(migration: Migration) = runCommand { it.updateMigration(migration) }
    fun cancelMigration(migrationId: String) = runCommand { it.cancelMigration(migrationId) }
    fun assignUnowned(entryIds: Set<String>, targetId: String) = runCommand { it.assignUnowned(entryIds, targetId) }

    fun addSchedule(entry: ScheduleEntry) = runCommand { it.addSchedule(entry) }
    fun updateSchedule(entry: ScheduleEntry) = runCommand { it.updateSchedule(entry) }
    fun addRecurrence(rule: RecurrenceRule) = runCommand { it.addRecurrence(rule) }
    fun materializeRecurrence(rule: RecurrenceRule, through: LocalDate) = runCommand { it.materializeOccurrences(rule, through) }
    fun stopRecurrence(ruleId: String) = runCommand { it.stopRecurrence(ruleId) }

    fun setSettings(settings: StoredSettings) = runCommand { it.copy(settings = settings) }
    fun setLaunchDestination(destination: LaunchDestination) = runCommand {
        it.copy(launchDestination = destination)
    }
    fun finishOnboarding() = runCommand { it.copy(onboardingCompleted = true) }
    fun saveLastOpened(destination: LaunchDestination) = runCommand {
        it.copy(lastOpenedDestination = destination)
    }

    fun startTimer(targetId: String?) = runCommand { it.startTimer(targetId, Instant.now(), zone) }
    fun switchTimer(targetId: String?) = scope.launch {
        runCatching {
            repository.update { current -> current.switchTimer(targetId, Instant.now(), zone).state.recalculateExperience() }
        }.onFailure { _error.value = it.message ?: "无法切换计时目标" }
    }
    fun pauseTimer() = runCommand { it.pauseTimer(Instant.now()) }
    fun resumeTimer() = runCommand { it.resumeTimer(Instant.now()) }
    fun recoverTimer() = runCommand { it.recoverTimer(Instant.now()) }
    fun stopTimer() = scope.launch {
        runCatching {
            repository.update { current -> current.stopTimer(Instant.now(), zone).state.recalculateExperience().awardEligibleMilestones(LocalDate.now(zone)) }
        }.onFailure { _error.value = it.message ?: "无法结束计时" }
    }

    fun awardMilestones() = runCommand { it.awardEligibleMilestones(java.time.LocalDate.now(zone)) }

    fun exportJson(onResult: (String) -> Unit) = scope.launch {
        runCatching { repository.exportJson() }
            .onSuccess(onResult)
            .onFailure { _error.value = it.message ?: "导出失败" }
    }

    fun exportCsv(onResult: (timeEntries: String, schedules: String) -> Unit) = scope.launch {
        runCatching {
            val current = repository.state.first()
            current.timeEntriesCsv() to current.schedulesCsv()
        }.onSuccess { (timeEntries, schedules) -> onResult(timeEntries, schedules) }
            .onFailure { _error.value = it.message ?: "CSV 导出失败" }
    }

    fun importJson(encoded: String, clearExisting: Boolean, onComplete: () -> Unit = {}) = scope.launch {
        runCatching { repository.importJson(encoded, clearExisting) }
            .onSuccess { onComplete() }
            .onFailure { _error.value = it.message ?: "导入失败，现有数据未改变" }
    }

    private fun runCommand(transform: (AttentionState) -> AttentionState) = scope.launch {
        runCatching { repository.update(transform) }
            .onFailure { _error.value = it.message ?: "操作未保存" }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
