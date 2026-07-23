/*
 * Copyright 2026 The Trace Authors
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

package com.trace.app.ui.rag

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trace.app.common.DocumentExtractor
import com.trace.app.data.BuiltInTaskId
import com.trace.app.data.Model
import com.trace.app.proto.ChatMessageProto
import com.trace.app.proto.ChatSessionProto
import com.trace.app.proto.ChatSideProto
import com.trace.app.proto.UserData
import com.trace.app.rag.Citation
import com.trace.app.rag.KnowledgeScope
import com.trace.app.rag.QuizItem
import com.trace.app.rag.RagEngine
import com.trace.app.rag.RagMode
import com.trace.app.rag.RagResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "TraceRagViewModel"

/**
 * One turn in the Notes conversation. UI-side only — not a cross-boundary
 * contract, so it can evolve freely with the screen.
 */
sealed interface RagMessage {
  /** Something the user asked (typed question, or a Quiz/Summarize action label). */
  data class UserMessage(val text: String) : RagMessage

  /** A conversational answer (summary or follow-up), with grounding citations. */
  data class AssistantText(val text: String, val citations: List<Citation> = emptyList()) : RagMessage

  /** An interactive quiz turn — a set of MCQ/flashcard items. */
  data class AssistantQuiz(val items: List<QuizItem>, val citations: List<Citation> = emptyList()) :
    RagMessage
}

/** UI state for the RAG module screen. */
data class RagUiState(
  /** Note sources currently in the index (file display names). */
  val indexedSources: List<String> = emptyList(),
  /** True while a document is being extracted/embedded. */
  val ingesting: Boolean = false,
  /** True while retrieval + generation is running. */
  val generating: Boolean = false,
  /** The conversation transcript, oldest first. */
  val messages: List<RagMessage> = emptyList(),
  /** Non-null when the last action failed in a user-visible way. */
  val errorMessage: String? = null,
  /** User toggle: ground strictly in notes, or blend model knowledge. */
  val knowledgeScope: KnowledgeScope = KnowledgeScope.NOTES_ONLY,
)

/**
 * ViewModel for the standalone RAG module (Phase 3).
 *
 * Owns the Notes conversation. The pipeline lives in the app-scoped [RagEngine]
 * singleton, shared with AI Chat so notes attached in either place are queryable
 * from both. Three entry points drive generation — [ask] (free-form follow-up),
 * [quiz], and [summarize] — each appending a user turn then an assistant turn.
 */
