package com.horse.walkmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class WalkmanViewModel : ViewModel() {
    private val _state = MutableStateFlow(WalkmanManagerState())
    val state: StateFlow<WalkmanManagerState> = _state.asStateFlow()

    private val PREFS_NAME = "walkman_prefs"
    private val KEY_LAST_URI = "last_walkman_uri"

    fun setWalkmanRoot(rootUri: Uri, context: Context) {
        _state.value = _state.value.copy(walkmanRootUri = rootUri)
        persistRootUri(rootUri, context)
        refreshTracks(context)
    }

    private fun persistRootUri(uri: Uri, context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_URI, uri.toString()).apply()
        
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
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val uriString = prefs.getString(KEY_LAST_URI, null) ?: return@launch
            
            try {
                _state.value = _state.value.copy(isLoading = true, isWaitingForMount = true)
                val uri = Uri.parse(uriString)
                
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
                    kotlinx.coroutines.delay(2000)
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
                    .sortedWith(
                        compareBy { 
                            when (_state.value.sortOption) {
                                SortOption.ByName -> it.name.lowercase()
                                SortOption.BySize -> it.size
                                SortOption.ByBPM -> it.bpm ?: 0
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
                    error = "Failed to load tracks: ${e.message}"
                )
            }
        }
    }

    private fun loadTracksHighPerformance(context: Context, rootUri: Uri): List<Track> {
        val allTracks = mutableListOf<Track>()
        val queue = ArrayDeque<Uri>()
        val visited = mutableSetOf<Uri>()

        // For SAF Tree URIs, we need the documentId of the root
        val rootDocId = DocumentsContract.getTreeDocumentId(rootUri)
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, rootDocId)
        
        queue.add(rootDocUri)
        visited.add(rootDocUri)

        android.util.Log.d("WalkmanScanner", "Starting HP scan from: $rootDocUri")

        while (queue.isNotEmpty()) {
            val parentUri = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, DocumentsContract.getDocumentId(parentUri))
            
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

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx)
                    val mime = cursor.getString(mimeIdx)
                    val size = cursor.getLong(sizeIdx)
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
                    
                    if (allTracks.size > 20000) return allTracks
                }
            }
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
