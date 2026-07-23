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

package com.trace.app.rag

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "TraceRagGenerator"

/** What the user asked the RAG module to produce from their notes. */
enum class RagMode {
  QUIZ,
  SUMMARY,
  /** A free-form question answered conversationally, grounded in the notes. */
  ASK,
}

/**
 * Knowledge scope for generation — the user-facing toggle. NOTES_ONLY keeps
 * Gemma strictly grounded in retrieved passages; NOTES_AND_MODEL lets it blend
 * its own internal knowledge with the notes (clearly separated in the prompt).
 * Both are fully offline.
 */
enum class KnowledgeScope {
  NOTES_ONLY,
  NOTES_AND_MODEL,
}

/**
 * Stateless helpers for the retrieval-augmented generation step: deciding
 * whether a query is a RAG request, building the grounded prompt from retrieved
 * chunks, and parsing Gemma's response into typed [QuizItem]s.
 *
 * The actual model call is NOT here — it lives in the ViewModel which owns the
 * resident Gemma instance. This object is pure logic so it's trivially testable
 * and carries no Android/model dependencies.
 */
object RagGenerator {

  // Trigger phrases. Kept simple and explicit — matches /docs/AGENT.md guidance
  // to prefer boring, debuggable rules over a second classifier model.
  private val QUIZ_TRIGGERS =
    listOf("quiz me", "test me", "flashcard", "flash card", "make a quiz", "quiz on", "practice question")
  private val SUMMARY_TRIGGERS =
    listOf("summarize", "summarise", "summary of", "give me a summary", "recap my", "tl;dr", "key points")
  // "my notes / my document" style references that signal grounding-in-attachments.
  private val NOTE_REFERENCES =
    listOf("my notes", "my note", "these notes", "the notes", "my document", "the document", "attached", "my pdf")

  /**
   * Classifies [query] as a RAG request or not. Returns the [RagMode] if the
   * query is asking to quiz/summarize the user's own content, else null (route
   * to normal chat). Only fires when there IS indexed content — a quiz request
   * with nothing attached should fall through to normal chat, which can tell the
   * user to attach notes first.
   */
  fun detectMode(query: String, hasIndexedContent: Boolean): RagMode? {
    if (!hasIndexedContent) return null
    val q = query.lowercase()
    val quiz = QUIZ_TRIGGERS.any { q.contains(it) }
    val summary = SUMMARY_TRIGGERS.any { q.contains(it) }
    val noteRef = NOTE_REFERENCES.any { q.contains(it) }

    return when {
      quiz -> RagMode.QUIZ
      // A summary request only counts as RAG when it references the user's
      // content — "summarize this article for me" about pasted text is chat.
      summary && noteRef -> RagMode.SUMMARY
      else -> null
    }
  }

  /** Builds the grounded prompt sent to Gemma for the given [mode] and [scope]. */
  fun buildPrompt(
    mode: RagMode,
    query: String,
    retrieved: List<RetrievalResult>,
    scope: KnowledgeScope = KnowledgeScope.NOTES_ONLY,
  ): String {
    val context =
      retrieved.joinToString("\n\n") { "[${it.chunk.sourceLabel}] ${it.chunk.text}" }

    val groundingRule = when (scope) {
      KnowledgeScope.NOTES_ONLY ->
        "Use ONLY the provided notes — do not add outside facts."
      KnowledgeScope.NOTES_AND_MODEL ->
        "Ground your answer in the provided notes first; you may add your own knowledge " +
          "to clarify or extend, but never contradict the notes."
    }

    return when (mode) {
      RagMode.QUIZ ->
        """
        You are generating a quiz from the user's own notes below. $groundingRule
        If the notes don't support a question, don't invent one.

        Return ONLY a JSON array of 3-5 questions, no prose before or after. Each item:
        {"question": "...", "answer": "...", "options": ["...","...","...","..."]}
        - "options" must be 4 choices with exactly one correct, and "answer" must be one
          of the options verbatim. Omit "options" for a plain flashcard-style item.

        NOTES:
        $context

        User request: $query
        JSON:
        """.trimIndent()

      RagMode.SUMMARY ->
        """
        Summarize the user's own notes below in 3-5 concise sentences. $groundingRule
        Plain text, no markdown.

        NOTES:
        $context

        User request: $query
        Summary:
        """.trimIndent()

      RagMode.ASK ->
        """
        Answer the user's question using their own notes below. $groundingRule
        Reply conversationally in a few sentences. Plain text, no markdown.
        If the notes don't cover it, say so plainly instead of guessing.

        NOTES:
        $context

        Question: $query
        Answer:
        """.trimIndent()
    }
  }

  /**
   * Builds display citations from retrieval results — deterministic, straight
   * from what was actually fed to the model.
   */
  fun buildCitations(retrieved: List<RetrievalResult>, maxSnippetChars: Int = 200): List<Citation> =
    retrieved.map { r ->
      Citation(
        sourceLabel = r.chunk.sourceLabel,
        snippet =
          if (r.chunk.text.length <= maxSnippetChars) r.chunk.text
          else r.chunk.text.take(maxSnippetChars).trimEnd() + "…",
        score = r.score,
      )
    }

  /**
   * Parses Gemma's raw [response] for a quiz into [QuizItem]s. Tolerant of the
   * model wrapping JSON in ``` fences or adding stray prose — extracts the first
   * JSON array it finds. Returns an empty list if nothing parseable (caller then
   * falls back to showing the raw text).
   *
   * [sources] labels are attached to each item for the "from your notes" UI.
   */
  fun parseQuiz(response: String, sources: List<String>): List<QuizItem> {
    val json = extractJsonArray(response) ?: return emptyList()
    return try {
      val arr = JSONArray(json)
      val sourceLabel = sources.joinToString(", ")
      val items = mutableListOf<QuizItem>()
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val question = obj.optString("question").trim()
        val answer = obj.optString("answer").trim()
        if (question.isEmpty() || answer.isEmpty()) continue
        val options = parseOptions(obj)
        items.add(
          QuizItem(
            question = question,
            answer = answer,
            options = options,
            sourceLabel = sourceLabel,
          )
        )
      }
      items
    } catch (e: Exception) {
      Log.e(TAG, "Failed to parse quiz JSON", e)
      emptyList()
    }
  }

  private fun parseOptions(obj: JSONObject): List<String> {
    val optsArr = obj.optJSONArray("options") ?: return emptyList()
    val opts = (0 until optsArr.length()).mapNotNull { optsArr.optString(it).trim().ifEmpty { null } }
    // A well-formed MC item needs the answer among the options; if the model
    // returned options that don't include the answer, treat it as a flashcard
    // rather than showing a broken multiple-choice card.
    val answer = obj.optString("answer").trim()
    return if (opts.size >= 2 && opts.any { it.equals(answer, ignoreCase = true) }) opts else emptyList()
  }

  /** Extracts the first top-level JSON array substring, ignoring code fences/prose. */
  private fun extractJsonArray(text: String): String? {
    val start = text.indexOf('[')
    val end = text.lastIndexOf(']')
    if (start < 0 || end <= start) return null
    return text.substring(start, end + 1)
  }
}
