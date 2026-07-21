# Trace — Dev C2 Catch-Up (post UI-redesign → Phase 4 kickoff)

**Who this is for:** Dev C2, returning after the first UI-redesign iteration. A lot changed on
`main` while you were off. This doc gets you re-synced and lays out your Phase 4 lane.

**TL;DR:** Your branch (`dev-b`) is 6 substantive commits behind `main`. The app now boots into a
**home landing screen** with a module grid + chat input, driven by a rewritten `AppShell`. The theme
was overhauled (off-white/charcoal + softened iOS-blue accent), file-fetch works from the home input,
and a couple of perf/bug fixes landed. **Rebase onto `main` before starting Phase 4** so you're
building the Memory/Schedules UI on top of the current shell, not the old one.

---

## 1. Sync first (important)

`dev-b` last branched at `31b67c2` ("docs: mark UI-redesign Phases 3-4 done"). Everything below is on
`main` but NOT on `dev-b`. Rebase/merge `main` into `dev-b` before you touch anything — the shell and
drawer you'll be extending for Phase 4 only exist in their current form on `main`.

```
git checkout dev-b
git fetch origin
git merge origin/main      # or rebase, your call — resolve, build, then start Phase 4
```

Commits you're picking up (newest first):

| Commit | What |
|--------|------|
| `ecfc17f` | fix: home-input message no longer re-sends when you leave a module and return |
| `7786ad4` | perf: intent-router regex hoist + O(n²)→O(n) streaming token update |
| `ad7ce2d` | feat: preload model at launch + run fetch commands directly from home input |
| `7bebe30` | feat: cohesive accent + global voice/cancel/settings polish |
| `479f9ed` | fix: home routing, sidebar-from-modules, cohesive iOS-blue theme |
| `c33c2ba` | feat: home landing screen on fresh launch |

---

## 2. What changed and why it matters to you

### 2a. The app now opens on a HOME screen, not straight into AI Chat
`ui/shell/AppShell.kt` was substantially rewritten (it's the big one — ~455 lines changed). New shape:

- **`ShellHomeScreen`** — fresh-launch landing: hamburger, "Hi, I'm Trace 👋" greeting, a 2×2 module
  grid (Vision, Notes, + Schedule/Audio placeholders), and a chat input at the bottom.
- **State the shell owns:** `activeModule`, `onHome` (default true), `pendingChatQuery`,
  `lastInitializedModule`. Picking a tile or sending from the home input sets `onHome = false` and
  routes into that module's own `MainScreen`. System back returns home.
- **`AppDrawerContent`** (the left sidebar) — `AppShell.kt:615`. Rows: Home, the three modules, then
  (home only) Benchmark / Model settings / File search scope, and a **global Settings** row pinned at
  the bottom. **This is where your Memory sidebar entry point will live** (see §4).
- **`DrawerRow`** (`AppShell.kt:670`) is the reusable row composable — match its style for any new
  drawer rows so the sidebar stays consistent.

### 2b. Model is preloaded at launch + stays resident
The AI Chat model now warms up as soon as the app opens (on the home screen), so the first send is
instant. `lastInitializedModule` guards against tearing the model down when you leave a module and
come back — only a *real* module switch (different capability flags) reinitializes. If you add a new
module, respect this: don't force-reinit on re-entry to the same module.

### 2c. Home input can fetch files directly (no trip through AI Chat)
The home input runs text through `IntentRouter` (`voice/IntentRouter.kt`). A **file-fetch** command
(e.g. `fetch resume`, `search test`, `pull invoice`) opens the file right from home — it never
navigates into AI Chat. Anything else launches AI Chat with the text. `search` and `pull` are now
first-class fetch verbs alongside `fetch`.

### 2d. Theme overhaul (affects every screen you build)
`ui/theme/Color.kt` + `Theme.kt`:
- **Light:** off-white `#F5F5F0` bg + charcoal `#1A1A1A` text.
- **Dark (default):** charcoal `#0E0E0E` bg + off-white `#F2F2ED` text.
- **Accent:** softened iOS-blue — `#3B82F6` (light) / `#4A90E2` (dark). This drives user bubbles,
  send button, links, and selected states. **Use `MaterialTheme.colorScheme.primary` for accent and
  `customColors.userBubbleBgColor` for bubbles** — don't hardcode a per-module color. The old
  per-module accent palette is gone; every module shares the one accent now.
- Idle icon buttons (mic, Notes Quiz) are grey (`surfaceContainerHighest`) and only take the accent
  when active. Follow this pattern for any new action buttons.

### 2e. Global UX conventions now in place
- **Voice mic** on the home input + chat inputs (grey idle, red while listening).
- **Cancel/stop** works during the "thinking" phase (before the first token), not just mid-stream.
- **Playful thinking labels** ("Brewing", "Pondering", …) show in *every* chat module via
  `MessageBodyLoading`, not just Notes.
- **Settings** is reachable from the sidebar on every screen.

### 2f. Perf + correctness fixes (context, no action needed)
- Streaming token updates are O(n) now (`ChatViewModel.updateLastTextMessageContentIncrementally`) —
  was re-normalizing the whole response per token.
- `IntentRouter` compiles its regexes once (companion object) instead of per-send.
- Home-input messages no longer double-send when you switch modules and return (a new
  `onInitialQueryConsumed` callback on `CustomTaskDataForBuiltinTask` clears the pending query the
  moment the module consumes it).

---

## 3. Working agreement (unchanged, re-stated)
- You work on **`dev-b`**. Dev A (C1) is the only one who merges to `main`. Open a merge/PR into
  `main`; C1 merges it.
- **100% offline** — no network calls for core features (see `/docs/CONSTRAINTS.md`).
- Don't edit C1's core files (shell/model/voice pipeline) unilaterally — if a Phase 4 UI task needs a
  data shape or interface from C1's backend, flag it and C1 defines it first. See §4.
- Surgical changes, match existing patterns, keep the design system cohesive (§2d).

---

## 4. Your Phase 4 lane (Dev B / C2)

From `/docs/ROADMAP.md` Phase 4 — Memory & Schedules. **Your tasks:**

1. **Memory sidebar UI** — view / edit / add memory entries (both user-authored and system-authored
   schedule entries). Entry point: a "Memory" row in `AppDrawerContent` (`AppShell.kt:615`, match
   `DrawerRow` style).
2. **Schedules list UI** + notification interaction flows.
3. **UI polish pass** across all screens built so far.

**Blocking dependency:** your Memory sidebar binds to the **Memory data store** C1 is building
(Task: "Build Memory structured data store + repository"). **C1 will define + document the memory-entry
shape in `/docs/ARCHITECTURE.md` before you start the sidebar** — bind to that interface, don't guess
at it. The scheduling backend (`notifications/NotificationScheduleManager`, `NotificationReceiver`,
`BootReceiver`, `proto/scheduled_notification.proto`) already largely exists; C1 is auditing/completing
it and will expose a clean create/cancel/list API for your Schedules list UI.

**Phase 4 gate:** you can add memory entries in the sidebar; C1's flow scans a prescription in Vision,
asks for a reminder, populates a schedule in Memory, and fires a reliable Android notification. Merged
to `main`.

---

## 5. Device-test the current build first
Before writing Phase 4 code, install the current `main` build and get familiar with the new shell:
fresh launch → home screen → tap a module / send from home input → sidebar (hamburger) → Settings.
Confirm the home input fetch works (`fetch <a filename on your device>`). This is the surface your
Phase 4 UI plugs into.

Questions on any interface or where something lives → ping C1.
