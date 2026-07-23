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

package com.trace.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.trace.app.AppLifecycleProvider
import com.trace.app.BenchmarkResultsSerializer
import com.trace.app.BuildConfig
import com.trace.app.CutoutsSerializer
import com.trace.app.GalleryLifecycleProvider
import com.trace.app.NotesIndexSerializer
import com.trace.app.SettingsSerializer
import com.trace.app.SkillsSerializer
import com.trace.app.UserDataSerializer
import com.trace.app.data.DataStoreRepository
import com.trace.app.data.DefaultDataStoreRepository
import com.trace.app.data.DefaultDownloadRepository
import com.trace.app.data.DownloadRepository
import com.trace.app.proto.BenchmarkResults
import com.trace.app.proto.CutoutCollection
import com.trace.app.proto.NotesIndex
import com.trace.app.proto.Settings
import com.trace.app.proto.Skills
import com.trace.app.proto.UserData
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AppModule {

  // Provides the SettingsSerializer
  @Provides
  @Singleton
  fun provideSettingsSerializer(): Serializer<Settings> {
    return SettingsSerializer
  }

  // Provides the CutoutSerializer
  @Provides
  @Singleton
  fun provideCutoutSerializer(): Serializer<CutoutCollection> {
    return CutoutsSerializer
  }

  // Provides the UserDataSerializer
  @Provides
  @Singleton
  fun provideUserDataSerializer(): Serializer<UserData> {
    return UserDataSerializer
  }

  // Provides the BenchmarkResultsSerializer
  @Provides
  @Singleton
  fun provideBenchmarkResultsSerializer(): Serializer<BenchmarkResults> {
    return BenchmarkResultsSerializer
  }

  // Provides the SkillsSerializer
  @Provides
  @Singleton
  fun provideSkillsSerializer(): Serializer<Skills> {
    return SkillsSerializer
  }

  // Provides DataStore<Settings>
  @Provides
  @Singleton
  fun provideSettingsDataStore(
    @ApplicationContext context: Context,
    settingsSerializer: Serializer<Settings>,
  ): DataStore<Settings> {
    return DataStoreFactory.create(
      serializer = settingsSerializer,
      produceFile = { context.dataStoreFile("settings.pb") },
    )
  }

  // Provides DataStore<CutoutCollection>
  @Provides
  @Singleton
  fun provideCutoutsDataStore(
    @ApplicationContext context: Context,
    cutoutsSerializer: Serializer<CutoutCollection>,
  ): DataStore<CutoutCollection> {
    return DataStoreFactory.create(
      serializer = cutoutsSerializer,
      produceFile = { context.dataStoreFile("cutouts.pb") },
    )
  }

  // Provides DataStore<UserData>
  @Provides
  @Singleton
  fun provideUserDataDataStore(
    @ApplicationContext context: Context,
    userDataSerializer: Serializer<UserData>,
  ): DataStore<UserData> {
    return DataStoreFactory.create(
      serializer = userDataSerializer,
      produceFile = { context.dataStoreFile("user_data.pb") },
    )
  }

  // Provides the NotesIndexSerializer
  @Provides
  @Singleton
  fun provideNotesIndexSerializer(): Serializer<NotesIndex> {
    return NotesIndexSerializer
  }

  // Provides DataStore<NotesIndex> — persisted Notes/RAG sources (extracted text,
  // re-embedded on launch).
  @Provides
  @Singleton
  fun provideNotesIndexDataStore(
    @ApplicationContext context: Context,
    notesIndexSerializer: Serializer<NotesIndex>,
  ): DataStore<NotesIndex> {
    return DataStoreFactory.create(
      serializer = notesIndexSerializer,
      produceFile = { context.dataStoreFile("notes_index.pb") },
    )
  }

  // Provides DataStore<BenchmarkResults>
  @Provides
  @Singleton
  fun provideBenchmarkResultsDataStore(
    @ApplicationContext context: Context,
    benchmarkResultsSerializer: Serializer<BenchmarkResults>,
  ): DataStore<BenchmarkResults> {
    return DataStoreFactory.create(
      serializer = benchmarkResultsSerializer,
      produceFile = { context.dataStoreFile("benchmark_results.pb") },
    )
  }

  // Provides DataStore<Skills>
  @Provides
  @Singleton
  fun provideSkillsDataStore(
    @ApplicationContext context: Context,
    skillsSerializer: Serializer<Skills>,
  ): DataStore<Skills> {
    return DataStoreFactory.create(
      serializer = skillsSerializer,
      produceFile = { context.dataStoreFile("skills.pb") },
    )
  }

  // Provides AppLifecycleProvider
  @Provides
  @Singleton
  fun provideAppLifecycleProvider(): AppLifecycleProvider {
    return GalleryLifecycleProvider()
  }

  // Provides DataStoreRepository
  @Provides
  @Singleton
  fun provideDataStoreRepository(
    dataStore: DataStore<Settings>,
    userDataDataStore: DataStore<UserData>,
    cutoutsDataStore: DataStore<CutoutCollection>,
    benchmarkResultsStore: DataStore<BenchmarkResults>,
    skillsDataStore: DataStore<Skills>,
  ): DataStoreRepository {
    return DefaultDataStoreRepository(
      dataStore,
      userDataDataStore,
      cutoutsDataStore,
      benchmarkResultsStore,
      skillsDataStore,
    )
  }

  // Provides DownloadRepository
  @Provides
  @Singleton
  fun provideDownloadRepository(
    @ApplicationContext context: Context,
    lifecycleProvider: AppLifecycleProvider,
  ): DownloadRepository {
    return DefaultDownloadRepository(context, lifecycleProvider)
  }

  @Provides
  @Singleton
  fun provideMoshi(): Moshi {
    return Moshi.Builder().build()
  }
}
