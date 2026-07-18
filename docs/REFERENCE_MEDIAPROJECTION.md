# Reference Implementation — MediaProjection Screen Understanding (End to End)

Purpose: same intent as the File Fetch doc — a correct implementation to check the real code against.
Covers both the working whole-screen path (to confirm it's actually correct, not just accidentally
working) and the broken single-app-share path (crash + lag).

---

## 0. The two MediaProjection modes, and why they behave completely differently

Android's screen-sharing permission dialog offers "share whole screen" or "share one app," but these are
**not the same underlying mechanism with a filter applied** — they use different code paths in
MediaProjection with different lifecycle guarantees, which is almost certainly why one works and the
other crashes.

- **Whole screen share:** the `VirtualDisplay` created by MediaProjection mirrors the entire physical
  display surface continuously. Your foreground service stays in the foreground, your app stays
  frontmost or at least alive, and capturing a frame is a straightforward `ImageReader` read from that
  virtual display.
- **Single app share:** Android creates a virtual display scoped to *just* that app's task, and —
  critically — **your own app (Trace) is not that app anymore.** Trace gets backgrounded. This matters
  a lot:
  - If Trace's own process is not protected as a proper foreground service with the correct service type
    declared, Android can and will suspend or kill background work shortly after backgrounding, which
    matches your "model never responds and crashes" symptom exactly.
  - The MediaProjection session itself can be silently invalidated by the OS during this transition if
    the code isn't handling the `MediaProjection.Callback.onStop()` case, or if the virtual display /
    image reader isn't correctly re-established for the new foreground app context.

**This is very likely a foreground-service lifecycle bug, not a MediaProjection API misuse bug per se.**

---

## 1. Required manifest and runtime setup

- [ ] `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />` declared
- [ ] `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />`
      declared — **this specific foreground service type permission is required on Android 14+ (API 34+)
      and is one of the most commonly missed pieces.** Without it, the service can be silently killed the
      moment the app backgrounds, which is exactly your symptom.
- [ ] The foreground service itself must declare `android:foregroundServiceType="mediaProjection"` in the
      manifest `<service>` entry — a generic foreground service without this specific type will not
      reliably keep a MediaProjection session alive on modern Android
- [ ] The service must call `startForeground()` with a valid, visible notification **before** the
      MediaProjection capture begins, not after — sequencing here matters and is a common bug source

### Checklist for the actual crash
- [ ] Pull the logcat/crash stack trace from the moment of backgrounding during single-app share — do
      not guess, the actual exception (likely something in the
      `ForegroundServiceStartNotAllowedException` / `SecurityException` / or a null virtual display
      family) tells you exactly which of the above is missing
- [ ] Confirm whether the crash happens immediately on backgrounding, or after some delay — immediate
      points to a manifest/permission issue; delayed points to a lifecycle/callback issue

---

## 2. MediaProjection session lifecycle — what must be handled explicitly

- [ ] `MediaProjection.Callback` must be registered, and its `onStop()` implemented — the OS can revoke
      a MediaProjection session at any time (including during app backgrounding), and if your code
      doesn't handle this, it'll be holding a dead reference and silently fail to capture anything,
      which matches "model never responds"
- [ ] The `VirtualDisplay` and `ImageReader` instances must be properly recreated if the session is
      re-established, not reused from a stale reference
- [ ] Confirm the capture call itself (`ImageReader.acquireLatestImage()` or similar) has a timeout or
      null-check — if it's called on a dead/invalid session, it can hang indefinitely, which would
      explain "the model never responds" separately from an outright crash

---

## 3. Why "the mobile lags a lot" during this specific path

This is a distinct symptom from the crash and worth diagnosing separately — likely one or more of:

- [ ] Is the screen-capture / image-processing work happening on the main thread? It should not be —
      capturing a frame, converting it to a bitmap, and preparing it for the model should all happen on
      a background thread or coroutine, off the UI thread
- [ ] Is anything looping or retrying rapidly while the session is in a broken state (e.g., a capture
      loop that keeps calling `acquireLatestImage()` in a tight loop against a null/dead reader)? A busy
      failure loop would produce exactly this lag symptom while never actually succeeding
- [ ] Check whether Gemma inference is being triggered multiple times redundantly during this broken
      state (e.g., once per failed capture attempt) — that would compound the lag

---

## 4. Practical recommendation given the hackathon/sprint timeline

Per `/CONSTRAINTS.md` and `/ROADMAP.md`, single-app screen share was already correctly identified as
lower priority than File Fetch. Given the above, the actual fix is a real, scoped, learnable Android
platform issue (foreground service type + lifecycle callback handling) — not a hardware limitation as
initially assumed. It's fixable, but:

- [ ] **Recommendation: for the hackathon demo itself, only demo whole-screen share**, since it's already
      confirmed working correctly. Don't spend limited remaining time chasing the single-app-share fix
      unless File Fetch (the higher-priority, more differentiated feature) is already solid.
- [ ] If time allows after File Fetch is fixed, come back to this doc and work through §1-§3 in order —
      the manifest/permission checklist in §1 is the fastest, highest-probability fix to try first.

---

## Summary checklist — run through in this exact order

1. [ ] Confirm `FOREGROUND_SERVICE_MEDIA_PROJECTION` permission is declared (Android 14+) — single most
       likely missing piece
2. [ ] Confirm the foreground service declares `foregroundServiceType="mediaProjection"` in the manifest
3. [ ] Confirm `startForeground()` is called before capture begins, with correct sequencing
4. [ ] Pull the actual crash stack trace — stop guessing, read the real exception
5. [ ] Confirm `MediaProjection.Callback.onStop()` is implemented and handled
6. [ ] Confirm capture/processing work is off the main thread
7. [ ] Given priority: park this after a first pass through §1-2 if File Fetch isn't yet solid — this
       feature is explicitly lower priority per `/ROADMAP.md`
