# Ollama Android Client

An Android application for interacting with Ollama AI models. Built with Jetpack Compose, this app provides a modern and intuitive interface for chatting with AI models running on your Ollama server.

## Features

- 🤖 **Chat with AI Models**: Interact with various Ollama AI models through a clean chat interface
- 💬 **Message History**: Persistent chat history using Room database
- 🎨 **Modern UI**: Built with Jetpack Compose and Material Design 3
- 🔄 **Real-time Streaming**: Receive AI responses streamed in real-time
- 📱 **Image Support**: Attach and send images in conversations
- ⚙️ **Configurable Settings**: Customize model parameters and server settings
- 🔐 **Secure Networking**: Support for both HTTP and HTTPS connections

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Dependency Injection**: Hilt
- **Networking**: Retrofit + OkHttp
- **Database**: Room
- **Async Operations**: Kotlin Coroutines
- **Analytics**: Firebase Analytics
- **Crash Reporting**: Firebase Crashlytics

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17 or later
- Android SDK 33 (API Level 33) or higher
- Gradle 8.0 or later
- An Ollama server running and accessible

## Setup

1. **Clone the repository**
   ```bash
   git clone http://10.0.0.129:3000/charles/ollama-android-client.git
   cd ollama-android-client
   ```

2. **Configure local.properties**
   Create a `local.properties` file in the root directory with your Android SDK path:
   ```properties
   sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
   ```

3. **Configure Firebase (Optional)**
   If you want to use Firebase features:
   - Add your `google-services.json` file to the `app/` directory
   - Configure Firebase in the Firebase Console

4. **Build the project**
   ```bash
   ./gradlew build
   ```

5. **Run the app**
   ```bash
   ./gradlew installDebug
   ```
   Or use Android Studio to build and run the app.

## Configuration

### Server Configuration

The app needs to be configured to connect to your Ollama server. You can configure the server URL in the app settings.

Default server URL: `http://localhost:11434`

### Network Security

The app includes network security configuration to allow cleartext traffic for local development. For production, ensure your server uses HTTPS.

## Project Structure

```
app/
├── src/
│   └── main/
│       ├── java/com/charles/ollama/client/
│       ├── data/           # Data layer (API, database, repositories)
│       ├── domain/        # Domain models and use cases
│       ├── ui/            # UI layer (Compose screens, ViewModels)
│       └── di/            # Dependency injection modules
│       ├── AndroidManifest.xml
│       └── res/           # Resources (layouts, drawables, etc.)
├── build.gradle.kts
└── google-services.json   # Firebase configuration (if using Firebase)
```

## Building

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

The APK will be generated in `app/build/outputs/apk/`

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- [Ollama](https://ollama.ai/) for providing the AI model server
- Jetpack Compose team for the amazing UI framework
- All the open-source libraries used in this project

## Support

For issues, questions, or contributions, please open an issue on the repository.

## Version

Current Version: 1.0 (versionCode: 1)

---

Made with ❤️ using Kotlin and Jetpack Compose

