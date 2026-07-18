/*
 * Trace — Push-to-Talk Button UI (Phase 1, Dev B)
 *
 * Pure Compose UI component. Three visual states:
 *   Idle       → ready, mic icon, accent border
 *   Listening  → recording, animated pulse ring, red tint
 *   Processing → waiting for model response, spinner overlay
 *
 * No audio capture logic lives here. Dev A wires the actual recording
 * start/stop; this composable only exposes callbacks and reacts to the
 * PttState it is given.
 */

package com.google.ai.edge.gallery.ui.voiceinput

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

/**
 * The three observable states of the push-to-talk button.
 *
 * Dev A drives transitions between these states via [PushToTalkButton]'s
 * [state] parameter; this composable only renders them.
 */
enum class PttState {
  /** Button is at rest — mic ready, press-and-hold to record. */
  IDLE,

  /** Recording is in progress — mic is hot, release to stop. */
  LISTENING,

  /** Audio sent to Gemma; waiting for the first token of a response. */
  PROCESSING,
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

/**
 * Push-to-talk button with animated idle / listening / processing states.
 *
 * ### Usage
 * ```kotlin
 * var pttState by remember { mutableStateOf(PttState.IDLE) }
 *
 * PushToTalkButton(
 *   state = pttState,
 *   onPressStart  = { pttState = PttState.LISTENING  /* Dev A starts capture */ },
 *   onPressEnd    = { pttState = PttState.PROCESSING /* Dev A stops capture  */ },
 * )
 * ```
 *
 * @param state       Current visual state — controlled externally by Dev A's logic.
 * @param onPressStart  Called when the user first presses the button (finger down). Dev A
 *                      should start audio capture here.
 * @param onPressEnd    Called when the user lifts their finger. Dev A should stop capture
 *                      and route audio to Gemma here.
 * @param enabled     When false the button is visually dimmed and unresponsive.
 * @param size        Diameter of the button circle. Defaults to 72.dp.
 * @param modifier    Standard Compose modifier.
 */
@Composable
fun PushToTalkButton(
  state: PttState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  size: Dp = 72.dp,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier,
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.size(size + 32.dp), // extra room for the pulse ring
    ) {
      // ── Pulse ring (LISTENING only) ──────────────────────────────────────
      if (state == PttState.LISTENING) {
        PulseRing(buttonSize = size)
      }

      // ── Main circle button ───────────────────────────────────────────────
      val backgroundColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        state == PttState.LISTENING -> Color(0xFFD32F2F)   // vivid red while recording
        state == PttState.PROCESSING -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primaryContainer
      }

      val iconTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        state == PttState.LISTENING -> Color.White
        state == PttState.PROCESSING -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimaryContainer
      }

      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .semantics {
              contentDescription =
                when (state) {
                  PttState.IDLE -> "Push and hold to speak"
                  PttState.LISTENING -> "Listening — release to send"
                  PttState.PROCESSING -> "Processing your voice input"
                }
            }
            .then(
              if (enabled && state != PttState.PROCESSING) {
                Modifier.clickable { onClick() }
              } else {
                Modifier
              }
            ),
      ) {
        when (state) {
          PttState.PROCESSING -> {
            // Spinner overlay when waiting for Gemma.
            CircularProgressIndicator(
              modifier = Modifier.size(size * 0.5f),
              strokeWidth = 3.dp,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          PttState.LISTENING -> {
            Icon(
              imageVector = Icons.Rounded.Mic,
              contentDescription = null,
              tint = iconTint,
              modifier = Modifier.size(size * 0.45f),
            )
          }
          PttState.IDLE -> {
            Icon(
              imageVector = if (enabled) Icons.Rounded.Mic else Icons.Rounded.MicOff,
              contentDescription = null,
              tint = iconTint,
              modifier = Modifier.size(size * 0.45f),
            )
          }
        }
      }
    }

    // ── Label beneath button ─────────────────────────────────────────────
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text =
        when (state) {
          PttState.IDLE -> if (enabled) "Hold to speak" else "Mic unavailable"
          PttState.LISTENING -> "Listening…"
          PttState.PROCESSING -> "Processing…"
        },
      style = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
      ),
      color =
        when (state) {
          PttState.LISTENING -> Color(0xFFD32F2F)
          else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
  }
}

// ---------------------------------------------------------------------------
// Pulse ring animation
// ---------------------------------------------------------------------------

/**
 * An infinitely-repeating, scaling ring shown behind the button while [PttState.LISTENING].
 */
@Composable
private fun PulseRing(buttonSize: Dp) {
  val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
  val scale by
    infiniteTransition.animateFloat(
      initialValue = 1f,
      targetValue = 1.35f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "pulse_scale",
    )
  val alpha by
    infiniteTransition.animateFloat(
      initialValue = 0.5f,
      targetValue = 0f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "pulse_alpha",
    )

  Box(
    modifier =
      Modifier
        .size(buttonSize)
        .scale(scale)
        .clip(CircleShape)
        .background(Color(0xFFD32F2F).copy(alpha = alpha)),
  )
}
