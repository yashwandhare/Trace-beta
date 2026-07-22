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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.rag.Citation
import com.google.ai.edge.gallery.rag.KnowledgeScope
import com.google.ai.edge.gallery.rag.QuizItem
import com.google.ai.edge.gallery.ui.common.Accordions
import com.google.ai.edge.gallery.ui.common.ModuleEmptyState
import com.google.ai.edge.gallery.ui.common.chat.ChatHistorySideSheetContent
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.MessageInputText
import com.google.ai.edge.gallery.ui.common.getTaskIconColor
import com.google.ai.edge.gallery.ui.common.StarThinkingIndicator
import com.google.ai.edge.gallery.ui.common.playfulThinkingLabel
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.customColors
import kotlinx.coroutines.launch

/**
 * Standalone RAG module screen (Phase 3) — the "Notes" tile.
 *
 * Chat-shaped: the conversation (summaries, follow-up answers, interactive quiz
 * cards) fills the scroll area from the top, and a compact bar docks at the
 * bottom with the attached-note chips, a text field, a send button, and Quiz /
 * Summarize quick actions. Sending a message runs a grounded follow-up so a
 * summary can flow into a real back-and-forth. The knowledge-scope toggle lives
 * in the top bar to keep the input area small.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagScreen(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  onNavUp: () -> Unit,
  viewModel: RagViewModel = hiltViewModel(),
  // When the screen is opened from a quiz-schedule notification deeplink, this
  // carries the quiz request; it auto-fires once the model + notes are ready.
  initialQuery: String? = null,
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  val historySessions by viewModel.historySessions.collectAsState()
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val model = modelManagerUiState.selectedModel
  val accent = getTaskIconColor(task)
  val scope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

  // Legacy tasks are rendered without the CustomTaskScreen wrapper that normally
  // initializes the model — so the Notes screen must load Gemma itself, or every
  // Quiz/Summarize inference silently no-ops on a null model instance.
  val downloadStatus = modelManagerUiState.modelDownloadStatus[model.name]
  LaunchedEffect(downloadStatus?.status, model.name) {
    if (downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
      modelManagerViewModel.initializeModel(context, task = task, model = model)
    }
  }

  // Quiz-from-schedule: when a quiz reminder is tapped, the deeplink lands here
  // with a quiz request. Fire it once the model instance is live and there are
  // indexed notes to quiz on; guard so it runs a single time per open.
  var autoQuizFired by remember { mutableStateOf(false) }
  LaunchedEffect(initialQuery, model.instance, uiState.indexedSources.size) {
    if (
      !autoQuizFired &&
        !initialQuery.isNullOrBlank() &&
        model.instance != null &&
        uiState.indexedSources.isNotEmpty()
    ) {
      autoQuizFired = true
      viewModel.ask(model, initialQuery)
    }
  }

  var query by remember { mutableStateOf("") }
  // URIs already ingested so a doc isn't re-indexed each time the picked-files list changes.
  val ingestedUris = remember { mutableStateListOf<android.net.Uri>() }

  // History drawer, opening from the right (RTL trick, matching AI Chat's ChatView).
  androidx.compose.runtime.CompositionLocalProvider(
    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
  ) {
    ModalNavigationDrawer(
      drawerState = drawerState,
      gesturesEnabled = drawerState.isOpen,
      drawerContent = {
        androidx.compose.runtime.CompositionLocalProvider(
          androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
        ) {
          ModalDrawerSheet {
            ChatHistorySideSheetContent(
              history = historySessions,
              onHistoryItemClicked = { id -> viewModel.loadSession(id); scope.launch { drawerState.close() } },
              onHistoryItemDeleted = { id -> viewModel.deleteSession(id) },
              onHistoryItemsDeleteAll = { viewModel.clearAllSessions() },
              onNewChatClicked = { viewModel.newConversation(); scope.launch { drawerState.close() } },
              onDismissed = { scope.launch { drawerState.close() } },
            )
          }
        }
      },
    ) {
      androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
      ) {
        Scaffold(
          topBar = {
            var showOverflow by remember { mutableStateOf(false) }
            CenterAlignedTopAppBar(
              title = {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Icon(
                    Icons.Rounded.MenuBook,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp),
                  )
                  Text("Notes", style = MaterialTheme.typography.titleMedium, color = accent)
                }
              },
              navigationIcon = {
                IconButton(onClick = onNavUp) {
                  Icon(Icons.Rounded.Menu, contentDescription = "Menu")
                }
              },
              actions = {
                IconButton(onClick = { viewModel.newConversation() }) {
                  Icon(Icons.Rounded.AddComment, contentDescription = "New conversation")
                }
                IconButton(onClick = { showOverflow = true }) {
                  Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                  DropdownMenuItem(
                    text = {
                      Text(
                        if (uiState.knowledgeScope == KnowledgeScope.NOTES_ONLY) "Notes only"
                        else "Notes + AI"
                      )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Lightbulb, contentDescription = null) },
                    onClick = {
                      viewModel.setKnowledgeScope(
                        if (uiState.knowledgeScope == KnowledgeScope.NOTES_ONLY) {
                          KnowledgeScope.NOTES_AND_MODEL
                        } else {
                          KnowledgeScope.NOTES_ONLY
                        }
                      )
                    },
                  )
                  DropdownMenuItem(
                    text = { Text("History") },
                    leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                    onClick = {
                      showOverflow = false
                      scope.launch { drawerState.open() }
                    },
                  )
                }
              },
              colors =
                TopAppBarDefaults.centerAlignedTopAppBarColors(
                  containerColor = MaterialTheme.colorScheme.surface,
                  titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
          },
          containerColor = MaterialTheme.colorScheme.surface,
        ) { innerPadding ->
          Column(modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
            // ---- Conversation: fills from the top ----
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
              if (uiState.messages.isEmpty() && !uiState.generating) {
                EmptyState(
                  hasNotes = uiState.indexedSources.isNotEmpty(),
                  accent = accent,
                  onExample = { example -> if (model.instance != null) viewModel.ask(model, example) },
                )
              } else {
                Conversation(uiState = uiState, accent = accent)
              }
            }

            // ---- Input: same MessageInputText as AI Chat / Vision ----
            // Attached-note chips sit just above the shared input.
            AttachedSourcesRow(
              uiState = uiState,
              onRemoveSource = { viewModel.removeSource(it) },
              modifier = Modifier.padding(horizontal = 12.dp),
            )
            uiState.errorMessage?.let { error ->
              Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
              )
            }
            MessageInputText(
              task = task,
              modelManagerViewModel = modelManagerViewModel,
              curMessage = query,
              isResettingSession = false,
              inProgress = uiState.generating,
              imageCount = 0,
              audioClipMessageCount = 0,
              modelInitializing = false,
              textFieldPlaceHolderRes = com.google.ai.edge.gallery.R.string.text_input_placeholder_llm_chat,
              onValueChanged = { query = it },
              onAmplitudeChanged = {},
              showAttachDocument = true,
              // Index each attached document immediately on pick (don't wait for send).
              onPickedFilesChanged = { uris ->
                uris.forEach { uri ->
                  if (uri !in ingestedUris) {
                    ingestedUris.add(uri)
                    viewModel.ingestDocument(context, uri)
                  }
                }
              },
              // Circular Quiz button just left of the mic (same size as mic/send).
              leadingSendAction = {
                val quizReady = uiState.indexedSources.isNotEmpty() && !uiState.generating
                IconButton(
                  onClick = { viewModel.quiz(model, query); query = "" },
                  enabled = quizReady,
                  colors =
                    IconButtonDefaults.iconButtonColors(
                      containerColor =
                        if (quizReady) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
                      disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                  Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = "Quiz me",
                    tint =
                      if (quizReady) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                  )
                }
              },
              onSendMessage = { messages ->
                // Documents are ingested on pick (onPickedFilesChanged); here we
                // only handle the typed query.
                val text =
                  messages.filterIsInstance<ChatMessageText>().joinToString("\n") { it.content }.trim()
                if (text.isNotEmpty()) {
                  viewModel.ask(model, text)
                }
                query = ""
              },
            )
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Conversation
// ---------------------------------------------------------------------------

@Composable
private fun Conversation(uiState: RagUiState, accent: Color) {
  val listState = rememberLazyListState()
  val itemCount = uiState.messages.size + if (uiState.generating) 1 else 0

  // Keep the newest turn in view as the conversation grows.
  LaunchedEffect(itemCount) {
    if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    items(uiState.messages) { message ->
      when (message) {
        is RagMessage.UserMessage -> UserBubble(message.text)
        is RagMessage.AssistantText -> AssistantTextBubble(message, accent)
        is RagMessage.AssistantQuiz -> QuizTurn(message, accent)
      }
    }
    if (uiState.generating) {
      item { GeneratingBubble(seed = uiState.messages.size) }
    }
  }
}

@Composable
private fun UserBubble(text: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    Surface(
      color = MaterialTheme.customColors.userBubbleBgColor,
      // Hard corner at top-right (teardrop points up), matching AI Chat.
      shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp),
      modifier = Modifier.widthIn(max = 300.dp),
    ) {
      Text(
        text,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun AssistantTextBubble(message: RagMessage.AssistantText, accent: Color) {
  // Agent responses render flat (no bubble), matching AI Chat.
  Column(
    modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      message.text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    if (message.citations.isNotEmpty()) {
      Citations(message.citations, accent)
    }
  }
}

@Composable
private fun QuizTurn(message: RagMessage.AssistantQuiz, accent: Color) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text("Quiz from your notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
    message.items.forEach { item -> QuizCard(item = item, accent = accent) }
    if (message.citations.isNotEmpty()) {
      Citations(message.citations, accent)
    }
  }
}

@Composable
private fun Citations(citations: List<Citation>, accent: Color) {
  var expanded by remember { mutableStateOf(false) }
  Accordions(
    title = "Sources (${citations.size})",
    expanded = expanded,
    onExpandedChange = { expanded = it },
    boldTitle = true,
    bgColor = Color.Transparent,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
      citations.forEach { citation ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          shape = RoundedCornerShape(12.dp),
        ) {
          Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(citation.sourceLabel, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, color = accent)
            Text(
              "“${citation.snippet}”",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

/**
 * Interactive quiz card.
 *
 * Multiple-choice items are Anki-style: the user taps an option, which locks the
 * answer and reveals grading — the correct option turns green, and a wrong pick
 * turns red. Flashcard items (no options) simply flip question → answer on tap.
 */
