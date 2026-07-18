package com.google.ai.edge.gallery.ui.vision

import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.SystemPromptRepository
import com.google.ai.edge.gallery.proto.UserData
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageImage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.llmchat.LlmChatViewModelBase
import com.google.ai.edge.gallery.voice.InteractionOrigin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.ui.graphics.asImageBitmap

@HiltViewModel
class VisionChatViewModel @Inject constructor(
    systemPromptRepository: SystemPromptRepository,
    userDataDataStore: DataStore<UserData>
) : LlmChatViewModelBase(systemPromptRepository, userDataDataStore) {

    /**
     * Processes a single camera frame (bitmap) and an optional voice/text input,
     * adding them to the chat history and triggering generation.
     */
    fun processCameraFrame(
        model: Model,
        bitmap: Bitmap,
        input: String,
        isVoice: Boolean = true,
        onFirstToken: (Model) -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Add image to chat history
        addMessage(
            model,
            ChatMessageImage(
                bitmaps = listOf(bitmap),
                imageBitMaps = listOf(bitmap.asImageBitmap()),
                side = ChatSide.USER
            )
        )

        val textInput = input.ifBlank { "What do you see?" }

        // Add text to chat history
        addMessage(
            model,
            ChatMessageText(
                content = textInput,
                side = ChatSide.USER,
                data = if (isVoice) InteractionOrigin.VOICE else InteractionOrigin.TEXT
            )
        )

        generateResponse(
            model = model,
            input = textInput,
            images = listOf(bitmap),
            onFirstToken = onFirstToken,
            onDone = onDone,
            onError = onError,
            interactionOrigin = if (isVoice) InteractionOrigin.VOICE else InteractionOrigin.TEXT,
            allowThinking = false // Generally false for quick scan-and-chat
        )
    }
}
