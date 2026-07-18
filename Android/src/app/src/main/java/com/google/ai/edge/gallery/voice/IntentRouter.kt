package com.google.ai.edge.gallery.voice

import android.util.Log

private const val TAG = "TraceIntentRouter"

enum class IntentType {
    LLM_CHAT,
    FILE_FETCH,
    SCREEN_EXPLAIN
}

data class IntentResult(
    val type: IntentType,
    val query: String,
    val extractedFileName: String? = null
)

class IntentRouter(private val context: android.content.Context) {

    fun routeIntent(inputText: String): IntentResult {
        val lowerText = inputText.lowercase().trim()
        Log.d(TAG, "routeIntent: input=\"$inputText\"")

        // -----------------------------------------------------------------------
        // SCREEN_EXPLAIN — checked first, most specific phrases
        // -----------------------------------------------------------------------
        val screenPhrases = listOf(
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
        if (screenPhrases.any { lowerText.startsWith(it) }) {
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

        // Strong verbs: these are unusual in general conversation without file intent
        val strongVerbRegex = Regex(
            "(?:fetch|pull\\s+up|bring\\s+up|locate|find\\s+(?:my|the|a)|open\\s+(?:my|the))" +
            "\\s+(?:(?:my|the|a|an)\\s+)?(?:file\\s+|document\\s+|photo\\s+|image\\s+|pdf\\s+|picture\\s+)?(.+)",
            RegexOption.IGNORE_CASE
        )

        // Weak verbs: common words that only become file-fetch signals when combined
        // with an explicit file-type qualifier in the target phrase
        val weakVerbRegex = Regex(
            "(?:show|get|open|read|access|find|look\\s+for)\\s+" +
            "(?:(?:my|the|a|an)\\s+)?" +
            "(?:file\\s+|document\\s+|photo\\s+|image\\s+|pdf\\s+|picture\\s+|screenshot\\s+|scan\\s+|receipt\\s+|id\\s+|card\\s+|license\\s+|certificate\\s+|form\\s+|report\\s+)" +
            "(.+)?",
            RegexOption.IGNORE_CASE
        )

        // Noise words that should NOT be treated as valid file targets
        val noiseTargets = setOf(
            "me", "it", "that", "this", "up", "out", "here", "there",
            "him", "her", "them", "something", "anything", "everything"
        )

        val strongMatch = strongVerbRegex.find(lowerText)
        val weakMatch = weakVerbRegex.find(lowerText)

        val matchResult = strongMatch ?: weakMatch
        if (matchResult != null) {
            val rawTarget = matchResult.groupValues[1].trim().trimEnd('.', '!', '?')
            val isValidTarget = rawTarget.length >= 3 && rawTarget.lowercase() !in noiseTargets

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
