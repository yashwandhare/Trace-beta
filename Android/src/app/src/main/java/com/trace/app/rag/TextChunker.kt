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

/**
 * Splits extracted document text into passage-sized chunks for embedding.
 *
 * Strategy: paragraph-first (split on blank lines, the natural boundary in the
 * OCR/structural output from DocumentExtractor), then pack consecutive
 * paragraphs up to [targetChars] so we don't emit dozens of one-line chunks,
 * and hard-split any single paragraph longer than [maxChars] on sentence
 * boundaries. This keeps each chunk semantically coherent and within the
 * embedder's comfortable input range.
 *
 * Deliberately simple — no sliding-window overlap, no token counting. For the
 * hackathon's note-quizzing use case, paragraph packing gives good retrieval
 * without the complexity, and matches the "boring, debuggable" guidance in
 * /docs/AGENT.md.
 */
object TextChunker {

  private const val DEFAULT_TARGET_CHARS = 600
  private const val DEFAULT_MAX_CHARS = 1000
  private const val MIN_CHUNK_CHARS = 40

  private val PARAGRAPH_SPLIT = Regex("\\n\\s*\\n")
  private val SENTENCE_SPLIT = Regex("(?<=[.!?])\\s+")

  /**
   * Chunks [text] into passages.
   *
   * @param targetChars soft target size — paragraphs are packed until adding
   *   the next would exceed this.
   * @param maxChars hard ceiling — a single oversized paragraph is sentence-split
   *   to stay under this.
   */
  fun chunk(
    text: String,
    targetChars: Int = DEFAULT_TARGET_CHARS,
    maxChars: Int = DEFAULT_MAX_CHARS,
  ): List<String> {
    val normalized = text.replace("\r\n", "\n").trim()
    if (normalized.isEmpty()) return emptyList()

    val paragraphs =
      PARAGRAPH_SPLIT.split(normalized)
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val chunks = mutableListOf<String>()
    val current = StringBuilder()

    fun flush() {
      val c = current.toString().trim()
      if (c.isNotEmpty()) chunks.add(c)
      current.setLength(0)
    }

    for (paragraph in paragraphs) {
      // A single over-long paragraph: sentence-split it into its own chunks.
      if (paragraph.length > maxChars) {
        flush()
        chunks.addAll(splitLongParagraph(paragraph, targetChars, maxChars))
        continue
      }

      // Packing the next paragraph would overflow the target — start a new chunk.
      if (current.isNotEmpty() && current.length + paragraph.length + 2 > targetChars) {
        flush()
      }
      if (current.isNotEmpty()) current.append("\n\n")
      current.append(paragraph)
    }
    flush()

    // Drop trivially small trailing fragments that carry no retrievable content.
    return chunks.filter { it.length >= MIN_CHUNK_CHARS || chunks.size == 1 }
  }

  private fun splitLongParagraph(paragraph: String, targetChars: Int, maxChars: Int): List<String> {
    val sentences = SENTENCE_SPLIT.split(paragraph).map { it.trim() }.filter { it.isNotEmpty() }
    val out = mutableListOf<String>()
    val buf = StringBuilder()

    fun flush() {
      val c = buf.toString().trim()
      if (c.isNotEmpty()) out.add(c)
      buf.setLength(0)
    }

    for (sentence in sentences) {
      // A single monster sentence (rare — long OCR run-on): hard-slice it.
      if (sentence.length > maxChars) {
        flush()
        var i = 0
        while (i < sentence.length) {
          out.add(sentence.substring(i, minOf(i + maxChars, sentence.length)))
          i += maxChars
        }
        continue
      }
      if (buf.isNotEmpty() && buf.length + sentence.length + 1 > targetChars) {
        flush()
      }
      if (buf.isNotEmpty()) buf.append(' ')
      buf.append(sentence)
    }
    flush()
    return out
  }
}
