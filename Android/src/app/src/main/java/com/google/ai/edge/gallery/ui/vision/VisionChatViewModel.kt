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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class VisionChatViewModel @Inject constructor(
    systemPromptRepository: SystemPromptRepository,
    userDataDataStore: DataStore<UserData>,
    memoryRepository: com.google.ai.edge.gallery.memory.MemoryRepository,
    notificationScheduleManager: com.google.ai.edge.gallery.notifications.NotificationScheduleManager,
) : LlmChatViewModelBase(
    systemPromptRepository = systemPromptRepository,
    userDataDataStore = userDataDataStore,
    memoryRepository = memoryRepository,
    notificationScheduleManager = notificationScheduleManager,
) {

    /**
     * Processes a single camera frame (bitmap) and an optional voice/text input,
     * adding them to the chat history and triggering generation.
     */
    fun processCameraFrame(
        model: Model,
        bitmap: Bitmap,
        input: String,
        isVoice: Boolean = false,
        onFirstToken: (Model) -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                // Add image to chat history
                addMessage(
                    model,
                    ChatMessageImage(
                        bitmaps = listOf(bitmap),
                        imageBitMaps = listOf(bitmap.asImageBitmap()),
                        side = ChatSide.USER
                    )
                )

                val ocrHelper = OcrHelper()
                var extractedText = ""
                try {
                    val ocrResult = ocrHelper.recognizeText(bitmap)
                    if (ocrResult.rawText.isNotBlank()) {
                        extractedText = "\n\n[OCR Text from Image]:\n${ocrResult.rawText}"
                    }
                } catch (e: Exception) {
                    // Ignore OCR failures silently
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

                // Wait up to 30s for model to initialize before generating
                var waited = 0
                while (model.instance == null && waited < 30_000) {
                    kotlinx.coroutines.delay(200)
                    waited += 200
                }
                if (model.instance == null) {
                    onError("Model not ready. Please wait for it to finish loading.")
                    return@launch
                }

                generateResponse(
                    model = model,
                    input = textInput,
                    images = listOf(bitmap),
                    onFirstToken = onFirstToken,
                    onDone = onDone,
                    onError = onError,
                    interactionOrigin = if (isVoice) InteractionOrigin.VOICE else InteractionOrigin.TEXT,
                    allowThinking = false
                )
            } catch (e: Exception) {
                android.util.Log.e("VisionChatViewModel", "processCameraFrame error", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Selects keyframes from a raw frame list using pixel-difference deduplication.
     *
     * Frames too visually similar to the previously kept frame are dropped.
     * Hard cap: MAX_VIDEO_FRAMES to keep model input manageable.
     *
     * @param frames Raw list captured at up to 1fps
     * @param maxFrames Hard cap on output frames
     * @param diffThreshold 0..1 fraction of pixels that must differ to keep a frame (0.05 = 5%)
     */
    private fun selectKeyframes(
        frames: List<Bitmap>,
        maxFrames: Int = 20,
        diffThreshold: Float = 0.05f
    ): List<Bitmap> {
        if (frames.isEmpty()) return emptyList()
        if (frames.size <= maxFrames) return frames  // Short video — keep all

        val kept = mutableListOf<Bitmap>()
        var lastKept: Bitmap? = null

        // Sample uniformly first if too many (e.g. 200fps capture edge-case)
        val step = if (frames.size > maxFrames * 4) frames.size / (maxFrames * 2) else 1
        val candidates = frames.filterIndexed { i, _ -> i % step == 0 }

        for (frame in candidates) {
            if (kept.size >= maxFrames) break
            val prev = lastKept
            if (prev == null) {
                kept.add(frame)
                lastKept = frame
                continue
            }
            // Downscale both to 64x64 for fast diff
            val scale = 64
            val w = minOf(frame.width, scale)
            val h = minOf(frame.height, scale)
            val curr64 = Bitmap.createScaledBitmap(frame, w, h, false)
            val prev64 = Bitmap.createScaledBitmap(prev, w, h, false)

            var diffCount = 0
            val total = w * h
            for (x in 0 until w) {
                for (y in 0 until h) {
                    val cp = curr64.getPixel(x, y)
                    val pp = prev64.getPixel(x, y)
                    val dr = Math.abs(android.graphics.Color.red(cp) - android.graphics.Color.red(pp))
                    val dg = Math.abs(android.graphics.Color.green(cp) - android.graphics.Color.green(pp))
                    val db = Math.abs(android.graphics.Color.blue(cp) - android.graphics.Color.blue(pp))
                    if ((dr + dg + db) > 60) diffCount++  // threshold: any channel diff > 20
                }
            }
            curr64.recycle()
            prev64.recycle()

            val diffRatio = diffCount.toFloat() / total
            if (diffRatio >= diffThreshold) {
                kept.add(frame)
                lastKept = frame
            }
        }

        // Always include the last frame so the end of the video is represented
        if (kept.lastOrNull() !== frames.last() && kept.size < maxFrames) {
            kept.add(frames.last())
        }
        return kept
    }

    /**
     * Processes a sequence of video frames.
     * Applies keyframe deduplication before sending to model.
     * Only uses TTS when isVoice=true (voice-originated input).
     */
    fun processVideoFrames(
        model: Model,
        bitmaps: List<Bitmap>,
        input: String,
        isVoice: Boolean = false,   // false = text/auto-query → no TTS
        onFirstToken: (Model) -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                // Select meaningful keyframes — avoids sending 100 near-identical frames
                val keyframes = selectKeyframes(bitmaps)
                android.util.Log.d("VisionChatViewModel",
                    "Video: ${bitmaps.size} raw frames → ${keyframes.size} keyframes sent")

                if (keyframes.isEmpty()) {
                    onError("No usable frames captured.")
                    return@launch
                }

                // Add images to chat history
                addMessage(
                    model,
                    ChatMessageImage(
                        bitmaps = keyframes,
                        imageBitMaps = keyframes.map { it.asImageBitmap() },
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

                // Wait for model to initialise
                var waited = 0
                while (model.instance == null && waited < 30_000) {
                    delay(200)
                    waited += 200
                }
                if (model.instance == null) {
                    onError("Model not ready.")
                    return@launch
                }

                generateResponse(
                    model = model,
                    input = textInput,
                    images = keyframes,
                    onFirstToken = onFirstToken,
                    onDone = onDone,
                    onError = onError,
                    interactionOrigin = if (isVoice) InteractionOrigin.VOICE else InteractionOrigin.TEXT,
                    allowThinking = false
                )
            } catch (e: Exception) {
                android.util.Log.e("VisionChatViewModel", "processVideoFrames error", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }
}
