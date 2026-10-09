package com.attention.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.attention.app.data.room.entity.RoomMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomMetadataDao {
    @Query("SELECT * FROM room_metadata WHERE id = :id")
    fun observe(id: Int = RoomMetadataEntity.SINGLETON_ID): Flow<RoomMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: RoomMetadataEntity)
}
