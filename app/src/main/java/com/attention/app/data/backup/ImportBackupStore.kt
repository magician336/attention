package com.attention.app.data.backup

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ImportBackupStore {
    suspend fun write(encoded: String)
    suspend fun read(): String?
}

/** Persists the most recent pre-import snapshot outside the runtime settings store. */
class FileImportBackupStore(
    context: Context,
    private val fileName: String = DEFAULT_FILE_NAME,
) : ImportBackupStore {
    private val directory: File = context.applicationContext.filesDir

    override suspend fun write(encoded: String) = withContext(Dispatchers.IO) {
        val target = File(directory, fileName)
        val temporary = File(directory, "$fileName.tmp")
        Files.write(
            temporary.toPath(),
            encoded.toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        runCatching {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.recoverCatching {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrThrow()
        Unit
    }

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        File(directory, fileName).takeIf(File::isFile)?.readText(StandardCharsets.UTF_8)
    }

    companion object {
        const val DEFAULT_FILE_NAME = "attention_import_backup.json"
    }
}

class InMemoryImportBackupStore : ImportBackupStore {
    private var value: String? = null

    override suspend fun write(encoded: String) {
        value = encoded
    }

    override suspend fun read(): String? = value
}
