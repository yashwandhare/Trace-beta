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

## 4. Core Modules

Ordered by priority. Build top-down; cut from the bottom under time pressure. See `/ROADMAP.md` for
phased sequencing and `/TODO.md` for current task breakdown.

### P0 — AI Chat (Module)
The primary interface. Existing text and voice chat, extended with file and image attachment so users can ask questions about an attached document or photo directly in chat. This is the primary ingestion point for RAG. Screen-explain (MediaProjection) remains as a separate feature integrated into the chat experience.

**Example:** "Hey Trace, what does this line in my attached bank statement mean?"

### P0 — Vision (Module)
Camera-based scan-and-chat, accessible from the homescreen. The user photographs something real-world (medicine prescription, product label, etc.) and chats about it. This is distinct from Screen-explain: Vision is the device camera pointed at the physical world.
*(Stretch goal: Live video chat via voice, not required for first pass).*

**Example:** "What does this nutrition label mean?"

### P0 — RAG (Module — Phase 3)
User's notes (photographed or typed) are OCR'd and embedded once with the bundled on-device Universal
Sentence Encoder. A pure-Kotlin cosine store persists the attached-note index locally; voice or text
commands retrieve relevant chunks and Gemma generates a quiz, flashcard set, or summary grounded in
that retrieved content.

**Important:** RAG ingestion is strictly via files the user explicitly attaches through AI Chat or selects directly. It NEVER performs a background search of device storage.

### P1 — Memory (Sidebar Feature)
A structured store the model can always reference. Not a homescreen module, but a sidebar feature with two write paths:
1. **User-authored:** free-form personal info and custom prompts/guides, entered and edited directly by the user.
2. **System-authored (Schedules):** created automatically when a schedule is generated. Visible and editable in the Memory screen so wrong entries can be corrected.
*Note: General conversational memory across chat sessions is explicitly out of scope.*

### P1 — Schedules (Module)
When a prescription or similar is scanned in Vision or attached in AI Chat, the user can ask the model to create a reminder routine with specific times. This writes to Memory as system-authored entries and triggers real Android notifications at the scheduled times.

### P2 — Quiz-from-schedule (Extension)
A Schedules-triggered quiz feature for students (e.g., a daily quiz on a topic from a source). This is a natural extension of Schedules + RAG once both exist, not new infrastructure of its own.

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
- Custom user-created homescreen modules (turning any feature into a user-defined homescreen tile)
- General conversational memory across chat sessions (remembering prior conversations)

## 6. Success Criteria for the Hackathon Build

A working demo must be able to, live, offline (airplane mode on):

1. Answer a genuine question about real content on screen
2. Do the same in Hindi or Marathi, proving multilingual is real, not a slide claim
3. Generate a quiz from the presenter's own real notes and take one question live
4. (Stretch) Fetch a named file by voice on request

The Qdrant Edge/JNI path was deliberately rejected as too risky for the hackathon; the pure-Kotlin
on-device retrieval path is the shipped implementation. If retrieval itself proves unreliable, the
notes feature degrades to "summarize this specific note" (direct OCR + Gemma, no retrieval) rather
than being cut entirely.

## 7. Business Model

Not mass-consumer freemium. Two real paths:

- Premium/pro tier for privacy-conscious users
- Licensable to OEMs or enterprise device-management providers wanting a "private on-device assistant"
  differentiator where cloud AI is a compliance non-starter

## 8. Non-Functional Requirements

- **Model stays resident in memory after first load.** Never reload per request (cold load ~90-100s,
  warm ~20-30s per pre-hackathon benchmarks on Exynos 1380 / 6GB RAM class hardware — see
  `/ARCHITECTURE.md`).
- **No network calls for any core feature.** The core demo must work in airplane mode. The sole explicit
  exception is user-enabled DuckDuckGo web search: it is session-only, off by default, and never
  required by any product flow.
- **Responses stay concise by default** (target 1-2 sentences unless expansion is requested) — both a UX
  choice and a hardware necessity at ~9 tok/s decode speed on target devices.
