# Trace — To-Do

Current project checklist. Phases 0 through 4 are complete; the remaining work is final polish,
validation, demo preparation, and explicitly approved extras. Read `/WORKSTREAM.md` before taking
ownership of a task.

**Rule for both devs and any agent working on this repo:** work only from this file and `/ROADMAP.md`.
Do not invent tasks, do not "improve" something outside the current phase, do not touch a file assigned
to the other dev. If a task is unclear or blocked, stop and flag it — do not guess.

Check off items as they're done. Move to the next phase's tasks only after that phase's gate (see
`/ROADMAP.md`) is met and merged to `main`.

---

## COMPLETED: Phase 0a — Strip & Rebrand Baseline
(Do this before Phase 0. Goal: stable, renamed, single-model, text-only chatbot baseline — no voice,
no screen capture, no RAG yet.)

### Dev A (Kazuto) — branch `dev-a`
- [x] Fork is confirmed Apache 2.0. Keep the LICENSE file in the repo (and NOTICE file if one exists,
      with its attribution content intact) — quietly in the repo, not surfaced in the app UI. Add a
      short note in the repo (e.g., in README or NOTICE) that Trace is modified from Google AI Edge
      Gallery, Apache 2.0 licensed. That's the full legal obligation — everything else (in-app TOS
      screens, onboarding, marketing copy, branding) is free to strip, it's product content, not license
      text
- [x] Confirm Gemma 4 E2B-IT loads correctly after Dev B's stripping work

