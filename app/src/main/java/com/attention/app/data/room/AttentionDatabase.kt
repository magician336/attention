package com.attention.app.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.attention.app.data.room.dao.RoomMetadataDao
import com.attention.app.data.room.entity.RoomMetadataEntity

@Database(
    entities = [RoomMetadataEntity::class],
    version = RoomMetadataEntity.INITIAL_SCHEMA_VERSION,
    exportSchema = true,
)
abstract class AttentionDatabase : RoomDatabase() {
    abstract fun metadataDao(): RoomMetadataDao

    companion object {
        const val DATABASE_NAME = "attention.db"

        fun create(context: Context): AttentionDatabase = Room.databaseBuilder(
            context.applicationContext,
            AttentionDatabase::class.java,
            DATABASE_NAME,
        ).build()
    }
}
