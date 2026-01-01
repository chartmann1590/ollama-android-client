# Changelog

All notable changes to Ollama Android Client will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- Enhanced error handling
- Offline mode support
- Export chat history
- Custom themes

## [1.0.0] - 2024-12-31

### Added
- Initial release of Ollama Android Client
- Chat interface with AI models
- Support for multiple chat threads
- Message history persistence using Room database
- Real-time streaming responses from Ollama
- Image attachment support in conversations
- Image compression and optimization for database storage
- Model management (list, pull, delete models)
- Server configuration management
- Multiple server support
- Settings screen for app configuration
- Firebase Analytics integration
- Firebase Crashlytics integration
- Firebase Performance Monitoring
- Material Design 3 UI with Jetpack Compose
- Dark theme support
- Navigation using Jetpack Navigation Compose
- Dependency injection with Hilt
- Network layer with Retrofit and OkHttp
- Local database with Room
- Coroutines for asynchronous operations

### Improved
- Enhanced chat message handling and database operations
- Optimized image storage to prevent SQLite CursorWindow overflow
- Improved UI components and message display
- Better error handling and user feedback

### Technical Details
- Minimum SDK: 33 (Android 13)
- Target SDK: 34 (Android 14)
- Compile SDK: 34
- Kotlin version: Latest stable
- Compose BOM: 2024.02.00
- Architecture: MVVM

---

## Version History

- **1.0.0** (2024-12-31): Initial release

---

## Types of Changes

- `Added` for new features
- `Changed` for changes in existing functionality
- `Deprecated` for soon-to-be removed features
- `Removed` for now removed features
- `Fixed` for any bug fixes
- `Security` for vulnerability fixes

---

For more details, see the [Git commit history](http://10.0.0.129:3000/charles/ollama-android-client/commits/master).