@HiltViewModel
class RagViewModel @Inject constructor(
  private val ragEngine: RagEngine,
  private val userDataDataStore: DataStore<UserData>,
) : ViewModel() {

  private val _uiState = MutableStateFlow(RagUiState())
  val uiState = _uiState.asStateFlow()

  /** Current conversation id — a new one starts each fresh Notes conversation. */
  private var currentSessionId: String = UUID.randomUUID().toString()

  /** Saved Notes conversations, newest first (task-scoped to the RAG module). */
  val historySessions: StateFlow<List<ChatSessionProto>> =
    userDataDataStore.data
      .map { userData ->
        userData.chatSessionsList
          .filter { it.taskId == BuiltInTaskId.RAG }
          .sortedByDescending { it.timestampMs }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  init {
    refreshSources()
    // Re-embed any notes attached in a previous session (persisted as text).
    // The chips reappear once the in-memory index is rebuilt.
    viewModelScope.launch(Dispatchers.Default) {
      if (ragEngine.indexedSources.isEmpty()) {
        _uiState.update { it.copy(ingesting = true) }
        ragEngine.warmUp()
        _uiState.update {
          it.copy(ingesting = false, indexedSources = ragEngine.indexedSources)
        }
      }
    }
  }

  fun setKnowledgeScope(scope: KnowledgeScope) {
    _uiState.update { it.copy(knowledgeScope = scope) }
  }

  fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  private fun refreshSources() {
    _uiState.update { it.copy(indexedSources = ragEngine.indexedSources) }
  }

  /**
   * Extracts text from a user-picked document [uri] and ingests it into the
   * shared index. Explicit selection only — never a background scan.
   */
  fun ingestDocument(context: Context, uri: Uri) {
    _uiState.update { it.copy(ingesting = true, errorMessage = null) }
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val text = DocumentExtractor(context.applicationContext).extract(uri)
        if (text.isNullOrBlank()) {
          _uiState.update {
            it.copy(ingesting = false, errorMessage = "Couldn't extract text from that document.")
          }
          return@launch
        }
        val label = uri.lastPathSegment?.substringAfterLast('/') ?: "note-${System.currentTimeMillis()}"
        val chunks = ragEngine.ingest(text, label)
        Log.d(TAG, "Ingested '$label': $chunks chunks")
        _uiState.update {
          it.copy(
            ingesting = false,
            indexedSources = ragEngine.indexedSources,
            errorMessage = if (chunks == 0) "Couldn't index that document." else null,
          )
        }
      } catch (e: Exception) {
        Log.e(TAG, "Ingest failed", e)
        _uiState.update { it.copy(ingesting = false, errorMessage = "Failed to read that document.") }
      }
    }
  }

  /** Removes one source from the index (and its persisted copy). */
  fun removeSource(sourceLabel: String) {
    ragEngine.removeSource(sourceLabel)
    refreshSources()
    viewModelScope.launch(Dispatchers.IO) { ragEngine.forgetSource(sourceLabel) }
  }

  /**
   * Answers a free-form [query] against the indexed notes. Typed input that
   * reads as a quiz/summary request is routed to the matching mode so "quiz me"
   * still works from the text field; everything else is a grounded follow-up.
   */
  fun ask(model: Model, query: String) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return
    val mode = ragEngine.detectMode(trimmed) ?: RagMode.ASK
    run(model, userLabel = trimmed, topic = trimmed, mode = mode)
  }

  /** Generates an interactive quiz, optionally focused on [topic]. */
  fun quiz(model: Model, topic: String) {
    val t = topic.trim()
    run(
      model,
      userLabel = if (t.isEmpty()) "Quiz me on my notes" else "Quiz me on: $t",
      topic = t,
      mode = RagMode.QUIZ,
    )
  }

  /** Summarizes the notes, optionally focused on [topic]. */
  fun summarize(model: Model, topic: String) {
    val t = topic.trim()
    run(
      model,
      userLabel = if (t.isEmpty()) "Summarize my notes" else "Summarize: $t",
      topic = t,
      mode = RagMode.SUMMARY,
    )
  }

  private fun run(model: Model, userLabel: String, topic: String, mode: RagMode) {
    if (!ragEngine.hasIndexedContent) {
      _uiState.update { it.copy(errorMessage = "Attach a note or document first.") }
      return
    }
    _uiState.update {
      it.copy(
        messages = it.messages + RagMessage.UserMessage(userLabel),
        generating = true,
        errorMessage = null,
      )
    }
    viewModelScope.launch(Dispatchers.Default) {
      try {
        if (!awaitModelReady(model)) {
          _uiState.update {
            it.copy(generating = false, errorMessage = "Model still loading — try again in a moment.")
          }
          return@launch
        }
        val response =
          ragEngine.generate(
            mode = mode,
            topic = topic,
            model = model,
            scope = this,
            knowledgeScope = _uiState.value.knowledgeScope,
          )
        val message = response?.toMessage()
        if (message == null) {
          _uiState.update {
            it.copy(
              generating = false,
              errorMessage = "Couldn't generate from your notes. Try a different topic or re-attach the note.",
            )
          }
          return@launch
        }
        _uiState.update {
          it.copy(generating = false, messages = it.messages + message, errorMessage = null)
        }
        saveCurrentSession(model.name)
      } catch (e: Exception) {
        Log.e(TAG, "Generation failed", e)
        _uiState.update { it.copy(generating = false, errorMessage = "Generation failed. Try again.") }
      }
    }
  }

  // -------------------------------------------------------------------------
  // History (persisted like AI Chat, task-scoped to the RAG module)
  // -------------------------------------------------------------------------

  /** Starts a fresh conversation, clearing the on-screen transcript. */
  fun newConversation() {
    currentSessionId = UUID.randomUUID().toString()
    _uiState.update { it.copy(messages = emptyList(), errorMessage = null) }
  }

  /** Loads a saved conversation into the transcript (read-only until a note is attached). */
  fun loadSession(sessionId: String) {
    val session = historySessions.value.firstOrNull { it.sessionId == sessionId } ?: return
    currentSessionId = sessionId
    _uiState.update {
      it.copy(messages = session.messagesList.mapNotNull { m -> m.toRagMessage() }, errorMessage = null)
    }
  }

  fun deleteSession(sessionId: String) {
    viewModelScope.launch(Dispatchers.IO) {
      userDataDataStore.updateData { userData ->
        val kept = userData.chatSessionsList.filter { it.sessionId != sessionId }
        userData.toBuilder().clearChatSessions().addAllChatSessions(kept).build()
      }
    }
    if (sessionId == currentSessionId) newConversation()
  }

  fun clearAllSessions() {
    viewModelScope.launch(Dispatchers.IO) {
      userDataDataStore.updateData { userData ->
        val kept = userData.chatSessionsList.filter { it.taskId != BuiltInTaskId.RAG }
        userData.toBuilder().clearChatSessions().addAllChatSessions(kept).build()
      }
    }
    newConversation()
  }

  /** Upserts the current transcript as a session under the RAG task id. */
  private fun saveCurrentSession(originalModel: String) {
    val messages = _uiState.value.messages
    if (messages.isEmpty()) return
    val sessionId = currentSessionId
    val title =
      messages.filterIsInstance<RagMessage.UserMessage>().firstOrNull()?.text
        ?.take(30)?.let { if (it.length == 30) "$it…" else it }
        ?: "Notes session"
    val protoMessages = messages.map { it.toProto() }
    viewModelScope.launch(Dispatchers.IO) {
      val sessionProto =
        ChatSessionProto.newBuilder()
          .setSessionId(sessionId)
          .setTitle(title)
          .setTimestampMs(System.currentTimeMillis())
          .setOriginalModel(originalModel)
          .setTaskId(BuiltInTaskId.RAG)
          .addAllMessages(protoMessages)
          .build()
      userDataDataStore.updateData { userData ->
        val sessions = userData.chatSessionsList.toMutableList()
        sessions.removeAll { it.sessionId == sessionId }
        sessions.add(sessionProto)
        userData.toBuilder().clearChatSessions().addAllChatSessions(sessions).build()
      }
    }
  }

  /**
   * Waits up to 30s for the resident model to finish initializing. Without this,
   * a tap before init completes runs inference on a null instance and silently
   * returns nothing.
   */
  private suspend fun awaitModelReady(model: Model): Boolean {
    var waited = 0
    while (model.instance == null && waited < 30_000) {
      delay(200)
      waited += 200
    }
    return model.instance != null
  }
}

