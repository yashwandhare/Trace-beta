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

## Example (delete once real entries start)

## 2026-07-18 — Dev A — Phase 0
- Did: Forked app builds and runs on real device. Gemma 4 E2B-IT loading via LiteRT-LM confirmed working.
- Benchmark: decode ~9 tok/s, cold load ~95s, warm load ~25s on [device model].
- Broken/open: none, gate met.
