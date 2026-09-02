package com.example.galaxycalendarprobe.todo

import com.example.galaxycalendarprobe.notifications.SummarySchedule

data class TodoListInfo(
    val id: String,
    val displayName: String,
)

data class TodoTaskItem(
    val id: String,
    val listId: String,
    val title: String,
    val importance: String,
    val dueAtMillis: Long?,
    val reminderAtMillis: Long?,
) {
    val effectiveAtMillis: Long?
        get() = listOfNotNull(dueAtMillis, reminderAtMillis).minOrNull()
}

data class TodoUiState(
    val enabled: Boolean = false,
    val microsoftAccount: String? = null,
    val isInitializing: Boolean = true,
    val isSyncing: Boolean = false,
    val schedules: List<SummarySchedule> = emptyList(),
    val lists: List<TodoListInfo> = emptyList(),
    val tasks: List<TodoTaskItem> = emptyList(),
    val disabledListIds: Set<String> = emptySet(),
    val lastSyncMillis: Long = 0L,
    val scheduledCount: Int = 0,
    val error: String? = null,
)
