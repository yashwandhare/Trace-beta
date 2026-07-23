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

import android.net.Uri
import com.trace.app.data.BuiltInTaskId
import com.trace.app.notifications.NotificationScheduleManager
import com.trace.app.proto.ScheduledNotification
import java.util.UUID

/**
 * Builds and schedules "quiz me on my notes" reminders that fire via
 * [NotificationScheduleManager]. When the notification is tapped, its deeplink
 * opens the Notes/RAG module and auto-runs a quiz over the indexed notes
 * (consumed as an initial query in `RagScreen`).
 *
 * A quiz schedule is just a normal [ScheduledNotification] whose deeplink carries
 * a quiz-phrased query — the alarm firing, notification posting, boot re-arm, and
 * tap handling are all the existing scheduling infra. This helper only owns the
 * deeplink shape so callers (and Dev C2's Schedules UI) don't hand-assemble it.
 */
object QuizScheduleHelper {
  /**
   * Builds the tap deeplink that routes into the Notes/RAG module and triggers a
   * quiz. [topic] optionally focuses the quiz; blank quizzes the whole note set.
   * The phrase intentionally contains a QUIZ trigger word ("quiz me") so the RAG
   * mode detector routes it to quiz mode.
   */
  fun buildQuizDeeplink(topic: String = ""): String {
    val query = if (topic.isBlank()) "Quiz me on my notes" else "Quiz me on $topic"
    return "com.trace.app://${BuiltInTaskId.RAG}?query=${Uri.encode(query)}"
  }

  /**
   * Schedules a quiz reminder at [hour]:[minute] (24-hour). Returns the created
   * [ScheduledNotification] on success, or null if the alarm couldn't be set.
   *
   * @param topic optional subject to focus the quiz on.
   * @param repeatDaily fire every day at the same time (e.g. a daily study nudge).
   */
  fun scheduleQuiz(
    scheduleManager: NotificationScheduleManager,
    hour: Int,
    minute: Int,
    topic: String = "",
    repeatDaily: Boolean = false,
    title: String = "Quiz time",
    message: String = if (topic.isBlank()) "Test yourself on your notes" else "Test yourself on $topic",
    id: String = UUID.randomUUID().toString(),
  ): ScheduledNotification? {
    require(hour in 0..23 && minute in 0..59) { "invalid time $hour:$minute" }
    val notification =
      ScheduledNotification.newBuilder()
        .setId(id)
        .setTitle(title)
        .setMessage(message)
        .setChannelId(QUIZ_CHANNEL_ID)
        .setChannelName(QUIZ_CHANNEL_NAME)
        .setHour(hour)
        .setMinute(minute)
        .setRepeatDaily(repeatDaily)
        .setDeeplink(buildQuizDeeplink(topic))
        .build()
    return if (scheduleManager.scheduleNotification(notification)) notification else null
  }

  const val QUIZ_CHANNEL_ID = "trace_quizzes"
  const val QUIZ_CHANNEL_NAME = "Quiz reminders"
}
