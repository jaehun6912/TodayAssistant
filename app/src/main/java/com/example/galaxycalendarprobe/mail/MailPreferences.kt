package com.example.galaxycalendarprobe.mail

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import com.example.galaxycalendarprobe.notifications.SummarySchedule

class MailPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_ENABLED, value).apply()

    var schedules: List<SummarySchedule>
        get() {
            initializeSchedulesIfNeeded()
            return preferences.getStringSet(KEY_SCHEDULES, emptySet())
                .orEmpty()
                .mapNotNull(::decodeSchedule)
                .sortedWith(compareBy<SummarySchedule> { it.hour }.thenBy { it.minute })
        }
        set(value) {
            val normalized = value
                .filter { it.id > 0 && it.hour in 0..23 && it.minute in 0..59 }
                .distinctBy { it.id }
            val nextId = maxOf(
                preferences.getInt(KEY_NEXT_SCHEDULE_ID, 1),
                (normalized.maxOfOrNull { it.id } ?: 0) + 1,
            )
            preferences.edit()
                .putStringSet(KEY_SCHEDULES, normalized.map(::encodeSchedule).toSet())
                .putInt(KEY_NEXT_SCHEDULE_ID, nextId)
                .putBoolean(KEY_SCHEDULES_INITIALIZED, true)
                .apply()
        }

    fun newSchedule(hour: Int, minute: Int): SummarySchedule {
        require(hour in 0..23 && minute in 0..59)
        initializeSchedulesIfNeeded()
        val id = preferences.getInt(KEY_NEXT_SCHEDULE_ID, 1).coerceAtLeast(1)
        preferences.edit().putInt(KEY_NEXT_SCHEDULE_ID, id + 1).apply()
        return SummarySchedule(id = id, hour = hour, minute = minute)
    }

    var disabledSourceIds: Set<String>
        get() = preferences.getStringSet(KEY_DISABLED_SOURCE_IDS, emptySet()).orEmpty()
        set(value) = preferences.edit()
            .putStringSet(KEY_DISABLED_SOURCE_IDS, value)
            .apply()

    var scheduledRequestCodes: Set<Int>
        get() = preferences.getStringSet(KEY_REQUEST_CODES, emptySet())
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .toSet()
        set(value) = preferences.edit()
            .putStringSet(KEY_REQUEST_CODES, value.map(Int::toString).toSet())
            .apply()

    var lastSummaryCheckMillis: Long
        get() = preferences.getLong(KEY_LAST_SUMMARY_CHECK, 0L)
        set(value) = preferences.edit().putLong(KEY_LAST_SUMMARY_CHECK, value).apply()

    var outlookDirectConnected: Boolean
        get() = preferences.getBoolean(KEY_OUTLOOK_DIRECT_CONNECTED, false)
        set(value) = preferences.edit().putBoolean(KEY_OUTLOOK_DIRECT_CONNECTED, value).apply()

    var outlookUsername: String?
        get() = preferences.getString(KEY_OUTLOOK_USERNAME, null)
        set(value) = preferences.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_OUTLOOK_USERNAME)
            else putString(KEY_OUTLOOK_USERNAME, value)
        }.apply()

    var outlookBaselineInitialized: Boolean
        get() = preferences.getBoolean(KEY_OUTLOOK_BASELINE_INITIALIZED, false)
        set(value) = preferences.edit().putBoolean(KEY_OUTLOOK_BASELINE_INITIALIZED, value).apply()

    var lastOutlookSyncMillis: Long
        get() = preferences.getLong(KEY_LAST_OUTLOOK_SYNC, 0L)
        set(value) = preferences.edit().putLong(KEY_LAST_OUTLOOK_SYNC, value).apply()

    var lastOutlookNewMailCount: Int
        get() = preferences.getInt(KEY_LAST_OUTLOOK_NEW_MAIL_COUNT, 0)
        set(value) = preferences.edit().putInt(KEY_LAST_OUTLOOK_NEW_MAIL_COUNT, value).apply()

    var lastOutlookError: String?
        get() = preferences.getString(KEY_LAST_OUTLOOK_ERROR, null)
        set(value) = preferences.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_LAST_OUTLOOK_ERROR)
            else putString(KEY_LAST_OUTLOOK_ERROR, value.take(500))
        }.apply()

    var seenOutlookMessageIds: Set<String>
        get() = preferences.getStringSet(KEY_SEEN_OUTLOOK_MESSAGE_IDS, emptySet()).orEmpty()
        set(value) = preferences.edit()
            .putStringSet(KEY_SEEN_OUTLOOK_MESSAGE_IDS, value.take(MAX_SEEN_OUTLOOK_IDS).toSet())
            .apply()

    fun resetOutlookBaseline() {
        preferences.edit()
            .putBoolean(KEY_OUTLOOK_BASELINE_INITIALIZED, false)
            .putLong(KEY_LAST_OUTLOOK_SYNC, 0L)
            .putInt(KEY_LAST_OUTLOOK_NEW_MAIL_COUNT, 0)
            .remove(KEY_LAST_OUTLOOK_ERROR)
            .remove(KEY_SEEN_OUTLOOK_MESSAGE_IDS)
            .apply()
    }

    var gmailDirectConnected: Boolean
        get() = preferences.getBoolean(KEY_GMAIL_DIRECT_CONNECTED, false)
        set(value) = preferences.edit().putBoolean(KEY_GMAIL_DIRECT_CONNECTED, value).apply()

    var gmailEmail: String?
        get() = preferences.getString(KEY_GMAIL_EMAIL, null)
        set(value) = preferences.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_GMAIL_EMAIL)
            else putString(KEY_GMAIL_EMAIL, value)
        }.apply()

    var gmailHistoryId: String?
        get() = preferences.getString(KEY_GMAIL_HISTORY_ID, null)
        set(value) = preferences.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_GMAIL_HISTORY_ID)
            else putString(KEY_GMAIL_HISTORY_ID, value)
        }.apply()

    var lastGmailSyncMillis: Long
        get() = preferences.getLong(KEY_LAST_GMAIL_SYNC, 0L)
        set(value) = preferences.edit().putLong(KEY_LAST_GMAIL_SYNC, value).apply()

    var lastGmailNewMailCount: Int
        get() = preferences.getInt(KEY_LAST_GMAIL_NEW_MAIL_COUNT, 0)
        set(value) = preferences.edit().putInt(KEY_LAST_GMAIL_NEW_MAIL_COUNT, value).apply()

    var lastGmailError: String?
        get() = preferences.getString(KEY_LAST_GMAIL_ERROR, null)
        set(value) = preferences.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_LAST_GMAIL_ERROR)
            else putString(KEY_LAST_GMAIL_ERROR, value.take(500))
        }.apply()

    fun resetGmailBaseline() {
        preferences.edit()
            .remove(KEY_GMAIL_HISTORY_ID)
            .putLong(KEY_LAST_GMAIL_SYNC, 0L)
            .putInt(KEY_LAST_GMAIL_NEW_MAIL_COUNT, 0)
            .remove(KEY_LAST_GMAIL_ERROR)
            .apply()
    }

    fun clearGmailConnection(removePendingMails: Boolean = true) {
        preferences.edit()
            .putBoolean(KEY_GMAIL_DIRECT_CONNECTED, false)
            .remove(KEY_GMAIL_EMAIL)
            .remove(KEY_GMAIL_HISTORY_ID)
            .putLong(KEY_LAST_GMAIL_SYNC, 0L)
            .putInt(KEY_LAST_GMAIL_NEW_MAIL_COUNT, 0)
            .remove(KEY_LAST_GMAIL_ERROR)
            .apply()
        if (removePendingMails) removePendingMailsForSource(GMAIL_SOURCE_ID)
    }

    fun clearOutlookConnection(removePendingMails: Boolean = true) {
        preferences.edit()
            .putBoolean(KEY_OUTLOOK_DIRECT_CONNECTED, false)
            .remove(KEY_OUTLOOK_USERNAME)
            .putBoolean(KEY_OUTLOOK_BASELINE_INITIALIZED, false)
            .putLong(KEY_LAST_OUTLOOK_SYNC, 0L)
            .putInt(KEY_LAST_OUTLOOK_NEW_MAIL_COUNT, 0)
            .remove(KEY_LAST_OUTLOOK_ERROR)
            .remove(KEY_SEEN_OUTLOOK_MESSAGE_IDS)
            .apply()
        if (removePendingMails) removePendingMailsForSource(OUTLOOK_SOURCE_ID)
    }

    val pendingMails: List<CapturedMail>
        get() = preferences.getString(KEY_PENDING_MAILS, "")
            .orEmpty()
            .lineSequence()
            .mapNotNull(::decodeMail)
            .sortedByDescending(CapturedMail::receivedAtMillis)
            .toList()

    @Synchronized
    fun upsertPendingMail(mail: CapturedMail) {
        val existing = pendingMails.firstOrNull {
            it.notificationKey == mail.notificationKey
        }
        val normalized = if (existing == null) mail else mail.copy(
            receivedAtMillis = minOf(existing.receivedAtMillis, mail.receivedAtMillis),
        )
        savePendingMails(
            (pendingMails.filterNot { it.notificationKey == mail.notificationKey } + normalized)
                .sortedByDescending(CapturedMail::receivedAtMillis)
                .take(MAX_PENDING_MAILS),
        )
    }

    @Synchronized
    fun acknowledgePendingMails(notificationKeys: Set<String>) {
        savePendingMails(
            pendingMails.filterNot { it.notificationKey in notificationKeys },
        )
    }

    @Synchronized
    fun removePendingMailsForSource(sourceId: String) {
        savePendingMails(pendingMails.filterNot { it.sourceId == sourceId })
    }

    @SuppressLint("ApplySharedPref")
    private fun savePendingMails(mails: List<CapturedMail>) {
        // 알림 리스너가 종료되더라도 수집 결과가 남도록 이 작은 목록은 동기 저장합니다.
        preferences.edit()
            .putString(KEY_PENDING_MAILS, mails.joinToString("\n", transform = ::encodeMail))
            .commit()
    }

    private fun initializeSchedulesIfNeeded() {
        if (preferences.getBoolean(KEY_SCHEDULES_INITIALIZED, false)) return
        val defaults = listOf(
            SummarySchedule(1, 8, 0),
            SummarySchedule(2, 13, 0),
            SummarySchedule(3, 19, 0),
        )
        preferences.edit()
            .putStringSet(KEY_SCHEDULES, defaults.map(::encodeSchedule).toSet())
            .putInt(KEY_NEXT_SCHEDULE_ID, 4)
            .putBoolean(KEY_SCHEDULES_INITIALIZED, true)
            .apply()
    }

    companion object {
        private const val FILE_NAME = "mail_summary_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SCHEDULES = "schedules"
        private const val KEY_SCHEDULES_INITIALIZED = "schedules_initialized"
        private const val KEY_NEXT_SCHEDULE_ID = "next_schedule_id"
        private const val KEY_DISABLED_SOURCE_IDS = "disabled_source_ids"
        private const val KEY_REQUEST_CODES = "scheduled_request_codes"
        private const val KEY_PENDING_MAILS = "pending_mails"
        private const val KEY_LAST_SUMMARY_CHECK = "last_summary_check"
        private const val KEY_OUTLOOK_DIRECT_CONNECTED = "outlook_direct_connected"
        private const val KEY_OUTLOOK_USERNAME = "outlook_username"
        private const val KEY_OUTLOOK_BASELINE_INITIALIZED = "outlook_baseline_initialized"
        private const val KEY_LAST_OUTLOOK_SYNC = "last_outlook_sync"
        private const val KEY_LAST_OUTLOOK_NEW_MAIL_COUNT = "last_outlook_new_mail_count"
        private const val KEY_LAST_OUTLOOK_ERROR = "last_outlook_error"
        private const val KEY_SEEN_OUTLOOK_MESSAGE_IDS = "seen_outlook_message_ids"
        private const val KEY_GMAIL_DIRECT_CONNECTED = "gmail_direct_connected"
        private const val KEY_GMAIL_EMAIL = "gmail_email"
        private const val KEY_GMAIL_HISTORY_ID = "gmail_history_id"
        private const val KEY_LAST_GMAIL_SYNC = "last_gmail_sync"
        private const val KEY_LAST_GMAIL_NEW_MAIL_COUNT = "last_gmail_new_mail_count"
        private const val KEY_LAST_GMAIL_ERROR = "last_gmail_error"
        private const val MAX_PENDING_MAILS = 200
        private const val MAX_SEEN_OUTLOOK_IDS = 2_000
        private const val OUTLOOK_SOURCE_ID = "outlook"
        private const val GMAIL_SOURCE_ID = "gmail"
    }
}

