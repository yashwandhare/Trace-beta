package com.trace.app.ui.memory

import androidx.lifecycle.ViewModel
import com.trace.app.memory.MemoryRepository
import com.trace.app.notifications.NotificationScheduleManager
import com.trace.app.proto.MemoryEntry
import com.trace.app.proto.ScheduledNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Thin UI boundary over the already-owned Memory and scheduling backends. */
@HiltViewModel
class MemoryScheduleViewModel @Inject constructor(
  private val memoryRepository: MemoryRepository,
  private val scheduleManager: NotificationScheduleManager,
) : ViewModel() {
  val entries: StateFlow<List<MemoryEntry>> = memoryRepository.entries
  val schedules: StateFlow<List<ScheduledNotification>> = scheduleManager.scheduledNotifications

  fun addMemory(title: String, body: String) = memoryRepository.add(title.trim(), body.trim())

  fun updateMemory(entry: MemoryEntry, title: String, body: String): Boolean {
    memoryRepository.update(entry.id, title.trim(), body.trim())
    val scheduleId = entry.linkedScheduleId
    val schedule = schedules.value.find { it.id == scheduleId }
    if (schedule != null) {
      return scheduleManager.updateNotification(
        schedule.toBuilder().setTitle(title.trim()).setMessage(body.trim().ifBlank { title.trim() }).build()
      )
    }
    return true
  }

  /** Removes both sides of a linked reminder; plain notes only remove their Memory entry. */
  fun removeMemory(entry: MemoryEntry) {
    if (entry.linkedScheduleId.isNotBlank()) scheduleManager.removeNotification(entry.linkedScheduleId)
    memoryRepository.remove(entry.id)
  }

  fun removeSchedule(schedule: ScheduledNotification) {
    scheduleManager.removeNotification(schedule.id)
    memoryRepository.get(schedule.id)?.let { memoryRepository.remove(it.id) }
  }

  fun canScheduleExactAlarms(): Boolean = scheduleManager.canScheduleExactAlarms()
  fun exactAlarmSettingsIntent() = scheduleManager.buildExactAlarmSettingsIntent()
}
