package com.attention.app

import com.attention.app.data.backup.BackupCommitResult
import com.attention.app.data.backup.ExportBundle
import com.attention.app.data.backup.ExportDocument
import com.attention.app.data.backup.ImportPlan
import com.attention.app.data.backup.ImportMode
import com.attention.domain.FutureGoalRule
import com.attention.domain.GoalCadence
import com.attention.domain.LaunchDestination
import com.attention.domain.Migration
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleBackfill
import com.attention.domain.ScheduleEntry
import com.attention.domain.StoredSettings
import com.attention.domain.TargetMove
import com.attention.domain.TimeEntry
import java.time.LocalDate
import kotlinx.coroutines.Job

/** Actions that change actual investment and the single active timer. */
interface InvestmentActions {
    fun editTime(entryId: String, minutes: Int, targetId: String?, note: String = ""): Job
    fun deleteTimeWithUndo(entryId: String, onRemoved: (TimeEntry) -> Unit): Unit
    fun restoreTimeEntry(entry: TimeEntry): Job
    fun startTimer(targetId: String?, scheduleEntryId: String? = null, onSuccess: () -> Unit = {}): Job
    fun stopTimer(onSuccess: () -> Unit = {}): Job
    fun pauseTimer(onSuccess: () -> Unit = {}): Job
    fun resumeTimer(onSuccess: () -> Unit = {}): Job
    fun recoverTimer(onSuccess: () -> Unit = {}): Job
    fun switchTimer(targetId: String?, onSuccess: () -> Unit = {}): Job
}

/** Planning settings used by the settings page and the onboarding flow. */
interface SettingsPlanningActions {
    fun setLaunchDestination(destination: LaunchDestination): Job
    fun setSettings(settings: StoredSettings): Job
    fun setWeekStart(day: Int): Job
}

/** Shared target creation intent used by onboarding and the target tree. */
interface TargetCreationActions {
    fun addTarget(title: String, parentId: String? = null, onSuccess: () -> Unit = {}): Job
}

/** Shared schedule write intent used by onboarding and the agenda page. */
interface ScheduleWriteActions {
    fun saveSchedule(entry: ScheduleEntry, backfill: ScheduleBackfill? = null): Job
}

/** Planning writes used by the first-run onboarding flow. */
interface OnboardingPlanningActions : SettingsPlanningActions, TargetCreationActions, ScheduleWriteActions {
    fun finishOnboarding(): Job
}

/** Target tree, goal, and migration writes used by the targets page. */
interface TargetPlanningActions : TargetCreationActions {
    fun renameTarget(id: String, title: String): Job
    fun moveTarget(id: String, parentId: String?): Job
    fun moveTargetFrom(id: String, parentId: String?, effectiveFrom: String): Job
    fun updateFutureTargetMove(move: TargetMove): Job
    fun cancelFutureTargetMove(moveId: String): Job
    fun reorderTarget(id: String, newIndex: Int): Job
    fun toggleTarget(id: String, expanded: Boolean): Job
    fun archiveTarget(id: String): Job
    fun restoreTargets(ids: Set<String>): Job
    fun deleteTargetRelations(ids: Set<String>): Job
    fun addTime(date: String, minutes: Int, targetId: String?, note: String = ""): Job
    fun addGoal(targetId: String, cadence: GoalCadence, minutes: Int, startDate: String, dueDate: String? = null): Job
    fun addFutureGoal(rule: FutureGoalRule, notBefore: LocalDate): Job
    fun updateFutureGoal(rule: FutureGoalRule, notBefore: LocalDate): Job
    fun cancelFutureGoal(ruleId: String, notBefore: LocalDate): Job
    fun appendOneTimeGoal(
        targetId: String,
        minutes: Int,
        startDate: String,
        dueDate: String? = null,
        onSuccess: () -> Unit = {},
    ): Job
    fun addMigration(migration: Migration, onDate: LocalDate): Job
    fun updateMigration(migration: Migration, onDate: LocalDate): Job
    fun cancelMigration(migrationId: String): Job
    fun assignUnowned(entryIds: Set<String>, targetId: String): Job
}

/** Schedule and recurring schedule writes used by the agenda page. */
interface AgendaPlanningActions : ScheduleWriteActions {
    fun addSchedule(entry: ScheduleEntry): Job
    override fun saveSchedule(entry: ScheduleEntry, backfill: ScheduleBackfill?): Job
    fun confirmScheduleCompleted(entryId: String, durationMinutes: Int? = null): Job
    fun toggleScheduleCompleted(entryId: String): Unit
    fun markStoppedScheduleCompleted(entryId: String): Job
    fun clearStoppedEntryIds(): Unit
    fun addRecurrenceAndMaterialize(rule: RecurrenceRule, through: LocalDate): Job
    fun stopRecurrence(ruleId: String): Job
    fun retryLoad(): Unit
}

/** Read-model maintenance actions initiated by the statistics page. */
interface StatisticsPlanningActions {
    fun settlePeriodSnapshots(): Job
    fun cancelMigration(migrationId: String): Job
    fun awardMilestones(): Job
}

/**
 * Full planning capability retained for the composition root. Page adapters
 * consume the smaller capability slices above so a page cannot see unrelated
 * planning writes.
 */
interface PlanningActions :
    SettingsPlanningActions,
    OnboardingPlanningActions,
    TargetPlanningActions,
    AgendaPlanningActions,
    StatisticsPlanningActions

/** Actions that cross the versioned backup workflow boundary. */
interface BackupActions {
    fun exportJsonDocument(onResult: (ExportDocument) -> Unit): Job
    fun exportTimeEntriesCsvDocument(onResult: (ExportDocument) -> Unit): Job
    fun exportSchedulesCsvDocument(onResult: (ExportDocument) -> Unit): Job
    fun exportCsvBundleDocument(onResult: (ExportBundle) -> Unit): Job
    fun prepareImport(
        encoded: String,
        clearExisting: Boolean,
        onResult: (ImportPlan) -> Unit,
        onFailure: (Throwable) -> Unit = {},
    ): Job
    fun commitImport(
        plan: ImportPlan,
        onResult: (BackupCommitResult) -> Unit = {},
        onComplete: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
    ): Job
    fun restoreLastImportBackupResult(
        onResult: (BackupCommitResult) -> Unit,
        onFailure: (Throwable) -> Unit = {},
    ): Job
}
