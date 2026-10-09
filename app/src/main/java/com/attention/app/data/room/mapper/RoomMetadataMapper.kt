package com.attention.app.data.room.mapper

import com.attention.app.data.room.entity.RoomMetadataEntity

fun RoomMetadataEntity.schemaVersionValue(): Int = schemaVersion

fun Int.toRoomMetadata(): RoomMetadataEntity = RoomMetadataEntity(schemaVersion = this)
