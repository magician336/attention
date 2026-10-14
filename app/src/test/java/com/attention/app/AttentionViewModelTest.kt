package com.attention.app

import com.attention.app.data.AttentionStateRepository
import com.attention.app.data.backup.AttentionBackupCodec
import com.attention.app.data.backup.BackupCommitResult
import com.attention.app.data.backup.BackupStateSubmissionPort
import com.attention.app.data.backup.BackupWorkflow
import com.attention.app.data.backup.ExportBundle
import com.attention.app.data.backup.ImportBackupStore
import com.attention.app.data.backup.ImportMode
import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.AttentionCommand
import com.attention.domain.AttentionCommandResult
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.LaunchDestination
import com.attention.domain.StoredSettings
import com.attention.domain.ScheduleEntry
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import com.attention.domain.planningDate
import com.attention.domain.toggleScheduleCompleted
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttentionViewModelTest {
    @Test
    fun manual_time_entry_is_submitted_as_typed_command() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeRepository(AttentionState())
            AttentionViewModel(repository, ZoneOffset.UTC).addTime("2026-10-15", 25, null, "专注")
            advanceUntilIdle()
            assertEquals(
                AttentionCommand.AddManualTimeEntry("2026-10-15", 25, null, "专注"),
                repository.executedCommands.single(),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun schedule_confirmation_is_submitted_as_typed_command() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val schedule = ScheduleEntry(
                id = "view-model-schedule",
                planningDate = "2026-10-15",
                title = "确认日程",
                estimatedMinutes = 30,
            )
            val repository = FakeRepository(AttentionState(schedules = listOf(schedule)))
            AttentionViewModel(repository, ZoneOffset.UTC).confirmScheduleCompleted(schedule.id, 25)
            advanceUntilIdle()
            assertEquals(
                AttentionCommand.ConfirmSchedule(schedule.id, 25),
                repository.executedCommands.single(),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun exporting_schedules_csv_returns_current_schedule_snapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val schedule = ScheduleEntry(
                id = "export-schedule",
                planningDate = "2026-10-12",
                title = "复习,章节",
                estimatedMinutes = 45,
                note = "分析备注",
            )
            val viewModel = AttentionViewModel(FakeRepository(AttentionState(schedules = listOf(schedule))), ZoneOffset.UTC)
            var exported: String? = null

            viewModel.exportSchedulesCsv { exported = it }
            advanceUntilIdle()

            assertTrue(exported?.contains("planning_date,title,completed") == true)
            assertTrue(exported?.contains("\"复习,章节\"") == true)
            assertTrue(exported?.contains("45") == true)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun editing_a_historical_entry_does_not_award_a_future_stage_milestone() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val date = LocalDate.of(2026, 10, 8)
            val target = Target("view-model-target", title = "阶段目标")
            val repository = FakeRepository(
                AttentionState(
                    targets = listOf(target),
                    goalStages = listOf(GoalStage("view-model-stage", target.id, GoalCadence.ONE_TIME, 30, date.toString())),
                    timeEntries = listOf(
                        TimeEntry("historical-entry", date.toString(), 10),
                        TimeEntry("later-entry", date.plusDays(1).toString(), 20, target.id),
                    ),
                ),
            )
            val viewModel = AttentionViewModel(repository, ZoneOffset.UTC)

            viewModel.editTime("historical-entry", 10, target.id)
            advanceUntilIdle()

            assertEquals(0, repository.state.value.milestones.count { it.kind == "one_time_goal" })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun editing_historical_entry_awards_stage_feedback_on_the_entry_date() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val date = LocalDate.of(2026, 10, 8)
            val target = Target("view-model-same-day-target", title = "阶段目标")
            val repository = FakeRepository(
                AttentionState(
                    targets = listOf(target),
                    goalStages = listOf(GoalStage("view-model-same-day-stage", target.id, GoalCadence.ONE_TIME, 30, date.toString())),
                    timeEntries = listOf(
                        TimeEntry("historical-entry", date.toString(), 10),
                        TimeEntry("same-day-entry", date.toString(), 20, target.id),
                    ),
                ),
            )
            val viewModel = AttentionViewModel(repository, ZoneOffset.UTC)

            viewModel.editTime("historical-entry", 10, target.id)
            advanceUntilIdle()

            assertEquals(1, repository.state.value.milestones.count { it.kind == "one_time_goal" })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun switching_timer_awards_feedback_for_the_finished_segment() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val date = AttentionState().planningDate(Instant.now(), ZoneOffset.UTC)
            val target = Target("switch-target", title = "阶段目标")
            val repository = FakeRepository(
                AttentionState(
                    targets = listOf(target),
                    goalStages = listOf(GoalStage("switch-stage", target.id, GoalCadence.ONE_TIME, 1, date.toString())),
                    activeTimer = ActiveTimer(
                        targetId = target.id,
                        startedAtEpochMillis = Instant.now().minusSeconds(120).toEpochMilli(),
                        lastPlanningDate = date.toString(),
                    ),
                ),
            )
            val viewModel = AttentionViewModel(repository, ZoneOffset.UTC)

            viewModel.switchTimer(null)
            advanceUntilIdle()

            assertEquals(1, repository.state.value.milestones.count { it.kind == "one_time_goal" })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun schedule_edit_persists_all_fields_and_reports_invalid_input() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val original = ScheduleEntry(id = "schedule-1", planningDate = "2026-10-08", title = "阅读", estimatedMinutes = 30)
            val repository = FakeRepository(AttentionState(schedules = listOf(original)))
            val viewModel = AttentionViewModel(repository, ZoneOffset.UTC)

            viewModel.updateSchedule(original.copy(planningDate = "2026-10-09", title = "复习", note = "章节 2", startMinute = 60, endMinute = 90, estimatedMinutes = 30))
            advanceUntilIdle()
            assertEquals("schedule-1", repository.state.value.schedules.single().id)
            assertEquals("章节 2", repository.state.value.schedules.single().note)
            assertEquals(60, repository.state.value.schedules.single().startMinute)

            viewModel.updateSchedule(original.copy(planningDate = "bad-date"))
            advanceUntilIdle()
            assertEquals("2026-10-09", repository.state.value.schedules.single().planningDate)
            assertTrue(!viewModel.error.value.isNullOrBlank())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun time_entry_csv_export_uses_one_state_snapshot_and_reports_csv() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val target = Target("csv-target", title = "学习")
            val repository = FakeRepository(
                AttentionState(
                    targets = listOf(target),
                    timeEntries = listOf(TimeEntry("csv-entry", "2026-10-08", 45, target.id, note = "带,逗号")),
                ),
            )
            val viewModel = AttentionViewModel(repository, ZoneOffset.UTC)
            var exported: String? = null

            viewModel.exportTimeEntriesCsv { exported = it }
            advanceUntilIdle()

            assertEquals(
                "\uFEFFplanning_date,duration_minutes,target_id,target_title,source,note\n" +
                    "2026-10-08,45,csv-target,学习,MANUAL,\"带,逗号\"\n",
                exported,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun combined_csv_export_returns_both_files_from_one_snapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val target = Target("bundle-target", title = "学习")
            val schedule = ScheduleEntry("bundle-schedule", "2026-10-08", "复习", estimatedMinutes = 30)
            val repository = FakeRepository(
                AttentionState(
                    targets = listOf(target),
                    timeEntries = listOf(TimeEntry("bundle-entry", "2026-10-08", 45, target.id)),
                    schedules = listOf(schedule),
                ),
            )
            val viewModel = AttentionViewModel(repository, ZoneOffset.UTC)
            var exported: AttentionViewModel.CsvExportBundle? = null

            viewModel.exportCsvBundle { exported = it }
            advanceUntilIdle()

            assertTrue(exported?.timeEntries?.contains("2026-10-08,45,bundle-target,学习,MANUAL") == true)
            assertTrue(exported?.schedules?.contains("2026-10-08,复习,false,30") == true)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun import_preview_and_commit_use_the_same_typed_plan() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeRepository(AttentionState(targets = listOf(Target("current", title = "当前"))))
            val incoming = AttentionBackupCodec.encode(AttentionState(targets = listOf(Target("incoming", title = "导入"))))
            val viewModel = AttentionViewModel(repository, ZoneOffset.UTC)
            var plan: com.attention.app.data.backup.ImportPlan? = null
            var result: BackupCommitResult? = null

            viewModel.prepareImport(incoming, clearExisting = false, onResult = { plan = it })
            advanceUntilIdle()
            viewModel.commitImport(requireNotNull(plan), onResult = { result = it })
            advanceUntilIdle()

            assertEquals(BackupCommitResult.Committed(repository.state.value), result)
            assertEquals(listOf("current", "incoming"), repository.state.value.targets.map { it.id })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stale_import_plan_is_returned_as_typed_view_model_result() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeRepository(AttentionState(targets = listOf(Target("current", title = "当前"))))
            val incoming = AttentionBackupCodec.encode(AttentionState(targets = listOf(Target("incoming", title = "导入"))))
            val viewModel = AttentionViewModel(repository, ZoneOffset.UTC)
            var plan: com.attention.app.data.backup.ImportPlan? = null
            var result: BackupCommitResult? = null

            viewModel.prepareImport(incoming, clearExisting = true, onResult = { plan = it })
            advanceUntilIdle()
            repository.updateBusiness { it.copy(targets = listOf(Target("edited", title = "后来修改"))) }
            viewModel.commitImport(requireNotNull(plan), onResult = { result = it })
            advanceUntilIdle()

            assertTrue(result is BackupCommitResult.StalePlan)
            assertTrue(viewModel.backupError.value is BackupViewModelError.StalePlan)
            assertEquals(listOf("edited"), repository.state.value.targets.map { it.id })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun invalid_import_is_reported_without_changing_state() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeRepository(AttentionState(targets = listOf(Target("current", title = "当前"))))
            val viewModel = AttentionViewModel(repository, ZoneOffset.UTC)
            var failure: Throwable? = null

            viewModel.prepareImport("{not-json", clearExisting = false, onResult = {}, onFailure = { failure = it })
            advanceUntilIdle()

            assertTrue(failure != null)
            assertTrue(viewModel.backupError.value is BackupViewModelError.InvalidImport)
            assertEquals(listOf("current"), repository.state.value.targets.map { it.id })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun restore_without_backup_returns_typed_no_backup_result() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = AttentionViewModel(FakeRepository(AttentionState()), ZoneOffset.UTC)
            var result: BackupCommitResult? = null

            viewModel.restoreLastImportBackupResult(onResult = { result = it })
            advanceUntilIdle()

            assertEquals(BackupCommitResult.NoImportBackup, result)
            assertEquals(BackupCommitResult.NoImportBackup, viewModel.backupResult.value)
            assertEquals(BackupViewModelError.NoImportBackup, viewModel.backupError.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun restore_failure_is_exposed_as_typed_operation_error() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeRepository(AttentionState())
            repository.savedBackup = AttentionBackupCodec.encode(AttentionState(targets = listOf(Target("saved", title = "已保存"))))
            repository.failBackupCommit = true
            val viewModel = AttentionViewModel(repository, ZoneOffset.UTC)
            var failure: Throwable? = null

            viewModel.restoreLastImportBackupResult(onResult = {}, onFailure = { failure = it })
            advanceUntilIdle()

            assertTrue(failure != null)
            assertTrue(viewModel.backupError.value is BackupViewModelError.OperationFailed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun typed_csv_bundle_is_returned_from_view_model_workflow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val state = AttentionState(
                targets = listOf(Target("bundle-typed-target", title = "学习")),
                timeEntries = listOf(TimeEntry("bundle-typed-entry", "2026-10-08", 45, "bundle-typed-target")),
            )
            val viewModel = AttentionViewModel(FakeRepository(state), ZoneOffset.UTC)
            var bundle: ExportBundle? = null

            viewModel.exportCsvBundleDocument { bundle = it }
            advanceUntilIdle()

            assertEquals("time_entries.csv", bundle?.timeEntries?.suggestedFileName)
            assertEquals("attention-schedules.csv", bundle?.schedules?.suggestedFileName)
            assertTrue(bundle?.timeEntries?.content?.contains("bundle-typed-target") == true)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun restoring_last_import_backup_replaces_current_state() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeRepository(AttentionState())
            repository.savedBackup = AttentionBackupCodec.encode(
                AttentionState(targets = listOf(Target("saved", title = "已保存"))),
            )
            val viewModel = AttentionViewModel(repository, ZoneOffset.UTC)
            viewModel.restoreLastImportBackup()
            advanceUntilIdle()
            assertEquals(listOf("saved"), repository.state.value.targets.map { it.id })
        } finally { Dispatchers.resetMain() }
    }

    private class FakeRepository(initial: AttentionState) : AttentionStateRepository {
        private val values = MutableStateFlow(initial)
        val executedCommands = mutableListOf<AttentionCommand>()
        var savedBackup: String? = null
        var failBackupCommit: Boolean = false
        var importedBackup: String? = null
        var importedClearExisting: Boolean? = null
        private val backupStore = object : ImportBackupStore {
            override suspend fun write(encoded: String) { savedBackup = encoded }
            override suspend fun read(): String? = savedBackup
        }
        override val backupWorkflow: BackupWorkflow by lazy {
            BackupWorkflow(object : BackupStateSubmissionPort {
                override suspend fun read(): AttentionState = values.value

                override suspend fun submit(
                    expectedFingerprint: String,
                    next: AttentionState,
                ): BackupCommitResult {
                    val current = values.value
                    val actual = BackupWorkflow.stateFingerprint(current)
                    if (actual != expectedFingerprint) {
                        return BackupCommitResult.StalePlan(expectedFingerprint, actual)
                    }
                    if (failBackupCommit) error("injected backup failure")
                    backupStore.write(AttentionBackupCodec.encode(current))
                    values.value = next
                    return BackupCommitResult.Committed(next)
                }
            }, backupStore)
        }
        override val state: StateFlow<AttentionState> = values

        override suspend fun updateBusiness(transform: (AttentionState) -> AttentionState) {
            values.value = transform(values.value)
        }

        override suspend fun executeCommand(command: AttentionCommand, now: Instant, zone: ZoneId): AttentionCommandResult {
            executedCommands += command
            val result = command.execute(values.value, now, zone)
            values.value = result.state
            return result
        }

        override suspend fun toggleScheduleCompleted(entryId: String) {
            values.value = values.value.toggleScheduleCompleted(entryId)
        }

        override suspend fun setPlannerSettings(settings: StoredSettings) = Unit
        override suspend fun setPlanningDayBoundaryMinutes(minutes: Int) = Unit
        override suspend fun setWeekStartDay(day: Int) = Unit
        override suspend fun setDailyCapacityMinutes(minutes: Int?) = Unit
        override suspend fun setLaunchDestination(destination: LaunchDestination) = Unit
        override suspend fun setLastOpenedDestination(destination: LaunchDestination) = Unit
        override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
        override suspend fun setNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun replace(state: AttentionState) { values.value = state }
        override suspend fun restoreReminders() = Unit
        override suspend fun exportJson(): String = ""
        override suspend fun importJson(encoded: String, clearExisting: Boolean): AttentionState {
            importedBackup = encoded
            importedClearExisting = clearExisting
            return values.value
        }
        override suspend fun lastImportBackup(): String? = savedBackup
    }
}
