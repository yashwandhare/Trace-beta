# UI Redesign — ChatGPT-style Shell (Phase 3.5, pre-Phase 4)

**Owner:** Dev C1 (started) → Dev C2 (continue from wherever C1 stopped)
**Branch:** dev-a (push to main on handoff)
**Date started:** 2026-07-20
**Status legend:** ☐ todo · ◐ in progress · ☑ done

---

## Goal
Replace the tile homescreen with a single **ChatGPT-style surface**: the app opens straight
into **AI Chat**; a **left hamburger drawer** switches between the 3 modules (AI Chat, Vision,
Notes), Benchmark/Settings, and per-module chat history. One shared input everywhere, configured
per module. Vision loses document-attach; Notes gains voice + persistent attachments; all chat
windows get example prompts and icon+description headers. **100% offline. dev-a only.**

## Locked decisions (from owner)
- Drawer middle section = **Benchmark + Settings** (not scope toggle / model selector).
- Notes persistence = **save extracted text, re-embed on launch** (not raw vectors).
- Reference: ChatGPT mobile (hamburger drawer with modules → divider → history; empty chat with
  example prompt rows; input with `+` left, mic + send right). Not pixel-exact — match the shape.

---

## Architecture notes (what already exists — reuse it)
- **`ChatViewWrapper` / `ChatView`** (`ui/llmchat/LlmChatScreen.kt`, `ui/common/chat/ChatView.kt`)
  already host a full chat with a RIGHT-side history `ModalNavigationDrawer` and take
  `showImagePicker` / `showAudioPicker` flags. Vision already routes into `ChatViewWrapper`
  after a capture (`ui/vision/VisionCameraScreen.kt:118`).
- **`MessageInputText`** (`ui/common/chat/MessageInputText.kt:159`) is the superset input:
  per-feature flags (`showImagePicker`, `showAudioPicker`, `showSkillsPicker`, `showMcpPicker`,
  `showPromptTemplatesInMenu`, `showStopButtonWhenInProgress`) + built-in voice PTT
  (HoldToDictate). The `+` menu's "Attach document / Take picture / Pick from album" are ALL
  gated by the single `showImagePicker` today (line ~718) — must split into finer flags.
- **Nav** (`ui/navigation/GalleryNavGraph.kt`): 3 routes — `homepage` (HomeScreen tiles) →
  `route_model/{taskId}/{modelName}` (hosts task screens via CustomTask.MainScreen; RAG/Vision
  are "legacy tasks" rendered without CustomTaskScreen) → `benchmark`. Start = `homepage`.
- **History persistence**: proto `DataStore<UserData>` (`di/AppModule.kt:114`); `ChatViewModel`
  save/load/list (`ui/common/chat/ChatViewModel.kt`); UI `ChatHistorySideSheetContent`
  (`ui/common/chat/ChatHistorySideSheet.kt:60`) is generic over `ChatSessionProto`. Notes history
  already persisted the same way via `RagViewModel` (task_id "rag").
- **Notes/RAG**: in-memory `VectorStore` (`rag/VectorStore.kt`) — vanishes on process death.
  `RagViewModel.ask/quiz/summarize` drive generation with plain strings; transcript is
  `RagUiState.messages` (UserMessage / AssistantText / AssistantQuiz).
- **Task registration**: `RagTask` / `VisionTask` are `CustomTask`s (`@IntoSet`), each exposes
  `task` (id, label, icon, description) + `MainScreen(data)`.

---

## TODO

### Phase 1 — Unified input, per-module config  ◐ (partially done — C2 finish Notes)
Standardize on `MessageInputText` as the single input across all 3 modules; differ only by flags.
- ☑ Split the `+`-menu gating in `MessageInputText`: the three items now gate on
  `showAttachDocument` (document) and `showImagePicker` (Take picture / Pick from album)
  independently. Threaded through `ChatView` → `ChatPanel` → `ChatViewWrapper` → `LlmChatScreen`.
  DONE in commit `bc77a7e`.
