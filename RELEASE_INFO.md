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

## To Create Release on GitLab

1. Go to your GitLab repository: http://10.0.0.129:3000/charles/ollama-android-client
2. Navigate to **Releases** section
3. Click **New Release**
4. Fill in:
   - **Tag**: v1.0.0 (already exists)
   - **Release title**: Ollama Android Client v1.0.0
   - **Release notes**: Copy from `docs/release_notes_v1.0.0.md`
5. Upload the APK: `releases/ollama-android-client-v1.0.0.apk`
6. Click **Create Release**

## Alternative: Using GitLab API

```bash
curl --request POST \
  --header "PRIVATE-TOKEN: <your-token>" \
  --form "name=v1.0.0" \
  --form "tag_name=v1.0.0" \
  --form "description=$(cat docs/release_notes_v1.0.0.md)" \
  --form "assets[links][][name]=APK" \
  --form "assets[links][][url]=<upload-url>" \
  "http://10.0.0.129:3000/api/v4/projects/charles%2Follama-android-client/releases"
```

## Build Information
- **Build Date**: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
- **APK Size**: See file properties
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Analytics**: Firebase Analytics enabled
- **Crash Reporting**: Firebase Crashlytics enabled
- **Performance**: Firebase Performance Monitoring enabled

