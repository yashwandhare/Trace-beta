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

import androidx.hilt.navigation.compose.hiltViewModel
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.GalleryEvent
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelCapability
import com.google.ai.edge.gallery.data.RuntimeType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.firebaseAnalytics
import com.google.ai.edge.gallery.ui.common.chat.ChatMessage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageAudioClip
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageImage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageFile
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.common.chat.ChatView
import com.google.ai.edge.gallery.ui.common.chat.SendMessageTrigger
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.emptyStateContent
import com.google.ai.edge.gallery.ui.theme.emptyStateTitle
import com.google.ai.edge.gallery.voice.InteractionOrigin
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message

private const val TAG = "AGLlmChatScreen"

@Composable
fun LlmChatScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  taskId: String = BuiltInTaskId.LLM_CHAT,
  onFirstToken: (Model) -> Unit = {},
  onGenerateResponseDone: (Model) -> Unit = {},
  onSkillClicked: () -> Unit = {},
  onMcpClicked: () -> Unit = {},
  onResetSessionClickedOverride: ((Task, Model, List<ChatMessage>, Boolean, () -> Unit) -> Unit)? =
    null,
  composableBelowMessageList: @Composable (Model) -> Unit = {},
  viewModel: LlmChatViewModel = hiltViewModel(),
  allowEditingSystemPrompt: Boolean = false,
  curSystemPrompt: String = "",
  onSystemPromptChanged: (String) -> Unit = {},
  emptyStateComposable: @Composable (Model) -> Unit = {},
  sendMessageTrigger: SendMessageTrigger? = null,
  showImagePicker: Boolean = false,
  showAttachDocument: Boolean = false,
  showAudioPicker: Boolean = false,
  getActiveSkills: () -> List<String> = { emptyList() },
  skillCount: Int = 0,
  mcpCount: Int = 0,
  mcpToolsCount: Int = 0,
  onBenchmarkScreenClicked: (Model) -> Unit = {},
) {
  ChatViewWrapper(
    viewModel = viewModel,
    modelManagerViewModel = modelManagerViewModel,
    taskId = taskId,
    navigateUp = navigateUp,
    modifier = modifier,
    onSkillClicked = onSkillClicked,
    onMcpClicked = onMcpClicked,
    onFirstToken = onFirstToken,
    onGenerateResponseDone = onGenerateResponseDone,
    onResetSessionClickedOverride = onResetSessionClickedOverride,
    composableBelowMessageList = composableBelowMessageList,
    allowEditingSystemPrompt = allowEditingSystemPrompt,
    curSystemPrompt = curSystemPrompt,
    skillCount = skillCount,
    mcpCount = mcpCount,
    mcpToolsCount = mcpToolsCount,
    onSystemPromptChanged = onSystemPromptChanged,
    emptyStateComposable = emptyStateComposable,
    sendMessageTrigger = sendMessageTrigger,
    showImagePicker = showImagePicker,
    showAttachDocument = showAttachDocument,
    showAudioPicker = showAudioPicker,
    getActiveSkills = getActiveSkills,
    onBenchmarkScreenClicked = onBenchmarkScreenClicked,
  )
}

