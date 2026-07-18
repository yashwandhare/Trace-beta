/*
 * Trace — PttOverlay (Phase 1, Dev B)
 *
 * Hosts the PushToTalkButton inside the LlmChat screen using the
 * `composableBelowMessageList` slot. Keeps PTT state local here;
 * Dev A replaces the stub callbacks with real audio-capture calls.
 */

package com.google.ai.edge.gallery.ui.voiceinput

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Floating PTT button overlay rendered inside the chat panel.
 *
 * Drop this into [LlmChatScreen]'s `composableBelowMessageList` parameter:
 * ```kotlin
 * LlmChatScreen(
 *   ...
 *   composableBelowMessageList = { _ -> PttOverlay() }
 * )
 * ```
 *
 * ### Dev A integration points
 * Replace the TODO stubs with real audio-capture calls once your
 * push-to-talk capture layer is ready.
 *
 * @param onStartRecording  Called by button press-start — Dev A starts mic capture here.
 * @param onStopRecording   Called by button press-end — Dev A stops capture, routes audio to Gemma.
 * @param externalPttState  Optional: Dev A can drive the state externally (e.g. set PROCESSING
 *                          once audio is submitted). When null the overlay manages state locally.
 */
@Composable
fun PttOverlay(
  modifier: Modifier = Modifier,
  onStartRecording: () -> Unit = { /* TODO Dev A: start audio capture */ },
  onStopRecording: () -> Unit = { /* TODO Dev A: stop capture, send to Gemma */ },
  externalPttState: PttState? = null,
) {
  // Local state — Dev A can override by passing externalPttState.
  var localState by remember { mutableStateOf(PttState.IDLE) }
  val state = externalPttState ?: localState

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.BottomCenter,
  ) {
    PushToTalkButton(
      state = state,
      onPressStart = {
        if (externalPttState == null) localState = PttState.LISTENING
        onStartRecording()
      },
      onPressEnd = {
        if (externalPttState == null) localState = PttState.PROCESSING
        onStopRecording()
        // Auto-reset to IDLE after stub delay when no external state drives us.
        if (externalPttState == null) localState = PttState.IDLE
      },
      modifier = Modifier.padding(bottom = 88.dp), // sit above the text input bar
    )
  }
}
