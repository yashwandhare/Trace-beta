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

package com.google.ai.edge.gallery.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.ai.edge.gallery.data.Task

// Cohesive single-accent scheme: modules no longer carry their own hue. Icons
// and accents use the theme's primary (iOS blue); tinted backgrounds use a
// neutral container. This intentionally collapses the old per-module palette.

@Composable
fun getTaskBgColor(task: Task): Color = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
fun getTaskBgGradientColors(task: Task): List<Color> =
  listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)

@Composable
fun getTaskIconColor(task: Task): Color = MaterialTheme.colorScheme.primary

@Composable
fun getTaskIconColor(index: Int): Color = MaterialTheme.colorScheme.primary
