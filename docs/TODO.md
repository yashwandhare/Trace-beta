# Trace — To-Do

Current, actionable tasks for **today's sprint**. This mirrors `/ROADMAP.md` phases exactly — if
something's not listed here for your role, it's not your task yet. Read `/WORKSTREAM.md` for the full
ownership rules before starting anything.

**Rule for both devs and any agent working on this repo:** work only from this file and `/ROADMAP.md`.
Do not invent tasks, do not "improve" something outside the current phase, do not touch a file assigned
to the other dev. If a task is unclear or blocked, stop and flag it — do not guess.

Check off items as they're done. Move to the next phase's tasks only after that phase's gate (see
`/ROADMAP.md`) is met and merged to `main`.

---

## ACTIVE PHASE: Phase 0a — Strip & Rebrand Baseline
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

## COMPLETED: Phase 1 — Core Voice Loop

### Dev A
- [x] Wire push-to-talk capture start/stop (changed to tap-to-toggle)
- [x] Route captured audio to Gemma's native audio path (used text via Android SpeechRecognition)
- [x] Wire TTS output
- [x] Build minimal rule-based intent router

### Dev B
- [x] Build push-to-talk button UI (idle/listening/processing states)
- [x] Begin File Fetch: isolated module

### Merge checkpoint
- [x] Dev A merges `dev-b` → `main`
- [x] Dev A merges `dev-a` → `main`
- [x] Dev A builds and tests combined `main`
- [x] Gate met? → Phase 1 complete.

---

## COMPLETED: Phase 2 — Screen Explain

### Dev A
- [x] MediaProjection permission flow
- [x] Screen capture → Gemma vision + voice question → response (Continuous Gemini Live mode implemented)

### Dev B
- [x] Finish File Fetch, connect to intent router's direct-action path
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

## ACTIVE PHASE: Phase 3 — Notes RAG Pipeline
(Highest-risk phase — full detail in `/ROADMAP.md`.)

### Dev A
- [ ] Qdrant Edge Rust crate for Android + JNI bridge
- [ ] Embedding pipeline
- [ ] End-to-end notes → OCR → embed → index → query → quiz/summary
- [ ] Go/no-go call on JNI integration — log outcome in `/DECISIONS.md`

### Dev B
- [ ] Quiz/Flashcard UI in Compose, built against a `QuizItem` mock data shape Dev A defines first
- [ ] Report OCR findings from Phase 2

### Dev C (If joining)
- [ ] Coordinate with Dev A and Dev B on RAG pipeline data structures
- [ ] Assist with JNI bridge or embedding pipeline depending on Rust/Kotlin expertise

### Merge checkpoint — same pattern as above.

---

## LATER: Phase 4 — Integration, Polish, Demo Prep
(Do not start until Phase 3 gate is confirmed met. Full detail in `/ROADMAP.md`.)

### Dev A
- [ ] File Summarize feature
- [ ] Full integration pass on `main`
- [ ] Demo script rehearsal
- [ ] Airplane-mode full test run, every feature

### Dev B
- [ ] UI polish pass
- [ ] Full QA checklist run, log bugs for Dev A
- [ ] Support demo rehearsal

### Merge checkpoint — same pattern as above. This is the sprint's end state.

---

## Logistics (either dev, not code)
- [ ] Confirm Kaggle submission format/requirements ahead of time
