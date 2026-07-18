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
import com.google.ai.edge.gallery.ocr.OcrHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

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

        viewModelScope.launch {
            val ocrHelper = OcrHelper()
            var extractedText = ""
            try {
                val ocrResult = ocrHelper.recognizeText(bitmap)
                if (ocrResult.rawText.isNotBlank()) {
                    extractedText = "\n\n[OCR Text from Image]:\n${ocrResult.rawText}"
                }
            } catch (e: Exception) {
                // Ignore OCR failures
            } finally {
                ocrHelper.close()
            }

            val textInput = (input.ifBlank { "What do you see?" }) + extractedText

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

    /**
     * Processes a sequence of video frames and an optional voice/text input.
     */
    fun processVideoFrames(
        model: Model,
        bitmaps: List<Bitmap>,
        input: String,
        isVoice: Boolean = true,
        onFirstToken: (Model) -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Add images to chat history
        addMessage(
            model,
            ChatMessageImage(
                bitmaps = bitmaps,
                imageBitMaps = bitmaps.map { it.asImageBitmap() },
                side = ChatSide.USER
            )
        )

        val textInput = input.ifBlank { "What is happening in this video?" }

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
            images = bitmaps,
            onFirstToken = onFirstToken,
            onDone = onDone,
            onError = onError,
            interactionOrigin = if (isVoice) InteractionOrigin.VOICE else InteractionOrigin.TEXT,
            allowThinking = false
        )
    }
}