@Composable
fun ChatViewWrapper(
  viewModel: LlmChatViewModelBase,
  modelManagerViewModel: ModelManagerViewModel,
  taskId: String,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  onSkillClicked: () -> Unit = {},
  onMcpClicked: () -> Unit = {},
  onFirstToken: (Model) -> Unit = {},
  onGenerateResponseDone: (Model) -> Unit = {},
  onResetSessionClickedOverride: ((Task, Model, List<ChatMessage>, Boolean, () -> Unit) -> Unit)? =
    null,
  composableBelowMessageList: @Composable (Model) -> Unit = {},
  emptyStateComposable: @Composable (Model) -> Unit = {},
  allowEditingSystemPrompt: Boolean = false,
  curSystemPrompt: String = "",
  onSystemPromptChanged: (String) -> Unit = {},
  sendMessageTrigger: SendMessageTrigger? = null,
  showImagePicker: Boolean = false,
  showAttachDocument: Boolean = false,
  showAudioPicker: Boolean = false,
  getActiveSkills: () -> List<String> = { emptyList() },
  skillCount: Int = 0,
  mcpCount: Int = 0,
  mcpToolsCount: Int = 0,
  onBenchmarkScreenClicked: (Model) -> Unit = {},
) {
  val context = LocalContext.current
  // Defensive: an unknown taskId previously crashed here with a null-assertion (!!).
  val task = modelManagerViewModel.getTaskById(id = taskId)
  if (task == null) {
    Log.e("LlmChatScreen", "No task found for id=$taskId — cannot render chat screen")
    return
  }
  val scope = rememberCoroutineScope()

  ChatView(
    task = task,
    viewModel = viewModel,
    modelManagerViewModel = modelManagerViewModel,
    onBenchmarkScreenClicked = onBenchmarkScreenClicked,
    onSendMessage = { model, messages ->
      for (message in messages) {
        viewModel.addMessage(model = model, message = message)
      }

      var text = ""
      val images: MutableList<Bitmap> = mutableListOf()
      val audioMessages: MutableList<ChatMessageAudioClip> = mutableListOf()
      val files: MutableList<ChatMessageFile> = mutableListOf()
      var chatMessageText: ChatMessageText? = null
      for (message in messages) {
        if (message is ChatMessageText) {
          chatMessageText = message
          text = message.content
        } else if (message is ChatMessageImage) {
          images.addAll(message.bitmaps)
        } else if (message is ChatMessageAudioClip) {
          audioMessages.add(message)
        } else if (message is ChatMessageFile) {
          files.add(message)
        }
      }
      if ((text.isNotEmpty() && chatMessageText != null) || audioMessages.isNotEmpty() || files.isNotEmpty() || images.isNotEmpty()) {
        if (text.isNotEmpty()) {
          modelManagerViewModel.addTextInputHistory(text)
        }
        // Phase 3 RAG: ingest any attached notes so later queries can be
        // grounded in them.
        if (files.isNotEmpty()) {
          viewModel.ingestAttachedFiles(files)
        }
        val ragOrigin =
          if (audioMessages.isNotEmpty() || chatMessageText?.data == InteractionOrigin.VOICE)
            InteractionOrigin.VOICE else InteractionOrigin.TEXT
        // Opt-in web search: a "websearch ..." message (only when the sidebar
        // toggle is on) fetches results, grounds the prompt, and generates. When
        // off or keyword-absent this returns false and normal flow continues.
        val handledByWebSearch =
          text.isNotEmpty() &&
            viewModel.tryHandleWebSearch(
              model = model,
              input = text,
              enabled = modelManagerViewModel.getWebSearchEnabled(),
              onFirstToken = onFirstToken,
              onDone = { onGenerateResponseDone(model) },
              onError = { errorMessage ->
                viewModel.handleError(
                  context = context,
                  task = task,
                  model = model,
                  errorMessage = errorMessage,
                  modelManagerViewModel = modelManagerViewModel,
                )
              },
              interactionOrigin = ragOrigin,
            )
        // If this is a "quiz me / summarize my notes" request, RAG handles it and
        // we skip normal generation.
        val handledByRag =
          !handledByWebSearch &&
            text.isNotEmpty() &&
            viewModel.tryHandleRagQuery(
              model = model,
              input = text,
              interactionOrigin = ragOrigin,
              onDone = { onGenerateResponseDone(model) },
            )
        if (!handledByWebSearch && !handledByRag) {
        viewModel.generateResponse(
          model = model,
          input = text,
          images = images,
          audioMessages = audioMessages,
          files = files,
          onFirstToken = onFirstToken,
          onDone = { onGenerateResponseDone(model) },
          onError = { errorMessage ->
            viewModel.handleError(
              context = context,
              task = task,
              model = model,
              errorMessage = errorMessage,
              modelManagerViewModel = modelManagerViewModel,
            )
          },
          allowThinking = task.allowCapability(ModelCapability.LLM_THINKING, model),
          // Determine origin: audio clip messages = voice, OR if the text message is marked as STT.
          interactionOrigin = if (audioMessages.isNotEmpty() || chatMessageText?.data == InteractionOrigin.VOICE) InteractionOrigin.VOICE else InteractionOrigin.TEXT,
        )
        } // end if (!handledByWebSearch && !handledByRag)
        val activeSkills = getActiveSkills()
        Log.d(
          TAG,
          "Analytics: generate_action, capability_name=${task.id}, active_skills=${activeSkills.joinToString(",")}, active_mcp_servers_count=$mcpCount, active_mcp_tools_count=$mcpToolsCount",
        )
        firebaseAnalytics?.logEvent(
          GalleryEvent.GENERATE_ACTION.id,
          Bundle().apply {
            putString("capability_name", task.id)
            putString("model_id", model.name)
            putBoolean("has_image", images.isNotEmpty())
            putInt("image_count", images.size)
            putBoolean("has_audio", audioMessages.isNotEmpty())
            putInt("audio_count", audioMessages.size)
            putBoolean("has_document", files.isNotEmpty())
            putInt("document_count", files.size)
            putInt("active_skills_count", activeSkills.size)
            putString("active_skills_list", activeSkills.joinToString(","))
            putInt("active_mcp_servers_count", mcpCount)
            putInt("active_mcp_tools_count", mcpToolsCount)
          },
        )
      }
    },
    onRunAgainClicked = { model, message ->
      if (message is ChatMessageText) {
        viewModel.runAgain(
          model = model,
          message = message,
          onError = { errorMessage ->
            viewModel.handleError(
              context = context,
              task = task,
              model = model,
              errorMessage = errorMessage,
              modelManagerViewModel = modelManagerViewModel,
            )
          },
          allowThinking = task.allowCapability(ModelCapability.LLM_THINKING, model),
        )
      }
    },
    onBenchmarkClicked = { _, _, _, _ -> },
    onResetSessionClicked = { model, chatMessages, clearHistory, onDone ->
      val litertMessages = chatMessages.mapNotNull { convertToLitertMessage(it) }
      if (onResetSessionClickedOverride != null) {
        onResetSessionClickedOverride(task, model, chatMessages, clearHistory, onDone)
      } else {
        viewModel.resetSession(
          task = task,
          model = model,
          systemInstruction = Contents.of(curSystemPrompt),
          supportImage = showImagePicker,
          supportAudio = showAudioPicker,
          initialMessages = litertMessages,
          onDone = onDone,
          clearHistory = clearHistory,
        )
      }
    },
    showStopButtonInInputWhenInProgress = true,
    onStopButtonClicked = { model -> viewModel.stopResponse(model = model) },
    onSkillClicked = onSkillClicked,
    onMcpClicked = onMcpClicked,
    navigateUp = navigateUp,
    skillCount = skillCount,
    mcpCount = mcpCount,
    modifier = modifier,
    composableBelowMessageList = composableBelowMessageList,
    showImagePicker = showImagePicker,
    showAttachDocument = showAttachDocument,
    emptyStateComposable = emptyStateComposable,
    allowEditingSystemPrompt = allowEditingSystemPrompt,
    curSystemPrompt = curSystemPrompt,
    onSystemPromptChanged = onSystemPromptChanged,
    sendMessageTrigger = sendMessageTrigger,
    showAudioPicker = showAudioPicker,
    // Voice-originated messages always use VOICE origin so TTS fires.
    onSendVoiceMessage = { model, messages ->
      for (message in messages) {
        viewModel.addMessage(model = model, message = message)
      }
      var text = ""
      val files: MutableList<ChatMessageFile> = mutableListOf()
      for (message in messages) {
        if (message is ChatMessageText) text = message.content
        else if (message is ChatMessageFile) files.add(message)
      }
      if (text.isNotEmpty() || files.isNotEmpty()) {
        if (text.isNotEmpty()) {
          modelManagerViewModel.addTextInputHistory(text)
        }
        // Phase 3 RAG: ingest attached notes, and let RAG handle quiz/summary
        // voice queries grounded in them.
        if (files.isNotEmpty()) {
          viewModel.ingestAttachedFiles(files)
        }
        val handledByWebSearch =
          text.isNotEmpty() &&
            viewModel.tryHandleWebSearch(
              model = model,
              input = text,
              enabled = modelManagerViewModel.getWebSearchEnabled(),
              onFirstToken = onFirstToken,
              onDone = { onGenerateResponseDone(model) },
              onError = { errorMessage ->
                viewModel.handleError(
                  context = context,
                  task = task,
                  model = model,
                  errorMessage = errorMessage,
                  modelManagerViewModel = modelManagerViewModel,
                )
              },
              interactionOrigin = InteractionOrigin.VOICE,
            )
        val handledByRag =
          !handledByWebSearch &&
            text.isNotEmpty() &&
            viewModel.tryHandleRagQuery(
              model = model,
              input = text,
              interactionOrigin = InteractionOrigin.VOICE,
              onDone = { onGenerateResponseDone(model) },
            )
        if (!handledByWebSearch && !handledByRag) {
        viewModel.generateResponse(
          model = model,
          input = text,
          files = files,
          onFirstToken = onFirstToken,
          onDone = { onGenerateResponseDone(model) },
          onError = { errorMessage ->
            viewModel.handleError(
              context = context,
              task = task,
              model = model,
              errorMessage = errorMessage,
              modelManagerViewModel = modelManagerViewModel,
            )
          },
          allowThinking = task.allowCapability(ModelCapability.LLM_THINKING, model),
          interactionOrigin = InteractionOrigin.VOICE,
        )
        } // end if (!handledByWebSearch && !handledByRag)
      }
    },
  )
}

private fun convertToLitertMessage(chatMessage: ChatMessage): Message? {
  // TODO: Restore image and audio messages to the LLM context.
  // We are currently bypassing them because the image and audio encoder may take
  // too long during chat history loading, which can cause stalls or stream errors.
  if (chatMessage is ChatMessageText) {
    return when (chatMessage.side) {
      ChatSide.USER -> Message.user(chatMessage.content)
      ChatSide.AGENT -> Message.model(chatMessage.content)
      ChatSide.SYSTEM ->
        null // TODO: Support SYSTEM role once we can decide on which system prompt to use.
    }
  }
  return null
}
