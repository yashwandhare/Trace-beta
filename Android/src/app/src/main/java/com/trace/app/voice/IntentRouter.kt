package com.trace.app.voice

import android.util.Log

private const val TAG = "TraceIntentRouter"

enum class IntentType {
    LLM_CHAT,
    FILE_FETCH,
    SCREEN_EXPLAIN,
}

data class IntentResult(
    val type: IntentType,
    val query: String,
    val extractedFileName: String? = null,
)

/**
 * Rule-based intent router.
 *
 * Requires a [context] for future semantic lookups (currently unused but
 * kept to match the wiring established by origin/main).
 *
 * Classification order:
 * 1. WEB_SEARCH keyword — "websearch"/"web search" always goes to LLM_CHAT
 * 2. SCREEN_EXPLAIN — most specific; any recognisable screen-describe phrase
 * 3. FILE_FETCH     — requires TWO independent signals (verb + valid target)
 * 4. LLM_CHAT       — default fallback
 */
class IntentRouter(private val context: android.content.Context) {

    companion object {
        // Compiled once and reused across every routeIntent call — these are
        // pure constants, so rebuilding them per send was wasted allocation on
        // the input hot path.
        private val SCREEN_PHRASES = listOf(
            "explain screen",
            "explain my screen",
            "what is on my screen",
            "what's on my screen",
            "whats on my screen",
            "read screen",
            "read my screen",
            "describe my screen",
            "describe the screen",
            "what do you see on screen",
            "screen",
        )

        // Strong verbs: unusual in general conversation without file intent.
        // "fetch", "search", and "pull" are treated as first-class fetch verbs
        // (bare "search test" / "pull test" work like "fetch test"). \b anchors
        // each verb to a real word start — without it, Regex.find matches verbs
        // as bare substrings anywhere (e.g. "search" inside "websearch" or
        // "research"), silently misrouting unrelated requests to FILE_FETCH.
        private val STRONG_VERB_REGEX = Regex(
            "\\b(?:fetch|search(?:\\s+for)?|pull(?:\\s+up)?|bring\\s+up|locate|find\\s+(?:my|the|a)|open\\s+(?:my|the))" +
            "\\s+(?:(?:my|the|a|an)\\s+)?(?:file\\s+|document\\s+|photo\\s+|image\\s+|pdf\\s+|picture\\s+)?(.+)",
            RegexOption.IGNORE_CASE
        )

        // Weak verbs: only a file-fetch signal when combined with an explicit file-type qualifier
        private val WEAK_VERB_REGEX = Regex(
            "\\b(?:show|get|open|read|access|find|look\\s+for)\\s+" +
            "(?:(?:my|the|a|an)\\s+)?" +
            "(?:file\\s+|document\\s+|photo\\s+|image\\s+|pdf\\s+|picture\\s+|screenshot\\s+|scan\\s+|receipt\\s+|id\\s+|card\\s+|license\\s+|certificate\\s+|form\\s+|report\\s+|resume\\s+|cv\\s+|invoice\\s+|ticket\\s+|bill\\s+|letter\\s+)" +
            "(.+)?",
            RegexOption.IGNORE_CASE
        )

        // Noise words that should NOT be treated as valid file targets
        private val NOISE_TARGETS = setOf(
            "me", "it", "that", "this", "up", "out", "here", "there",
            "him", "her", "them", "something", "anything", "everything"
        )
    }

    fun routeIntent(inputText: String): IntentResult {
        val lowerText = inputText.lowercase().trim()
        Log.d(TAG, "routeIntent: input=\"$inputText\"")

        // -----------------------------------------------------------------------
        // WEB_SEARCH keyword — always LLM_CHAT, checked first. The "search" fetch
        // verb has a real word boundary before it in "web search" (space between
        // words), so without this explicit bypass that phrase would still be
        // misrouted to FILE_FETCH even with \b anchoring below. The model-side
        // web-search handler (tryHandleWebSearch) does its own prefix stripping.
        // -----------------------------------------------------------------------
        if (lowerText.startsWith("websearch") || lowerText.startsWith("web search")) {
            Log.d(TAG, "routeIntent: classified=LLM_CHAT reason=websearch_keyword")
            return IntentResult(type = IntentType.LLM_CHAT, query = inputText)
        }

        // -----------------------------------------------------------------------
        // SCREEN_EXPLAIN — checked first, most specific phrases
        // -----------------------------------------------------------------------
        if (SCREEN_PHRASES.any { lowerText.startsWith(it) }) {
            Log.d(TAG, "routeIntent: classified=SCREEN_EXPLAIN action=requestCapture")
            return IntentResult(type = IntentType.SCREEN_EXPLAIN, query = inputText)
        }

        // -----------------------------------------------------------------------
        // FILE_FETCH — requires TWO independent signals to fire:
        //
        // Signal A: an unambiguous file-action verb that cannot appear in normal
        //           conversation without intent to open/retrieve something.
        //           Deliberately excludes bare "show/get/open/read" which are too
        //           common in general questions (e.g. "show me the weather").
        //
        // Signal B: the extracted target must pass a minimum-quality check — it
        //           must be at least 3 characters and must NOT be a known
        //           conversational noise phrase (e.g. "me", "it", "that", "the").
        //
        // Either signal alone is not sufficient; both must pass.
        // -----------------------------------------------------------------------
        val strongMatch = STRONG_VERB_REGEX.find(lowerText)
        val weakMatch   = WEAK_VERB_REGEX.find(lowerText)

        val matchResult = strongMatch ?: weakMatch
        if (matchResult != null) {
            val rawTarget = matchResult.groupValues[1].trim().trimEnd('.', '!', '?')
            val isValidTarget = rawTarget.length >= 3 && rawTarget.lowercase() !in NOISE_TARGETS

            if (isValidTarget) {
                Log.d(TAG, "routeIntent: classified=FILE_FETCH target=\"$rawTarget\" via=${if (strongMatch != null) "STRONG_VERB" else "WEAK_VERB+QUALIFIER"}")
                return IntentResult(
                    type = IntentType.FILE_FETCH,
                    query = inputText,
                    extractedFileName = rawTarget
                )
            } else {
                Log.d(TAG, "routeIntent: file-fetch regex matched but target \"$rawTarget\" failed quality check → falling through to LLM_CHAT")
            }
        }

        // -----------------------------------------------------------------------
        // LLM_CHAT — default
        // -----------------------------------------------------------------------
        Log.d(TAG, "routeIntent: classified=LLM_CHAT action=sendToModel")
        return IntentResult(type = IntentType.LLM_CHAT, query = inputText)
    }
}
