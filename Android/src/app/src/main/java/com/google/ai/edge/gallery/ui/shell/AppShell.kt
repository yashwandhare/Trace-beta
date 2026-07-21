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

package com.google.ai.edge.gallery.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.customtasks.common.CustomTaskDataForBuiltinTask
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.convertValueToTargetType
import com.google.ai.edge.gallery.ui.common.ConfigDialog
import com.google.ai.edge.gallery.ui.home.SettingsDialog
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.launch

/** The three homescreen modules the shell switches between. */
private enum class ShellModule(val taskId: String, val label: String, val description: String, val icon: ImageVector) {
  AI_CHAT(BuiltInTaskId.LLM_CHAT, "AI Chat", "Chat with Trace, fully on-device", Icons.Outlined.Forum),
  VISION(BuiltInTaskId.VISION, "Vision", "Ask about a photo or your camera", Icons.Outlined.PhotoCamera),
  NOTES(BuiltInTaskId.RAG, "Notes", "Quiz & summarize your own notes", Icons.AutoMirrored.Outlined.MenuBook),
}

/**
 * ChatGPT-style app shell (Phase 3). A single surface that opens into AI Chat; a
 * LEFT hamburger drawer switches the three modules in place and reaches
 * Benchmark + Settings.
 *
 * Design choice (low-risk): each module renders through its OWN existing
 * `MainScreen` (`CustomTask.MainScreen`), so its top bar, history drawer, and
 * setup are reused unchanged — the shell only owns the module-switcher drawer
 * and the model-capability re-init on switch. Each module's own hamburger/back
 * still opens its history / navigates; the shell drawer is opened from the same
 * left edge. This avoids nesting/suppressing the modules' internal drawers.
 *
 * NOT device-tested — the entry point + inline module switching need on-device
 * iteration (see docs/UI_REDESIGN_HANDOFF.md Phase 3).
 */
