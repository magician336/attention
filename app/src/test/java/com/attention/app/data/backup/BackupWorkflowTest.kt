package com.attention.app.data.backup

import com.attention.domain.AttentionState
import com.attention.domain.Milestone
import com.attention.domain.ScheduleEntry
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupWorkflowTest {
    @Test
    fun prepare_import_reads_current_state_once_and_keeps_preview_and_candidate_consistent() = runBlocking {
        val current = AttentionState(targets = listOf(Target("current", title = "已有")))
        val incoming = AttentionState(targets = listOf(Target("incoming", title = "导入")))
        val encoded = AttentionBackupCodec.encode(incoming)
        var reads = 0
        val workflow = BackupWorkflow {
            reads += 1
            current
        }

        val plan = workflow.prepareImport(encoded, ImportMode.MERGE)

        assertEquals(1, reads)
        assertEquals(AttentionBackup.CURRENT_VERSION, plan.inputVersion)
        assertEquals(plan.inputVersion, plan.preview.version)
        assertEquals(2, plan.candidateState.targets.size)
        assertEquals(plan.preview.targets + current.targets.size, plan.candidateState.targets.size)
        assertEquals(ImportMode.MERGE, plan.mode)
    }

    @Test
    fun fingerprint_is_stable_for_reordered_collections_and_changes_for_content() = runBlocking {
        val first = AttentionState(
            targets = listOf(Target("b", title = "B"), Target("a", title = "A")),
            timeEntries = listOf(TimeEntry("entry-b", "2026-10-02", 20), TimeEntry("entry-a", "2026-10-01", 10)),
        )
        val reordered = first.copy(
            targets = first.targets.reversed(),
            timeEntries = first.timeEntries.reversed(),
        )
        val changed = first.copy(targets = listOf(Target("a", title = "changed"), Target("b", title = "B")))

        assertEquals(BackupWorkflow.stateFingerprint(first), BackupWorkflow.stateFingerprint(reordered))
        assertNotEquals(BackupWorkflow.stateFingerprint(first), BackupWorkflow.stateFingerprint(changed))
        assertEquals(
            BackupWorkflow.stateFingerprint(first),
            BackupWorkflow.stateFingerprint(first.copy(experience = 999_999L)),
        )
    }

    @Test
    fun fingerprint_preserves_valid_duplicate_milestone_rows() = runBlocking {
        val oneRow = AttentionState(
            milestones = listOf(
                Milestone("milestone-a", "goal_period", "goal:1", 500L, 1L),
            ),
        )
        val twoRows = oneRow.copy(
            milestones = oneRow.milestones + Milestone("milestone-b", "goal_period", "goal:1", 500L, 2L),
        )

        assertNotEquals(
            BackupWorkflow.stateFingerprint(oneRow),
            BackupWorkflow.stateFingerprint(twoRows),
        )
    }

    @Test
    fun invalid_prepare_import_routes_snapshot_preservation_through_submission_port() = runBlocking {
        val port = RecordingSubmissionPort(AttentionState(targets = listOf(Target("current", title = "已有"))))
        val workflow = BackupWorkflow(
            stateSubmissionPort = port,
            importBackupStore = InMemoryImportBackupStore(),
        )

        assertTrue(runCatching { workflow.prepareImport("{not-json", ImportMode.REPLACE) }.isFailure)
        assertEquals(1, port.preserveCalls)
    }

    @Test
    fun replace_plan_uses_the_validated_incoming_state_without_merging_current_rows() = runBlocking {
        val current = AttentionState(targets = listOf(Target("current", title = "已有")))
        val incoming = AttentionState(targets = listOf(Target("incoming", title = "导入")))

        val plan = BackupWorkflow { current }
            .prepareImport(AttentionBackupCodec.encode(incoming), ImportMode.REPLACE)

        assertEquals(listOf("incoming"), plan.candidateState.targets.map { it.id })
    }

    @Test
    fun replacement_mode_reuses_the_preview_plan_without_reparsing_or_rereading() = runBlocking {
        val current = AttentionState(targets = listOf(Target("current", title = "已有")))
        val incoming = AttentionState(targets = listOf(Target("incoming", title = "导入")))
        var reads = 0
        val mergePlan = BackupWorkflow {
            reads += 1
            current
        }.prepareImport(AttentionBackupCodec.encode(incoming), ImportMode.MERGE)

        val replacement = mergePlan.forReplacement()

        assertEquals(1, reads)
        assertEquals(ImportMode.REPLACE, replacement.mode)
        assertEquals(incoming.targets, replacement.candidateState.targets)
        assertEquals(mergePlan.preview, replacement.preview)
        assertEquals(mergePlan.currentStateFingerprint, replacement.currentStateFingerprint)
    }

    @Test
    fun typed_exports_have_format_filename_and_content() = runBlocking {
        val state = AttentionState(
            targets = listOf(Target("target", title = "学习")),
            timeEntries = listOf(TimeEntry("entry", "2026-10-01", 30, "target")),
            schedules = listOf(ScheduleEntry("schedule", "2026-10-01", "复习", targetId = "target")),
        )
        val workflow = BackupWorkflow { state }

        val json = workflow.exportJson()
        val timeEntries = workflow.exportTimeEntriesCsv()
        val schedules = workflow.exportSchedulesCsv()

        assertEquals(ExportFormat.JSON, json.format)
        assertEquals("attention-backup.json", json.suggestedFileName)
        assertTrue(json.content.contains("\"version\":1"))
        assertEquals(ExportFormat.TIME_ENTRIES_CSV, timeEntries.format)
        assertEquals("time_entries.csv", timeEntries.suggestedFileName)
        assertTrue(timeEntries.content.contains("planning_date,duration_minutes"))
        assertEquals(ExportFormat.SCHEDULES_CSV, schedules.format)
        assertEquals("attention-schedules.csv", schedules.suggestedFileName)
        assertTrue(schedules.content.contains("planning_date,title,completed"))
    }

    @Test
    fun csv_bundle_reads_one_snapshot_for_both_documents() = runBlocking {
        val snapshots = listOf(
            AttentionState(timeEntries = listOf(TimeEntry("first", "2026-10-01", 10))),
            AttentionState(timeEntries = listOf(TimeEntry("second", "2026-10-02", 20))),
        )
        var reads = 0
        val workflow = BackupWorkflow {
            snapshots[reads++]
        }

        val bundle = workflow.exportCsvBundle()

        assertEquals(1, reads)
        assertEquals(ExportFormat.TIME_ENTRIES_CSV, bundle.timeEntries.format)
        assertEquals(ExportFormat.SCHEDULES_CSV, bundle.schedules.format)
        assertTrue(bundle.timeEntries.content.contains("2026-10-01"))
        assertTrue(!bundle.timeEntries.content.contains("2026-10-02"))
        assertTrue(bundle.schedules.content.contains("planning_date,title,completed"))
    }

    @Test
    fun stale_plan_is_typed_and_does_not_rotate_snapshot_or_write() = runBlocking {
        val current = AttentionState(targets = listOf(Target("current", title = "已有")))
        val incoming = AttentionState(targets = listOf(Target("incoming", title = "导入")))
        val backupStore = CountingBackupStore()
        val port = InMemorySubmissionPort(current, backupStore)
        val workflow = BackupWorkflow(port, backupStore)
        val plan = workflow.prepareImport(AttentionBackupCodec.encode(incoming), ImportMode.REPLACE)

        // Simulate an edit after the preview was shown.
        port.current = AttentionState(targets = listOf(Target("edited", title = "后来修改")))
        val result = workflow.commit(plan)

        assertTrue(result is BackupCommitResult.StalePlan)
        assertEquals(0, port.commits)
        assertEquals(0, backupStore.writes)
        assertEquals("edited", port.current.targets.single().id)
    }

    @Test
    fun restore_uses_commit_path_and_exchanges_the_one_step_snapshot() = runBlocking {
        val initial = AttentionState(targets = listOf(Target("initial", title = "初始")))
        val imported = AttentionState(targets = listOf(Target("imported", title = "导入")))
        val backupStore = CountingBackupStore()
        val port = InMemorySubmissionPort(initial, backupStore)
        val workflow = BackupWorkflow(port, backupStore)

        val plan = workflow.prepareImport(AttentionBackupCodec.encode(imported), ImportMode.REPLACE)
        assertTrue(workflow.commit(plan) is BackupCommitResult.Committed)
        assertEquals("imported", port.current.targets.single().id)

        val restored = workflow.restoreLatestImportBackup()

        assertTrue(restored is BackupCommitResult.Committed)
        assertEquals("initial", port.current.targets.single().id)
        assertEquals(
            "imported",
            AttentionBackupCodec.decode(requireNotNull(backupStore.read())).targets.single().id,
        )
        assertEquals(2, backupStore.writes)
    }

    @Test
    fun restore_without_a_snapshot_returns_typed_no_backup_result() = runBlocking {
        val backupStore = CountingBackupStore()
        val port = InMemorySubmissionPort(AttentionState(), backupStore)

        val result = BackupWorkflow(port, backupStore).restoreLatestImportBackup()

        assertEquals(BackupCommitResult.NoImportBackup, result)
        assertEquals(0, port.commits)
        assertEquals(0, backupStore.writes)
    }

    @Test
    fun concurrent_commits_serialize_and_only_one_matching_plan_is_applied() = runBlocking {
        val current = AttentionState(targets = listOf(Target("current", title = "已有")))
        val first = AttentionState(targets = listOf(Target("first", title = "第一")))
        val second = AttentionState(targets = listOf(Target("second", title = "第二")))
        val backupStore = CountingBackupStore()
        val port = InMemorySubmissionPort(current, backupStore)
        val workflow = BackupWorkflow(port, backupStore)
        val firstPlan = workflow.prepareImport(AttentionBackupCodec.encode(first), ImportMode.REPLACE)
        val secondPlan = workflow.prepareImport(AttentionBackupCodec.encode(second), ImportMode.REPLACE)

        val results = coroutineScope {
            listOf(
                async { workflow.commit(firstPlan) },
                async { workflow.commit(secondPlan) },
            ).awaitAll()
        }

        assertEquals(1, results.count { it is BackupCommitResult.Committed })
        assertEquals(1, results.count { it is BackupCommitResult.StalePlan })
        assertEquals(1, port.commits)
        assertEquals(1, backupStore.writes)
    }

    private class CountingBackupStore : ImportBackupStore {
        private val delegate = InMemoryImportBackupStore()
        var writes: Int = 0
            private set

        override suspend fun write(encoded: String) {
            writes += 1
            delegate.write(encoded)
        }

        override suspend fun read(): String? = delegate.read()
    }

    private class InMemorySubmissionPort(
        initial: AttentionState,
        private val backupStore: ImportBackupStore,
    ) : BackupStateSubmissionPort {
        private val mutex = Mutex()
        var current: AttentionState = initial
        var commits: Int = 0
            private set

        override suspend fun read(): AttentionState = current

        override suspend fun submit(
            expectedFingerprint: String,
            next: AttentionState,
        ): BackupCommitResult = mutex.withLock {
            val actual = BackupWorkflow.stateFingerprint(current)
            if (actual != expectedFingerprint) {
                return@withLock BackupCommitResult.StalePlan(expectedFingerprint, actual)
            }
            backupStore.write(AttentionBackupCodec.encode(current))
            current = next
            commits += 1
            BackupCommitResult.Committed(current)
        }
    }

    private class RecordingSubmissionPort(
        private val current: AttentionState,
    ) : BackupStateSubmissionPort {
        var preserveCalls: Int = 0
            private set

        override suspend fun read(): AttentionState = current

        override suspend fun preserveImportBackupIfAbsent() {
            preserveCalls += 1
        }

        override suspend fun submit(
            expectedFingerprint: String,
            next: AttentionState,
        ): BackupCommitResult = error("not used")
    }
}
