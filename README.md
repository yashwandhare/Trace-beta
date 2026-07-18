# Trace ✨

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> **Trace** is modified from [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery),
> licensed under the [Apache License, Version 2.0](LICENSE). The original copyright notices and
> license text are retained in this repository per the terms of the Apache 2.0 License.

**A fully offline, voice-first Android assistant for your private content — files, documents, notes, and whatever is on your screen — without any of it ever leaving your device.**

Trace is not a general-purpose chatbot. It is scoped specifically to private, personal data: identity documents, forms, medicines, class notes, screenshots. The reason it exists is that no cloud assistant can be trusted with that category of content, and no file manager can explain or reason about what's inside a file.


## What Trace Does

### 🖥️ Screen Explain
Ask a spoken question about anything currently on your screen. Trace captures the screen (via MediaProjection), sends it alongside your voice question to Gemma 4 E2B running entirely on-device, and returns a spoken and on-screen answer.

**Example:** *"Hey Trace, what does this line in my bank statement mean?"*

### 📓 Notes → Flashcards / Quiz / Summary
Your notes (photographed or typed) are OCR'd and indexed locally. A voice command triggers semantic retrieval over that index, and Gemma generates a quiz, flashcard set, or summary grounded in your actual content — not general knowledge.

**Example:** *"Hey Trace, quiz me on my DBMS normalization notes."*

### 📂 File Fetch
Direct voice or text file lookup — ask for a file by name and Trace finds it via Android's Storage Access Framework, without any cloud search.

**Example:** *"Hey Trace, pull up my driver's licence."*

### 📄 File Summarize
Once a file is located, summarize its contents using on-device OCR and Gemma. Works on scanned documents, handwritten notes, and photos of text.


## Why Local?

For this specific data category — ID documents, medical reports, government forms, personal notes — sending content to any cloud API is close to a non-starter on privacy grounds, independent of cost or latency. Trace fills exactly that gap:

- **Zero marginal cost per inference** — no per-token pricing, no usage caps
- **No session limits** — unlike Gemini Live (2-min audio+video cap, 1 FPS throttle), Trace has no imposed ceiling on sustained interaction
- **No network required** — every feature works in airplane mode, always


## Technology

| Component | Technology |
|---|---|
| App shell & UI | Kotlin / Jetpack Compose |
| Model runtime | Gemma 4 E2B-IT via LiteRT-LM (on-device, GPU-accelerated) |
| Voice input | Gemma 4's native audio encoder (no separate STT stage) |
| Screen capture | Android MediaProjection API |
| Notes retrieval | Qdrant Edge (embedded Rust vector search, JNI bridge) |
| Embeddings | FastEmbed / on-device embeddings |

Forked from [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery). The LiteRT-LM model loading and inference plumbing is retained from the upstream fork; the app shell, UI, and product surface are Trace's own.


## Requirements

- **Android 12+** (API level 31+)
- **~6 GB RAM** recommended for Gemma 4 E2B-IT
- No internet connection required — all features work fully offline


## Development

Check out the [development notes](DEVELOPMENT.md) for instructions on how to build the app locally.

Branch model:
- `main` — always stable, always the last known-good merged state
- `dev-a` — Kazuto's working branch (model/voice pipeline, core architecture)
- `dev-b` — Yash's working branch (UI, File Fetch, Quiz screens)


## Status

Active development — pre-release, hackathon sprint build. Not yet available on any app store.

See [`docs/ROADMAP.md`](docs/ROADMAP.md) for the phased build plan and [`docs/TODO.md`](docs/TODO.md) for current active tasks.


## License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

This project is modified from [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) by Google LLC, originally licensed under Apache 2.0. Original copyright notices are retained.
