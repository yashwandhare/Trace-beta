# Trace — Constraints

Hard rules. These are not style preferences — each one exists for a specific reason (security,
liability, product-scope, or a hardware/time reality). Do not override any of these without an explicit
new entry in `/DECISIONS.md` explaining why.

## Security

- **Never use AccessibilityService for screen reading.** `takeScreenshot()` and related AccessibilityService
  APIs are the same mechanism used by stalkerware and banking trojans for silent, invisible screen
  capture — no notification, no visible indicator. Use **MediaProjection** instead: it's slower, but it
  shows a visible system "screen recording" notification, which is a feature for this product's privacy
  pitch, not a limitation. This is core to the "you don't stress about security" positioning — using the
  same API surface as malware would directly undermine it.

## Scope — what this product is not

- **Not a general-purpose chatbot.** Every feature must trace back to: finding, understanding, or acting
  on the user's own private content (files, screen, notes). "Ask me anything" is explicitly out — it's
  undifferentiated against Gemini Live and isn't the product being pitched.
- **Not an object/world-recognition app.** No plant/crop diagnosis, no "what is this object" camera
  features. This category is saturated (existing production apps, published research, prior hackathon
  submissions) and was deliberately ruled out. Do not add it back in.
- **Not a multi-device or swarm system.** Single phone, single user, single model instance.

## Reliability / demo-safety

- **No true always-on wake-word listening for the hackathon build.** Push-to-talk only. Always-on
  listening is real future scope (see `/ROADMAP.md` Phase 5) but battery drain, false triggers, and
  background-service reliability under Android's OS-level app-killing make it a genuine live-demo risk.
  Don't build it now; don't let the pitch overclaim it as already working.
- **Every feature must function with the network disabled.** No feature is complete until it's been
  tested in airplane mode. A single accidental network call anywhere breaks the entire premise of the
  product if discovered during a live demo.
  - **One deliberate, explicit exception: opt-in web search.**
    `ModelManagerViewModel.uiState.webSearchEnabled` gates the only network call in the app
    (`websearch/WebSearchClient.kt`, DuckDuckGo lite). It is session-only and OFF by default —
    intentionally NOT persisted to disk, so it resets to off on every app launch and must be
    re-enabled each session via the sidebar toggle. Never auto-enabled, never triggered by a
    background flow. The core airplane-mode demo works untouched since a fresh launch always starts
    with it off. Any new feature must NOT silently depend on this toggle being on.
- **The model must load once and stay resident.** Never write code that reloads Gemma per-request — cold
  load is ~90-100s on target hardware, which would make the app unusable and the demo dead on arrival.

## Data / action safety

- **No autonomous actions on personal data.** Form-fill assist may surface and suggest information, but
  must always show the user what it's about to provide and require explicit confirmation before filling
  or submitting anything. Never auto-submit a form. Never take an irreversible action without
  confirmation.
- **No claims of diagnostic or medical certainty.** If the medicine/document-understanding feature
  touches health content, it explains what a document says — it does not diagnose, recommend dosages, or
  claim clinical accuracy. Reference general product safety framing from `/PRD.md`.

## Hardware / platform

- **Phone only. No external hardware.** No Raspberry Pi, no Jetson, no ESP32, no companion devices. If a
  feature seems to require external compute, it's out of scope, not a reason to add hardware.
- **Kotlin is the app language.** The planned Qdrant Edge/Rust JNI path was rejected during Phase 3;
  Trace ships a pure-Kotlin on-device vector search instead. Do not introduce Rust/JNI without an
  explicit new decision.

## Positioning discipline

- **Every feature must have an honest, defensible "why does this need to be local / why does this need
  Gemma specifically" answer.** If a feature would work identically with a cloud API swapped in, it's
  either not core to the pitch or needs to be reframed. See `/DECISIONS.md` for the full reasoning chain
  that arrived at the current feature set — don't add features that fail this test without discussing it
  first.
