# WalkManager Polishing Guide

This guide covers all the changes for v1.1: package rename, BPM extraction, UI polish, and logo integration.

## What's New

### 1. **Package & Naming Updates**
- "Walkman Manager" → "WalkManager"
- Package remains: `com.horse.walkmanager`
- Updated throughout: app name, strings, file paths

### 2. **BPM Extraction** ✨
- Added `MediaMetadataRetriever` to extract BPM from ID3 tags
- BPM displays in track list (e.g., "120 BPM" or "—" if unavailable)
- New **Sort by BPM** option in filter chips
- BPM extraction runs asynchronously during track refresh (non-blocking)

### 3. **UI Polish**
- Tighter padding/spacing throughout
- Improved button sizing (36dp height instead of 32dp, better typography)
- Better error message dismissal (closer icon)
- Refined track item layout with BPM display
- Track items now show 2 lines: filename + metadata (size, BPM)
- Smoother spacing between controls
- Better visual hierarchy

### 4. **Icon/Logo Integration**
- Ready for custom waveform icon (replace default launcher icons)

---

## Step-by-Step Migration

### 1. Package Name (No Change Needed)

Your package name `com.horse.walkmanager` stays as-is. No refactoring needed!

### 2. Replace Kotlin Files

Replace these files in `src/main/java/com/rednova/walkmanager/`:

```
WalkmanModels.kt          ← Use WalkmanModels_v2.kt
WalkmanViewModel.kt       ← Use WalkmanViewModel_v2.kt
MainActivity.kt           ← Use MainActivity_v2.kt
```

### 3. Update Resources

**strings.xml** (`src/main/res/values/strings.xml`):
- Replace with `strings_v2.xml` content

**AndroidManifest.xml** (`src/main/AndroidManifest.xml`):
- Package attribute stays: `com.horse.walkmanager`
- Update `android:theme` to `@style/Theme.WalkManager` (if not already)

**build.gradle.kts** (`app/build.gradle.kts`):
- Namespace stays: `namespace = "com.horse.walkmanager"`
- ApplicationId stays: `applicationId = "com.horse.walkmanager"`
- Update version: versionCode = 2, versionName = "1.1"

### 4. Integrate Custom Logo

#### Generate Icon Assets

You'll need to convert your waveform PNG to Android icon assets. Two approaches:

**Option A: Android Studio Image Asset Tool (Easiest)**
1. In Android Studio: **File** → **New** → **Image Asset**
2. Select your `Walkmanager_icon.png`
3. Choose **Foreground Layer**, upload PNG
4. Studio auto-generates all densities (hdpi, xhdpi, xxhdpi, etc.)
5. Click **Finish**

**Option B: Manual - Use PNG directly**
If the icon tool doesn't work:
1. Place `Walkmanager_icon.png` in:
   - `src/main/res/mipmap-mdpi/`
   - `src/main/res/mipmap-hdpi/`
   - `src/main/res/mipmap-xhdpi/`
   - `src/main/res/mipmap-xxhdpi/`
   - `src/main/res/mipmap-xxxhdpi/`
2. Rename to `ic_launcher.png` in each (or update `ic_launcher.xml` reference)

#### Update Manifest
In `AndroidManifest.xml`, ensure the activity has:
```xml
android:icon="@mipmap/ic_launcher"
```

#### Update Theme (Optional - Advanced Branding)
If you want the waveform in your app's UI too:
1. Add to `src/main/res/drawable/waveform_icon.xml` or save PNG as `waveform.png`
2. Reference in code where needed

### 5. Test & Verify

```bash
# Build the app
./gradlew clean build

# Run on emulator/device
./gradlew installDebug

# Check:
# ✓ App name in launcher shows "WalkManager"
# ✓ Waveform icon displays as launcher icon
# ✓ Tracks show BPM values (or "—" if not available)
# ✓ BPM sort option works
# ✓ UI spacing looks cleaner
```

---

## Key Code Changes Summary

### WalkmanViewModel.kt (BPM Extraction)
```kotlin
private fun extractBPM(context: Context, trackUri: Uri): Int? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, trackUri)
        val bpmString = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_BEATS_PER_MINUTE
        )
        retriever.release()
        bpmString?.toIntOrNull()?.takeIf { it > 0 }
    } catch (e: Exception) {
        null
    }
}
```

### WalkmanModels.kt (BPM Display)
```kotlin
val bpmDisplay: String
    get() = bpm?.let { "$it BPM" } ?: "—"
```

### MainActivity.kt (UI Improvements)
- Track items now show size + BPM on second line
- BPM sort option enabled (3rd filter chip)
- Tighter padding: 10dp instead of 12dp on track items
- Better button spacing: 6dp gaps instead of 4dp
- Refined typography: `labelMedium` and `labelSmall` for consistency

---

## File Checklist

Before pushing to GitHub:

- [ ] All Kotlin files updated (3 files)
- [ ] strings.xml updated with new app name
- [ ] AndroidManifest.xml updated with new namespace
- [ ] build.gradle.kts updated (namespace, applicationId, version)
- [ ] Custom logo integrated as launcher icon
- [ ] No compilation errors: `./gradlew clean build`
- [ ] App runs and shows "WalkManager" as app name
- [ ] Tracks display BPM (where available)
- [ ] Sort by BPM works
- [ ] UI spacing looks polished

---

## Troubleshooting

**"Cannot find symbol" after package rename**
- Run **Build** → **Clean Build Folder**
- Invalidate caches: **File** → **Invalidate Caches...**

**BPM not showing for some tracks**
- Not all formats/files have BPM metadata. This is normal—it will show "—" gracefully.

**Logo doesn't show**
- Verify image is in correct `mipmap-*dpi` folders
- Check `AndroidManifest.xml` references correct `@mipmap/ic_launcher`
- Try Image Asset tool in Android Studio instead of manual approach

**Build fails with "namespace not found"**
- Ensure `build.gradle.kts` has correct namespace and applicationId
- Sync Gradle: **File** → **Sync Now**

---

## Next Steps

Once updated and working:

```bash
git add -A
git commit -m "Polish v1.1: rebrand to WalkManager, add BPM extraction, UI tightening"
git push origin main
```

Then on your Mac with Claude Code, you can further iterate:
- Add duration display
- Implement BPM caching (SQLite)
- Enhance track search/filter
- Build release APK for distribution

---

**Questions?** When you're back at your Mac in Claude Code, I can help with any integration issues!
