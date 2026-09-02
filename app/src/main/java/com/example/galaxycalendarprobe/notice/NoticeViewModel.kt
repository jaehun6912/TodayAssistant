package com.example.galaxycalendarprobe.notice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoticeViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = NoticePreferences(application.applicationContext)
    private val monitor = NoticeMonitor(application.applicationContext)
    private val scheduler = NoticeScheduler(application.applicationContext)
    private val _uiState = MutableStateFlow(monitor.cachedState())
    val uiState: StateFlow<NoticeUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            runCatching {
                withContext(Dispatchers.IO) { monitor.check() }
            }.onSuccess { result ->
                _uiState.value = _uiState.value.copy(
                    sources = result.sources,
                    isRefreshing = false,
                    lastCheckedMillis = result.checkedAtMillis,
                    newArticleKeys = result.newArticles.mapTo(mutableSetOf()) { it.key() },
                    newArticleCount = result.newArticles.size,
                )
                if (_uiState.value.monitoringEnabled) scheduler.scheduleNext()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    sources = _uiState.value.sources.map { state ->
                        if (!state.enabled) state
                        else state.copy(
                            error = error.message ?: "공지사항을 확인하지 못했습니다.",
                        )
                    },
                )
            }
        }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        preferences.monitoringEnabled = enabled
        _uiState.value = _uiState.value.copy(
            monitoringEnabled = enabled,
            notificationError = null,
        )
        if (enabled) {
            scheduler.scheduleNext()
            refresh()
        } else {
            scheduler.cancel()
        }
    }

    fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        val disabled = preferences.disabledSourceIds.toMutableSet()
        if (enabled) disabled -= sourceId else disabled += sourceId
        preferences.disabledSourceIds = disabled
        _uiState.value = _uiState.value.copy(
            sources = _uiState.value.sources.map { state ->
                if (state.source.id == sourceId) state.copy(enabled = enabled, error = null)
                else state
            },
        )
        if (enabled) refresh()
    }

    fun addCustomSource(name: String, url: String): String? {
        validateCustomSource(name, url)?.let { return it }
        return runCatching {
            refreshJob?.cancel()
            preferences.addCustomSource(name, url)
            _uiState.value = monitor.cachedState()
            refresh()
        }.exceptionOrNull()?.message
    }

    fun updateSource(sourceId: String, name: String, url: String): String? {
        validateCustomSource(name, url)?.let { return it }
        val state = _uiState.value.sources.firstOrNull { it.source.id == sourceId }
        if (state == null) {
            return "수정할 사이트를 찾지 못했습니다."
        }
        if (state.source.isBuiltIn && state.locked) return "잠금을 해제한 후 수정해 주세요."
        return runCatching {
            refreshJob?.cancel()
            if (state.source.isBuiltIn) {
                preferences.updateBuiltInSource(sourceId, name, url)
            } else {
                preferences.updateCustomSource(sourceId, name, url)
            }
            _uiState.value = monitor.cachedState()
            refresh()
        }.exceptionOrNull()?.message
    }

    fun setBuiltInSourceLocked(sourceId: String, locked: Boolean) {
        if (_uiState.value.sources.none { it.source.id == sourceId && it.source.isBuiltIn }) return
        preferences.setBuiltInLocked(sourceId, locked)
        _uiState.value = monitor.cachedState()
    }

    fun restoreBuiltInSource(sourceId: String) {
        if (_uiState.value.sources.none { it.source.id == sourceId && it.source.isBuiltIn }) return
        refreshJob?.cancel()
        preferences.restoreBuiltInSource(sourceId)
        _uiState.value = monitor.cachedState()
        refresh()
    }

    fun deleteCustomSource(sourceId: String) {
        if (_uiState.value.sources.none { it.source.id == sourceId && !it.source.isBuiltIn }) return
        refreshJob?.cancel()
        preferences.deleteCustomSource(sourceId)
        _uiState.value = monitor.cachedState()
    }

    fun onNotificationPermissionDenied() {
        val wasEnabled = _uiState.value.monitoringEnabled
        preferences.monitoringEnabled = false
        scheduler.cancel()
        _uiState.value = _uiState.value.copy(
            monitoringEnabled = false,
            notificationError = if (wasEnabled) {
                "1시간 자동 확인을 사용하려면 알림 권한이 필요합니다."
            } else {
                null
            },
        )
    }
}
