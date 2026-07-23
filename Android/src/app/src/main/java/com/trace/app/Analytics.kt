/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.trace.app

import android.os.Bundle

/**
 * No-op analytics.
 *
 * Trace is a fully offline, on-device app — it ships no telemetry. Firebase
 * Analytics (and the Google Services plugin) were removed; this stub keeps the
 * former call sites compiling and doing nothing. [firebaseAnalytics] is always
 * null so `firebaseAnalytics?.logEvent(...)` no-ops; the retained [NoOpAnalytics]
 * covers the two direct calls that weren't null-guarded.
 */
object NoOpAnalytics {
  fun logEvent(name: String, params: Bundle) {}

  fun setAnalyticsCollectionEnabled(enabled: Boolean) {}
}

/** Always null — no analytics backend. Kept so existing `?.logEvent` sites compile. */
val firebaseAnalytics: NoOpAnalytics? = null

enum class GalleryEvent(val id: String) {
  CAPABILITY_SELECT(id = "capability_select"),
  MODEL_DOWNLOAD(id = "model_download"),
  GENERATE_ACTION(id = "generate_action"),
  BUTTON_CLICKED(id = "button_clicked"),
  SKILL_MANAGEMENT(id = "skill_management"),
  SKILL_EXECUTION(id = "skill_execution"),
  CHAT_HISTORY(id = "chat_history"),
  MCP_MANAGEMENT(id = "mcp_management"),
  MCP_EXECUTION(id = "mcp_execution"),
  MODEL_CONFIG_CHANGE(id = "model_config_change"),
}
