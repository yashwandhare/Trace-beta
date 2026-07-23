/*
 * Trace — OcrHelper (Phase 2, Dev B)
 *
 * Wraps ML Kit TextRecognition for on-device Latin text extraction.
 * Offline — no network calls. Model is bundled with ML Kit.
 *
 * Used by: DocumentExtractor, ScreenCaptureService, and VisionChatViewModel.
 *
 * Permissions: none required — reads bitmaps passed in-memory.
 */

package com.trace.app.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "TraceOcr"

// ---------------------------------------------------------------------------
// Result type
// ---------------------------------------------------------------------------

/**
 * Result of a single OCR pass on one image.
 *
 * @param rawText       Full extracted text, newlines preserved.
 * @param lines         Individual text lines detected (trimmed, non-empty).
 * @param wordCount     Number of whitespace-separated tokens in [rawText].
 * @param charCount     Number of non-whitespace characters in [rawText].
 * @param elapsedMs     Wall-clock time for the recognition call in milliseconds.
 * @param confidence    Average block confidence [0.0 – 1.0] if available, else null.
 */
data class OcrResult(
  val rawText: String,
  val lines: List<String>,
  val wordCount: Int,
  val charCount: Int,
  val elapsedMs: Long,
  val confidence: Float?,
)

// ---------------------------------------------------------------------------
// OcrHelper
// ---------------------------------------------------------------------------

/**
 * Runs on-device OCR using ML Kit Text Recognition (Latin script).
 *
 * ### Usage
 * ```kotlin
 * val helper = OcrHelper()
 * val result = helper.recognizeText(bitmap)
 * Log.d("Trace", result.rawText)
 * helper.close()
 * ```
 *
 * Call [close] when done to release the ML Kit recognizer resources.
 */
class OcrHelper : AutoCloseable {

  private val recognizer =
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

  /**
   * Runs text recognition on [bitmap] and suspends until the result is ready.
   *
   * This is a coroutine-safe wrapper — safe to call from a ViewModel or
   * background coroutine scope.
   *
   * @param bitmap  The image to run OCR on. Any size; ML Kit handles scaling.
   * @return        [OcrResult] with extracted text and quality metrics.
   * @throws        Exception if ML Kit fails (e.g. out-of-memory).
   */
  suspend fun recognizeText(bitmap: Bitmap): OcrResult =
    suspendCancellableCoroutine { cont ->
      val image = InputImage.fromBitmap(bitmap, 0)
      val startMs = System.currentTimeMillis()

      recognizer
        .process(image)
        .addOnSuccessListener { visionText ->
          val elapsedMs = System.currentTimeMillis() - startMs
          val rawText = visionText.text

          val lines =
            visionText.textBlocks
              .flatMap { block -> block.lines }
              .map { it.text.trim() }
              .filter { it.isNotEmpty() }

          val wordCount = rawText.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
          val charCount = rawText.count { !it.isWhitespace() }

          // Confidence: average over blocks that have a valid confidence value.
          val confidences =
            visionText.textBlocks.mapNotNull { block ->
              block.lines.flatMap { it.elements }.mapNotNull { it.confidence }.takeIf { it.isNotEmpty() }
            }.flatten()
          val avgConfidence = if (confidences.isNotEmpty()) confidences.average().toFloat() else null

          Log.d(TAG, "OCR done in ${elapsedMs}ms | words=$wordCount chars=$charCount conf=$avgConfidence")
          cont.resume(
            OcrResult(
              rawText = rawText,
              lines = lines,
              wordCount = wordCount,
              charCount = charCount,
              elapsedMs = elapsedMs,
              confidence = avgConfidence,
            )
          )
        }
        .addOnFailureListener { e ->
          Log.e(TAG, "OCR failed", e)
          cont.resumeWithException(e)
        }

      cont.invokeOnCancellation { /* ML Kit tasks are not cancellable; result is discarded */ }
    }

  /** Releases the ML Kit recognizer. Call when the helper is no longer needed. */
  override fun close() {
    recognizer.close()
  }
}
