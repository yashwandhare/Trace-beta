# Trace — Roadmap

Phased, end to end, built in a single sprint today to replicate the hackathon build from scratch. Read
`/WORKSTREAM.md` first — every phase below is split into **Dev A** and **Dev B** tasks that touch
different files and do not conflict.

**Merge order every phase (see `/WORKSTREAM.md` for full detail):** Dev B pushes to `dev-b` -> Dev A
merges `dev-b` into `main` -> Dev A finishes their own tasks on `dev-a` -> Dev A merges `dev-a` into
`main` -> Dev A builds and tests combined `main` -> only then does the team move to the next phase.

Each phase has a **gate** — the thing that must be true before moving on. Do not start the next phase
until the gate is met.

## Current status — 2026-07-23

The tested demo build runs on the Samsung Galaxy M35 (6 GB RAM, Android 16) with Gemma 4 E2B-IT.
Phase 3.5 shell/polish and Phase 4 backend and UI implementation are complete in this checkout.
Remaining work is final device QA, integration validation, demo preparation, and explicitly approved
extras. The newly added Memory/Schedules surfaces should be exercised on the demo device before the
closeout push.
Opt-in web search is an intentional non-core feature: it is session-only and off by default; the
airplane-mode core demo remains the required path.

---

## Phase 0a — Strip & Rebrand Baseline (do this first, before Phase 0)

Goal: a stable, renamed, single-model, text-only chatbot baseline. No voice, no screen capture, no RAG —
just confirm the fork works as a plain text LLM chat app under the Trace name, so Phase 0's benchmarking
and system-prompt work happens on a clean, de-branded base rather than on top of Google's reference app
scaffolding.

**Dev A:**
- [ ] Fork is confirmed Apache 2.0. Keep the LICENSE file in the repo (and NOTICE file if one exists,
      attribution intact) — not surfaced in the UI, just present in the repo. Add a short note (README
      or NOTICE) that Trace is modified from Google AI Edge Gallery, Apache 2.0. That's the entire legal
      obligation — everything else (in-app TOS, onboarding, marketing copy, branding) is ordinary product
      content and free to remove
- [ ] Confirm the single hardcoded model (Gemma 4 E2B-IT) loads correctly after stripping

