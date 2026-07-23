/*
 * Trace — File Fetch Module (Phase 1, Dev B)
 *
 * Isolated, self-contained file lookup via Android MediaStore / SAF.
 * No dependency on the model, voice pipeline, or intent router.
 *
 * Dev A connects this to the intent router's direct-action path in Phase 2.
 *
 * Public surface:
 *   FileFetcher.findFile(query)  →  FileResult?
 *   FileFetcher.findFiles(query) →  List<FileResult>
 */

package com.trace.app.filefetch

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log

private const val TAG = "TraceFetch"

// ---------------------------------------------------------------------------
// Result type
// ---------------------------------------------------------------------------

/**
 * A file found on-device by [FileFetcher].
 *
 * @param uri         Content URI — pass to ContentResolver to open the file.
 * @param displayName Filename as shown to the user (e.g. "Lecture 3 notes.pdf").
 * @param mimeType    MIME type (e.g. "application/pdf", "image/jpeg"). May be null if unknown.
 * @param sizeBytes   File size in bytes. May be null if unavailable.
 */
data class FileResult(
  val uri: Uri,
  val displayName: String,
  val mimeType: String?,
  val sizeBytes: Long?,
)

// ---------------------------------------------------------------------------
// FileFetcher
// ---------------------------------------------------------------------------

/**
 * Queries Android's MediaStore to find files whose name contains [query].
 *
 * ### Usage
 * ```kotlin
 * val fetcher = FileFetcher(context)
 * val result  = fetcher.findFile("Lecture 3 notes")
 * result?.let { Log.d("Trace", "Found: ${it.displayName} at ${it.uri}") }
 * ```
 *
 * ### What it searches
 * - [MediaStore.Files.getContentUri] — all files (documents, PDFs, text, etc.)
 * - [MediaStore.Images.Media.EXTERNAL_CONTENT_URI] — images
 *
 * ### Permissions required
 * The caller is responsible for holding at minimum:
 * - `READ_EXTERNAL_STORAGE` (SDK ≤ 32), or
 * - `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` + `READ_MEDIA_AUDIO` (SDK ≥ 33)
 * before calling these functions. No permission requests happen here.
 */
class FileFetcher(private val context: Context) {

  /**
   * Returns the *first* file whose display name contains [query] (case-insensitive),
   * or null if nothing is found.
   *
   * @param query  A substring of the filename to search for (e.g. "lecture notes").
   */
  fun findFile(query: String): FileResult? = findFiles(query, limit = 1).firstOrNull()

  /**
   * Searches a user-granted SAF document tree (see [DocumentsContract]) for files
   * whose display name contains any query token. This is how we reach documents —
   * PDFs, docx, etc. — that the granular media permissions (READ_MEDIA_*) cannot
   * see. Returns the first match, or null if [treeUriString] is blank/invalid or
   * nothing matches. Recurses into subfolders.
   */
  fun findInDocumentTree(treeUriString: String, query: String, limit: Int = 1): List<FileResult> {
    if (treeUriString.isBlank()) return emptyList()
    val tokens = query.lowercase()
      .split(Regex("[^\\p{L}\\p{N}]+"))
      .filter { it.length >= 2 }
      .take(6)
    if (tokens.isEmpty()) return emptyList()

    val treeUri = try { Uri.parse(treeUriString) } catch (_: Exception) { return emptyList() }
    val results = mutableListOf<FileResult>()
    val rootDocId = try {
      android.provider.DocumentsContract.getTreeDocumentId(treeUri)
    } catch (_: Exception) {
      return emptyList()
    }
    searchTreeFolder(treeUri, rootDocId, tokens, limit, results)
    Log.d(TAG, "findInDocumentTree: \"$query\" → ${results.size} hit(s)")
    return results
  }

