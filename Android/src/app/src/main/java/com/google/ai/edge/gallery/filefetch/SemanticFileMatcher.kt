/*
 * Trace — SemanticFileMatcher
 *
 * DEMO-SCOPED IMPLEMENTATION — see /docs/DECISIONS.md
 * "File Fetch — semantic fallback candidate scope: 'last 10 photos + Downloads folder' is demo-only"
 *
 * When a direct filename match returns zero results, this class:
 *   1. Checks an in-memory session cache (query → FileResult) — avoids re-scanning for repeated queries.
 *   2. If not cached: collects a small candidate set (last 10 images + Downloads folder contents).
 *   3. Classifies each candidate via Gemma vision with a yes/no prompt.
 *   4. Returns the first confident match and stores it in the cache.
 *
 * This cache and candidate scope will be REPLACED by the Qdrant Edge vector index in Phase 3.
 * Do not extend this layer — keep it minimal.
 */

package com.google.ai.edge.gallery.filefetch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.ai.edge.gallery.data.Model
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "TraceSemanticMatcher"

/**
 * Callback type for running a single Gemma vision inference call.
 * The caller provides the image and prompt; the callback runs inference and returns the full
 * text response via the onResult callback.
 *
 * This decouples SemanticFileMatcher from the ViewModel layer.
 */
typealias GemmaClassifyFn = suspend (image: Bitmap, prompt: String) -> String

/**
 * Singleton that manages the per-session classification cache and the candidate collection logic.
 *
 * Wire-up: call [setClassifier] from LlmChatViewModel once the model is initialized.
 * The classifier is cleared when the ViewModel is destroyed.
 *
 * DEMO SCOPE (see DECISIONS.md): candidate set = last 10 photos + Downloads folder.
 */
object SemanticFileMatcher {

    // -------------------------------------------------------------------------
    // Session cache: query string → matched FileResult
    // Keyed by lowercase-trimmed query so "Driver's License" and "driver's license" share a cache hit.
    // Intentionally in-memory only — cleared on app restart. Phase 3 replaces this with a persistent index.
    // -------------------------------------------------------------------------
    private val cache = ConcurrentHashMap<String, FileResult>()

    @Volatile
    private var classifyFn: GemmaClassifyFn? = null

    /** Called by LlmChatViewModel after model init to wire in the inference function. */
    fun setClassifier(fn: GemmaClassifyFn) {
        classifyFn = fn
        Log.d(TAG, "setClassifier: classifier registered")
    }

    /** Called by LlmChatViewModel onCleared to prevent stale model references. */
    fun clearClassifier() {
        classifyFn = null
        Log.d(TAG, "clearClassifier: classifier removed")
    }

    /** Clears the cache — useful for testing or when the user wants a fresh scan. */
    fun clearCache() {
        cache.clear()
        Log.d(TAG, "clearCache: cache cleared")
    }

    /**
     * Attempts to find a file matching [query] semantically.
     *
     * Returns null if:
     * - No classifier is registered (model not initialized yet)
     * - No confident match found in the candidate set
     *
     * This is a suspend function — must be called from a coroutine (Dispatchers.IO recommended).
     */
    suspend fun findSemantically(context: Context, query: String): FileResult? {
        val cacheKey = query.lowercase().trim()

        // 1. Cache hit
        cache[cacheKey]?.let { cached ->
            Log.d(TAG, "findSemantically: cache hit for \"$query\" → ${cached.displayName}")
            return cached
        }

        val classify = classifyFn
        if (classify == null) {
            Log.w(TAG, "findSemantically: no classifier registered — model not initialized yet, skipping")
            return null
        }

        Log.d(TAG, "findSemantically: cache miss for \"$query\", collecting candidates...")

        // 2. Collect candidate set (demo scope)
        val candidates = withContext(Dispatchers.IO) {
            collectCandidates(context)
        }

        Log.d(TAG, "findSemantically: ${candidates.size} candidate(s) to classify")

        if (candidates.isEmpty()) {
            Log.w(TAG, "findSemantically: no candidates found — check storage permissions")
            return null
        }

        // 3. Classify each candidate
        val classificationPrompt = buildClassificationPrompt(query)

        for ((fileResult, bitmap) in candidates) {
            try {
                Log.d(TAG, "findSemantically: classifying ${fileResult.displayName}...")
                val response = classify(bitmap, classificationPrompt)
                val isMatch = response.trim().lowercase().let {
                    it.startsWith("yes") || it.contains("yes,") || it == "yes."
                }
                Log.d(TAG, "findSemantically: ${fileResult.displayName} → \"${response.take(60)}\" isMatch=$isMatch")
                bitmap.recycle()

                if (isMatch) {
                    // 4. Cache the match before returning
                    cache[cacheKey] = fileResult
                    Log.d(TAG, "findSemantically: matched \"$query\" → ${fileResult.displayName}, cached")
                    return fileResult
                }
            } catch (e: Exception) {
                Log.e(TAG, "findSemantically: classification failed for ${fileResult.displayName}", e)
                bitmap.recycle()
            }
        }

        Log.w(TAG, "findSemantically: no match found for \"$query\" in ${candidates.size} candidate(s)")
        return null
    }

