# Trace — Architecture

## System overview

```
Voice input (push-to-talk button)
        |
        v
Gemma 4 E2B audio encoder -> intent understood
        |
        v
Intent Router (lightweight)
        |
        +---- Simple/direct action --------> Android API directly (open file, etc.)
        |
        +---- Needs reasoning --------------> Gemma 4 E2B
                                                    |
                                    +---------------+---------------+
                                    |                                |
                            Screen capture                   Notes/file query
                            (MediaProjection)                       |
                                    |                     Qdrant Edge vector search
                                    |                     (Rust crate via JNI)
                                    |                                |
                                    |                     Retrieved chunks -> Gemma
                                    +----------------+----------------+
                                                     |
                                            Response generated
                                                     |
                                    +----------------+----------------+
                                    |                                 |
                              Spoken (TTS)                    Visual UI update
                                                        (quiz card / summary panel / doc preview)
```

## Components

### App shell — Kotlin / Jetpack Compose
Forked from Google AI Edge Gallery. Retained: LiteRT-LM integration, Gemma model loading/lifecycle
handling. Removed: model-catalog/picker UI, generic multi-model chat screen. Replaced with Trace's own
product surface (voice interaction UI, quiz/flashcard screens, file preview panels).

### Model runtime — Gemma 4 E2B-IT via LiteRT-LM
- Instruction-tuned variant (not base) — better assistant behavior, tool-calling, multilingual handling.
- Loaded once at app start (or first use), kept resident in memory for the app's lifetime. Never
  reloaded per-request.
- Recommended inference config (from pre-hackathon benchmarking, adjust if real-device numbers differ):
  - Max tokens: 512-768
  - Temperature: 0.4-0.5
  - Top P: 0.9 / Top K: 32-40
  - GPU backend
  - Thinking mode: OFF (latency-sensitive voice assistant use case)
  - Speculative decoding: ON if stable on target hardware
- System prompt keeps responses concise by default (1-2 sentences unless expansion requested) — both a
  UX choice and a real hardware constraint at ~9 tok/s decode on target devices.

### Intent Router
Simple commands (open camera, toggle flashlight, basic system actions) should not invoke the full model —
route directly to Android APIs. Gemma is reserved for reasoning, ambiguity, summarization, tool
selection, and anything genuinely requiring understanding. Start with a simple rule/keyword-based router
for the hackathon build; a FunctionGemma-style dedicated small routing model is a reasonable future
upgrade, not a hackathon requirement.

### Screen Capture — MediaProjection
See `/CONSTRAINTS.md` for why this and not AccessibilityService. Captures the current screen on request,
passed to Gemma's vision path alongside the spoken question.

