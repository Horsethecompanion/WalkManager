# Walkmanager: BPM Online Fetching and Local Caching

The goal is to automatically fetch missing BPM values from an online source (Option B), cache them locally to avoid repeated lookups, and embed them into the music files on the Walkman.

## Proposed Changes

### 1. Dependency Updates
- **[MODIFY] [build.gradle.kts](file:///F:/Walkmanager/app/build.gradle.kts)**: Add dependencies for Room (database), Retrofit/OkHttp (network), and jaudiotagger (metadata writing).

### 2. Local Caching with Room
- **[NEW] BpmDatabase.kt**: Define the Room database and DAO for caching BPM data.
- **[NEW] BpmEntity.kt**: Data class representing a cached BPM entry (Artist, Title, BPM).

### 3. Online BPM Fetching (Option B)
- **[NEW] BpmApiService.kt**: Retrofit interface for fetching music metadata (e.g., via MusicBrainz or a dedicated BPM API).
- **[NEW] BpmRepository.kt**: Orchestrates fetching from local cache first, then online if missing.

### 4. Metadata Writing (Embedding)
- **[MODIFY] [WalkmanViewModel.kt](file:///F:/Walkmanager/app/src/main/java/com/horse/walkmanager/WalkmanViewModel.kt)**:
    - Implement a "Sync & Fix" process that runs on device connection.
    - Integrate jaudiotagger to write BPM values to files using the "Copy-Modify-Replace" strategy.

### 5. UI Enhancements
- **[MODIFY] [MainActivity.kt](file:///F:/Walkmanager/app/src/main/java/com/horse/walkmanager/MainActivity.kt)**:
    - Add a "Sync BPM" button to trigger the fetching and embedding process manually.
    - Show a progress indicator specifically for the BPM synchronization process.

## User Review Required

> [!IMPORTANT]
> **API Key/Service**: Some online BPM databases require an API key. I will initially implement a placeholder or use a free service like MusicBrainz. If you have a specific service in mind (e.g., GetSongBPM), please provide the details.

> [!WARNING]
> **Performance**: Writing to files over USB (especially with the Copy-Modify-Replace strategy) can be slow for large libraries. The sync process will run in the background to avoid freezing the UI.

## Verification Plan

### Automated Tests
- Run unit tests for the BPM repository to verify cache/online logic.
- Verify the Room database migrations (if any).

### Manual Verification
1. Connect the Walkman and select the music folder.
2. Trigger the "Sync BPM" process.
3. Verify that tracks with missing BPM are updated in the list.
4. Verify that the BPM is persistent in the file tags by checking on another device.
5. Unplug and replug the device to verify that the local cache prevents redundant online lookups.
