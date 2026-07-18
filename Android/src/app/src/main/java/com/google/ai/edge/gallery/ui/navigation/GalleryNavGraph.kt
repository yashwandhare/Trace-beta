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

package com.google.ai.edge.gallery.ui.navigation

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.ai.edge.gallery.GalleryEvent
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.customtasks.common.CustomTaskDataForBuiltinTask
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.isLegacyTasks
import com.google.ai.edge.gallery.firebaseAnalytics
import com.google.ai.edge.gallery.ui.home.HomeScreen
import com.google.ai.edge.gallery.ui.common.ErrorDialog
import com.google.ai.edge.gallery.ui.common.ModelPageAppBar
import com.google.ai.edge.gallery.ui.common.chat.ModelDownloadStatusInfoPanel
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AGGalleryNavGraph"
private const val ROUTE_HOMESCREEN = "homepage"
private const val ROUTE_MODEL = "route_model"
const val ROUTE_BENCHMARK = "benchmark"
private const val ENTER_ANIMATION_DURATION_MS = 500
private val ENTER_ANIMATION_EASING = EaseOutExpo
private const val ENTER_ANIMATION_DELAY_MS = 100

private const val EXIT_ANIMATION_DURATION_MS = 500
private val EXIT_ANIMATION_EASING = EaseOutExpo

private fun enterTween(): FiniteAnimationSpec<IntOffset> {
  return tween(
    ENTER_ANIMATION_DURATION_MS,
    easing = ENTER_ANIMATION_EASING,
    delayMillis = ENTER_ANIMATION_DELAY_MS,
  )
}

private fun exitTween(): FiniteAnimationSpec<IntOffset> {
  return tween(EXIT_ANIMATION_DURATION_MS, easing = EXIT_ANIMATION_EASING)
}

private fun AnimatedContentTransitionScope<*>.slideEnter(): EnterTransition {
  return slideIntoContainer(
    animationSpec = enterTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Left,
  )
}

private fun AnimatedContentTransitionScope<*>.slideExit(): ExitTransition {
  return slideOutOfContainer(
    animationSpec = exitTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Right,
  )
}

private fun AnimatedContentTransitionScope<*>.slideUpEnter(): EnterTransition {
  return slideIntoContainer(
    animationSpec = enterTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Up,
  )
}

private fun AnimatedContentTransitionScope<*>.slideDownExit(): ExitTransition {
  return slideOutOfContainer(
    animationSpec = exitTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Down,
  )
}