  private fun searchTreeFolder(
    treeUri: Uri,
    parentDocId: String,
    tokens: List<String>,
    limit: Int,
    results: MutableList<FileResult>,
  ) {
    if (results.size >= limit) return
    val childrenUri =
      android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    val projection = arrayOf(
      android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
      android.provider.DocumentsContract.Document.COLUMN_SIZE,
    )
    val subFolders = mutableListOf<String>()
    try {
      context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
        val idCol = c.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = c.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = c.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeCol = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_SIZE)
        while (c.moveToNext() && results.size < limit) {
          val docId = c.getString(idCol) ?: continue
          val name = c.getString(nameCol) ?: continue
          val mime = c.getString(mimeCol)
          if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
            subFolders += docId
            continue
          }
          val lower = name.lowercase()
          if (tokens.all { lower.contains(it) }) {
            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            results += FileResult(
              uri = docUri,
              displayName = name,
              mimeType = mime,
              sizeBytes = if (sizeCol >= 0) c.getLong(sizeCol).takeIf { it > 0 } else null,
            )
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "searchTreeFolder failed for docId=$parentDocId", e)
    }
    // Recurse into subfolders (bounded by the limit check at each entry).
    for (folderId in subFolders) {
      if (results.size >= limit) break
      searchTreeFolder(treeUri, folderId, tokens, limit, results)
    }
  }

  /**
   * Returns up to [limit] files whose display name contains [query] (case-insensitive).
   *
   * Searches general files first (documents, PDFs, text), then images, and de-duplicates
   * by URI before returning.
   *
   * @param query  A substring of the filename to search for.
   * @param limit  Max results to return. Defaults to 20.
   */
  fun findFiles(query: String, limit: Int = 20): List<FileResult> {
    val results = mutableListOf<FileResult>()
    val seen = mutableSetOf<String>()

    val tokens = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }.take(6)
    Log.d(TAG, "findFiles: raw=\"$query\" tokens=$tokens limit=$limit")

    if (tokens.isEmpty()) {
      Log.w(TAG, "findFiles: empty token set after cleaning \"$query\" — no query will be issued")
      return results
    }

    // 1. General files (documents, text, pdf, etc.)
    val beforeGeneral = results.size
    queryMediaStore(
      collectionUri = MediaStore.Files.getContentUri("external"),
      query = query,
      limit = limit,
      seen = seen,
      results = results,
    )
    Log.d(TAG, "findFiles: general-files collection → ${results.size - beforeGeneral} hit(s), cumulative=${results.size}")

    // 2. Images (catch files MediaStore.Files may miss on some devices)
    if (results.size < limit) {
      val beforeImages = results.size
      queryMediaStore(
        collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        query = query,
        limit = limit - results.size,
        seen = seen,
        results = results,
      )
      Log.d(TAG, "findFiles: images collection → ${results.size - beforeImages} hit(s), cumulative=${results.size}")
    }

    if (results.isEmpty()) {
      Log.w(TAG, "findFiles: zero results for tokens=$tokens — direct filename match failed; semantic fallback needed")
    } else {
      Log.d(TAG, "findFiles: returning ${results.size} result(s): ${results.take(3).map { it.displayName }}")
    }
    return results
  }

  // ---------------------------------------------------------------------------
  // Internal
  // ---------------------------------------------------------------------------

  private fun queryMediaStore(
    collectionUri: Uri,
    query: String,
    limit: Int,
    seen: MutableSet<String>,
    results: MutableList<FileResult>,
  ) {
    val projection =
      arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.SIZE,
      )

    val extensions = setOf("jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "txt", "mp3", "mp4", "wav")
    val queryTokens = query
      .lowercase()
      .split(Regex("[^\\p{L}\\p{N}]+"))
      .filter { it.length >= 2 && it !in extensions }
      .take(6)
    if (queryTokens.isEmpty()) return

    // Use LOWER() on the column so matching is always case-insensitive regardless of device locale
    val selection = queryTokens.joinToString(" AND ") {
      "LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ?"
    }
    val selectionArgs = queryTokens.map { "%$it%" }.toTypedArray()
    // NOTE: do NOT append "LIMIT n" to the sort order — modern Android (API 30+)
    // treats this argument as a raw ORDER BY and rejects an embedded LIMIT,
    // making the whole query throw/return nothing. The cursor loop below caps
    // the result count instead.
    val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

    var cursor: Cursor? = null
    try {
      cursor =
        context.contentResolver.query(
          collectionUri,
          projection,
          selection,
          selectionArgs,
          sortOrder,
        )

      cursor?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)

        while (c.moveToNext() && results.size < limit) {
          val id = c.getLong(idCol)
          val contentUri = Uri.withAppendedPath(collectionUri, id.toString())
          val uriStr = contentUri.toString()

          if (uriStr in seen) continue
          seen += uriStr

          val displayName = c.getString(nameCol) ?: continue
          val mimeType = if (mimeCol >= 0) c.getString(mimeCol) else null
          val sizeBytes = if (sizeCol >= 0) c.getLong(sizeCol).takeIf { it > 0 } else null

          results +=
            FileResult(
              uri = contentUri,
              displayName = displayName,
              mimeType = mimeType,
              sizeBytes = sizeBytes,
            )
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "MediaStore query failed for URI=$collectionUri query=\"$query\"", e)
    } finally {
      cursor?.close()
    }
  }
}

// ---------------------------------------------------------------------------
// SAF helper — resolve display name from an arbitrary Uri (e.g. picker result)
// ---------------------------------------------------------------------------

/**
 * Attempts to read the display name of a file from [uri] using [OpenableColumns].
 * Useful when the caller receives a Uri from a Storage Access Framework file picker.
 *
 * @return The display name, or null if it cannot be determined.
 */
fun resolveDisplayName(context: Context, uri: Uri): String? {
  return try {
    context.contentResolver
      .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor ->
        if (cursor.moveToFirst()) {
          cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        } else null
      }
  } catch (e: Exception) {
    Log.e(TAG, "resolveDisplayName failed for uri=$uri", e)
    null
  }
}
