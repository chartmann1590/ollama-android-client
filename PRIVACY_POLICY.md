# Privacy Policy

**Last Updated:** [Date]

## Introduction

Ollama Android Client ("we", "our", or "the app") is committed to protecting your privacy. This Privacy Policy explains how we collect, use, disclose, and safeguard your information when you use our mobile application.

## Information We Collect

### Information You Provide

- **Chat Messages**: The app stores chat messages locally on your device using Room database. These messages are stored on your device and are not transmitted to our servers unless you explicitly configure a remote server or enable Web sync.
- **Account Information**: If you create an account or sign in, Firebase Authentication stores account identifiers such as your email address, user ID, and sign-in provider.
- **Synced Chat Data**: If you enable Web sync, chat threads, messages, message metadata, and pending web requests are stored under your Firebase user account in Firebase Realtime Database so your signed-in devices and future web interface can sync.
- **Server Configuration**: Server URLs and connection settings you configure are stored locally on your device.

### Automatically Collected Information

- **Analytics Data**: If Firebase Analytics is enabled, we may collect:
  - App usage statistics
  - Device information (model, OS version)
  - Crash reports and error logs
  - Performance metrics

### Third-Party Services

This app uses the following third-party services that may collect information:

- **Firebase Analytics**: For app usage analytics and crash reporting
- **Firebase Crashlytics**: For crash reporting and error tracking
- **Firebase Authentication**: For optional email/password and Google sign-in
- **Firebase Realtime Database**: For optional Web sync when enabled by the user
- **Ollama Server**: Your chat messages are sent to the Ollama server you configure. We do not control or have access to data sent to your Ollama server.

## How We Use Your Information

We use the information we collect to:

- Provide and maintain the app's functionality
- Authenticate users who choose to create an account or sign in
- Sync chats across signed-in app sessions and the future web interface when Web sync is enabled
- Improve user experience and app performance
- Diagnose and fix technical issues
- Analyze app usage patterns (if analytics enabled)

## Data Storage

- **Local Storage**: Chat messages, threads, and app settings are stored locally on your device using Room database.
- **Optional Cloud Sync**: If Web sync is enabled, synced chats are stored in Firebase Realtime Database under your authenticated user ID.
- **No Cloud Storage When Sync Is Off**: We do not store your chat messages in Firebase Realtime Database when Web sync is off.
- **Third-Party Servers**: When you use the app with an Ollama server, your messages are sent to that server. Please review your Ollama server's privacy policy.

## Data Security

We implement appropriate technical and organizational measures to protect your information:

- Local data is encrypted using Android's built-in security features
- Network communications use HTTPS when available
- Firebase services use industry-standard encryption
- Firebase Realtime Database security rules restrict synced chat data to the signed-in user ID

## Your Rights

You have the right to:

- **Access**: View your locally stored data through the app
- **Delete**: Delete chat threads and messages directly from the app
- **Disable Sync**: Turn off Web sync in Settings to stop cloud synchronization
- **Sign Out**: Sign out from Firebase Authentication in Settings
- **Opt-out**: Disable Firebase Analytics in your device settings
- **Uninstall**: Remove all app data by uninstalling the app

## Children's Privacy

Our app is not intended for children under the age of 13. We do not knowingly collect personal information from children under 13.

## Changes to This Privacy Policy

We may update our Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy on this page and updating the "Last Updated" date.

## Contact Us

If you have any questions about this Privacy Policy, please contact us:

- **Repository**: [https://github.com/chartmann1590/ollama-android-client](https://github.com/chartmann1590/ollama-android-client)
- **Issues**: Open an issue on the repository

## Compliance

This app complies with:
- General Data Protection Regulation (GDPR)
- California Consumer Privacy Act (CCPA)
- Android Privacy Guidelines

---

**Note**: This privacy policy applies only to the Ollama Android Client app. When you connect to an Ollama server, that server's privacy policy applies to data processed by that server.

