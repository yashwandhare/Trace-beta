# Trace — Dev C Comprehensive Onboarding & Project History

Welcome to the **Trace** project! This document serves as your complete joining reference. It will bring you up to speed on everything that has happened from the initial fork up to today. 

Trace is a privacy-first, on-device (offline) personal AI assistant built for Android, aimed at helping users parse sensitive documents (like medical prescriptions and government forms) without ever sending data to the cloud.

---

## 1. Project Genesis & Core Architecture

* **The Base**: We forked the open-source **Google AI Edge Gallery** app. It provided a robust, pre-built scaffolding for running Large Language Models directly on Android via LiteRT-LM.
* **The Model**: We are exclusively using **Gemma 4 E2B-IT**. 
* **Why Gemma 4?**: It natively fuses audio, vision, and text into a single small model, which is unmatched in this size class. It also supports tool-calling and has a larger context window than previous iterations.

---

## 2. Key Product Decisions & Constraints
*(See `/docs/DECISIONS.md` for full context)*

* **100% Local / Offline**: The product is pitched specifically for highly sensitive data (ID cards, financial records). Cloud APIs (like Gemini Live or GPT) were rejected because the target user cannot risk sending this data to third-party servers. Trace is designed to survive an airplane-mode demo.
* **Push-to-Talk over Wake Word**: For the hackathon build, we chose explicit push-to-talk to avoid the battery drain and OS-kill risks of a background listening service.
* **Consent-First Screen Capture**: Screen Explain uses Android's `MediaProjection` (which shows a visible recording icon) rather than `AccessibilityService` (which is often flagged as stalkerware).
* **High-Quality Local TTS Only**: We explicitly filter out "Network" TTS voices to ensure speech generation works offline, falling back to the highest-quality local voice pack installed on the device.

---

## 3. Development History: From Start to Present

We have successfully completed the foundation and the first two major feature modules. Here is the chronological breakdown of past actions and fixes.

### Phase 0a & 0: Baseline & Foundation
* **Stripped the Fork**: Removed all Google branding, model catalogs, unused example modules, and TOS screens. 
* **Rebranded**: Renamed the app to "Trace", applied a simple dark theme, and hardcoded the boot sequence to load Gemma 4 directly into a text-only chat interface.
* **Benchmarking & Stability**: Verified the model loads into memory once and stays resident without reloading per request. Verified that the app opens directly to a plain text chat screen.

### Phase 1: Core Voice Loop & AI Chat Module
* **Voice Capture Loop**: Built a push-to-talk button UI (idle/listening/processing) that routes audio directly into Gemma's native audio path.
* **TTS Pipeline**: Wired up Android's `TextToSpeech` API to read responses aloud.
* **Document & Image Uploads**: Built a file picker allowing users to attach images and documents to their prompts. Explicit file attachments were added as custom `ChatMessageFile` objects to support RAG in future phases.
* **Intent Routing**: Built a minimal rule-based intent router to separate simple device commands from complex queries requiring Gemma.

### Phase 2: Screen Explain & Vision Module
* **Screen Explain**: Implemented continuous Gemini Live-style screen capture so users can ask questions about what's currently on their screen using `MediaProjection`.
* **Vision Module**: Built a standalone camera-first interface (`VisionCameraScreen`) hooked to a `VisionChatViewModel`. Users can snap a photo of a physical document (like a prescription) and immediately chat with the model about it.
* **OCR Quality Test**: Conducted OCR quality tests on real sample notes to validate text extraction capabilities.

### Pre-Phase 3 Stabilization & Optimization (Recent Critical Fixes)
Before starting Phase 3, the team spent significant time fixing bugs, optimizing UI, and stabilizing the app:
1. **Document Extraction Rewrite (`DocumentExtractor.kt`)**: We stopped reading PDFs as raw binary text. We built a system that elegantly renders PDFs to bitmaps and runs them through on-device ML Kit OCR. It also natively unpacks DOCX/PPTX zip structures and strips the XML for clean text injection.
2. **Semantic File Fallback**: Implemented a 2-pass search logic for document fetching (Pass 1 filename matching + Pass 2 Gemma vision semantic matching).
3. **Performance Optimization Pass (7-Bug Fix)**: 
   - Removed unnecessary 500ms delays in the UI.
   - Refactored chat rendering to use `LazyColumn` efficiently.
   - Fixed TTS gating and improved latency via chunked emission.
   - Reused `VoiceManager` instances to save memory.
   - Set the `IntentRouter` regex to compile only once.
4. **TTS Polish**: Built a Regex cleaner in `TtsManager.kt` so the engine no longer reads out Markdown asterisks (`**`) and hashes (`###`). Filtered out cloud voices to automatically select the highest `QUALITY_...` local English voice.
5. **Memory & Lifecycle Leaks**: Fixed a bug where `ScreenCaptureService` leaked scope and caused UI lag. Restored `savedInstanceState` handling in `MainActivity.kt` to prevent the app from crashing when Android reclaims memory during file picking.
6. **UI Polish**: Applied visual updates for the Vision module, updated chat bubble colors to match user accents, and tweaked the homescreen grid layout.

---

## 4. Current State & Immediate Next Steps

We are currently sitting at the start of **Phase 3: RAG (Retrieval-Augmented Generation) Module**.

### The Phase 3 Architecture
The goal of Phase 3 is to allow users to explicitly attach notes -> OCR -> Embed -> Index -> Voice Query -> Retrieval -> Gemma-generated quiz/summary.

### The Immediate Blocker
Phase 3 requires a local vector database to index the documents. The roadmap originally planned to cross-compile the **Qdrant Edge Rust crate** via a JNI bridge for Android. 

**Go/No-Go Decision Finalized**: We have made the decision to **No-Go** on the Rust JNI bridge. Building a JNI bridge and cross-compiling Rust for a hackathon is extremely high risk and time-consuming. 
Instead, we will use a **pure-Kotlin fallback** (e.g., in-memory cosine similarity array, or a lightweight Kotlin vector library) to ensure we actually finish the RAG feature before the hackathon deadline.

### Your Role (Dev C)
As you join the team, your first priority is to build Phase 3 (RAG). You are authorized to take over both Dev A and Dev B tasks for Phase 3:
1. **Familiarize**: Review `LlmChatViewModel.kt`, `VisionChatViewModel.kt`, and `DocumentExtractor.kt`.
2. **Build the Kotlin RAG Pipeline**: Build a simple Kotlin embedding and vector search pipeline. You can use FastEmbed or on-device embeddings, and a simple in-memory cosine similarity store.
3. **Build the UI**: Build the Quiz/Flashcard UI screens in Compose (question card, answer reveal, right/wrong feedback).
4. **End-to-End**: Wire it so attached notes -> embed -> index -> voice query -> retrieval -> Gemma-generated quiz/summary works!

Welcome to the team! Let's build something incredible.