/**
 * Maps an engine [RagResponse] to a conversation message, or null when the
 * response carries nothing renderable (no quiz items and a blank summary) — the
 * caller treats that as a user-visible failure rather than an empty bubble.
 */
private fun RagResponse.toMessage(): RagMessage? =
  when {
    items.isNotEmpty() -> RagMessage.AssistantQuiz(items = items, citations = citations)
    summary.isNotBlank() -> RagMessage.AssistantText(text = summary, citations = citations)
    else -> null
  }

// ---------------------------------------------------------------------------
// RagMessage <-> ChatMessageProto encoding
//
// The proto's message_type/content are free strings, so we reuse the existing
// chat_history schema without changing it: quiz items and citations (which have
// no native proto shape) are JSON-encoded into content under RAG_* markers.
// ---------------------------------------------------------------------------

private const val TYPE_USER = "RAG_USER"
private const val TYPE_ASSISTANT = "RAG_ASSISTANT"
private const val TYPE_QUIZ = "RAG_QUIZ"

private fun RagMessage.toProto(): ChatMessageProto {
  val builder = ChatMessageProto.newBuilder()
  return when (this) {
    is RagMessage.UserMessage ->
      builder.setMessageType(TYPE_USER).setContent(text).setSide(ChatSideProto.CHAT_SIDE_USER).build()
    is RagMessage.AssistantText ->
      builder
        .setMessageType(TYPE_ASSISTANT)
        .setContent(encodeAssistantText(text, citations))
        .setSide(ChatSideProto.CHAT_SIDE_MODEL)
        .build()
    is RagMessage.AssistantQuiz ->
      builder
        .setMessageType(TYPE_QUIZ)
        .setContent(encodeQuiz(items, citations))
        .setSide(ChatSideProto.CHAT_SIDE_MODEL)
        .build()
  }
}

