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
        val fileFetchRegex = Regex("(?i).*(?:find|fetch|open)\\s+(?:file|my|the)\\s+(.+)")
        val matchResult = fileFetchRegex.find(lowerText)
        
        if (matchResult != null) {
            val fileName = matchResult.groupValues[1].trim()
            val intentResult = IntentResult(
                type = IntentType.FILE_FETCH,
                query = inputText,
                extractedFileName = fileName
            )
            
            val handler = com.google.ai.edge.gallery.filefetch.DefaultIntentFileFetchHandler(context)
            val fileResult = handler.handleFindFile(intentResult.query)
            if (fileResult != null) {
                val launchIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(fileResult.uri, context.contentResolver.getType(fileResult.uri) ?: "*/*")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(launchIntent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "No app to open this file", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.widget.Toast.makeText(context, "File not found", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            return intentResult
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
