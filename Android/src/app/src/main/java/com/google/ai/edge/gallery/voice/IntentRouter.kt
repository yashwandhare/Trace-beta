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

class IntentRouter(private val context: android.content.Context) {
    fun routeIntent(inputText: String): IntentResult {
        val lowerText = inputText.lowercase().trim()
        
        // Very minimal rule-based router
        // If the user says "find file X", "fetch file X", "open file X"
        if (lowerText.startsWith("find file ") || 
            lowerText.startsWith("fetch file ") || 
            lowerText.startsWith("open file ")) {
            
            val fileName = lowerText.substringAfter("file ").trim()
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
        
        // Otherwise, route to normal LLM chat
        return IntentResult(
            type = IntentType.LLM_CHAT,
            query = inputText
        )
    }
}
