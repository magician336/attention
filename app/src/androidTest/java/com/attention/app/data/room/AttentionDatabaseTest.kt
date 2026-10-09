package com.attention.app.data.room

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.entity.RoomMetadataEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttentionDatabaseTest {
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
        context.deleteDatabase(AttentionDatabase.DATABASE_NAME)
        val legacy = context.openOrCreateDatabase(AttentionDatabase.DATABASE_NAME, Context.MODE_PRIVATE, null)
        try {
            legacy.execSQL("CREATE TABLE room_metadata (id INTEGER NOT NULL, schemaVersion INTEGER NOT NULL, PRIMARY KEY(id))")
            legacy.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            legacy.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) VALUES (42, '91d9333bc58abc527205e22c49515aed')",
            )
            legacy.execSQL("PRAGMA user_version = 1")
        } finally {
            legacy.close()
        }

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
