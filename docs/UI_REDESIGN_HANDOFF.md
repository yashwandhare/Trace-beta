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

### Phase 1 — Unified input, per-module config  ☐
Standardize on `MessageInputText` as the single input across all 3 modules; differ only by flags.
- ☐ Split the `+`-menu gating in `MessageInputText`: replace the single `showImagePicker` guard
  over the three items with `showAttachDocument`, `showTakePhoto`, `showPickImage` (keep
  `showAudioPicker`). Thread these through `ChatView` → `ChatViewWrapper` → `LlmChatScreen`.
- ☐ Add an optional `leadingSendAction: @Composable () -> Unit` slot to `MessageInputText`'s
  send row (left of the send button) for Notes' circular "Quiz me" button.
- ☐ **AI Chat**: doc + photo + album + audio + voice + history (all true). No behavior change.
- ☐ **Vision**: photo + album ONLY — `showAttachDocument=false`, `showAudioPicker=false`
  (removes attach-doc, item 5). Verify image support stays on.
- ☐ **Notes**: adopt `MessageInputText` in `RagScreen` (replaces the bespoke `TraceChatInput`
  usage): `showAttachDocument=true`, photo/album/audio off, voice on. Put the circular Quiz-me
  button in `leadingSendAction`. Bridge send: extract text → `RagViewModel.ask`; extract
  `ChatMessageFile`s → `RagViewModel.ingestDocument`. Keep Notes' own transcript/quiz rendering.
- ☐ Build green; commit.

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
