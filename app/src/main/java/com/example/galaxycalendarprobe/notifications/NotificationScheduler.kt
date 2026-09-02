package com.example.galaxycalendarprobe.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.galaxycalendarprobe.MainActivity
import com.example.galaxycalendarprobe.R
import com.example.galaxycalendarprobe.data.CalendarEventInstance
import com.example.galaxycalendarprobe.data.CalendarRepository
import com.example.galaxycalendarprobe.data.DeviceCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationScheduler(private val context: Context) {
    private val preferences = NotificationPreferences(context)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun reschedule(): Int {
        cancelAll()
        createNotificationChannel()

        if (!preferences.enabled || !canPostNotifications()) return 0

        val requestCodes = preferences.schedules.map { schedule ->
            val requestCode = requestCodeFor(schedule.id)
            schedule(schedule, requestCode)
            requestCode
        }.toSet()
        preferences.scheduledRequestCodes = requestCodes
        return requestCodes.size
    }

    fun cancelAll() {
        val requestCodes = preferences.scheduledRequestCodes + LEGACY_SUMMARY_REQUEST_CODES
        requestCodes.forEach { requestCode ->
            val pendingIntent = summaryPendingIntent(
                requestCode = requestCode,
                scheduleId = 0,
                flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            pendingIntent?.let(alarmManager::cancel)
        }
        preferences.scheduledRequestCodes = emptySet()
        cancelLegacyEventAlarms()
    }

    fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "오늘 일정 요약",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "설정한 시각에 선택한 캘린더의 오늘 일정을 알려 줍니다."
                },
            )
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        }
    }

    private fun schedule(schedule: SummarySchedule, requestCode: Int) {
        val triggerAtMillis = nextSummaryTriggerAtMillis(
            nowMillis = System.currentTimeMillis(),
            hour = schedule.hour,
            minute = schedule.minute,
        )
        val pendingIntent = summaryPendingIntent(
            requestCode = requestCode,
            scheduleId = schedule.id,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    private fun summaryPendingIntent(
        requestCode: Int,
        scheduleId: Int,
        flags: Int,
    ): PendingIntent? = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, AgendaAlarmReceiver::class.java).apply {
            action = ACTION_AGENDA_ALARM
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        },
        flags,
    )

    private fun cancelLegacyEventAlarms() {
        preferences.legacyEventRequestCodes.forEach { requestCode ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, EventAlarmReceiver::class.java).apply {
                    action = LEGACY_ACTION_EVENT_ALARM
                },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            pendingIntent?.let(alarmManager::cancel)
        }
        preferences.clearLegacyEventRequestCodes()
    }

    companion object {
        const val ACTION_AGENDA_ALARM =
            "com.example.galaxycalendarprobe.action.AGENDA_ALARM"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val CHANNEL_ID = "daily_agenda_summary_v1"

        private val LEGACY_SUMMARY_REQUEST_CODES = setOf(8_101, 8_102)
        private const val LEGACY_ACTION_EVENT_ALARM =
            "com.example.galaxycalendarprobe.action.EVENT_ALARM"
        private const val LEGACY_CHANNEL_ID = "calendar_event_reminders"
    }
}

class AgendaAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationScheduler.ACTION_AGENDA_ALARM) return
        val preferences = NotificationPreferences(context)
        if (!preferences.enabled) return

        val scheduleId = intent.getIntExtra(NotificationScheduler.EXTRA_SCHEDULE_ID, 0)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val scheduler = NotificationScheduler(context.applicationContext)
                scheduler.createNotificationChannel()
                if (scheduler.canPostNotifications() && canReadCalendar(context)) {
                    val repository = CalendarRepository(context.applicationContext)
                    val calendars = repository.getCalendars()
                    val excludedIds = preferences.excludedCalendarIds
                    val selectedCalendars = calendars.filterNot { it.id in excludedIds }
                    val selectedIds = selectedCalendars.mapTo(mutableSetOf()) { it.id }
                    val events = repository.getInstances(
                        startAtBeginningOfToday = true,
                    ).events.filter {
                        it.calendarId in selectedIds && it.occursOn(LocalDate.now())
                    }
                    showAgendaNotification(
                        context = context,
                        scheduleId = scheduleId,
                        calendars = selectedCalendars,
                        events = events,
                    )
                }
            } finally {
                NotificationScheduler(context.applicationContext).reschedule()
                pendingResult.finish()
            }
        }
    }
}

