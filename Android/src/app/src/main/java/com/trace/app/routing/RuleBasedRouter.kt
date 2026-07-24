package com.trace.app.routing

enum class RouterAction {
    SAVE_MEMORY,
    SEARCH_MEMORY,
    LIST_MEMORY,
    CREATE_REMINDER,
    LIST_REMINDERS,
    DELETE_REMINDER,
    UPDATE_REMINDER,
    FIND_FILE,
    LIST_FILES,
    SEARCH_WEB,
    EXPLAIN_SCREEN,
    DEVICE_CONTROL,
    NONE
}

data class RouterCall(
    val action: RouterAction,
    val arguments: Map<String, String>
)

object RuleBasedRouter {
    fun parse(input: String): RouterCall? {
        val lowerInput = input.trim().lowercase()

        // 1. Web Search
        if (lowerInput.startsWith("search the web for ") || lowerInput.startsWith("web search ")) {
            val query = lowerInput.removePrefix("search the web for ").removePrefix("web search ").trim()
            return RouterCall(RouterAction.SEARCH_WEB, mapOf("query" to query))
        }

        // 2. Fetch/Find File
        if (lowerInput.startsWith("fetch file ") || lowerInput.startsWith("find file ")) {
            val query = lowerInput.removePrefix("fetch file ").removePrefix("find file ").trim()
            return RouterCall(RouterAction.FIND_FILE, mapOf("query" to query))
        }
        if (lowerInput.startsWith("fetch ") && !lowerInput.contains("reminder") && !lowerInput.contains("memory")) {
            val query = lowerInput.removePrefix("fetch ").trim()
            return RouterCall(RouterAction.FIND_FILE, mapOf("query" to query))
        }

        // 3. Reminders
        if (lowerInput.startsWith("remind me to ")) {
            return RouterCall(RouterAction.CREATE_REMINDER, mapOf("message" to input.trim()))
        }
        if (lowerInput == "what are my reminders" || lowerInput == "list reminders" || lowerInput == "show my reminders") {
            return RouterCall(RouterAction.LIST_REMINDERS, emptyMap())
        }

        // 4. Memories
        if (lowerInput.startsWith("remember that ") || lowerInput.startsWith("save memory ")) {
            val content = input.trim().substring(input.trim().lowercase().indexOf(if (lowerInput.startsWith("remember that ")) "that " else "memory ") + (if (lowerInput.startsWith("remember that ")) 5 else 7)).trim()
            return RouterCall(RouterAction.SAVE_MEMORY, mapOf("content" to content))
        }
        if (lowerInput.startsWith("what did i say about ") || lowerInput.startsWith("search memory for ")) {
            val query = lowerInput.removePrefix("what did i say about ").removePrefix("search memory for ").trim()
            return RouterCall(RouterAction.SEARCH_MEMORY, mapOf("query" to query))
        }
        
        // 5. Screen Explain
        if (lowerInput == "explain screen" || lowerInput == "what is on my screen" || lowerInput == "what's on my screen") {
            return RouterCall(RouterAction.EXPLAIN_SCREEN, mapOf("query" to "Describe this screen"))
        }

        return null
    }
}
