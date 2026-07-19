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

package com.google.ai.edge.gallery.ui.rag

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.rag.Citation
import com.google.ai.edge.gallery.rag.KnowledgeScope
import com.google.ai.edge.gallery.rag.QuizItem
import com.google.ai.edge.gallery.rag.RagMode
import com.google.ai.edge.gallery.rag.RagResponse
import com.google.ai.edge.gallery.ui.common.getTaskIconColor
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/**
 * Standalone RAG module screen (Phase 3) — the "Notes" tile.
 *
 * Layout is chat-shaped: generated results (quiz cards / summary / citations)
 * fill the scrollable area from the top, and all the controls (attached notes,
 * knowledge-scope toggle, topic field, Quiz/Summarize actions) are docked in a
 * bottom bar — the same input-at-the-bottom model as AI Chat.
 *
 * The interactive Quiz/Flashcard experience (tap-to-reveal, self-graded
 * right/wrong) lives in [QuizCard] and consumes [RagResponse.items].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagScreen(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  onNavUp: () -> Unit,
  viewModel: RagViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val model = modelManagerUiState.selectedModel
  val accent = getTaskIconColor(task)

  // Legacy tasks are rendered without the CustomTaskScreen wrapper that normally
  // initializes the model — so the Notes screen must load Gemma itself, or every
  // Quiz/Summarize inference silently no-ops on a null model instance.
  val downloadStatus = modelManagerUiState.modelDownloadStatus[model.name]
  LaunchedEffect(downloadStatus?.status, model.name) {
    if (downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
      modelManagerViewModel.initializeModel(context, task = task, model = model)
    }
  }

  var query by remember { mutableStateOf("") }

  val filePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        try {
          context.contentResolver.takePersistableUriPermission(
            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
          )
        } catch (_: Exception) { /* non-persistable URIs are fine */ }
        viewModel.ingestDocument(context, uri)
      }
    }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Notes", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = onNavUp) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    },
    containerColor = MaterialTheme.colorScheme.surface,
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      // ---- Results area: fills from the top ----
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        val response = uiState.response
        when {
          uiState.generating -> GeneratingState(accent = accent)
          response != null -> ResultsList(response = response, accent = accent)
          else -> EmptyState(hasNotes = uiState.indexedSources.isNotEmpty(), accent = accent)
        }
      }

      // ---- Controls: docked at the bottom ----
      BottomControls(
        uiState = uiState,
        query = query,
        accent = accent,
        onQueryChange = { query = it },
        onAttach = {
          filePicker.launch(
            arrayOf(
              "application/pdf",
              "text/*",
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
              "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            )
          )
        },
        onRemoveSource = { viewModel.removeSource(it) },
        onScopeChange = { viewModel.setKnowledgeScope(it) },
        onQuiz = { viewModel.generate(model, query, RagMode.QUIZ) },
        onSummarize = { viewModel.generate(model, query, RagMode.SUMMARY) },
      )
    }
  }
}

// ---------------------------------------------------------------------------
// Results area
// ---------------------------------------------------------------------------

