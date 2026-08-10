# Walkmanager: Final Stability and Scanner Hardening

This plan addresses the persistent crash after selecting the Music folder and cleans up the "Code Analysis" warnings reported during the last commit.

## Proposed Changes

### 1. High-Performance Scanner Hardening
- **Index Safety**: Update `loadTracksHighPerformance` to use `getColumnIndexOrThrow` or verify that indices are not `-1`.
- **Null Safety**: Handle potential null values from `cursor.getString()` and `cursor.getLong()` more gracefully.
- **URI Construction**: Ensure `docId` is valid before calling `buildDocumentUriUsingTree`.

### 2. UI Error Transparency
- **Detailed Error State**: If a scan fails, show the **actual exception message** in the error banner instead of a generic "Failed to load" message. This will help diagnose the issue if it persists.

### 3. Code Cleanup (Lint Fixes)
- Remove unused imports.
- Fill the empty `onNewIntent` stub.
- Standardize private property names (no underscores).
- Use KTX extension functions where recommended (e.g., `prefs.edit { ... }`).
- Fix missing trailing commas and logic stubs.

### 4. Build System Integrity
- Perform a `./gradlew clean` before rebuilding to ensure no stale artifacts (like the `NoSuchMethodError` found in logs) remain in the APK.

## Verification Plan

### Automated Tests
- Build check: `./gradlew clean :app:assembleDebug`.

### Manual Verification
- Deploy to device.
- Select the Music folder.
- If it crashes, I will ask for the message in the "Error Banner" or a fresh Logcat capture.
