package com.horse.walkmanager

import android.content.Context

class BpmRepository(private val db: BpmDatabase) {
    suspend fun getBpm(artist: String, title: String): Int? {
        if (artist.isBlank() || title.isBlank()) return null
        
        val id = "$artist - $title".lowercase()
        val cached = db.bpmDao().getBpm(id)
        if (cached != null) return cached.bpm
        
        // Placeholder for real network fetch
        // In the future, this would call BpmApiService
        return null
    }

    suspend fun saveBpm(artist: String, title: String, bpm: Int) {
        val id = "$artist - $title".lowercase()
        db.bpmDao().insertBpm(BpmEntity(id, artist, title, bpm))
    }
}
