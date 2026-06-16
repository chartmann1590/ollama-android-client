# Ollama Android Client

[![Android CI](https://github.com/chartmann1590/ollama-android-client/actions/workflows/android-ci.yml/badge.svg)](https://github.com/chartmann1590/ollama-android-client/actions/workflows/android-ci.yml)
[![Get it on Google Play](https://img.shields.io/badge/Google%20Play-Download-34A853?logo=googleplay&logoColor=white)](https://play.google.com/store/apps/details?id=com.charles.ollama.client.play&pcampaignid=web_share)
[![Website](https://img.shields.io/badge/Website-GitHub%20Pages-6366f1?logo=github&logoColor=white)](https://chartmann1590.github.io/ollama-android-client/)
[![Web Chat](https://img.shields.io/badge/Web%20Chat-Live-8b5cf6?logo=firebase&logoColor=white)](https://chartmann1590.github.io/ollama-android-client/chat.html)

> 📲 **Available on Google Play**: [Install Ollama AI Chat](https://play.google.com/store/apps/details?id=com.charles.ollama.client.play&pcampaignid=web_share)
>
> 💻 **Web Chat**: [Access your chats from any browser](https://chartmann1590.github.io/ollama-android-client/chat.html) — real-time 2-way sync with your phone via Firebase

An Android application for interacting with Ollama AI models — remote or fully on-device. Built with Jetpack Compose, this app provides a modern and intuitive interface for chatting with AI models running on your Ollama server **or on your phone itself** via Google's LiteRT-LM runtime. Your chats sync in real time to a **web interface** accessible from any browser.

## Features

### New: Web Chat & 2-Way Sync
- 🌐 **Web Chat interface**: Access all your conversations from any browser at [chat.html](https://chartmann1590.github.io/ollama-android-client/chat.html) — no app install needed on desktop
- 🔄 **Real-time 2-way sync**: Messages written on the web are routed to your phone for inference; responses stream back to the browser within seconds
- 📱 **Phone presence indicator**: The web UI shows whether your phone is online and ready to respond
- 📬 **Offline message queue**: Type messages on the web even when your phone is offline — they dispatch automatically when the phone reconnects
- 🤖 **Model selector**: The web UI shows only the models actually installed on your phone (Ollama + LiteRT), pre-selects the right model per thread, and includes it in every request
- 🔐 **Firebase Auth**: Sign in with Google or email/password — the same account links your phone and browser sessions

### AI Inference
- 🤖 **Chat with AI Models**: Interact with various Ollama AI models through a clean chat interface
- 📲 **On-device inference (LiteRT-LM)**: Download and run Gemma 4, Gemma 3, Qwen 3, DeepSeek R1 Distill, and Phi-4 Mini directly on your phone — no server, no network, no data leaving the device
- 🔄 **Real-time Streaming**: Receive AI responses streamed in real-time (remote *and* on-device)
- ⏹️ **Stop generation**: Halt a streaming reply mid-stream — the partial response is kept

### Conversation Management
- 💬 **Message History**: Persistent chat history using Room database
- 🏷️ **Labels & folders**: Organize threads with labels and filter the list by them, on top of pin/archive
- 📌 **Pin and archive threads**: Keep active chats focused without deleting older conversations
- 💬 **Per-message actions**: Copy, share, delete, edit-and-resend, and regenerate directly from message menus
- 📤 **Export / share chats**: Share threads (or individual messages) as Markdown/text logs
- 🔎 **In-thread search**: Search inside the current conversation with next/previous match navigation
- 🌍 **Global message search**: Search across every conversation by message content and jump straight to the result

### Input & Accessibility
- 🎙️ **Voice input**: Use Android system speech-to-text to dictate directly into the composer
- 🔊 **Read aloud replies**: Play assistant responses using Android Text-to-Speech for accessibility
- 📱 **Image Support**: Attach and send images in conversations with vision-capable remote models

### Configuration & Stats
- 🎚️ **Per-thread model parameters**: Tune temperature, top_p, top_k, context length (num_ctx), and seed per chat (remote Ollama)
- 📊 **Token-speed stats**: See tokens/sec, token counts, and response time under each remote reply
- ℹ️ **Model details**: Inspect a model's parameters, quantization, template, license, and modelfile via `/api/show`
- 📱 **Launcher shortcut**: Resume your most recent thread via a dynamic home launcher shortcut
- 🎨 **Modern UI**: Built with Jetpack Compose and Material Design 3
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
- **Auth**: Firebase Authentication (Google + email/password)
- **Sync**: Firebase Realtime Database (2-way web-phone chat sync)

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 21 or later
- Android SDK 35 (API Level 35) or higher
- Gradle 8.7 or later
- An Ollama server running and accessible

## Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/chartmann1590/ollama-android-client.git
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
   ./gradlew :app:installPlayDebug
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

### Web Chat & Firebase Sync

Enable real-time 2-way sync between your phone and browser:

1. **Enable Web Sync** in **Settings → Web Sync** on the app. Sign in with Google or email/password.
2. Open [chat.html](https://chartmann1590.github.io/ollama-android-client/chat.html) in any browser and sign in with the same account.
3. Your existing threads appear immediately. Send a message — the phone claims it, runs inference, and the response streams back to the browser.

**How it works (Firebase Realtime Database paths):**
```
/users/{uid}/
  threads/{syncId}/          ← phone syncs up; web reads in real time
  messages/{syncId}/{msgId}/ ← phone syncs up; web reads in real time
  webRequests/{requestId}/   ← web writes; phone claims → runs → marks complete
  devices/{deviceId}/        ← phone heartbeats every 60s; web shows online indicator
  availableModels/           ← phone publishes installed model list; web populates dropdown
```

The web UI requires no backend — it's a static GitHub Pages site that talks directly to Firebase.

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
./gradlew :app:assemblePlayDebug
```

### Release Build
```bash
./gradlew :app:assemblePlayRelease
```

The APK will be generated in `app/build/outputs/apk/play/`

## Documentation

- [**Contributing Guidelines**](CONTRIBUTING.md) - How to contribute to this project
- [**Code of Conduct**](CODE_OF_CONDUCT.md) - Community guidelines and standards
- [**Privacy Policy**](PRIVACY_POLICY.md) - How we handle your data and privacy
- [**Firebase Web Sync**](docs/firebase-web-sync.md) - Auth, Realtime Database schema, rules, and GitHub Pages web contract
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

Current Version: 1.2 (versionCode: 4+CI run number)

---

Made with ❤️ using Kotlin and Jetpack Compose
