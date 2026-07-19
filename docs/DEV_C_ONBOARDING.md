# Trace — Dev C Onboarding & Project History

Welcome to the **Trace** project! This document will bring you up to speed on everything that has happened from the initial fork up to today. Trace is a privacy-first, on-device (offline) personal AI assistant built for Android, aimed at helping users parse sensitive documents (like medical prescriptions and government forms) without ever sending data to the cloud.

## 1. Project Genesis & Core Architecture
* **The Base**: We forked the open-source **Google AI Edge Gallery** app. It provided a robust, pre-built scaffolding for running Large Language Models directly on Android via LiteRT-LM.
* **The Model**: We are exclusively using **Gemma 4 E2B-IT**. 
* **Why Gemma 4?**: It natively fuses audio, vision, and text into a single small model, which is unmatched in this size class. It also supports tool-calling and has a larger context window than previous iterations.

## 2. Key Product Decisions (See `/docs/DECISIONS.md` for full context)
* **100% Local / Offline**: The product is pitched specifically for highly sensitive data (ID cards, financial records). Cloud APIs (like Gemini Live or GPT) were rejected because the target user cannot risk sending this data to third-party servers. Trace is designed to survive an airplane-mode demo.
* **Push-to-Talk over Wake Word**: For the hackathon build, we chose explicit push-to-talk to avoid the battery drain and OS-kill risks of a background listening service.
* **Consent-First Screen Capture**: Screen Explain uses Android's `MediaProjection` (which shows a visible recording icon) rather than `AccessibilityService` (which is often flagged as stalkerware).
* **High-Quality Local TTS Only**: We explicitly filter out "Network" TTS voices to ensure speech generation works offline, falling back to the highest-quality local voice pack installed on the device.

## 3. Development History (What's Done)

We have successfully completed the foundation and the first two major feature modules.

### Phase 0a & 0: Baseline & Foundation
* **Stripped the Fork**: Removed all Google branding, model catalogs, and TOS screens. 
* **Rebranded**: Renamed the app to "Trace", applied a simple dark theme, and hardcoded the boot sequence to load Gemma 4 directly into a text-only chat interface.
* **Benchmarking**: Verified the model loads into memory once and stays resident without reloading per request.

### Phase 1: AI Chat Module
* **Push-to-Talk**: Built a voice capture loop that routes audio directly into Gemma's native audio path.
* **TTS Pipeline**: Wired up Android's `TextToSpeech` API to read responses aloud.
* **Document Uploads**: Built a file picker allowing users to attach images and documents to their prompts.

### Phase 2: Screen Explain & Vision Module
* **Screen Explain**: Implemented continuous Gemini Live-style screen capture so users can ask questions about what's currently on their screen.
* **Vision Module**: Built a standalone camera-first interface. Users can snap a photo of a physical document (like a prescription) and chat with the model about it.

### Pre-Phase 3 Stabilization (Recent Critical Fixes)
Before starting Phase 3, we spent a sprint entirely on fixing bugs and stabilizing the app:
1. **Document Extraction Rewrite**: We stopped reading PDFs as raw binary text (which fed the LLM garbage). We built `DocumentExtractor.kt`, which elegantly renders PDFs to bitmaps and runs them through on-device ML Kit OCR. It also natively unpacks DOCX/PPTX zip structures and strips the XML for clean text injection.
2. **Document UI Attachments**: Instead of dumping 12,000 characters of extracted text into the user's input box, files are now cleanly represented as visual "chips" in the UI. The text is secretly prefixed to the prompt at inference time.
3. **TTS Markdown Stripping**: Built a Regex cleaner in `TtsManager.kt` so the TTS engine no longer awkwardly reads out Markdown asterisks (`**`) and hashes (`###`).
4. **TTS High-Quality Offline Voices**: Updated the TTS engine to scan the device's installed voices, filter out cloud/network voices, and automatically select the highest `QUALITY_...` local English voice available.
5. **Background Process Death Crash**: Fixed a massive bug in `MainActivity.kt` where launching the system file picker would cause Android to reclaim memory, restarting the app and dumping the user back on the homescreen. Restored `savedInstanceState` handling to fix this.

## 4. Current State & Immediate Next Steps

We are currently sitting at the start of **Phase 3: RAG (Retrieval-Augmented Generation) Module**.

**The Block:** 
Phase 3 requires a local vector database to index the documents users attach so they can be queried later. The roadmap originally planned to cross-compile the **Qdrant Edge Rust crate** via a JNI bridge for Android. 

**Your First Task:**
We are currently evaluating a **Go/No-Go decision** on the Rust/JNI approach. Building a JNI bridge and cross-compiling Rust for a hackathon is extremely high risk and time-consuming. We need to decide whether to:
1. Proceed with the Qdrant Edge Rust JNI integration.
2. Fallback to a pure-Kotlin solution (e.g., in-memory cosine similarity, or a lightweight Kotlin vector library) to ensure we actually finish the feature before the hackathon deadline.

As Dev C, please review the architecture, familiarize yourself with `LlmChatViewModel.kt` (where inference is triggered) and `DocumentExtractor.kt` (where our text data originates), and prepare to assist with the RAG data structures!