- ☑ Added `leadingSendAction: @Composable () -> Unit` slot to `MessageInputText`'s send row
  (renders left of the mic/send buttons). Ready for Notes' Quiz-me button. DONE `bc77a7e`.
- ☑ **AI Chat**: `showAttachDocument=true`, `showImagePicker=true`, `showAudioPicker=true`
  (`LlmChatTaskModule.kt:154`). No behavior change. DONE.
- ☑ **Vision**: document attach removed — `ChatViewWrapper` call in `VisionCameraScreen.kt:124`
  passes `showImagePicker=true` and leaves `showAttachDocument` default false (item 5). DONE.
- ☐ **Notes**: NOT DONE. Replace the bespoke `TraceChatInput` in `RagScreen.kt` (the block at
  ~line 251, `// ---- Compact input bar (shared TraceChatInput) ----`) with `MessageInputText`:
    - Pass `task` (Notes task via `modelManagerViewModel.getTaskById(BuiltInTaskId.RAG)`),
      `showAttachDocument=true`, `showImagePicker=false`, `showAudioPicker=false` (voice PTT is
      built into MessageInputText, so it comes for free — item 2's "add voice to Notes").
    - `leadingSendAction = { }` → a circular "Quiz me" icon button (item 2: quiz icon in a circle
      left of send). Wire onClick → `viewModel.quiz(model, query)`.
    - Bridge `onSendMessage: (List<ChatMessage>)`: extract `ChatMessageText.content` →
      `viewModel.ask(model, text)`; extract `ChatMessageFile`s → `viewModel.ingestDocument(...)`
      (Notes ingests on attach, so attaching a doc should call ingest, not queue it as a message).
    - Keep Notes' own transcript rendering (`Conversation`, quiz cards) and the attached-source
      chips + Summarize action somewhere (they were in `TraceChatInput`'s slots — reuse
      `AttachedSourcesRow`/`QuizSummarizeActions` above the input, or fold into leadingSendAction).
    - CAUTION: `MessageInputText` routes sends through `IntentRouter` (`dispatchIntent`, line ~355)
      which can hijack "file fetch"/"screen explain" phrasings. Normal note queries fall through
      to `LLM_CHAT → onSendMessage`, so it's usually fine, but verify on device. If it's a problem,
      keep `TraceChatInput` for Notes and just add a voice mic + Quiz-left-of-send to it instead —
      simpler and avoids the IntentRouter. **This fallback is acceptable.**
  - ☐ Build green; commit.

**C2 START HERE:** finish the Notes bullet above, then continue to Phase 2.

### Phase 2 — Persistent Notes attachments  ☐
Notes attachments survive app restart (save extracted text, re-embed on launch).
- ☐ New `proto/notes_index.proto`: `NoteSourceProto { source_label, extracted_text,
  ingested_at_ms }`, `NotesIndex { repeated NoteSourceProto sources }`. Serializer + a
  `DataStore<NotesIndex>` provider in `di/AppModule.kt` (mirror the UserData one).
- ☐ `RagRepository`/`RagEngine`: on `ingest`, persist source text; on `removeSource`/`clearIndex`,
  update the store. Inject the DataStore (RagEngine is an app singleton via `RagDiModule`).
- ☐ `warmUp()`: on first RagEngine/RagViewModel init, load persisted sources → re-chunk+re-embed
  into `VectorStore` on a background dispatcher (~1-2s). `indexedSources` then repopulates so the
  chips reappear. Show existing "Indexing…" state if a query lands first.
- ☐ Build green; commit.

### Phase 3 — App shell + left hamburger drawer + shared top bar  ☐  (largest)
AI Chat becomes the home surface; a left drawer switches modules; one shared top bar.
- ☐ `ui/shell/AppShell.kt`: owns a LEFT `ModalNavigationDrawer`; state `activeModule`
  (AI_CHAT default / VISION / NOTES); renders the active module inline (switch in place, no nav).
- ☐ `ui/shell/AppDrawer.kt` content top→bottom: "Trace" header → **modules** (vertical,
  active highlighted) → divider → **Benchmark** (→ `route_benchmark/<model>`) + **Settings**
  (existing entry) → divider → **history for the active module** (reuse
  `ChatHistorySideSheetContent`, filtered to active task.id; AI Chat/Vision use
  `ChatViewModel.historySessions`, Notes uses `RagViewModel.historySessions`).
- ☐ `ui/shell/ShellTopBar.kt`: `☰ | moduleIcon + name + one-line description | + newChat | ⋮`.
  Overflow ⋮ = per-module extras (Notes knowledge-scope toggle; model selector if >1 model).
- ☐ Nested-drawer fix: add `useExternalDrawer` (+ `onOpenDrawer`) to `ChatView` so it renders NO
  internal drawer when hosted in the shell, and its hamburger calls `onOpenDrawer`. Add a
  `hideOwnTopBar`/top-bar slot to `ChatView`, `RagScreen`, and the Vision screen so the shell
  provides the bar.
- ☐ `GalleryNavGraph`: change `startDestination` to a new `ROUTE_SHELL` rendering `AppShell`.
  Keep `route_model` (deep links) + `benchmark`. HomeScreen file kept but no longer the entry.
- ☐ Build green; commit.

### Phase 4 — Example demo prompts in empty chats  ☐  (C2 may do)
- ☐ AI Chat & Vision: pass `emptyStateComposable` rendering 2-3 tappable prompt rows (icon+text);
  tap → send path (Vision prompts assume an attached image).
- ☐ Notes: example chips in `RagScreen` `EmptyState` ("Summarize my notes", "Quiz me",
  "Explain the key concepts"); tap → `ask/quiz/summarize`.
- ☐ Build green; commit.

### Phase 5 — Icon + description module headers  ☑ (folded into Phase 3 ShellTopBar)
- Titles: AI Chat "Chat with Trace, fully on-device" · Vision "Ask about a photo or your camera"
  · Notes "Quiz & summarize your own notes".

---

## Files
**New:** `ui/shell/AppShell.kt`, `ui/shell/AppDrawer.kt`, `ui/shell/ShellTopBar.kt`,
`proto/notes_index.proto` (+ serializer + DI provider).
**Modified:** `ui/navigation/GalleryNavGraph.kt`, `ui/common/chat/ChatView.kt`,
`ui/common/chat/MessageInputText.kt`, `ui/llmchat/LlmChatScreen.kt`,
`ui/vision/VisionCameraScreen.kt` + `VisionTaskModule.kt`, `ui/rag/RagScreen.kt` +
`RagViewModel.kt` + `RagTaskModule.kt`, `rag/RagRepository.kt` / `RagEngine.kt` /
`VectorStore.kt`, `di/AppModule.kt`.

## Risks / notes
- **Nested drawers** — must gate `ChatView`'s internal drawer off in the shell (biggest risk).
- Phases 1–2 are self-contained and safe to ship even if the shell (Phase 3) needs iteration.
- Not device-tested until the end — call out in commits/LOG.
- Keep everything offline (no network paths added).

## Handoff protocol
When C1 stops: tick the boxes above for what's done, note the last green commit hash here, and
push to main so C2 can pull and continue from the next unchecked box.

**Progress log:**
- 2026-07-20: doc created; starting Phase 1.
- 2026-07-20: Phase 1 shared-input foundation done (commit `bc77a7e`): flag split +
  leadingSendAction slot; AI Chat all-on; Vision doc-attach removed. Notes input adoption still
  pending (see Phase 1 Notes bullet). Last green commit on main handoff: pushed to main.
  **C2 resumes at Phase 1 → Notes bullet.** Phases 2-4 untouched.
