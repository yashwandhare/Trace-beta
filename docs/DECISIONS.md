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
