package com.horse.walkmanager

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bpm_cache")
data class BpmEntity(
    @PrimaryKey val id: String, // Use "Artist - Title" as key
    val artist: String,
    val title: String,
    val bpm: Int,
    val timestamp: Long = System.currentTimeMillis()
)
