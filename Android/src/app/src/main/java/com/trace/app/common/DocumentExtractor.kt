package com.trace.app.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.trace.app.ocr.OcrHelper
import java.io.InputStream
import java.util.zip.ZipInputStream

private const val TAG = "DocumentExtractor"

private const val MAX_CHARS = 12_000

// Hard ceiling on how many chars we ever pull into memory while reading a file.
// The whole document text is truncated to MAX_CHARS anyway; reading past this
// (via readText() on a huge or binary file) only risks OutOfMemoryError.
private const val READ_CAP_CHARS = 200_000

private const val MAX_PDF_PAGES = 20

class DocumentExtractor(private val context: Context) {

    suspend fun extract(uri: Uri): String? {
        val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
        val name = displayName(uri)?.lowercase() ?: ""
        Log.d(TAG, "extract: uri=$uri mimeType=$mimeType name=$name")

        return try {
            when {
                mimeType == "application/pdf" -> extractPdf(uri)
                mimeType == "text/html" || name.endsWith(".html") || name.endsWith(".htm") ->
                    extractHtml(uri)
                mimeType.startsWith("text/") -> extractPlainText(uri)
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    extractDocx(uri)
                mimeType == "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
                    extractPptx(uri)
                mimeType == "application/msword" -> extractPlainText(uri)
                // Many devices report .md / .txt with no or an octet-stream MIME.
                // The file picker may still surface them, so read as UTF-8 text.
                else -> extractPlainText(uri)
            }
        } catch (e: Throwable) {
            // Throwable, not Exception: a large or binary file can trigger
            // OutOfMemoryError (an Error) during read/decode — must not crash the app.
            Log.e(TAG, "extract failed for mimeType=$mimeType", e)
            null
        }
    }

    /** Best-effort display name from the content resolver, for extension sniffing. */
    private fun displayName(uri: Uri): String? =
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        }

    private suspend fun extractPdf(uri: Uri): String? {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: run {
                Log.e(TAG, "extractPdf: could not open file descriptor")
                return null
            }

        val sb = StringBuilder()
        var pageCount = 0
        var processed = 0

        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                pageCount = renderer.pageCount
                val pagesToProcess = minOf(pageCount, MAX_PDF_PAGES)
                Log.d(TAG, "PDF has $pageCount page(s), processing $pagesToProcess")

                val ocr = OcrHelper()
                try {
                    for (i in 0 until pagesToProcess) {
                        if (sb.length >= MAX_CHARS) break
                        renderer.openPage(i).use { page ->
                            val bitmap = renderPageToBitmap(page)
                            try {
                                val result = ocr.recognizeText(bitmap)
                                if (result.rawText.isNotBlank()) {
                                    if (sb.isNotEmpty()) sb.append("\n\n")
                                    sb.append("[Page ${i + 1}]\n")
                                    sb.append(result.rawText)
                                }
                                Log.d(TAG, "PDF page ${i + 1}: ${result.wordCount} words in ${result.elapsedMs}ms")
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        processed++
                    }
                } finally {
                    ocr.close()
                }
            }
        }

        val text = sb.toString()
        if (text.isBlank()) {
            Log.w(TAG, "PDF yielded no text after OCR ($pageCount pages)")
            return null
        }

        return buildTruncated(text, pageCount, processed)
    }

    private fun renderPageToBitmap(page: PdfRenderer.Page): Bitmap {
        val scale = 2f
        val width = (page.width * scale).toInt().coerceAtLeast(1)
        val height = (page.height * scale).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    private fun extractPlainText(uri: Uri): String? {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            readCapped(stream.bufferedReader(Charsets.UTF_8))
        } ?: return null

        if (text.isBlank()) return null
        return truncate(text)
    }

    /**
     * Reads an HTML file and strips it down to visible text. Without this, raw
     * markup (<div>, <span>, inline CSS/JS) would pollute embeddings and the
     * grounding context. Drops <script>/<style> bodies, converts block tags to
     * newlines, removes remaining tags, and unescapes common entities.
     */
    private fun extractHtml(uri: Uri): String? {
        val html = context.contentResolver.openInputStream(uri)?.use { stream ->
            readCapped(stream.bufferedReader(Charsets.UTF_8))
        } ?: return null

        val text = html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<(br|/p|/div|/li|/h[1-6]|/tr)[^>]*>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        if (text.isBlank()) return null
        return truncate(text)
    }

    private fun extractDocx(uri: Uri): String? {
        val stream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
        val sb = StringBuilder()

        ZipInputStream(stream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = readCapped(zip.bufferedReader(Charsets.UTF_8))
                    sb.append(stripXmlTags(xml))
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val text = sb.toString().trim()
        if (text.isBlank()) return null
        return truncate(text)
    }

    private fun extractPptx(uri: Uri): String? {
        val stream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
        val sb = StringBuilder()

        ZipInputStream(stream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                    val xml = readCapped(zip.bufferedReader(Charsets.UTF_8))
                    val text = stripXmlTags(xml).trim()
                    if (text.isNotBlank()) {
                        if (sb.isNotEmpty()) sb.append("\n\n")
                        sb.append(text)
                    }
                    if (sb.length >= MAX_CHARS) break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val text = sb.toString().trim()
        if (text.isBlank()) return null
        return truncate(text)
    }

    private fun stripXmlTags(xml: String): String {
        val withBreaks = xml
            .replace(Regex("<w:p[ >]"), "\n<w:p>")
            .replace(Regex("<a:p[ >]"), "\n<a:p>")
        return withBreaks
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun truncate(text: String): String =
        if (text.length > MAX_CHARS) text.take(MAX_CHARS) + "\n\n[document truncated at $MAX_CHARS characters]"
        else text

    /**
     * Reads at most [READ_CAP_CHARS] characters from [reader]. Prevents an
     * OutOfMemoryError from readText() pulling a huge (or binary-read-as-text)
     * file fully into memory — the caller truncates to MAX_CHARS afterward anyway.
     */
    private fun readCapped(reader: java.io.Reader): String {
        val buf = CharArray(8 * 1024)
        val sb = StringBuilder()
        while (sb.length < READ_CAP_CHARS) {
            val n = reader.read(buf)
            if (n < 0) break
            sb.append(buf, 0, minOf(n, READ_CAP_CHARS - sb.length))
        }
        return sb.toString()
    }

    private fun buildTruncated(text: String, totalPages: Int, processedPages: Int): String {
        val truncated = truncate(text)
        return if (processedPages < totalPages) {
            "$truncated\n\n[PDF truncated: showed $processedPages of $totalPages pages]"
        } else {
            truncated
        }
    }
}
