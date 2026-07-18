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

package com.google.ai.edge.gallery.ui.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.llmchat.ChatViewWrapper
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.util.concurrent.Executors

private const val TAG = "VisionCameraScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionCameraScreen(
  modelManagerViewModel: ModelManagerViewModel,
  onNavUp: () -> Unit,
  viewModel: VisionChatViewModel = hiltViewModel()
) {
  var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
  val context = LocalContext.current
  val task = modelManagerViewModel.getTaskById(BuiltInTaskId.VISION)
  
  LaunchedEffect(task) {
    if (task != null) {
      viewModel.loadSystemPrompt(task)
      viewModel.initTts(context)
    }
  }

  if (capturedBitmap != null && task != null) {
    val selectedModel = modelManagerViewModel.uiState.value.selectedModel
    // Show ChatViewWrapper with the VisionChatViewModel
    ChatViewWrapper(
      viewModel = viewModel,
      modelManagerViewModel = modelManagerViewModel,
      taskId = BuiltInTaskId.VISION,
      navigateUp = { capturedBitmap = null }, // Back button goes back to camera
      emptyStateComposable = { }, // Keep it empty for overlay
      showImagePicker = true
    )
  } else {
    // Show Camera View
    Scaffold(
      topBar = {
        TopAppBar(
          title = { },
          navigationIcon = {
            IconButton(onClick = onNavUp) {
              Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
      }
    ) { paddingValues ->
      Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val previewUseCase = remember { Preview.Builder().build() }
        val imageCaptureUseCase = remember {
          val preferredSize = Size(1024, 1024)
          val resolutionStrategy =
            ResolutionStrategy(
              preferredSize,
              ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            )
          val resolutionSelector =
            ResolutionSelector.Builder()
              .setResolutionStrategy(resolutionStrategy)
              .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
              .build()

          ImageCapture.Builder().setResolutionSelector(resolutionSelector).build()
        }
        var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
        var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
        val cameraSide by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
        val executor = remember { Executors.newSingleThreadExecutor() }

        var isVideoMode by remember { mutableStateOf(false) }
        var isRecording by remember { mutableStateOf(false) }
        val recordedFrames = remember { mutableStateListOf<Bitmap>() }
        var lastFrameTimeMs by remember { mutableStateOf(0L) }

        val imageAnalysisUseCase = remember {
          ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
              setAnalyzer(executor) { image ->
                if (isRecording) {
                  val currentTime = System.currentTimeMillis()
                  // Record 1 frame per second
                  if (currentTime - lastFrameTimeMs > 1000) {
                    val bitmap = image.toBitmap()
                    val rotation = image.imageInfo.rotationDegrees
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    recordedFrames.add(rotatedBitmap)
                    lastFrameTimeMs = currentTime
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
              val camera =
                provider.bindToLifecycle(
                  lifecycleOwner = lifecycleOwner,
                  cameraSelector = cameraSelector,
                  previewUseCase,
                  imageCaptureUseCase,
                  imageAnalysisUseCase
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

        // UI Controls at the bottom
        Column(
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Photo / Video Toggle
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Photo", color = if (!isVideoMode) Color.White else Color.Gray)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
              checked = isVideoMode,
              onCheckedChange = { 
                isVideoMode = it
                if (!it) isRecording = false // Stop recording if switched
              }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Video", color = if (isVideoMode) Color.White else Color.Gray)
          }

          // Capture / Record Button
          IconButton(
            onClick = {
              if (isVideoMode) {
                if (isRecording) {
                  // Stop recording
                  isRecording = false
                  if (recordedFrames.isNotEmpty()) {
                    val framesToProcess = recordedFrames.toList()
                    capturedBitmap = framesToProcess.first() // Just to trigger ChatViewWrapper
                    val selectedModel = modelManagerViewModel.uiState.value.selectedModel
                    viewModel.processVideoFrames(
                      model = selectedModel,
                      bitmaps = framesToProcess,
                      input = ""
                    )
                    recordedFrames.clear()
                  }
                } else {
                  // Start recording
                  recordedFrames.clear()
                  lastFrameTimeMs = System.currentTimeMillis()
                  isRecording = true
                }
              } else {
                // Photo Mode
                imageCaptureUseCase.takePicture(
                  executor,
                  object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                      val bitmap = image.toBitmap()
                      val rotation = image.imageInfo.rotationDegrees
                      image.close()

                      val matrix = Matrix()
                      matrix.postRotate(rotation.toFloat())
                      val rotatedBitmap =
                        Bitmap.createBitmap(
                          bitmap,
                          0,
                          0,
                          bitmap.width,
                          bitmap.height,
                          matrix,
                          true,
                        )
                      capturedBitmap = rotatedBitmap
                      
                      // Trigger processCameraFrame immediately after capture
                      val selectedModel = modelManagerViewModel.uiState.value.selectedModel
                      viewModel.processCameraFrame(
                        model = selectedModel,
                        bitmap = rotatedBitmap,
                        input = "" // Default input used in ViewModel ("What do you see?")
                      )
                    }

                    override fun onError(exception: ImageCaptureException) {
                      Log.e(TAG, "Failed to capture image", exception)
                    }
                  },
                )
              }
            },
            modifier = Modifier
              .padding(top = 16.dp)
              .size(80.dp)
              .background(if (isRecording) Color.Red.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f), CircleShape),
          ) {
            Icon(
              imageVector = if (isVideoMode) Icons.Rounded.Videocam else Icons.Rounded.Camera,
              contentDescription = if (isVideoMode) "Record" else "Capture",
              modifier = Modifier.size(48.dp),
              tint = Color.Black
            )
          }
        }
      }
    }
  }
}
