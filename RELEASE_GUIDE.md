# Play Store Release Guide

## Release Bundle Created Successfully ✅

Your Android App Bundle (AAB) has been built with analytics enabled:
- **Location**: `app/build/outputs/bundle/release/app-release.aab`
- **Size**: ~15 MB
- **Version**: 1.0 (versionCode: 1)

## ⚠️ Important: Release Signing Required

**The current bundle is signed with the debug keystore**, which cannot be used for Play Store submission. You need to set up a release keystore before uploading to Google Play.

### Setting Up Release Signing

1. **Generate a release keystore** (if you don't have one):
   ```bash
   keytool -genkey -v -keystore app/release.keystore -alias ollama-release -keyalg RSA -keysize 2048 -validity 10000
   ```
   
   You'll be prompted for:
   - Keystore password (save this securely!)
   - Key password (can be same as keystore password)
   - Your name, organization, etc.

2. **Create `keystore.properties` file** in the project root:
   ```properties
   storeFile=app/release.keystore
   storePassword=your_store_password
   keyAlias=ollama-release
   keyPassword=your_key_password
   ```

3. **Rebuild the bundle**:
   ```bash
   .\gradlew.bat bundleRelease
   ```

   The new bundle will be automatically signed with your release keystore.

## Analytics Configuration

The release bundle includes:
- ✅ **Firebase Analytics** - Enabled and configured
- ✅ **Firebase Crashlytics** - Error tracking enabled
- ✅ **Firebase Performance Monitoring** - Performance metrics enabled
- ✅ **ProGuard/R8** - Minification and code obfuscation enabled
- ✅ **Resource Shrinking** - Unused resources removed

All Firebase services are properly configured with ProGuard rules to ensure analytics work correctly in the release build.

## Building for Play Store

Once you have your release keystore configured:

1. **Build the release bundle**:
   ```bash
   .\gradlew.bat bundleRelease
   ```

2. **Upload to Play Console**:
   - Go to Google Play Console
   - Navigate to your app → Release → Production (or Internal Testing)
   - Create a new release
   - Upload `app/build/outputs/bundle/release/app-release.aab`

3. **Verify the bundle**:
   - The Play Console will validate your bundle
   - Ensure all required information is provided
   - Complete the release checklist

## Keystore Security

⚠️ **IMPORTANT**: 
- Keep your `keystore.properties` file secure and **never commit it to version control** (already in `.gitignore`)
- Back up your keystore file (`app/release.keystore`) in a secure location
- If you lose your keystore, you cannot update your app on Play Store
- Consider using Google Play App Signing for additional security

## Next Steps

1. Set up release keystore (if not done)
2. Rebuild bundle with release signing
3. Test the bundle locally (optional, using `bundletool`)
4. Upload to Google Play Console
5. Complete store listing and release checklist

---

**Note**: The current bundle (`app-release.aab`) is signed with debug keys and is suitable for testing only. You must rebuild with a release keystore before Play Store submission.

