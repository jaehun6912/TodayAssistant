package com.example.galaxycalendarprobe.mail

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.example.galaxycalendarprobe.R
import com.example.galaxycalendarprobe.todo.TodoPreferences
import com.example.galaxycalendarprobe.todo.TodoScheduler
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class OutlookSyncResult(
    val newMailCount: Int,
    val baselineCreated: Boolean,
)

class OutlookMailRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = MailPreferences(appContext)
    private val application = CompletableDeferred<ISingleAccountPublicClientApplication>()

    init {
        PublicClientApplication.createSingleAccountPublicClientApplication(
            appContext,
            R.raw.auth_config_single_account,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication) {
                    this@OutlookMailRepository.application.complete(application)
                }

                override fun onError(exception: MsalException) {
                    this@OutlookMailRepository.application.completeExceptionally(exception)
                }
            },
        )
    }

    suspend fun loadAccount(): DirectMailAccount? {
        val account = currentAccount()
        if (account == null) {
            preferences.clearOutlookConnection(removePendingMails = false)
            return null
        }
        return rememberAccount(account)
    }

    suspend fun signIn(activity: Activity): DirectMailAccount {
        val client = application.await()
        val result = suspendCoroutine<IAuthenticationResult> { continuation ->
            client.signIn(
                SignInParameters.builder()
                    .withActivity(activity)
                    .withScopes(SCOPES.toList())
                    .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        continuation.resume(authenticationResult)
                    }

                    override fun onError(exception: MsalException) {
                        continuation.resumeWithException(exception)
                    }

                    override fun onCancel() {
                        continuation.resumeWithException(
                            OutlookAuthenticationCancelledException(),
                        )
                    }
                })
                    .build(),
            )
        }
        val account = rememberAccount(result.account)
        preferences.removePendingMailsForSource(OUTLOOK_SOURCE_ID)
        createBaseline(result.accessToken)
        return account
    }

    suspend fun signOut() {
        val client = application.await()
        suspendCoroutine<Unit> { continuation ->
            client.signOut(
                object : ISingleAccountPublicClientApplication.SignOutCallback {
                    override fun onSignOut() {
                        continuation.resume(Unit)
                    }

                    override fun onError(exception: MsalException) {
                        continuation.resumeWithException(exception)
                    }
                },
            )
        }
        preferences.clearOutlookConnection()
        TodoScheduler(appContext).cancelAll()
        TodoPreferences(appContext).clearDisconnectedAccount()
    }

    suspend fun syncIfConnected(): OutlookSyncResult? {
        val account = currentAccount() ?: run {
            preferences.clearOutlookConnection(removePendingMails = false)
            return null
        }
        rememberAccount(account)
        if (OUTLOOK_SOURCE_ID in preferences.disabledSourceIds) return null
        val token = acquireTokenSilently(account)
        return if (!preferences.outlookBaselineInitialized) {
            createBaseline(token)
        } else {
            collectNewMessages(token)
        }
    }

    private suspend fun currentAccount(): IAccount? {
        val client = application.await()
        return suspendCoroutine { continuation ->
            client.getCurrentAccountAsync(
                object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                    override fun onAccountLoaded(activeAccount: IAccount?) {
                        continuation.resume(activeAccount)
                    }

                    override fun onAccountChanged(
                        priorAccount: IAccount?,
                        currentAccount: IAccount?,
                    ) = Unit

                    override fun onError(exception: MsalException) {
                        continuation.resumeWithException(exception)
                    }
                },
            )
        }
    }

    private suspend fun acquireTokenSilently(account: IAccount): String {
        val client = application.await()
        return suspendCoroutine { continuation ->
            val parameters = AcquireTokenSilentParameters.Builder()
                .forAccount(account)
                .fromAuthority(account.authority)
                .withScopes(SCOPES.toList())
                .withCallback(
                    object : SilentAuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            continuation.resume(authenticationResult.accessToken)
                        }

                        override fun onError(exception: MsalException) {
                            val mapped = if (exception is MsalUiRequiredException) {
                                OutlookReauthenticationRequiredException(exception)
                            } else {
                                exception
                            }
                            continuation.resumeWithException(mapped)
                        }
                    },
                )
                .build()
            client.acquireTokenSilentAsync(parameters)
        }
    }

    private suspend fun createBaseline(accessToken: String): OutlookSyncResult {
        val syncStartedAt = System.currentTimeMillis()
        val messages = withContext(Dispatchers.IO) {
            fetchMessages(
                accessToken = accessToken,
                receivedAfterMillis = null,
                maxPages = 1,
            )
        }
        preferences.seenOutlookMessageIds = messages.mapTo(mutableSetOf()) { it.id }
        preferences.outlookBaselineInitialized = true
        preferences.lastOutlookSyncMillis = syncStartedAt
        preferences.lastOutlookNewMailCount = 0
        preferences.lastOutlookError = null
        return OutlookSyncResult(newMailCount = 0, baselineCreated = true)
    }

    private suspend fun collectNewMessages(accessToken: String): OutlookSyncResult {
        val syncStartedAt = System.currentTimeMillis()
        val previousSync = preferences.lastOutlookSyncMillis
        val receivedAfter = (previousSync - SYNC_OVERLAP_MILLIS).coerceAtLeast(0L)
        val messages = withContext(Dispatchers.IO) {
            fetchMessages(
                accessToken = accessToken,
                receivedAfterMillis = receivedAfter,
                maxPages = MAX_GRAPH_PAGES,
            )
        }
        val previouslySeen = preferences.seenOutlookMessageIds
        val newMessages = messages
            .filterNot { it.id in previouslySeen }
            .sortedBy(OutlookMessage::receivedAtMillis)

        newMessages.forEach { message ->
            preferences.upsertPendingMail(
                CapturedMail(
                    notificationKey = "graph:${message.id}",
                    sourceId = OUTLOOK_SOURCE_ID,
                    sender = message.sender,
                    subject = message.subject,
                    preview = null,
                    receivedAtMillis = message.receivedAtMillis,
                    collectionMethod = MailCollectionMethod.DIRECT_ACCOUNT,
                ),
            )
        }
        preferences.seenOutlookMessageIds = buildSet {
            messages.forEach { add(it.id) }
            previouslySeen.forEach { add(it) }
        }
        preferences.lastOutlookSyncMillis = syncStartedAt
        preferences.lastOutlookNewMailCount = newMessages.size
        preferences.lastOutlookError = null
        return OutlookSyncResult(
            newMailCount = newMessages.size,
            baselineCreated = false,
        )
    }

    private fun rememberAccount(account: IAccount): DirectMailAccount {
        val username = account.username.takeIf(String::isNotBlank) ?: "Microsoft 계정"
        preferences.outlookDirectConnected = true
        preferences.outlookUsername = username
        preferences.lastOutlookError = null
        return DirectMailAccount(username)
    }

    private fun fetchMessages(
        accessToken: String,
        receivedAfterMillis: Long?,
        maxPages: Int,
    ): List<OutlookMessage> {
        val firstUrl = Uri.parse(GRAPH_MESSAGES_URL).buildUpon()
            .appendQueryParameter("\$select", "id,subject,sender,receivedDateTime")
            .apply {
                if (receivedAfterMillis != null) {
                    appendQueryParameter(
                        "\$filter",
                        "receivedDateTime ge ${Instant.ofEpochMilli(receivedAfterMillis)}",
                    )
                    appendQueryParameter("\$orderby", "receivedDateTime desc")
                }
            }
            .appendQueryParameter("\$top", PAGE_SIZE.toString())
            .build()
            .toString()

        val result = mutableListOf<OutlookMessage>()
        var nextUrl: String? = firstUrl
        var page = 0
        while (nextUrl != null && page < maxPages) {
            ensureGraphUrl(nextUrl)
            val response = getJson(nextUrl, accessToken)
            val values = response.optJSONArray("value")
            if (values != null) {
                for (index in 0 until values.length()) {
                    parseMessage(values.optJSONObject(index))?.let(result::add)
                }
            }
            nextUrl = response.optString("@odata.nextLink")
                .takeIf(String::isNotBlank)
            page += 1
        }
        return result.distinctBy(OutlookMessage::id)
    }

    private fun getJson(url: String, accessToken: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw OutlookGraphException(status, graphErrorMessage(status, body))
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseMessage(json: JSONObject?): OutlookMessage? {
        json ?: return null
        val id = json.optString("id").takeIf(String::isNotBlank) ?: return null
        val receivedAt = runCatching {
            Instant.parse(json.optString("receivedDateTime")).toEpochMilli()
        }.getOrNull() ?: return null
        val emailAddress = json.optJSONObject("sender")?.optJSONObject("emailAddress")
        val sender = emailAddress?.optString("name")?.takeIf(String::isNotBlank)
            ?: emailAddress?.optString("address")?.takeIf(String::isNotBlank)
            ?: "발신자 미제공"
        val subject = json.optString("subject").takeIf(String::isNotBlank) ?: "(제목 없음)"
        return OutlookMessage(
            id = id,
            sender = sender.take(500),
            subject = subject.take(500),
            receivedAtMillis = receivedAt,
        )
    }

    private fun ensureGraphUrl(value: String) {
        val url = URL(value)
        if (url.protocol != "https" || url.host != GRAPH_HOST) {
            throw IOException("Microsoft Graph가 아닌 주소의 응답은 열지 않았습니다.")
        }
    }

    private fun graphErrorMessage(status: Int, body: String): String {
        val serviceMessage = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf(String::isNotBlank)
        return when (status) {
            401 -> "Microsoft 로그인이 만료되었습니다. Outlook 계정을 다시 연결해 주세요."
            403 -> "메일 읽기 권한이 없습니다. Entra의 Mail.ReadBasic 위임 권한을 확인해 주세요."
            429 -> "Microsoft 요청 한도에 도달했습니다. 잠시 뒤 다시 시도해 주세요."
            else -> serviceMessage?.let { "Microsoft Graph 오류: ${it.take(300)}" }
                ?: "Microsoft Graph 요청에 실패했습니다. (HTTP $status)"
        }
    }

    private data class OutlookMessage(
        val id: String,
        val sender: String,
        val subject: String,
        val receivedAtMillis: Long,
    )

    companion object {
        private val SCOPES = arrayOf("Mail.ReadBasic")
        private const val OUTLOOK_SOURCE_ID = "outlook"
        private const val GRAPH_HOST = "graph.microsoft.com"
        private const val GRAPH_MESSAGES_URL =
            "https://graph.microsoft.com/v1.0/me/mailFolders/inbox/messages"
        private const val PAGE_SIZE = 100
        private const val MAX_GRAPH_PAGES = 5
        private const val SYNC_OVERLAP_MILLIS = 5 * 60 * 1_000L
        private const val NETWORK_TIMEOUT_MILLIS = 15_000
    }
}

private class OutlookAuthenticationCancelledException : Exception(
    "Microsoft 계정 연결을 취소했습니다.",
)

private class OutlookReauthenticationRequiredException(cause: Throwable) : Exception(
    "Microsoft 로그인이 만료되었습니다. Outlook 계정을 다시 연결해 주세요.",
    cause,
)

private class OutlookGraphException(
    val status: Int,
    message: String,
) : IOException(message)
