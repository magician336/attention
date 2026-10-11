package com.attention.app.data.room

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.entity.RoomMetadataEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttentionDatabaseTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AttentionDatabase::class.java,
    )

    @Test
    fun new_database_opens_empty_and_persists_versioned_metadata() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(AttentionDatabase.DATABASE_NAME)
        val database = AttentionDatabase.create(context)
        try {
            assertNull(database.metadataDao().observe().first())
            database.metadataDao().upsert(RoomMetadataEntity())
            val metadata = database.metadataDao().observe().first()
            assertNotNull(metadata)
            assertEquals(RoomMetadataEntity.INITIAL_SCHEMA_VERSION, metadata?.schemaVersion)
        } finally {
            database.close()
            context.deleteDatabase(AttentionDatabase.DATABASE_NAME)
        }
    }

    @Test
    fun known_pre_release_schema_is_explicitly_reset() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createLegacyDatabase(context, version = 1, identityHash = "91d9333bc58abc527205e22c49515aed")

        assertKnownLegacySchemaIsReset(context)
    }

    @Test
    fun known_business_schema_is_explicitly_reset() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createLegacyDatabase(context, version = 2, identityHash = "5038600f45cff26737644acf4d6cced")

        assertKnownLegacySchemaIsReset(context)
    }

    @Test
    fun schema_v3_migrates_period_snapshots_with_legacy_stage_default() {
        val databaseName = "attention-migration-v3.db"
        val legacy = migrationHelper.createDatabase(databaseName, 3)
        legacy.execSQL(
            "INSERT INTO period_snapshots " +
                "(id, targetId, cadence, periodStart, periodEnd, targetMinutes, actualMinutes, gapMinutes, excessMinutes, completed) " +
                "VALUES ('snapshot', 'deleted-target', 'WEEKLY', '2026-10-01', '2026-10-07', 120, 90, 30, 0, 0)",
        )
        legacy.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            AttentionDatabase.MIGRATION_3_4,
        )
        try {
            migrated.query("SELECT stageId FROM period_snapshots WHERE id = 'snapshot'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
            }
        } finally {
            migrated.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
        }
    }

    private fun createLegacyDatabase(context: Context, version: Int, identityHash: String) {
        context.deleteDatabase(AttentionDatabase.DATABASE_NAME)
        val legacy = context.openOrCreateDatabase(AttentionDatabase.DATABASE_NAME, Context.MODE_PRIVATE, null)
        try {
            legacy.execSQL("CREATE TABLE room_metadata (id INTEGER NOT NULL, schemaVersion INTEGER NOT NULL, PRIMARY KEY(id))")
            if (version == 2) {
                // Representative v2 table from app/schemas/.../2.json ensures
                // the business-schema fallback is exercised, not only metadata.
                legacy.execSQL(
                    "CREATE TABLE targets (id TEXT NOT NULL, parentId TEXT, title TEXT NOT NULL, " +
                        "note TEXT NOT NULL, icon TEXT, colorHex TEXT, archived INTEGER NOT NULL, " +
                        "sortOrder INTEGER NOT NULL, expanded INTEGER NOT NULL, PRIMARY KEY(id))",
                )
            }
            legacy.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            legacy.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) VALUES (42, '$identityHash')",
            )
            legacy.execSQL("PRAGMA user_version = $version")
        } finally {
            legacy.close()
        }
    }

    private suspend fun assertKnownLegacySchemaIsReset(context: Context) {
        val database = AttentionDatabase.create(context)
        try {
            assertNull(database.metadataDao().observe().first())
            assertEquals(0, database.targetDao().getAll().size)
        } finally {
            database.close()
            context.deleteDatabase(AttentionDatabase.DATABASE_NAME)
        }
    }
}
