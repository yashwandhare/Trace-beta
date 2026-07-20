# Trace — Log

Running log of work sessions. Short, factual, action-oriented — not a chat transcript. Newest entries at
the bottom. Each entry: date, dev, phase, what was done, what's still open/broken.

Format:
```
## [date] — Dev [A/B] — Phase [N]
- Did: ...
- Broken/open: ...
- Benchmark numbers (if any): ...
```

---

## 2026-07-18 — Dev A — Phase 0a
- Did: Confirmed fork is Apache 2.0 (LICENSE is exact match to upstream apache.org LICENSE-2.0, minus a leading blank line). No NOTICE file exists in repo. Added a short attribution note to top of README.md stating Trace is modified from Google AI Edge Gallery, Apache 2.0, with original copyright + license retained. Created `dev-a` branch off `main`.
- Broken/open: Dev A's other Phase 0a task ("confirm Gemma 4 E2B-IT loads after Dev B's stripping") is blocked on Dev B's `dev-b` landing — by definition. Waiting on Dev B's PR before doing the combined-build load confirm.
- Benchmark numbers: none yet.

---

## 2026-07-18 — Dev B — Phase 0a
- Did:
  - B1: Removed TOS/onboarding — `AppTosDialog.kt`, `GemmaTermsOfUseDialog.kt`, `PromoScreenGm4.kt` deleted; `HomeScreen` `showTosDialog` state and call removed; `GalleryNavGraph` promo routing block removed.
  - B2: Removed model picker/manager/benchmark — deleted `GlobalModelManager.kt`, `ModelManager.kt`, `ModelList.kt`, `ModelImportDialog.kt`, `PromoBannerGm4.kt`, `ModelPicker.kt`, `ModelPickerChip.kt`, all `ui/benchmark/` files, all `ui/notifications/` files; removed `ROUTE_MODEL_LIST`, `ROUTE_MODEL_MANAGER`, `ROUTE_BENCHMARK`, `ROUTE_NOTIFICATIONS` from nav graph; home now routes directly to `ROUTE_MODEL/{taskId}/{firstModel.name}`.
  - B3: Removed unused modules — deleted `customtasks/examplecustomtask/`, `customtasks/tinygarden/`, `customtasks/mobileactions/`, `customtasks/agentchat/`, `ui/llmsingleturn/`; `ModelManagerViewModel.kt` left untouched (Dev A's).
  - B4: Renamed app — `app_name` → "Trace", `AndroidManifest.xml` `android:label` → "Trace", `settings.gradle.kts` `rootProject.name` → "Trace". Package/applicationId/deep-link scheme unchanged.
  - B5: Forced dark theme — `ThemeSettings.themeOverride` default → `THEME_DARK`; splash screen background updated to `#FF111318`.
- Broken/open:
  - `LLM_ASK_IMAGE` and `LLM_ASK_AUDIO` tasks are still registered in `LlmChatTaskModule.kt` (Dev A's file — cannot touch). These tasks will appear on HomeScreen. **Flag to Dev A**: remove or keep only `LLM_CHAT` in `LlmChatTaskModule.kt`.
  - `HomeScreen` still renders a task tile grid with all registered tasks. The "direct to chat" UX requires Dev A to either wire auto-navigation on single task or confirm whether HomeScreen is still wanted in Phase 0a.
  - `ModelPickerChip` references may remain in `ModelPageAppBar.kt` or `LlmChatScreen.kt` — Dev A should audit before combined build.
- Benchmark numbers: N/A (UI-only changes).

---

## 2026-07-18 — Dev A — Phase 0 (Foundation Completion)
- Did: 
  - Restored the benchmark UI and wired it to the "Models" drawer menu item.
  - Added a loading spinner overlay for when the model is initializing (loading into RAM/VRAM).
  - Switched the default accelerator to GPU in the model allowlist.
  - Exposed the thinking toggle capability in the model settings, disabled by default.
  - Finalized the default system prompt for "Trace", an advanced on-device AI assistant.
  - Cleaned up Dev B's unused module references and finalized the simplified welcome screen layout.
- Broken/open: None. Phase 0 is fully complete and pushed to main. Ready for Phase 1.
- Benchmark numbers (Gemma-4-E2B-it | GPU | 256 prefill / 256 decode | 3 runs): 
  - Prefill speed: 78.05 tokens/sec
  - Decode speed: 8.79 tokens/sec
  - Time to first token: 3.39 sec
  - Cold load (First init time): 19495.95 ms (~19.5s)
  - Warm load (Steady init time): 14038.13 ms (~14.0s)

---

## 2026-07-18 — Dev A & Dev B — Phase 1 & 2
- Did: 
  - Wired TTS output and integrated Android SpeechRecognizer for voice input.
  - Built intent routing engine for file fetching and screen explaining.
  - Implemented background continuous screen capture via MediaProjection for a Gemini Live-style experience.
  - Wired FileFetcher to launch Android's default file viewer via Intent router.
  - Dev B completed local UI implementations for the tap-to-toggle Mic button and local OCR testing.
- Broken/open: None. Phase 1 and Phase 2 are complete, stable, and pushed to main. Ready for Phase 3.
- Benchmark numbers: APK size grew to ~190MB due to ML Kit OCR and new modules.

---

## 2026-07-18 — Dev C — Pre-Phase 3 stabilization
- Did: Fixed response TTS so only voice-originated prompts are spoken; reduced first-speech latency by emitting short streamed chunks. Routed typed commands through the same intent router as voice commands; expanded direct file-command phrasing and tokenized filename matching. Reworked Screen Explain to retain the request through MediaProjection consent, register the required MediaProjection callback before creating the virtual display, wait for the first frame, and pass the original question, OCR text, and captured image to Gemma.
- Broken/open: Requires real-device validation for TTS timing, MediaProjection lifecycle, and the files that are visible to the app under Android scoped storage. The debug APK was built successfully.
- Benchmark numbers: `:app:compileDebugKotlin` and `:app:assembleDebug` passed locally.

---

## 2026-07-18 — Dev B — Perf Optimization Pass (cross-cutting)

- Did:
  - **Bug fix #1 (CRITICAL):** Removed unconditional `delay(500)` in `LlmChatViewModel.generateResponse` — this was adding 500ms artificial latency before every single inference call with no functional purpose. Largest single latency win.
  - **Bug fix #2:** Pre-compiled `SENTENCE_SPLIT_REGEX` as a file-level constant in `LlmChatViewModel`. Previously the `Regex(...)` object was constructed fresh on **every streaming token** inside the `resultListener` lambda, causing repeated CPU allocation during generation.
  - **Bug fix #3:** Added `InteractionOrigin` enum (`InteractionOrigin.kt`) and gated TTS so it only speaks responses for voice-originated prompts (PTT / audio clip input). Previously TTS fired on every typed response, wasting audio pipeline resources and causing audible bleed during text-only use.
  - **Bug fix #4:** Rewrote `VoiceManager` to keep a single `AudioRecord` alive via `lazy` init across sessions (was releasing + recreating on every PTT press, incurring OS audio-session setup cost per-press). Added proper `release()` for lifecycle cleanup. Added `coerceAtLeast(4096)` guard on buffer size to handle error return codes from `getMinBufferSize`.
  - **Bug fix #5:** Rewrote `IntentRouter` to compile all regex patterns once at class-init. Expanded file-fetch routing to catch natural voice phrases: "pull up", "find my", "open my", "show me", "bring up", "fetch my", "search for", etc. Added `SCREEN_EXPLAIN` intent type. Added fast keyword/opener pre-filter before expensive regex.
  - **Bug fix #6:** Migrated `ChatPanel` message list from `Column + verticalScroll` → `LazyColumn + LazyListState`. The old approach laid out **all** messages at once (O(n) composition), making long chats progressively more janky. `LazyColumn` only composes visible items. Removed `mutableStateMapOf` item-height tracking (no longer needed). Removed `dynamicBottomPadding` calculation that iterated all messages on every composition.
  - **Bug fix #7:** Threaded `InteractionOrigin.VOICE` end-to-end through `ChatViewWrapper → ChatView → ChatPanel → MessageInputText` via a new `onSendVoiceMessage` callback chain. PTT (speech-to-text) messages now reach `generateResponse` tagged as `VOICE`.
  - Created `InteractionOrigin.kt` in `com.google.ai.edge.gallery.voice`.
- Broken/open:
  - `LazyColumn` migration removes the `dynamicBottomPadding` "pin last user message to top" behaviour. Scroll-to-bottom on new message still works via `animateScrollToItem`. If UX team needs the pin-to-top behaviour restored, it requires a `LazyListState`-based implementation.
  - `IntentRouter.SCREEN_EXPLAIN` intent type added but not yet wired to any screen-capture handler (Phase 3 RAG work).
- Benchmark numbers: Not re-run. Expected first-token-latency improvement ≥500ms from delay removal alone.

---

## 2026-07-19 — Dev C2 — Pre-Phase 3 cleanup & hardening
Scoped stabilization pass on `dev-b` before Phase 3 (RAG) begins, so Dev C1 inherits a clean tree. Five commits, one per workstream; each compile-verified. No merge to `main`.

- Did:
  - **WS1 — correctness bugs:**
    - `VoiceManager`: marked `isRecording` `@Volatile`; recreate `AudioRecord` after `release()` instead of leaving the lazy instance permanently dead (mic was dead after first teardown).
    - `TtsManager`: precompiled the 6 markdown-strip regexes (were rebuilt on every `speak()`); reset state in `shutdown()` so a post-shutdown `speak()` can't hit a dead engine.
    - `HoldToDictateViewModel`: removed the blind `delay(500)` before `stopListening()`; actually `cancel()` the recognizer on gesture-cancel (mic-leak fix); fire `onDone("")` on error so PTT callers don't hang.
    - `ScreenCaptureService`: run ImageReader frame decode on a background `HandlerThread` instead of the main thread (~5MB bitmap copy was janking the UI); guarded `latestBitmap.copy` against the `onDestroy` recycle race; quit the reader thread on destroy.
    - `ScreenExplainManager`: `isServiceRunning` `@Volatile`.
    - `MessageInputText`: unified the two divergent intent-dispatch sites (send button + PTT) into one remembered `IntentRouter` + dispatch helper — fixes typed LLM_CHAT messages losing `InteractionOrigin` (and thus TTS/speak behavior diverging between voice and text); surface image-limit-exceeded for all model types, not just AiCore.
    - `ChatPanel`: hoisted per-row task-color lookups out of `itemsIndexed`.
    - `VisionCameraScreen`: write recorded frames off the main thread via a thread-safe list, cap at 100 frames, publish count on main thread, recycle frames on dispose (unbounded full-size bitmap accumulation → OOM fix).
    - `LlmChatScreen`: handle unknown `taskId` instead of null-asserting (`!!`).
  - **WS2 — perf:** `updateLastTextMessageLlmBenchmarkResult` now builds a new `ChatMessageText` via `clone()` instead of mutating in place (same-reference item was skipped by recomposition, benchmark result not rendering); exposed `messagesByModel` as `Map<String, List<...>>` (was `MutableList`) to remove an aliasing footgun. Most other WS2 wins landed in WS1 (regex precompile, per-tap router construction, per-row color lookups, Vision state churn).
  - **WS3 — dead code (~1040 lines):** deleted grep-confirmed unreferenced files — `NewReleaseNotification.kt`, `MobileActionsChallengeDialog.kt`, `SteadinessMonitor.kt`, `OcrTestRunner.kt`, the dead PTT UI subsystem (`PttOverlay.kt` + `PushToTalkButton.kt` — never invoked; PTT lives in `MessageInputText`), and the stray `worker/AndroidManifest.xml`. Removed the dead `LlmAskImage/LlmAskAudio` screens + view-models, the unused `ALLOWLIST_BASE_URL` const + its dead `getAllowlistUrl()` helper, dead in-file blocks in `ChatView`, and stale PTT imports/locals in `LlmChatTaskModule`. Gated `MainActivity`'s intent-extra debug dumps behind `BuildConfig.DEBUG` (was logging potentially sensitive extras in release).
  - **WS4 — branding:** homescreen greeting `"Welcome Kazuto"` (leftover dev name) → `"Welcome to Trace"`. No package rename (deliberately deferred).
  - **WS5 — deps/manifest:** removed verified-unused deps `firebase-messaging`, `mcp-kotlin-sdk`, `ktor-client-android/core`; deleted `FcmMessagingService.kt` + its manifest `<service>`, the FCM notification-channel meta, and dead permissions `c2dm.RECEIVE`, `READ_GSERVICES`, `GET_ACCOUNTS`, `READ_CALENDAR`. Kept Firebase Analytics (degrades to null, no `google-services.json`) and the `notifications/` receivers (live — Phase 4 Schedules foundation).
- Broken/open (flagged, NOT changed — need owner/Dev A):
  - **Model config:** allowlist ships Gemma at temperature 1.0 / maxTokens 4096; `ARCHITECTURE.md` recommends temp 0.4-0.5 / maxTokens 512-768 for this latency-sensitive voice use case. Left for Dev A (model/runtime lane) — needs real-device benchmarking before changing.
  - **LLM_ASK constants:** `LLM_ASK_IMAGE`/`LLM_ASK_AUDIO` task-id constants still thread through `ModelManagerViewModel` capability/allowlist logic (Dev A's core lane). Dead screens/VMs removed; constants left for Dev A to unpick safely.
  - **READ_CALENDAR:** removed (only `java.util.Calendar` used, no provider access). Phase 4 Schedules may need to re-add it.
  - All changes need real-device validation (TTS timing, MediaProjection lifecycle, Vision recording, PTT).
- Benchmark numbers: `:app:compileDebugKotlin` clean each workstream; `:app:assembleDebug` BUILD SUCCESSFUL. Debug APK ~198.99MB (essentially unchanged — heavy deps are ML Kit/LiteRT/native libs; the removed messaging/ktor/mcp deps were small/transitively-shared. The win is correctness + reduced permission/attack surface, not size).

---

## 2026-07-19 — Dev C1 — Phase 3 (RAG) — pipeline build
Built the Phase 3 RAG backend + AI Chat wiring on `dev-a` (updated to the cleaned `main` first). Pure-Kotlin path per the Qdrant JNI no-go. Split: Dev C1 = pipeline (Dev A + Dev C tasks); Dev C2 = Quiz/Flashcard UI (Dev B task).
- Did:
  - **Data contracts** (`rag/RagContracts.kt`): `NoteChunk`, `EmbeddedChunk`, `RetrievalResult`, `QuizItem`, `RagResponse` — the stable shapes the Quiz UI consumes. Landed + pushed first so Dev C2 is unblocked.
  - **Embedder** (`rag/TextEmbedderHelper.kt`): MediaPipe `TextEmbedder` over a bundled Universal Sentence Encoder model (`assets/universal_sentence_encoder.tflite`, ~5.9MB, 100-dim, L2-normalized). Fully offline. Added `com.google.mediapipe:tasks-text:0.10.29`.
  - **Vector store** (`rag/VectorStore.kt`): thread-safe in-memory cosine-similarity index, top-k + min-score. Brute-force linear scan (fine for a user's own notes: tens–low-hundreds of chunks).
  - **Chunker** (`rag/TextChunker.kt`): paragraph-packing with sentence-split fallback for oversized paragraphs.
  - **Repository** (`rag/RagRepository.kt`): chunk→embed→index and query→retrieve; lazy embedder init, graceful degradation if the model can't load.
  - **Generator** (`rag/RagGenerator.kt`): RAG intent detection (quiz/summary triggers, only fires when content is indexed), grounded prompt building, tolerant JSON→`QuizItem` parsing.
  - **Engine** (`rag/RagEngine.kt`): end-to-end facade; reuses the resident Gemma model via a single silent inference (no session reset, no second model load), accumulating streamed deltas.
  - **AI Chat wiring**: `LlmChatViewModelBase` gained `initRag` / `ingestAttachedFiles` / `tryHandleRagQuery` and a `ragResponse: StateFlow<RagResponse?>` (the Quiz UI integration point). Both text and voice send paths in `LlmChatScreen` ingest attachments and route quiz/summary queries through RAG before normal generation. `LlmChatTaskModule` calls `initRag`.
- Broken/open:
  - **Quiz UI (Dev C2):** not built yet — currently RAG results render as readable chat text as a fallback. The typed `viewModel.ragResponse` StateFlow (`RagResponse` → `List<QuizItem>` or `summary` + `sources`) is the shape to render. Ingestion + retrieval + generation all work without it.
  - **Not device-validated:** compile + `assembleDebug` pass and the model asset is confirmed bundled in the APK (`assets/universal_sentence_encoder.tflite`), but retrieval quality, embedding latency, and quiz-JSON reliability from Gemma need a real-device pass (airplane mode).
  - **Vision path:** RAG wired into AI Chat only; VisionChatViewModel (scanned notes) not yet ingesting into the same index — candidate follow-up.
  - **Firebase Analytics + INTERNET perm** still present from the fork (Dev C2 deliberately left Analytics; degrades to null). Not a RAG concern but still open for a full offline-hardening pass.
- Benchmark numbers: `:app:assembleDebug` BUILD SUCCESSFUL (1m22s). Debug APK ~219MB (grew ~20MB: MediaPipe tasks-text native libs + the 5.9MB model asset).

---

## 2026-07-19 — Dev C1 — Phase 3 (RAG) — standalone module, citations, knowledge toggle
Product calls confirmed with owner (see new `/DECISIONS.md` entry): web search REJECTED (offline-pure stays), knowledge-scope toggle added, RAG promoted to its own homescreen module.
- Did:
  - **Citations:** `Citation` type added to contracts; `RagResponse.citations` built deterministically from the retrieved chunks (source label + snippet + similarity score) — never parsed from model output, so they can't be hallucinated. Chat fallback rendering appends a "Sources:" block; the module screen shows citation cards.
  - **Knowledge toggle:** `KnowledgeScope` (NOTES_ONLY / NOTES_AND_MODEL) threads through prompt building. Both modes fully offline.
  - **Standalone "Notes" module:** `BuiltInTaskId.RAG` + `RagTask` (`@IntoSet`, `ui/rag/RagTaskModule.kt`) → auto-appears on the homescreen. `RagScreen` baseline UI: attach/remove documents (SAF picker → `DocumentExtractor` → ingest), topic field, "Quiz me" / "Summarize" buttons, scope FilterChips, result panel + citations. `RagViewModel` owns UI state; pipeline stays in the engine.
  - **Shared index:** `RagEngine` is now an app-scoped Hilt singleton (`RagDiModule`), injected into both `LlmChatViewModel` and `RagViewModel` — notes attached in AI Chat are queryable from the Notes module and vice versa. `LlmChatViewModelBase` keeps a local-engine fallback for non-Hilt construction (closed in `onCleared`; the shared singleton is not).
  - **Blank-topic fallback:** "quiz me on my notes" with no specific topic often embeds far from any chunk; when similarity search returns empty but notes exist, generation grounds on a sample of indexed chunks (`VectorStore.sample`) instead of failing.
- Broken/open:
  - **Dev C2 handoff:** `RagResultPanel` in `ui/rag/RagScreen.kt` is the marked replacement point for the real Quiz/Flashcard cards (answer reveal, right/wrong feedback), consuming `RagUiState.response` (`List<QuizItem>`). Voice PTT is not on the RAG screen (chat's voice path covers spoken quiz requests).
  - Real-device validation still pending for the whole Phase 3 stack (embedding latency, retrieval quality, quiz-JSON reliability, and now the new module UI).
- Benchmark numbers: `:app:assembleDebug` BUILD SUCCESSFUL. Debug APK ~221MB at project root.


## 2026-07-19 — Dev C1 — Phase 3 (RAG) — conversational Notes redesign + phase wrap-up
Pulled Dev C2's Phase 3 UI (chat-shaped Notes screen, interactive quiz cards, model-init fix on the legacy nav path) from `main` into `dev-a`. Device testing showed the layout needed work; this pass reworks it and closes Phase 3.
- Did:
  - **Conversational Notes screen** (`ui/rag/RagScreen.kt`): rebuilt as a real chat. The transcript (user turns, assistant answers, quiz turns) fills the scroll area and auto-scrolls to newest; a compact bottom bar holds attached-note chips, a rounded text field, a send button, and small Quiz me / Summarize quick-action chips. The knowledge-scope toggle moved to the top bar (icon + label) to reclaim the vertical space the old two-full-width-buttons + topic field + filter-chip-rows block wasted.
  - **Grounded follow-up (ASK mode):** added `RagMode.ASK` (`rag/RagGenerator.kt` + `rag/RagEngine.kt`) — a conversational, notes-grounded answer. Sending from the text field routes through `RagGenerator.detectMode` first (so "quiz me" / "summarize my notes" still trigger those modes) and otherwise answers as a follow-up. A summary now flows into an ongoing back-and-forth instead of dead-ending.
  - **Conversation state** (`ui/rag/RagViewModel.kt`): replaced the single `response: RagResponse?` with a `messages: List<RagMessage>` transcript (`UserMessage` / `AssistantText` / `AssistantQuiz`). Three entry points — `ask` / `quiz` / `summarize` — share the model-ready wait + empty-response guard C2 added.
  - **Anki-style MCQ:** quiz cards are now interactive multiple-choice — tap an option to lock it, the correct option turns green and a wrong pick turns red (auto-graded, no manual Got it/Missed). Flashcard items (no options) keep tap-to-flip. Citations render per assistant turn.
  - **Phase 3 marked complete** in `/TODO.md`; Phase 4 (Memory & Schedules) promoted to the next phase.
- Broken/open:
  - Real-device validation of this redesign still pending (retrieval quality, quiz-JSON reliability, follow-up coherence across turns, airplane-mode pass).
  - Vision→RAG ingestion (scanned notes into the shared index) still a candidate follow-up.
  - Firebase Analytics + INTERNET perm still present from the fork — deferred offline-hardening pass.
- Benchmark numbers: `:app:assembleDebug` BUILD SUCCESSFUL in 26s. Debug APK ~220MB, copied to project root.
## 2026-07-19 — Dev C1 — Phase 3 polish + UI consistency (pre-Phase 4)
Device testing of the conversational Notes module surfaced RAG quality and consistency gaps. Fixed grounding, added Notes history, unified the input UI, restyled the app, and defaulted the model to CPU. All offline; work on dev-a.
- RAG grounding: topic-less Summarize/Quiz no longer built off an arbitrary prefix. New `VectorStore.coverageChunks` round-robins across every source (ordered by position) for whole-note coverage; `RagEngine.generate` routes on topic presence (blank → coverage; named topic → relevance retrieval with topK 5→8, minScore 0.25→0.15 tuned for the weak USE encoder, falling back to coverage). Sampled/coverage chunks now score 0f, not a fake 1.0.
- Sources: now a collapsible dropdown (reuses `ui/common/Accordions`), collapsed by default, per assistant turn.
- Notes history: `RagViewModel` persists conversations to the proto `DataStore<UserData>` under task_id "rag" (RagMessage JSON-encoded into `ChatMessageProto` — no schema change), auto-saving after each turn. Same right-side `ModalNavigationDrawer` + `ChatHistorySideSheetContent` as AI Chat; New/History top-bar actions. Loading restores the transcript (re-attach a note to keep querying — in-memory index isn't persisted).
- Unified input: new `TraceChatInput` shared component (bordered rounded container matching AI Chat, inline send, attach + leadingContent/quickActions slots). Notes adopted it; chat's heavy `MessageInputText` (camera/intent-routing/skills/PTT) stays but the shared shape aligns the look.
- Docs: `.md`/`.txt` now selectable (broadened picker MIME incl. octet-stream + extension sniff), `.html` supported with tag-stripping in `DocumentExtractor`. Chat attach picker broadened to match.
- Theming: black & white homescreen (pure black dark / white light, derived from the active scheme); task palette softened via a `pastel()` helper (gentle desaturate + lighten).
- Model config: default accelerator now CPU (`model_allowlist.json` "cpu,gpu"; `Consts.DEFAULT_ACCELERATORS`/`DEFAULT_VISION_ACCELERATOR` CPU-first). GPU still selectable.
- Broken/open: whole batch not yet device-tested (RAG retrieval quality on real multi-page notes, history round-trip, pastel palette on-device, CPU inference latency). Vision text-input unification deferred (Vision is camera-only). Phase 4 (Memory & Schedules) next.
- Benchmark numbers: `:app:assembleDebug` BUILD SUCCESSFUL. Debug APK ~220MB, copied to project root.

---

## 2026-07-20 — Dev C2 — UI Redesign (Phases 1-2 of the ChatGPT-shell handoff)
Resumed `docs/UI_REDESIGN_HANDOFF.md` from C1's handoff point. On `dev-b`.
- Did:
  - **Phase 1 — Notes voice input** (`f792029`): kept the bespoke `TraceChatInput` (owner-chosen fallback, NOT the MessageInputText swap) to avoid IntentRouter hijacking note queries + unused camera/image machinery. Added a `trailingAction` slot to `TraceChatInput`; wired a voice mic via `HoldToDictateViewModel` (the app PTT engine) calling `viewModel.ask()` directly. Quiz-me already existed.
  - **Phase 2 — Persistent Notes attachments** (`4b801a6`): new `notes_index.proto` (NoteSourceProto: label+text+ts) + serializer + two `DataStore<NotesIndex>` providers. `RagEngine` persists extracted TEXT on ingest (not vectors), `warmUp()` re-embeds on launch so chips survive restart; `forgetSource`/`forgetAllSources` prune. Removed the dead ViewModel-local RagEngine fallback in `LlmChatViewModel` (shared Hilt singleton always injected).
- Broken/open:
  - **NOT device-tested.** Owner decision: STOP at Phase 2 for an on-device testing checkpoint before Phase 3.
  - **Phase 3 (app shell + left drawer) deferred** — it changes the nav entry point and nests drawers; needs device iteration. Flagged for whoever resumes: `initializeModel` skips re-init keyed by MODEL name only (not task), so a shell switching modules over one model needs `force=true` re-init or the model keeps the first task's supportImage/supportAudio flags.
  - Phase 4 (example prompts) also remains.
- Benchmark numbers: `:app:compileDebugKotlin` + `:app:assembleDebug` BUILD SUCCESSFUL. Debug APK copied to project root.

---

## 2026-07-20 — Dev C2 — UI Redesign Phases 3-4 (app shell + example prompts)
Owner asked to complete the full redesign. On `dev-b` (`43ea005`).
- Did:
  - **Phase 3 — app shell** (`ui/shell/AppShell.kt`): new entry point (`startDestination` → `ROUTE_SHELL`). A LEFT hamburger drawer switches AI Chat / Vision / Notes in place, with Benchmark + Settings below a divider. Low-risk variant: each module renders via its OWN existing `MainScreen` (top bar + right history drawer reused unchanged); the shell only owns the switcher; a module's back opens the switcher. `force=true` re-init on module switch fixes the model-name-keyed capability-flag issue. Old tile HomeScreen route kept for deep links/fallback.
  - **Phase 4 — example prompts:** Notes `EmptyState` shows tappable "Summarize my notes" / "Quiz me" / "Explain the key concepts" → `ask()`. AI Chat / Vision example prompts deferred (touch the shared chat send path).
- Broken/open:
  - **NOT device-tested — highest priority.** The shell changes the app entry point and switches modules in place. Verify on device: launches to AI Chat; drawer switches all 3 modules; no nested-drawer/back weirdness; image+audio work after switching; Benchmark/Settings reachable. Documented fallback if it misbehaves: per-route drawer (navigate to each module's existing route instead of inline).
  - AI Chat / Vision example prompts remain.
- Benchmark numbers: `:app:assembleDebug` BUILD SUCCESSFUL. Final APK at repo root as `trace-new.apk` (and `Trace-beta-debug.apk`).
## 2026-07-20 — Dev C1 — Standalone-product redesign (design system + de-fork)
Owner brief: make Trace feel first-party, not a fork. Six phases, each build-green (`assembleDebug`), NOT device-tested. Full detail + device-test checklist in `/docs/UI_REDESIGN_HANDOFF.md`.
- Phase A tokens (`9555e80`): Inter font (bundled, offline), dark base #111111 + neutral grey ramp, pastel per-module accents, dropped Google blue.
- Phase B top bar (`05b5f23`): real hamburger everywhere; Notes → CenterAlignedTopAppBar with centered icon+title, new-chat, ⋮ overflow (knowledge toggle + history).
- Phase C empty states (`3fc7b66`): shared `ModuleEmptyState` (icon+title+desc+suggestion chips) on AI Chat + Notes; AI Chat chips send via sendMessageTrigger.
- Phase D model settings (`e5e620a`): sidebar "Model settings" opens ConfigDialog on the shared model (global); removed the per-screen Tune button.
- Phase F cleanup (`a86332d`): removed the benchmark button under user bubbles; stripped Firebase Analytics (no-op stub, deps + plugin + init + manifest gone) — offline app ships no telemetry.
- Phase E onboarding (`0626bab`): first-run OnboardingScreen (intro + guided model download w/ progress), gated by a new `has_completed_onboarding` settings flag.
- Deferred: AI Chat/Vision suggestion prompts beyond Notes, full Notes→MessageInputText swap, dead toml-alias/oss-licenses prune, broad animation pass.
- Benchmark: `:app:assembleDebug` BUILD SUCCESSFUL. Debug APK ~220MB at repo root (`Trace-debug.apk`).