    // -------------------------------------------------------------------------
    // Candidate collection — DEMO SCOPE
    // -------------------------------------------------------------------------

    /**
     * Collects the candidate set:
     *   - Last 10 images from MediaStore (most recently added first)
     *   - All image/document files in the Downloads folder
     *
     * Returns pairs of (FileResult, decoded Bitmap) for classification.
     * Only includes files that can be decoded into a Bitmap.
     *
     * DEMO SCOPE — see DECISIONS.md. This will be replaced by a Qdrant vector index in Phase 3.
     */
    private fun collectCandidates(context: Context): List<Pair<FileResult, Bitmap>> {
        val candidates = mutableListOf<Pair<FileResult, Bitmap>>()
        val seenUris = mutableSetOf<String>()

        // Lane A: last 10 images from MediaStore
        collectRecentImages(context, limit = 10, seen = seenUris, out = candidates)
        Log.d(TAG, "collectCandidates: ${candidates.size} from recent images")

        // Lane B: Downloads folder
        collectDownloadsFolder(context, seen = seenUris, out = candidates)
        Log.d(TAG, "collectCandidates: ${candidates.size} total after Downloads")

        return candidates
    }

    private fun collectRecentImages(
        context: Context,
        limit: Int,
        seen: MutableSet<String>,
        out: MutableList<Pair<FileResult, Bitmap>>,
    ) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $limit"
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    val uriStr = uri.toString()
                    if (uriStr in seen) continue

                    val bitmap = decodeBitmapFromUri(context, uri) ?: continue
                    seen += uriStr
                    out += FileResult(
                        uri = uri,
                        displayName = cursor.getString(nameCol) ?: "image_$id",
                        mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else null,
                        sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).takeIf { it > 0 } else null,
                    ) to bitmap
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "collectRecentImages: query failed", e)
        }
    }

    private fun collectDownloadsFolder(
        context: Context,
        seen: MutableSet<String>,
        out: MutableList<Pair<FileResult, Bitmap>>,
    ) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            Log.d(TAG, "collectDownloadsFolder: Downloads dir does not exist")
            return
        }

        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "bmp")

        downloadsDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val ext = file.extension.lowercase()
            if (ext !in imageExtensions) return@forEach

            val uri = Uri.fromFile(file)
            val uriStr = uri.toString()
            if (uriStr in seen) return@forEach

            val bitmap = decodeBitmapFromFile(file) ?: return@forEach
            seen += uriStr
            out += FileResult(
                uri = uri,
                displayName = file.name,
                mimeType = "image/$ext",
                sizeBytes = file.length().takeIf { it > 0 },
            ) to bitmap
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                // Decode at reduced size to save memory — 512px max dimension is sufficient for classification
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)

                val sampleSize = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / 512)
                val opts2 = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                context.contentResolver.openInputStream(uri)?.use { s2 ->
                    BitmapFactory.decodeStream(s2, null, opts2)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "decodeBitmapFromUri: failed for $uri", e)
            null
        }
    }

    private fun decodeBitmapFromFile(file: File): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            val sampleSize = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / 512)
            val opts2 = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, opts2)
        } catch (e: Exception) {
            Log.w(TAG, "decodeBitmapFromFile: failed for ${file.name}", e)
            null
        }
    }

    /**
     * Builds a tightly scoped yes/no classification prompt.
     * Structured to minimise Gemma hallucination — a descriptive question with explicit instructions
     * to answer only "yes" or "no" with no elaboration.
     */
    private fun buildClassificationPrompt(query: String): String {
        return "Look at this image carefully. " +
            "Is this image a \"$query\"? " +
            "Answer with only the single word \"yes\" or \"no\". " +
            "Do not explain. Do not add punctuation. Just \"yes\" or \"no\"."
    }
}
