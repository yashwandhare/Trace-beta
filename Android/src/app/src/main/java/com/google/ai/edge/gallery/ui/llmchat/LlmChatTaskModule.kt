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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Mms
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskDataForBuiltinTask
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.gallery.ui.theme.emptyStateContent
import com.google.ai.edge.gallery.ui.theme.emptyStateTitle
import com.google.ai.edge.litertlm.Contents
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.ai.edge.gallery.voice.IntentType
import com.google.ai.edge.gallery.filefetch.FileFetcher
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageAudioClip
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.common.ModuleEmptyState
import com.google.ai.edge.gallery.ui.common.chat.SendMessageTrigger
import com.google.ai.edge.gallery.ui.common.getTaskIconColor

////////////////////////////////////////////////////////////////////////////////////////////////////
// AI Chat.

class LlmChatTask @Inject constructor() : CustomTask {
  override val task: Task =
    Task(
      id = BuiltInTaskId.LLM_CHAT,
      label = "AI Chat",
      category = Category.LLM,
      icon = Icons.Outlined.Forum,
      models = mutableListOf(),
      description = "Chat with on-device large language models",
      shortDescription = "Chat with an on-device LLM",
      docUrl = "https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/README.md",
      sourceCodeUrl =
        "https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt",
      textInputPlaceHolderRes = R.string.text_input_placeholder_llm_chat,
      defaultSystemPrompt = "You are Trace, an advanced, highly intelligent on-device AI assistant powered by Gemma 4. You are designed to be concise, helpful, and exceptionally capable at reasoning, problem-solving, and answering questions. You always provide direct, well-structured, and accurate responses, avoiding unnecessary filler. Since you run completely on-device, you prioritize efficiency and clarity in your interactions."
    )

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    systemInstruction: Contents?,
    onDone: (String) -> Unit,
  ) {
    model.runtimeHelper.initialize(
      context = context,
      model = model,
      taskId = task.id,
      supportImage = true,
      supportAudio = false,
      onDone = onDone,
      coroutineScope = coroutineScope,
      systemInstruction = systemInstruction,
    )
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    model.runtimeHelper.cleanUp(model = model, onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val myData = data as CustomTaskDataForBuiltinTask
    val viewModel: LlmChatViewModel = hiltViewModel()
    
    val context = LocalContext.current
    LaunchedEffect(task) {
      viewModel.loadSystemPrompt(task)
      viewModel.initTts(context)
      viewModel.initRag(context)
    }

    // Register the SemanticFileMatcher classifier once the model warms up.
    // DEMO SCOPE — see /docs/DECISIONS.md "File Fetch — semantic fallback candidate scope".
    val modelManagerUiState by myData.modelManagerViewModel.uiState.collectAsState()
    val selectedModel = modelManagerUiState.selectedModel
    LaunchedEffect(selectedModel.instance) {
      if (selectedModel.instance != null) {
        viewModel.registerSemanticClassifier(selectedModel)
      }
    }
    
    val uiSystemPrompt by viewModel.uiSystemPrompt.collectAsState()
    val systemPromptUpdatedMessage = stringResource(R.string.system_prompt_updated)

    var sendTrigger by remember { mutableStateOf<SendMessageTrigger?>(null) }

    LlmChatScreen(
      modelManagerViewModel = myData.modelManagerViewModel,
      navigateUp = myData.onNavUp,
      viewModel = viewModel,
      allowEditingSystemPrompt = true,
      curSystemPrompt = uiSystemPrompt,
      sendMessageTrigger = sendTrigger,
      onSystemPromptChanged = { newPrompt ->
        val selectedModel = myData.modelManagerViewModel.uiState.value.selectedModel
        viewModel.applySystemPromptChange(
          task = task,
          model = selectedModel,
          newPrompt = newPrompt,
          systemPromptUpdatedMessage = systemPromptUpdatedMessage,
        )
      },
      showImagePicker = true,
      showAttachDocument = true,
      showAudioPicker = true,
      composableBelowMessageList = { model ->
        // Voice input (PTT) lives inside MessageInputText; nothing to render here.
      },
      emptyStateComposable = { model ->
        ModuleEmptyState(
          icon = Icons.Outlined.Forum,
          accent = getTaskIconColor(task),
          title = stringResource(R.string.aichat_emptystate_title),
          description = stringResource(R.string.aichat_emptystate_content),
          suggestions = listOf("Explain a concept", "Draft a message", "Brainstorm ideas"),
          onSuggestionClick = { text ->
            sendTrigger =
              SendMessageTrigger(model, listOf(ChatMessageText(content = text, side = ChatSide.USER)))
          },
        )
      },
      onBenchmarkScreenClicked = myData.onBenchmarkScreenClicked
    )
  }
}

@Module
@InstallIn(SingletonComponent::class) // Or another component that fits your scope
internal object LlmChatTaskModule {
  @Provides
  @IntoSet
  fun provideTask(): CustomTask {
    return LlmChatTask()
  }
}

