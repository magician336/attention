package com.attention.app.data.backup

import com.attention.domain.AttentionState
import com.attention.domain.recalculateExperience
import com.attention.domain.schedulesCsv
import com.attention.domain.timeEntriesCsv
import java.security.MessageDigest
import java.nio.charset.StandardCharsets

/** The import operation requested by the caller of [BackupWorkflow]. */
enum class ImportMode {
    MERGE,
    REPLACE;

    companion object {
        /** Compatibility aliases for callers that describe replacement as clearing first. */
        val CLEAR: ImportMode
            get() = REPLACE
        val CLEAR_EXISTING: ImportMode
            get() = REPLACE
        val CLEAR_AND_RESTORE: ImportMode
            get() = REPLACE
    }
}

/** An immutable result of parsing and planning an import against one state snapshot. */
data class ImportPlan(
    val mode: ImportMode,
    val preview: BackupPreview,
    val candidateState: AttentionState,
    val inputVersion: Int,
    val currentStateFingerprint: String,
    /** The decoded incoming state retained so UI mode changes do not reparse. */
    val incomingState: AttentionState? = null,
) {
    /** Short alias for adapters that refer to the candidate as the planned state. */
    val plannedState: AttentionState
        get() = candidateState

    /** Short alias used by stale-plan checks in persistence adapters. */
    val fingerprint: String
        get() = currentStateFingerprint

    /** Selects clear-and-restore while retaining this exact preview snapshot. */
    fun forReplacement(): ImportPlan = copy(
        mode = ImportMode.REPLACE,
        candidateState = incomingState ?: candidateState,
        preview = preview.withCandidateState(incomingState ?: candidateState),
    )
}

/** The stable formats emitted by the backup workflow. */
enum class ExportFormat {
    JSON,
    TIME_ENTRIES_CSV,
    SCHEDULES_CSV,
}

/** A typed file payload; Android SAF adapters only need to write [content]. */
data class ExportDocument(
    val format: ExportFormat,
    val suggestedFileName: String,
    val content: String,
) {
    val fileName: String
        get() = suggestedFileName
}

/** Both CSV documents produced from one immutable state snapshot. */
data class ExportBundle(
    val timeEntries: ExportDocument,
    val schedules: ExportDocument,
) {
    val documents: List<ExportDocument>
        get() = listOf(timeEntries, schedules)
}

/**
 * Result returned by a state submission port. A stale result is deliberately
 * typed so callers can ask the user to prepare the import again without
 * treating a rejected plan as a storage failure.
 */
sealed interface BackupCommitResult {
    data class Committed(val state: AttentionState) : BackupCommitResult

    data class StalePlan(
        val expectedFingerprint: String,
        val actualFingerprint: String,
    ) : BackupCommitResult {
        val fingerprint: String
            get() = expectedFingerprint
    }

    data object NoImportBackup : BackupCommitResult

    val isCommitted: Boolean
        get() = this is Committed

    val isStale: Boolean
        get() = this is StalePlan
}

/** Compatibility name for adapters that call this operation a submission. */
typealias StateSubmissionResult = BackupCommitResult

/**
 * Persistence seam used by [BackupWorkflow]. Implementations own their
 * mutex, Room transaction, DataStore transaction, reminder reconciliation and
 * import-snapshot rotation. The workflow only supplies a typed candidate and
 * the fingerprint captured by its immutable [ImportPlan].
 */
interface BackupStateSubmissionPort : BackupStateReader {
    suspend fun submit(expectedFingerprint: String, next: AttentionState): BackupCommitResult

    /**
     * Compatibility imports call this when parsing fails before a plan exists.
     * Production ports perform the read/write under the same serialized
     * boundary as [submit]; test and non-persistent ports may leave it empty.
     */
    suspend fun preserveImportBackupIfAbsent() = Unit

    suspend fun commit(expectedFingerprint: String, next: AttentionState): BackupCommitResult =
        submit(expectedFingerprint, next)

    suspend fun replace(expectedFingerprint: String, next: AttentionState): BackupCommitResult =
        submit(expectedFingerprint, next)
}

/** Alias for callers that use "state committer" terminology. */
typealias BackupStateCommitter = BackupStateSubmissionPort

/**
 * Pure Kotlin seam for backup preparation and export.
 *
 * Reading state is injected so this class does not know about Room, DataStore,
 * reminders, or Android file APIs. A caller supplies one read operation and
 * the workflow never reads a second snapshot while building a result.
 */
