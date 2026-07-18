/*
 * Trace — DefaultIntentFileFetchHandler (Phase 2, Dev B)
 *
 * Concrete implementation of IntentFileFetchHandler backed by FileFetcher.
 *
 * Dev A wires this into the intent router's direct-action path in Phase 2.
 * Dev B owns this file; Dev A owns the call site in the router.
 *
 * Integration by Dev A:
 *   val handler = DefaultIntentFileFetchHandler(context)
 *   val result  = handler.handleFindFile(intentQuery)   // from parsed voice intent
 *   result?.let { launchFileViewer(it.uri) }
 */

package com.google.ai.edge.gallery.filefetch

import android.content.Context
import android.util.Log

private const val TAG = "TraceFileFetch"

/**
 * Default implementation of [IntentFileFetchHandler].
 *
 * Delegates all lookups to [FileFetcher] (MediaStore).
 *
 * @param context Application or activity context for MediaStore access.
 */
class DefaultIntentFileFetchHandler(private val context: Context) : IntentFileFetchHandler {

  private val fetcher = FileFetcher(context)

  /**
   * Returns the best matching file for [query], or null if nothing found.
   *
   * Strips common filler words from voice queries before searching so that
   * "open my lecture notes" → searches "lecture notes".
   */
  override fun handleFindFile(query: String): FileResult? {
    val cleaned = cleanQuery(query)
    Log.d(TAG, "handleFindFile: raw=\"$query\" cleaned=\"$cleaned\"")
    val result = fetcher.findFile(cleaned)
    if (result == null) {
      Log.d(TAG, "handleFindFile: no match for \"$cleaned\"")
    } else {
      Log.d(TAG, "handleFindFile: found \"${result.displayName}\" at ${result.uri}")
    }
    return result
  }

  /**
   * Returns up to [limit] files matching [query].
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
