# Trace — Decisions Log

Running record of *why* choices were made, so they aren't re-litigated later without cause. Append new
entries at the bottom with a date. If a past decision is later reversed, don't delete the old entry —
add a new one referencing it and explain why.

---

### Product direction: Edge / On-Device, not a swarm/cluster or big-model desktop project
A separate, parked "big model" project (a screen-aware VLM desktop assistant) exists for a future,
longer-timeline build. This hackathon project is deliberately scoped to mobile edge only. Smartphone
clustering / distributed inference was considered and rejected — it's model-agnostic (doesn't pass the
"why Gemma specifically" test) and solves an infrastructure problem (server-load offloading), not the
product's actual problem.

### Model: Gemma 4 E2B-IT, not Gemma 3n E2B, not a bigger Gemma variant
Gemma 4 is a real generational jump over Gemma 3n: thinking mode, native tool-calling, larger context
window, Apache 2.0 licensing (matters for the business model), and better performance across vision,
reasoning, and coding. Confirmed via technical report that Gemma 4 E2B retains full vision (an earlier,
incorrect assumption that E2B dropped vision was checked and corrected). Instruction-tuned variant chosen
over base for better assistant behavior and tool-calling.

### Why Gemma over other local models (Qwen, Phi, Llama) — narrow, specific claim only
Verified: Gemma is not the best local model at raw vision quality (Qwen3-VL leads) or purest multilingual
text breadth (Qwen 3.5 has a real claim there too). The actual, defensible Gemma-specific advantage is
narrower: audio natively fused into the same small model as vision and text — a combination nothing else
in this size class matches. Do not overclaim beyond this in the pitch. Full reasoning trail available in
conversation history if needed; this entry is the condensed, load-bearing conclusion.

### Why local, not a cloud API (Gemini Live, GPT, Claude) — specific claims, not vibes
Verified, not assumed: Gemini Live caps combined audio+video sessions at 2 minutes and throttles video to
1 FPS server-side — both structurally incompatible with sustained, on-demand personal-assistant use.
Audio/video API pricing scales in a way that's incompatible with a free, everyday-use product. And for
the specific data category this product touches (ID documents, medical info, government forms), sending
that content to any third party is close to a non-starter regardless of cost or latency. "Local because
it's private" was rejected as a *general* pitch (most users don't evaluate or value that claim
abstractly) but accepted here because the specific content category makes the stakes concrete and
legible without requiring the user to understand the underlying architecture.

### Feature scope: cut "point camera at any object and ask about it"
Rejected for two reasons: (1) Google Lens already does object identification better via web-scale
retrieval — no structural advantage for Trace there; (2) it doesn't serve the actual product concept
(personal/private content), it's a generic capability demo, not a differentiated feature.

### Feature scope: cut crop/plant disease diagnosis entirely
Researched and confirmed saturated — multiple production apps already exist with 90%+ accuracy,
comprehensive databases, multilingual support, offline modes. A prior Smart India Hackathon submission
already built essentially this exact idea. Any judge familiar with the space would correctly flag this as
non-novel. Ruled out, not revisited.

### Feature scope: cut real-time scam-call detection
Rejected for two independent, both-fatal reasons: (1) irreducible false-negative risk on a
catastrophic-consequence problem — a missed detection during an actual fraud call isn't a bad UX moment,
it's someone's life savings; overclaiming reliability here would be dishonest. (2) Adoption/trust
paradox — the target demographic (elderly, less digitally literate, rural) is the least likely to grant
call-recording permissions to an unfamiliar app, and "it's local and private" is a technical reassurance
they have no framework to evaluate. This same adoption-paradox reasoning is why Trace's actual target
user (see PRD §3) is a power user who *can* evaluate a local-privacy claim, not a general/vulnerable
population being asked to trust an architecture they can't assess.

### Feature scope: cut voice-only interaction for non-literate users (separate rejected concept)
A government-form-literacy assistant concept was explored and shelved — not because the problem isn't
real (it is, well-documented), but because (a) it's already addressed at institutional scale (582,000+
CSC kiosks in India), making the differentiation story weak, and (b) research on voice interfaces for
illiterate users shows pure voice-only significantly underperforms voice+icon/visual confirmation (~53%
vs ~82% success rates in cited studies) — directly informing the "voice + real UI, not voice-only" rule
now in `/ARCHITECTURE.md`, applied to Trace's actual quiz/flashcard feature instead.