@Composable
private fun ResultsList(response: RagResponse, accent: Color) {
  val listState = rememberLazyListState()
  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (response.isQuiz) {
      item {
        SectionHeader(icon = Icons.Rounded.AutoAwesome, label = "Quiz from your notes", accent = accent)
      }
      items(response.items) { quizItem -> QuizCard(item = quizItem, accent = accent) }
    } else if (response.summary.isNotBlank()) {
      item {
        SectionHeader(icon = Icons.Rounded.Summarize, label = "Summary", accent = accent)
      }
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
          shape = RoundedCornerShape(18.dp),
        ) {
          Text(
            response.summary,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }

    if (response.citations.isNotEmpty()) {
      item { Spacer(Modifier.height(4.dp)) }
      item {
        Text(
          "Sources",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      items(response.citations) { citation -> CitationCard(citation = citation) }
    }
  }
}

@Composable
private fun SectionHeader(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  accent: Color,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
  }
}

/**
 * Interactive quiz/flashcard card (Dev C2).
 *
 * Starts with only the question shown. Tapping reveals the answer (and, for
 * multiple-choice items, marks the correct option). Once revealed, the user
 * self-grades with Got it / Missed — a lightweight right/wrong signal that
 * colors the card edge so progress through a set is visible at a glance.
 */
@Composable
private fun QuizCard(item: QuizItem, accent: Color) {
  var revealed by remember(item) { mutableStateOf(false) }
  var graded by remember(item) { mutableStateOf<Boolean?>(null) }

  val edgeColor =
    when (graded) {
      true -> Color(0xFF4CAF50)
      false -> MaterialTheme.colorScheme.error
      null -> Color.Transparent
    }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    shape = RoundedCornerShape(18.dp),
    modifier =
      Modifier.fillMaxWidth()
        .animateContentSize()
        .clickable(enabled = !revealed) { revealed = true },
  ) {
    Row(modifier = Modifier.fillMaxWidth()) {
      // Grade edge indicator.
      Box(modifier = Modifier.width(4.dp).height(if (revealed) 120.dp else 56.dp).background(edgeColor))
      Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(item.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        if (item.isMultipleChoice) {
          item.options.forEach { opt ->
            val isCorrect = revealed && opt.trim().equals(item.answer.trim(), ignoreCase = true)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                Modifier.fillMaxWidth()
                  .background(
                    if (isCorrect) accent.copy(alpha = 0.18f) else Color.Transparent,
                    RoundedCornerShape(10.dp),
                  )
                  .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
              if (isCorrect) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
              }
              Text(opt, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }

        if (!revealed) {
          Text(
            "Tap to reveal answer",
            style = MaterialTheme.typography.labelMedium,
            color = accent,
          )
        } else {
          AnimatedVisibility(visible = true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              if (!item.isMultipleChoice) {
                Surface(
                  color = accent.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(10.dp),
                ) {
                  Text(
                    item.answer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                  )
                }
              }
              if (item.sourceLabel.isNotBlank()) {
                Text(
                  "from ${item.sourceLabel}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              // Self-grade row.
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradePill(label = "Got it", selected = graded == true, color = Color(0xFF4CAF50)) { graded = true }
                GradePill(label = "Missed", selected = graded == false, color = MaterialTheme.colorScheme.error) { graded = false }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GradePill(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest,
    modifier = Modifier.height(36.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
    ) {
      Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
      )
    }
  }
}

@Composable
private fun CitationCard(citation: Citation) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = RoundedCornerShape(14.dp),
  ) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(citation.sourceLabel, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
      Text(
        "“${citation.snippet}”",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun EmptyState(hasNotes: Boolean, accent: Color) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = accent, modifier = Modifier.size(48.dp))
    Spacer(Modifier.height(16.dp))
    Text(
      if (hasNotes) "Ready to quiz" else "Study from your own notes",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      if (hasNotes)
        "Pick a topic below, then tap Quiz me or Summarize."
      else
        "Attach a note or document below. It's indexed on-device and never leaves your phone.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun GeneratingState(accent: Color) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator(color = accent)
    Spacer(Modifier.height(16.dp))
    Text(
      "Generating from your notes…",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

// ---------------------------------------------------------------------------
// Bottom controls
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomControls(
  uiState: RagUiState,
  query: String,
  accent: Color,
  onQueryChange: (String) -> Unit,
  onAttach: () -> Unit,
  onRemoveSource: (String) -> Unit,
  onScopeChange: (KnowledgeScope) -> Unit,
  onQuiz: () -> Unit,
  onSummarize: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    tonalElevation = 3.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().imePadding(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Attached notes as a horizontally-scrolling chip row + attach button.
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AssistChip(
          onClick = onAttach,
          enabled = !uiState.ingesting,
          label = { Text(if (uiState.ingesting) "Indexing…" else "Attach note") },
          leadingIcon = {
            if (uiState.ingesting) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
              Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            }
          },
          colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = accent),
        )
        uiState.indexedSources.forEach { source ->
          AssistChip(
            onClick = { onRemoveSource(source) },
            label = { Text(source, maxLines = 1) },
            trailingIcon = {
              Icon(Icons.Rounded.Close, contentDescription = "Remove $source", modifier = Modifier.size(16.dp))
            },
          )
        }
      }

      // Knowledge-scope toggle.
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = uiState.knowledgeScope == KnowledgeScope.NOTES_ONLY,
          onClick = { onScopeChange(KnowledgeScope.NOTES_ONLY) },
          label = { Text("My notes only") },
          colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.22f)),
        )
        FilterChip(
          selected = uiState.knowledgeScope == KnowledgeScope.NOTES_AND_MODEL,
          onClick = { onScopeChange(KnowledgeScope.NOTES_AND_MODEL) },
          label = { Text("Notes + AI knowledge") },
          colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.22f)),
        )
      }

      // Topic field.
      OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Topic (optional) — e.g. “DBMS normalization”") },
        singleLine = true,
      )

      // Actions.
      val hasNotes = uiState.indexedSources.isNotEmpty()
      val enabled = !uiState.generating && hasNotes
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
          onClick = onQuiz,
          enabled = enabled,
          modifier = Modifier.weight(1f),
          colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
        ) {
          Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Quiz me")
        }
        Button(
          onClick = onSummarize,
          enabled = enabled,
          modifier = Modifier.weight(1f),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
          Icon(Icons.Rounded.Summarize, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Summarize")
        }
      }

      uiState.errorMessage?.let { error ->
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}
