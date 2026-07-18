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

---

## 2026-07-18 — Dev B — Phase 0a
- Did:
  - B1: Removed TOS/onboarding — `AppTosDialog.kt`, `GemmaTermsOfUseDialog.kt`, `PromoScreenGm4.kt` deleted; `HomeScreen` `showTosDialog` state and call removed; `GalleryNavGraph` promo routing block removed.
  - B2: Removed model picker/manager/benchmark — deleted `GlobalModelManager.kt`, `ModelManager.kt`, `ModelList.kt`, `ModelImportDialog.kt`, `PromoBannerGm4.kt`, `ModelPicker.kt`, `ModelPickerChip.kt`, all `ui/benchmark/` files, all `ui/notifications/` files; removed `ROUTE_MODEL_LIST`, `ROUTE_MODEL_MANAGER`, `ROUTE_BENCHMARK`, `ROUTE_NOTIFICATIONS` from nav graph; home now routes directly to `ROUTE_MODEL/{taskId}/{firstModel.name}`.
  - B3: Removed unused modules — deleted `customtasks/examplecustomtask/`, `customtasks/tinygarden/`, `customtasks/mobileactions/`, `customtasks/agentchat/`, `ui/llmsingleturn/`; `ModelManagerViewModel.kt` left untouched (Dev A's).
  - B4: Renamed app — `app_name` → "Trace", `AndroidManifest.xml` `android:label` → "Trace", `settings.gradle.kts` `rootProject.name` → "Trace". Package/applicationId/deep-link scheme unchanged.
  - B5: Forced dark theme — `ThemeSettings.themeOverride` default → `THEME_DARK`; splash screen background updated to `#FF111318`.
- Broken/open:
  - `LLM_ASK_IMAGE` and `LLM_ASK_AUDIO` tasks are still registered in `LlmChatTaskModule.kt` (Dev A's file — cannot touch). These tasks will appear on HomeScreen. **Flag to Dev A**: remove or keep only `LLM_CHAT` in `LlmChatTaskModule.kt`.
  - `HomeScreen` still renders a task tile grid with all registered tasks. The "direct to chat" UX requires Dev A to either wire auto-navigation on single task or confirm whether HomeScreen is still wanted in Phase 0a.
  - `ModelPickerChip` references may remain in `ModelPageAppBar.kt` or `LlmChatScreen.kt` — Dev A should audit before combined build.
- Benchmark numbers: N/A (UI-only changes).

---

## 2026-07-18 — Dev A — Phase 0 (Foundation Completion)
- Did: 
  - Restored the benchmark UI and wired it to the "Models" drawer menu item.
  - Added a loading spinner overlay for when the model is initializing (loading into RAM/VRAM).
  - Switched the default accelerator to GPU in the model allowlist.
  - Exposed the thinking toggle capability in the model settings, disabled by default.
  - Finalized the default system prompt for "Trace", an advanced on-device AI assistant.
  - Cleaned up Dev B's unused module references and finalized the simplified welcome screen layout.
- Broken/open: None. Phase 0 is fully complete and pushed to main. Ready for Phase 1.
- Benchmark numbers (Gemma-4-E2B-it | GPU | 256 prefill / 256 decode | 3 runs): 
  - Prefill speed: 78.05 tokens/sec
  - Decode speed: 8.79 tokens/sec
  - Time to first token: 3.39 sec
  - Cold load (First init time): 19495.95 ms (~19.5s)
  - Warm load (Steady init time): 14038.13 ms (~14.0s)
