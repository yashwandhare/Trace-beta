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

package com.trace.app.ui.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.trace.app.data.BuiltInTaskId
import com.trace.app.ui.llmchat.ChatViewWrapper
import com.trace.app.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "VisionCameraScreen"

// Cap on retained video frames: at 1 frame/3s this is ~5 min of recording, past which
// keyframe selection would drop most anyway. Prevents unbounded full-size bitmap growth.
private const val MAX_RECORDED_FRAMES = 100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionCameraScreen(
  modelManagerViewModel: ModelManagerViewModel,
  onNavUp: () -> Unit,
  viewModel: VisionChatViewModel = hiltViewModel()
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val task = modelManagerViewModel.getTaskById(BuiltInTaskId.VISION)

  // Once a photo is captured, switch to the chat view
  var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(task) {
    if (task != null) {
      viewModel.loadSystemPrompt(task)
      viewModel.initTts(context)
    }
  }

  if (capturedBitmap != null && task != null) {
    // ---- Chat View ----
    ChatViewWrapper(
      viewModel = viewModel,
      modelManagerViewModel = modelManagerViewModel,
      taskId = BuiltInTaskId.VISION,
      navigateUp = { capturedBitmap = null }, // Back button returns to camera
      emptyStateComposable = { },
      showImagePicker = true,
    )
  } else {
    // ---- Camera View (matching AI Chat camera style) ----
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewUseCase = remember { Preview.Builder().build() }
    val imageCaptureUseCase = remember {
      val resolutionStrategy = ResolutionStrategy(
        Size(1024, 1024),
        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
      )
      val resolutionSelector = ResolutionSelector.Builder()
        .setResolutionStrategy(resolutionStrategy)
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .build()
      ImageCapture.Builder().setResolutionSelector(resolutionSelector).build()
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraSide by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    // Video mode state
    var isVideoMode by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    // Frames are written from the analyzer (background executor), so the backing list must be
    // thread-safe and must NOT be Compose state. The UI reads [frameCount] (updated on the main
    // thread) instead. Capped so a long recording can't accumulate unbounded full-size bitmaps.
    val recordedFrames = remember { java.util.Collections.synchronizedList(mutableListOf<Bitmap>()) }
    var frameCount by remember { mutableIntStateOf(0) }
    val lastFrameTimeMs = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    var recordingStartMs by remember { mutableStateOf(0L) }
    var recordingElapsedSecs by remember { mutableStateOf(0) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    fun clearRecordedFrames() {
      synchronized(recordedFrames) {
        recordedFrames.forEach { if (!it.isRecycled) it.recycle() }
        recordedFrames.clear()
      }
      frameCount = 0
    }

    // Tick the recording timer every second
    LaunchedEffect(isRecording) {
      if (isRecording) {
        while (isRecording) {
          kotlinx.coroutines.delay(1000)
          if (isRecording) {
            recordingElapsedSecs = ((System.currentTimeMillis() - recordingStartMs) / 1000).toInt()
          }
        }
      } else {
        recordingElapsedSecs = 0
      }
    }

    val imageAnalysisUseCase = remember {
      ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also { analysis ->
          analysis.setAnalyzer(executor) { image ->
            if (isRecording) {
              val currentTime = System.currentTimeMillis()
              // 1 frame every 3 seconds — keeps frame count low for longer videos.
              if (currentTime - lastFrameTimeMs.get() > 3000 && recordedFrames.size < MAX_RECORDED_FRAMES) {
                try {
                  val bitmap = image.toBitmap()
                  val rotation = image.imageInfo.rotationDegrees
                  val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                  val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                  )
                  recordedFrames.add(rotatedBitmap)
                  lastFrameTimeMs.set(currentTime)
                  // Publish count to Compose state on the main thread only.
                  val newCount = recordedFrames.size
                  mainHandler.post { frameCount = newCount }
                } catch (e: Exception) {
                  Log.e(TAG, "Error capturing frame for video", e)
                }
              }
            }
            image.close()
          }
        }
    }

    fun rebindCameraProvider() {
      cameraProvider?.let { provider ->
        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraSide).build()
        try {
          provider.unbindAll()
          val camera = provider.bindToLifecycle(
            lifecycleOwner = lifecycleOwner,
            cameraSelector = cameraSelector,
            previewUseCase,
            imageCaptureUseCase,
            imageAnalysisUseCase,
          )
          cameraControl = camera.cameraControl
        } catch (e: Exception) {
          Log.e(TAG, "Failed to bind camera", e)
        }
      }
    }

    LaunchedEffect(Unit) {
      cameraProvider = ProcessCameraProvider.awaitInstance(context)
      rebindCameraProvider()
    }

    LaunchedEffect(cameraSide) { rebindCameraProvider() }

    DisposableEffect(Unit) {
      onDispose {
        cameraProvider?.unbindAll()
        if (!executor.isShutdown) executor.shutdown()
        // Free any frames that were never handed off to processing.
        synchronized(recordedFrames) {
          recordedFrames.forEach { if (!it.isRecycled) it.recycle() }
          recordedFrames.clear()
        }
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("Vision", color = Color.White) },
          navigationIcon = {
            IconButton(onClick = onNavUp) {
              Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.4f))
        )
      },
      containerColor = Color.Black,
    ) { paddingValues ->
      Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        // Camera preview
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { ctx ->
            PreviewView(ctx).apply {
              this.scaleType = PreviewView.ScaleType.FILL_CENTER
              layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
              )
              previewUseCase.surfaceProvider = this.surfaceProvider
            }
          },
        )

        // Controls at the bottom — same layout as AI Chat camera sheet
        Column(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 40.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          // Recording timer badge
          if (isRecording) {
            val mins = recordingElapsedSecs / 60
            val secs = recordingElapsedSecs % 60
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier
                .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .background(Color.Red, shape = CircleShape)
              )
              Text(
                text = "%02d:%02d".format(mins, secs),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
              )
              Text(
                text = "$frameCount frames",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }

          // Photo / Video toggle
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Photo",
              color = if (!isVideoMode) Color.White else Color.White.copy(alpha = 0.5f),
              style = MaterialTheme.typography.labelLarge,
            )
            Switch(
              checked = isVideoMode,
              onCheckedChange = {
                isVideoMode = it
                if (!it) isRecording = false
              },
            )
            Text(
              text = "Video",
              color = if (isVideoMode) Color.White else Color.White.copy(alpha = 0.5f),
              style = MaterialTheme.typography.labelLarge,
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
          ) {
            // Camera flip button (same as AI Chat camera)
            IconButton(
              onClick = {
                cameraSide = if (cameraSide == CameraSelector.LENS_FACING_BACK)
                  CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
              },
              modifier = Modifier.size(48.dp),
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
              ),
            ) {
              Icon(
                Icons.Rounded.FlipCameraAndroid,
                contentDescription = "Flip camera",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }

            // Main capture / record button (same style as AI Chat camera shutter)
            IconButton(
              onClick = {
                if (isVideoMode) {
                  if (isRecording) {
                    // Stop recording → switch to chat
                    isRecording = false
                    val framesToProcess = synchronized(recordedFrames) { recordedFrames.toList() }
                    if (framesToProcess.isNotEmpty()) {
                      capturedBitmap = framesToProcess.first()
                      val selectedModel = modelManagerViewModel.uiState.value.selectedModel
                      scope.launch {
                        viewModel.processVideoFrames(
                          model = selectedModel,
                          bitmaps = framesToProcess,
                          input = "",
                        )
                      }
                    }
                    // Drop our tracking references without recycling — the frames are now
                    // owned by processVideoFrames / capturedBitmap.
                    synchronized(recordedFrames) { recordedFrames.clear() }
                    frameCount = 0
                  } else {
                    // Start recording — recycle any leftover frames from a prior take.
                    clearRecordedFrames()
                    recordingStartMs = System.currentTimeMillis()
                    lastFrameTimeMs.set(System.currentTimeMillis())
                    isRecording = true
                  }
                } else {
                  // Photo mode — same capture callback as AI Chat
                  imageCaptureUseCase.takePicture(
                    executor,
                    object : ImageCapture.OnImageCapturedCallback() {
                      override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                          val bitmap = image.toBitmap()
                          val rotation = image.imageInfo.rotationDegrees
                          val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                          val rotated = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                          )
                          // Post to main thread before touching Compose state
                          android.os.Handler(android.os.Looper.getMainLooper()).post {
                            capturedBitmap = rotated
                            val selectedModel = modelManagerViewModel.uiState.value.selectedModel
                            scope.launch {
                              viewModel.processCameraFrame(
                                model = selectedModel,
                                bitmap = rotated,
                                input = "",
                              )
                            }
                          }
                        } catch (e: Exception) {
                          Log.e(TAG, "Error processing captured image", e)
                        } finally {
                          image.close()
                        }
                      }

                      override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Failed to capture image", exception)
                      }
                    },
                  )
                }
              },
              modifier = Modifier
                .size(72.dp)
                .border(
                  width = 3.dp,
                  color = Color.White,
                  shape = CircleShape,
                )
                .background(
                  color = if (isRecording) Color.Red.copy(alpha = 0.7f)
                          else Color.White.copy(alpha = 0.15f),
                  shape = CircleShape,
                ),
              colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Transparent),
            ) {
              Icon(
                imageVector = when {
                  isVideoMode && isRecording -> Icons.Rounded.Stop
                  isVideoMode -> Icons.Rounded.FiberManualRecord
                  else -> Icons.Rounded.Camera
                },
                contentDescription = if (isVideoMode) "Record" else "Capture",
                modifier = Modifier.size(40.dp),
                tint = if (isVideoMode && !isRecording) Color.Red else Color.White,
              )
            }

            // Placeholder to balance layout (same width as flip button)
            Spacer(modifier = Modifier.size(48.dp))
          }
        }
      }
    }
  }
}
