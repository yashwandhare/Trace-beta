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

package com.google.ai.edge.gallery.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared empty/landing state for every module: accent-tinted icon, title, a
 * one-to-two-line description, and optional tappable suggested-prompt chips.
 * One composable so AI Chat, Vision, and Notes read identically (same alignment,
 * typography, spacing).
 */
@Composable
fun ModuleEmptyState(
  icon: ImageVector,
  accent: Color,
  title: String,
  description: String,
  modifier: Modifier = Modifier,
  suggestions: List<String> = emptyList(),
  onSuggestionClick: (String) -> Unit = {},
) {
  Column(
    modifier = modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(48.dp))
    Text(
      title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 16.dp),
    )
    Text(
      description,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 8.dp),
    )
    suggestions.forEach { suggestion ->
      Surface(
        onClick = { onSuggestionClick(suggestion) },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
      ) {
        Text(
          suggestion,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}
