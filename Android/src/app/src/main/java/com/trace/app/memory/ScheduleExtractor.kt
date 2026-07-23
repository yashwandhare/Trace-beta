/*
 * Copyright 2026 The Trace Authors
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
package com.trace.app.memory

import android.graphics.Bitmap
import android.util.Log
import com.trace.app.data.Model
import com.trace.app.notifications.NotificationScheduleManager
import com.trace.app.proto.MemoryEntry
import com.trace.app.proto.MemoryKind
import com.trace.app.proto.MemorySource
import com.trace.app.proto.ScheduledNotification
import com.trace.app.runtime.runtimeHelper
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject

/**
 * Turns a natural-language reminder request — optionally grounded in a scanned
 * prescription image or an attached document's text — into a persisted reminder:
 * a [MemoryEntry] the user can see/edit plus a [ScheduledNotification] that fires
 * the alarm. Both records share one id so the Memory and Schedules stores stay in
 * sync (see ARCHITECTURE.md "Memory store").
 *
 * 100% on-device: a single silent inference on the already-initialized model
 * extracts a small JSON schedule intent; nothing leaves the phone.
 *
 * This is the "scan a prescription → ask for a reminder → it just appears"
 * demo flow. It is deliberately model-helper-only (no chat history side effects)
 * so it can be triggered from Vision or Chat without polluting the transcript.
 */
class ScheduleExtractor(
  private val memoryRepository: MemoryRepository,
  private val scheduleManager: NotificationScheduleManager,
) {
  private val TAG = "ScheduleExtractor"

  /** Outcome of an extraction attempt. */
  sealed interface Result {
    /** A reminder was created; both records share [entry].id == schedule id. */
    data class Scheduled(val entry: MemoryEntry) : Result
    /** The model found no schedule intent in the request. */
    data object NoSchedule : Result
    /** Inference or parsing failed. */
    data class Failed(val reason: String) : Result
  }

  /**
   * Attempts to extract and persist a reminder from [request].
   *
   * @param model an initialized LLM model instance.
   * @param request the user's words (e.g. "remind me to take this twice a day").
   * @param prescriptionImage optional scanned prescription for the model to read.
   * @param documentText optional extracted text of an attached document.
   * @param source where the request originated (Vision scan vs. Chat document).
   */
  suspend fun extractAndSchedule(
    model: Model,
    request: String,
    prescriptionImage: Bitmap? = null,
    documentText: String = "",
    source: MemorySource = MemorySource.MEMORY_SOURCE_VISION_PRESCRIPTION,
    coroutineScope: CoroutineScope? = null,
  ): Result {
    val raw =
      runSilentInference(model, buildPrompt(request, documentText), prescriptionImage, coroutineScope)
        ?: return Result.Failed("inference returned nothing")

    val json = extractJsonObject(raw) ?: return Result.Failed("no JSON in model output")

    if (!json.optBoolean("has_schedule", false)) return Result.NoSchedule

    val hour = json.optInt("hour", -1)
    val minute = json.optInt("minute", -1)
    if (hour !in 0..23 || minute !in 0..59) return Result.Failed("invalid time h=$hour m=$minute")

    val title = json.optString("title").ifBlank { "Reminder" }
    val body = json.optString("body")
    val repeatDaily = json.optBoolean("repeat_daily", false)

    // One shared id → the Memory entry and the scheduled alarm reference each other.
    val id = UUID.randomUUID().toString()

    val notification =
      ScheduledNotification.newBuilder()
        .setId(id)
        .setTitle(title)
        .setMessage(body.ifBlank { title })
        .setChannelId(REMINDER_CHANNEL_ID)
        .setChannelName(REMINDER_CHANNEL_NAME)
        .setHour(hour)
        .setMinute(minute)
        .setRepeatDaily(repeatDaily)
        .build()

    if (!scheduleManager.scheduleNotification(notification)) {
      return Result.Failed("scheduleNotification failed")
    }

    val entry =
      memoryRepository.add(
        title = title,
        body = body,
        kind = MemoryKind.SYSTEM_AUTHORED,
        source = source,
        linkedScheduleId = id,
        id = id,
      )
    Log.d(TAG, "Scheduled reminder '$title' at $hour:$minute (repeat=$repeatDaily) id=$id")
    return Result.Scheduled(entry)
  }

  private suspend fun runSilentInference(
    model: Model,
    prompt: String,
    image: Bitmap?,
    coroutineScope: CoroutineScope?,
  ): String? {
    if (model.instance == null) return null
    val done = CompletableDeferred<String>()
    val sb = StringBuilder()
    model.runtimeHelper.runInference(
      model = model,
      input = prompt,
      images = if (image != null) listOf(image) else listOf(),
      resultListener = { partial, isDone, _ ->
        if (!partial.startsWith("<ctrl")) sb.append(partial)
        if (isDone && !done.isCompleted) done.complete(sb.toString())
      },
      cleanUpListener = { if (!done.isCompleted) done.complete(sb.toString()) },
      onError = {
        Log.e(TAG, "extraction inference error: $it")
        if (!done.isCompleted) done.complete("")
      },
      coroutineScope = coroutineScope,
    )
    return done.await().ifBlank { null }
  }

  private fun buildPrompt(request: String, documentText: String): String = buildString {
    append(
      "You are a scheduling assistant. From the user's request")
    if (documentText.isNotBlank()) append(" and the attached document")
    append(
      " (and the image if one is provided), decide whether they want a time-based reminder.\n" +
        "Respond with ONLY a single JSON object, no prose, no markdown fences. Schema:\n" +
        "{\"has_schedule\": bool, \"title\": string, \"body\": string, " +
        "\"hour\": int 0-23, \"minute\": int 0-59, \"repeat_daily\": bool}\n" +
        "If there is no reminder intent, return {\"has_schedule\": false}. " +
        "Use 24-hour time. If a dose repeats every day, set repeat_daily true.\n\n" +
        "User request: ")
    append(request)
    if (documentText.isNotBlank()) {
      append("\n\nAttached document:\n")
      append(documentText.take(4000))
    }
  }

  /** Pulls the first balanced {...} JSON object out of a possibly-noisy response. */
  private fun extractJsonObject(text: String): JSONObject? {
    val start = text.indexOf('{')
    if (start < 0) return null
    var depth = 0
    for (i in start until text.length) {
      when (text[i]) {
        '{' -> depth++
        '}' -> {
          depth--
          if (depth == 0) {
            return runCatching { JSONObject(text.substring(start, i + 1)) }.getOrNull()
          }
        }
      }
    }
    return null
  }

  companion object {
    const val REMINDER_CHANNEL_ID = "trace_reminders"
    const val REMINDER_CHANNEL_NAME = "Reminders"
  }
}
