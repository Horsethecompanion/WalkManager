package com.horse.walkmanager

import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class Track(
    val uri: Uri,
    val name: String,
    val fileName: String,
    val size: Long,
    val mimeType: String,
    val bpm: Int? = null
) {
    val sizeDisplay: String
        get() = when {
            size >= 1_000_000_000 -> "%.1f GB".format(size / 1_000_000_000.0)
            size >= 1_000_000 -> "%.1f MB".format(size / 1_000_000.0)
            else -> "%.1f KB".format(size / 1_000.0)
        }

    companion object {
        fun fromDocumentFile(doc: DocumentFile): Track? {
            val type = doc.type
            return if (doc.isFile && (type?.startsWith("audio/") == true)) {
                Track(
                    uri = doc.uri,
                    name = doc.name ?: "Unknown",
                    fileName = doc.name ?: "unknown.mp3",
                    size = doc.length(),
                    mimeType = type ?: "audio/mpeg",
                    bpm = null // Will extract later if implemented
                )
            } else {
                null
            }
        }
    }
}

sealed class SortOption {
    object ByName : SortOption()
    object BySize : SortOption()
    object ByBPM : SortOption() // For future use
}

data class WalkmanManagerState(
    val walkmanRootUri: Uri? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isWaitingForMount: Boolean = false,
    val error: String? = null,
    val sortOption: SortOption = SortOption.ByName,
    val selectedTracks: Set<String> = emptySet()
)
