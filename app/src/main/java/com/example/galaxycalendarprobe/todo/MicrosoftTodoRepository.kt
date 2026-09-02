package com.example.galaxycalendarprobe.todo

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.example.galaxycalendarprobe.R
import com.example.galaxycalendarprobe.mail.MailPreferences
import com.microsoft.identity.client.AcquireTokenParameters
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
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MicrosoftTodoRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = TodoPreferences(appContext)
    private val mailPreferences = MailPreferences(appContext)
    private val application = CompletableDeferred<ISingleAccountPublicClientApplication>()

    init {
        PublicClientApplication.createSingleAccountPublicClientApplication(
            appContext,
            R.raw.auth_config_single_account,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication) {
                    this@MicrosoftTodoRepository.application.complete(application)
                }

                override fun onError(exception: MsalException) {
                    this@MicrosoftTodoRepository.application.completeExceptionally(exception)
                }
            },
        )
    }

    suspend fun loadAccount(): String? {
        val account = currentAccount()
        if (account == null) {
            clearDisconnectedAccount()
            return null
        }
        return rememberAccount(account)
    }

    suspend fun authorizeAndSync(activity: Activity) {
        val account = currentAccount()
        val result = if (account == null) signIn(activity) else acquireTokenInteractively(activity, account)
        rememberAccount(result.account)
        syncWithToken(result.accessToken)
    }

    suspend fun syncIfConnected(): Int? {
        val account = currentAccount() ?: run {
            clearDisconnectedAccount()
            return null
        }
        rememberAccount(account)
        val token = acquireTokenSilently(account)
        return syncWithToken(token)
    }

    private suspend fun signIn(activity: Activity): IAuthenticationResult {
        val client = application.await()
        return suspendCoroutine { continuation ->
            client.signIn(
                SignInParameters.builder()
                    .withActivity(activity)
                    .withScopes(listOf(TASKS_SCOPE, MAIL_SCOPE))
                    .withCallback(authenticationCallback(continuation))
                    .build(),
            )
        }
    }

    private suspend fun acquireTokenInteractively(
        activity: Activity,
        account: IAccount,
    ): IAuthenticationResult {
        val client = application.await()
        return suspendCoroutine { continuation ->
            client.acquireToken(
                AcquireTokenParameters.Builder()
                    .startAuthorizationFromActivity(activity)
                    .forAccount(account)
                    .fromAuthority(account.authority)
                    .withScopes(listOf(TASKS_SCOPE))
                    .withCallback(authenticationCallback(continuation))
                    .build(),
            )
        }
    }

    private fun authenticationCallback(
        continuation: kotlin.coroutines.Continuation<IAuthenticationResult>,
    ) = object : AuthenticationCallback {
        override fun onSuccess(authenticationResult: IAuthenticationResult) {
            continuation.resume(authenticationResult)
        }

        override fun onError(exception: MsalException) {
            continuation.resumeWithException(exception)
        }

        override fun onCancel() {
            continuation.resumeWithException(IOException("Microsoft 권한 승인을 취소했습니다."))
        }
    }

    private suspend fun acquireTokenSilently(account: IAccount): String {
        val client = application.await()
        return suspendCoroutine { continuation ->
            client.acquireTokenSilentAsync(
                AcquireTokenSilentParameters.Builder()
                    .forAccount(account)
                    .fromAuthority(account.authority)
                    .withScopes(listOf(TASKS_SCOPE))
                    .withCallback(object : SilentAuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            continuation.resume(authenticationResult.accessToken)
                        }

                        override fun onError(exception: MsalException) {
                            continuation.resumeWithException(
                                if (exception is MsalUiRequiredException) {
                                    TodoConsentRequiredException(exception)
                                } else exception,
                            )
                        }
                    })
                    .build(),
            )
        }
    }

    private suspend fun currentAccount(): IAccount? {
        val client = application.await()
        return suspendCoroutine { continuation ->
            client.getCurrentAccountAsync(
                object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                    override fun onAccountLoaded(activeAccount: IAccount?) = continuation.resume(activeAccount)
                    override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) = Unit
                    override fun onError(exception: MsalException) = continuation.resumeWithException(exception)
                },
            )
        }
    }

    private fun rememberAccount(account: IAccount): String {
        val username = account.username.takeIf(String::isNotBlank) ?: "Microsoft 계정"
        preferences.microsoftAccount = username
        preferences.lastError = null
        // Both modules use the same single-account MSAL cache.
        mailPreferences.outlookDirectConnected = true
        mailPreferences.outlookUsername = username
        return username
    }

    private fun clearDisconnectedAccount() {
        TodoScheduler(appContext).cancelAll()
        preferences.clearDisconnectedAccount()
    }

    private suspend fun syncWithToken(accessToken: String): Int = withContext(Dispatchers.IO) {
        val lists = fetchLists(accessToken)
        val tasks = lists.flatMap { list -> fetchTasks(accessToken, list.id) }
            .distinctBy(TodoTaskItem::id)
            .sortedWith(compareBy<TodoTaskItem> { it.effectiveAtMillis == null }
                .thenBy { it.effectiveAtMillis ?: Long.MAX_VALUE })
        preferences.lists = lists
        preferences.tasks = tasks
        preferences.lastSyncMillis = System.currentTimeMillis()
        preferences.lastError = null
        tasks.size
    }

    private fun fetchLists(accessToken: String): List<TodoListInfo> {
        val result = mutableListOf<TodoListInfo>()
        // The personal-account To Do backend can reject otherwise valid $select/$top
        // combinations with invalidRequest. Use the documented base endpoint and follow
        // @odata.nextLink for paging instead.
        var nextUrl: String? = GRAPH_LISTS_URL
        var page = 0
        while (nextUrl != null && page < MAX_PAGES) {
            val json = getJson(nextUrl, accessToken)
            val values = json.optJSONArray("value")
            if (values != null) for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                result += TodoListInfo(id, item.optString("displayName").ifBlank { "이름 없는 목록" })
            }
            nextUrl = json.optString("@odata.nextLink").takeIf(String::isNotBlank)
            page += 1
        }
        return result.distinctBy(TodoListInfo::id)
    }

    private fun fetchTasks(accessToken: String, listId: String): List<TodoTaskItem> {
        val result = mutableListOf<TodoTaskItem>()
        var nextUrl: String? = "$GRAPH_LISTS_URL/${Uri.encode(listId)}/tasks"
        var page = 0
        while (nextUrl != null && page < MAX_PAGES) {
            val json = getJson(nextUrl, accessToken)
            val values = json.optJSONArray("value")
            if (values != null) for (index in 0 until values.length()) {
                parseTask(values.optJSONObject(index), listId)?.let(result::add)
            }
            nextUrl = json.optString("@odata.nextLink").takeIf(String::isNotBlank)
            page += 1
        }
        return result
    }

    private fun parseTask(json: JSONObject?, listId: String): TodoTaskItem? {
        json ?: return null
        if (json.optString("status") == "completed") return null
        return TodoTaskItem(
            id = json.optString("id").takeIf(String::isNotBlank) ?: return null,
            listId = listId,
            title = json.optString("title").ifBlank { "제목 없는 할 일" }.take(500),
            importance = json.optString("importance").ifBlank { "normal" },
            dueAtMillis = parseGraphDateTime(json.optJSONObject("dueDateTime")),
            reminderAtMillis = parseGraphDateTime(json.optJSONObject("reminderDateTime")),
        )
    }

    private fun parseGraphDateTime(json: JSONObject?): Long? {
        json ?: return null
        val value = json.optString("dateTime").takeIf(String::isNotBlank) ?: return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching {
                val zone = when (val name = json.optString("timeZone")) {
                    "UTC" -> ZoneId.of("UTC")
                    "Korea Standard Time" -> ZoneId.of("Asia/Seoul")
                    else -> runCatching { ZoneId.of(name) }.getOrDefault(ZoneId.systemDefault())
                }
                LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()
    }

    private fun getJson(url: String, token: String): JSONObject {
        val parsed = URL(url)
        if (parsed.protocol != "https" || parsed.host != GRAPH_HOST) {
            throw IOException("Microsoft Graph가 아닌 주소의 응답은 열지 않았습니다.")
        }
        val connection = parsed.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException(todoError(status, body))
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun todoError(status: Int, body: String): String {
        val serviceError = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val serviceMessage = serviceError?.optString("message")?.takeIf(String::isNotBlank)
        val serviceCode = serviceError?.optString("code")?.takeIf(String::isNotBlank)
        val innerCode = serviceError?.optJSONObject("innerError")
            ?.optString("code")?.takeIf(String::isNotBlank)
        return when (status) {
            401 -> "Microsoft 로그인이 만료되었습니다. 권한 승인을 다시 해 주세요."
            403 -> "할 일 읽기 권한이 없습니다. Tasks.Read 위임 권한을 확인해 주세요."
            429 -> "Microsoft 요청 한도에 도달했습니다. 잠시 뒤 다시 시도해 주세요."
            else -> serviceMessage?.let {
                buildString {
                    append("Microsoft To Do 오류: ")
                    append(it.take(240))
                    listOfNotNull(serviceCode, innerCode).distinct().takeIf(List<String>::isNotEmpty)
                        ?.let { codes -> append(" (${codes.joinToString(" / ")})") }
                }
            }
                ?: "Microsoft To Do 요청에 실패했습니다. (HTTP $status)"
        }
    }

    companion object {
        private const val TASKS_SCOPE = "Tasks.Read"
        private const val MAIL_SCOPE = "Mail.ReadBasic"
        private const val GRAPH_HOST = "graph.microsoft.com"
        private const val GRAPH_LISTS_URL = "https://graph.microsoft.com/v1.0/me/todo/lists"
        private const val MAX_PAGES = 5
        private const val NETWORK_TIMEOUT_MILLIS = 15_000
    }
}

private class TodoConsentRequiredException(cause: Throwable) : Exception(
    "Microsoft To Do 읽기 승인이 필요합니다. ‘Microsoft 권한 승인’을 눌러 주세요.",
    cause,
)
