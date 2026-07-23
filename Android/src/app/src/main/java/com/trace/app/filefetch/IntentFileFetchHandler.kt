/*
 * Trace — FileFetcher usage contract for Dev A
 *
 * This file documents the expected interface that the intent router
 * should call in Phase 2. Do not modify this file — it is the spec.
 *
 * Integration by Dev A (Phase 2):
 *   1. In the intent router, detect a "find file" intent (e.g. "open my lecture notes").
 *   2. Extract the query string from the parsed intent.
 *   3. Call FileFetcher(context).findFile(query) → FileResult?
 *   4. Pass the FileResult.uri to the downstream handler (summarizer, viewer, etc.)
 *
 * The FileFetcher does not start any Activity, does not prompt the user,
 * and does not access the network. It only reads from MediaStore.
 */

package com.trace.app.filefetch

/**
 * Contract / interface for the intent router to call into.
 *
 * Dev A: implement [IntentFileFetchHandler] in the router module and
 * inject a [FileFetcher] there. Do not call FileFetcher from inside the
 * voice or model layers directly.
 */
interface IntentFileFetchHandler {
  /**
   * Find a single file best matching [query].
   * Returns null if no match found or permissions are not granted.
   */
  fun handleFindFile(query: String): FileResult?

  /**
   * Find multiple files matching [query], up to [limit].
   */
  fun handleFindFiles(query: String, limit: Int = 5): List<FileResult>
}