**Dev B:**
- [ ] Remove Google AI Edge Gallery product branding, in-app marketing copy, and TOS/onboarding screens
      — none of this is a license requirement (see Dev A's task above for what must stay), all of it is
      free to strip
- [ ] Remove the model list/picker UI and any other model-switching UI entirely (single model only, no
      selector)
- [ ] Remove unused example modules/screens/buttons that aren't part of Trace's feature set
- [ ] Rename all remaining app-facing strings, package/app name, and UI branding to "Trace"
- [ ] Apply a simple dark theme across the remaining screens
- [ ] Confirm end state: app opens directly to a plain text chat screen, Gemma 4 E2B-IT loaded, no
      picker, no unrelated screens, Trace-branded, dark themed

**Gate:** App opens to a simple, working, Trace-branded, dark-themed, text-only chatbot with Gemma 4
E2B-IT as the only model, nothing else in the UI. This becomes the baseline Phase 0 benchmarks run against
and Phase 0's system prompt is tuned against. Merged to `main`, confirmed working by Dev A before Phase 0
begins.

---

## Phase 0 — Foundation

**Dev A:**
- [ ] Confirm forked Edge Gallery app builds and runs unmodified, as a baseline
- [ ] Get Gemma 4 E2B-IT running via LiteRT-LM on real target device (not emulator)
- [ ] Benchmark decode speed, cold load, warm load — record actual numbers
- [ ] Confirm model loads once and stays resident (no per-request reload)

**Dev B:**
- [ ] Strip model-catalog/picker screens from the forked UI — replace with a single splash/loading
      screen that assumes one hardcoded model (UI work only; does not touch model-loading code — Dev A
      owns wiring the actual load call)
- [ ] Set up own local branch (`dev-b`), confirm build environment works, confirm can run the app on
      their own device

**Gate:** App launches, loads Gemma 4 E2B automatically (Dev A's wiring, on Dev B's stripped screen), and
returns a response to a hardcoded test prompt, on a real device, in acceptable time. Merged to `main` and
confirmed working by Dev A.

---

## Phase 1 — Core Voice Loop & AI Chat (Module)

**Dev A:**
- [ ] Wire push-to-talk button behavior (capture start/stop)
- [ ] Route captured audio to Gemma's native audio path
- [ ] Wire TTS for spoken responses
- [ ] Build minimal rule-based intent router (simple command vs. needs-Gemma)
- [ ] Extend AI Chat logic to handle explicit file and image attachments as RAG/processing inputs

**Dev B:**
- [ ] Build the push-to-talk button UI component itself (visual state: idle / listening / processing)
- [ ] Implement AI Chat UI for explicitly attaching files and images from the device

**Gate:** Press the button, ask a spoken question, get a spoken answer back. Attach a file and ask about it. Merged to `main`, confirmed working.

---

## Phase 2 — Screen Explain & Vision (Module)

**Dev A:**
- [ ] MediaProjection permission flow implemented and tested for Screen Explain
- [ ] Screen capture → image passed to Gemma alongside the voice question
- [ ] Wire device camera capture logic for the new Vision module (scan-and-chat)

**Dev B:**
- [ ] Build the new Vision module homescreen entry and camera capture UI
- [ ] Build the chat overlay for Vision (taking a photo of the real world and asking about it)
- [ ] Begin OCR quality test pass against real sample scanned/handwritten notes

**Gate:** Screen Explain works live on-device. Vision module opens camera, captures physical world object, and correctly answers questions about it. Merged to `main`, confirmed working.

---

## Pre-Phase 3 Stabilization

**Dev C:**
- [ ] Fix TTS reading typed text out loud and reduce first-speech latency
- [ ] Fix Intent Router to handle typed inputs and correctly parse commands
- [ ] Fix Screen Explain MediaProjection lifecycle and ensure Gemma receives the captured screen image

**Gate:** Screen Explain works continuously without crashing, TTS responds fast and only when appropriate. Merged to `main`, confirmed working.

---

## Phase 3 — RAG (Module)

**Dev A:**
- [ ] Qdrant Edge Rust crate compiled for Android target
- [ ] Minimal JNI bridge: index test documents, run a query, confirm results reach Kotlin correctly
- [ ] Embedding pipeline wired (FastEmbed or on-device embeddings)
- [ ] End-to-end: explicitly attached notes → OCR → embed → index → voice query → retrieval → Gemma-generated quiz/summary
- [ ] **Go/no-go call on this integration** — if not working cleanly, switch to fallback (pure-Kotlin vector search or single-note summarize)

**Dev B:**
- [ ] Build Quiz/Flashcard UI screens in Compose (question card, answer reveal, right/wrong feedback). Build against a mock data shape.
- [ ] Finish OCR quality findings from Phase 2, report to Dev A

**Dev C (If joining):**
- [ ] Support Dev A in JNI bridge development and embedding pipeline validation
- [ ] Cross-check RAG data structures between Dev A's backend and Dev B's UI models

**Gate:** Voice-triggered quiz generation from explicitly attached notes, rendered in UI, working offline. Merged to `main`, confirmed working.

---

## Phase 4 — Memory & Schedules (Modules) — COMPLETE

**Dev A:**
- [x] Build the Memory structured data store with read/write paths for both user-authored and system-authored entries
- [x] Build the Schedules backend (AlarmManager, notification channels, persistence, boot re-arm, exact-alarm fallback)
- [x] Expose generation of schedules from scanned Vision prescriptions or attached Chat documents to the Memory store and scheduling system
- [x] Implement Quiz-from-schedule logic (triggering RAG quiz via AlarmManager deeplink)

**Dev B:**
- [x] Build the Memory sidebar UI (viewing, editing, and adding user-authored and system-authored schedule entries)
- [x] Build the Schedules list UI and notification interaction flows
- [x] Apply the Phase 4 UI polish pass to the new Memory/Schedules surfaces and their shell entry points

**Gate:** Implementation is complete in this checkout. Final device verification remains before the
closeout push: add memory entries, generate a reminder from Vision or Chat, confirm persistence, and
confirm the Android notification and quiz deeplink behavior.

---

## Closeout — Integration, Polish, Demo Prep

**Dev A:**
- [ ] Full integration pass on `main` across all 5 modules
- [ ] Demo script rehearsal — see `/PRD.md` §6 Success Criteria
- [ ] Airplane-mode full test run across every feature — confirm zero network dependency

**Dev B:**
- [ ] UI polish pass across all screens built so far
- [ ] Run full QA checklist across every feature, log bugs for Dev A to triage
- [ ] Support demo rehearsal

**Gate:** All modules work reliably, live, offline, multiple times in a row. This is the project's
closeout state for the hackathon prototype.

---

## Phase 6 — Post-sprint / future scope (not built today)

- Form-Fill Assist (confirmation-gated, per `/CONSTRAINTS.md`)
- Proactive organization & reminders
- True always-on wake-word
- Broader file-type support
- Custom user-created homescreen modules (explicitly cut)
