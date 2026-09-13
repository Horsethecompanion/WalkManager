package com.horse.walkmanager

import retrofit2.http.GET
import retrofit2.http.Query

interface BpmApiService {
    // This is a placeholder for a real BPM API service
    // For now, we simulate finding BPM data
    @GET("search")
    suspend fun findBpm(
        @Query("artist") artist: String,
        @Query("title") title: String
    ): BpmResponse
}

data class BpmResponse(
    val bpm: Int?
)