@Composable
fun AppShell(
  modelManagerViewModel: ModelManagerViewModel,
  onOpenBenchmark: (modelName: String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val uiState by modelManagerViewModel.uiState.collectAsState()

  var activeModule by remember { mutableStateOf(ShellModule.AI_CHAT) }
  var showSettings by remember { mutableStateOf(false) }
  var showModelSettings by remember { mutableStateOf(false) }
  var showSearchScope by remember { mutableStateOf(false) }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet {
        AppDrawerContent(
          active = activeModule,
          onModuleSelected = { module ->
            activeModule = module
            scope.launch { drawerState.close() }
          },
          onBenchmark = {
            scope.launch { drawerState.close() }
            onOpenBenchmark(uiState.selectedModel.name)
          },
          onModelSettings = {
            showModelSettings = true
            scope.launch { drawerState.close() }
          },
          onSearchScope = {
            showSearchScope = true
            scope.launch { drawerState.close() }
          },
          onSettings = {
            showSettings = true
            scope.launch { drawerState.close() }
          },
        )
      }
    },
  ) {
    // Select the active module's task + first model, and (re)initialize with THIS
    // task's capability flags. initializeModel is keyed by model name, not task,
    // so force=true is required or the model keeps the previous module's
    // supportImage/supportAudio flags.
    val task = modelManagerViewModel.getTaskById(activeModule.taskId)
    val customTask = modelManagerViewModel.getCustomTaskByTaskId(activeModule.taskId)

    if (task != null && customTask != null) {
      val firstModel = task.models.firstOrNull()
      androidx.compose.runtime.LaunchedEffect(activeModule) {
        if (firstModel != null) {
          modelManagerViewModel.selectModel(firstModel)
          val status = uiState.modelDownloadStatus[firstModel.name]?.status
          if (status == ModelDownloadStatusType.SUCCEEDED) {
            modelManagerViewModel.initializeModel(context, task = task, model = firstModel, force = true)
          }
        }
      }

      Box(modifier = Modifier.fillMaxSize()) {
        customTask.MainScreen(
          data =
            CustomTaskDataForBuiltinTask(
              modelManagerViewModel = modelManagerViewModel,
              // Back from a module opens the module switcher instead of leaving the app.
              onNavUp = { scope.launch { drawerState.open() } },
              onBenchmarkScreenClicked = { onOpenBenchmark(it.name) },
            )
        )
      }
    }
  }

  if (showSearchScope) {
    com.google.ai.edge.gallery.ui.home.SearchScopeDialog(
      onDismissed = { showSearchScope = false },
      viewModel = modelManagerViewModel,
    )
  }

  if (showSettings) {
    SettingsDialog(
      curThemeOverride = modelManagerViewModel.readThemeOverride(),
      modelManagerViewModel = modelManagerViewModel,
      onDismissed = { showSettings = false },
    )
  }

  if (showModelSettings) {
    val model = uiState.selectedModel
    val configs = remember(model.name) { model.configs }
    val settingsTask = modelManagerViewModel.getTaskById(activeModule.taskId)
    if (configs.isEmpty() || settingsTask == null) {
      showModelSettings = false
    } else {
      val curSystemPrompt = remember(activeModule) { modelManagerViewModel.readSystemPrompt(settingsTask) }
      ConfigDialog(
        title = "Model settings",
        configs = configs,
        initialValues = model.configValues,
        showSystemPromptEditorTab = true,
        defaultSystemPrompt = settingsTask.defaultSystemPrompt,
        curSystemPrompt = curSystemPrompt,
        onDismissed = { showModelSettings = false },
        onOk = { curConfigValues, oldSystemPrompt, newSystemPrompt ->
          showModelSettings = false
          // Persist a changed system prompt for the active module's task.
          if (newSystemPrompt != oldSystemPrompt) {
            modelManagerViewModel.saveSystemPrompt(settingsTask.id, newSystemPrompt)
          }
          // Global effect: all three modules share this one model, so editing its
          // config here applies everywhere. Reuse the same save + reinit path as
          // the old per-screen Tune dialog.
          var changed = false
          var needReinit = false
          for (config in configs) {
            val key = config.key.label
            val oldValue =
              convertValueToTargetType(model.configValues.getValue(key), config.valueType)
            val newValue =
              convertValueToTargetType(curConfigValues.getValue(key), config.valueType)
            if (oldValue != newValue) {
              changed = true
              if (config.needReinitialization) needReinit = true
            }
          }
          val promptChanged = newSystemPrompt != oldSystemPrompt
          if (!changed && !promptChanged) return@ConfigDialog
          if (changed) {
            model.prevConfigValues = model.configValues
            model.configValues = curConfigValues
            modelManagerViewModel.saveModelConfig(model)
            modelManagerViewModel.updateConfigValuesUpdateTrigger()
          }
          // A system-prompt change also needs a reinit to take effect.
          if (needReinit || promptChanged) {
            modelManagerViewModel.initializeModel(context, task = settingsTask, model = model, force = true)
          }
        },
      )
    }
  }
}

@Composable
private fun AppDrawerContent(
  active: ShellModule,
  onModuleSelected: (ShellModule) -> Unit,
  onBenchmark: () -> Unit,
  onModelSettings: () -> Unit,
  onSearchScope: () -> Unit,
  onSettings: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    Text(
      "Trace",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 16.dp),
    )

    ShellModule.values().forEach { module ->
      DrawerRow(
        icon = module.icon,
        label = module.label,
        description = module.description,
        selected = module == active,
        onClick = { onModuleSelected(module) },
      )
      Spacer(Modifier.height(4.dp))
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))

    DrawerRow(icon = Icons.Rounded.Speed, label = "Benchmark", description = null, selected = false, onClick = onBenchmark)
    Spacer(Modifier.height(4.dp))
    DrawerRow(icon = Icons.Rounded.Tune, label = "Model settings", description = null, selected = false, onClick = onModelSettings)
    Spacer(Modifier.height(4.dp))
    DrawerRow(icon = Icons.Rounded.FindInPage, label = "File search scope", description = null, selected = false, onClick = onSearchScope)
    Spacer(Modifier.height(4.dp))
    DrawerRow(icon = Icons.Rounded.Settings, label = "Settings", description = null, selected = false, onClick = onSettings)
  }
}

@Composable
private fun DrawerRow(
  icon: ImageVector,
  label: String,
  description: String?,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(24.dp),
    )
    Spacer(Modifier.width(16.dp))
    Column {
      Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
      )
      if (description != null) {
        Text(
          description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
