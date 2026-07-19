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
import com.google.ai.edge.gallery.rag.KnowledgeScope
import com.google.ai.edge.gallery.rag.RagEngine
import com.google.ai.edge.gallery.rag.RagMode
import com.google.ai.edge.gallery.rag.RagResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "TraceRagViewModel"

/** UI state for the RAG module screen. */
data class RagUiState(
  /** Note sources currently in the index (file display names). */
  val indexedSources: List<String> = emptyList(),
  /** True while a document is being extracted/embedded. */
  val ingesting: Boolean = false,
  /** True while retrieval + generation is running. */
  val generating: Boolean = false,
  /** The latest generation result to render (quiz cards or summary + citations). */
  val response: RagResponse? = null,
  /** Non-null when the last action failed in a user-visible way. */
  val errorMessage: String? = null,
  /** User toggle: ground strictly in notes, or blend model knowledge. */
  val knowledgeScope: KnowledgeScope = KnowledgeScope.NOTES_ONLY,
)

/**
 * ViewModel for the standalone RAG module (Phase 3).
 *
 * Owns UI state only — the pipeline lives in the app-scoped [RagEngine]
 * singleton, shared with AI Chat so notes attached in either place are
 * queryable from both. The Quiz/Flashcard rendering consumes [uiState.response]
 * (Dev C2's lane); this ViewModel is the backend boundary.
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

  fun clearResponse() {
    _uiState.update { it.copy(response = null) }
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
   * Runs quiz or summary generation for [query] against the indexed notes,
   * grounded per the current knowledge-scope toggle.
   */
  fun generate(model: Model, query: String, mode: RagMode) {
    if (!ragEngine.hasIndexedContent) {
      _uiState.update { it.copy(errorMessage = "Attach a note or document first.") }
      return
    }
    _uiState.update { it.copy(generating = true, errorMessage = null, response = null) }
    viewModelScope.launch(Dispatchers.Default) {
      try {
        // Wait for the resident model to finish initializing (up to 30s). Without
        // this, a tap before init completes runs inference on a null instance and
        // silently returns nothing.
        var waited = 0
        while (model.instance == null && waited < 30_000) {
          kotlinx.coroutines.delay(200)
          waited += 200
        }
        if (model.instance == null) {
          _uiState.update {
            it.copy(generating = false, errorMessage = "Model still loading — try again in a moment.")
          }
          return@launch
        }
        val response =
          ragEngine.generate(
            mode = mode,
            query = query.ifBlank { if (mode == RagMode.QUIZ) "quiz me on my notes" else "summarize my notes" },
            model = model,
            scope = this,
            knowledgeScope = _uiState.value.knowledgeScope,
          )
        // Treat an all-empty response (no quiz items, blank summary) as a failure
        // rather than rendering an empty results area that looks like nothing happened.
        val isEmpty = response == null ||
          (response.items.isEmpty() && response.summary.isBlank())
        _uiState.update {
          it.copy(
            generating = false,
            response = if (isEmpty) null else response,
            errorMessage =
              if (isEmpty) "Couldn't generate from your notes. Try a different topic or re-attach the note." else null,
          )
        }
      } catch (e: Exception) {
        Log.e(TAG, "Generation failed", e)
        _uiState.update { it.copy(generating = false, errorMessage = "Generation failed. Try again.") }
      }
    }
  }
}
