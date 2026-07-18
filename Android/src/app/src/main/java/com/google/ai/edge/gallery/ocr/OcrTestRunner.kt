/*
 * Trace — OcrTestRunner (Phase 2, Dev B)
 *
 * Quality validation harness for the OcrHelper.
 * Runs OCR on a set of sample bitmaps and produces a report that Dev A
 * can use to decide if ML Kit quality is sufficient for the Screen Explain
 * pipeline.
 *
 * This is a dev/validation tool — NOT part of the production user flow.
 * Trigger it from a ViewModel or a debug menu.
 */

package com.google.ai.edge.gallery.ocr

import android.graphics.Bitmap
import android.util.Log

private const val TAG = "TraceOcrTest"

// ---------------------------------------------------------------------------
// Test case / report types
// ---------------------------------------------------------------------------

/**
 * A single OCR test case: a bitmap and the ground-truth text expected to be found in it.
 *
 * @param label        Human-readable name (e.g. "Handwritten lecture notes").
 * @param bitmap       The image to run OCR on.
 * @param expectedText Key phrases that should appear in the OCR output
 *                     (used to compute a simple hit-rate score).
 */
data class OcrTestCase(
  val label: String,
  val bitmap: Bitmap,
  val expectedText: List<String> = emptyList(),
)

/**
 * Result for one [OcrTestCase].
 */
data class OcrTestResult(
  val label: String,
  val ocrResult: OcrResult,
  /** Fraction of [OcrTestCase.expectedText] phrases found in the OCR output [0.0–1.0]. */
  val hitRate: Float,
  /** True if all expected phrases were found. */
  val passed: Boolean,
)

/**
 * Aggregated report across all test cases.
 */
data class OcrTestReport(
  val results: List<OcrTestResult>,
  val averageElapsedMs: Long,
  val averageWordCount: Float,
  val averageHitRate: Float,
  val passCount: Int,
  val totalCount: Int,
) {
  val passRate: Float get() = if (totalCount > 0) passCount.toFloat() / totalCount else 0f

  fun summary(): String = buildString {
    appendLine("=== Trace OCR Quality Report ===")
    appendLine("Total cases : $totalCount")
    appendLine("Passed      : $passCount / $totalCount  (${(passRate * 100).toInt()}%)")
    appendLine("Avg hit rate: ${(averageHitRate * 100).toInt()}%")
    appendLine("Avg words   : ${"%.1f".format(averageWordCount)}")
    appendLine("Avg latency : ${averageElapsedMs}ms")
    appendLine()
    results.forEach { r ->
      val status = if (r.passed) "✓ PASS" else "✗ FAIL"
      appendLine("$status  [${r.label}]")
      appendLine("  words=${r.ocrResult.wordCount}  chars=${r.ocrResult.charCount}  " +
        "conf=${r.ocrResult.confidence?.let { "%.2f".format(it) } ?: "n/a"}  " +
        "time=${r.ocrResult.elapsedMs}ms  hit=${(r.hitRate * 100).toInt()}%")
      if (r.ocrResult.lines.isNotEmpty()) {
        appendLine("  First line: \"${r.ocrResult.lines.first().take(80)}\"")
      }
    }
  }
}

// ---------------------------------------------------------------------------
// OcrTestRunner
// ---------------------------------------------------------------------------

/**
 * Runs a batch of [OcrTestCase]s through [OcrHelper] and returns an [OcrTestReport].
 *
 * ### Usage
 * ```kotlin
 * // In a ViewModel or coroutine scope:
 * val runner = OcrTestRunner()
 * val cases = listOf(
 *   OcrTestCase(
 *     label   = "Printed syllabus",
 *     bitmap  = BitmapFactory.decodeResource(resources, R.drawable.test_syllabus),
 *     expectedText = listOf("Week 1", "Assignment", "Due date"),
 *   )
 * )
 * val report = runner.run(cases)
 * Log.d("TraceOcr", report.summary())
 * runner.close()
 * ```
 *
 * Call [close] when done to release resources.
 */
class OcrTestRunner : AutoCloseable {

  private val helper = OcrHelper()

  /**
   * Runs OCR on each [OcrTestCase] in [cases] sequentially and returns a report.
   *
   * Must be called from a coroutine (suspend function).
   */
  suspend fun run(cases: List<OcrTestCase>): OcrTestReport {
    if (cases.isEmpty()) {
      Log.w(TAG, "run() called with empty test case list")
      return OcrTestReport(
        results = emptyList(),
        averageElapsedMs = 0,
        averageWordCount = 0f,
        averageHitRate = 0f,
        passCount = 0,
        totalCount = 0,
      )
    }

    val results = mutableListOf<OcrTestResult>()

    for (case in cases) {
      Log.d(TAG, "Running OCR on: ${case.label}")
      val ocrResult = try {
        helper.recognizeText(case.bitmap)
      } catch (e: Exception) {
        Log.e(TAG, "OCR failed for case \"${case.label}\"", e)
        // Treat failure as empty result so the rest of the cases can still run.
        OcrResult(
          rawText = "",
          lines = emptyList(),
          wordCount = 0,
          charCount = 0,
          elapsedMs = 0,
          confidence = null,
        )
      }

      val hitRate = computeHitRate(ocrResult.rawText, case.expectedText)
      val passed = case.expectedText.isEmpty() || hitRate == 1.0f

      results +=
        OcrTestResult(
          label = case.label,
          ocrResult = ocrResult,
          hitRate = hitRate,
          passed = passed,
        )
    }

    val report =
      OcrTestReport(
        results = results,
        averageElapsedMs = results.map { it.ocrResult.elapsedMs }.average().toLong(),
        averageWordCount = results.map { it.ocrResult.wordCount }.average().toFloat(),
        averageHitRate = results.map { it.hitRate }.average().toFloat(),
        passCount = results.count { it.passed },
        totalCount = results.size,
      )

    Log.i(TAG, report.summary())
    return report
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Fraction of [expectedPhrases] found (case-insensitive substring match) in [ocrText].
   */
  private fun computeHitRate(ocrText: String, expectedPhrases: List<String>): Float {
    if (expectedPhrases.isEmpty()) return 1.0f
    val lower = ocrText.lowercase()
    val hits = expectedPhrases.count { phrase -> lower.contains(phrase.lowercase()) }
    return hits.toFloat() / expectedPhrases.size
  }

  override fun close() {
    helper.close()
  }
}
