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
package com.trace.app.ui.memory

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.trace.app.proto.MemoryEntry
import com.trace.app.proto.MemoryKind
import com.trace.app.proto.MemorySource
import com.trace.app.proto.ScheduledNotification
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class OrganizerTab { MEMORY, SCHEDULES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScheduleScreen(
  initialTab: OrganizerTab,
  onBack: () -> Unit,
  viewModel: MemoryScheduleViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val entries by viewModel.entries.collectAsState()
  val schedules by viewModel.schedules.collectAsState()
  var tab by remember(initialTab) { mutableStateOf(initialTab) }
  var editingEntry by remember { mutableStateOf<MemoryEntry?>(null) }
  var deletingEntry by remember { mutableStateOf<MemoryEntry?>(null) }
  var deletingSchedule by remember { mutableStateOf<ScheduledNotification?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (tab == OrganizerTab.MEMORY) "Memory" else "Schedules") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (tab == OrganizerTab.MEMORY) {
            IconButton(onClick = { editingEntry = MemoryEntry.getDefaultInstance() }) {
              Icon(Icons.Rounded.Add, contentDescription = "Add memory")
            }
          }
        },
        scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
      )
    },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      TabRow(selectedTabIndex = tab.ordinal) {
        Tab(
          selected = tab == OrganizerTab.MEMORY,
          onClick = { tab = OrganizerTab.MEMORY },
          text = { Text("Memory") },
          icon = { Icon(Icons.Rounded.NoteAlt, contentDescription = null) },
        )
        Tab(
          selected = tab == OrganizerTab.SCHEDULES,
          onClick = { tab = OrganizerTab.SCHEDULES },
          text = { Text("Schedules") },
          icon = { Icon(Icons.Rounded.Event, contentDescription = null) },
        )
      }
      if (tab == OrganizerTab.MEMORY) {
        MemoryList(
          entries = entries,
          onAdd = { editingEntry = MemoryEntry.getDefaultInstance() },
          onEdit = { editingEntry = it },
          onDelete = { deletingEntry = it },
        )
      } else {
        SchedulesList(
          schedules = schedules,
          canScheduleExact = viewModel.canScheduleExactAlarms(),
          onRequestExactAlarm = {
            viewModel.exactAlarmSettingsIntent()?.let { intent ->
              intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              try {
                context.startActivity(intent)
              } catch (_: Exception) {
                // Android may not expose the exact-alarm settings page on some
                // device builds; the schedule remains usable inexactly.
              }
            }
          },
          onDelete = { deletingSchedule = it },
        )
      }
    }
  }

  editingEntry?.let { entry ->
    MemoryEditorDialog(
      entry = entry,
      onDismiss = { editingEntry = null },
      onSave = { title, body ->
        if (entry.id.isBlank()) viewModel.addMemory(title, body)
        else viewModel.updateMemory(entry, title, body)
        editingEntry = null
      },
    )
  }
  deletingEntry?.let { entry ->
    ConfirmDeleteDialog(
      title = "Delete memory?",
      message = if (entry.linkedScheduleId.isBlank()) {
        "This note will be removed from Trace."
      } else {
        "This reminder and its scheduled notification will be cancelled."
      },
      onDismiss = { deletingEntry = null },
      onConfirm = { viewModel.removeMemory(entry); deletingEntry = null },
    )
  }
  deletingSchedule?.let { schedule ->
    ConfirmDeleteDialog(
      title = "Cancel schedule?",
      message = "Trace will stop this notification${if (schedule.repeatDaily) " and its daily repeat" else ""}.",
      onDismiss = { deletingSchedule = null },
      onConfirm = { viewModel.removeSchedule(schedule); deletingSchedule = null },
    )
  }
}

