# Ollama Android Client

[![Android CI](https://github.com/chartmann1590/ollama-android-client/actions/workflows/android-ci.yml/badge.svg)](https://github.com/chartmann1590/ollama-android-client/actions/workflows/android-ci.yml)

An Android application for interacting with Ollama AI models — remote or fully on-device. Built with Jetpack Compose, this app provides a modern and intuitive interface for chatting with AI models running on your Ollama server **or on your phone itself** via Google's LiteRT-LM runtime.

## Features

- 🤖 **Chat with AI Models**: Interact with various Ollama AI models through a clean chat interface
- 📲 **On-device inference (LiteRT-LM)**: Download and run Gemma 4, Gemma 3, Qwen 3, DeepSeek R1 Distill, and Phi-4 Mini directly on your phone — no server, no network, no data leaving the device
- 💬 **Message History**: Persistent chat history using Room database
- 🎨 **Modern UI**: Built with Jetpack Compose and Material Design 3
- 🔄 **Real-time Streaming**: Receive AI responses streamed in real-time (remote *and* on-device)
- 📱 **Image Support**: Attach and send images in conversations with vision-capable remote models
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

### On-device models (LiteRT-LM)

No Ollama server? You can also run models fully on-device using Google's [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) runtime:

1. Open the **Servers** screen and tap **Add LiteRT (on-device)** — this creates a local backend using the `litert-local://` sentinel URL.
2. Open the **Models** screen (Download icon in the top bar) and pick any bundle from the built-in catalog:

   | Model | Approx. size |
   |---|---|
   | Gemma 3 270M IT (q8) | ~304 MB |
   | Gemma 3 1B IT (int4) | ~584 MB |
   | Qwen 3 0.6B | ~614 MB |
   | Qwen 2.5 1.5B Instruct (q8) | ~1.6 GB |
   | DeepSeek R1 Distill Qwen 1.5B (q8) | ~1.83 GB |
   | Gemma 4 E2B (LiteRT) | ~2.58 GB |
   | Gemma 4 E4B (LiteRT) | ~3.65 GB |
   | Phi-4 Mini Instruct (q8) | ~3.91 GB |

   All bundles are pulled from the public [`litert-community`](https://huggingface.co/litert-community) Hugging Face organization as `.litertlm` files. Downloads support resume (`Range` requests with a `.part` file), free-space pre-checks, and optional Hugging Face tokens for gated repos via **Settings → Hugging Face token**.
3. Start a new chat thread and pick the downloaded model. Inference runs via `com.google.ai.edge.litertlm.Engine` on the CPU backend — no traffic leaves the device.

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

## Documentation

- [**Contributing Guidelines**](CONTRIBUTING.md) - How to contribute to this project
- [**Code of Conduct**](CODE_OF_CONDUCT.md) - Community guidelines and standards
- [**Privacy Policy**](PRIVACY_POLICY.md) - How we handle your data and privacy
- [**Security Policy**](SECURITY.md) - How to report security vulnerabilities
- [**Changelog**](CHANGELOG.md) - History of changes and updates
- [**License**](LICENSE) - MIT License

## Contributing

We welcome contributions! Please read our [Contributing Guidelines](CONTRIBUTING.md) and [Code of Conduct](CODE_OF_CONDUCT.md) before contributing.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

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