### Voice pipeline
Gemma 4 E2B's native audio encoder handles ASR directly — no separate Whisper/STT stage. This is a
deliberate architectural choice: Gemma's audio tokens feed into the same forward pass as vision and text,
enabling joint reasoning (e.g., a spoken question that references what's currently visible) that a
pipelined STT-then-LLM approach can't do as cleanly. TTS handles the spoken response side.

### Retrieval layer — Qdrant Edge (RAG Module)
In-process, no-server, embedded vector search engine. Used specifically for the RAG module. 
**Important Ingestion Clarification:** RAG ingestion is strictly via files the user explicitly attaches through AI Chat or selects directly from the device. It never performs a background search of device storage. This means the scoped-storage limitations documented for the old File Fetch feature do not apply to RAG.
Notes are OCR'd (via Gemma vision) and embedded once, then indexed locally. A voice query like "quiz me on my DBMS notes" triggers a real semantic retrieval over indexed chunks, and Gemma generates the quiz/summary grounded in what's actually retrieved.

- Only available as Python bindings or a Rust crate — no native Kotlin/JNI package exists. Integration
  path: compile the Rust crate for Android, expose a small, contained JNI boundary to Kotlin (index
  document, query top-k, done). This is the single highest-risk technical integration in the project —
  see `/ROADMAP.md` Phase 3 for the validation gate and fallback plan.
- Storage: local directory on disk (Edge Shard), disk-backed, offline-capable, no background service.

### Semantic File Matcher (Demo Fallback)
When exact file matches fail, a user-configurable semantic fallback runs (using Gemma vision to classify image candidates). Due to Android 13+ scoped storage limitations, fetching general files (e.g. PDFs) directly from shared storage without a user picker is heavily restricted. Thus, voice-fetch (and the semantic fallback) operates strictly on images from MediaStore and accessible user directories (e.g., Downloads, Screenshots). This scope is configurable in settings to balance thoroughness against the ~0.5s/image latency of the vision model.

### Schedules
When a prescription or similar is scanned in Vision or attached in AI Chat, the user can ask the model to create a reminder routine. This requires a highly reliable architecture due to strict Android background execution constraints:
- **Scheduling Backend:** Uses `AlarmManager` for precise, time-critical alarms (e.g., medicine reminders) and `WorkManager` for flexible, deferrable tasks.
- **Reliability:** The system must survive OS process death and device reboots. This is handled by persisting all schedule data in the Memory store and re-registering alarms on the `BOOT_COMPLETED` broadcast.
- **Battery Optimization:** Must handle Android Doze mode and App Standby Buckets using `setExactAndAllowWhileIdle` for `AlarmManager` to ensure notifications fire even when the device is asleep.
- **Notifications:** Triggers real Android Notifications using dedicated Notification Channels to alert the user at the scheduled times.

### Memory store
The single source of truth for the user's kept records — free-text notes they author and reminders Trace generates (e.g. from a scanned prescription). This is the shared interface the Memory sidebar UI (Dev C2) binds to and that the Vision/Chat → schedule wiring writes into.

- **Persistence:** proto DataStore (`proto/memory_store.proto`, file `memory_store.pb`), following the same pattern as `NotificationScheduleManager` — a `MemoryStore` message holding a repeated `MemoryEntry`.
- **Access:** `memory/MemoryRepository.kt` — a Hilt `@Singleton`. Exposes an observable `StateFlow<List<MemoryEntry>> entries` (newest-first, ready to render directly) plus `add(...)`, `update(id, title, body)`, `remove(id)`, and `get(id)`. All writes persist on an IO scope; UI never blocks. Non-Hilt callers can reach it via `MemoryRepositoryEntryPoint`.
- **Entry shape (`MemoryEntry`):**
  - `id` — UUID string. Also used as the linked schedule id when the entry backs a reminder, so the Memory and Schedules stores stay in sync.
  - `kind` — `USER_AUTHORED` (freely editable/deletable) or `SYSTEM_AUTHORED` (generated by Trace; show its origin).
  - `source` — for system entries: `MANUAL`, `VISION_PRESCRIPTION`, or `CHAT_DOCUMENT`; `UNSPECIFIED` for user entries.
  - `title` / `body` — display text.
  - `linked_schedule_id` — the `ScheduledNotification.id` this entry backs if it is a reminder; empty for plain notes.
  - `created_at_ms` / `updated_at_ms` — epoch millis.
- **Reminder linkage:** a reminder is TWO records that share one id — a `MemoryEntry` (what the user sees/edits) and a `ScheduledNotification` (what fires the alarm). The wiring layer creates both with the same id; deleting one should cancel the other. Plain notes have an empty `linked_schedule_id`.
- **Dev C2 contract:** the sidebar reads `entries` and calls `add`/`update`/`remove`. For user-authored notes use the `add(title, body)` defaults (kind=USER_AUTHORED, source=MANUAL). Do not synthesize schedule links from the UI — reminder creation flows through the wiring layer (Dev A). Bind to `MemoryRepository`'s API as documented here; if you need a field that isn't here, flag it before adding it to the proto.

### Why voice + visual UI, not voice-only
Research on voice-only interfaces for low-literacy/general users consistently shows pure voice
underperforms compared to voice paired with visual confirmation (illiterate-user studies show ~53%
success for voice-only vs. ~82% for semi-literate users with visual support; industry consensus by 2026
is that voice-only interfaces are rare for exactly this reason). Applied here: quiz questions, flashcards,
and file previews render as real UI driven by voice interaction, not spoken-only exchanges. This is a
product-quality decision, not scope creep.

## Why this needs Gemma specifically (not a swap-in cloud API or different local model)

Documented in full in `/DECISIONS.md`. Summary of the surviving, verified claims:

1. **Native audio fused into the same small model as vision and text** — genuinely rare at this size
   class; most comparably-sized local models (Qwen, Phi at small sizes) are text/vision only, with audio
   either absent or bolted on as a separate model.
2. **No session-length or frame-rate ceiling**, unlike Gemini Live (2-minute audio+video cap, 1 FPS video
   throttle server-side) — this product needs sustained, on-demand interaction without an imposed cap.
3. **Zero marginal cost per inference** once deployed — relevant given the product's premise depends on
   frequent, everyday use, which would be cost-prohibitive against per-token cloud API pricing at scale.
4. **Local-only is close to a hard requirement, not a preference**, for this specific data category (ID
   documents, medical info, government forms) — a cloud version of several core features is close to a
   non-starter on privacy grounds alone, independent of the cost/latency arguments.

Note: Gemma is **not** claimed to be the best local model at raw vision quality (Qwen3-VL leads there) or
the best at pure multilingual text breadth (Qwen 3.5 has a real claim there too). The honest, defensible
claim is narrower and specific to the combination above — don't overclaim in the pitch beyond what's
listed here.
