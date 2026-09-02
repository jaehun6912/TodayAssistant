package com.example.galaxycalendarprobe.notice

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NoticeScheduler(private val context: Context) {
    private val preferences = NoticePreferences(context)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleNext() {
        cancel()
        createNotificationChannel()
        if (!preferences.monitoringEnabled || !canPostNotifications()) return

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + CHECK_INTERVAL_MILLIS,
            pendingIntent(PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                ?: return,
        )
    }

    fun cancel() {
        pendingIntent(PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let(alarmManager::cancel)
    }

    fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "신규 공지사항",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "선택한 게시판에 새 글이 등록되면 알려 줍니다."
                    },
                )
        }
    }

    private fun pendingIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, NoticeAlarmReceiver::class.java).apply {
            action = ACTION_NOTICE_CHECK
        },
        flags,
    )

    companion object {
        const val ACTION_NOTICE_CHECK =
            "com.example.galaxycalendarprobe.action.NOTICE_CHECK"
        const val CHANNEL_ID = "notice_updates_v1"
        private const val REQUEST_CODE = 52_001
        private const val CHECK_INTERVAL_MILLIS = 60L * 60L * 1_000L
    }
}

class NoticeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NoticeScheduler.ACTION_NOTICE_CHECK) return
        if (!NoticePreferences(context).monitoringEnabled) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = NoticeMonitor(context.applicationContext).check()
                if (result.newArticles.isNotEmpty()) {
                    showNewNoticeNotification(context, result.newArticles)
                }
            } finally {
                NoticeScheduler(context.applicationContext).scheduleNext()
                pendingResult.finish()
            }
        }
    }
}

class NoticeRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions) return
        val scheduler = NoticeScheduler(context.applicationContext)
        if (NoticePreferences(context).monitoringEnabled) scheduler.scheduleNext()
        else scheduler.cancel()
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
private fun showNewNoticeNotification(
    context: Context,
    newArticles: List<NoticeArticle>,
) {
    val scheduler = NoticeScheduler(context)
    scheduler.createNotificationChannel()
    if (!scheduler.canPostNotifications()) return

    val sourceNames = NoticePreferences(context).allSources().associate { it.id to it.name }
    val lines = newArticles.take(MAX_NOTIFICATION_ARTICLES).map { article ->
        "[${sourceNames[article.sourceId] ?: "공지"}] ${article.title}"
    }.toMutableList()
    if (newArticles.size > MAX_NOTIFICATION_ARTICLES) {
        lines += "그 밖에 ${newArticles.size - MAX_NOTIFICATION_ARTICLES}개"
    }
    val openApp = PendingIntent.getActivity(
        context,
        NOTICE_CONTENT_REQUEST_CODE,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_SECTION, MainActivity.SECTION_NOTICES)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, NoticeScheduler.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("신규 공지 ${newArticles.size}개")
        .setContentText(lines.firstOrNull().orEmpty())
        .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
}

private const val MAX_NOTIFICATION_ARTICLES = 5
private const val NOTIFICATION_ID = 40_001
private const val NOTICE_CONTENT_REQUEST_CODE = 41_001
