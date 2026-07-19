/*
 * Copyright 2026 Google LLC
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

package com.google.ai.edge.gallery.ui.rag

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskDataForBuiltinTask
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.litertlm.Contents
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * The standalone RAG module (Phase 3): notes -> quiz / flashcards / summary,
 * grounded with citations, fully on-device.
 */
class RagTask @Inject constructor() : CustomTask {
  override val task: Task =
    Task(
      id = BuiltInTaskId.RAG,
      label = "Notes",
      category = Category.LLM,
      icon = Icons.AutoMirrored.Outlined.MenuBook,
      models = mutableListOf(),
      description = "Quiz and summarize your own notes",
      shortDescription = "Notes",
      docUrl = "",
      sourceCodeUrl = "",
      textInputPlaceHolderRes = com.google.ai.edge.gallery.R.string.text_input_placeholder_llm_chat,
      defaultSystemPrompt =
        "You are Trace, an on-device AI assistant. You generate quizzes and summaries " +
          "grounded strictly in the user's own notes.",
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
      supportImage = false,
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
    RagScreen(
      task = task,
      modelManagerViewModel = myData.modelManagerViewModel,
      onNavUp = myData.onNavUp,
    )
  }
}

@Module
@InstallIn(SingletonComponent::class)
internal object RagTaskModule {
  @Provides
  @IntoSet
  fun provideTask(): CustomTask {
    return RagTask()
  }
}
