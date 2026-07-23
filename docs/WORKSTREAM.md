# Trace — Workstream Division (Dev A / Dev B)

This file is the source of truth for who owns what. Read this before touching any file. If a task isn't
clearly yours per this document, don't touch it — flag it instead of guessing.

## Roles

- **Dev A — Kazuto.** Owns critical path, core model/voice pipeline, all key architectural decisions,
  merge integration, and final testing. Dev A is the only one who merges into `main`. Dev A resolves any
  conflict or ambiguity — Dev B does not make unilateral calls on anything outside their lane.
- **Dev B — Yash.** Owns clearly bounded, self-contained features that don't touch the core model/voice
  pipeline. Works entirely inside their own files/modules. Pushes to their own branch, opens for review,
  does not touch `main` directly, does not touch Dev A's files.

## Branching model

- `main` — always stable, always the last known-good merged state.
- `dev-a` — Dev A's working branch.
- `dev-b` — Dev B's working branch.

**Merge order, every phase, no exceptions:**
1. Dev B finishes their phase task(s), pushes to `dev-b`.
2. Dev B opens a merge/PR into `main`. **Dev A merges it** (Dev B does not self-merge into `main`).
3. Dev A finishes their own phase task(s) on `dev-a`, then merges `dev-a` into `main`.
4. Dev A builds and tests `main` with both dev's work combined.
5. Only once that build is confirmed working does the team move to the next phase's tasks.

This order matters: Dev B's work merges first because it's the lower-risk, more isolated code. Dev A
merges second and is the one who validates the combined build, since Dev A has the full architectural
context to debug integration issues.

## Hard rule: no file overlap

Each phase's task list (see `/TODO.md` and `/ROADMAP.md`) specifies exactly which files/modules belong to
which dev. If a task would require editing a file outside your lane, stop and flag it to Dev A rather
than editing it — this is what prevents merge conflicts entirely, not just resolving them faster.

---

## Ownership by module

### Dev A — Kazuto (critical path)
- Gemma 4 E2B-IT / LiteRT-LM integration and model lifecycle (load-once, resident-in-memory)
- Voice pipeline: push-to-talk capture/transcription, TTS output, and interaction-origin handling
- Intent router (rule-based router logic)
- MediaProjection screen capture + screen-explain feature end to end
- On-device RAG architecture and resident-model integration (the Qdrant Edge/Rust JNI bridge was
  rejected; the shipped retrieval stack is Kotlin-only)
- All architectural and product decisions — anything not explicitly assigned to Dev B below
- Final merges into `main`, integration builds, end-to-end testing

### Dev B — Yash (bounded, isolated features)
- Stripping the forked Edge Gallery model-picker/catalog UI down to a single hardcoded model screen
  (UI-only work, no model-loading logic changes)
- File Fetch feature: Storage Access Framework / MediaStore lookup-by-name, isolated to its own
  file(s)/module — does not touch the model, voice, or router code
- Quiz/Flashcard UI screens (Jetpack Compose views only — rendering and interaction, consuming data
  passed to them; does not implement the retrieval or generation logic behind it)
- OCR quality test pass against sample scanned notes (testing/validation task, reports findings to Dev A,
  does not modify core OCR pipeline code)
- Basic QA checklist execution per phase (see `/TODO.md`) — testing, bug reporting, not core feature
  implementation

**Why this split:** Dev B's tasks are all things that can be built and tested in isolation, without
needing deep context on the model runtime, JNI boundary, or intent-routing logic — consistent with
`/CONSTRAINTS.md` and known team capability constraints. Nothing on Dev B's list is on the demo's
single point of failure path (voice loop, screen-explain, RAG pipeline) — if a Dev B task slips, the core
demo still works; Dev A's tasks are the ones that cannot slip.

---

## Communication protocol

- Before starting a phase, both devs confirm they've read the current `/TODO.md` phase section.
- If Dev B is blocked (e.g., needs a data shape or interface from Dev A's code), they flag it — they do
  not guess at Dev A's interface and implement against an assumption.
- Dev A defines any shared interface (e.g., "the quiz UI expects a `QuizData` object shaped like X")
  *before* Dev B starts UI work that depends on it, and documents it in `/ARCHITECTURE.md` or directly in
  code comments where the interface is defined.
