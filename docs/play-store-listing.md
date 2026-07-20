# Play Store Listing — Ollama AI Chat

## Short Description (80 chars max)
Ollama AI chat client — remote server or fully offline on-device models.

## Full Description

**Chat with AI from your Android phone — or from any browser.**

Ollama AI Chat connects to your Ollama server or runs powerful language models directly on your device — Gemma 4, Qwen 3, DeepSeek R1 Distill, and Phi-4 Mini — with no data leaving your phone. Now with **Web Chat Sync**: open a browser on any computer, sign in with the same account, and continue your conversations in real time.

---

### 💻 NEW: Web Chat — Chat from Any Browser

No laptop app to install. Open the web chat page in any desktop or mobile browser, sign in with Google or your email, and all your threads appear instantly. Type a message and your Android phone receives it, runs AI inference (Ollama or on-device), and streams the response back to your browser in seconds.

- **Phone offline?** No problem — messages queue locally in the browser and dispatch automatically when your phone reconnects.
- **Real-time model selector** shows only models you've actually installed, and pre-selects the right one per thread.
- **Phone presence indicator** shows when your phone is online and ready.

---

### 📲 On-Device AI — No Server, No Internet Required

Run state-of-the-art language models completely offline via Google's LiteRT-LM runtime. Download once, chat forever — no data ever leaves your device.

Available on-device models:
- Gemma 4 E2B (~2.6 GB)
- Gemma 4 E4B (~3.7 GB)
- Gemma 3 1B (~584 MB)
- Gemma 3 270M (~304 MB)
- Qwen 3 0.6B (~614 MB)
- Qwen 2.5 1.5B Instruct (~1.6 GB)
- DeepSeek R1 Distill Qwen 1.5B (~1.8 GB)
- Phi-4 Mini Instruct (~3.9 GB)

---

### 🤖 Remote Ollama — Full Server Power

Connect to your home or work Ollama server and access any model it hosts. Multiple servers, per-thread model switching, real-time streaming.

---

### ✨ Full Feature List

**Conversations**
- Real-time streaming responses (remote and on-device)
- Persistent chat history saved locally
- Pin, archive, and label threads for organization
- Per-message copy, share, delete, edit & resend, regenerate
- Stop generation mid-stream — partial response is kept
- Export/share entire threads as Markdown

**Search**
- Search inside the current conversation
- Global search across all conversations — jump directly to the message

**Images**
- Attach images for vision-capable remote models
- Automatic compression for fast uploads

**Voice & Accessibility**
- Voice input via Android speech-to-text
- Read-aloud replies via Text-to-Speech

**Model Control**
- Per-thread temperature, top_p, top_k, context window, and seed
- Token-speed stats and response time under every reply
- Full model info: parameters, quantization, template, license

**Web Sync**
- Enable in Settings → Web Sync
- Sign in with Google or email/password
- All threads and messages sync to Firebase Realtime Database
- Access from any browser at no extra cost

---

### 🔒 Privacy

- Conversation history is stored locally on your device (Room database)
- On-device inference: zero network traffic for chat content
- Web Sync uses Firebase — only your own account data is synced, secured by Firebase Auth
- No third-party advertising data collection for chat content

---

### Requirements
- Android 7.0 (API 24) or higher
- Internet connection for Ollama remote inference and Web Chat Sync
- On-device inference works fully offline after model download

---

Open source under the MIT License: github.com/chartmann1590/ollama-android-client
