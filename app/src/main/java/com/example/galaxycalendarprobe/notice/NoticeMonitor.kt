package com.example.galaxycalendarprobe.notice

import android.content.Context
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class NoticeMonitor(context: Context) {
    private val preferences = NoticePreferences(context.applicationContext)
    private val repository = NoticeRepository()

    fun cachedState(): NoticeUiState {
        val disabled = preferences.disabledSourceIds
        val sources = preferences.allSources()
        return NoticeUiState(
            monitoringEnabled = preferences.monitoringEnabled,
            sources = sources.map { source ->
                NoticeSourceState(
                    source = source,
                    enabled = source.id !in disabled,
                    locked = preferences.isBuiltInLocked(source.id),
                    modified = preferences.isBuiltInModified(source.id),
                    articles = preferences.cachedArticles(source.id),
                )
            },
            lastCheckedMillis = preferences.lastCheckedMillis,
        )
    }

    suspend fun check(): NoticeCheckResult = coroutineScope {
        val disabled = preferences.disabledSourceIds
        val sources = preferences.allSources()
        val enabledSources = sources.filterNot { it.id in disabled }
        val fetches = enabledSources.map { source ->
            async { source to runCatching { repository.fetch(source) } }
        }.awaitAll().toMap()

        val allNewArticles = mutableListOf<NoticeArticle>()
        val states = sources.map { source ->
            val enabled = source.id !in disabled
            if (!enabled) {
                NoticeSourceState(
                    source = source,
                    enabled = false,
                    locked = preferences.isBuiltInLocked(source.id),
                    modified = preferences.isBuiltInModified(source.id),
                    articles = preferences.cachedArticles(source.id),
                )
            } else {
                fetches.getValue(source).fold(
                    onSuccess = { articles ->
                        val seenIds = preferences.seenArticleIds(source.id)
                        val initialized = preferences.isInitialized(source.id)
                        val newArticles = if (initialized) {
                            articles.filterNot { it.articleId in seenIds }
                        } else {
                            emptyList()
                        }
                        allNewArticles += newArticles
                        preferences.saveSeenArticleIds(
                            source.id,
                            seenIds + articles.map { it.articleId },
                        )
                        preferences.saveCachedArticles(source.id, articles)
                        preferences.setInitialized(source.id)
                        NoticeSourceState(
                            source = source,
                            enabled = true,
                            locked = preferences.isBuiltInLocked(source.id),
                            modified = preferences.isBuiltInModified(source.id),
                            articles = articles,
                        )
                    },
                    onFailure = { error ->
                        NoticeSourceState(
                            source = source,
                            enabled = true,
                            locked = preferences.isBuiltInLocked(source.id),
                            modified = preferences.isBuiltInModified(source.id),
                            articles = preferences.cachedArticles(source.id),
                            error = error.toNoticeMessage(),
                        )
                    },
                )
            }
        }
        val checkedAt = System.currentTimeMillis()
        preferences.lastCheckedMillis = checkedAt
        NoticeCheckResult(
            sources = states,
            newArticles = allNewArticles,
            checkedAtMillis = checkedAt,
        )
    }
}

private fun Throwable.toNoticeMessage(): String = when (this) {
    is SocketTimeoutException -> "응답 시간이 초과되었습니다."
    is UnknownHostException -> "네트워크 또는 사이트 주소를 확인할 수 없습니다."
    is SSLException -> "사이트 보안 연결에 실패했습니다."
    else -> message?.takeIf(String::isNotBlank) ?: "게시판을 확인하지 못했습니다."
}