/** Navigation routes. */
@Composable
fun GalleryNavHost(
  navController: NavHostController,
  modifier: Modifier = Modifier,
  modelManagerViewModel: ModelManagerViewModel,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  var pickedTask by remember { mutableStateOf<Task?>(null) }
  var enableHomeScreenAnimation by remember { mutableStateOf(true) }
  var lastNavigatedModelName = remember { "" }
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()

  // Track whether app is in foreground.
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START,
        Lifecycle.Event.ON_RESUME -> {
          modelManagerViewModel.setAppInForeground(foreground = true)
        }
        Lifecycle.Event.ON_STOP,
        Lifecycle.Event.ON_PAUSE -> {
          modelManagerViewModel.setAppInForeground(foreground = false)
        }
        else -> {
          /* Do nothing for other events */
        }
      }
    }

    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  NavHost(
    navController = navController,
    startDestination = ROUTE_HOMESCREEN,
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
  ) {
    // Home screen.
    composable(route = ROUTE_HOMESCREEN) {
      Box(modifier = modifier.fillMaxSize()) {
        HomeScreen(
          modelManagerViewModel = modelManagerViewModel,
          enableAnimation = enableHomeScreenAnimation,
          navigateToTaskScreen = { task ->
            pickedTask = task
            // Navigate directly to the model page, picking the first model.
            val firstModel = task.models.firstOrNull()
            if (firstModel != null) {
              navController.navigate("$ROUTE_MODEL/${task.id}/${firstModel.name}")
            }
            firebaseAnalytics?.logEvent(
              GalleryEvent.CAPABILITY_SELECT.id,
              Bundle().apply { putString("capability_name", task.id) },
            )
          },
          onModelsClicked = { 
            navController.navigate("$ROUTE_BENCHMARK/Gemma-4-E2B-it")
          },
          onNotificationsClicked = { /* notifications removed */ },
          gm4 = true,
        )
      }
    }

    // Model page — hosts LlmChatScreen (and any other task screens).
    composable(
      route = "$ROUTE_MODEL/{taskId}/{modelName}?query={query}",
      arguments =
        listOf(
          navArgument("taskId") { type = NavType.StringType },
          navArgument("modelName") { type = NavType.StringType },
          navArgument("query") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
          },
        ),
      enterTransition = { slideEnter() },
      exitTransition = { slideExit() },
    ) { backStackEntry ->
      val modelName = backStackEntry.arguments?.getString("modelName") ?: ""
      val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
      val queryParam = backStackEntry.arguments?.getString("query")
      val scope = rememberCoroutineScope()
      val context = LocalContext.current

      modelManagerViewModel.getModelByName(name = modelName)?.let { initialModel ->
        if (lastNavigatedModelName != modelName) {
          modelManagerViewModel.selectModel(initialModel)
          lastNavigatedModelName = modelName
        }

        val customTask = modelManagerViewModel.getCustomTaskByTaskId(id = taskId)
        if (customTask != null) {
          if (isLegacyTasks(customTask.task.id)) {
            customTask.MainScreen(
              data =
                CustomTaskDataForBuiltinTask(
                  modelManagerViewModel = modelManagerViewModel,
                  onNavUp = {
                    lastNavigatedModelName = ""
                    navController.navigateUp()
                  },
                  initialQuery = queryParam,
                  onBenchmarkScreenClicked = { clickedModel ->
                    navController.navigate("$ROUTE_BENCHMARK/${clickedModel.name}")
                  }
                )
            )
          } else {
            var disableAppBarControls by remember { mutableStateOf(false) }
            var hideTopBar by remember { mutableStateOf(false) }
            var customNavigateUpCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
            CustomTaskScreen(
              task = customTask.task,
              modelManagerViewModel = modelManagerViewModel,
              onNavigateUp = {
                if (customNavigateUpCallback != null) {
                  customNavigateUpCallback?.invoke()
                } else {
                  lastNavigatedModelName = ""
                  navController.navigateUp()
                  for (curModel in customTask.task.models) {
                    val instanceToCleanUp = curModel.instance
                    scope.launch(Dispatchers.Default) {
                      modelManagerViewModel.cleanupModel(
                        context = context,
                        task = customTask.task,
                        model = curModel,
                        instanceToCleanUp = instanceToCleanUp,
                      )
                    }
                  }
                }
              },
              disableAppBarControls = disableAppBarControls,
              hideTopBar = hideTopBar,
              useThemeColor = customTask.task.useThemeColor,
            ) { bottomPadding ->
              customTask.MainScreen(
                data =
                  CustomTaskData(
                    modelManagerViewModel = modelManagerViewModel,
                    bottomPadding = bottomPadding,
                    setAppBarControlsDisabled = { disableAppBarControls = it },
                    setTopBarVisible = { hideTopBar = !it },
                    setCustomNavigateUpCallback = { customNavigateUpCallback = it },
                  )
              )
            }
          }
        }
      }
    }

    // Benchmark screen.
    composable(
      route = "$ROUTE_BENCHMARK/{modelName}",
      arguments = listOf(navArgument("modelName") { type = NavType.StringType }),
      enterTransition = { slideEnter() },
      exitTransition = { slideExit() },
    ) { backStackEntry ->
      val modelName = backStackEntry.arguments?.getString("modelName") ?: ""
      modelManagerViewModel.getModelByName(name = modelName)?.let { model ->
        com.google.ai.edge.gallery.ui.benchmark.BenchmarkScreen(
          initialModel = model,
          modelManagerViewModel = modelManagerViewModel,
          onBackClicked = { navController.navigateUp() }
        )
      }
    }
  }

  // Handle incoming intents for deep links
  val intent = androidx.activity.compose.LocalActivity.current?.intent
  val data = intent?.data
  // Wait until the model manager has been initialized and the tasks are available.
  if (data != null && modelManagerUiState.tasks.isNotEmpty()) {
    intent.data = null
    val uriStr = data.toString()
    Log.d(TAG, "navigation link clicked: $data")
    // 1. Precise model deep links: com.google.ai.edge.gallery://model/<taskId>/<modelName>
    if (uriStr.startsWith("com.google.ai.edge.gallery://model/")) {
      if (data.pathSegments.size >= 2) {
        val taskId = data.pathSegments.get(data.pathSegments.size - 2)
        val modelName = data.pathSegments.last()
        val queryStr = data.getQueryParameter("query")
        modelManagerViewModel.getModelByName(name = modelName)?.let { model ->
          val route =
            if (!queryStr.isNullOrEmpty()) {
              "$ROUTE_MODEL/${taskId}/${model.name}?query=${Uri.encode(queryStr)}"
            } else {
              "$ROUTE_MODEL/${taskId}/${model.name}"
            }
          navController.navigate(route)
        }
      } else {
        Log.e(TAG, "Malformed deep link URI received: $data")
      }
    } else {
      // 2. Dynamic task-level deep links: com.google.ai.edge.gallery://<taskId>
      val host = data.host
      if (host != null) {
        val queryStr = data.getQueryParameter("query")
        val task = modelManagerUiState.tasks.find { it.id == host }
        if (task != null) {
          // Pick the first successfully downloaded model or the default active model for this task
          val defaultModel =
            task.models.firstOrNull { model ->
              modelManagerUiState.modelDownloadStatus[model.name]?.status ==
                ModelDownloadStatusType.SUCCEEDED
            } ?: task.models.firstOrNull()

          if (defaultModel != null) {
            val route =
              if (!queryStr.isNullOrEmpty()) {
                "$ROUTE_MODEL/${task.id}/${defaultModel.name}?query=${Uri.encode(queryStr)}"
              } else {
                "$ROUTE_MODEL/${task.id}/${defaultModel.name}"
              }
            navController.navigate(route)
          } else {
            Log.e(TAG, "No available model found for task: $host")
          }
        }
      }
    }
  }
}

