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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "TraceRagRepository"

/**
 * Session-scoped coordinator for the RAG pipeline.
 *
 * Owns the embedder and the vector store, and exposes the two operations the
 * rest of the app needs:
 *   - [ingest]: take explicitly-attached note text, chunk → embed → index.
 *   - [retrieve]: embed a query and return the top-k grounding chunks.
 *
 * Ingestion is strictly from text the user explicitly attached (per
 * /docs/PRD.md and /docs/DECISIONS.md — RAG never background-scans storage).
 * The embedder is created lazily on first use so app startup isn't slowed, and
 * a failed/absent model degrades to [isSemanticAvailable] == false rather than
 * crashing.
 *
 * All heavy work runs on [Dispatchers.Default]; callers can invoke from any
 * scope. A mutex serializes embedder access — MediaPipe's TextEmbedder is not
 * guaranteed reentrant across threads.
 */
class RagRepository(private val appContext: Context) {

  private val store = VectorStore()
  private val embedderMutex = Mutex()
  private var embedder: TextEmbedderHelper? = null
  private var embedderInitFailed = false

  /** True once at least one chunk is indexed and semantic retrieval is possible. */
  val hasIndexedContent: Boolean
    get() = !store.isEmpty()

  val indexedSources: List<String>
    get() = store.sources()

  private suspend fun getEmbedder(): TextEmbedderHelper? =
    embedderMutex.withLock {
      if (embedder == null && !embedderInitFailed) {
        embedder = TextEmbedderHelper.create(appContext)
        if (embedder == null) {
          embedderInitFailed = true
          Log.w(TAG, "Embedder unavailable — RAG semantic retrieval disabled")
        }
      }
      embedder
    }

  /**
   * Ingests one attached document's extracted [text] under a human-readable
   * [sourceLabel] (e.g. the file's display name). Chunks, embeds, and indexes.
   * Returns the number of chunks indexed (0 if the embedder is unavailable or
   * the text produced no usable chunks). Idempotent per source: re-ingesting
   * the same label first clears its prior chunks.
   */
  suspend fun ingest(text: String, sourceLabel: String): Int =
    withContext(Dispatchers.Default) {
      val emb = getEmbedder() ?: return@withContext 0
      val chunks = TextChunker.chunk(text)
      if (chunks.isEmpty()) return@withContext 0

      // Replace any existing chunks for this source so re-attaching updates.
      store.removeSource(sourceLabel)

      var indexed = 0
      embedderMutex.withLock {
        chunks.forEachIndexed { i, chunkText ->
          val vector = emb.embed(chunkText) ?: return@forEachIndexed
          store.add(
            EmbeddedChunk(
              chunk =
                NoteChunk(
                  id = "$sourceLabel#$i",
                  text = chunkText,
                  sourceLabel = sourceLabel,
                  ordinal = i,
                ),
              embedding = vector,
            )
          )
          indexed++
        }
      }
      Log.d(TAG, "Ingested '$sourceLabel': ${chunks.size} chunks → $indexed indexed (store=${store.size})")
      indexed
    }

  /**
   * Loads pre-embedded chunks for [sourceLabel] straight into the store, with no
   * embedder call — the launch-restore path for vectors persisted on a prior
   * run. Replaces any existing chunks for the source. Returns the count loaded.
   */
  fun loadEmbedded(sourceLabel: String, embedded: List<EmbeddedChunk>): Int {
    if (embedded.isEmpty()) return 0
    store.removeSource(sourceLabel)
    store.addAll(embedded)
    Log.d(TAG, "Loaded ${embedded.size} persisted chunk(s) for '$sourceLabel' (store=${store.size})")
    return embedded.size
  }

  /** The embedded chunks currently indexed for [sourceLabel], for persistence. */
  fun embeddedChunksFor(sourceLabel: String): List<EmbeddedChunk> =
    store.chunksForSource(sourceLabel)

  /**
   * Retrieves the [topK] chunks most relevant to [query]. Returns an empty list
   * if nothing is indexed, the embedder is unavailable, or nothing clears
   * [minScore]. Runs off the main thread.
   */
  suspend fun retrieve(
    query: String,
    topK: Int = 8,
    minScore: Float = 0.15f,
  ): List<RetrievalResult> =
    withContext(Dispatchers.Default) {
      if (store.isEmpty()) return@withContext emptyList()
      val emb = getEmbedder() ?: return@withContext emptyList()
      val queryVector = embedderMutex.withLock { emb.embed(query) } ?: return@withContext emptyList()
      store.query(queryVector, topK = topK, minScore = minScore)
    }

  /**
   * Fallback grounding when the query names no specific topic ("quiz me on my
   * notes"): the first [topK] chunks in ingestion order, no similarity filter.
   */
  fun sampleChunks(topK: Int = 5): List<RetrievalResult> = store.sample(topK)

  /**
   * Grounding for topic-less summary/quiz requests: chunks spread evenly across
   * every indexed source (see [VectorStore.coverageChunks]) so the whole of the
   * notes is represented, not just the front of the first document.
   */
  fun coverageChunks(maxChunks: Int = 12): List<RetrievalResult> = store.coverageChunks(maxChunks)

  /** Drops a source from the index (e.g. when the user removes an attachment). */
  fun removeSource(sourceLabel: String) = store.removeSource(sourceLabel)

  /** Clears all indexed content (e.g. on session reset). */
  fun clearIndex() = store.clear()

  /** Releases the embedder. Call when the owning ViewModel is cleared. */
  suspend fun close() {
    embedderMutex.withLock {
      embedder?.close()
      embedder = null
    }
  }
}
