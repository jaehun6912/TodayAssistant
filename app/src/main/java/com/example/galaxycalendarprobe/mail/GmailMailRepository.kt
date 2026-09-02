package com.example.galaxycalendarprobe.mail

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

sealed interface GmailAuthorization {
    data class Authorized(val accessToken: String) : GmailAuthorization
    data class NeedsUserAction(val pendingIntent: PendingIntent) : GmailAuthorization
}

data class GmailSyncResult(
    val newMailCount: Int,
    val baselineCreated: Boolean,
)

class GmailMailRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = MailPreferences(appContext)
    private val authorizationClient = Identity.getAuthorizationClient(appContext)
    private val requestedScopes = listOf(Scope(GMAIL_METADATA_SCOPE))

    suspend fun requestAuthorization(): GmailAuthorization {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes)
        preferences.gmailEmail?.takeIf(String::isNotBlank)?.let { email ->
            builder.setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
        }
        return authorizationClient.authorize(builder.build()).await().toAuthorization()
    }

    fun authorizationFromIntent(data: Intent): GmailAuthorization.Authorized {
        val result = authorizationClient.getAuthorizationResultFromIntent(data)
        return GmailAuthorization.Authorized(
            result.accessToken ?: throw GmailReauthenticationRequiredException(),
        )
    }

    suspend fun finishConnection(accessToken: String): GmailSyncResult {
        val profile = withContext(Dispatchers.IO) { fetchProfile(accessToken) }
        preferences.gmailDirectConnected = true
        preferences.gmailEmail = profile.email
        preferences.gmailHistoryId = profile.historyId
        preferences.lastGmailSyncMillis = System.currentTimeMillis()
        preferences.lastGmailNewMailCount = 0
        preferences.lastGmailError = null
        preferences.removePendingMailsForSource(GMAIL_SOURCE_ID)
        return GmailSyncResult(newMailCount = 0, baselineCreated = true)
    }

    suspend fun syncIfConnected(): GmailSyncResult? {
        if (!preferences.gmailDirectConnected) return null
        if (GMAIL_SOURCE_ID in preferences.disabledSourceIds) return null
        return when (val authorization = requestAuthorization()) {
            is GmailAuthorization.NeedsUserAction -> throw GmailReauthenticationRequiredException()
            is GmailAuthorization.Authorized -> syncWithToken(authorization.accessToken)
        }
    }

    suspend fun syncWithToken(accessToken: String): GmailSyncResult {
        val startHistoryId = preferences.gmailHistoryId
        if (startHistoryId.isNullOrBlank()) return finishConnection(accessToken)
        val syncStartedAt = System.currentTimeMillis()
        return try {
            val history = withContext(Dispatchers.IO) {
                fetchHistory(accessToken, startHistoryId)
            }
            val newMessages = withContext(Dispatchers.IO) {
                history.messageIds.distinct().mapNotNull { messageId ->
                    runCatching { fetchMessage(accessToken, messageId) }.getOrNull()
                }
            }.sortedBy(GmailMessage::receivedAtMillis)
            newMessages.forEach { message ->
                preferences.upsertPendingMail(
                    CapturedMail(
                        notificationKey = "gmail:${message.id}",
                        sourceId = GMAIL_SOURCE_ID,
                        sender = message.sender,
                        subject = message.subject,
                        preview = null,
                        receivedAtMillis = message.receivedAtMillis,
                        collectionMethod = MailCollectionMethod.DIRECT_ACCOUNT,
                    ),
                )
            }
            preferences.gmailHistoryId = history.latestHistoryId
            preferences.lastGmailSyncMillis = syncStartedAt
            preferences.lastGmailNewMailCount = newMessages.size
            preferences.lastGmailError = null
            GmailSyncResult(newMessages.size, baselineCreated = false)
        } catch (error: GmailApiException) {
            if (error.status == 404) {
                // Gmail history IDs expire. Reset to the current point without replaying old mail.
                finishConnection(accessToken)
            } else {
                throw error
            }
        }
    }

    suspend fun disconnect() {
        val email = preferences.gmailEmail
        if (!email.isNullOrBlank()) {
            val request = RevokeAccessRequest.builder()
                .setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
                .setScopes(requestedScopes)
                .build()
            runCatching { authorizationClient.revokeAccess(request).await() }
        }
        preferences.clearGmailConnection()
    }

    private fun AuthorizationResult.toAuthorization(): GmailAuthorization {
        accessToken?.let { return GmailAuthorization.Authorized(it) }
        if (hasResolution()) {
            return GmailAuthorization.NeedsUserAction(
                pendingIntent ?: throw GmailReauthenticationRequiredException(),
            )
        }
        throw GmailReauthenticationRequiredException()
    }

    private fun fetchProfile(accessToken: String): GmailProfile {
        val json = getJson(GMAIL_PROFILE_URL, accessToken)
        val email = json.optString("emailAddress").takeIf(String::isNotBlank)
            ?: throw IOException("Gmail 계정 주소를 확인하지 못했습니다.")
        val historyId = json.optString("historyId").takeIf(String::isNotBlank)
            ?: throw IOException("Gmail 동기화 기준을 만들지 못했습니다.")
        return GmailProfile(email, historyId)
    }

    private fun fetchHistory(accessToken: String, startHistoryId: String): GmailHistory {
        val ids = linkedSetOf<String>()
        var nextPageToken: String? = null
        var latestHistoryId = startHistoryId
        var page = 0
        do {
            val uri = Uri.parse(GMAIL_HISTORY_URL).buildUpon()
                .appendQueryParameter("startHistoryId", startHistoryId)
                .appendQueryParameter("historyTypes", "messageAdded")
                .appendQueryParameter("labelId", "INBOX")
                .appendQueryParameter("maxResults", "500")
                .apply { nextPageToken?.let { appendQueryParameter("pageToken", it) } }
                .build()
            val json = getJson(uri.toString(), accessToken)
            latestHistoryId = json.optString("historyId")
                .takeIf(String::isNotBlank) ?: latestHistoryId
            val history = json.optJSONArray("history")
            if (history != null) {
                for (historyIndex in 0 until history.length()) {
                    val added = history.optJSONObject(historyIndex)
                        ?.optJSONArray("messagesAdded") ?: continue
                    for (messageIndex in 0 until added.length()) {
                        val message = added.optJSONObject(messageIndex)
                            ?.optJSONObject("message") ?: continue
                        val labels = message.optJSONArray("labelIds")
                        val isInbox = labels == null || (0 until labels.length()).any {
                            labels.optString(it) == "INBOX"
                        }
                        if (isInbox) {
                            message.optString("id").takeIf(String::isNotBlank)?.let(ids::add)
                        }
                    }
                }
            }
            nextPageToken = json.optString("nextPageToken").takeIf(String::isNotBlank)
            page += 1
        } while (nextPageToken != null && page < MAX_HISTORY_PAGES)
        return GmailHistory(ids.toList(), latestHistoryId)
    }

    private fun fetchMessage(accessToken: String, id: String): GmailMessage? {
        val uri = Uri.parse("$GMAIL_MESSAGES_URL/${Uri.encode(id)}").buildUpon()
            .appendQueryParameter("format", "metadata")
            .appendQueryParameter("metadataHeaders", "From")
            .appendQueryParameter("metadataHeaders", "Subject")
            .build()
        val json = getJson(uri.toString(), accessToken)
        val labels = json.optJSONArray("labelIds")
        if (labels != null && (0 until labels.length()).none { labels.optString(it) == "INBOX" }) {
            return null
        }
        val headers = json.optJSONObject("payload")?.optJSONArray("headers")
        var sender = "발신자 미제공"
        var subject = "(제목 없음)"
        if (headers != null) {
            for (index in 0 until headers.length()) {
                val header = headers.optJSONObject(index) ?: continue
                when (header.optString("name").lowercase()) {
                    "from" -> sender = header.optString("value").ifBlank { sender }
                    "subject" -> subject = header.optString("value").ifBlank { subject }
                }
            }
        }
        return GmailMessage(
            id = json.optString("id").takeIf(String::isNotBlank) ?: return null,
            sender = sender.take(500),
            subject = subject.take(500),
            receivedAtMillis = json.optString("internalDate").toLongOrNull()
                ?: System.currentTimeMillis(),
        )
    }

    private fun getJson(url: String, accessToken: String): JSONObject {
        val parsed = URL(url)
        if (parsed.protocol != "https" || parsed.host != GMAIL_HOST) {
            throw IOException("Gmail API가 아닌 주소의 응답은 열지 않았습니다.")
        }
        val connection = parsed.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw GmailApiException(status, gmailError(status, body))
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun gmailError(status: Int, body: String): String {
        val serviceMessage = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf(String::isNotBlank)
        return when (status) {
            401 -> "Google 로그인이 만료되었습니다. Gmail 계정을 다시 연결해 주세요."
            403 -> "Gmail 메타데이터 읽기 권한이 없습니다. Gmail API와 OAuth 설정을 확인해 주세요."
            429 -> "Google 요청 한도에 도달했습니다. 잠시 뒤 다시 시도해 주세요."
            else -> serviceMessage?.let { "Gmail API 오류: ${it.take(300)}" }
                ?: "Gmail 요청에 실패했습니다. (HTTP $status)"
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener {
            continuation.resumeWithException(IOException("Google 계정 연결을 취소했습니다."))
        }
    }

    private data class GmailProfile(val email: String, val historyId: String)
    private data class GmailHistory(val messageIds: List<String>, val latestHistoryId: String)
    private data class GmailMessage(
        val id: String,
        val sender: String,
        val subject: String,
        val receivedAtMillis: Long,
    )

    companion object {
        const val GMAIL_METADATA_SCOPE = "https://www.googleapis.com/auth/gmail.metadata"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val GMAIL_SOURCE_ID = "gmail"
        private const val GMAIL_HOST = "gmail.googleapis.com"
        private const val GMAIL_PROFILE_URL = "https://gmail.googleapis.com/gmail/v1/users/me/profile"
        private const val GMAIL_HISTORY_URL = "https://gmail.googleapis.com/gmail/v1/users/me/history"
        private const val GMAIL_MESSAGES_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages"
        private const val MAX_HISTORY_PAGES = 10
        private const val NETWORK_TIMEOUT_MILLIS = 15_000
    }
}

private class GmailReauthenticationRequiredException(cause: Throwable? = null) : Exception(
    "Google 로그인이 필요합니다. Gmail 계정의 ‘연결’ 또는 ‘지금 확인’을 눌러 주세요.",
    cause,
)

private class GmailApiException(
    val status: Int,
    message: String,
) : IOException(message)
