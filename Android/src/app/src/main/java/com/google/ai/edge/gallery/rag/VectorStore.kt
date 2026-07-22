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

import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory vector store for the RAG module.
 *
 * Holds embedded note chunks for the session and answers top-k nearest-neighbor
 * queries by cosine similarity. This is the pure-Kotlin fallback chosen over the
 * Qdrant Edge Rust/JNI bridge (see /docs/DECISIONS.md go/no-go). Deliberately
 * minimal: a linear scan over an in-memory list. For the hackathon's scale
 * (a user's own attached notes — tens to low-hundreds of chunks) a brute-force
 * scan is well under a frame and avoids any indexing complexity.
 *
 * Thread-safe: ingestion (add) and query can run from different coroutines.
 * Not persisted — the index lives only for the session, matching the PRD's
 * "no background indexing" and "explicit attachment only" ingestion model.
 */
class VectorStore {

  private val entries = CopyOnWriteArrayList<EmbeddedChunk>()

  /** Number of indexed chunks. */
  val size: Int
    get() = entries.size

  fun isEmpty(): Boolean = entries.isEmpty()

  /** Adds one embedded chunk. Ignores a chunk whose id is already present. */
  fun add(embedded: EmbeddedChunk) {
    if (entries.any { it.chunk.id == embedded.chunk.id }) return
    entries.add(embedded)
  }

  /** Adds many embedded chunks. */
  fun addAll(embedded: Collection<EmbeddedChunk>) {
    embedded.forEach { add(it) }
  }

  /**
   * Returns the [topK] most similar chunks to [queryEmbedding], most-relevant
   * first, filtered to those at or above [minScore]. An empty store returns an
   * empty list.
   *
   * @param minScore cosine floor; drops weak matches so an off-topic query
   *   ("quiz me on X" when no X notes exist) returns nothing rather than
   *   surfacing unrelated chunks the model would then hallucinate around.
   */
  fun query(
    queryEmbedding: FloatArray,
    topK: Int = 5,
    minScore: Float = 0.0f,
  ): List<RetrievalResult> {
    if (entries.isEmpty() || topK <= 0) return emptyList()
    return entries
      .asSequence()
      .map { entry ->
        RetrievalResult(
          chunk = entry.chunk,
          score = TextEmbedderHelper.cosineSimilarity(queryEmbedding, entry.embedding),
        )
      }
      .filter { it.score >= minScore }
      .sortedByDescending { it.score }
      .take(topK)
      .toList()
  }

  /** Removes all chunks originating from [sourceLabel] (e.g. when a file is detached). */
  fun removeSource(sourceLabel: String) {
    entries.removeAll { it.chunk.sourceLabel == sourceLabel }
  }

  /** Distinct source labels currently indexed — used for "based on: <notes>" UI. */
  fun sources(): List<String> = entries.map { it.chunk.sourceLabel }.distinct()

  /**
   * All embedded chunks originating from [sourceLabel], in insertion order —
   * used to persist a source's vectors so it can be restored on launch without
   * re-running the embedder.
   */
  fun chunksForSource(sourceLabel: String): List<EmbeddedChunk> =
    entries.filter { it.chunk.sourceLabel == sourceLabel }

  /**
   * Returns up to [topK] chunks in insertion order without similarity ranking —
   * for blank-topic requests ("quiz me" with no subject), where the grounding
   * set is simply the user's notes. Scored 0f: these were not relevance-matched,
   * so callers/citations don't imply a confidence they don't have.
   */
  fun sample(topK: Int = 5): List<RetrievalResult> =
    entries.take(topK).map { RetrievalResult(chunk = it.chunk, score = 0f) }

  /**
   * Returns up to [maxChunks] chunks spread evenly across *every* indexed source,
   * for topic-less summary/quiz requests. Unlike [sample] (the first-k in
   * ingestion order, which only ever sees the front of the first document), this
   * round-robins across sources and orders each source's picks by [NoteChunk.ordinal]
   * so a long note contributes from throughout, not just its opening.
   *
   * Scored 0f — this is coverage, not relevance ranking.
   */
  fun coverageChunks(maxChunks: Int = 12): List<RetrievalResult> {
    if (entries.isEmpty() || maxChunks <= 0) return emptyList()

    // Group by source, each ordered by position within its document.
    val bySource: Map<String, List<EmbeddedChunk>> =
      entries.groupBy { it.chunk.sourceLabel }
        .mapValues { (_, chunks) -> chunks.sortedBy { it.chunk.ordinal } }

    // Round-robin: take one chunk from each source in turn until we hit the cap
    // or exhaust every source. This spreads the budget across notes AND across
    // each note's length.
    val result = mutableListOf<EmbeddedChunk>()
    val queues = bySource.values.map { it.toMutableList() }
    while (result.size < maxChunks && queues.any { it.isNotEmpty() }) {
      for (queue in queues) {
        if (result.size >= maxChunks) break
        if (queue.isNotEmpty()) result.add(queue.removeAt(0))
      }
    }
    return result.map { RetrievalResult(chunk = it.chunk, score = 0f) }
  }

  /** Clears the whole index. */
  fun clear() {
    entries.clear()
  }
}