/** v0.3의 이미 예약된 개별 일정 알람을 조용히 흡수하는 호환용 수신기입니다. */
class EventAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

@SuppressLint("MissingPermission")
private fun showAgendaNotification(
    context: Context,
    scheduleId: Int,
    calendars: List<DeviceCalendar>,
    events: List<CalendarEventInstance>,
) {
    val calendarsById = calendars.associateBy { it.id }
    val dateText = LocalDate.now().format(dateFormatter)
    val lines = events.take(MAX_VISIBLE_EVENTS).map { event ->
        val calendarName = calendarsById[event.calendarId]?.displayName
        buildString {
            append(event.summaryTimeText())
            append(" · ")
            append(event.title?.takeIf(String::isNotBlank) ?: "제목 없는 일정")
            if (!calendarName.isNullOrBlank()) append(" · $calendarName")
        }
    }.toMutableList()
    if (events.size > MAX_VISIBLE_EVENTS) {
        lines += "그 밖에 ${events.size - MAX_VISIBLE_EVENTS}개 일정"
    }

    val noTargetCalendars = calendars.isEmpty()
    val title = when {
        noTargetCalendars -> "알림 대상 캘린더 없음"
        events.isEmpty() -> "오늘 일정 없음"
        else -> "오늘 일정 ${events.size}개"
    }
    val body = when {
        noTargetCalendars -> "알림 설정에서 캘린더를 한 개 이상 선택해 주세요."
        events.isEmpty() -> "$dateText · 선택한 캘린더에 등록된 일정이 없습니다."
        else -> "$dateText\n${lines.joinToString("\n")}"
    }

    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_OPEN_SECTION, MainActivity.SECTION_TODAY)
    }
    val contentIntent = PendingIntent.getActivity(
        context,
        SUMMARY_CONTENT_REQUEST_CODE,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(
        context,
        NotificationScheduler.CHANNEL_ID,
    )
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(
            when {
                noTargetCalendars -> "알림 대상 캘린더를 선택해 주세요."
                events.isEmpty() -> "선택한 캘린더에 등록된 일정이 없습니다."
                else -> lines.firstOrNull().orEmpty()
            },
        )
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .setWhen(System.currentTimeMillis())
        .build()

    NotificationManagerCompat.from(context).notify(
        notificationIdFor(scheduleId),
        notification,
    )
}

internal fun nextSummaryTriggerAtMillis(
    nowMillis: Long,
    hour: Int,
    minute: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
    var candidate = now.toLocalDate()
        .atTime(hour, minute)
        .atZone(zoneId)
    if (!candidate.toInstant().isAfter(now.toInstant())) candidate = candidate.plusDays(1)
    return candidate.toInstant().toEpochMilli()
}

private fun requestCodeFor(scheduleId: Int): Int =
    SUMMARY_REQUEST_CODE_BASE + scheduleId.coerceAtLeast(1)

private fun notificationIdFor(scheduleId: Int): Int =
    SUMMARY_NOTIFICATION_ID_BASE + scheduleId.coerceAtLeast(1)

private fun canReadCalendar(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

private fun CalendarEventInstance.occursOn(date: LocalDate): Boolean {
    val zone = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
    val startDate = Instant.ofEpochMilli(beginMillis).atZone(zone).toLocalDate()
    val inclusiveEndDate = Instant.ofEpochMilli(maxOf(beginMillis, endMillis - 1))
        .atZone(zone)
        .toLocalDate()
    return !date.isBefore(startDate) && !date.isAfter(inclusiveEndDate)
}

private fun CalendarEventInstance.summaryTimeText(): String {
    if (allDay) return "종일"
    val start = Instant.ofEpochMilli(beginMillis).atZone(ZoneId.systemDefault())
    return if (start.toLocalDate().isBefore(LocalDate.now())) {
        "진행 중"
    } else {
        start.format(timeFormatter)
    }
}

private const val MAX_VISIBLE_EVENTS = 7
private const val SUMMARY_REQUEST_CODE_BASE = 20_000
private const val SUMMARY_NOTIFICATION_ID_BASE = 30_000
private const val SUMMARY_CONTENT_REQUEST_CODE = 31_000
private val dateFormatter = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
private val timeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
