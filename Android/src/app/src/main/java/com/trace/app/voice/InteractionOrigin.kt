package com.trace.app.voice

/**
 * Tracks whether a prompt was sent via voice (mic) or text (keyboard).
 *
 * Used by [LlmChatViewModelBase.generateResponse] to decide whether TTS
 * should read the response aloud — only voice-originated prompts trigger TTS.
 */
enum class InteractionOrigin {
    VOICE,
    TEXT,
}
