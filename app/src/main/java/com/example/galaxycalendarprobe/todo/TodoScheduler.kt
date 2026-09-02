package com.example.galaxycalendarprobe.todo

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
import com.example.galaxycalendarprobe.notifications.SummarySchedule
import com.example.galaxycalendarprobe.notifications.nextSummaryTriggerAtMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TodoScheduler(private val context: Context) {
    private val preferences = TodoPreferences(context)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun reschedule(): Int {
        cancelAll()
        createChannel()
        if (!preferences.enabled || preferences.microsoftAccount == null || !canPostNotifications()) return 0
        val requestCodes = preferences.schedules.map { schedule ->
            val code = REQUEST_CODE_BASE + schedule.id
            schedule(schedule, code)
            code
        }.toSet()
        preferences.scheduledRequestCodes = requestCodes
        return requestCodes.size
    }

    fun cancelAll() {
        preferences.scheduledRequestCodes.forEach { code ->
            pendingIntent(code, 0, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let(alarmManager::cancel)
        }
        preferences.scheduledRequestCodes = emptySet()
    }

    fun deliver(scheduleId: Int): Int {
        if (!preferences.enabled || !canPostNotifications()) return 0
        val zone = ZoneId.systemDefault()
        val endOfToday = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val selected = preferences.tasks.filter { task ->
            task.listId !in preferences.disabledListIds &&
                task.effectiveAtMillis?.let { it < endOfToday } == true
        }
        if (selected.isEmpty()) return 0
        showNotification(context, scheduleId, selected)
        return selected.size
    }

    private fun schedule(value: SummarySchedule, requestCode: Int) {
        val intent = pendingIntent(
            requestCode,
            value.id,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextSummaryTriggerAtMillis(System.currentTimeMillis(), value.hour, value.minute),
            intent,
        )
    }

    private fun pendingIntent(requestCode: Int, scheduleId: Int, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, TodoSummaryReceiver::class.java).apply {
                action = ACTION_TODO_SUMMARY
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            },
            flags,
        )

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "오늘의 할 일 요약", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "설정한 시각에 오늘까지 처리할 Microsoft To Do 항목을 알려 줍니다."
            },
        )
    }

    companion object {
        const val ACTION_TODO_SUMMARY = "com.example.galaxycalendarprobe.action.TODO_SUMMARY"
        const val EXTRA_SCHEDULE_ID = "todo_schedule_id"
        const val CHANNEL_ID = "todo_summary_v1"
        private const val REQUEST_CODE_BASE = 60_000
    }
}

class TodoSummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TodoScheduler.ACTION_TODO_SUMMARY) return
        val preferences = TodoPreferences(context)
        if (!preferences.enabled) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { MicrosoftTodoRepository(context).syncIfConnected() }
                    .onFailure { preferences.lastError = it.localizedMessage }
                val scheduler = TodoScheduler(context.applicationContext)
                scheduler.deliver(intent.getIntExtra(TodoScheduler.EXTRA_SCHEDULE_ID, 0))
            } finally {
                runCatching { TodoScheduler(context.applicationContext).reschedule() }
                pendingResult.finish()
            }
        }
    }
}

class TodoRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions) return
        val scheduler = TodoScheduler(context.applicationContext)
        if (TodoPreferences(context).enabled) scheduler.reschedule() else scheduler.cancelAll()
    }

    companion object {
        private val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}

@SuppressLint("MissingPermission")
private fun showNotification(context: Context, scheduleId: Int, tasks: List<TodoTaskItem>) {
    val lines = tasks.take(7).map { task ->
        val time = task.effectiveAtMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
        }
        when {
            time == null -> task.title
            time.toLocalDate().isBefore(LocalDate.now()) -> "기한 지남 · ${task.title}"
            else -> "오늘 · ${task.title}"
        }
    }.toMutableList()
    if (tasks.size > 7) lines += "그 밖에 ${tasks.size - 7}개 할 일"
    val contentIntent = PendingIntent.getActivity(
        context,
        TODO_CONTENT_REQUEST_CODE,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_SECTION, MainActivity.SECTION_TODOS)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, TodoScheduler.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("오늘까지 할 일 ${tasks.size}개")
        .setContentText(lines.firstOrNull().orEmpty())
        .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(70_000 + scheduleId.coerceAtLeast(1), notification)
}

private const val TODO_CONTENT_REQUEST_CODE = 71_000
