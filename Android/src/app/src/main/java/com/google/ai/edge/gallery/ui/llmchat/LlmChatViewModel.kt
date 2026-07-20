/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.llmchat

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.common.SystemPromptHelper
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.SystemPromptRepository
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.proto.UserData
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageAudioClip
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageError
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageInfo
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageLoading
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageFile
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageThinking
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageType
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageWarning
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.common.chat.ChatViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ToolProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.google.ai.edge.gallery.voice.InteractionOrigin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AGLlmChatViewModel"

// Pre-compiled once — avoids recompiling Regex on every streaming token.
private val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?])\\s+|(?<=[.!?])$")

@OptIn(ExperimentalApi::class)
open class LlmChatViewModelBase(
  private val systemPromptRepository: SystemPromptRepository? = null,
  userDataDataStore: DataStore<UserData>? = null,
  private val modelFeedbackRepository: Any? = null,
  // App-wide shared RAG engine (Hilt singleton) so AI Chat and the RAG module
  // query one index.
  private val sharedRagEngine: com.google.ai.edge.gallery.rag.RagEngine? = null,
) : ChatViewModel(userDataDataStore) {
  private val _uiSystemPrompt = MutableStateFlow("")
  val uiSystemPrompt = _uiSystemPrompt.asStateFlow()

  private var ttsManager: com.google.ai.edge.gallery.voice.TtsManager? = null

  // --- Phase 3 RAG ---
  private val ragEngine: com.google.ai.edge.gallery.rag.RagEngine?
    get() = sharedRagEngine

  // The most recent quiz/summary the RAG engine produced. Dev C2's Quiz UI
  // observes this to render cards; null when there's no active RAG result.
  private val _ragResponse =
    MutableStateFlow<com.google.ai.edge.gallery.rag.RagResponse?>(null)
  val ragResponse = _ragResponse.asStateFlow()

  fun initRag(context: android.content.Context) {
      // The shared RAG engine is an injected app singleton — nothing to construct.
  }

  /** Clears the surfaced RAG result (e.g. when the UI dismisses the quiz panel). */
  fun clearRagResponse() {
      _ragResponse.value = null
  }

  fun initTts(context: android.content.Context) {
      if (ttsManager == null) {
          ttsManager = com.google.ai.edge.gallery.voice.TtsManager(context)
      }
  }

  override fun onCleared() {
      super.onCleared()
      ttsManager?.shutdown()
      // Clear the SemanticFileMatcher classifier so it doesn't hold a stale model reference.
      com.google.ai.edge.gallery.filefetch.SemanticFileMatcher.clearClassifier()
      // The shared RAG engine is app-scoped (Hilt singleton) and must NOT be closed here.
  }

  /**
   * Sets the system prompt in the UI.
   *
   * This method updates the UI system prompt without saving it to the repository or resetting the
   * session. It is primarily used for initializing the UI system prompt.
   *
   * @param systemPrompt The new system prompt to set in the UI.
   */
  fun setUISystemPrompt(systemPrompt: String) {
    _uiSystemPrompt.value = systemPrompt
  }

  /**
   * Loads the system prompt for the given [task] from the repository.
   *
   * @param task The task to load the system prompt for.
   */
  fun loadSystemPrompt(task: Task) {
    viewModelScope.launch {
      val effectivePrompt =
        SystemPromptHelper.getEffectiveSystemPrompt(systemPromptRepository, task)
      _uiSystemPrompt.value = effectivePrompt
    }
  }

  /**
   * Applies a system prompt change to the given [task] and [model].
   *
   * This method updates the UI system prompt, saves the new prompt to the repository, and resets
   * the session with the new prompt.
   *
   * @param task The task to apply the system prompt change to.
   * @param model The model to apply the system prompt change to.
   * @param newPrompt The new system prompt to apply.
   * @param systemPromptUpdatedMessage The message to add to the chat after the system prompt is
   *   updated.
   */
  fun applySystemPromptChange(
    task: Task,
    model: Model,
    newPrompt: String,
    systemPromptUpdatedMessage: String,
  ) {
    _uiSystemPrompt.value = newPrompt
    viewModelScope.launch {
      systemPromptRepository?.updateSystemPrompt(task.id, newPrompt)
      resetSession(
        task = task,
        model = model,
        systemInstruction = Contents.of(newPrompt),
        supportImage = true,
        supportAudio = true,
        onDone = { addMessage(model, ChatMessageInfo(content = systemPromptUpdatedMessage)) },
      )
    }
  }

  /**
   * Registers the SemanticFileMatcher classifier using this ViewModel's already-warm model.
   * Call this once the model instance is ready (e.g., after initialization completes).
   *
   * DEMO SCOPE — see /docs/DECISIONS.md "File Fetch — semantic fallback candidate scope".
   * This wiring will be removed when Phase 3 (Qdrant Edge) replaces the fallback entirely.
   */
  fun registerSemanticClassifier(model: Model) {
      com.google.ai.edge.gallery.filefetch.SemanticFileMatcher.setClassifier { bitmap, prompt ->
          val result = CompletableDeferred<String>()
          // Run a single silent inference on the already-initialized model.
          // We do NOT add messages to the chat history for this call.
          model.runtimeHelper.runInference(
              model = model,
              input = prompt,
              images = listOf(bitmap),
              resultListener = { partialResult, done, _ ->
                  if (done) result.complete(partialResult)
              },
              cleanUpListener = {
                  if (!result.isCompleted) result.complete("")
              },
              onError = { msg ->
                  android.util.Log.e("SemanticMatcher", "Classifier inference error: $msg")
                  if (!result.isCompleted) result.complete("")
              },
              coroutineScope = viewModelScope,
          )
          result.await()
      }
  }

  /**
   * Ingests explicitly-attached files into the RAG index (chunk -> embed ->
   * index) so later queries can be grounded in them. Call when the user sends a
   * message carrying [files]. No-op if RAG isn't initialized or there's no
   * extracted text. Runs in the background; safe to fire-and-forget.
   *
   * Strictly explicit-attachment ingestion — never a background storage scan
   * (see /docs/PRD.md, /docs/DECISIONS.md).
   */
  fun ingestAttachedFiles(files: List<ChatMessageFile>) {
    val engine = ragEngine ?: return
    if (files.isEmpty()) return
    viewModelScope.launch(Dispatchers.Default) {
      files.forEachIndexed { fileIdx, file ->
        val text = file.extractedText
        if (text.isBlank()) return@forEachIndexed
        // Label by the file's display name when resolvable, else a stable fallback.
        val label = file.uris.firstOrNull()?.lastPathSegment?.substringAfterLast('/')
          ?: "attachment-${fileIdx + 1}"
        val n = engine.ingest(text, label)
        Log.d(TAG, "RAG ingest '$label': $n chunks")
      }
    }
  }

  /**
   * If [input] is a RAG request (quiz/summary grounded in attached notes),
   * handles it: retrieves, generates via the resident model, publishes the typed
   * result to [ragResponse], and renders a readable message in chat. Returns
   * true if it handled the query (caller should NOT also run normal generation),
   * false to fall through to normal chat.
   */
  fun tryHandleRagQuery(
    model: Model,
    input: String,
    interactionOrigin: InteractionOrigin,
    onDone: () -> Unit,
  ): Boolean {
    val engine = ragEngine ?: return false
    val mode = engine.detectMode(input) ?: return false

    viewModelScope.launch(Dispatchers.Default) {
      setInProgress(true)
      addMessage(model = model, message = ChatMessageLoading())
      try {
        val response = engine.generate(mode, input, model, this)
        removeLastMessageIfLoading(model)
        if (response == null) {
          val msg = "I couldn't find anything about that in your attached notes. Try attaching the relevant document first."
          addMessage(model, ChatMessageText(content = msg, side = ChatSide.AGENT))
          if (interactionOrigin == InteractionOrigin.VOICE) ttsManager?.speak(msg)
        } else {
          _ragResponse.value = response
          val rendered = renderRagResponseForChat(response)
          addMessage(model, ChatMessageText(content = rendered, side = ChatSide.AGENT))
          if (interactionOrigin == InteractionOrigin.VOICE) {
            // Speak the summary or the first question — not the whole JSON.
            val spoken = if (response.isQuiz) response.items.firstOrNull()?.question ?: rendered
                         else response.summary
            if (spoken.isNotBlank()) ttsManager?.speak(spoken)
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "RAG query failed", e)
        removeLastMessageIfLoading(model)
        addMessage(model, ChatMessageText(content = "Something went wrong generating from your notes.", side = ChatSide.AGENT))
      } finally {
        setInProgress(false)
        onDone()
      }
    }
    return true
  }

  private fun removeLastMessageIfLoading(model: Model) {
    if (getLastMessage(model = model) is ChatMessageLoading) {
      removeLastMessage(model = model)
    }
  }

  /** Renders a [RagResponse] as human-readable chat text (UI panel is separate). */
  private fun renderRagResponseForChat(
    response: com.google.ai.edge.gallery.rag.RagResponse
  ): String {
    val header = if (response.sources.isNotEmpty())
      "From your notes (${response.sources.joinToString(", ")}):\n\n" else ""
    val body = if (response.isQuiz) {
      response.items.mapIndexed { i, item ->
        val opts = if (item.isMultipleChoice)
          "\n" + item.options.joinToString("\n") { "   - $it" } else ""
        "${i + 1}. ${item.question}$opts\n   Answer: ${item.answer}"
      }.joinToString("\n\n")
    } else {
      response.summary
    }
    val citations = if (response.citations.isNotEmpty()) {
      "\n\n---\nSources:\n" + response.citations.joinToString("\n") {
        "• [${it.sourceLabel}] \"${it.snippet}\""
      }
    } else ""
    return header + body + citations
  }

  fun generateResponse(
    model: Model,
    input: String,
    images: List<Bitmap> = listOf(),
    audioMessages: List<ChatMessageAudioClip> = listOf(),
    files: List<ChatMessageFile> = listOf(),
    onFirstToken: (Model) -> Unit = {},
    onDone: () -> Unit = {},
    onError: (String) -> Unit,
    allowThinking: Boolean = false,
    interactionOrigin: InteractionOrigin = InteractionOrigin.TEXT,
  ) {
    val accelerator = model.getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = "")
    viewModelScope.launch(Dispatchers.Default) {
      setInProgress(true)
      setPreparing(true)

      // Loading.
      addMessage(model = model, message = ChatMessageLoading(accelerator = accelerator))

      // Wait for instance to be initialized.
      while (model.instance == null) {
        delay(100)
      }
      // NOTE: The previous delay(500) here was removed — it added 500ms to every
      // inference call with no functional purpose.

      // Run inference.
      val audioClips: MutableList<ByteArray> = mutableListOf()
      for (audioMessage in audioMessages) {
        audioClips.add(audioMessage.genByteArrayForWav())
      }

      var firstRun = true
      var ttsBuffer = ""
      val start = System.currentTimeMillis()

      try {
        val resultListener: (String, Boolean, String?) -> Unit =
          { partialResult, done, partialThinkingResult ->
            if (partialResult.startsWith("<ctrl")) {
              // Do nothing. Ignore control tokens.
            } else {
              // Remove the last message if it is a "loading" message.
              // This will only be done once.
              val lastMessage = getLastMessage(model = model)
              val wasLoading = lastMessage?.type == ChatMessageType.LOADING
              if (wasLoading) {
                removeLastMessage(model = model)
              }

              val thinkingText = partialThinkingResult
              val isThinking = thinkingText != null && thinkingText.isNotEmpty()
              var currentLastMessage = getLastMessage(model = model)

              // If thinking is enabled, add a thinking message.
              if (isThinking) {
                if (currentLastMessage?.type != ChatMessageType.THINKING) {
                  addMessage(
                    model = model,
                    message =
                      ChatMessageThinking(
                        content = "",
                        inProgress = true,
                        side = ChatSide.AGENT,
                        accelerator = accelerator,
                        hideSenderLabel =
                          currentLastMessage?.type == ChatMessageType.COLLAPSABLE_PROGRESS_PANEL,
                      ),
                  )
                }
                updateLastThinkingMessageContentIncrementally(
                  model = model,
                  partialContent = thinkingText!!,
                )
              } else {
                if (currentLastMessage?.type == ChatMessageType.THINKING) {
                  val thinkingMsg = currentLastMessage as ChatMessageThinking
                  if (thinkingMsg.inProgress) {
                    replaceLastMessage(
                      model = model,
                      message =
                        ChatMessageThinking(
                          content = thinkingMsg.content,
                          inProgress = false,
                          side = thinkingMsg.side,
                          accelerator = thinkingMsg.accelerator,
                          hideSenderLabel = thinkingMsg.hideSenderLabel,
                        ),
                      type = ChatMessageType.THINKING,
                    )
                  }
                }
                currentLastMessage = getLastMessage(model = model)
                if (
                  currentLastMessage?.type != ChatMessageType.TEXT ||
                    currentLastMessage.side != ChatSide.AGENT
                ) {
                  // Add an empty message that will receive streaming results.
                  addMessage(
                    model = model,
                    message =
                      ChatMessageText(
                        content = "",
                        side = ChatSide.AGENT,
                        accelerator = accelerator,
                        hideSenderLabel =
                          currentLastMessage?.type == ChatMessageType.COLLAPSABLE_PROGRESS_PANEL ||
                            currentLastMessage?.type == ChatMessageType.THINKING,
                      ),
                  )
                }

                // Incrementally update the streamed partial results.
                val latencyMs: Long = if (done) System.currentTimeMillis() - start else -1
                if (partialResult.isNotEmpty() || wasLoading || done) {
                  updateLastTextMessageContentIncrementally(
                    model = model,
                    partialContent = partialResult,
                    latencyMs = latencyMs.toFloat(),
                  )
                  // Only buffer/emit TTS for voice-originated prompts.
                  // Uses main's improved chunking: word-count + last-punctuation boundary.
                  if (interactionOrigin == InteractionOrigin.VOICE) {
                    ttsBuffer += partialResult
                    val boundary = ttsBuffer.indexOfLast { it == '.' || it == '!' || it == '?' || it == '\n' }
                    val enoughWords = ttsBuffer.trim().split(Regex("\\s+")).size >= TTS_CHUNK_WORD_COUNT
                    if (boundary >= 0 || enoughWords) {
                      val splitAt = if (boundary >= 0) boundary + 1 else ttsBuffer.lastIndexOf(' ').coerceAtLeast(0)
                      val chunk = ttsBuffer.substring(0, splitAt).trim()
                      if (chunk.isNotEmpty()) {
                        ttsManager?.speak(chunk, android.speech.tts.TextToSpeech.QUEUE_ADD)
                      }
                      ttsBuffer = ttsBuffer.substring(splitAt).trimStart()
                    }
                  }
                }
              }

              if (firstRun) {
                firstRun = false
                setPreparing(false)
                onFirstToken(model)
              }

              if (done) {
                if (interactionOrigin == InteractionOrigin.VOICE && ttsBuffer.isNotBlank()) {
                  ttsManager?.speak(ttsBuffer.trim(), android.speech.tts.TextToSpeech.QUEUE_ADD)
                  ttsBuffer = ""
                }
                val finalLastMessage = getLastMessage(model = model)
                if (finalLastMessage?.type == ChatMessageType.THINKING) {
                  val thinkingMsg = finalLastMessage as ChatMessageThinking
                  if (thinkingMsg.inProgress) {
                    replaceLastMessage(
                      model = model,
                      message =
                        ChatMessageThinking(
                          content = thinkingMsg.content,
                          inProgress = false,
                          side = thinkingMsg.side,
                          accelerator = thinkingMsg.accelerator,
                          hideSenderLabel = thinkingMsg.hideSenderLabel,
                        ),
                      type = ChatMessageType.THINKING,
                    )
                  }
                }
                setInProgress(false)
                onDone()
              }
            }
          }

        val cleanUpListener: () -> Unit = {
          setInProgress(false)
          setPreparing(false)
        }

        val errorListener: (String) -> Unit = { message ->
          Log.e(TAG, "Error occurred while running inference")
          setInProgress(false)
          setPreparing(false)
          onError(message)
        }

        val enableThinking =
          allowThinking &&
            model.getBooleanConfigValue(key = ConfigKeys.ENABLE_THINKING, defaultValue = false)
        val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

        var finalInput = input
        if (files.isNotEmpty()) {
            val fileText = files.joinToString("\n\n") { it.extractedText }
            if (fileText.isNotBlank()) {
                finalInput = "[Attached Document Content:]\n$fileText\n\n$finalInput"
            }
        }

        model.runtimeHelper.runInference(
          model = model,
          input = finalInput,
          images = images,
          audioClips = audioClips,
          resultListener = resultListener,
          cleanUpListener = cleanUpListener,
          onError = errorListener,
          coroutineScope = viewModelScope,
          extraContext = extraContext,
        )
      } catch (e: Exception) {
        Log.e(TAG, "Error occurred while running inference", e)
        setInProgress(false)
        setPreparing(false)
        onError(e.message ?: "")
      }
    }
  }

  fun stopResponse(model: Model) {
    Log.d(TAG, "Stopping response for model ${model.name}...")
    if (getLastMessage(model = model) is ChatMessageLoading) {
      removeLastMessage(model = model)
    }
    setInProgress(false)
    ttsManager?.stop()
    model.runtimeHelper.stopResponse(model)
    Log.d(TAG, "Done stopping response")
  }

  fun resetSession(
    task: Task,
    model: Model,
    systemInstruction: Contents? = null,
    tools: List<ToolProvider> = listOf(),
    supportImage: Boolean = false,
    supportAudio: Boolean = false,
    onDone: () -> Unit = {},
    enableConversationConstrainedDecoding: Boolean = false,
    initialMessages: List<Message> = listOf(),
    clearHistory: Boolean = true,
  ) {
    viewModelScope.launch(Dispatchers.Default) {
      setIsResettingSession(true)
      if (clearHistory) {
        clearAllMessages(model = model)
      }
      stopResponse(model = model)

      while (true) {
        try {
          model.runtimeHelper.resetConversation(
            model = model,
            supportImage = supportImage,
            supportAudio = supportAudio,
            systemInstruction = systemInstruction,
            tools = tools,
            enableConversationConstrainedDecoding = enableConversationConstrainedDecoding,
            initialMessages = initialMessages,
          )
          break
        } catch (e: Exception) {
          Log.d(TAG, "Failed to reset session. Trying again")
        }
        delay(200)
      }
      setIsResettingSession(false)
      onDone()
    }
  }

  fun runAgain(
    model: Model,
    message: ChatMessageText,
    onError: (String) -> Unit,
    allowThinking: Boolean = false,
  ) {
    viewModelScope.launch(Dispatchers.Default) {
      // Wait for model to be initialized.
      while (model.instance == null) {
        delay(100)
      }

      // Clone the clicked message and add it.
      addMessage(model = model, message = message.clone())

      // Run inference.
      generateResponse(
        model = model,
        input = message.content,
        onError = onError,
        allowThinking = allowThinking,
      )
    }
  }

  fun handleError(
    context: Context,
    task: Task,
    model: Model,
    modelManagerViewModel: ModelManagerViewModel,
    errorMessage: String,
  ) {
    // Remove the "loading" message.
    if (getLastMessage(model = model) is ChatMessageLoading) {
      removeLastMessage(model = model)
    }

    // Show error message.
    addMessage(model = model, message = ChatMessageError(content = errorMessage))

    // Clean up and re-initialize.
    viewModelScope.launch(Dispatchers.Default) {
      modelManagerViewModel.cleanupModel(
        context = context,
        task = task,
        model = model,
        onDone = {
          modelManagerViewModel.initializeModel(
            context = context,
            task = task,
            model = model,
            onDone = {
              // Add a warning message for re-initializing the session.
              addMessage(
                model = model,
                message = ChatMessageWarning(content = "Session re-initialized"),
              )
            },
            onError = {
              addMessage(
                model = model,
                message =
                  ChatMessageError(
                    content = "Failed to re-initialize session, please restart the app"
                  ),
              )
            },
          )
        },
      )
    }
  }
}

private const val TTS_CHUNK_WORD_COUNT = 12

@HiltViewModel
class LlmChatViewModel
@Inject
constructor(
  systemPromptRepository: SystemPromptRepository,
  userDataDataStore: DataStore<UserData>,
  ragEngine: com.google.ai.edge.gallery.rag.RagEngine,
) : LlmChatViewModelBase(systemPromptRepository, userDataDataStore, null, ragEngine)
