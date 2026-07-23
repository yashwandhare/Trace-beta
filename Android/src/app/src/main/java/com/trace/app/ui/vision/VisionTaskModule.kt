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

package com.trace.app.ui.vision

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.runtime.Composable
import com.trace.app.customtasks.common.CustomTask
import com.trace.app.customtasks.common.CustomTaskDataForBuiltinTask
import com.trace.app.data.BuiltInTaskId
import com.trace.app.data.Category
import com.trace.app.data.Model
import com.trace.app.data.Task
import com.trace.app.runtime.runtimeHelper
import com.google.ai.edge.litertlm.Contents
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class VisionTask @Inject constructor() : CustomTask {
  override val task: Task =
    Task(
      id = BuiltInTaskId.VISION,
      label = "Vision",
      category = Category.LLM,
      icon = Icons.Outlined.PhotoCamera,
      models = mutableListOf(),
      description = "for ocr and visual queries",
      shortDescription = "Vision",
      docUrl = "",
      sourceCodeUrl = "",
      textInputPlaceHolderRes = com.trace.app.R.string.text_input_placeholder_llm_chat,
      defaultSystemPrompt = com.trace.app.data.TracePersona.VISION_SYSTEM_PROMPT
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
    // For Phase 2, we will route to VisionCameraScreen here.
    VisionCameraScreen(
      modelManagerViewModel = myData.modelManagerViewModel,
      onNavUp = myData.onNavUp
    )
  }
}

@Module
@InstallIn(SingletonComponent::class)
internal object VisionTaskModule {
  @Provides
  @IntoSet
  fun provideTask(): CustomTask {
    return VisionTask()
  }
}