private fun ChatMessageProto.toRagMessage(): RagMessage? =
  when (messageType) {
    TYPE_USER -> RagMessage.UserMessage(content)
    TYPE_ASSISTANT -> {
      val obj = runCatching { JSONObject(content) }.getOrNull()
      if (obj != null) {
        RagMessage.AssistantText(obj.optString("text"), decodeCitations(obj.optJSONArray("citations")))
      } else {
        RagMessage.AssistantText(content)
      }
    }
    TYPE_QUIZ -> {
      val obj = runCatching { JSONObject(content) }.getOrNull() ?: return null
      RagMessage.AssistantQuiz(
        items = decodeQuizItems(obj.optJSONArray("items")),
        citations = decodeCitations(obj.optJSONArray("citations")),
      )
    }
    else -> null
  }

private fun encodeAssistantText(text: String, citations: List<Citation>): String =
  JSONObject().put("text", text).put("citations", encodeCitations(citations)).toString()

private fun encodeQuiz(items: List<QuizItem>, citations: List<Citation>): String {
  val itemsArr = JSONArray()
  items.forEach { item ->
    itemsArr.put(
      JSONObject()
        .put("question", item.question)
        .put("answer", item.answer)
        .put("options", JSONArray(item.options))
        .put("sourceLabel", item.sourceLabel)
    )
  }
  return JSONObject().put("items", itemsArr).put("citations", encodeCitations(citations)).toString()
}

private fun encodeCitations(citations: List<Citation>): JSONArray {
  val arr = JSONArray()
  citations.forEach { c ->
    arr.put(
      JSONObject()
        .put("sourceLabel", c.sourceLabel)
        .put("snippet", c.snippet)
        .put("score", c.score.toDouble())
    )
  }
  return arr
}

private fun decodeCitations(arr: JSONArray?): List<Citation> {
  if (arr == null) return emptyList()
  return (0 until arr.length()).mapNotNull { i ->
    val o = arr.optJSONObject(i) ?: return@mapNotNull null
    Citation(o.optString("sourceLabel"), o.optString("snippet"), o.optDouble("score", 0.0).toFloat())
  }
}

private fun decodeQuizItems(arr: JSONArray?): List<QuizItem> {
  if (arr == null) return emptyList()
  return (0 until arr.length()).mapNotNull { i ->
    val o = arr.optJSONObject(i) ?: return@mapNotNull null
    val optsArr = o.optJSONArray("options")
    val options =
      if (optsArr == null) emptyList()
      else (0 until optsArr.length()).map { optsArr.optString(it) }
    QuizItem(
      question = o.optString("question"),
      answer = o.optString("answer"),
      options = options,
      sourceLabel = o.optString("sourceLabel"),
    )
  }
}
