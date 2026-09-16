package com.horse.walkmanager

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class BpmRepository(private val db: BpmDatabase) {
    
    private val api: BpmApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://musicbrainz.org/ws/2/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BpmApiService::class.java)
    }

    suspend fun getBpm(artist: String, title: String): Int? {
        if (artist.isBlank() || title.isBlank()) return null
        
        val cacheId = "$artist - $title".lowercase()
        val cached = db.bpmDao().getBpm(cacheId)
        if (cached != null) return cached.bpm
        
        // Try searching online
        return try {
            val query = "recording:\"$title\" AND artist:\"$artist\""
            val response = api.searchRecording(query)
            
            // Extract BPM from tags if available (some recordings have BPM tags)
            val bpm = response.recordings?.firstOrNull()?.tags
                ?.firstOrNull { it.name.contains("bpm", ignoreCase = true) }
                ?.name?.filter { it.isDigit() }?.toIntOrNull()
            
            if (bpm != null) {
                saveBpm(artist, title, bpm)
            }
            bpm
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveBpm(artist: String, title: String, bpm: Int) {
        val id = "$artist - $title".lowercase()
        db.bpmDao().insertBpm(BpmEntity(id, artist, title, bpm))
    }
}
