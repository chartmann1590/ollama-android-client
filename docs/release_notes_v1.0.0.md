# Ollama Android Client v1.0.0

## Initial Release

We're excited to announce the first release of Ollama Android Client! This app provides a modern and intuitive interface for chatting with AI models running on your Ollama server.

## Features

### Core Functionality
- 🤖 **Chat with AI Models**: Interact with various Ollama AI models through a clean chat interface
- 💬 **Message History**: Persistent chat history using Room database
- 🔄 **Real-time Streaming**: Receive AI responses streamed in real-time
- 📱 **Image Support**: Attach and send images in conversations with automatic compression
- ⚙️ **Configurable Settings**: Customize model parameters and server settings
- 🔐 **Secure Networking**: Support for both HTTP and HTTPS connections

### Technical Features
- 🎨 **Modern UI**: Built with Jetpack Compose and Material Design 3
- 🌙 **Dark Theme**: Full dark theme support
- 📊 **Analytics**: Firebase Analytics integration for usage insights
- 🐛 **Crash Reporting**: Firebase Crashlytics for error tracking
- ⚡ **Performance Monitoring**: Firebase Performance Monitoring
- 🗄️ **Local Database**: Room database for offline message storage
- 🔧 **Dependency Injection**: Hilt for clean architecture

## What's New

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
- **Image Compression & Storage Optimization**
  - Added `ImageCompressionHelper` utility class for automatic image compression
  - Images are automatically compressed to max 800x800 pixels and 200KB before database storage
  - Prevents SQLite CursorWindow overflow errors when storing large images
  - Compression uses JPEG format with quality optimization (75% quality, adjustable down to 50% if needed)
  - Base64 encoding applied after compression to minimize storage footprint

- **Database Performance & Reliability**
  - Implemented paginated message loading in `ChatRepository` to handle very large messages
  - Added fallback mechanisms for messages that exceed SQLite CursorWindow limits
  - New DAO methods: `getMessagesByThreadIdPaged`, `getMessagesByThreadIdPagedMetadata`, `getMessagesByThreadIdPagedBasic`
  - Messages are loaded one at a time with error recovery for oversized content
  - Metadata-only queries available as fallback when full message content is too large

- **Error Handling & Resilience**
  - Enhanced error handling in `ChatViewModel` for database operations
  - Added graceful error handling for `SQLiteBlobTooBigException` in message loading
  - Improved error recovery when loading messages with large content or images
  - Error handling in message Flow with proper exception catching and logging

- **Firebase Integration**
  - Added Firebase Performance Monitoring for app performance tracking
  - Enabled debug logging for Performance Monitoring in development

- **UI & User Experience**
  - Improved UI components and message display
  - Better error handling and user feedback
  - Enhanced chat message handling and database operations

### Fixed
- Fixed SQLite CursorWindow overflow errors when loading messages with large images
- Fixed database crashes when storing uncompressed images in chat messages
- Improved handling of oversized messages that exceed SQLite blob size limits
- Fixed message loading failures when individual messages exceed database row size limits

## Requirements

- Android 13 (API Level 33) or higher
- An Ollama server running and accessible
- Network connection to your Ollama server

## Technical Details

- **Minimum SDK**: 33 (Android 13)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM
- **UI Framework**: Jetpack Compose
- **Database**: Room
- **Networking**: Retrofit + OkHttp
- **Analytics**: Firebase Analytics, Crashlytics, Performance Monitoring

## Installation

Download the APK from the assets below and install it on your Android device.

## License

This project is licensed under the MIT License.

---

Made with ❤️ using Kotlin and Jetpack Compose

