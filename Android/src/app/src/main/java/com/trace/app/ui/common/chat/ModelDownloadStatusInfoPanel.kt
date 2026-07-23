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

package com.trace.app.ui.common.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trace.app.data.Model
import com.trace.app.data.ModelDownloadStatusType
import com.trace.app.data.Task
import com.trace.app.ui.common.DownloadAndTryButton
import com.trace.app.ui.common.modelitem.calculateDownloadProgress
import com.trace.app.ui.modelmanager.ModelManagerViewModel

@Composable
fun ModelDownloadStatusInfoPanel(
  model: Model,
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  onBenchmarkClicked: (Model) -> Unit = {},
) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    com.trace.app.ui.common.modelitem.ModelItem(
      model = model,
      task = task,
      modelManagerViewModel = modelManagerViewModel,
      onModelClicked = {},
      onBenchmarkClicked = onBenchmarkClicked,
      modifier = Modifier.padding(16.dp).fillMaxWidth(),
      canExpand = false,
      expanded = true
    )
  }
}
