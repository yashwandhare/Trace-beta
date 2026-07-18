package com.google.ai.edge.gallery.voice

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
        
        // More lenient rule-based router for File Fetch
        val fileFetchRegex = Regex("(?i).*(?:find|fetch|open|get|look for|show|access|read)\\s+(?:file|my|the|a|an)\\s+(.+)")
        val matchResult = fileFetchRegex.find(lowerText)
        
        if (matchResult != null) {
            val fileName = matchResult.groupValues[1].trim()
            return IntentResult(
                type = IntentType.FILE_FETCH,
                query = inputText,
                extractedFileName = fileName
            )
        }
        
        if (lowerText.startsWith("explain screen") || 
            lowerText.startsWith("what is on my screen") || 
            lowerText.startsWith("what's on my screen") ||
            lowerText.startsWith("read screen")) {
            return IntentResult(
                type = IntentType.SCREEN_EXPLAIN,
                query = inputText
            )
        }
        
        // Otherwise, route to normal LLM chat
        return IntentResult(
            type = IntentType.LLM_CHAT,
            query = inputText
        )
    }
}
