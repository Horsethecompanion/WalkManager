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
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque
import kotlin.time.Duration.Companion.seconds

class WalkmanViewModel : ViewModel() {
    private val _state = MutableStateFlow(WalkmanManagerState())
    val state: StateFlow<WalkmanManagerState> = _state.asStateFlow()

    private val prefsName = "walkman_prefs"
    private val keyLastUri = "last_walkman_uri"
    
    private var bpmRepository: BpmRepository? = null

    private fun getBpmRepository(context: Context): BpmRepository {
        return bpmRepository ?: BpmRepository(BpmDatabase.getDatabase(context)).also { 
            bpmRepository = it 
        }
    }

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
                        if (track.bpm == null) {
                            val bpm = extractBpm(context, track.uri)
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
                                SortOption.ByBPM -> it.bpm ?: Int.MAX_VALUE
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

    fun syncBpm(context: Context) {
        val rootUri = _state.value.walkmanRootUri ?: return
        val repo = getBpmRepository(context)
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(isSyncingBpm = true, syncProgress = 0f)
                
                val tracksToSync = _state.value.tracks.filter { it.bpm == null }
                if (tracksToSync.isEmpty()) {
                    _state.value = _state.value.copy(isSyncingBpm = false)
                    return@launch
                }

                tracksToSync.forEachIndexed { index, track ->
                    // Attempt to parse artist and title from filename
                    val nameParts = track.name.split(" - ")
                    if (nameParts.size >= 2) {
                        val artist = nameParts[0].trim()
                        val title = nameParts[1].substringBeforeLast(".").trim()
                        
                        val bpm = repo.getBpm(artist, title)
                        if (bpm != null) {
                            if (writeBpmToFile(context, track.uri, bpm)) {
                                android.util.Log.d("WalkmanViewModel", "Synced BPM for ${track.name}: $bpm")
                            }
                        }
                    }
                    
                    _state.value = _state.value.copy(syncProgress = (index + 1).toFloat() / tracksToSync.size)
                }
                
                _state.value = _state.value.copy(isSyncingBpm = false)
                refreshTracks(context)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSyncingBpm = false, error = "BPM Sync failed: ${e.message}")
            }
        }
    }

    private fun writeBpmToFile(context: Context, trackUri: Uri, bpm: Int): Boolean {
        val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.mp3")
        val outputFile = File(context.cacheDir, "output_audio_${System.currentTimeMillis()}.mp3")
        return try {
            context.contentResolver.openInputStream(trackUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return false

            val mp3file = Mp3File(tempFile)
            val id3v2Tag = if (mp3file.hasId3v2Tag()) {
                mp3file.id3v2Tag
            } else {
                val tag = ID3v24Tag()
                mp3file.id3v2Tag = tag
                tag
            }
            id3v2Tag.bpm = bpm
            mp3file.save(outputFile.absolutePath)

            context.contentResolver.openOutputStream(trackUri, "rwt")?.use { output ->
                outputFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: return false
            true
        } catch (e: Exception) {
            android.util.Log.e("BpmSync", "Failed to write BPM to ${trackUri}: ${e.message}")
            false
        } finally {
            if (tempFile.exists()) tempFile.delete()
            if (outputFile.exists()) outputFile.delete()
        }
    }

    private fun extractBpm(context: Context, trackUri: Uri): Int? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, trackUri)
            
            // Try known BPM keys
            // 31 is the official METADATA_KEY_BEATS_PER_MINUTE in newer APIs
            // Some devices use generic technical metadata keys
            val keysToTry = listOf(31, 1000, 1001) 
            var bpm: Int? = null
            
            for (key in keysToTry) {
                val value = retriever.extractMetadata(key)
                bpm = value?.toIntOrNull()?.takeIf { it > 0 }
                if (bpm != null) break
            }
            
            retriever.release()
            bpm
        } catch (e: Exception) {
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

            while (queue.isNotEmpty()) {
                val parentUri = queue.removeFirst()
                val parentDocId = try { DocumentsContract.getDocumentId(parentUri) } catch (e: Exception) { continue }
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

                    if (idIdx == -1 || nameIdx == -1 || mimeIdx == -1 || sizeIdx == -1) return@use

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
                        if (allTracks.size > 20000) return allTracks
                    }
                }
            }
        } catch (e: Exception) {
            throw e
        }
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
