# Walkman Manager

A minimal, focused Android app for managing music on your Sony NWZ-W470 (or similar USB mass storage) Walkman.

## Features

**MVP (Current):**
- 🎵 Browse audio files on your Walkman
- ➕ Transfer music from phone to Walkman  
- 🗑️ Delete tracks (single or bulk)
- 📊 Sort by name or file size
- 🔄 Refresh track list on demand
- 📱 Clean, simple Material 3 UI

**Future (Phase 2):**
- 🎼 Extract and sort by BPM
- 📁 Multi-directory support
- 🎶 ID3 tag editing
- 🔍 Search/filter tracks

## Setup

### Requirements
- Android 8.0+ (API 26)
- A USB-C hub or adapter to connect Walkman to phone
- Read/Write permissions for file access

### Build
```bash
# Clone the project
git clone <repo-url>
cd WalkmanManager

# Build and run
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Project Structure
```
├── MainActivity.kt        # Jetpack Compose UI
├── WalkmanViewModel.kt    # State & file operations  
├── WalkmanModels.kt       # Data classes
├── build.gradle.kts       # Dependencies
└── AndroidManifest.xml    # Permissions & USB filter
```

## How to Use

1. **Connect Walkman**: Plug in via USB-C hub
2. **Select Root**: Tap "Select Walkman Folder" → navigate to the root or MUSIC folder
3. **Browse**: App scans for all audio files recursively
4. **Manage**:
   - **Add**: Tap "Add Tracks" → select from phone
   - **Delete**: Long-press a track or bulk-select and delete
   - **Sort**: Toggle between Name/Size sort
5. **Disconnect**: Remove USB when done

## Technical Notes

- **Storage Access Framework (SAF)**: Uses `DocumentFile` API for cross-platform file access (no rooting required)
- **USB Integration**: Listens for mass storage device attachment via Intent filter
- **Coroutines**: Async file I/O to prevent UI blocking
- **Compose**: Modern, reactive UI with Material Design 3

## BPM Feature (Roadmap)

When implemented, BPM extraction will:
- Use `MediaMetadataRetriever` to read ID3 tags
- Cache BPM locally (SQLite/SharedPreferences)
- Only re-scan on new file additions
- Display/sort in the track list

## Permissions

- `READ_EXTERNAL_STORAGE` - Access phone files for transfer
- `WRITE_EXTERNAL_STORAGE` - Write to Walkman
- `USB_PERMISSION` - USB device access (requested at runtime on Android 12+)

## Troubleshooting

**"Failed to load tracks"**
- Ensure you've selected the correct root folder on the Walkman
- Try selecting the parent directory instead

**Files won't transfer**
- Verify the Walkman is mounted as read-write
- Check that the MUSIC folder has write permissions

**App crashes on file delete**
- Some file systems may not support deletes; try via computer first

## License

MIT - Feel free to fork and modify!
