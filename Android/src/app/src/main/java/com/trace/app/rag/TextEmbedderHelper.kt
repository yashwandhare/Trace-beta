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
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions

private const val TAG = "TraceRagEmbedder"

/** Asset filename bundled in app/src/main/assets — the on-device embedding model. */
private const val MODEL_ASSET = "universal_sentence_encoder.tflite"

/**
 * On-device text embedder for the RAG pipeline.
 *
 * Wraps MediaPipe's [TextEmbedder] running the bundled Universal Sentence
 * Encoder model. Fully offline — the model ships in the APK assets, no network
 * or Play Services model download. Produces a fixed-length float vector per
 * string that the vector store compares by cosine similarity.
 *
 * Lifecycle: create once (it loads the tflite model), reuse for the session,
 * [close] when done. Not tied to a single ViewModel so ingestion and query can
 * share one warm embedder.
 */
class TextEmbedderHelper private constructor(private val embedder: TextEmbedder) {

  /**
   * Embeds [text] into a vector. Returns null if the model produced no
   * embedding (empty/whitespace input or an internal failure) so callers can
   * skip the chunk rather than index a garbage vector.
   *
   * Blocking call — run off the main thread.
   */
  fun embed(text: String): FloatArray? {
    val clean = text.trim()
    if (clean.isEmpty()) return null
    return try {
      val result = embedder.embed(clean)
      val embedding: Embedding? = result.embeddingResult().embeddings().firstOrNull()
      embedding?.floatEmbedding()
    } catch (e: Exception) {
      Log.e(TAG, "Embedding failed", e)
      null
    }
  }

  fun close() {
    try {
      embedder.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close embedder", e)
    }
  }

  companion object {
    /**
     * Loads the embedder from the bundled model asset. Returns null if the
     * model can't be loaded (missing asset, incompatible device) — callers
     * should treat a null embedder as "RAG semantic retrieval unavailable" and
     * degrade gracefully rather than crash.
     */
    fun create(context: Context): TextEmbedderHelper? {
      return try {
        val baseOptions =
          BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build()
        val options =
          TextEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            // L2-normalize so cosine similarity reduces to a dot product and
            // scores are comparable across chunks.
            .setL2Normalize(true)
            .setQuantize(false)
            .build()
        val embedder = TextEmbedder.createFromOptions(context, options)
        Log.d(TAG, "TextEmbedder loaded from asset '$MODEL_ASSET'")
        TextEmbedderHelper(embedder)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to create TextEmbedder from '$MODEL_ASSET'", e)
        null
      }
    }

    /**
     * Cosine similarity between two L2-normalized vectors (reduces to a dot
     * product, but we don't assume normalization here to stay correct if the
     * model config changes). Returns a score in [-1, 1]; higher is closer.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
      if (a.size != b.size || a.isEmpty()) return 0f
      var dot = 0f
      var normA = 0f
      var normB = 0f
      for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
      }
      if (normA == 0f || normB == 0f) return 0f
      return dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    }
  }
}
