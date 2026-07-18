# Reference Implementation — File Fetch (End to End)

Purpose: this is what a correct, complete implementation of File Fetch looks like, written so it can be
checked line-by-line against what your agent actually built. If a step here isn't present in the real
code, that's a candidate for your bug.

Read alongside `/CONSTRAINTS.md` and `/ARCHITECTURE.md` — this doc doesn't repeat product decisions
already settled there, it goes one level deeper into the actual implementation.

---

## 0. The core problem this feature has to solve

"Pull up my driver's license" is **not a filename lookup.** Nobody's driver's license photo is named
`driver_license.jpg`. It's `IMG_20240311_142207.jpg`, or `Screenshot_2024-08-01.png`, or something
equally meaningless. If your `findFile()` implementation does a direct string match against filenames,
**it will never find anything for a natural-language query like this, ever, for any user, regardless of
whether the router or voice pipeline works perfectly.** This is very likely your actual root cause given
that typed text also fails.

A correct implementation has two fundamentally different lookup modes, and confusing them is the most
common failure:

- **Mode A — Named/exact fetch:** user says the actual filename or something very close to it
  ("open IMG_2024" or "open my resume pdf" where the file is literally named `resume.pdf`). This can be
  solved with direct filename/metadata matching.
- **Mode B — Descriptive/semantic fetch:** user says what the file *is*, not what it's *named*
  ("pull up my driver's license"). This requires the file to have been **classified or tagged ahead of
  time** — either by the user manually, or by running vision/OCR classification over files at some point
  (on import, on first index, or on-demand) so there's a stored mapping like
  `{file: IMG_2024.jpg, classified_as: "driver's license", confidence: 0.91}` that a query can match
  against.

**If your implementation only has Mode A, "driver's license" will never work — not because of a bug, but
because the feature as specified requires Mode B, and Mode B has not been built yet.** This is the first
thing to check before debugging anything else: does a classification/tagging step exist anywhere in the
codebase at all? If not, that's not a bug to fix, it's a missing feature to build — see §5.

---

## 1. Permissions — the full, correct set, and where implementations usually get this wrong

Android's storage permission model is version-dependent and this is one of the most common sources of
"file exists but app can't see it" bugs.

### Required permissions, by Android version
- **Android 13+ (API 33+):** granular media permissions — `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`,
  `READ_MEDIA_AUDIO`. There is no single "read all files" permission for media anymore. If the app only
  requests `READ_MEDIA_IMAGES`, it will silently fail to find PDFs, documents, or anything outside the
  three media buckets — **this is a very common bug and matches your symptom exactly if the driver's
  license is a photo but other document types also fail.**
- **Android 11-12 (API 30-32):** `READ_EXTERNAL_STORAGE` still works but is being phased out; scoped
  storage rules already apply, meaning direct filesystem paths outside the app's own sandbox may not be
  readable even with the permission granted, depending on how the file was created.
- **For non-media files (PDFs, generic documents) on any modern Android version:** MediaStore's
  `Files` collection, or the Storage Access Framework (SAF) via `ACTION_OPEN_DOCUMENT` /
  `ACTION_OPEN_DOCUMENT_TREE`, is required — `READ_MEDIA_*` permissions alone do not grant access to
  arbitrary documents.

### Checklist to verify against the actual code
- [ ] Does the manifest declare `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and `READ_MEDIA_AUDIO`
      separately (not just one)?
- [ ] Is the runtime permission request actually checking and requesting **all three**, or just one and
      assuming the rest?
- [ ] For non-media documents (PDF, DOCX, scanned forms saved as files rather than photos): is SAF being
      used at all, or is the code only querying MediaStore's image/video/audio collections?
- [ ] Is there a difference in behavior between "user said yes at the permission dialog" and "the query
      code is actually using the granted permission correctly"? (i.e., permission granted ≠ query
      correctly scoped — these are two separate potential failure points, test them separately)
- [ ] Log the actual permission state (`checkSelfPermission()` result) at the moment the query runs, not
      just at app startup — permissions can be revoked mid-session on newer Android versions and a stale
      cached "yes" is a real bug source

---

## 2. The MediaStore / SAF query itself

Even with correct permissions, the query construction is a second, independent place things break.

### For images (MediaStore)
```
Correct minimal pattern:
- ContentResolver.query() against MediaStore.Images.Media.EXTERNAL_CONTENT_URI
- Projection should include at minimum: _ID, DISPLAY_NAME, DATE_ADDED, DATA (or use
  MediaStore.Images.Media.getContentUri() pattern for the actual file URI, not a raw file path — raw
  paths are unreliable on scoped storage)
- Selection clause, if doing filename matching: use LIKE with wildcards, not exact equals — exact equals
  against a natural-language query will never match anything (see §0)
```

### Common query bugs to check for
- [ ] Is the query using a raw file path (`/storage/emulated/0/...`) anywhere? This breaks on scoped
      storage for files not created by your own app. Should use content URIs (`content://...`)
      exclusively.
