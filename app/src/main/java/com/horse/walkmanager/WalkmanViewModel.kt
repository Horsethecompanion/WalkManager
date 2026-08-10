package com.horse.walkmanager

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import kotlin.time.Duration.Companion.seconds

class WalkmanViewModel : ViewModel() {
    private val _state = MutableStateFlow(WalkmanManagerState())
    val state: StateFlow<WalkmanManagerState> = _state.asStateFlow()

    private val prefsName = "walkman_prefs"
    private val keyLastUri = "last_walkman_uri"

    fun setWalkmanRoot(rootUri: Uri, context: Context) {
        _state.value = _state.value.copy(walkmanRootUri = rootUri)
        persistRootUri(rootUri, context)
        refreshTracks(context)
    }

    private fun persistRootUri(uri: Uri, context: Context) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit { putString(keyLastUri, uri.toString()) }
        
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            android.util.Log.w("WalkmanViewModel", "Failed to take persistable permission: ${e.message}")
        }
    }

    fun tryAutoRestore(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val uriString = prefs.getString(keyLastUri, null) ?: return@launch
            
            try {
                _state.value = _state.value.copy(isLoading = true, isWaitingForMount = true)
                val uri = uriString.toUri()
                
                // Retry loop for up to 10 seconds (5 attempts * 2s delay)
                var attempts = 0
                while (attempts < 5) {
                    val hasPermission = context.contentResolver.persistedUriPermissions.any { 
                        it.uri == uri && it.isReadPermission 
                    }
                    
                    if (hasPermission) {
                        val root = DocumentFile.fromTreeUri(context, uri)
                        if (root != null && root.exists() && root.canRead()) {
                            android.util.Log.d("WalkmanViewModel", "Auto-restore successful on attempt ${attempts + 1}")
                            _state.value = _state.value.copy(
                                walkmanRootUri = uri,
                                isWaitingForMount = false
                            )
                            refreshTracks(context)
                            return@launch
                        }
                    }
                    
                    attempts++
                    android.util.Log.d("WalkmanViewModel", "Device not ready, retry $attempts...")
                    kotlinx.coroutines.delay(2.seconds)
                }
                
                // If we reach here, we failed to restore
                _state.value = _state.value.copy(isLoading = false, isWaitingForMount = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, isWaitingForMount = false)
            }
        }
    }

    fun refreshTracks(context: Context) {
        val rootUri = _state.value.walkmanRootUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                
                val tracks = loadTracksHighPerformance(context.applicationContext, rootUri)
                    .map { track ->
                        // Extract BPM if not already present
                        if (track.bpm == null) {
                            val bpm = extractBPM(context, track.uri)
                            track.copy(bpm = bpm)
                        } else {
                            track
                        }
                    }
                    .sortedWith(
                        compareBy { 
                            when (_state.value.sortOption) {
                                SortOption.ByName -> it.name.lowercase()
                                SortOption.BySize -> it.size
                                SortOption.ByBPM -> it.bpm ?: Int.MAX_VALUE // Put unknown BPM at end
                            }
                        }
                    )

                _state.value = _state.value.copy(
                    tracks = tracks,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load tracks: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    private fun extractBPM(context: Context, trackUri: Uri): Int? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, trackUri)
            
            val bpmString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BEATS_PER_MINUTE)
            retriever.release()
            
            bpmString?.toIntOrNull()?.takeIf { it > 0 }
        } catch (e: Exception) {
            android.util.Log.d("BPMExtractor", "Could not extract BPM from ${trackUri.lastPathSegment}: ${e.message}")
            null
        }
    }

    private fun loadTracksHighPerformance(context: Context, rootUri: Uri): List<Track> {
        val allTracks = mutableListOf<Track>()
        val queue = ArrayDeque<Uri>()
        val visited = mutableSetOf<Uri>()

        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(rootUri)
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, rootDocId)
            
            queue.add(rootDocUri)
            visited.add(rootDocUri)

            android.util.Log.d("WalkmanScanner", "Starting HP scan from: $rootDocUri")

            while (queue.isNotEmpty()) {
                val parentUri = queue.removeFirst()
                val parentDocId = try { 
                    DocumentsContract.getDocumentId(parentUri) 
                } catch (e: Exception) { 
                    continue 
                }
                
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, parentDocId)
                
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE
                    ),
                    null, null, null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                    if (idIdx == -1 || nameIdx == -1 || mimeIdx == -1 || sizeIdx == -1) {
                        android.util.Log.e("WalkmanScanner", "Required columns missing in cursor")
                        return@use
                    }

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idIdx) ?: continue
                        val name = cursor.getString(nameIdx)
                        val mime = cursor.getString(mimeIdx) ?: ""
                        val size = if (cursor.isNull(sizeIdx)) 0L else cursor.getLong(sizeIdx)
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (!visited.contains(docUri)) {
                                visited.add(docUri)
                                queue.add(docUri)
                            }
                        } else if (mime.startsWith("audio/")) {
                            allTracks.add(Track(
                                uri = docUri,
                                name = name ?: "Unknown",
                                fileName = name ?: "unknown.mp3",
                                size = size,
                                mimeType = mime
                            ))
                        }
                        
                        if (allTracks.size > 20000) {
                            android.util.Log.w("WalkmanScanner", "Soft limit of 20,000 tracks reached.")
                            return allTracks
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WalkmanScanner", "Fatal error during scan: ${e.message}", e)
            throw e
        }
        
        android.util.Log.d("WalkmanScanner", "HP scan finished: ${allTracks.size} tracks")
        return allTracks
    }

    fun deleteTrack(track: Track, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (DocumentsContract.deleteDocument(context.contentResolver, track.uri)) {
                    refreshTracks(context)
                } else {
                    _state.value = _state.value.copy(error = "Failed to delete: ${track.name}")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Delete error: ${e.message}")
            }
        }
    }

    fun transferTrack(sourceUri: Uri, context: Context) {
        val rootUri = _state.value.walkmanRootUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return@launch
                val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri) ?: return@launch

                val musicDir = findOrCreateMusicDirectory(rootDoc)
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                val outputFile = musicDir.createFile(sourceDoc.type ?: "audio/mpeg", sourceDoc.name ?: "audio.mp3")
                
                if (inputStream != null && outputFile != null) {
                    val outputStream = context.contentResolver.openOutputStream(outputFile.uri)
                    if (outputStream != null) {
                        inputStream.copyTo(outputStream)
                        outputStream.close()
                        inputStream.close()
                        refreshTracks(context)
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Transfer error: ${e.message}")
            }
        }
    }

    private fun findOrCreateMusicDirectory(root: DocumentFile): DocumentFile {
        val musicDir = root.findFile("MUSIC")
        return musicDir ?: root.createDirectory("MUSIC") ?: root
    }

    fun setSortOption(option: SortOption, context: Context) {
        _state.value = _state.value.copy(sortOption = option)
        refreshTracks(context)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun toggleTrackSelection(fileName: String) {
        val selected = _state.value.selectedTracks.toMutableSet()
        if (selected.contains(fileName)) {
            selected.remove(fileName)
        } else {
            selected.add(fileName)
        }
        _state.value = _state.value.copy(selectedTracks = selected)
    }

    fun deleteSelectedTracks(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value.tracks
                    .filter { it.fileName in _state.value.selectedTracks }
                    .forEach { track -> 
                        DocumentsContract.deleteDocument(context.contentResolver, track.uri)
                    }
                _state.value = _state.value.copy(selectedTracks = emptySet())
                refreshTracks(context)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Bulk delete error: ${e.message}")
            }
        }
    }
}
