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
package com.trace.app.memory

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.trace.app.proto.MemoryEntry
import com.trace.app.proto.MemoryKind
import com.trace.app.proto.MemorySource
import com.trace.app.proto.MemoryStore
import com.google.protobuf.InvalidProtocolBufferException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The single source of truth for Trace's [MemoryEntry] records — user-authored
 * notes and system-authored reminders (see `memory_store.proto`).
 *
 * Mirrors the [com.trace.app.notifications.NotificationScheduleManager]
 * pattern: a proto DataStore for persistence plus an observable in-memory
 * [StateFlow] the UI collects. Thread-safe global singleton; all disk writes run
 * on an IO scope so callers never block.
 *
 * This is the interface Dev C2's Memory sidebar binds to. Reminder entries carry
 * a [MemoryEntry.getLinkedScheduleId] that matches a
 * [com.trace.app.proto.ScheduledNotification.getId], so the two
 * stores stay in sync — the wiring layer (Vision/Chat → schedule) creates both.
 */
@Singleton
class MemoryRepository
@Inject
constructor(@ApplicationContext private val context: Context) {
  private val fileName = "memory_store.pb"
  private val dataStore: DataStore<MemoryStore> =
    DataStoreFactory.create(
      serializer = MemoryStoreSerializer,
      produceFile = { File(context.filesDir, fileName) },
    )
  private val TAG = "MemoryRepository"

  private val coroutineScope = CoroutineScope(Dispatchers.IO)

  // Newest-first, so the UI can render the flow directly without re-sorting.
  private val _entries = MutableStateFlow<List<MemoryEntry>>(emptyList())
  val entries: StateFlow<List<MemoryEntry>> = _entries.asStateFlow()

  init {
    load()
  }

  private fun load() {
    coroutineScope.launch {
      try {
        val file = File(context.filesDir, fileName)
        if (file.exists()) {
          val data = file.inputStream().use { MemoryStoreSerializer.readFrom(it) }
          _entries.value = data.entryList.sortedByDescending { it.updatedAtMs }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to load memory store", e)
      }
    }
  }

  private fun persist() {
    coroutineScope.launch {
      dataStore.updateData {
        MemoryStore.newBuilder().addAllEntry(_entries.value).build()
      }
    }
  }

  /**
   * Adds a new entry and returns it (with a generated id + timestamps filled in).
   * Pass an [id] only when the caller needs it to match a schedule id it already
   * minted; otherwise a UUID is generated.
   */
  fun add(
    title: String,
    body: String,
    kind: MemoryKind = MemoryKind.USER_AUTHORED,
    source: MemorySource = MemorySource.MEMORY_SOURCE_MANUAL,
    linkedScheduleId: String = "",
    id: String = UUID.randomUUID().toString(),
  ): MemoryEntry {
    val now = System.currentTimeMillis()
    val entry =
      MemoryEntry.newBuilder()
        .setId(id)
        .setKind(kind)
        .setSource(source)
        .setTitle(title)
        .setBody(body)
        .setLinkedScheduleId(linkedScheduleId)
        .setCreatedAtMs(now)
        .setUpdatedAtMs(now)
        .build()
    _entries.update { (listOf(entry) + it) }
    persist()
    return entry
  }

  /**
   * Updates the title/body of an existing entry (bumping updatedAtMs). No-op if
   * the id is unknown. Kind/source/link are immutable after creation.
   */
  fun update(id: String, title: String, body: String) {
    _entries.update { list ->
      list
        .map { entry ->
          if (entry.id == id) {
            entry.toBuilder()
              .setTitle(title)
              .setBody(body)
              .setUpdatedAtMs(System.currentTimeMillis())
              .build()
          } else {
            entry
          }
        }
        .sortedByDescending { it.updatedAtMs }
    }
    persist()
  }

  /** Removes an entry by id. No-op if unknown. */
  fun remove(id: String) {
    _entries.update { list -> list.filter { it.id != id } }
    persist()
  }

  /** Returns the current entry with [id], or null. */
  fun get(id: String): MemoryEntry? = _entries.value.find { it.id == id }
}

object MemoryStoreSerializer : Serializer<MemoryStore> {
  override val defaultValue: MemoryStore = MemoryStore.getDefaultInstance()

  override suspend fun readFrom(input: InputStream): MemoryStore {
    try {
      return MemoryStore.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
      throw CorruptionException("Cannot read proto.", exception)
    }
  }

  override suspend fun writeTo(t: MemoryStore, output: OutputStream) = t.writeTo(output)
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface MemoryRepositoryEntryPoint {
  fun memoryRepository(): MemoryRepository
}
