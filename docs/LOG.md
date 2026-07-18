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
