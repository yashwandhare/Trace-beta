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

package com.google.ai.edge.gallery.ui.rag

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.common.DocumentExtractor
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.rag.Citation
import com.google.ai.edge.gallery.rag.KnowledgeScope
import com.google.ai.edge.gallery.rag.QuizItem
import com.google.ai.edge.gallery.rag.RagEngine
import com.google.ai.edge.gallery.rag.RagMode
import com.google.ai.edge.gallery.rag.RagResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "TraceRagViewModel"

/**
 * One turn in the Notes conversation. UI-side only — not a cross-boundary
 * contract, so it can evolve freely with the screen.
 */
sealed interface RagMessage {
  /** Something the user asked (typed question, or a Quiz/Summarize action label). */
  data class UserMessage(val text: String) : RagMessage

  /** A conversational answer (summary or follow-up), with grounding citations. */
  data class AssistantText(val text: String, val citations: List<Citation> = emptyList()) : RagMessage

  /** An interactive quiz turn — a set of MCQ/flashcard items. */
  data class AssistantQuiz(val items: List<QuizItem>, val citations: List<Citation> = emptyList()) :
    RagMessage
}

/** UI state for the RAG module screen. */
data class RagUiState(
  /** Note sources currently in the index (file display names). */
  val indexedSources: List<String> = emptyList(),
  /** True while a document is being extracted/embedded. */
  val ingesting: Boolean = false,
  /** True while retrieval + generation is running. */
  val generating: Boolean = false,
  /** The conversation transcript, oldest first. */
  val messages: List<RagMessage> = emptyList(),
  /** Non-null when the last action failed in a user-visible way. */
  val errorMessage: String? = null,
  /** User toggle: ground strictly in notes, or blend model knowledge. */
  val knowledgeScope: KnowledgeScope = KnowledgeScope.NOTES_ONLY,
)

/**
 * ViewModel for the standalone RAG module (Phase 3).
 *
 * Owns the Notes conversation. The pipeline lives in the app-scoped [RagEngine]
 * singleton, shared with AI Chat so notes attached in either place are queryable
 * from both. Three entry points drive generation — [ask] (free-form follow-up),
 * [quiz], and [summarize] — each appending a user turn then an assistant turn.
 */
@HiltViewModel
class RagViewModel @Inject constructor(
  private val ragEngine: RagEngine,
) : ViewModel() {

  private val _uiState = MutableStateFlow(RagUiState())
  val uiState = _uiState.asStateFlow()

  init {
    refreshSources()
  }

  fun setKnowledgeScope(scope: KnowledgeScope) {
    _uiState.update { it.copy(knowledgeScope = scope) }
  }

  fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  private fun refreshSources() {
    _uiState.update { it.copy(indexedSources = ragEngine.indexedSources) }
  }

  /**
   * Extracts text from a user-picked document [uri] and ingests it into the
   * shared index. Explicit selection only — never a background scan.
   */
  fun ingestDocument(context: Context, uri: Uri) {
    _uiState.update { it.copy(ingesting = true, errorMessage = null) }
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val text = DocumentExtractor(context.applicationContext).extract(uri)
        if (text.isNullOrBlank()) {
          _uiState.update {
            it.copy(ingesting = false, errorMessage = "Couldn't extract text from that document.")
          }
          return@launch
        }
        val label = uri.lastPathSegment?.substringAfterLast('/') ?: "note-${System.currentTimeMillis()}"
        val chunks = ragEngine.ingest(text, label)
        Log.d(TAG, "Ingested '$label': $chunks chunks")
        _uiState.update {
          it.copy(
            ingesting = false,
            indexedSources = ragEngine.indexedSources,
            errorMessage = if (chunks == 0) "Couldn't index that document." else null,
          )
        }
      } catch (e: Exception) {
        Log.e(TAG, "Ingest failed", e)
        _uiState.update { it.copy(ingesting = false, errorMessage = "Failed to read that document.") }
      }
    }
  }

  /** Removes one source from the index. */
  fun removeSource(sourceLabel: String) {
    ragEngine.removeSource(sourceLabel)
    refreshSources()
  }

  /**
   * Answers a free-form [query] against the indexed notes. Typed input that
   * reads as a quiz/summary request is routed to the matching mode so "quiz me"
   * still works from the text field; everything else is a grounded follow-up.
   */
  fun ask(model: Model, query: String) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return
    val mode = ragEngine.detectMode(trimmed) ?: RagMode.ASK
    run(model, userLabel = trimmed, query = trimmed, mode = mode)
  }

  /** Generates an interactive quiz, optionally focused on [topic]. */
  fun quiz(model: Model, topic: String) {
    val t = topic.trim()
    run(
      model,
      userLabel = if (t.isEmpty()) "Quiz me on my notes" else "Quiz me on: $t",
      query = t.ifBlank { "quiz me on my notes" },
      mode = RagMode.QUIZ,
    )
  }

  /** Summarizes the notes, optionally focused on [topic]. */
  fun summarize(model: Model, topic: String) {
    val t = topic.trim()
    run(
      model,
      userLabel = if (t.isEmpty()) "Summarize my notes" else "Summarize: $t",
      query = t.ifBlank { "summarize my notes" },
      mode = RagMode.SUMMARY,
    )
  }

  private fun run(model: Model, userLabel: String, query: String, mode: RagMode) {
    if (!ragEngine.hasIndexedContent) {
      _uiState.update { it.copy(errorMessage = "Attach a note or document first.") }
      return
    }
    _uiState.update {
      it.copy(
        messages = it.messages + RagMessage.UserMessage(userLabel),
        generating = true,
        errorMessage = null,
      )
    }
    viewModelScope.launch(Dispatchers.Default) {
      try {
        if (!awaitModelReady(model)) {
          _uiState.update {
            it.copy(generating = false, errorMessage = "Model still loading — try again in a moment.")
          }
          return@launch
        }
        val response =
          ragEngine.generate(
            mode = mode,
            query = query,
            model = model,
            scope = this,
            knowledgeScope = _uiState.value.knowledgeScope,
          )
        val message = response?.toMessage()
        if (message == null) {
          _uiState.update {
            it.copy(
              generating = false,
              errorMessage = "Couldn't generate from your notes. Try a different topic or re-attach the note.",
            )
          }
          return@launch
        }
        _uiState.update {
          it.copy(generating = false, messages = it.messages + message, errorMessage = null)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Generation failed", e)
        _uiState.update { it.copy(generating = false, errorMessage = "Generation failed. Try again.") }
      }
    }
  }

  /**
   * Waits up to 30s for the resident model to finish initializing. Without this,
   * a tap before init completes runs inference on a null instance and silently
   * returns nothing.
   */
  private suspend fun awaitModelReady(model: Model): Boolean {
    var waited = 0
    while (model.instance == null && waited < 30_000) {
      delay(200)
      waited += 200
    }
    return model.instance != null
  }
}

/**
 * Maps an engine [RagResponse] to a conversation message, or null when the
 * response carries nothing renderable (no quiz items and a blank summary) — the
 * caller treats that as a user-visible failure rather than an empty bubble.
 */
private fun RagResponse.toMessage(): RagMessage? =
  when {
    items.isNotEmpty() -> RagMessage.AssistantQuiz(items = items, citations = citations)
    summary.isNotBlank() -> RagMessage.AssistantText(text = summary, citations = citations)
    else -> null
  }
