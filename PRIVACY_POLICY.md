# Privacy Policy

**Last Updated:** June 16, 2026

## Introduction

Ollama Android Client ("we", "our", or "the app") is committed to protecting your privacy. This Privacy Policy explains how we collect, use, disclose, and safeguard your information when you use our mobile application.

## Information We Collect

### Information You Provide

- **Chat Messages**: The app stores chat messages locally on your device using Room database. These messages are stored on your device and are not transmitted to our servers unless you explicitly configure a remote server or enable Web sync.
- **Account Information**: If you create an account or sign in, Firebase Authentication stores account identifiers such as your email address, user ID, and sign-in provider.
- **Synced Chat Data**: If you enable Web sync, chat threads, messages, message metadata, and pending web requests are stored under your Firebase user account in Firebase Realtime Database so your signed-in devices and the Web Chat interface can sync.
- **Server Configuration**: Server URLs and connection settings you configure are stored locally on your device.

### Automatically Collected Information

- **Analytics and Diagnostics**: If analytics are enabled (such as Firebase Analytics, Crashlytics, and Performance Monitoring), we may collect:
  - App usage statistics and event logs
  - Device information (model, manufacturer, operating system version)
  - Crash logs, stack traces, and error reports
  - Performance trace data (e.g., app startup time, screen rendering speeds, network latency)
- **Push Notification and Sync Token**: If you allow notification permissions, Firebase Cloud Messaging (FCM) generates a unique device registration token to route push notifications and background synchronization triggers to your device. This token is unique to your specific app/device installation.

### Third-Party Services

This app uses the following third-party services that may collect information:

- **Firebase Analytics**: For app usage analytics, tracking interactions, and general statistics.
- **Firebase Crashlytics**: For crash reporting, exception tracking, and debugging stability issues.
- **Firebase Performance Monitoring**: For collecting performance metrics, trace logs, and latency statistics to improve the speed and responsiveness of the application.
- **Firebase Cloud Messaging (FCM)**: For delivering push notifications and initiating silent background sync triggers to synchronize web chat events.
- **Firebase Authentication**: For secure user registration, email/password or Google sign-in, and session management.
- **Firebase Realtime Database**: For real-time Web sync, storing and syncing chat threads, messages, and metadata across your authenticated devices.
- **Ollama Server**: Your chat messages are sent to the Ollama server you configure. We do not control or have access to data sent to your Ollama server.

## How We Use Your Information

We use the information we collect to:

- Provide and maintain the app's functionality
- Authenticate users who choose to create an account or sign in
- Sync chats across signed-in app sessions and the Web Chat interface when Web sync is enabled
- Deliver notifications and updates via Cloud Messaging
- Diagnose and fix technical issues, crashes, and performance issues
- Improve user experience, loading speed, and app performance
- Analyze app usage patterns (if analytics are enabled)

## Data Storage

- **Local Storage**: Chat messages, threads, and app settings are stored locally on your device using Room database.
- **Optional Cloud Sync**: If Web sync is enabled, synced chats are stored in Firebase Realtime Database under your authenticated user ID.
- **No Cloud Storage When Sync Is Off**: We do not store your chat messages in Firebase Realtime Database when Web sync is disabled.
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

