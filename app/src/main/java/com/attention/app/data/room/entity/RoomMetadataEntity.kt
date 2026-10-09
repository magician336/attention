package com.attention.app.data.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Optional schema metadata row reserved for future Room initialization state. */
@Entity(tableName = "room_metadata")
data class RoomMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val schemaVersion: Int = INITIAL_SCHEMA_VERSION,
) {
    companion object {
        const val SINGLETON_ID = 1
        const val INITIAL_SCHEMA_VERSION = 1
    }
}
