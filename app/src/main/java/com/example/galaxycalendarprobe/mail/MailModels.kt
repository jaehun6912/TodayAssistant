package com.example.galaxycalendarprobe.mail

import com.example.galaxycalendarprobe.notifications.SummarySchedule

data class MailSource(
    val id: String,
    val displayName: String,
    val packageName: String,
)

data class CapturedMail(
    val notificationKey: String,
    val sourceId: String,
    val sender: String,
    val subject: String,
    val preview: String?,
    val receivedAtMillis: Long,
    val collectionMethod: MailCollectionMethod = MailCollectionMethod.NOTIFICATION,
)

enum class MailCollectionMethod {
    NOTIFICATION,
    DIRECT_ACCOUNT,
}

data class DirectMailAccount(
    val username: String,
)

data class MailUiState(
    val enabled: Boolean = false,
    val notificationAccessGranted: Boolean = false,
    val schedules: List<SummarySchedule> = emptyList(),
    val disabledSourceIds: Set<String> = emptySet(),
    val pendingMails: List<CapturedMail> = emptyList(),
    val lastSummaryCheckMillis: Long = 0L,
    val scheduledCount: Int = 0,
    val isScheduling: Boolean = false,
    val outlookAccount: DirectMailAccount? = null,
    val isOutlookInitializing: Boolean = true,
    val isOutlookSyncing: Boolean = false,
    val outlookBaselineInitialized: Boolean = false,
    val lastOutlookSyncMillis: Long = 0L,
    val lastOutlookNewMailCount: Int = 0,
    val outlookError: String? = null,
    val gmailAccount: DirectMailAccount? = null,
    val isGmailSyncing: Boolean = false,
    val gmailBaselineInitialized: Boolean = false,
    val lastGmailSyncMillis: Long = 0L,
    val lastGmailNewMailCount: Int = 0,
    val gmailError: String? = null,
    val error: String? = null,
)

object MailSources {
    val all = listOf(
        MailSource(
            id = "gmail",
            displayName = "Gmail",
            packageName = "com.google.android.gm",
        ),
        MailSource(
            id = "outlook",
            displayName = "Outlook",
            packageName = "com.microsoft.office.outlook",
        ),
    )

    fun fromPackage(packageName: String): MailSource? =
        all.firstOrNull { it.packageName == packageName }
}

data class ParsedMailNotification(
    val sender: String,
    val subject: String,
    val preview: String?,
)

internal fun parseMailNotificationText(
    title: String?,
    text: String?,
    bigText: String?,
    textLines: List<String>,
): ParsedMailNotification? {
    val sender = title.cleanedText() ?: return null
    val candidates = buildList {
        textLines.mapNotNullTo(this) { it.cleanedText() }
        text.cleanedText()?.let(::add)
        bigText.cleanedText()?.let(::add)
    }.distinct()
    val subject = candidates.firstOrNull() ?: return null
    val preview = candidates.firstOrNull { it != subject }
    return ParsedMailNotification(sender, subject, preview)
}

private fun String?.cleanedText(): String? = this
    ?.replace(Regex("\\s+"), " ")
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?.take(500)
