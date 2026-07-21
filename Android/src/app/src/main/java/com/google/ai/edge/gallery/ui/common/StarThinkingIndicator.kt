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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R

/**
 * A "thinking" indicator: a short row of Trace star marks that fade in and out
 * one after another (like typing dots, but with the brand star). Used while the
 * model is generating.
 */
@Composable
fun StarThinkingIndicator(
  modifier: Modifier = Modifier,
  count: Int = 3,
  starSize: androidx.compose.ui.unit.Dp = 14.dp,
) {
  val transition = rememberInfiniteTransition(label = "star-thinking")
  val cycleMs = 1200
  val tint = MaterialTheme.colorScheme.onSurfaceVariant

  Row(modifier = modifier) {
    for (i in 0 until count) {
      // Each star peaks at a staggered point in the shared cycle, so they light
      // up one by one and fade back down.
      val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.25f,
        animationSpec =
          infiniteRepeatable(
            animation =
              keyframes {
                durationMillis = cycleMs
                val peak = (cycleMs / count) * i
                0.25f at peak.coerceIn(0, cycleMs) using LinearEasing
                1f at (peak + cycleMs / (count * 2)).coerceIn(0, cycleMs) using LinearEasing
                0.25f at (peak + cycleMs / count).coerceIn(0, cycleMs) using LinearEasing
              },
            repeatMode = RepeatMode.Restart,
          ),
        label = "star-$i-alpha",
      )
      Image(
        painter = painterResource(R.drawable.icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(starSize).alpha(alpha),
      )
      if (i < count - 1) {
        Spacer(Modifier.size(4.dp))
      }
    }
  }
}
