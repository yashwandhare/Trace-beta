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

package com.google.ai.edge.gallery.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.ui.common.getTaskIconColor
import com.google.ai.edge.gallery.ui.common.modelitem.calculateDownloadProgress
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/**
 * First-run onboarding: a snappy intro, a one-line explainer, and a guided
 * model-download step with live progress. Replaces dropping a first-time user
 * straight into an empty chat with a download tile.
 */
@Composable
fun OnboardingScreen(
  modelManagerViewModel: ModelManagerViewModel,
  onDone: () -> Unit,
) {
  val context = LocalContext.current
  val uiState by modelManagerViewModel.uiState.collectAsState()
  val task = remember { modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT) }
  val model = task?.models?.firstOrNull()
  val accent = if (task != null) getTaskIconColor(task) else MaterialTheme.colorScheme.primary

  val status = model?.let { uiState.modelDownloadStatus[it.name] }
  val statusType = status?.status

  // Once the model is present, move straight into the app.
  LaunchedEffect(statusType) {
    if (statusType == ModelDownloadStatusType.SUCCEEDED) onDone()
  }

  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { visible = true }

  Box(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    contentAlignment = Alignment.Center,
  ) {
    AnimatedVisibility(
      visible = visible,
      enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 6 },
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Icon(
          Icons.Rounded.AutoAwesome,
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(64.dp),
        )
        Text(
          "Welcome to Trace",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
        )
        Text(
          "Your private, on-device AI. Chat, look at photos, and study your own notes — all offline, nothing leaves your phone.",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        val downloading =
          statusType == ModelDownloadStatusType.IN_PROGRESS ||
            statusType == ModelDownloadStatusType.UNZIPPING
        when {
          downloading -> {
            val progress = calculateDownloadProgress(status)
            Column(
              modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
              )
              Text(
                if (statusType == ModelDownloadStatusType.UNZIPPING) "Unpacking…"
                else "Downloading model… ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          else -> {
            Button(
              onClick = { if (task != null && model != null) modelManagerViewModel.downloadModel(task, model) },
              modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
              shape = RoundedCornerShape(16.dp),
            ) {
              Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp).padding(end = 8.dp))
              Text("Download the AI model (~3 GB)")
            }
            Text(
              "A one-time download. It runs entirely on your device afterward.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            if (statusType == ModelDownloadStatusType.FAILED) {
              Text(
                "Download failed. Check your connection and try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      }
    }
  }
}
