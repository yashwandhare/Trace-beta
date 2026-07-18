/*
 * Trace — DefaultIntentFileFetchHandler (Phase 2, Dev B)
 *
 * Concrete implementation of IntentFileFetchHandler backed by FileFetcher.
 *
 * Lookup strategy (two-pass):
 *   Pass 1 — direct filename match via FileFetcher (MediaStore LIKE query).
 *   Pass 2 — semantic fallback via SemanticFileMatcher (Gemma vision classification).
 *            Only runs when Pass 1 returns zero results AND a classifier is registered.
 *
 * DEMO SCOPE for Pass 2: see /docs/DECISIONS.md
 * "File Fetch — semantic fallback candidate scope: 'last 10 photos + Downloads folder' is demo-only"
 */

package com.google.ai.edge.gallery.filefetch

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private const val TAG = "TraceFileFetch"

/**
 * Default implementation of [IntentFileFetchHandler].
 *
 * Attempts a direct MediaStore filename match first. If that returns nothing,
 * falls back to [SemanticFileMatcher] for Gemma vision classification
 * over a small demo-scoped candidate set.
 *
 * @param context Application or activity context for MediaStore access.
 */
class DefaultIntentFileFetchHandler(private val context: Context) : IntentFileFetchHandler {

  private val fetcher = FileFetcher(context)

  /**
   * Returns the best matching file for [query], or null if nothing found.
   *
   * Pass 1: Direct filename match (fast, synchronous).
   * Pass 2: Semantic vision classification fallback (slow, ~5-20s, DEMO SCOPE).
   */
  override fun handleFindFile(query: String): FileResult? {
    val cleaned = cleanQuery(query)
    Log.d(TAG, "handleFindFile: raw=\"$query\" cleaned=\"$cleaned\"")

    // --- Pass 1: direct filename match ---
    val directResult = fetcher.findFile(cleaned)
    if (directResult != null) {
      Log.d(TAG, "handleFindFile: Pass 1 hit → \"${directResult.displayName}\" at ${directResult.uri}")
      return directResult
    }
    Log.d(TAG, "handleFindFile: Pass 1 miss for \"$cleaned\" — trying semantic fallback")

    // --- Pass 2: semantic fallback (DEMO SCOPE) ---
    // runBlocking is acceptable here because handleFindFile is already called from a background
    // thread (Dispatchers.IO) via the UI's file fetch handler. Do NOT call this from the main thread.
    return runBlocking {
      withContext(Dispatchers.IO) {
        val semanticResult = SemanticFileMatcher.findSemantically(context, query)
        if (semanticResult != null) {
          Log.d(TAG, "handleFindFile: Pass 2 hit → \"${semanticResult.displayName}\"")
        } else {
          Log.w(TAG, "handleFindFile: Pass 2 miss — no match found for \"$query\"")
        }
        semanticResult
      }
    }
  }

  /**
   * Returns up to [limit] files matching [query].
   * Only uses direct filename match — semantic fallback not applied here (cost vs. benefit).
   */
  override fun handleFindFiles(query: String, limit: Int): List<FileResult> {
    val cleaned = cleanQuery(query)
    Log.d(TAG, "handleFindFiles: raw=\"$query\" cleaned=\"$cleaned\" limit=$limit")
    return fetcher.findFiles(cleaned, limit = limit)
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Strips common voice-command filler words so that MediaStore substring
   * matching works against actual filenames.
   *
   * Examples:
   *   "open my lecture notes"       → "lecture notes"
   *   "find the chemistry homework"  → "chemistry homework"
   *   "show me biology lab report"   → "biology lab report"
   */
  private fun cleanQuery(raw: String): String {
    val fillers =
      setOf(
        "open", "find", "show", "get", "fetch", "look", "search",
        "my", "me", "the", "a", "an", "for", "please", "can", "you",
        "i", "need", "want", "file", "document", "doc",
      )
    return raw
      .lowercase()
      .split(" ")
      .filter { it.isNotBlank() && it !in fillers }
      .joinToString(" ")
      .trim()
      .ifEmpty { raw.lowercase().trim() } // fallback: if all words were fillers, use raw
  }
}