- [ ] Is the selection clause doing an exact match (`DISPLAY_NAME = ?`) instead of a fuzzy match
      (`DISPLAY_NAME LIKE ?` with `%query%`)? Exact match will fail almost always for natural queries.
- [ ] Is the query scoped to `EXTERNAL_CONTENT_URI` only, missing files that live in
      `INTERNAL_CONTENT_URI` or on removable storage if applicable?
- [ ] Is there a query timeout or is the query running on the main thread (causing the "app lags a lot"
      symptom you reported for MediaProjection, and possibly relevant here too if fetch is also slow) —
      all ContentResolver queries should run off the main thread
- [ ] Does the query actually return results in a test case with a **known filename that definitely
      exists**, before testing against natural-language queries? Isolate: does exact-filename lookup work
      at all? If no, the query itself is broken. If yes, the problem is purely §0 (no semantic
      classification layer).

---

## 3. The intent router path — verify separately from the query itself

Even after the query itself is confirmed correct in isolation, the router has to actually invoke it.

- [ ] Confirm: does a voice-transcribed query and an identically-worded typed query produce the exact
      same string passed into the router? (Log the raw string right before router classification, for
      both input methods, and diff them.)
- [ ] Confirm: does the router have an explicit rule/pattern for "fetch/find/pull up/show me [X]" style
      phrasing, or is it relying on the LLM itself to decide whether this is a fetch intent? If the
      latter, check what the LLM is actually being asked to decide, and whether "driver's license" as a
      target is something the router's prompt/logic even considers a valid fetch target
- [ ] If the router correctly identifies "this is a fetch request" but the *target* (driver's license →
      what file?) is unresolved, that's the classification gap from §0 again, not a router bug
- [ ] Add explicit logging at each router decision point: input received → intent classified as → action
      taken. Without this, "the model says no access" vs. "the query genuinely found nothing" look
      identical from the user's side but are different bugs with different fixes.

---

## 4. Why "the model denies having access" specifically happens

This error message is a strong diagnostic clue on its own. If Gemma is generating a text response like
"I don't have access to your files," that means **the request never reached the file-fetch code path at
all** — it went straight to the general chat/reasoning path, and the model is doing exactly what a
general chatbot should do when asked about local files it genuinely can't see: honestly say it can't help.

This confirms: the router (or whatever decides router-path vs. chat-path) is **not routing this query to
the fetch function.** This is worth confirming as step one, before even checking the query logic in §2 —
if the fetch function is never being called, its correctness doesn't matter yet.

**Test to isolate this:** temporarily hardcode the router to always route any query containing the word
"fetch" or "find" (or whatever your trigger word is) directly to the fetch function, bypassing any
LLM-based intent classification. If it then successfully finds a file you know exists by exact filename,
the query logic (§2) is fine and the bug is 100% in routing/classification. If it still fails, the bug is
in §1 or §2, not routing.

---

## 5. What actually needs to be built for "driver's license" to work — the missing piece

Given your symptom (fails for both voice and text), the most likely real situation is: the fetch function
itself may be fine or close to fine for **exact/named** lookups, but there is **no classification step**
that would let "driver's license" resolve to an actual file at all. This isn't a bug in the traditional
sense — it's an unbuilt requirement.

Minimum viable version for the hackathon/sprint timeline:
- **On-demand classification, not a background index (simpler, avoids a whole separate indexing
  pipeline for now):** when a fetch query doesn't match any filename directly, fall back to: pull a
  reasonable candidate set (e.g., all images in the last N months, or all images if the set is small
  enough), pass each through Gemma's vision capability with a classification prompt ("is this a driver's
  license? yes/no" or "what type of document is this?"), and return the first/best match.
- This is slow if done naively over hundreds of images — for a demo, scope the candidate set
  deliberately small (e.g., a designated "Documents" or "IDs" folder the user has pre-organized, or a
  hardcoded small test set for the demo itself) rather than trying to classify an entire camera roll live
  in the hackathon build.
- Log what candidate set was searched and what confidence/result came back for each, so failures are
  diagnosable rather than a silent "not found."

**This is the piece to build, not just debug**, if §0-§4 all check out and the query logic itself is
technically correct but simply has nothing to search against for descriptive queries.

---

## Summary checklist — run through in this exact order

1. [ ] Does exact-filename fetch work at all, bypassing the router (hardcoded test)? If no → fix §1/§2
       first, nothing else matters until this works.
2. [ ] Does the router correctly send a fetch-style query to the fetch function at all (logged, not
       assumed)? If no → fix routing/classification logic.
3. [ ] Does voice-transcribed text match typed text exactly before hitting the router? If no → ASR
       transcription/normalization issue, separate from routing.
4. [ ] Is there any classification/tagging layer that maps "driver's license" (a description) to an
       actual file? If no → this is the missing feature from §5, build it, don't keep debugging the
       query logic — it was never going to work without this piece.
