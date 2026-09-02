package com.example.galaxycalendarprobe.mail

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MailScheduler(private val context: Context) {
    private val preferences = MailPreferences(context)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun reschedule(): Int {
        cancelAll()
        createNotificationChannel()
        if (
            !preferences.enabled ||
            !canPostNotifications() ||
            (!hasNotificationAccess() && !hasDirectAccountAccess())
        ) {
            return 0
        }

        val requestCodes = preferences.schedules.map { schedule ->
            val requestCode = MAIL_REQUEST_CODE_BASE + schedule.id
            schedule(schedule, requestCode)
            requestCode
        }.toSet()
        preferences.scheduledRequestCodes = requestCodes
        return requestCodes.size
    }

    fun cancelAll() {
        preferences.scheduledRequestCodes.forEach { requestCode ->
            mailPendingIntent(
                requestCode = requestCode,
                scheduleId = 0,
                flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let(alarmManager::cancel)
        }
        preferences.scheduledRequestCodes = emptySet()
    }

    fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    private fun hasDirectAccountAccess(): Boolean =
        (preferences.outlookDirectConnected && "outlook" !in preferences.disabledSourceIds) ||
            (preferences.gmailDirectConnected && "gmail" !in preferences.disabledSourceIds)

    fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun createNotificationChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "새 메일 요약",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "설정한 시각에 Gmail과 Outlook의 새 메일을 모아 알려 줍니다."
            },
        )
    }

    fun requestListenerRebind() {
        NotificationListenerServiceCompat.requestRebind(
            ComponentName(context, MailCaptureService::class.java),
        )
    }

    fun deliverPending(scheduleId: Int): Int {
        if (!preferences.enabled || !canPostNotifications()) return 0
        val pending = preferences.pendingMails
        preferences.lastSummaryCheckMillis = System.currentTimeMillis()
        if (pending.isEmpty()) return 0
        showMailSummaryNotification(context, scheduleId, pending)
        preferences.acknowledgePendingMails(
            pending.mapTo(mutableSetOf(), CapturedMail::notificationKey),
        )
        return pending.size
    }

    private fun schedule(schedule: SummarySchedule, requestCode: Int) {
        val pendingIntent = mailPendingIntent(
            requestCode,
            schedule.id,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextSummaryTriggerAtMillis(
                nowMillis = System.currentTimeMillis(),
                hour = schedule.hour,
                minute = schedule.minute,
            ),
            pendingIntent,
        )
    }

    private fun mailPendingIntent(
        requestCode: Int,
        scheduleId: Int,
        flags: Int,
    ): PendingIntent? = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, MailSummaryReceiver::class.java).apply {
            action = ACTION_MAIL_SUMMARY
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        },
        flags,
    )

    companion object {
        const val ACTION_MAIL_SUMMARY =
            "com.example.galaxycalendarprobe.action.MAIL_SUMMARY"
        const val EXTRA_SCHEDULE_ID = "mail_schedule_id"
        const val CHANNEL_ID = "mail_summary_v1"
        private const val MAIL_REQUEST_CODE_BASE = 40_000
    }
}

class MailSummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MailScheduler.ACTION_MAIL_SUMMARY) return
        val preferences = MailPreferences(context)
        if (!preferences.enabled) return
        val pendingResult = goAsync()
        val scheduler = MailScheduler(context.applicationContext)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching {
                    OutlookMailRepository(context.applicationContext).syncIfConnected()
                }.onFailure { error ->
                    preferences.lastOutlookError = error.localizedMessage
                        ?: "예약된 Outlook 메일 확인에 실패했습니다."
                }
                runCatching {
                    GmailMailRepository(context.applicationContext).syncIfConnected()
                }.onFailure { error ->
                    preferences.lastGmailError = error.localizedMessage
                        ?: "예약된 Gmail 확인에 실패했습니다."
                }
                scheduler.deliverPending(
                    intent.getIntExtra(MailScheduler.EXTRA_SCHEDULE_ID, 0),
                )
            } finally {
                runCatching { scheduler.reschedule() }
                pendingResult.finish()
            }
        }
    }
}

class MailRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions) return
        val scheduler = MailScheduler(context.applicationContext)
        if (MailPreferences(context).enabled) scheduler.reschedule()
        else scheduler.cancelAll()
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

private object NotificationListenerServiceCompat {
    fun requestRebind(componentName: ComponentName) {
        android.service.notification.NotificationListenerService.requestRebind(componentName)
    }
}

@SuppressLint("MissingPermission")
private fun showMailSummaryNotification(
    context: Context,
    scheduleId: Int,
    mails: List<CapturedMail>,
) {
    val sourcesById = MailSources.all.associateBy(MailSource::id)
    val lines = mails.take(MAX_VISIBLE_MAILS).map { mail ->
        val sourceName = sourcesById[mail.sourceId]?.displayName ?: "메일"
        "$sourceName · ${mail.sender} · ${mail.subject}"
    }.toMutableList()
    if (mails.size > MAX_VISIBLE_MAILS) {
        lines += "그 밖에 ${mails.size - MAX_VISIBLE_MAILS}개 메일"
    }
    val nowText = Instant.ofEpochMilli(System.currentTimeMillis())
        .atZone(ZoneId.systemDefault())
        .format(summaryTimeFormatter)
    val body = "$nowText\n${lines.joinToString("\n")}"
    val contentIntent = PendingIntent.getActivity(
        context,
        MAIL_CONTENT_REQUEST_CODE,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_SECTION, MainActivity.SECTION_MAIL)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, MailScheduler.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("새 메일 ${mails.size}개")
        .setContentText(lines.firstOrNull().orEmpty())
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setCategory(NotificationCompat.CATEGORY_EMAIL)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .setWhen(System.currentTimeMillis())
        .build()
    NotificationManagerCompat.from(context).notify(
        MAIL_NOTIFICATION_ID_BASE + scheduleId.coerceAtLeast(1),
        notification,
    )
}

private const val MAX_VISIBLE_MAILS = 7
private const val MAIL_NOTIFICATION_ID_BASE = 50_000
private const val MAIL_CONTENT_REQUEST_CODE = 51_000
private val summaryTimeFormatter =
    DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREAN)