### Screen capture: MediaProjection, not AccessibilityService
See `/CONSTRAINTS.md` for the full rule. Short version: AccessibilityService's screenshot APIs are the
same mechanism used by stalkerware/banking trojans for silent capture. MediaProjection's visible system
notification is treated as a feature (proof of consent/observability), not a limitation, and is essential
to the product's own privacy-first positioning holding up under scrutiny.

### Voice interaction: push-to-talk, not always-on wake-word, for the hackathon build
Always-on listening is real future scope but a genuine live-demo risk given battery drain, false
triggers, and Android's background-service reliability constraints. Push-to-talk is the honest, buildable
choice for the event; the pitch should describe always-on as the natural next step, not claim it's
already working.

### Language/stack: Kotlin-first, Rust isolated to the Qdrant Edge JNI boundary only
Forking Google AI Edge Gallery (Kotlin) preserves the hardest-solved part of the project (LiteRT-LM +
Gemma integration) rather than re-solving it in a different stack. Rust is required only because Qdrant
Edge ships exclusively as Python bindings or a Rust crate — no native Kotlin package exists. The
integration is scoped to a small, contained JNI surface (index, query) rather than a parallel codebase.
A pure-Kotlin fallback vector-search library is the agreed fallback if this integration proves too risky
by the go/no-go date in `/ROADMAP.md` Phase 3.

### Retrieval architecture: Qdrant Edge, mobile-appropriate, confirmed publicly accessible
Initially believed gated behind a private beta based on an older secondary source; confirmed via Qdrant's
own live documentation that Quickstart docs are open with no application/beta gate required. Chosen over
building a custom embedded vector store because it's purpose-built for exactly this deployment target
(in-process, no server, mobile/robotics/kiosk use cases named explicitly by Qdrant).

### Naming: "Trace"
Chosen over more literal options (e.g., a "Hey Gemma" wake-phrase framing) to signal the product's actual
scope — finding and understanding your own content — rather than reading as a generic AI-chat brand.

### Form-fill: confirmation-gated only, never autonomous
Consistent with the broader pattern applied across every rejected/scoped-down feature in this log:
anything with real-world consequence if wrong (financial data, identity data, submitted forms) must have
a human-confirmation step. No exceptions, no "smart enough to skip it" carve-out.

### File Fetch — semantic fallback candidate scope: configurable (formerly "last 10 photos + Downloads folder" demo-only)
When a direct filename match returns zero results, the app falls back to scanning a candidate
set and classifying each image via Gemma vision ("Is this a [description]? Yes or no").

**What the scope is:**
- User configurable in settings (Downloads, Screenshots, Documents, and recent MediaStore images count [10-50]).
- Combined upper bound: configurable but typically < 100 candidates → ~0.5s classification time per image.

**Why this scope was chosen:** speed + demo realism. Originally hardcoded, it is now exposed to users to allow them to balance search exhaustiveness against inference latency (e.g., ~15s worst-case for 30 candidates). This assumption holds for live demos and short-horizon testing but does not hold for real users with large, unorganized galleries.

**What this is NOT:** a production-grade semantic search. It is a deliberately small, time-boxed shortcut
to make the hackathon demo work without building a full indexing pipeline first.

**What replaces it in Phase 3:** the Qdrant Edge RAG pipeline. The classification cache built here
(`SemanticFileCache` — query → matched FileResult, persisted in-memory per session) is intentionally
structured as a stepping stone. It proves the Gemma vision classification loop works end-to-end, gives
real data on match accuracy before the indexing work starts, and will be replaced (not extended) by
the vector-store index once Phase 3 is complete. Do not add new capability to the cache layer — keep
it minimal so Phase 3 doesn't have to unpick it later.

**Decision owner:** confirmed with user (2026-07-18) before implementation.

### Module Restructure (2026-07-18)
The product scope was restructured from a flat feature list into five distinct modules: AI Chat, Vision, RAG, Memory, and Schedules. This provides better organization and clarifies the user interaction paradigm:

