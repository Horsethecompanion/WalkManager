package com.horse.walkmanager

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface BpmApiService {
    @Headers("User-Agent: WalkManager/1.0 ( your-email@example.com )")
    @GET("recording")
    suspend fun searchRecording(
        @Query("query") query: String,
        @Query("fmt") format: String = "json"
    ): MusicBrainzResponse
}

data class MusicBrainzResponse(
    val recordings: List<Recording>?
)

data class Recording(
    val id: String,
    val title: String,
    val artistCredit: List<ArtistCredit>?,
    val tags: List<Tag>?
)

data class ArtistCredit(
    val artist: Artist
)

data class Artist(
    val name: String
)

data class Tag(
    val count: Int,
    val name: String
)