@Composable
private fun MemoryList(
  entries: List<MemoryEntry>,
  onAdd: () -> Unit,
  onEdit: (MemoryEntry) -> Unit,
  onDelete: (MemoryEntry) -> Unit,
) {
  if (entries.isEmpty()) {
    EmptyOrganizerState(
      icon = Icons.Rounded.Lightbulb,
      title = "Keep what matters",
      body = "Save a note for Trace, or create a reminder from a prescription or attached document.",
      action = "Add memory",
      onAction = onAdd,
    )
    return
  }
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    items(entries, key = { it.id }) { entry ->
      MemoryCard(entry = entry, onEdit = { onEdit(entry) }, onDelete = { onDelete(entry) })
    }
  }
}

@Composable
private fun MemoryCard(entry: MemoryEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
  val isSystem = entry.kind == MemoryKind.SYSTEM_AUTHORED
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          if (isSystem) Icons.Rounded.AutoAwesome else Icons.Rounded.NoteAlt,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit memory") }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete memory") }
      }
      if (entry.body.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(entry.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Spacer(Modifier.height(10.dp))
      AssistChip(
        onClick = {},
        label = { Text(memorySourceLabel(entry)) },
        leadingIcon = {
          Icon(if (entry.linkedScheduleId.isBlank()) Icons.Rounded.NoteAlt else Icons.Rounded.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
        },
      )
    }
  }
}

@Composable
private fun SchedulesList(
  schedules: List<ScheduledNotification>,
  canScheduleExact: Boolean,
  onRequestExactAlarm: () -> Unit,
  onDelete: (ScheduledNotification) -> Unit,
) {
  if (schedules.isEmpty()) {
    EmptyOrganizerState(
      icon = Icons.Rounded.Event,
      title = "No schedules yet",
      body = "Ask Trace to set a reminder from a prescription, document, or your Notes quiz plan.",
      action = null,
      onAction = {},
    )
    return
  }
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (!canScheduleExact) {
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.LockClock, contentDescription = null)
              Spacer(Modifier.size(10.dp))
              Text("Allow precise reminders", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text("Without this Android permission, reminders still work but can be delayed while your phone sleeps.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onRequestExactAlarm) { Text("Open Android settings") }
          }
        }
      }
    }
    items(schedules.sortedWith(compareBy<ScheduledNotification> { it.hour }.thenBy { it.minute }), key = { it.id }) { schedule ->
      ScheduleCard(schedule = schedule, onDelete = { onDelete(schedule) })
    }
  }
}

@Composable
private fun ScheduleCard(schedule: ScheduledNotification, onDelete: () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(schedule.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(formatScheduleTime(schedule), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Cancel schedule") }
      }
      if (schedule.message.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(schedule.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (schedule.deeplink.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        AssistChip(onClick = {}, label = { Text("Opens a Notes quiz") }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp)) })
      }
    }
  }
}

@Composable
private fun EmptyOrganizerState(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  body: String,
  action: String?,
  onAction: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (action != null) {
      Spacer(Modifier.height(20.dp))
      Button(onClick = onAction) { Text(action) }
    }
  }
}

@Composable
private fun MemoryEditorDialog(entry: MemoryEntry, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
  var title by remember(entry.id) { mutableStateOf(entry.title) }
  var body by remember(entry.id) { mutableStateOf(entry.body) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (entry.id.isBlank()) "New memory" else "Edit memory") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
        androidx.compose.material3.OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Details") }, minLines = 3, maxLines = 6)
        if (entry.linkedScheduleId.isNotBlank()) {
          Text("Saving also updates the linked notification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    },
    confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onSave(title, body) }, enabled = title.isNotBlank()) { Text("Save") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun ConfirmDeleteDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

private fun memorySourceLabel(entry: MemoryEntry): String = when {
  entry.linkedScheduleId.isNotBlank() -> "Reminder"
  entry.source == MemorySource.MEMORY_SOURCE_VISION_PRESCRIPTION -> "From Vision"
  entry.source == MemorySource.MEMORY_SOURCE_CHAT_DOCUMENT -> "From document"
  else -> "Your note"
}

private fun formatScheduleTime(schedule: ScheduledNotification): String {
  val time = String.format(Locale.getDefault(), "%02d:%02d", schedule.hour, schedule.minute)
  return if (schedule.repeatDaily) "$time · Every day" else "$time · Once"
}