@Composable
private fun QuizCard(item: QuizItem, accent: Color) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier.fillMaxWidth().animateContentSize(),
  ) {
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(item.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

      if (item.isMultipleChoice) {
        McqOptions(item = item, accent = accent)
      } else {
        Flashcard(item = item, accent = accent)
      }

      if (item.sourceLabel.isNotBlank()) {
        Text(
          "from ${item.sourceLabel}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private val CorrectGreen = Color(0xFF2E7D32)

@Composable
private fun McqOptions(item: QuizItem, accent: Color) {
  var selected by remember(item) { mutableStateOf<String?>(null) }
  val locked = selected != null

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item.options.forEach { opt ->
      val isCorrect = opt.trim().equals(item.answer.trim(), ignoreCase = true)
      val isPicked = opt == selected

      val (bg, borderColor) = when {
        !locked -> MaterialTheme.colorScheme.surfaceContainerHighest to Color.Transparent
        isCorrect -> CorrectGreen.copy(alpha = 0.18f) to CorrectGreen
        isPicked -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f) to MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to Color.Transparent
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier.fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = !locked) { selected = opt }
            .padding(horizontal = 12.dp, vertical = 12.dp),
      ) {
        Text(opt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (locked && isCorrect) {
          Icon(Icons.Rounded.Check, contentDescription = "Correct", tint = CorrectGreen, modifier = Modifier.size(18.dp))
        } else if (locked && isPicked) {
          Icon(Icons.Rounded.Close, contentDescription = "Your pick", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        }
      }
    }

    if (!locked) {
      Text("Tap an option to answer", style = MaterialTheme.typography.labelMedium, color = accent)
    }
  }
}

@Composable
private fun Flashcard(item: QuizItem, accent: Color) {
  var revealed by remember(item) { mutableStateOf(false) }
  if (!revealed) {
    Surface(
      onClick = { revealed = true },
      color = MaterialTheme.colorScheme.surfaceContainerHighest,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        "Tap to reveal answer",
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        style = MaterialTheme.typography.labelMedium,
        color = accent,
      )
    }
  } else {
    Surface(
      color = accent.copy(alpha = 0.14f),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        item.answer,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
    }
  }
}

@Composable
private fun EmptyState(hasNotes: Boolean, accent: Color, onExample: (String) -> Unit = {}) {
  ModuleEmptyState(
    icon = Icons.Rounded.MenuBook,
    accent = accent,
    title = if (hasNotes) "Ask, quiz, or summarize" else "Study from your own notes",
    description =
      if (hasNotes)
        "Type a question about your notes, or tap Quiz me or Summarize. You can keep the conversation going."
      else
        "Attach a note or document below. It's indexed on-device and never leaves your phone.",
    suggestions =
      if (hasNotes) listOf("Summarize my notes", "Quiz me", "Explain the key concepts")
      else emptyList(),
    onSuggestionClick = onExample,
  )
}

@Composable
private fun GeneratingBubble(seed: Int) {
  // Flat (no bubble), matching the agent message style.
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    StarThinkingIndicator(starSize = 16.dp)
    Text(playfulThinkingLabel(seed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

// ---------------------------------------------------------------------------
// Input-bar slots (composed into the shared TraceChatInput)
// ---------------------------------------------------------------------------

/** Horizontally-scrolling row of attached-note chips. Attaching is handled by the input's + button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachedSourcesRow(
  uiState: RagUiState,
  onRemoveSource: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (uiState.indexedSources.isEmpty() && !uiState.ingesting) return
  Row(
    modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (uiState.ingesting) {
      AssistChip(
        onClick = {},
        enabled = false,
        label = { Text("Indexing…") },
        leadingIcon = { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) },
      )
    }
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
}

