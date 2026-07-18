package com.google.ai.edge.gallery.voice

enum class IntentType {
    LLM_CHAT,
    FILE_FETCH
}

data class IntentResult(
    val type: IntentType,
    val query: String,
    val extractedFileName: String? = null
)

class IntentRouter {
    fun routeIntent(inputText: String): IntentResult {
        val lowerText = inputText.lowercase().trim()
        
        // Very minimal rule-based router
        // If the user says "find file X", "fetch file X", "open file X"
        if (lowerText.startsWith("find file ") || 
            lowerText.startsWith("fetch file ") || 
            lowerText.startsWith("open file ")) {
            
            val fileName = lowerText.substringAfter("file ").trim()
            return IntentResult(
                type = IntentType.FILE_FETCH,
                query = inputText,
                extractedFileName = fileName
            )
        }
        
        // Otherwise, route to normal LLM chat
        return IntentResult(
            type = IntentType.LLM_CHAT,
            query = inputText
        )
    }
}
