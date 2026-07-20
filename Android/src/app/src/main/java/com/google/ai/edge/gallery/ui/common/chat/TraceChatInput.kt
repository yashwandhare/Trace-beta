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

package com.google.ai.edge.gallery.ui.common.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Shared, configurable message-input bar used across Trace's chat-style screens
 * (Notes today; the reference visual language matches AI Chat's [MessageInputText]).
 *
 * Deliberately lighter than [MessageInputText] — it carries the common shape
 * (bordered rounded container, transparent text field, inline send, an attach
 * affordance) plus two slots so a screen can inject its own extras without this
 * component knowing about them:
 *   - [leadingContent]: a row above the field (e.g. attached-source chips).
 *   - [quickActions]: a row below the field (e.g. Quiz / Summarize).
 *
 * Heavy chat-only features (camera capture, screen-capture intent routing,
 * skills/MCP pickers, voice PTT) intentionally stay in [MessageInputText]; this
 * is the shared core, not a replacement for that screen.
 */
@Composable
fun TraceChatInput(
  value: String,
  onValueChange: (String) -> Unit,
  onSendText: (String) -> Unit,
  accent: Color,
  modifier: Modifier = Modifier,
  placeholder: String = "Type a message…",
  enabled: Boolean = true,
  inProgress: Boolean = false,
  showStopButton: Boolean = false,
  onStop: () -> Unit = {},
  showAttach: Boolean = false,
  onAttach: () -> Unit = {},
  leadingContent: (@Composable () -> Unit)? = null,
  quickActions: (@Composable () -> Unit)? = null,
  // Rendered inside the input row, just left of the send button (e.g. a voice mic).
  trailingAction: (@Composable () -> Unit)? = null,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    tonalElevation = 3.dp,
    modifier = modifier,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp)
          .navigationBarsPadding()
          .imePadding(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      leadingContent?.invoke()

      // Bordered input container (matches AI Chat's MessageInputText shape).
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
      ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          if (showAttach) {
            IconButton(onClick = onAttach, enabled = enabled) {
              Icon(
                Icons.Outlined.Add,
                contentDescription = "Attach",
                tint = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            Spacer(Modifier.width(12.dp))
          }

          TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            placeholder = { Text(placeholder) },
            maxLines = 4,
            colors =
              TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
              ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions =
              KeyboardActions(onSend = { if (canSend(value, enabled, inProgress)) onSendText(value) }),
          )

          val sendEnabled = canSend(value, enabled, inProgress)
          trailingAction?.invoke()
          Box(modifier = Modifier.padding(end = 6.dp)) {
            if (showStopButton && inProgress) {
              IconButton(
                onClick = onStop,
                modifier = Modifier.size(44.dp).background(accent, CircleShape),
              ) {
                Icon(Icons.Rounded.Stop, contentDescription = "Stop", tint = Color.Black, modifier = Modifier.size(20.dp))
              }
            } else {
              IconButton(
                onClick = { onSendText(value) },
                enabled = sendEnabled,
                modifier =
                  Modifier.size(44.dp)
                    .background(
                      if (sendEnabled) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
                      CircleShape,
                    ),
              ) {
                Icon(
                  Icons.AutoMirrored.Rounded.Send,
                  contentDescription = "Send",
                  tint = if (sendEnabled) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }
      }

      quickActions?.invoke()
    }
  }
}

private fun canSend(value: String, enabled: Boolean, inProgress: Boolean): Boolean =
  enabled && !inProgress && value.isNotBlank()
