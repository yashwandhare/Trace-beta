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

package com.google.ai.edge.gallery.rag

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.runtime.runtimeHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope

private const val TAG = "TraceRagEngine"

/**
 * End-to-end RAG engine: ingest attached notes, then answer a query by
 * retrieving grounding chunks and asking the resident Gemma model to generate a
 * quiz or summary strictly from them.
 *
 * This wraps [RagRepository] (embedding + retrieval) and reuses the already-warm
 * Gemma instance for generation via a single silent inference — the same
 * pattern the file-fetch semantic classifier uses, so we never load a second
 * model or reset the user's chat session.
 *
 * Ownership: create one per RAG-capable screen/ViewModel, pass the [Model] and a
 * [CoroutineScope] (the ViewModel's) into [generate], and [close] on clear.
 */
class RagEngine(appContext: Context) {

  private val repository = RagRepository(appContext)

  val hasIndexedContent: Boolean
    get() = repository.hasIndexedContent

  val indexedSources: List<String>
    get() = repository.indexedSources

  /** Ingests an attached document. See [RagRepository.ingest]. */
  suspend fun ingest(text: String, sourceLabel: String): Int =
    repository.ingest(text, sourceLabel)

  fun removeSource(sourceLabel: String) = repository.removeSource(sourceLabel)

  fun clearIndex() = repository.clearIndex()

  /**
   * Detects whether [query] is a RAG request against indexed content. Returns
   * the [RagMode] or null (caller routes to normal chat).
   */
  fun detectMode(query: String): RagMode? =
    RagGenerator.detectMode(query, repository.hasIndexedContent)

  /**
   * Runs the full retrieve → generate path for a query already known to be a RAG
   * request of [mode]. Retrieves grounding chunks, prompts Gemma, and parses the
   * result into a [RagResponse] carrying deterministic citations. Returns null
   * if nothing relevant was retrieved (caller should tell the user their notes
   * don't cover the topic).
   *
   * @param model the resident Gemma model instance.
   * @param scope coroutine scope for the inference (the caller's ViewModel scope).
   * @param knowledgeScope notes-only vs notes+model-knowledge (user toggle).
   */
  suspend fun generate(
    mode: RagMode,
    query: String,
    model: Model,
    scope: CoroutineScope,
    knowledgeScope: KnowledgeScope = KnowledgeScope.NOTES_ONLY,
  ): RagResponse? {
    val retrieved = repository.retrieve(query, topK = 5).ifEmpty {
      // Generic requests ("quiz me on my notes") often embed far from any one
      // chunk. If nothing clears the similarity floor but notes ARE indexed,
      // ground on a sample of the notes rather than failing.
      repository.sampleChunks(topK = 5)
    }
    if (retrieved.isEmpty()) {
      Log.d(TAG, "No relevant chunks for query; skipping generation")
      return null
    }
    val sources = retrieved.map { it.chunk.sourceLabel }.distinct()
    val citations = RagGenerator.buildCitations(retrieved)
    val prompt = RagGenerator.buildPrompt(mode, query, retrieved, knowledgeScope)

    val raw = runSilentInference(model, prompt, scope)
    Log.d(TAG, "RAG generation (${mode.name}, ${knowledgeScope.name}) produced ${raw.length} chars from ${retrieved.size} chunks")

    return when (mode) {
      RagMode.QUIZ -> {
        val items = RagGenerator.parseQuiz(raw, sources)
        if (items.isEmpty()) {
          // Model didn't return parseable quiz JSON — fall back to surfacing the
          // raw text as a summary so the user still gets grounded output.
          RagResponse(summary = raw.trim(), sources = sources, citations = citations)
        } else {
          RagResponse(items = items, sources = sources, citations = citations)
        }
      }
      RagMode.SUMMARY -> RagResponse(summary = raw.trim(), sources = sources, citations = citations)
    }
  }

  /**
   * One-shot inference on the resident model, accumulating streamed deltas into
   * the full response. Does NOT touch chat history. Mirrors the semantic-file
   * classifier pattern in LlmChatViewModel.
   */
  private suspend fun runSilentInference(
    model: Model,
    prompt: String,
    scope: CoroutineScope,
  ): String {
    val result = CompletableDeferred<String>()
    val sb = StringBuilder()
    model.runtimeHelper.runInference(
      model = model,
      input = prompt,
      resultListener = { partialResult, done, _ ->
        // partialResult is a streamed delta; accumulate. On done it's empty.
        if (!partialResult.startsWith("<ctrl")) sb.append(partialResult)
        if (done && !result.isCompleted) result.complete(sb.toString())
      },
      cleanUpListener = {
        if (!result.isCompleted) result.complete(sb.toString())
      },
      onError = { msg ->
        Log.e(TAG, "RAG inference error: $msg")
        if (!result.isCompleted) result.complete(sb.toString())
      },
      coroutineScope = scope,
    )
    return result.await()
  }

  suspend fun close() = repository.close()
}
