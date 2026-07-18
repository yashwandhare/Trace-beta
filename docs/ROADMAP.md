# Trace — Roadmap

Phased, end to end, built in a single sprint today to replicate the hackathon build from scratch. Read
`/WORKSTREAM.md` first — every phase below is split into **Dev A** and **Dev B** tasks that touch
different files and do not conflict.

**Merge order every phase (see `/WORKSTREAM.md` for full detail):** Dev B pushes to `dev-b` -> Dev A
merges `dev-b` into `main` -> Dev A finishes their own tasks on `dev-a` -> Dev A merges `dev-a` into
`main` -> Dev A builds and tests combined `main` -> only then does the team move to the next phase.

Each phase has a **gate** — the thing that must be true before moving on. Do not start the next phase
until the gate is met.

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

## Phase 1 — Core Voice Loop

**Dev A:**
- [ ] Wire push-to-talk button behavior (capture start/stop)
- [ ] Route captured audio to Gemma's native audio path
- [ ] Wire TTS for spoken responses
- [ ] Build minimal rule-based intent router (simple command vs. needs-Gemma)

**Dev B:**
- [ ] Build the push-to-talk button UI component itself (visual state: idle / listening / processing —
      Compose UI only, wired to callbacks Dev A's code will trigger, not implementing the audio capture
      logic)
- [ ] Begin File Fetch feature: Storage Access Framework / MediaStore lookup-by-filename, as an isolated
      module with its own function signature (e.g., `fun findFile(query: String): FileResult?`) — no
      dependency on model/voice/router code yet

**Gate:** Press the button, ask a spoken question with no on-screen content involved, get a spoken answer
back, end to end, offline. Merged to `main`, confirmed working.

---

## Phase 2 — Screen Explain

**Dev A:**
- [ ] MediaProjection permission flow implemented and tested
- [ ] Screen capture → image passed to Gemma alongside the voice question
- [ ] Response covers both spoken and on-screen text (on-screen text rendering can hand off to a simple
      Dev B-built component — coordinate the interface before Dev B starts it)

**Dev B:**
- [ ] Continue/finish File Fetch feature — connect it to the intent router's "simple/direct action" path
      once Dev A confirms the router's expected interface for direct actions
- [ ] Begin OCR quality test pass: run OCR against real sample scanned/handwritten notes (Dev A supplies
      or points to the OCR call path), report accuracy findings back — this is a validation task, not new
      pipeline code

**Gate:** Point the app at a real screen, ask a real question about it, get a correct answer — live,
offline, on the real device. File Fetch working via voice command. Merged to `main`, confirmed working.

---

## Phase 3 — Notes RAG Pipeline (highest technical risk — Dev A owns this fully)

**Dev A:**
- [ ] Qdrant Edge Rust crate compiled for Android target
- [ ] Minimal JNI bridge: index test documents, run a query, confirm results reach Kotlin correctly
- [ ] Embedding pipeline wired (FastEmbed or on-device embeddings)
- [ ] End-to-end: notes → OCR → embed → index → voice query → retrieval → Gemma-generated quiz/summary
- [ ] **Go/no-go call on this integration** — if not working cleanly within the time budgeted for this
      phase today, switch to the fallback (pure-Kotlin vector search library, or no-retrieval
      "summarize this one note" fallback) and log the call in `/DECISIONS.md`

**Dev B:**
- [ ] Build Quiz/Flashcard UI screens in Compose — question card, answer reveal, right/wrong feedback,
      progress through a set. Build against a **mock/sample data shape** Dev A defines up front (e.g., a
      `QuizItem` data class with question/answer/options fields) so this can be built and tested fully
      independently of whether the RAG pipeline is ready yet
- [ ] Finish OCR quality findings from Phase 2, report to Dev A

**Gate:** Voice-triggered quiz generation from real notes, rendered in the Dev B-built UI, working
offline. If Dev A's RAG pipeline hit the fallback path, the gate is: fallback feature (e.g.,
single-note summarize) working end to end instead. Merged to `main`, confirmed working.

---

## Phase 4 — Integration, Polish, Demo Prep

**Dev A:**
- [ ] File Summarize feature (OCR + Gemma on a located file)
- [ ] Full integration pass across all features on combined `main`
- [ ] Demo script rehearsal — see `/PRD.md` §6 Success Criteria
- [ ] Airplane-mode full test run across every feature — confirm zero network dependency

**Dev B:**
- [ ] UI polish pass across all screens built so far
- [ ] Run full QA checklist (see `/TODO.md`) across every feature, log bugs for Dev A to triage
- [ ] Support demo rehearsal (e.g., operate a second device to simulate a live interaction, if useful)

**Gate:** All P0 features work reliably, live, offline, multiple times in a row. This is the end state
for today's sprint.

---

## Phase 5 — Post-sprint / future scope (not built today)

- Form-Fill Assist (confirmation-gated, per `/CONSTRAINTS.md`)
- Proactive organization & reminders
- True always-on wake-word
- Broader file-type support
