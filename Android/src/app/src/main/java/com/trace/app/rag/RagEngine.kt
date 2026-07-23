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

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import com.trace.app.data.Model
import com.trace.app.proto.ChunkVectorProto
import com.trace.app.proto.NoteSourceProto
import com.trace.app.proto.NotesIndex
import com.trace.app.runtime.runtimeHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

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
class RagEngine(appContext: Context, private val notesIndexStore: DataStore<NotesIndex>) {

  private val repository = RagRepository(appContext)

  val hasIndexedContent: Boolean
    get() = repository.hasIndexedContent

  val indexedSources: List<String>
    get() = repository.indexedSources

  /**
   * Ingests an attached document (see [RagRepository.ingest]) and persists its
   * extracted text AND embeddings so it survives process death — restored on
   * next launch by [warmUp] without re-running the embedder. Persistence is
   * best-effort; a failure here doesn't fail the ingest.
   */
  suspend fun ingest(text: String, sourceLabel: String): Int {
    val indexed = repository.ingest(text, sourceLabel)
    if (indexed > 0) persistSource(sourceLabel, text)
    return indexed
  }

  fun removeSource(sourceLabel: String) = repository.removeSource(sourceLabel)

  fun clearIndex() = repository.clearIndex()

  // ---------------------------------------------------------------------------
  // Persistence (extracted text + embeddings; loaded directly on launch, only
  // re-embedded if a source has no persisted vectors — e.g. legacy data)
  // ---------------------------------------------------------------------------

  private suspend fun persistSource(sourceLabel: String, text: String) {
    try {
      // Snapshot the embeddings the store just produced for this source so the
      // launch path can skip the embedder entirely.
      val chunkProtos =
        repository.embeddedChunksFor(sourceLabel).map { ec ->
          ChunkVectorProto.newBuilder()
            .setId(ec.chunk.id)
            .setText(ec.chunk.text)
            .setOrdinal(ec.chunk.ordinal)
            .addAllEmbedding(ec.embedding.asList())
            .build()
        }
      notesIndexStore.updateData { current ->
        val kept = current.sourcesList.filter { it.sourceLabel != sourceLabel }
        NotesIndex.newBuilder()
          .addAllSources(kept)
          .addSources(
            NoteSourceProto.newBuilder()
              .setSourceLabel(sourceLabel)
              .setExtractedText(text)
              .setIngestedAtMs(System.currentTimeMillis())
              .addAllChunks(chunkProtos)
              .build()
          )
          .build()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to persist source '$sourceLabel'", e)
    }
  }

  /** Drops a persisted source (call alongside [removeSource]). Best-effort. */
  suspend fun forgetSource(sourceLabel: String) {
    try {
      notesIndexStore.updateData { current ->
        NotesIndex.newBuilder()
          .addAllSources(current.sourcesList.filter { it.sourceLabel != sourceLabel })
          .build()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to forget source '$sourceLabel'", e)
    }
  }

  /** Clears all persisted sources (call alongside [clearIndex]). Best-effort. */
  suspend fun forgetAllSources() {
    try {
      notesIndexStore.updateData { NotesIndex.getDefaultInstance() }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to clear persisted sources", e)
    }
  }

  /**
   * Restores persisted note sources into the in-memory store. For each source
   * with persisted embeddings, loads the vectors directly — no embedder call,
   * so a cold launch no longer pays the re-embed cost. Sources without vectors
   * (legacy data, or a prior run where vector persistence failed) fall back to
   * re-embedding from the retained text. Idempotent: skips already-indexed
   * sources. Call once on first Notes/RAG init; safe if nothing is persisted.
   */
  suspend fun warmUp() {
    if (repository.hasIndexedContent) return
    val persisted =
      try {
        notesIndexStore.data.first().sourcesList
      } catch (e: Exception) {
        Log.w(TAG, "Failed to read persisted sources", e)
        return
      }
    if (persisted.isEmpty()) return
    val already = repository.indexedSources.toSet()
    var loaded = 0
    var reEmbedded = 0
    for (source in persisted) {
      if (source.sourceLabel in already) continue
      try {
        if (source.chunksList.isNotEmpty()) {
          val embedded =
            source.chunksList.map { c ->
              EmbeddedChunk(
                chunk =
                  NoteChunk(
                    id = c.id,
                    text = c.text,
                    sourceLabel = source.sourceLabel,
                    ordinal = c.ordinal,
                  ),
                embedding = c.embeddingList.toFloatArray(),
              )
            }
          repository.loadEmbedded(source.sourceLabel, embedded)
          loaded++
        } else {
          // Legacy / vectorless source: re-embed from retained text, then
          // persist so the next launch takes the fast path.
          if (repository.ingest(source.extractedText, source.sourceLabel) > 0) {
            persistSource(source.sourceLabel, source.extractedText)
          }
          reEmbedded++
        }
      } catch (e: Exception) {
        Log.w(TAG, "warmUp: failed to restore '${source.sourceLabel}'", e)
      }
    }
    Log.d(TAG, "warmUp: restored ${persisted.size} source(s) ($loaded from vectors, $reEmbedded re-embedded)")
  }

  /**
   * Detects whether [query] is a RAG request against indexed content. Returns
   * the [RagMode] or null (caller routes to normal chat).
   */
  fun detectMode(query: String): RagMode? =
    RagGenerator.detectMode(query, repository.hasIndexedContent)

  /**
   * Runs the full retrieve → generate path for a request of [mode]. [topic] is
   * the user's subject and may be blank: a blank topic (e.g. "Summarize" with no
   * subject) grounds on a coverage spread across all notes, while a real topic
   * runs relevance retrieval and only falls back to coverage if nothing matches.
   * This is what keeps a summary/quiz from being built off an arbitrary prefix
   * of the first document. Returns null if nothing at all is indexed.
   *
   * @param model the resident Gemma model instance.
   * @param scope coroutine scope for the inference (the caller's ViewModel scope).
   * @param knowledgeScope notes-only vs notes+model-knowledge (user toggle).
   */
  suspend fun generate(
    mode: RagMode,
    topic: String,
    model: Model,
    scope: CoroutineScope,
    knowledgeScope: KnowledgeScope = KnowledgeScope.NOTES_ONLY,
  ): RagResponse? {
    val hasTopic = topic.isNotBlank()
    val retrieved =
      if (hasTopic) {
        // A named subject: relevance retrieval, falling back to whole-note
        // coverage only if the query embeds too far from every chunk.
        repository.retrieve(topic).ifEmpty { repository.coverageChunks() }
      } else {
        // No subject ("summarize my notes"): a generic query embeds far from any
        // one chunk and would degrade to an arbitrary prefix, so ground on a
        // spread across all notes directly.
        repository.coverageChunks()
      }
    if (retrieved.isEmpty()) {
      Log.d(TAG, "No chunks to ground on; skipping generation")
      return null
    }
    // The query passed into the prompt: the topic when present, else a mode-
    // appropriate instruction so the model still knows what to produce.
    val query =
      if (hasTopic) topic
      else if (mode == RagMode.QUIZ) "Create a quiz covering the key points of these notes."
      else "Summarize these notes."
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
      RagMode.ASK -> RagResponse(summary = raw.trim(), sources = sources, citations = citations)
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
