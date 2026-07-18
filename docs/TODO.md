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
- [ ] Fork is confirmed Apache 2.0. Keep the LICENSE file in the repo (and NOTICE file if one exists,
      with its attribution content intact) — quietly in the repo, not surfaced in the app UI. Add a
      short note in the repo (e.g., in README or NOTICE) that Trace is modified from Google AI Edge
      Gallery, Apache 2.0 licensed. That's the full legal obligation — everything else (in-app TOS
      screens, onboarding, marketing copy, branding) is free to strip, it's product content, not license
      text
- [ ] Confirm Gemma 4 E2B-IT loads correctly after Dev B's stripping work

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
- [ ] Dev A merges `dev-b` → `main`
- [ ] Dev A merges `dev-a` → `main`
- [ ] Dev A builds and tests combined `main`
- [ ] Gate met? → move to Phase 0.

---

## NEXT: Phase 0 — Foundation

### Dev A (Kazuto) — branch `dev-a`
- [ ] Confirm forked Edge Gallery app builds and runs unmodified
- [ ] Get Gemma 4 E2B-IT running via LiteRT-LM on real device
- [ ] Benchmark: decode speed, cold load, warm load — record real numbers in `/LOG.md`
- [ ] Confirm model loads once, stays resident, no reload per request

### Dev B (Yash) — branch `dev-b`
- [ ] Strip model-picker/catalog screens, replace with single hardcoded-model splash/loading screen
      (UI only — do not touch model-loading logic, that's Dev A's)
- [ ] Confirm own build environment and device run works
- [ ] Push to `dev-b`, open merge into `main`

### Merge checkpoint
- [ ] Dev A merges `dev-b` → `main`
- [ ] Dev A merges `dev-a` → `main`
- [ ] Dev A builds and tests combined `main`
- [ ] Gate met? → move to Phase 1. If not, fix before proceeding.

---

## NEXT: Phase 1 — Core Voice Loop
(Do not start until Phase 0 gate is confirmed met.)

### Dev A
- [ ] Wire push-to-talk capture start/stop
- [ ] Route captured audio to Gemma's native audio path
- [ ] Wire TTS output
- [ ] Build minimal rule-based intent router

### Dev B
- [ ] Build push-to-talk button UI (idle/listening/processing states) — Compose UI only, callbacks wired
      to Dev A's logic
- [ ] Begin File Fetch: isolated module, `fun findFile(query: String): FileResult?`, no dependency on
      model/voice/router code yet

### Merge checkpoint
- [ ] Dev A merges `dev-b` → `main`
- [ ] Dev A merges `dev-a` → `main`
- [ ] Dev A builds and tests combined `main`
- [ ] Gate met? → move to Phase 2.

---

## LATER: Phase 2 — Screen Explain
(Do not start until Phase 1 gate is confirmed met. Full task detail in `/ROADMAP.md`.)

### Dev A
- [ ] MediaProjection permission flow
- [ ] Screen capture → Gemma vision + voice question → response

### Dev B
- [ ] Finish File Fetch, connect to intent router's direct-action path (coordinate interface with Dev A
      first)
- [ ] OCR quality test pass on real sample notes, report findings

### Merge checkpoint — same pattern as above.

---

## LATER: Phase 3 — Notes RAG Pipeline
(Do not start until Phase 2 gate is confirmed met. Highest-risk phase — full detail in `/ROADMAP.md`.)

### Dev A
- [ ] Qdrant Edge Rust crate for Android + JNI bridge
- [ ] Embedding pipeline
- [ ] End-to-end notes → OCR → embed → index → query → quiz/summary
- [ ] Go/no-go call on JNI integration — log outcome in `/DECISIONS.md`

### Dev B
- [ ] Quiz/Flashcard UI in Compose, built against a `QuizItem` mock data shape Dev A defines first
- [ ] Report OCR findings from Phase 2

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
