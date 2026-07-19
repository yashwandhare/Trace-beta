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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.rag.KnowledgeScope
import com.google.ai.edge.gallery.rag.RagMode
import com.google.ai.edge.gallery.rag.RagResponse
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/**
 * Standalone RAG module screen (Phase 3).
 *
 * Functional baseline built by Dev C1: attach notes -> index -> quiz/summarize
 * with citations and the notes-only / notes+model toggle. The rich
 * Quiz/Flashcard card experience (answer reveal, right/wrong feedback) is Dev
 * C2's lane — it should replace [RagResultPanel]'s quiz rendering, consuming
 * the same [RagUiState.response].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagScreen(
  modelManagerViewModel: ModelManagerViewModel,
  onNavUp: () -> Unit,
  viewModel: RagViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val model = modelManagerUiState.selectedModel

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

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // --- Notes / sources section ---
    item {
      Text("Your notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(4.dp))
      Text(
        "Attach notes or documents. They're indexed on-device and never leave your phone.",
        style = MaterialTheme.typography.bodySmall,
      )
    }
    items(uiState.indexedSources) { source ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
          onClick = {},
          label = { Text(source, maxLines = 1) },
          trailingIcon = {
            Icon(
              Icons.Outlined.Close,
              contentDescription = "Remove $source",
              modifier = Modifier.width(16.dp),
            )
          },
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { viewModel.removeSource(source) }) { Text("Remove") }
      }
    }
    item {
      OutlinedButton(
        onClick = {
          filePicker.launch(
            arrayOf(
              "application/pdf",
              "text/*",
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
              "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            )
          )
        },
        enabled = !uiState.ingesting,
      ) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(if (uiState.ingesting) "Indexing…" else "Attach note / document")
      }
    }

    // --- Knowledge scope toggle ---
    item {
      Text("Answer using", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = uiState.knowledgeScope == KnowledgeScope.NOTES_ONLY,
          onClick = { viewModel.setKnowledgeScope(KnowledgeScope.NOTES_ONLY) },
          label = { Text("My notes only") },
        )
        FilterChip(
          selected = uiState.knowledgeScope == KnowledgeScope.NOTES_AND_MODEL,
          onClick = { viewModel.setKnowledgeScope(KnowledgeScope.NOTES_AND_MODEL) },
          label = { Text("Notes + AI knowledge") },
        )
      }
    }

    // --- Query + actions ---
    item {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Topic (optional) — e.g. \"DBMS normalization\"") },
        singleLine = true,
      )
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = { viewModel.generate(model, query, RagMode.QUIZ) },
          enabled = !uiState.generating && uiState.indexedSources.isNotEmpty(),
        ) { Text("Quiz me") }
        Button(
          onClick = { viewModel.generate(model, query, RagMode.SUMMARY) },
          enabled = !uiState.generating && uiState.indexedSources.isNotEmpty(),
        ) { Text("Summarize") }
      }
    }

    // --- Status / errors ---
    if (uiState.generating) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
          Spacer(Modifier.width(8.dp))
          Text("Generating from your notes…", style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
    uiState.errorMessage?.let { error ->
      item { Text(error, color = MaterialTheme.colorScheme.error) }
    }

    // --- Result ---
    uiState.response?.let { response ->
      item { RagResultPanel(response = response) }
    }
  }
}

/**
 * Baseline result rendering. Dev C2: replace the quiz branch with the real
 * Quiz/Flashcard cards (question card, answer reveal, right/wrong feedback).
 */
@Composable
private fun RagResultPanel(response: RagResponse) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    if (response.isQuiz) {
      Text("Quiz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      response.items.forEachIndexed { i, item ->
        Card {
          Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${i + 1}. ${item.question}", fontWeight = FontWeight.SemiBold)
            if (item.isMultipleChoice) {
              item.options.forEach { opt -> Text("• $opt", style = MaterialTheme.typography.bodyMedium) }
            }
            Text("Answer: ${item.answer}", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    } else if (response.summary.isNotBlank()) {
      Text("Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Card { Text(response.summary, Modifier.padding(12.dp)) }
    }

    if (response.citations.isNotEmpty()) {
      Text("Sources", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      response.citations.forEach { citation ->
        Card {
          Column(Modifier.padding(10.dp)) {
            Text(citation.sourceLabel, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            Text("\"${citation.snippet}\"", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
}
