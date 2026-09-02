package com.example.galaxycalendarprobe.todo

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.galaxycalendarprobe.notifications.SummarySchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = TodoPreferences(application)
    private val repository = MicrosoftTodoRepository(application)
    private val scheduler = TodoScheduler(application)
    private var initializing = true
    private var syncing = false
    private val _uiState = MutableStateFlow(readState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.loadAccount() }
                .onFailure { preferences.lastError = friendlyError(it) }
            initializing = false
            refreshState()
            if (preferences.enabled) reschedule()
        }
    }

    fun authorizeAndSync(activity: Activity) {
        if (syncing) return
        viewModelScope.launch {
            syncing = true
            _uiState.value = _uiState.value.copy(isSyncing = true, error = null)
            runCatching { repository.authorizeAndSync(activity) }
                .onSuccess {
                    syncing = false
                    preferences.lastError = null
                    refreshState()
                    if (preferences.enabled) reschedule()
                }
                .onFailure {
                    syncing = false
                    preferences.lastError = friendlyError(it)
                    refreshState()
                }
        }
    }

    fun syncNow() {
        if (syncing || preferences.microsoftAccount == null) return
        viewModelScope.launch {
            syncing = true
            _uiState.value = _uiState.value.copy(isSyncing = true, error = null)
            runCatching { repository.syncIfConnected() }
                .onSuccess { syncing = false; preferences.lastError = null; refreshState() }
                .onFailure { syncing = false; preferences.lastError = friendlyError(it); refreshState() }
        }
    }

    fun onMicrosoftAccountDisconnected() {
        scheduler.cancelAll()
        preferences.clearDisconnectedAccount()
        initializing = false
        syncing = false
        refreshState()
    }

    fun setEnabled(enabled: Boolean) {
        preferences.enabled = enabled
        refreshState()
        if (enabled) reschedule() else scheduler.cancelAll()
    }

    fun setListEnabled(id: String, enabled: Boolean) {
        val disabled = preferences.disabledListIds.toMutableSet()
        if (enabled) disabled -= id else disabled += id
        preferences.disabledListIds = disabled
        refreshState()
    }

    fun addSchedule(hour: Int, minute: Int) {
        if (hour !in 0..23 || minute !in 0..59) return
        if (_uiState.value.schedules.any { it.hour == hour && it.minute == minute }) {
            _uiState.value = _uiState.value.copy(error = "같은 시각의 할 일 알림이 이미 있습니다.")
            return
        }
        saveSchedules(_uiState.value.schedules + preferences.newSchedule(hour, minute))
    }

    fun updateSchedule(id: Int, hour: Int, minute: Int) {
        if (_uiState.value.schedules.any { it.id != id && it.hour == hour && it.minute == minute }) {
            _uiState.value = _uiState.value.copy(error = "같은 시각의 할 일 알림이 이미 있습니다.")
            return
        }
        saveSchedules(_uiState.value.schedules.map { if (it.id == id) it.copy(hour = hour, minute = minute) else it })
    }

    fun deleteSchedule(id: Int) = saveSchedules(_uiState.value.schedules.filterNot { it.id == id })

    fun notifyNow() {
        viewModelScope.launch {
            runCatching { repository.syncIfConnected() }
            withContext(Dispatchers.IO) { scheduler.deliver(999) }
            refreshState()
        }
    }

    private fun saveSchedules(value: List<SummarySchedule>) {
        preferences.schedules = value
        refreshState()
        if (preferences.enabled) reschedule()
    }

    private fun reschedule() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { scheduler.reschedule() }
            _uiState.value = readState().copy(scheduledCount = count)
        }
    }

    private fun refreshState() {
        _uiState.value = readState().copy(scheduledCount = _uiState.value.scheduledCount)
    }

    private fun readState() = TodoUiState(
        enabled = preferences.enabled,
        microsoftAccount = preferences.microsoftAccount,
        isInitializing = initializing,
        isSyncing = syncing,
        schedules = preferences.schedules,
        lists = preferences.lists,
        tasks = preferences.tasks,
        disabledListIds = preferences.disabledListIds,
        lastSyncMillis = preferences.lastSyncMillis,
        error = preferences.lastError,
    )

    private fun friendlyError(error: Throwable) = error.localizedMessage?.takeIf(String::isNotBlank)
        ?: "Microsoft To Do를 확인하지 못했습니다."
}
