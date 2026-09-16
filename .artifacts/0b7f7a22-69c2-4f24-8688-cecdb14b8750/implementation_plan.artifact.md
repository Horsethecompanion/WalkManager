# Walkmanager: BPM Fetching Fix and UI Cleanup

The user reported that the BPM data is not being picked up and that there are confusing duplicate icons in the top bar. This plan fixes the placeholder logic in the BPM repository and cleans up the UI.

## User Review Required

> [!NOTE]
> I will be implementing a real online lookup using the **MusicBrainz** API. It is a free service, but please note that not every track in the world has BPM data available there.

## Proposed Changes

### 1. BPM Acquisition Fix
- **[MODIFY] [BpmApiService.kt](file:///F:/Walkmanager/app/src/main/java/com/horse/walkmanager/BpmApiService.kt)**: Update to use MusicBrainz search endpoints.
- **[MODIFY] [BpmRepository.kt](file:///F:/Walkmanager/app/src/main/java/com/horse/walkmanager/BpmRepository.kt)**: Implement the actual network call to fetch BPM data when the local cache is empty.
- **[MODIFY] [WalkmanViewModel.kt](file:///F:/Walkmanager/app/src/main/java/com/horse/walkmanager/WalkmanViewModel.kt)**:
    - Improve `extractBpm` to try multiple known metadata keys for different Android versions.
    - Ensure the "Sync BPM" process correctly updates the local cache after a successful online fetch.

### 2. UI Cleanup and Fixes
- **[MODIFY] [MainActivity.kt](file:///F:/Walkmanager/app/src/main/java/com/horse/walkmanager/MainActivity.kt)**:
    - **Merge/Distinguish Icons**: Change the "Sync BPM" icon to `CloudDownload` to distinguish it from the "Refresh" (library scan) icon.
    - **Fix Selection Logic**: Update `TrackItem` to use the file URI as a unique key for selection and deletion, fixing a potential bug where the wrong file might be selected.
    - **Syntax Cleanup**: Fix a minor syntax error in the Sort options bar (`.0horizontalScroll`).

### 3. Networking Configuration
- **[MODIFY] [AndroidManifest.xml](file:///F:/Walkmanager/app/src/main/AndroidManifest.xml)**: Add `android.permission.INTERNET` if missing.

## Verification Plan

### Automated Tests
- Verify build: `./gradlew :app:assembleDebug`.

### Manual Verification
1. Connect the Walkman.
2. Tap the new **Cloud icon** (Fetch BPM).
3. Verify that the app now attempts to fetch data online and displays it in the list.
4. Verify that selecting a track correctly highlights only that track.
