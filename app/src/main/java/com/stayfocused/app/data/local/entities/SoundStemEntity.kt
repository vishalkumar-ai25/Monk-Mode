package com.stayfocused.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sound_stems")
data class SoundStemEntity(
    @PrimaryKey
    val stemId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "assetPath")
    val assetPath: String,
    @ColumnInfo(name = "volume")
    val volume: Float = 0.0f,
    @ColumnInfo(name = "isEnabled")
    val isEnabled: Boolean = false
)
