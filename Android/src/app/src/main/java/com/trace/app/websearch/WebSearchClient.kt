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
package com.trace.app.websearch

import android.util.Log
import java.net.HttpURLConnection
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A single web result: title, snippet, and source URL.
 */
data class WebSearchResult(val title: String, val snippet: String, val url: String)

/**
 * Minimal DuckDuckGo search client backing Trace's OPT-IN web search.
 *
 * IMPORTANT: this is the one part of Trace that makes a network call. It must
 * only ever run when the user has explicitly enabled web search (see
 * `Settings.web_search_enabled` and /docs/CONSTRAINTS.md). It never runs on the
 * offline core paths.
 *
 * Uses the no-JS "lite" HTML endpoint (no API key). Every failure — offline,
 * timeout, markup change — degrades to an empty list so callers can silently
 * fall back to answering from the model alone.
 */
object WebSearchClient {
  private const val TAG = "WebSearchClient"
  private const val ENDPOINT = "https://lite.duckduckgo.com/lite/"
  private const val TIMEOUT_MS = 8_000

  /**
   * Result-row anchors on the lite page. DDG uses SINGLE quotes and puts href
   * BEFORE class (e.g. `<a rel="nofollow" href='URL' class='result-link'>TITLE</a>`),
   * so we match the whole opening tag by its result-link class regardless of
   * attribute order or quote style, then pull href out of the captured attrs.
   */
  private val RESULT_LINK = Regex(
    "<a\\b([^>]*\\bclass=['\"][^'\"]*result-link[^'\"]*['\"][^>]*)>(.*?)</a>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
  )
  /** Pulls the href value out of an anchor's attribute string (either quote style). */
  private val HREF_ATTR = Regex("\\bhref=['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
  /** Snippet cells: <td class='result-snippet'>SNIPPET</td> (single or double quotes). */
  private val RESULT_SNIPPET = Regex(
    "<td[^>]*\\bclass=['\"][^'\"]*result-snippet[^'\"]*['\"][^>]*>(.*?)</td>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
  )
  private val TAG_STRIP = Regex("<[^>]+>")

  /**
   * Runs a search and returns up to [limit] results, or an empty list on any
   * failure (offline included). Safe to call from any dispatcher — network work
   * is moved to IO and bounded by a timeout.
   */
  suspend fun search(query: String, limit: Int = 5): List<WebSearchResult> {
    if (query.isBlank()) return emptyList()
    return withTimeoutOrNull(TIMEOUT_MS.toLong()) {
      withContext(Dispatchers.IO) {
        try {
          fetchAndParse(query, limit)
        } catch (e: Exception) {
          Log.w(TAG, "web search failed for \"$query\": ${e.message}")
          emptyList()
        }
      }
    } ?: emptyList()
  }

  /**
   * Formats [results] into a compact block for prompt injection. Empty string
   * when there are no results, so the caller can decide to skip grounding.
   */
  fun formatForPrompt(results: List<WebSearchResult>): String {
    if (results.isEmpty()) return ""
    return buildString {
      append("Web search results:\n")
      results.forEachIndexed { i, r ->
        append("${i + 1}. ${r.title}\n")
        if (r.snippet.isNotBlank()) append("   ${r.snippet}\n")
        if (r.url.isNotBlank()) append("   (${r.url})\n")
      }
    }
  }

  private fun fetchAndParse(query: String, limit: Int): List<WebSearchResult> {
    val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      connectTimeout = TIMEOUT_MS
      readTimeout = TIMEOUT_MS
      doOutput = true
      // A UA + form POST matches how the lite page expects queries; without a UA
      // DDG may serve a challenge page instead of results.
      setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Trace/1.0")
      setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    }
    try {
      conn.outputStream.use { it.write("q=${URLEncoder.encode(query, "UTF-8")}".toByteArray()) }
      if (conn.responseCode != HttpURLConnection.HTTP_OK) {
        Log.w(TAG, "web search HTTP ${conn.responseCode}")
        return emptyList()
      }
      val html = conn.inputStream.bufferedReader().use { it.readText() }
      return parse(html, limit)
    } finally {
      conn.disconnect()
    }
  }

  private fun parse(html: String, limit: Int): List<WebSearchResult> {
    val links = RESULT_LINK.findAll(html).toList()
    val snippets = RESULT_SNIPPET.findAll(html).map { clean(it.groupValues[1]) }.toList()
    return links.take(limit).mapIndexed { i, m ->
      val attrs = m.groupValues[1]
      val href = HREF_ATTR.find(attrs)?.groupValues?.get(1) ?: ""
      WebSearchResult(
        title = clean(m.groupValues[2]),
        snippet = snippets.getOrElse(i) { "" },
        // Modern lite results carry a direct href; decodeDdgUrl is a no-op unless
        // a legacy uddg= redirect wrapper is present, so it's safe either way.
        url = decodeDdgUrl(href),
      )
    }.filter { it.title.isNotBlank() }
  }

  /** Strips HTML tags + collapses whitespace from a fragment. */
  private fun clean(s: String): String =
    TAG_STRIP.replace(s, "").replace("&amp;", "&").replace("&#x27;", "'")
      .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
      .replace(Regex("\\s+"), " ").trim()

  /** DDG lite wraps outbound links as //duckduckgo.com/l/?uddg=<encoded>. Unwrap it. */
  private fun decodeDdgUrl(href: String): String {
    val marker = "uddg="
    val idx = href.indexOf(marker)
    if (idx < 0) return href
    val enc = href.substring(idx + marker.length).substringBefore('&')
    return try {
      URLDecoder.decode(enc, "UTF-8")
    } catch (e: Exception) {
      href
    }
  }
}