private fun encodeSchedule(schedule: SummarySchedule): String =
    "${schedule.id}|${schedule.hour}|${schedule.minute}"

private fun decodeSchedule(value: String): SummarySchedule? {
    val parts = value.split('|')
    if (parts.size != 3) return null
    val id = parts[0].toIntOrNull() ?: return null
    val hour = parts[1].toIntOrNull() ?: return null
    val minute = parts[2].toIntOrNull() ?: return null
    return SummarySchedule(id, hour, minute)
        .takeIf { id > 0 && hour in 0..23 && minute in 0..59 }
}

private fun encodeMail(mail: CapturedMail): String = listOf(
    mail.notificationKey,
    mail.sourceId,
    mail.sender,
    mail.subject,
    mail.preview.orEmpty(),
    mail.receivedAtMillis.toString(),
    mail.collectionMethod.name,
).joinToString("|") { value ->
    Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
}

private fun decodeMail(encoded: String): CapturedMail? = runCatching {
    val parts = encoded.split('|').map { value ->
        String(Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
    }
    if (parts.size !in 6..7) return null
    CapturedMail(
        notificationKey = parts[0],
        sourceId = parts[1],
        sender = parts[2],
        subject = parts[3],
        preview = parts[4].takeIf(String::isNotBlank),
        receivedAtMillis = parts[5].toLong(),
        collectionMethod = parts.getOrNull(6)
            ?.let { runCatching { MailCollectionMethod.valueOf(it) }.getOrNull() }
            ?: MailCollectionMethod.NOTIFICATION,
    )
}.getOrNull()