1. **AI Chat** serves as the primary conversational hub, expanded to accept file and image attachments directly from the device.
2. **Vision** introduces a new physical-world, camera-first interaction paradigm, separating it cleanly from the MediaProjection-based Screen Explain feature. 
3. **RAG Ingestion Clarification:** RAG ingestion is strictly triggered by the user explicitly attaching files in AI Chat or directly selecting them. It will *never* perform a background scan of device storage. This removes the ambiguity regarding Android 13+ scoped storage limitations — those constraints applied to the old background file fetch, but RAG was never blocked by them.
4. **Memory** is restricted to a structured store for user-authored guidelines and system-authored schedules. General conversational memory across sessions is explicitly out of scope to avoid unpredictable model state accumulation.
5. **Schedules** formalizes reminder routines (e.g. medicine schedules derived from a Vision scan) into real Android notifications. This requires robust scheduling architecture (AlarmManager, WorkManager) to survive OS process death and doze mode. Custom user-created homescreen modules were cut to focus on this reliability.

### Phase 3 RAG — go/no-go resolved: pure-Kotlin, no Rust/JNI (2026-07-19)
The `/ROADMAP.md` Phase 3 go/no-go on the Qdrant Edge Rust crate + JNI bridge is **NO-GO** (already flagged in the Dev C onboarding doc, now executed). Reason: cross-compiling a Rust crate and building/debugging a JNI boundary is high-risk, high-time-cost work for a hackathon with no product-visible payoff over a simpler approach at this data scale.

**What was built instead (the agreed fallback):** a fully-Kotlin, on-device retrieval stack under `com.google.ai.edge.gallery.rag`:
- **Embeddings:** MediaPipe `TextEmbedder` running a bundled Universal Sentence Encoder `.tflite` (in `assets/`, ~5.9MB, offline). Chosen over Gemma-as-embedder (slower, ties up the LLM) and over a pure lexical/BM25 approach (no semantic recall). Adds one dependency (`com.google.mediapipe:tasks-text`) and one asset.
- **Vector store:** in-memory cosine-similarity brute-force scan. No embedded DB. Justified because RAG ingests only the user's *explicitly attached* notes (tens–low-hundreds of chunks), where a linear scan is sub-frame and a real index (Qdrant/HNSW) is unwarranted complexity. If a future scope needs whole-library indexing, revisit — but that's not this product (see the "explicit attachment only" ingestion rule above).
- **Generation:** reuses the resident Gemma model via a single silent inference (no per-request reload, no session reset), grounded strictly in retrieved chunks.

This satisfies the Phase 3 gate's fallback clause in `/ROADMAP.md` and `/PRD.md §6`. If retrieval quality proves inadequate on-device, the degrade path is unchanged: "summarize this specific note" (direct OCR + Gemma, no retrieval). **Decision owner:** confirmed with user (2026-07-19).

### RAG: web search rejected; citations + knowledge toggle added; promoted to own module (2026-07-19)
Three product calls made with the owner while building Phase 3:

1. **Web search (DuckDuckGo tool-call): REJECTED — keep offline-pure.** Technically feasible without an API key, but it directly violates the `/CONSTRAINTS.md` hard rule ("no network calls, ever, for any core feature") and would send queries — often about sensitive attached documents — to a third party, undermining the product's entire premise. Considered as an off-by-default opt-in "online mode" and still rejected for now. If ever revisited, it requires a new entry here plus the safeguards discussed: off by default, visible online indicator, warning when sensitive attachments are present.
2. **Knowledge scope toggle: ADDED.** User-facing choice between "My notes only" (strict grounding) and "Notes + AI knowledge" (Gemma may blend its internal knowledge, never contradicting the notes). Both fully offline — this is a prompt-mode switch, not a data source change.
3. **RAG promoted to a standalone homescreen module ("Notes").** Consistent with `/PRD.md §4` which lists RAG as its own P0 module; AI Chat remains an ingestion point (attachments there share the same app-scoped index via a Hilt singleton `RagEngine`). Citations are built deterministically from the actual retrieved chunks (source + snippet + score), never from model output, so they can't be hallucinated.

### Opt-in web search accepted as a deliberate non-core exception (2026-07-22)
The earlier rejection above is superseded for the narrow implementation that shipped. DuckDuckGo Lite
search is intentionally available only after an explicit sidebar toggle; its state is in memory only,
defaults to off on every launch, and is never used by a core flow. The airplane-mode demo remains fully
functional without it. This is a convenience capability for non-sensitive, current-information questions,
not part of Trace's private-content or privacy-first pitch.
