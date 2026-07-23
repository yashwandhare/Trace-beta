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

package com.trace.app.ui.common

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.trace.app.BuildConfig
import com.trace.app.GalleryEvent
import com.trace.app.R

import com.trace.app.data.BuiltInTaskId
import com.trace.app.data.ConfigKeys
import com.trace.app.data.Model
import com.trace.app.data.ModelCapability
import com.trace.app.data.ModelDownloadStatusType
import com.trace.app.data.RuntimeType
import com.trace.app.data.Task
import com.trace.app.data.convertValueToTargetType
import com.trace.app.firebaseAnalytics
import com.trace.app.ui.modelmanager.ModelInitializationStatusType
import com.trace.app.ui.modelmanager.ModelManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPageAppBar(
  task: Task,
  model: Model,
  modelManagerViewModel: ModelManagerViewModel,
  onBackClicked: () -> Unit,
  onModelSelected: (prev: Model, cur: Model) -> Unit,
  inProgress: Boolean,
  modelPreparing: Boolean,
  modifier: Modifier = Modifier,
  hideModelSelector: Boolean = false,
  useThemeColor: Boolean = false,
  onConfigChanged: (oldConfigValues: Map<String, Any>, newConfigValues: Map<String, Any>) -> Unit =
    { _, _ ->
    },
  allowEditingSystemPrompt: Boolean = false,
  curSystemPrompt: String = "",
  onSystemPromptChanged: (String) -> Unit = {},
  shouldShowHistoryButton: Boolean = false,
  onHistoryClicked: (Model) -> Unit = {},
  onNewChatClicked: (() -> Unit)? = null,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val context = LocalContext.current
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[model.name]
  val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[model.name]
  val isModelInitializing =
    modelInitializationStatus?.status == ModelInitializationStatusType.INITIALIZING
  val isModelInitialized =
    modelInitializationStatus?.status == ModelInitializationStatusType.INITIALIZED

  CenterAlignedTopAppBar(
    title = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        // Task type.
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          val tintColor =
            if (useThemeColor) MaterialTheme.colorScheme.onSurface
            else getTaskIconColor(task = task)
          Icon(
            task.icon ?: ImageVector.vectorResource(task.iconVectorResourceId!!),
            tint = tintColor,
            modifier = Modifier.size(24.dp),
            contentDescription = null,
          )
          Text(task.label, style = MaterialTheme.typography.titleMedium, color = tintColor)
        }


      }
    },
    modifier = modifier,
    // The hamburger button (opens the shell module drawer).
    navigationIcon = {
      val enableBackButton = !isModelInitializing && !inProgress
      IconButton(onClick = onBackClicked, enabled = enableBackButton) {
        Icon(
          imageVector = Icons.Rounded.Menu,
          contentDescription = stringResource(R.string.cd_navigate_back_icon),
        )
      }
    },
    // The config button for the model (if existed).
    actions = {
      val downloadSucceeded = curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
      val enableActions =
        downloadSucceeded && !isModelInitializing && !modelPreparing && !inProgress && isModelInitialized
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (downloadSucceeded && onNewChatClicked != null) {
          IconButton(
            onClick = { onNewChatClicked() },
            enabled = enableActions,
            modifier = Modifier.alpha(if (!enableActions) 0.5f else 1f),
          ) {
            Icon(
              imageVector = Icons.Rounded.AddComment,
              contentDescription = stringResource(R.string.new_chat),
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(22.dp),
            )
          }
        }
        if (downloadSucceeded && shouldShowHistoryButton) {
          IconButton(
            onClick = { onHistoryClicked(model) },
            enabled = enableActions,
            modifier = Modifier.alpha(if (!enableActions) 0.5f else 1f),
          ) {
            Icon(
              imageVector = Icons.Rounded.History,
              contentDescription = stringResource(R.string.cd_chat_history),
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    },
  )
}
