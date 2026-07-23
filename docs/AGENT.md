# Trace — Agent Guidelines

This file is for any AI coding agent working on this codebase. Read this, `/CONSTRAINTS.md`,
`/ARCHITECTURE.md`, and `/WORKSTREAM.md` before writing code. Read `/TODO.md` for what's currently in
scope — it is the single source of truth for what to work on right now.

## Non-negotiable working rules for this sprint

- **Only work from `/TODO.md` and `/ROADMAP.md`.** Do not invent tasks, do not "improve" something
  outside the current active phase, do not jump ahead to a later phase's tasks even if it seems
  efficient.
- **Only touch files/modules owned by the dev you're assisting.** Check `/WORKSTREAM.md`'s ownership
  list before editing anything. If you're assisting Dev B, do not edit any file on Dev A's list, and
  vice versa. This is what keeps both branches conflict-free — treat it as a hard boundary, not a
  suggestion.
- **Read existing code and docs before writing new code.** Don't assume a function, interface, or
  pattern doesn't exist — check first. Don't duplicate something that's already there.
- **Write simple, production-ready, optimized code. Avoid unnecessary complexity.** No speculative
  abstraction, no premature generalization, no clever tricks that make the code harder to review. If two
  approaches solve the task, pick the one that's easier to read and debug, not the more impressive one.
- **Follow the task as written.** If a task in `/TODO.md` is ambiguous or you think it should be done
  differently, flag that to the human rather than silently reinterpreting it.

## How this project is built

Nearly all code in this repo is AI-generated. The human on this project (Kazuto) is the orchestrator,
tester, and reviewer — not the author of most individual lines. This means:

- **Don't assume prior context the docs don't state.** If something isn't written down in `/PRD.md`,
  `/ARCHITECTURE.md`, or `/CONSTRAINTS.md`, don't infer it from "what a normal app would do" — ask, or
  flag the ambiguity in your output, rather than silently deciding.
- **Prefer boring, debuggable solutions over clever ones.** The human reviewing your output has limited
  time and is not always able to deeply audit complex logic. Straightforward code that's easy to verify
  beats elegant code that's hard to check.
- **Never silently reach for a more powerful API than what's specified**, even if it would work better.
  See `/CONSTRAINTS.md` — several "obvious" implementation choices are deliberately ruled out for
  security or product reasons that aren't visible from the code alone (e.g., AccessibilityService).

## Before implementing any feature

1. Check `/CONSTRAINTS.md` — does this feature touch anything on the hard-rules list?
2. Check `/ARCHITECTURE.md` — is there an existing pattern for this kind of component?
3. Check `/DECISIONS.md` — has this exact tradeoff already been settled? Don't re-litigate a closed
   decision; if you think a past decision was wrong, flag it explicitly rather than quietly overriding it.

## Code style / stack reminders

- **Kotlin/Jetpack Compose** for all app-shell, UI, and Android-platform code.
- **Kotlin-only app implementation.** The planned Qdrant Edge/Rust JNI bridge was rejected during
  Phase 3; RAG uses the shipped Kotlin retrieval stack. Do not introduce Rust or JNI without a new
  explicit decision in `/DECISIONS.md`.
- Forked from Google AI Edge Gallery — preserve and reuse its existing LiteRT-LM / Gemma loading code
  rather than reimplementing model loading from scratch. Removing UI (model picker, generic chat screen)
  is in scope; rewriting the inference plumbing is not, unless something is actually broken.
- Model must be loaded once and kept resident — never add code that reloads the model per-request.

## What "done" means for a task

A task is not done when the code compiles. It is done when:
- It's been tested on the actual target device (or the human has explicitly deferred that)
- It behaves correctly with the network disabled (airplane mode) — every feature must work fully offline
- Any new permission, dependency, or architectural choice is logged in `/DECISIONS.md`
- `/LOG.md` has an entry (see below)

## Logging

After any non-trivial work session, append an entry to `/LOG.md` — see that file's format. This is a
running log, not a chat transcript: short, factual, action-oriented. It exists so the human (or a
different agent session) can pick up context fast without re-deriving what happened.

## When uncertain

If a request conflicts with `/CONSTRAINTS.md`, or requires a product decision not covered in `/PRD.md`,
stop and surface the conflict rather than picking an interpretation and proceeding. Wrong guesses here
are expensive — this is a solo-plus-agents team with a hard deadline, not a project with slack for
rework.
