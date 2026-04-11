# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Gradle wrapper commands (run from repo root; `gradlew.bat` on Windows, `./gradlew` elsewhere):

- `./gradlew assembleDebug` — build debug APK (output under `app/build/outputs/apk/debug/`)
- `./gradlew assembleRelease` — release build; requires `keystore.properties` at repo root or it falls back to debug signing
- `./gradlew installDebug` — install on a connected device/emulator
- `./gradlew lint` / `./gradlew lintDebug` — Android lint
- `./gradlew test` — unit tests (`testDebugUnitTest`, `testReleaseUnitTest`)
- `./gradlew :app:testDebugUnitTest --tests "com.charles.ollama.client.SomeTest"` — run a single unit test
- `./gradlew connectedDebugAndroidTest` — instrumented tests (requires device/emulator)

Toolchain: JDK 21 (`jvmToolchain(21)`, `VERSION_21` source/target), Kotlin with Compose compiler plugin, KAPT for Hilt, KSP for Room. `compileSdk = 35`, `minSdk = 24`, `targetSdk = 35`.

Firebase: `app/google-services.json` is required for a full build (Analytics, Crashlytics, Messaging, Remote Config, Performance plugins are applied unconditionally in `app/build.gradle.kts`).

## Architecture

Single-module Android app (`:app`, package `com.charles.ollama.client`) using MVVM + Clean-ish layering with Hilt DI. Entry points: `OllamaApplication` (`@HiltAndroidApp`) and `MainActivity` (`@AndroidEntryPoint`) which hosts a single Compose `NavGraph`.

Layer layout under `app/src/main/java/com/charles/ollama/client/`:

- `data/api/` — Retrofit `OllamaApi` + `OllamaApiFactory` (per-server base-URL clients) and `OllamaStreamingService` (raw OkHttp `callbackFlow` for NDJSON streaming from `/api/chat`). DTOs in `data/api/dto/`.
- `data/database/` — Room `OllamaDatabase` (version 7), entities (`ChatThreadEntity`, `ChatMessageEntity`, `ServerConfigEntity`, `InstalledLitertModelEntity`), DAOs, `Migrations.kt`, and `converter/StringListConverter`. **Bump the version and add a migration in `Migrations.kt` when changing any entity.**
- `data/litert/` — on-device inference via Google AI Edge LiteRT-LM (`litertlm-android`). `LiteRtChatService` mirrors the streaming API surface; `LocalModelCatalog` + `ModelDownloadManager` handle `.litertlm` model files. `LitertConstants.LOCAL_BASE_URL = "litert-local://"` is the sentinel stored in `ServerConfigEntity.baseUrl` to flag the on-device backend, and `ServerBackend { OLLAMA, LITERT_LOCAL }` is the discriminator.
- `data/repository/` — `ChatRepository`, `ModelRepository`, `ServerRepository`. `ChatRepository` is the key integration point: it routes each send to either `OllamaStreamingService` (remote Ollama) or `LiteRtChatService` (on-device) based on the thread's server backend, while persisting threads/messages via Room and instrumenting calls with `util.PerformanceMonitor`.
- `domain/model/` + `domain/usecase/` — plain Kotlin models and thin use cases (`SendChatMessageUseCase`, `GetModelsUseCase`, `PullModelUseCase`, `DeleteModelUseCase`, `ManageServerUseCase`, `GetChatThreadsUseCase`) consumed by ViewModels.
- `ui/` — Compose screens by feature: `chat/` (`ChatScreen`, `ChatThreadsScreen` + their ViewModels), `models/`, `servers/`, `settings/`, plus shared `components/`, `theme/`, and `navigation/`. Routes are centralized in `ui/navigation/Screen.kt`; `NavGraph.kt` picks `ChatThreads` vs `Servers` as the start destination based on whether a default server exists.
- `di/` — Hilt modules: `DatabaseModule` (Room + DAOs), `NetworkModule` (OkHttp/Gson/Retrofit factory + `OllamaStreamingService`), `RepositoryModule`, `LitertModule`. Add new bindings here rather than in feature code.
- `ads/InterstitialAdManager` — AdMob interstitial loader/show logic; `NavGraph` calls `showAdIfAvailable` on route changes via a Hilt `EntryPoint` (`InterstitialAdManagerEntryPoint`).
- `messaging/` — Firebase Cloud Messaging service (requires `POST_NOTIFICATIONS` runtime permission, requested from `MainActivity` on Android 13+).
- `util/` — `ThinkingParser` (splits `<think>` reasoning from model output), `PerformanceMonitor` (wraps suspend blocks with Firebase Performance traces).

### Request flow for a chat send

`ChatScreen` → `ChatViewModel` → `SendChatMessageUseCase` → `ChatRepository.sendMessage(...)`:
1. Persists the user `ChatMessageEntity` via `ChatMessageDao`.
2. Looks up the thread's `ServerConfigEntity`; if `baseUrl` matches `LitertConstants.isLitertLocalBaseUrl`, delegates to `LiteRtChatService`; otherwise builds a `ChatRequest` and calls `OllamaStreamingService.streamChat(baseUrl, request)`.
3. Collects the streamed `StreamDelta` flow, incrementally updates the assistant message row, and runs deltas through `ThinkingParser` so reasoning and final content are stored separately.

Keep this split intact when adding features — UI should never touch Retrofit/OkHttp/LiteRT directly, and new backends should plug in at the repository level behind the same flow-of-deltas contract.

## Conventions worth knowing

- **Room schema changes**: update `@Database.version`, add a `Migration` in `Migrations.kt`, and wire it into `DatabaseModule`. `exportSchema = false`, so there's no schema JSON to update.
- **Per-server Retrofit clients**: don't inject a singleton `OllamaApi`; use `OllamaApiFactory` to get a client bound to a specific `baseUrl` (users can configure multiple servers).
- **Streaming**: prefer `OllamaStreamingService` (raw OkHttp) for `/api/chat`; the Retrofit `OllamaApi` is used for non-streaming calls (model list, pull, delete, etc.).
- **On-device vs remote** is decided by `ServerBackend` / the `litert-local://` base URL sentinel — don't branch on model name or thread flags.
- **Performance tracing**: wrap new DB/network operations with `PerformanceMonitor.measureSuspend("trace_name") { ... }` to match existing traces.
- **Secrets**: `keystore.properties`, `local.properties`, and `app/google-services.json` are developer-local; never commit them. A `keystore.properties.template` is provided.
