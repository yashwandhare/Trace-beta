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

package com.trace.app

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.trace.app.ui.modelmanager.ModelManagerViewModel
import com.trace.app.ui.theme.GalleryTheme
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  private val modelManagerViewModel: ModelManagerViewModel by viewModels()
  private var splashScreenAboutToExit: Boolean = false
  private var contentSet: Boolean = false

  override fun onCreate(savedInstanceState: Bundle?) {
    // We intentionally pass null to discard the saved instance state bundle.
    // This prevents Jetpack Compose from automatically restoring the previous screen
    // and forces the app to start cleanly on the Home Screen after an OS kill.
    super.onCreate(null)

    // Dump intent extras only in debug builds — these can contain sensitive values.
    if (BuildConfig.DEBUG) {
      intent.extras?.let { extras ->
        for (key in extras.keySet()) {
          Log.d(TAG, "onCreate Extra -> Key: $key, Value: ${extras.get(key)}")
        }
      }
    }

    // Convert deeplink data extras to intent data for GalleryNavGraph to pick up
    intent.getStringExtra("deeplink")?.let { link ->
      Log.d(TAG, "onCreate: Found deeplink extra: $link")
      if (link.startsWith("http://") || link.startsWith("https://")) {
        val browserIntent = Intent(Intent.ACTION_VIEW, link.toUri())
        startActivity(browserIntent)
      } else {
        intent.data = link.toUri()
      }
    }

    fun setContent() {
      if (contentSet) {
        return
      }

      setContent {
        GalleryTheme {
          Surface(modifier = Modifier.fillMaxSize()) {
            GalleryApp(modelManagerViewModel = modelManagerViewModel)

            // Opening: a static Trace mark on an off-white screen shown for a
            // brief moment, then faded out to reveal the app. No grow animation.
            var startFade by remember { mutableStateOf(false) }
            var openDone by remember { mutableStateOf(false) }
            val openAlpha by animateFloatAsState(
              targetValue = if (startFade) 0f else 1f,
              animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
              label = "openAlpha",
            )
            LaunchedEffect(Unit) {
              delay(550)   // hold the mark on screen
              startFade = true
              delay(260)
              openDone = true
            }
            if (!openDone) {
              Box(
                modifier =
                  Modifier.fillMaxSize()
                    .graphicsLayer { alpha = openAlpha }
                    .background(Color(0xFFF5F5F0)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  painter = painterResource(R.drawable.icon),
                  contentDescription = null,
                  tint = Color.Unspecified,
                  modifier = Modifier.size(96.dp),
                )
              }
            }
          }
        }
      }

      @OptIn(ExperimentalApi::class)
      ExperimentalFlags.enableBenchmark = false

      contentSet = true
    }

    modelManagerViewModel.loadModelAllowlist()

    // Show splash screen.
    val splashScreen = installSplashScreen()

    // Set the content when the system-provided splash screen is not shown.
    //
    // This is necessary on some Android versions where the splash screen is optimized away (e.g.,
    // after a force-quit) to ensure the main content is displayed immediately and correctly.
    lifecycleScope.launch {
      delay(1000)
      if (!splashScreenAboutToExit) {
        setContent()
      }
    }

    // Hand off from the system splash to the app immediately: the opening
    // animation (star grows from center) lives in Compose (see setContent), so
    // the splash should exit right away rather than run its own icon animation.
    splashScreen.setOnExitAnimationListener { splashScreenView ->
      splashScreenAboutToExit = true
      setContent()
      splashScreenView.remove()
    }

    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // Fix for three-button nav not properly going edge-to-edge.
      // See: https://issuetracker.google.com/issues/298296168
      window.isNavigationBarContrastEnforced = false
    }
    // Keep the screen on while the app is running for better demo experience.
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)

    // Dump intent extras only in debug builds — these can contain sensitive values.
    if (BuildConfig.DEBUG) {
      intent.extras?.let { extras ->
        for (key in extras.keySet()) {
          Log.d(TAG, "onNewIntent Extra -> Key: $key, Value: ${extras.get(key)}")
        }
      }
    }

    intent.getStringExtra("deeplink")?.let { link ->
      Log.d(TAG, "onNewIntent: Found deeplink extra: $link")
      if (link.startsWith("http://") || link.startsWith("https://")) {
        val browserIntent = Intent(Intent.ACTION_VIEW, link.toUri())
        startActivity(browserIntent)
      } else {
        intent.data = link.toUri()
      }
    }
  }

  override fun onResume() {
    super.onResume()

    firebaseAnalytics?.logEvent(
      "app_open",
      bundleOf(
        "app_version" to BuildConfig.VERSION_NAME,
        "os_version" to Build.VERSION.SDK_INT.toString(),
        "device_model" to Build.MODEL,
      ),
    )
  }

  companion object {
    private const val TAG = "AGMainActivity"
  }
}
