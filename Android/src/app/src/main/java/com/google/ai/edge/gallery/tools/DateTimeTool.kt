/*
 * Copyright 2026 Google LLC
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
package com.google.ai.edge.gallery.tools

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Deterministic answer for "what's today's date" / "what time is it" style questions.
 *
 * Gemma has no wall-clock access, so this never goes through the model — it's a Kotlin-only
 * fact lookup, formatted and shown directly as an agent message. See
 * `LlmChatViewModelBase.tryHandleQuickTools` for the interception point.
 */
object DateTimeTool {

  /**
   * Curated phrase list, not a bare "date"/"time" keyword match — those words are common in
   * ordinary conversation ("let's set a date for the demo"), so this requires an actual
   * date/time QUESTION shape. Checked with `containsMatchIn` (not `startsWith`) since these
   * questions often appear mid-sentence, unlike the websearch command-prefix convention.
   */
  private val TRIGGER = Regex(
    "\\b(?:" +
      "what(?:'s| is) (?:today'?s |the )?date\\b" +
      "|today'?s date\\b" +
      "|current date\\b" +
      "|what day is (?:it|today)\\b" +
      "|what(?:'s| is) the time\\b" +
      "|current time\\b" +
      "|what time is it\\b" +
      ")",
    RegexOption.IGNORE_CASE,
  )

  fun isDateTimeQuery(input: String): Boolean = TRIGGER.containsMatchIn(input)

  /** Formats "now" as a short, natural sentence — e.g. "Today is Wednesday, July 22, 2026, 21:04." */
  fun answer(): String {
    val now = LocalDateTime.now()
    val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val datePart = now.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
    val timePart = now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    return "Today is $weekday, $datePart, $timePart."
  }
}
