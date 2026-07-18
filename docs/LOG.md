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
