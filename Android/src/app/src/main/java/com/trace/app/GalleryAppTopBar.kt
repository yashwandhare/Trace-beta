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

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.trace.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trace.app.data.AppBarAction
import com.trace.app.data.AppBarActionType

/** The top app bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryTopAppBar(
  title: String,
  modifier: Modifier = Modifier,
  leftAction: AppBarAction? = null,
  rightAction: AppBarAction? = null,
  scrollBehavior: TopAppBarScrollBehavior? = null,
  subtitle: String = "",
) {
  val titleColor = MaterialTheme.colorScheme.onSurface
  CenterAlignedTopAppBar(
    title = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (title == stringResource(com.trace.app.R.string.app_name)) {
            Icon(
              painterResource(com.trace.app.R.drawable.logo),
              modifier = Modifier.size(20.dp),
              contentDescription = null,
              tint = Color.Unspecified,
            )
          }
          BasicText(
            text = title,
            maxLines = 1,
            color = { titleColor },
            style = MaterialTheme.typography.titleMedium,
            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 16.sp, stepSize = 1.sp),
          )
        }
        if (subtitle.isNotEmpty()) {
          Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
      }
    },
    modifier = modifier,
    scrollBehavior = scrollBehavior,
    navigationIcon = {
      when (leftAction?.actionType) {
        AppBarActionType.NAVIGATE_UP -> IconButton(onClick = leftAction.actionFn) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(com.trace.app.R.string.cd_navigate_back_icon))
        }
        AppBarActionType.MENU -> IconButton(onClick = leftAction.actionFn) {
          Icon(Icons.Rounded.Menu, contentDescription = stringResource(com.trace.app.R.string.cd_menu))
        }
        else -> {}
      }
    },
    actions = {
      when (rightAction?.actionType) {
        AppBarActionType.APP_SETTING -> IconButton(onClick = rightAction.actionFn) {
          Icon(Icons.Rounded.Settings, contentDescription = stringResource(com.trace.app.R.string.cd_app_settings_icon), tint = MaterialTheme.colorScheme.onSurface)
        }
        AppBarActionType.NAVIGATE_UP -> TextButton(onClick = rightAction.actionFn) { Text("Done") }
        else -> {}
      }
    },
  )
}