@Composable
private fun CustomTaskScreen(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  disableAppBarControls: Boolean,
  hideTopBar: Boolean,
  useThemeColor: Boolean,
  onNavigateUp: () -> Unit,
  content: @Composable (bottomPadding: Dp) -> Unit,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var navigatingUp by remember { mutableStateOf(false) }
  var showErrorDialog by remember { mutableStateOf(false) }
  var appBarHeight by remember { mutableIntStateOf(0) }

  val handleNavigateUp = {
    navigatingUp = true
    onNavigateUp()
  }

  // Handle system's edge swipe.
  BackHandler { handleNavigateUp() }

  // Initialize model when model/download state changes.
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(curDownloadStatus, selectedModel.name) {
    if (!navigatingUp) {
      if (curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
        Log.d(
          TAG,
          "Initializing model '${selectedModel.name}' from CustomTaskScreen launched effect",
        )
        modelManagerViewModel.initializeModel(context, task = task, model = selectedModel)
      }
    }
  }

  val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[selectedModel.name]
  LaunchedEffect(modelInitializationStatus) {
    showErrorDialog = modelInitializationStatus?.status == ModelInitializationStatusType.ERROR
  }

  Scaffold(
    topBar = {
      AnimatedVisibility(
        !hideTopBar,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
      ) {
        ModelPageAppBar(
          task = task,
          model = selectedModel,
          modelManagerViewModel = modelManagerViewModel,
          inProgress = disableAppBarControls,
          modelPreparing = disableAppBarControls,
          shouldShowHistoryButton = false,
          useThemeColor = useThemeColor,
          modifier =
            Modifier.onGloballyPositioned { coordinates -> appBarHeight = coordinates.size.height },
          hideModelSelector = task.models.size <= 1,
          onConfigChanged = { _, _ -> },
          onBackClicked = { handleNavigateUp() },
          onModelSelected = { prevModel, newSelectedModel ->
            val instanceToCleanUp = prevModel.instance
            scope.launch(Dispatchers.Default) {
              // Clean up prev model.
              if (prevModel.name != newSelectedModel.name) {
                modelManagerViewModel.cleanupModel(
                  context = context,
                  task = task,
                  model = prevModel,
                  instanceToCleanUp = instanceToCleanUp,
                )
              }

              // Update selected model.
              Log.d(TAG, "from model picker. new: ${newSelectedModel.name}")
              modelManagerViewModel.selectModel(model = newSelectedModel)
            }
          },
        )
      }
    }
  ) { innerPadding ->
    // Calculate the target height in Dp for the content's top padding.
    val targetPaddingDp =
      if (!hideTopBar && appBarHeight > 0) {
        // Convert measured pixel height to Dp
        with(LocalDensity.current) { appBarHeight.toDp() }
      } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
      }

    // Animate the actual top padding value.
    val animatedTopPadding by
      animateDpAsState(
        targetValue = targetPaddingDp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "TopPaddingAnimation",
      )

    Box(
      modifier =
        Modifier.padding(
          top = if (!hideTopBar) innerPadding.calculateTopPadding() else animatedTopPadding,
          start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
          end = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
        )
    ) {
      val curModelDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
      AnimatedContent(
        targetState = curModelDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
      ) { targetState ->
        when (targetState) {
          // Main UI when model is downloaded.
          true -> content(innerPadding.calculateBottomPadding())
          // Model download
          false ->
            ModelDownloadStatusInfoPanel(
              model = selectedModel,
              task = task,
              modelManagerViewModel = modelManagerViewModel,
            )
        }
      }
    }
  }

  if (showErrorDialog) {
    ErrorDialog(
      error = modelInitializationStatus?.error ?: "",
      onDismiss = {
        showErrorDialog = false
        onNavigateUp()
      },
    )
  }
}