### Dev B (Yash) — branch `dev-b`
- [ ] Remove Google product branding, marketing copy, and TOS/onboarding screens — all of this is free
      to strip, none of it is a license requirement (see Dev A's task above for what actually must stay)
- [ ] Remove model list/picker UI entirely — single hardcoded model, no selector
- [ ] Remove unused example modules/screens/buttons outside Trace's feature set
- [ ] Rename all app-facing strings, package/app name, UI branding to "Trace"
- [ ] Apply simple dark theme across remaining screens
- [ ] Confirm: app opens directly to a plain text chat screen, Gemma 4 E2B-IT loaded, no picker, no
      unrelated screens, Trace-branded, dark themed

### Merge checkpoint
- [x] Dev A merges `dev-b` → `main`
- [x] Dev A merges `dev-a` → `main`
- [x] Dev A builds and tests combined `main`
- [x] Gate met? → move to Phase 0.

---

## NEXT: Phase 0 — Foundation

### Dev A (Kazuto) — branch `dev-a`
- [x] Confirm forked Edge Gallery app builds and runs unmodified
- [x] Get Gemma 4 E2B-IT running via LiteRT-LM on real device
- [x] Benchmark: decode speed, cold load, warm load — record real numbers in `/LOG.md`
- [x] Confirm model loads once, stays resident, no reload per request

### Dev B (Yash) — branch `dev-b`
- [x] Strip model-picker/catalog screens, replace with single hardcoded-model splash/loading screen
      (UI only — do not touch model-loading logic, that's Dev A's)
- [x] Confirm own build environment and device run works
- [x] Push to `dev-b`, open merge into `main`

### Merge checkpoint
- [x] Dev A merges `dev-b` → `main`
- [x] Dev A merges `dev-a` → `main`
- [x] Dev A builds and tests combined `main`
- [x] Gate met? → move to Phase 1. If not, fix before proceeding.

---

## COMPLETED: Phase 1 — Core Voice Loop & AI Chat (Module)

### Dev A
- [x] Wire push-to-talk capture start/stop (changed to tap-to-toggle)
- [x] Route captured audio to Gemma's native audio path (used text via Android SpeechRecognition)
- [x] Wire TTS output
- [x] Build minimal rule-based intent router
- [x] Extend AI Chat logic to handle explicit file and image attachments as RAG/processing inputs

### Dev B
- [x] Build push-to-talk button UI (idle/listening/processing states)
- [x] Implement AI Chat UI for explicitly attaching files and images from the device

### Merge checkpoint
- [x] Dev A merges `dev-b` → `main`
- [x] Dev A merges `dev-a` → `main`
- [x] Dev A builds and tests combined `main`
- [x] Gate met? → Phase 1 complete.

---

## COMPLETED: Phase 2 — Screen Explain & Vision (Module)

### Dev A
- [x] MediaProjection permission flow
- [x] Screen capture → Gemma vision + voice question → response (Continuous Gemini Live mode implemented)
- [x] Wire device camera capture logic for the new Vision module (scan-and-chat)

### Dev B
- [x] Build the new Vision module homescreen entry and camera capture UI
- [x] Build the chat overlay for Vision (taking a photo of the real world and asking about it)
- [x] OCR quality test pass on real sample notes, report findings

### Merge checkpoint
- [x] Dev A merges `dev-b` → `main`
- [x] Dev A merges `dev-a` → `main`
- [x] Dev A builds and tests combined `main`
- [x] Gate met? → Phase 2 complete.

---

## COMPLETED: Pre-Phase 3 Stabilization

### Dev C
- [x] Fix TTS: read aloud only for voice-originated prompts; reduce latency via chunked emission.
- [x] Fix Intent Router: route typed commands through the router; improve regex and token matching.
- [x] Fix Screen Explain: retain request through consent, wait for first frame, pass image to Gemma.

### Merge checkpoint
- [x] Dev C builds and tests combined `main`
- [x] Gate met? → Pre-Phase 3 complete.

---

## COMPLETED: Phase 3 — RAG (Module)
(Highest-risk phase — full detail in `/ROADMAP.md`. Pure-Kotlin fallback shipped; Qdrant/JNI was a No-Go, see `/DECISIONS.md`.)

### Dev A
- [x] ~~Qdrant Edge Rust crate for Android + JNI bridge~~ → No-Go; replaced with pure-Kotlin in-memory cosine store (`rag/VectorStore.kt`)
- [x] Embedding pipeline (MediaPipe TextEmbedder + bundled Universal Sentence Encoder, `rag/TextEmbedderHelper.kt`)
- [x] End-to-end: explicitly attached notes → OCR → embed → index → query → quiz/summary/ask
- [x] Go/no-go call on JNI integration — logged in `/DECISIONS.md`

### Dev B
- [x] Quiz/Flashcard UI in Compose against the `QuizItem` contract — Anki-style interactive MCQ + flashcards in the conversational Notes screen
- [x] Report OCR findings from Phase 2

### Dev C
- [x] Coordinate with Dev A and Dev B on RAG pipeline data structures (`rag/RagContracts.kt`)
- [x] Built the embedding pipeline, engine, standalone module, citations, knowledge toggle, and conversational ASK follow-up

### Merge checkpoint — same pattern as above.

---

## COMPLETED: Phase 4 — Memory & Schedules (Modules)
(The backend and user-facing Memory/Schedules surfaces are implemented in this checkout. The new
surfaces still need final device QA and polish on the demo build.)

### Dev A
- [x] Build the Memory structured data store for user-authored and system-authored entries
- [x] Build the Schedules backend (AlarmManager, Notification Channels, persistence, boot re-arm, and exact-alarm fallback)
- [x] Expose reminder generation from scanned Vision prescriptions or attached Chat documents to Memory and the scheduling system
- [x] Implement Quiz-from-schedule logic (AlarmManager deeplink into RAG)

### Dev B
- [x] Build the Memory sidebar UI (viewing, editing, and adding user-authored and system-authored schedule entries)
- [x] Build the Schedules list UI and notification interaction flows
- [x] Apply the Phase 4 UI polish pass to the new Memory/Schedules surfaces and their shell entry points

### Merge checkpoint — same pattern as above.

---

## REMAINING: Final Polish, QA & Demo Prep
(This is the project closeout checklist, not a new core product phase.)

### Dev A
- [ ] Full integration pass on `main` across all 5 modules
- [ ] Demo script rehearsal
- [ ] Airplane-mode full test run, every feature

### Dev B
- [ ] UI polish pass
- [ ] Full QA checklist run, log bugs for Dev A
- [ ] Support demo rehearsal

### Closeout checkpoint
- [ ] Merge the final reviewed work into `main`
- [ ] Build and test the final demo artifact on the Samsung Galaxy M35

---

## Logistics (either dev, not code)
- [ ] Confirm Kaggle submission format/requirements ahead of time

---

## PENDING: Dev-B — Intent Router & Memory/Schedules Hardening
(Status: queued — do NOT begin until owner gives the go-ahead. Work only on `dev-2`; never push to `main`.)

### 1. Improve the intent router
- [ ] Audit the current `IntentRouter` + `RuleBasedRouter` for gaps, false positives, and missed natural phrasings.
- [ ] Make routing as smart as possible without an NLP model (rule/keyword/regex-based, tolerant of near-natural-language commands).
- [ ] Ensure typed and voice paths route identically.

### 2. Device-control actions via the intent router
- [ ] Flashlight on/off
- [ ] Wi-Fi toggle
- [ ] Bluetooth toggle
- [ ] Open an app by name
- [ ] (Scope guard: keep confirmation-gating for any externally-visible or state-changing action per `/CONSTRAINTS.md`.)

### 3. Near-natural-language command tolerance
- [ ] Accept variations like "turn the torch on", "switch on flashlight", "can you open whatsapp", "kill bluetooth", etc.
- [ ] No NLP model — stay deterministic, but be as forgiving as a rule-based router can be.

### 4. Schedule & Memory bug fixes
- [ ] Audit Memory store + Schedule backend + MemoryScheduleScreen for UX/correctness bugs.
- [ ] Make Memory/Schedules as good as possible for an end user (reliability, clarity, edge cases).
- [ ] Verify reminder creation, persistence across reboot, cancellation, edit re-arm, and empty/error states.

### 5. Branch hygiene
- [ ] All of the above stays on `dev-2` only. Do not push to `main`.

