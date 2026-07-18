# Trace — Product Requirements Document

## 1. Summary

Trace is a fully offline, voice-first Android assistant that helps a user find, understand, and act on
the personal content already stored on their phone — files, scanned documents, notes, and whatever is
currently on screen — without any of that content ever leaving the device.

It is not a general-purpose chatbot. It is scoped specifically to private, personal data: identity
documents, forms, medicines, class notes, screenshots. The reason it exists is that no cloud assistant
(Gemini, GPT, Google Lens) can be trusted with that category of content, and no file manager can explain
or reason about what's inside a file. Trace fills exactly that gap and nothing more.

## 2. Problem Statement

People store a large amount of personal and academic content on their phones, but get almost no AI help
with any of it, for two separate reasons:

1. **Retrieval is manual.** Finding a specific file means opening a file manager and searching. There is
   no "just ask for it" path.
2. **Understanding requires the cloud, and for a lot of this content, the cloud is not an option.**
   Nobody uploads their driver's license, Aadhaar card, medical report, or a government form to ChatGPT
   or Gemini to ask a question about it. Students with large volumes of scanned or photographed notes
   won't upload all of them to a cloud AI to study from, either — it's slow, it's a privacy step they
   don't want to take, and it doesn't scale to "quiz me on three weeks of notes."

The result: this content sits on the device, mostly unread, unindexed, and unused beyond simple storage.

## 3. Target User

Not a mass-market "AI for everyone" pitch. Trace targets:

- **Students** with meaningful volumes of scanned/photographed notes who want to revise and self-test
  without retyping everything into a chatbot.
- **Anyone with sensitive personal documents** on their phone (ID, medical, financial, government forms)
  who wants help finding or understanding them without a privacy tradeoff.

This is a real, specific, non-generic audience — not "grandma," not "every Android user." See
`/CONSTRAINTS.md` for why this scoping is deliberate.

## 4. Core Features

Ordered by priority. Build top-down; cut from the bottom under time pressure. See `/ROADMAP.md` for
phased sequencing and `/TODO.md` for current task breakdown.

### P0 — Screen Explain
User asks a question about whatever is currently on screen. The app captures the screen (via
MediaProjection — see `/CONSTRAINTS.md` for why not AccessibilityService), sends it with the spoken
question to Gemma 4 E2B, and returns a spoken + on-screen answer.

**Example:** "Hey Trace, what does this line in my bank statement mean?"

### P0 — Notes → Flashcards / Quiz / Summary (RAG)
User's notes (photographed or typed) are OCR'd and embedded once, indexed locally in Qdrant Edge. Voice
commands trigger retrieval over that index, and Gemma generates a quiz, flashcard set, or summary
grounded in the actual retrieved content — not general knowledge.

**Example:** "Hey Trace, quiz me on my DBMS normalization notes."

Rendered as a real UI (quiz cards with right/wrong feedback), not spoken-only — see `/ARCHITECTURE.md`
for the reasoning ("voice-only underperforms" finding).

### P1 — File Fetch
Direct voice/text file lookup and retrieval — not semantic search, a direct filesystem query via Storage
Access Framework / MediaStore.

**Example:** "Hey Trace, pull up my driver's license."

### P1 — File Summarize
Once a file is located (via fetch, or directly named), summarize its content using OCR + Gemma.

### P2 — Form-Fill Assist (stretch goal)
Surfaces relevant saved information (from an explicit, user-populated data store — not arbitrary file
search) while a user is filling out a form. **Always shows what it's about to provide and requires
confirmation.** Never auto-submits. See `/CONSTRAINTS.md` — this is a hard rule, not a style preference.

### P2 — Proactive Organization & Reminders (stretch goal, likely post-hackathon)
Learns routine patterns well enough to act proactively — e.g., surfacing today's class timetable
unprompted, or reminding the user to check a specific file on a schedule. Out of scope for the hackathon
build; documented here because it's part of the pitched vision and Expected Impact.

## 5. Explicitly Out of Scope

See `/CONSTRAINTS.md` for the authoritative, agent-facing version of this list. Summary:

- General-purpose "ask me anything" chat (undifferentiated — loses to Gemini Live on capability, and
  isn't the product)
- True always-on wake-word listening (battery/reliability risk not worth it for a hackathon demo)
- Autonomous form submission
- AccessibilityService-based screen reading
- Anything requiring external hardware (Pi, Jetson, etc.) — phone only
- Object/crop/plant recognition, general vision Q&A about the physical world — not the product, and this
  category is saturated territory (see `/DECISIONS.md` for the research that ruled this out)
- Multi-device / swarm architectures

## 6. Success Criteria for the Hackathon Build

A working demo must be able to, live, offline (airplane mode on):

1. Answer a genuine question about real content on screen
2. Do the same in Hindi or Marathi, proving multilingual is real, not a slide claim
3. Generate a quiz from the presenter's own real notes and take one question live
4. (Stretch) Fetch a named file by voice on request

If Qdrant Edge / RAG integration is not working reliably by the pre-hackathon cutoff, the notes feature
degrades to "summarize this specific note" (direct OCR + Gemma, no retrieval) rather than being cut
entirely — see `/ROADMAP.md` Phase 4 fallback.

## 7. Business Model

Not mass-consumer freemium. Two real paths:

- Premium/pro tier for privacy-conscious users
- Licensable to OEMs or enterprise device-management providers wanting a "private on-device assistant"
  differentiator where cloud AI is a compliance non-starter

## 8. Non-Functional Requirements

- **Model stays resident in memory after first load.** Never reload per request (cold load ~90-100s,
  warm ~20-30s per pre-hackathon benchmarks on Exynos 1380 / 6GB RAM class hardware — see
  `/ARCHITECTURE.md`).
- **No network calls, ever, for any core feature.** This is the entire premise of the product; a single
  accidental cloud call in the demo undermines the whole pitch.
- **Responses stay concise by default** (target 1-2 sentences unless expansion is requested) — both a UX
  choice and a hardware necessity at ~9 tok/s decode speed on target devices.
