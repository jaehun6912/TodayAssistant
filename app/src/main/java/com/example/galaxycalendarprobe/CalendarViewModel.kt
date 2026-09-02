package com.example.galaxycalendarprobe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.galaxycalendarprobe.data.CalendarEventInstance
import com.example.galaxycalendarprobe.data.CalendarRepository
import com.example.galaxycalendarprobe.data.DeviceCalendar
import com.example.galaxycalendarprobe.notifications.NotificationPreferences
import com.example.galaxycalendarprobe.notifications.NotificationScheduler
import com.example.galaxycalendarprobe.notifications.SummarySchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CalendarUiState(
    val calendars: List<DeviceCalendar> = emptyList(),
    val overviewEvents: List<CalendarEventInstance> = emptyList(),
    val overviewWindowStartMillis: Long? = null,
    val overviewWindowEndMillis: Long? = null,
    val selectedCalendar: DeviceCalendar? = null,
    val events: List<CalendarEventInstance> = emptyList(),
    val eventWindowStartMillis: Long? = null,
    val eventWindowEndMillis: Long? = null,
    val isLoadingCalendars: Boolean = false,
    val isLoadingOverview: Boolean = false,
    val isLoadingEvents: Boolean = false,
    val calendarError: String? = null,
    val overviewError: String? = null,
    val eventError: String? = null,
    val notificationsEnabled: Boolean = false,
    val summarySchedules: List<SummarySchedule> = emptyList(),
    val excludedNotificationCalendarIds: Set<Long> = emptySet(),
    val scheduledNotificationCount: Int = 0,
    val isSchedulingNotifications: Boolean = false,
    val notificationError: String? = null,
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CalendarRepository(application.applicationContext)
    private val notificationPreferences = NotificationPreferences(
        application.applicationContext,
    )
    private val notificationScheduler = NotificationScheduler(
        application.applicationContext,
    )
    private val _uiState = MutableStateFlow(
        CalendarUiState(
            notificationsEnabled = notificationPreferences.enabled,
            summarySchedules = notificationPreferences.schedules,
            excludedNotificationCalendarIds =
                notificationPreferences.excludedCalendarIds,
        ),
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var calendarsLoaded = false
    private var calendarJob: Job? = null
    private var eventJob: Job? = null
    private var notificationJob: Job? = null

    fun loadCalendars(force: Boolean = false) {
        if (!force && (calendarsLoaded || calendarJob?.isActive == true)) return

        calendarJob?.cancel()
        calendarJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingCalendars = true,
                isLoadingOverview = true,
                calendarError = null,
                overviewError = null,
            )

            val calendarResult = runCatching {
                withContext(Dispatchers.IO) { repository.getCalendars() }
            }

            calendarResult.onSuccess { calendars ->
                calendarsLoaded = true
                val selectedId = _uiState.value.selectedCalendar?.id
                _uiState.value = _uiState.value.copy(
                    calendars = calendars,
                    selectedCalendar = calendars.firstOrNull { it.id == selectedId },
                    isLoadingCalendars = false,
                )

                runCatching {
                    withContext(Dispatchers.IO) {
                        repository.getInstances(startAtBeginningOfToday = true)
                    }
                }.onSuccess { window ->
                    _uiState.value = _uiState.value.copy(
                        overviewEvents = window.events,
                        overviewWindowStartMillis = window.startMillis,
                        overviewWindowEndMillis = window.endMillis,
                        isLoadingOverview = false,
                    )
                    if (_uiState.value.notificationsEnabled) {
                        rescheduleNotifications()
                    }
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingOverview = false,
                        overviewError = error.toKoreanMessage(),
                    )
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoadingCalendars = false,
                    isLoadingOverview = false,
                    calendarError = error.toKoreanMessage(),
                )
            }
        }
    }

    fun selectCalendar(calendar: DeviceCalendar) {
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedCalendar = calendar,
                events = emptyList(),
                eventWindowStartMillis = null,
                eventWindowEndMillis = null,
                isLoadingEvents = true,
                eventError = null,
            )

            runCatching {
                withContext(Dispatchers.IO) {
                    repository.getInstances(calendarId = calendar.id)
                }
            }.onSuccess { window ->
                _uiState.value = _uiState.value.copy(
                    events = window.events,
                    eventWindowStartMillis = window.startMillis,
                    eventWindowEndMillis = window.endMillis,
                    isLoadingEvents = false,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoadingEvents = false,
                    eventError = error.toKoreanMessage(),
                )
            }
        }
    }

    fun refreshCurrentScreen() {
        _uiState.value.selectedCalendar?.let(::selectCalendar) ?: loadCalendars(force = true)
    }

    fun clearSelection() {
        eventJob?.cancel()
        _uiState.value = _uiState.value.copy(
            selectedCalendar = null,
            events = emptyList(),
            eventWindowStartMillis = null,
            eventWindowEndMillis = null,
            isLoadingEvents = false,
            eventError = null,
        )
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        notificationPreferences.enabled = enabled
        _uiState.value = _uiState.value.copy(
            notificationsEnabled = enabled,
            notificationError = null,
        )
        if (enabled) {
            rescheduleNotifications()
        } else {
            notificationJob?.cancel()
            notificationScheduler.cancelAll()
            _uiState.value = _uiState.value.copy(
                scheduledNotificationCount = 0,
                isSchedulingNotifications = false,
            )
        }
    }

    fun addSummarySchedule(hour: Int, minute: Int) {
        if (hour !in 0..23 || minute !in 0..59) return
        if (_uiState.value.summarySchedules.any { it.hour == hour && it.minute == minute }) {
            _uiState.value = _uiState.value.copy(
                notificationError = "같은 시각의 알림이 이미 있습니다.",
            )
            return
        }
        saveSchedules(
            _uiState.value.summarySchedules +
                notificationPreferences.newSchedule(hour, minute),
        )
    }

    fun updateSummarySchedule(id: Int, hour: Int, minute: Int) {
        if (hour !in 0..23 || minute !in 0..59) return
        if (_uiState.value.summarySchedules.any {
                it.id != id && it.hour == hour && it.minute == minute
            }
        ) {
            _uiState.value = _uiState.value.copy(
                notificationError = "같은 시각의 알림이 이미 있습니다.",
            )
            return
        }
        saveSchedules(
            _uiState.value.summarySchedules.map { schedule ->
                if (schedule.id == id) schedule.copy(hour = hour, minute = minute)
                else schedule
            },
        )
    }

    fun deleteSummarySchedule(id: Int) {
        saveSchedules(_uiState.value.summarySchedules.filterNot { it.id == id })
    }

    fun setNotificationCalendarEnabled(calendarId: Long, enabled: Boolean) {
        val excluded = _uiState.value.excludedNotificationCalendarIds.toMutableSet()
        if (enabled) excluded -= calendarId else excluded += calendarId
        notificationPreferences.excludedCalendarIds = excluded
        _uiState.value = _uiState.value.copy(
            excludedNotificationCalendarIds = excluded,
            notificationError = null,
        )
    }

    fun selectAllNotificationCalendars() {
        notificationPreferences.excludedCalendarIds = emptySet()
        _uiState.value = _uiState.value.copy(
            excludedNotificationCalendarIds = emptySet(),
            notificationError = null,
        )
    }

    fun clearAllNotificationCalendars() {
        val excluded = _uiState.value.excludedNotificationCalendarIds +
            _uiState.value.calendars.map { it.id }
        notificationPreferences.excludedCalendarIds = excluded
        _uiState.value = _uiState.value.copy(
            excludedNotificationCalendarIds = excluded,
            notificationError = null,
        )
    }

    fun onNotificationPermissionDenied() {
        val wasEnabled = _uiState.value.notificationsEnabled
        notificationPreferences.enabled = false
        notificationScheduler.cancelAll()
        _uiState.value = _uiState.value.copy(
            notificationsEnabled = false,
            scheduledNotificationCount = 0,
            isSchedulingNotifications = false,
            notificationError = if (wasEnabled) "알림 권한이 허용되지 않았습니다." else null,
        )
    }

    fun rescheduleNotifications() {
        if (!_uiState.value.notificationsEnabled) return
        if (!notificationScheduler.canPostNotifications()) {
            onNotificationPermissionDenied()
            return
        }

        notificationJob?.cancel()
        notificationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSchedulingNotifications = true,
                notificationError = null,
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    notificationScheduler.reschedule()
                }
            }.onSuccess { count ->
                _uiState.value = _uiState.value.copy(
                    scheduledNotificationCount = count,
                    isSchedulingNotifications = false,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSchedulingNotifications = false,
                    notificationError = error.toKoreanMessage(),
                )
            }
        }
    }

    fun onPermissionRevoked() {
        calendarJob?.cancel()
        eventJob?.cancel()
        notificationJob?.cancel()
        notificationScheduler.cancelAll()
        calendarsLoaded = false
        val currentState = _uiState.value
        _uiState.value = CalendarUiState(
            notificationsEnabled = currentState.notificationsEnabled,
            summarySchedules = currentState.summarySchedules,
            excludedNotificationCalendarIds =
                currentState.excludedNotificationCalendarIds,
        )
    }

    private fun saveSchedules(schedules: List<SummarySchedule>) {
        notificationPreferences.schedules = schedules
        _uiState.value = _uiState.value.copy(
            summarySchedules = notificationPreferences.schedules,
            notificationError = null,
        )
        if (_uiState.value.notificationsEnabled) rescheduleNotifications()
    }
}

private fun Throwable.toKoreanMessage(): String {
    val detail = localizedMessage?.takeIf { it.isNotBlank() }
    return if (detail == null) "캘린더 데이터를 읽는 중 오류가 발생했습니다."
    else "캘린더 데이터를 읽지 못했습니다: $detail"
}
