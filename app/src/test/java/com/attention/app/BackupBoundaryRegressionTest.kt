package com.attention.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deletion tests for the #152 backup boundaries. These checks intentionally
 * fail if a page or ViewModel starts owning protocol parsing or merge rules
 * again. The source assertion is the public architecture seam available to
 * this Android module; codec behavior is covered by its own contract tests.
 */
class BackupBoundaryRegressionTest {
    @Test
    fun workspace_does_not_call_the_protocol_codec_directly() {
        val workspace = source("app/src/main/java/com/attention/app/Workspace.kt")

        assertFalse(workspace.contains("AttentionBackupCodec"))
        assertFalse(Regex("AttentionBackupCodec\\s*\\.").containsMatchIn(workspace))
        assertTrue(workspace.contains("viewModel.prepareImport"))
        assertTrue(workspace.contains("viewModel.commitImport"))
    }

    @Test
    fun view_model_submits_typed_workflow_commands_without_codec_or_merge_logic() {
        val viewModel = source("app/src/main/java/com/attention/app/AttentionViewModel.kt")

        assertFalse(viewModel.contains("AttentionBackupCodec"))
        assertFalse(Regex("\\.(decode|merge|preview)\\s*\\(").containsMatchIn(viewModel))
        assertTrue(viewModel.contains("backupWorkflow.prepareImport"))
        assertTrue(viewModel.contains("backupWorkflow.commit"))
        assertTrue(viewModel.contains("backupWorkflow.restoreLatestImportBackup"))
    }

    private fun source(relativePath: String): String {
        var directory = File(requireNotNull(System.getProperty("user.dir")))
        repeat(5) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            val parent = directory.parentFile ?: return@repeat
            directory = parent
        }
        error("Unable to locate $relativePath from ${System.getProperty("user.dir")}")
    }
}
