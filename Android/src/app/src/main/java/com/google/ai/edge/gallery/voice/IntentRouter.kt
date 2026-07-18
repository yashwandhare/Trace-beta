package com.google.ai.edge.gallery.voice

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
 * Lightweight rule-based intent router.
 *
 * Classifies a user query as one of:
 * - [IntentType.FILE_FETCH]     — voice/text file-fetch command
 * - [IntentType.SCREEN_EXPLAIN] — ask about the current screen
 * - [IntentType.LLM_CHAT]       — everything else → send to Gemma
 *
 * All regex objects are compiled once at class-init time to avoid per-call overhead.
 */
class IntentRouter {

    // ---------------------------------------------------------------------------
    // File-fetch patterns
    // Matches: "find file X", "open my resume", "pull up my ID", "show me lecture notes", etc.
    // ---------------------------------------------------------------------------
    private val fileFetchPrefixes = listOf(
        Regex("""^(find|fetch|get|pull up|bring up|open|show|show me|look for|search for|locate)\s+(my\s+|the\s+|a\s+)?(?:file\s+)?(.+)""", RegexOption.IGNORE_CASE),
        Regex("""^(?:can you |please )?(find|fetch|get|open|show|pull up|bring up)\s+(?:my\s+|the\s+)?(.+)""", RegexOption.IGNORE_CASE),
    )

    // Keywords whose presence strongly signals a file fetch intent regardless of phrasing.
    private val fileFetchKeywords = setOf(
        "file", "document", "doc", "pdf", "photo", "image", "picture", "screenshot",
        "notes", "note", "resume", "cv", "id", "license", "card", "certificate",
        "aadhar", "aadhaar", "passport", "marksheet", "report",
    )

    // Prefixes that are clearly file-fetch openers even without "file" in the query.
    private val fileFetchOpeners = listOf(
        "find file ", "fetch file ", "open file ",
        "pull up ", "bring up ", "find my ", "open my ", "show my ",
        "fetch my ", "get my ", "search for ", "look for ", "locate ",
    )

    // ---------------------------------------------------------------------------
    // Screen-explain patterns
    // ---------------------------------------------------------------------------
    private val screenExplainKeywords = setOf(
        "screen", "what's on screen", "what is on screen", "what do you see",
        "explain this", "what is this", "describe this", "read this",
        "what does this say", "what does this mean",
    )

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    fun routeIntent(inputText: String): IntentResult {
        val lower = inputText.lowercase().trim()

        // 1. Screen explain check (fast keyword scan)
        if (screenExplainKeywords.any { lower.contains(it) }) {
            return IntentResult(type = IntentType.SCREEN_EXPLAIN, query = inputText)
        }

        // 2. File fetch — check openers first (cheapest path)
        for (opener in fileFetchOpeners) {
            if (lower.startsWith(opener)) {
                val extracted = extractFileTarget(lower, opener)
                return IntentResult(
                    type = IntentType.FILE_FETCH,
                    query = inputText,
                    extractedFileName = extracted,
                )
            }
        }

        // 3. File fetch — check for strong file keywords in short queries
        //    (e.g. "my notes", "chemistry pdf")
        val words = lower.split(Regex("\\s+"))
        if (words.size <= 6 && words.any { it in fileFetchKeywords }) {
            val cleaned = words.filter { it !in FILLER_WORDS }.joinToString(" ")
            if (cleaned.isNotBlank()) {
                return IntentResult(
                    type = IntentType.FILE_FETCH,
                    query = inputText,
                    extractedFileName = cleaned,
                )
            }
        }

        // 4. File fetch — regex patterns for more complex phrasings
        for (pattern in fileFetchPrefixes) {
            val match = pattern.find(lower) ?: continue
            val target = match.groupValues.last().trim()
            if (target.isNotBlank() && target.split(" ").any { it in fileFetchKeywords }) {
                return IntentResult(
                    type = IntentType.FILE_FETCH,
                    query = inputText,
                    extractedFileName = target,
                )
            }
        }

        // 5. Default — send to LLM
        return IntentResult(type = IntentType.LLM_CHAT, query = inputText)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun extractFileTarget(lower: String, matchedOpener: String): String {
        val after = lower.removePrefix(matchedOpener).trim()
        // Strip leading filler words ("the", "my", "a", "an")
        return after
            .split(" ")
            .dropWhile { it in setOf("the", "my", "a", "an", "me", "for") }
            .joinToString(" ")
            .trim()
    }

    companion object {
        private val FILLER_WORDS = setOf(
            "open", "find", "show", "get", "fetch", "look", "search", "pull", "up",
            "my", "me", "the", "a", "an", "for", "please", "can", "you",
            "i", "need", "want", "bring",
        )
    }
}
