package com.example.galaxycalendarprobe.mail

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.galaxycalendarprobe.notifications.SummarySchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MailViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = MailPreferences(application.applicationContext)
    private val scheduler = MailScheduler(application.applicationContext)
    private val outlookRepository = OutlookMailRepository(application.applicationContext)
    private val gmailRepository = GmailMailRepository(application.applicationContext)
    private var outlookInitializing = true
    private var outlookSyncing = false
    private var gmailSyncing = false
    private var gmailAuthorizationForConnection = true
    private val _uiState = MutableStateFlow(readState())
    val uiState: StateFlow<MailUiState> = _uiState.asStateFlow()

    init {
        initializeOutlookAccount()
    }

    fun refresh() {
        _uiState.value = readState().copy(
            scheduledCount = _uiState.value.scheduledCount,
        )
        if (_uiState.value.outlookAccount != null) syncOutlookNow()
    }

    fun setEnabled(enabled: Boolean) {
        preferences.enabled = enabled
        _uiState.value = _uiState.value.copy(enabled = enabled, error = null)
        if (enabled) reschedule() else {
            scheduler.cancelAll()
            _uiState.value = _uiState.value.copy(scheduledCount = 0)
        }
    }

    fun addSchedule(hour: Int, minute: Int) {
        if (hour !in 0..23 || minute !in 0..59) return
        if (_uiState.value.schedules.any { it.hour == hour && it.minute == minute }) {
            _uiState.value = _uiState.value.copy(error = "같은 시각의 메일 알림이 이미 있습니다.")
            return
        }
        saveSchedules(
            _uiState.value.schedules + preferences.newSchedule(hour, minute),
        )
    }

    fun updateSchedule(id: Int, hour: Int, minute: Int) {
        if (hour !in 0..23 || minute !in 0..59) return
        if (_uiState.value.schedules.any {
                it.id != id && it.hour == hour && it.minute == minute
            }
        ) {
            _uiState.value = _uiState.value.copy(error = "같은 시각의 메일 알림이 이미 있습니다.")
            return
        }
        saveSchedules(
            _uiState.value.schedules.map { schedule ->
                if (schedule.id == id) schedule.copy(hour = hour, minute = minute)
                else schedule
            },
        )
    }

    fun deleteSchedule(id: Int) {
        saveSchedules(_uiState.value.schedules.filterNot { it.id == id })
    }

    fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        val disabled = preferences.disabledSourceIds.toMutableSet()
        if (enabled) disabled -= sourceId else {
            disabled += sourceId
            preferences.removePendingMailsForSource(sourceId)
        }
        preferences.disabledSourceIds = disabled
        if (sourceId == OUTLOOK_SOURCE_ID) preferences.resetOutlookBaseline()
        if (sourceId == GMAIL_SOURCE_ID) preferences.resetGmailBaseline()
        refresh()
        if (_uiState.value.enabled) reschedule()
    }

    fun onNotificationAccessChanged(granted: Boolean) {
        _uiState.value = readState().copy(
            notificationAccessGranted = granted,
            scheduledCount = _uiState.value.scheduledCount,
        )
        if (_uiState.value.enabled) {
            if (granted) scheduler.requestListenerRebind()
            reschedule()
        }
    }

    fun onNotificationPermissionDenied() {
        val wasEnabled = preferences.enabled
        preferences.enabled = false
        scheduler.cancelAll()
        _uiState.value = readState().copy(
            error = if (wasEnabled) "새 메일 요약을 받으려면 알림 권한이 필요합니다." else null,
        )
    }

    fun deliverPendingNow() {
        viewModelScope.launch {
            outlookSyncing = true
            _uiState.value = _uiState.value.copy(isOutlookSyncing = true, error = null)
            runCatching {
                if (_uiState.value.outlookAccount != null) {
                    outlookRepository.syncIfConnected()
                }
                if (_uiState.value.gmailAccount != null) {
                    gmailRepository.syncIfConnected()
                }
                withContext(Dispatchers.IO) {
                    scheduler.deliverPending(MANUAL_SCHEDULE_ID)
                }
            }.onSuccess {
                outlookSyncing = false
                _uiState.value = readState().copy(
                    scheduledCount = _uiState.value.scheduledCount,
                    isOutlookSyncing = false,
                )
            }.onFailure { error ->
                outlookSyncing = false
                preferences.lastOutlookError = friendlyOutlookError(error)
                _uiState.value = _uiState.value.copy(
                    isOutlookSyncing = false,
                    outlookError = preferences.lastOutlookError,
                    error = "메일을 확인했지만 요약 알림을 보내지 못했습니다.",
                )
            }
        }
    }

    fun connectOutlook(activity: Activity) {
        viewModelScope.launch {
            outlookInitializing = false
            outlookSyncing = true
            _uiState.value = _uiState.value.copy(
                isOutlookInitializing = false,
                isOutlookSyncing = true,
                outlookError = null,
            )
            runCatching { outlookRepository.signIn(activity) }
                .onSuccess {
                    outlookSyncing = false
                    preferences.lastOutlookError = null
                    _uiState.value = readState().copy(
                        scheduledCount = _uiState.value.scheduledCount,
                        isOutlookInitializing = false,
                        isOutlookSyncing = false,
                    )
                    if (_uiState.value.enabled) reschedule()
                }
                .onFailure { error ->
                    outlookSyncing = false
                    preferences.lastOutlookError = friendlyOutlookError(error)
                    _uiState.value = readState().copy(
                        scheduledCount = _uiState.value.scheduledCount,
                        isOutlookInitializing = false,
                        isOutlookSyncing = false,
                    )
                }
        }
    }

    fun disconnectOutlook(onDisconnected: () -> Unit = {}) {
        viewModelScope.launch {
            outlookSyncing = true
            _uiState.value = _uiState.value.copy(isOutlookSyncing = true, outlookError = null)
            runCatching { outlookRepository.signOut() }
                .onSuccess {
                    outlookSyncing = false
                    onDisconnected()
                    _uiState.value = readState().copy(
                        scheduledCount = _uiState.value.scheduledCount,
                        isOutlookInitializing = false,
                        isOutlookSyncing = false,
                    )
                    if (_uiState.value.enabled) reschedule()
                }
                .onFailure { error ->
                    outlookSyncing = false
                    preferences.lastOutlookError = friendlyOutlookError(error)
                    _uiState.value = readState().copy(
                        scheduledCount = _uiState.value.scheduledCount,
                        isOutlookInitializing = false,
                        isOutlookSyncing = false,
                    )
                }
        }
    }

    fun syncOutlookNow() {
        if (_uiState.value.isOutlookSyncing || _uiState.value.outlookAccount == null) return
        viewModelScope.launch {
            outlookSyncing = true
            _uiState.value = _uiState.value.copy(isOutlookSyncing = true, outlookError = null)
            runCatching { outlookRepository.syncIfConnected() }
                .onSuccess {
                    outlookSyncing = false
                    _uiState.value = readState().copy(
                        scheduledCount = _uiState.value.scheduledCount,
                        isOutlookInitializing = false,
                        isOutlookSyncing = false,
                    )
                }
                .onFailure { error ->
                    outlookSyncing = false
                    preferences.lastOutlookError = friendlyOutlookError(error)
                    _uiState.value = readState().copy(
                        scheduledCount = _uiState.value.scheduledCount,
                        isOutlookInitializing = false,
                        isOutlookSyncing = false,
                    )
                }
        }
    }

    fun connectGmail(onNeedsUserAction: (PendingIntent) -> Unit) {
        authorizeAndSyncGmail(connecting = true, onNeedsUserAction = onNeedsUserAction)
    }

    fun syncGmailNow(onNeedsUserAction: (PendingIntent) -> Unit) {
        if (gmailSyncing || _uiState.value.gmailAccount == null) return
        authorizeAndSyncGmail(connecting = false, onNeedsUserAction = onNeedsUserAction)
    }

    fun completeGmailAuthorization(data: Intent?) {
        if (data == null) {
            gmailSyncing = false
            preferences.lastGmailError = "Google 계정 연결을 취소했습니다."
            refreshGmailState()
            return
        }
        viewModelScope.launch {
            gmailSyncing = true
            _uiState.value = _uiState.value.copy(isGmailSyncing = true, gmailError = null)
            runCatching {
                val authorization = gmailRepository.authorizationFromIntent(data)
                if (gmailAuthorizationForConnection || !preferences.gmailDirectConnected) {
                    gmailRepository.finishConnection(authorization.accessToken)
                } else {
                    gmailRepository.syncWithToken(authorization.accessToken)
                }
            }.onSuccess {
                gmailSyncing = false
                preferences.lastGmailError = null
                refreshGmailState()
                if (_uiState.value.enabled) reschedule()
            }.onFailure { error ->
                gmailSyncing = false
                preferences.lastGmailError = friendlyGmailError(error)
                refreshGmailState()
            }
        }
    }

    fun disconnectGmail() {
        if (gmailSyncing) return
        viewModelScope.launch {
            gmailSyncing = true
            _uiState.value = _uiState.value.copy(isGmailSyncing = true, gmailError = null)
            runCatching { gmailRepository.disconnect() }
                .onSuccess {
                    gmailSyncing = false
                    refreshGmailState()
                    if (_uiState.value.enabled) reschedule()
                }
                .onFailure { error ->
                    gmailSyncing = false
                    preferences.lastGmailError = friendlyGmailError(error)
                    refreshGmailState()
                }
        }
    }

    private fun authorizeAndSyncGmail(
        connecting: Boolean,
        onNeedsUserAction: (PendingIntent) -> Unit,
    ) {
        if (gmailSyncing) return
        gmailAuthorizationForConnection = connecting
        viewModelScope.launch {
            gmailSyncing = true
            _uiState.value = _uiState.value.copy(isGmailSyncing = true, gmailError = null)
            runCatching { gmailRepository.requestAuthorization() }
                .onSuccess { authorization ->
                    when (authorization) {
                        is GmailAuthorization.NeedsUserAction -> {
                            onNeedsUserAction(authorization.pendingIntent)
                        }
                        is GmailAuthorization.Authorized -> {
                            runCatching {
                                if (connecting || !preferences.gmailDirectConnected) {
                                    gmailRepository.finishConnection(authorization.accessToken)
                                } else {
                                    gmailRepository.syncWithToken(authorization.accessToken)
                                }
                            }.onSuccess {
                                gmailSyncing = false
                                preferences.lastGmailError = null
                                refreshGmailState()
                                if (_uiState.value.enabled) reschedule()
                            }.onFailure { error ->
                                gmailSyncing = false
                                preferences.lastGmailError = friendlyGmailError(error)
                                refreshGmailState()
                            }
                        }
                    }
                }
                .onFailure { error ->
                    gmailSyncing = false
                    preferences.lastGmailError = friendlyGmailError(error)
                    refreshGmailState()
                }
        }
    }

    private fun refreshGmailState() {
        _uiState.value = readState().copy(
            scheduledCount = _uiState.value.scheduledCount,
            isOutlookInitializing = false,
            isOutlookSyncing = outlookSyncing,
            isGmailSyncing = gmailSyncing,
        )
    }

    fun reschedule() {
        if (!_uiState.value.enabled) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScheduling = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { scheduler.reschedule() }
            }.onSuccess { count ->
                _uiState.value = readState().copy(
                    scheduledCount = count,
                    isScheduling = false,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isScheduling = false,
                    error = error.localizedMessage ?: "메일 알림을 예약하지 못했습니다.",
                )
            }
        }
    }

    private fun saveSchedules(schedules: List<SummarySchedule>) {
        preferences.schedules = schedules
        refresh()
        if (_uiState.value.enabled) reschedule()
    }

    private fun initializeOutlookAccount() {
        viewModelScope.launch {
            outlookInitializing = true
            _uiState.value = _uiState.value.copy(isOutlookInitializing = true)
            runCatching { outlookRepository.loadAccount() }
                .onSuccess {
                    outlookInitializing = false
                    _uiState.value = readState().copy(
                        scheduledCount = _uiState.value.scheduledCount,
                        isOutlookInitializing = false,
                    )
                    if (_uiState.value.enabled) reschedule()
                }
                .onFailure { error ->
                    outlookInitializing = false
                    preferences.lastOutlookError = friendlyOutlookError(error)
                    _uiState.value = readState().copy(
                        scheduledCount = _uiState.value.scheduledCount,
                        isOutlookInitializing = false,
                    )
                }
        }
    }

    private fun readState(): MailUiState = MailUiState(
        enabled = preferences.enabled,
        notificationAccessGranted = scheduler.hasNotificationAccess(),
        schedules = preferences.schedules,
        disabledSourceIds = preferences.disabledSourceIds,
        pendingMails = preferences.pendingMails,
        lastSummaryCheckMillis = preferences.lastSummaryCheckMillis,
        outlookAccount = if (preferences.outlookDirectConnected) {
            DirectMailAccount(preferences.outlookUsername ?: "Microsoft 계정")
        } else {
            null
        },
        isOutlookInitializing = outlookInitializing,
        isOutlookSyncing = outlookSyncing,
        outlookBaselineInitialized = preferences.outlookBaselineInitialized,
        lastOutlookSyncMillis = preferences.lastOutlookSyncMillis,
        lastOutlookNewMailCount = preferences.lastOutlookNewMailCount,
        outlookError = preferences.lastOutlookError,
        gmailAccount = if (preferences.gmailDirectConnected) {
            DirectMailAccount(preferences.gmailEmail ?: "Google 계정")
        } else {
            null
        },
        isGmailSyncing = gmailSyncing,
        gmailBaselineInitialized = !preferences.gmailHistoryId.isNullOrBlank(),
        lastGmailSyncMillis = preferences.lastGmailSyncMillis,
        lastGmailNewMailCount = preferences.lastGmailNewMailCount,
        gmailError = preferences.lastGmailError,
    )

    private fun friendlyOutlookError(error: Throwable): String =
        error.localizedMessage?.takeIf(String::isNotBlank)
            ?: "Outlook 계정을 확인하지 못했습니다. 잠시 뒤 다시 시도해 주세요."

    private fun friendlyGmailError(error: Throwable): String =
        error.localizedMessage?.takeIf(String::isNotBlank)
            ?: "Gmail 계정을 확인하지 못했습니다. 잠시 뒤 다시 시도해 주세요."

    companion object {
        private const val MANUAL_SCHEDULE_ID = 999
        private const val OUTLOOK_SOURCE_ID = "outlook"
        private const val GMAIL_SOURCE_ID = "gmail"
    }
}
