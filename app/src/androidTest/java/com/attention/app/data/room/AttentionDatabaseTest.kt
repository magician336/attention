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

    @Test
    fun schema_v4_migrates_schedule_links_without_rewriting_history() {
        val databaseName = "attention-migration-v4.db"
        val legacy = migrationHelper.createDatabase(databaseName, 4)
        legacy.execSQL(
            "INSERT INTO targets (id, parentId, title, note, icon, colorHex, archived, sortOrder, expanded) " +
                "VALUES ('target', NULL, '阅读', '', NULL, NULL, 0, 0, 1)",
        )
        legacy.execSQL(
            "INSERT INTO schedule_entries (id, planningDate, title, startMinute, endMinute, estimatedMinutes, note, " +
                "targetId, completed, reminderEpochMillis, recurrenceRuleId) " +
                "VALUES ('schedule', '2026-10-08', '阅读', NULL, NULL, 45, '', 'target', 1, NULL, NULL)",
        )
        legacy.execSQL(
            "INSERT INTO time_entries (id, planningDate, durationMinutes, targetId, source, occurredAtEpochMillis, note) " +
                "VALUES ('manual-entry', '2026-10-08', 30, 'target', 'MANUAL', NULL, '')",
        )
        legacy.execSQL(
            "INSERT INTO time_entries (id, planningDate, durationMinutes, targetId, source, occurredAtEpochMillis, note) " +
                "VALUES ('timer-entry', '2026-10-08', 60, 'target', 'TIMER', 1000, '')",
        )
        legacy.execSQL(
            "INSERT INTO time_entries (id, planningDate, durationMinutes, targetId, source, occurredAtEpochMillis, note) " +
                "VALUES ('unowned-entry', '2026-10-08', 20, NULL, 'IMPORT', NULL, '')",
        )
        legacy.execSQL(
            "INSERT INTO active_timer (id, targetId, startedAtEpochMillis, accumulatedMillis, paused, lastPlanningDate, completedSegmentsJson) " +
                "VALUES (1, 'target', 1000, 0, 0, '2026-10-08', '[]')",
        )
        legacy.execSQL("INSERT INTO experience (id, points) VALUES (1, 90)")
        legacy.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            AttentionDatabase.MIGRATION_4_5,
        )
        try {
            // History is untouched: same rows, same sources, no guessed schedule link.
            migrated.query("SELECT id, source, durationMinutes, scheduleEntryId FROM time_entries ORDER BY id").use { cursor ->
                assertEquals(3, cursor.count)
                val rows = buildList {
                    while (cursor.moveToNext()) {
                        add(Triple(cursor.getString(0), cursor.getString(1), cursor.getInt(2)) to cursor.getString(3))
                    }
                }
                assertEquals(
                    listOf(
                        Triple("manual-entry", "MANUAL", 30) to null,
                        Triple("timer-entry", "TIMER", 60) to null,
                        Triple("unowned-entry", "IMPORT", 20) to null,
                    ),
                    rows,
                )
            }
            migrated.query("SELECT scheduleEntryId FROM active_timer WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertNull(cursor.getString(0))
            }
            migrated.query("SELECT points FROM experience WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(90L, cursor.getLong(0))
            }
            migrated.query("SELECT SUM(durationMinutes) FROM time_entries").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(110L, cursor.getLong(0))
            }

            } finally {
            migrated.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
        }
    }

    @Test
    fun schema_v5_adds_the_backfill_submission_intent_without_rewriting_history() {
        val databaseName = "attention-migration-v5.db"
        val legacy = migrationHelper.createDatabase(databaseName, 5)
        legacy.execSQL(
            "INSERT INTO targets (id, parentId, title, note, icon, colorHex, archived, sortOrder, expanded) " +
                "VALUES ('target', NULL, '阅读', '', NULL, NULL, 0, 0, 1)",
        )
        legacy.execSQL(
            "INSERT INTO schedule_entries (id, planningDate, title, startMinute, endMinute, estimatedMinutes, note, " +
                "targetId, completed, reminderEpochMillis, recurrenceRuleId) " +
                "VALUES ('schedule', '2026-10-08', '阅读', NULL, NULL, 45, '', 'target', 1, NULL, NULL)",
        )
        legacy.execSQL(
            "INSERT INTO time_entries (id, planningDate, durationMinutes, targetId, source, occurredAtEpochMillis, note, scheduleEntryId) " +
                "VALUES ('confirmed-entry', '2026-10-08', 30, 'target', 'SCHEDULE_CONFIRMED', NULL, '', 'schedule')",
        )
        legacy.execSQL(
            "INSERT INTO time_entries (id, planningDate, durationMinutes, targetId, source, occurredAtEpochMillis, note, scheduleEntryId) " +
                "VALUES ('timer-entry', '2026-10-08', 60, 'target', 'TIMER', 1000, '', 'schedule')",
        )
        legacy.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            AttentionDatabase.MIGRATION_5_6,
        )
        try {
            // Historical rows keep their source and schedule link, and are never guessed
            // to be a replay of any submission intent.
            migrated.query("SELECT id, source, scheduleEntryId, backfillRequestId FROM time_entries ORDER BY id").use { cursor ->
                assertEquals(2, cursor.count)
                val rows = buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0) to (cursor.getString(1) to (cursor.getString(2) to cursor.getString(3))))
                    }
                }
                assertEquals(
                    listOf(
                        "confirmed-entry" to ("SCHEDULE_CONFIRMED" to ("schedule" to null)),
                        "timer-entry" to ("TIMER" to ("schedule" to null)),
                    ),
                    rows,
                )
            }
            migrated.query("SELECT SUM(durationMinutes) FROM time_entries").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(90L, cursor.getLong(0))
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
