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

package com.google.ai.edge.gallery.rag

import android.content.Context
import androidx.datastore.core.DataStore
import com.google.ai.edge.gallery.proto.NotesIndex
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the app-wide [RagEngine] singleton so AI Chat and the RAG module
 * share one index: notes attached in chat are queryable from the RAG screen and
 * vice versa. The engine's embedder loads lazily on first ingest, so this adds
 * no startup cost.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object RagDiModule {
  @Provides
  @Singleton
  fun provideRagEngine(
    @ApplicationContext context: Context,
    notesIndexStore: DataStore<NotesIndex>,
  ): RagEngine {
    return RagEngine(context, notesIndexStore)
  }
}
