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
}