class BackupWorkflow(
    private val stateReader: suspend () -> AttentionState,
    private val stateSubmissionPort: BackupStateSubmissionPort? = null,
    private val importBackupStore: ImportBackupStore? = null,
) {
    constructor(reader: BackupStateReader) : this(stateReader = { reader.read() })

    constructor(
        stateSubmissionPort: BackupStateSubmissionPort,
        importBackupStore: ImportBackupStore,
    ) : this(
        stateReader = { stateSubmissionPort.read() },
        stateSubmissionPort = stateSubmissionPort,
        importBackupStore = importBackupStore,
    )

    constructor(
        reader: BackupStateReader,
        stateSubmissionPort: BackupStateSubmissionPort,
        importBackupStore: ImportBackupStore,
    ) : this(
        stateReader = { reader.read() },
        stateSubmissionPort = stateSubmissionPort,
        importBackupStore = importBackupStore,
    )

    /** Builds an immutable plan without writing or retaining mutable repository state. */
    suspend fun prepareImport(encoded: String, mode: ImportMode): ImportPlan {
        // Decode and preview are both delegated to the protocol authority. The
        // state read is deliberately performed once and is shared by the plan
        // fingerprint and merge candidate.
        return try {
            val current = stateReader()
            val incoming = AttentionBackupCodec.decode(encoded)
            val preview = AttentionBackupCodec.preview(encoded)
            val candidate = if (mode == ImportMode.MERGE) {
                AttentionBackupCodec.merge(current, incoming)
            } else {
                incoming
            }
            // Merge can combine references from two independently valid states;
            // validate the resulting candidate before exposing it to a caller.
            AttentionBackupCodec.validate(candidate)
            ImportPlan(
                mode = mode,
                preview = preview.withImportStates(current, candidate),
                candidateState = candidate,
                inputVersion = preview.version,
                currentStateFingerprint = stateFingerprint(current),
                incomingState = incoming,
            )
        } catch (error: Throwable) {
            stateSubmissionPort?.let { port ->
                runCatching { port.preserveImportBackupIfAbsent() }
                    .onFailure(error::addSuppressed)
            }
            throw error
        }
    }

    /** Transitional overload for adapters that still expose the old boolean flag. */
    suspend fun prepareImport(encoded: String, clearExisting: Boolean): ImportPlan =
        prepareImport(encoded, if (clearExisting) ImportMode.REPLACE else ImportMode.MERGE)

    /**
     * Commits exactly the candidate captured by [plan]. The submission port
     * re-reads state and compares the fingerprint while holding its mutex, so
     * an edit made after preview cannot silently be overwritten.
     */
    suspend fun commit(plan: ImportPlan): BackupCommitResult {
        val submission = stateSubmissionPort
            ?: error("BackupWorkflow requires a BackupStateSubmissionPort for commit")
        return submission.submit(plan.currentStateFingerprint, plan.candidateState)
    }

    /**
     * Restores the latest pre-import snapshot through the same commit path.
     * The production submission port rotates the snapshot before replacement,
     * which preserves one-step exchange (restore can be undone once).
     */
    suspend fun restoreLatestImportBackup(): BackupCommitResult {
        val store = importBackupStore
            ?: error("BackupWorkflow requires an ImportBackupStore for restore")
        val encoded = store.read() ?: return BackupCommitResult.NoImportBackup
        val plan = prepareImport(encoded, ImportMode.REPLACE)
        return commit(plan)
    }

    suspend fun exportJson(): ExportDocument {
        val snapshot = stateReader()
        return exportDocument(ExportFormat.JSON, "attention-backup.json", AttentionBackupCodec.encode(snapshot))
    }

    suspend fun exportTimeEntriesCsv(): ExportDocument {
        val snapshot = stateReader()
        return exportDocument(ExportFormat.TIME_ENTRIES_CSV, "time_entries.csv", snapshot.timeEntriesCsv())
    }

    suspend fun exportSchedulesCsv(): ExportDocument {
        val snapshot = stateReader()
        return exportDocument(ExportFormat.SCHEDULES_CSV, "attention-schedules.csv", snapshot.schedulesCsv())
    }

    /** Produces both CSVs from one snapshot, preventing mixed-version bundles. */
    suspend fun exportCsvBundle(): ExportBundle {
        val snapshot = stateReader()
        return ExportBundle(
            timeEntries = exportDocument(ExportFormat.TIME_ENTRIES_CSV, "time_entries.csv", snapshot.timeEntriesCsv()),
            schedules = exportDocument(ExportFormat.SCHEDULES_CSV, "attention-schedules.csv", snapshot.schedulesCsv()),
        )
    }

    private fun exportDocument(format: ExportFormat, fileName: String, content: String): ExportDocument =
        ExportDocument(format = format, suggestedFileName = fileName, content = content)

    companion object {
        private const val FINGERPRINT_PREFIX = "attention-state-v1:"

        /**
         * Computes a deterministic fingerprint from canonical state data.
         * Collection order and persisted experience caches are normalized before
         * the existing versioned codec produces the protocol representation.
         */
        fun stateFingerprint(state: AttentionState): String {
            val normalized = normalizeForFingerprint(state)
            val encoded = AttentionBackupCodec.encodeForFingerprint(normalized)
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(encoded.toByteArray(StandardCharsets.UTF_8))
            return FINGERPRINT_PREFIX + digest.joinToString("") { byte -> "%02x".format(byte) }
        }

        private fun normalizeForFingerprint(state: AttentionState): AttentionState {
            // IDs are the stable identity for every persisted entity. Sorting
            // them removes incidental list order while retaining each value.
            val normalized = state.copy(
                targets = state.targets.sortedBy { it.id },
                goalStages = state.goalStages.sortedBy { it.id },
                timeEntries = state.timeEntries.sortedBy { it.id },
                schedules = state.schedules.sortedBy { it.id },
                recurrenceRules = state.recurrenceRules
                    .map { it.copy(weekdays = it.weekdays.sorted()) }
                    .sortedBy { it.id },
                migrations = state.migrations.sortedBy { it.id },
                futureGoalRules = state.futureGoalRules.sortedBy { it.id },
                periodSnapshots = state.periodSnapshots.sortedBy { it.id },
                targetMoves = state.targetMoves.sortedBy { it.id },
                milestones = state.milestones
                    .sortedWith(compareBy({ it.instanceKey }, { it.id }))
            )
            // Experience is a derived cache. Recompute it through the domain
            // rule so stale persisted totals do not invalidate an unchanged
            // ledger fingerprint.
            return normalized.recalculateExperience()
        }
    }
}

/** Adapter-facing state read seam; implementations may be Room/DataStore based. */
fun interface BackupStateReader {
    suspend fun read(): AttentionState
}
