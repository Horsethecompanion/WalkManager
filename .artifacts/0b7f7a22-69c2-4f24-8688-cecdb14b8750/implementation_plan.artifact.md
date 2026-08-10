# Walkmanager: Auto-Restore Reliability and Polling

The app now launches automatically but often fails to restore the music folder immediately, requiring manual selection. This is likely because the Walkman takes a few seconds to fully mount its storage after the USB connection is established.

This plan adds a robust polling mechanism to wait for the device to become ready before giving up.

## Proposed Changes

### 1. Robust Polling in `WalkmanViewModel`
- **Retry Logic**: Update `tryAutoRestore` to attempt to access the persisted URI up to 5 times, with a 2-second delay between attempts (10 seconds total).
- **Existence Check**: Specifically handle the case where the URI is "found" but `doc.exists()` or `doc.canRead()` returns false, which is typical during the device's mounting phase.

### 2. UI Feedback
- **"Waiting for Device" State**: Add a new state in `WalkmanManagerState` to track if we are specifically waiting for a known device to mount.
- **Loading Screen Update**: Display "Waiting for Walkman to mount..." during the polling phase so the user knows the app is actively working to restore the session.

### 3. Permission Management
- Ensure `persistedUriPermissions` are handled correctly even if the underlying volume is temporarily missing.

## Verification Plan

### Automated Tests
- Build check: `./gradlew :app:assembleDebug`.

### Manual Verification
- **Test Auto-Restore**: Plug in the Walkman. The app should launch and show "Waiting for Walkman to mount...". Within a few seconds, it should automatically load the music library without manual navigation.
