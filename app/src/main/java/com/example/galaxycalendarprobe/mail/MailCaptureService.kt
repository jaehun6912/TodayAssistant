package com.example.galaxycalendarprobe.mail

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MailCaptureService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val source = MailSources.fromPackage(sbn.packageName) ?: return
        val preferences = MailPreferences(applicationContext)
        if (!preferences.enabled || source.id in preferences.disabledSourceIds) return
        if (source.id == OUTLOOK_SOURCE_ID && preferences.outlookDirectConnected) return
        if (source.id == GMAIL_SOURCE_ID && preferences.gmailDirectConnected) return
        if (sbn.isOngoing || notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val parsed = parseMailNotificationText(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.map(CharSequence::toString)
                .orEmpty(),
        ) ?: return

        preferences.upsertPendingMail(
            CapturedMail(
                notificationKey = sbn.key,
                sourceId = source.id,
                sender = parsed.sender,
                subject = parsed.subject,
                preview = parsed.preview,
                receivedAtMillis = sbn.postTime.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        private const val OUTLOOK_SOURCE_ID = "outlook"
        private const val GMAIL_SOURCE_ID = "gmail"
    }
}
