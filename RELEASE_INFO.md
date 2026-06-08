# Release v1.0.0 - Ready for Upload

## APK Location
- **File**: `releases/ollama-android-client-v1.0.0.apk`
- **Build Type**: Release (with analytics enabled)
- **Version**: 1.0.0 (versionCode: 1)

## Release Notes
See `docs/release_notes_v1.0.0.md` for complete release notes.

## Quick Summary
- Initial release of Ollama Android Client
- Firebase Analytics, Crashlytics, and Performance Monitoring enabled
- Ad integration (banner and interstitial ads)
- Performance monitoring and optimization
- Image compression and storage optimization
- Modern UI with Jetpack Compose and Material Design 3

## To Create Release on GitHub

1. Go to your GitHub repository: https://github.com/chartmann1590/ollama-android-client
2. Navigate to **Releases** section (under Releases on the right side of the main page)
3. Click **Draft a new release**
4. Fill in:
   - **Tag**: v1.0.0 (already exists)
   - **Release title**: Ollama Android Client v1.0.0
   - **Description**: Copy from `docs/release_notes_v1.0.0.md`
5. Upload the APK: `releases/ollama-android-client-v1.0.0.apk`
6. Click **Publish release**

## Alternative: Using GitHub CLI

```bash
gh release create v1.0.0 releases/ollama-android-client-v1.0.0.apk \
  --title "Ollama Android Client v1.0.0" \
  --notes-file docs/release_notes_v1.0.0.md
```

## Build Information
- **Build Date**: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
- **APK Size**: See file properties
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Analytics**: Firebase Analytics enabled
- **Crash Reporting**: Firebase Crashlytics enabled
- **Performance**: Firebase Performance Monitoring enabled

