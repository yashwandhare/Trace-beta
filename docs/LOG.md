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
