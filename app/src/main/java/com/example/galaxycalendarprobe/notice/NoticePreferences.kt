package com.example.galaxycalendarprobe.notice

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

class NoticePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )

    var monitoringEnabled: Boolean
        get() = preferences.getBoolean(KEY_MONITORING_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()

    var disabledSourceIds: Set<String>
        get() = preferences.getStringSet(KEY_DISABLED_SOURCE_IDS, emptySet()).orEmpty()
        set(value) = preferences.edit().putStringSet(KEY_DISABLED_SOURCE_IDS, value).apply()

    var unlockedBuiltInSourceIds: Set<String>
        get() = preferences.getStringSet(KEY_UNLOCKED_BUILT_IN_SOURCE_IDS, emptySet()).orEmpty()
        set(value) = preferences.edit()
            .putStringSet(KEY_UNLOCKED_BUILT_IN_SOURCE_IDS, value)
            .apply()

    fun allSources(): List<NoticeSource> {
        val overrides = builtInOverrides()
        return NoticeSources.builtIns.map { source -> overrides[source.id] ?: source } +
            customSources()
    }

    fun isBuiltInLocked(sourceId: String): Boolean =
        sourceId in NoticeSources.builtIns.mapTo(mutableSetOf()) { it.id } &&
            sourceId !in unlockedBuiltInSourceIds

    fun isBuiltInModified(sourceId: String): Boolean = sourceId in builtInOverrides()

    fun setBuiltInLocked(sourceId: String, locked: Boolean) {
        if (NoticeSources.builtIns.none { it.id == sourceId }) return
        val unlocked = unlockedBuiltInSourceIds.toMutableSet()
        if (locked) unlocked -= sourceId else unlocked += sourceId
        unlockedBuiltInSourceIds = unlocked
    }

    fun updateBuiltInSource(sourceId: String, name: String, url: String) {
        check(!isBuiltInLocked(sourceId)) { "잠금을 해제한 후 수정해 주세요." }
        val original = NoticeSources.builtIns.firstOrNull { it.id == sourceId }
            ?: error("기본 사이트를 찾지 못했습니다.")
        val updated = original.copy(name = name.trim(), url = url.trim())
        val overrides = builtInOverrides().toMutableMap()
        if (updated.name == original.name && updated.url == original.url) {
            overrides -= sourceId
        } else {
            overrides[sourceId] = updated
        }
        saveBuiltInOverrides(overrides.values.toList())
        clearSourceData(sourceId)
    }

    fun restoreBuiltInSource(sourceId: String) {
        val overrides = builtInOverrides().filterKeys { it != sourceId }
        saveBuiltInOverrides(overrides.values.toList())
        clearSourceData(sourceId)
    }

    private fun builtInOverrides(): Map<String, NoticeSource> = preferences
        .getStringSet(KEY_BUILT_IN_SOURCE_OVERRIDES, emptySet())
        .orEmpty()
        .mapNotNull(::decodeBuiltInOverride)
        .associateBy(NoticeSource::id)

    private fun saveBuiltInOverrides(sources: List<NoticeSource>) {
        preferences.edit()
            .putStringSet(KEY_BUILT_IN_SOURCE_OVERRIDES, sources.map(::encodeSource).toSet())
            .apply()
    }

    fun customSources(): List<NoticeSource> = preferences
        .getStringSet(KEY_CUSTOM_SOURCES, emptySet())
        .orEmpty()
        .mapNotNull(::decodeSource)
        .sortedBy { it.name.lowercase() }

    fun addCustomSource(name: String, url: String): NoticeSource {
        check(customSources().size < MAX_CUSTOM_SOURCES) {
            "직접 추가 사이트는 최대 ${MAX_CUSTOM_SOURCES}개까지 등록할 수 있습니다."
        }
        val source = NoticeSource(
            id = "custom:${UUID.randomUUID()}",
            name = name.trim(),
            organization = runCatching { java.net.URI(url.trim()).host }
                .getOrNull()
                .orEmpty(),
            url = url.trim(),
        )
        saveCustomSources(customSources() + source)
        return source
    }

    fun updateCustomSource(sourceId: String, name: String, url: String) {
        val updated = customSources().map { source ->
            if (source.id != sourceId) source else source.copy(
                name = name.trim(),
                organization = runCatching { java.net.URI(url.trim()).host }
                    .getOrNull()
                    .orEmpty(),
                url = url.trim(),
            )
        }
        saveCustomSources(updated)
        clearSourceData(sourceId)
    }

    fun deleteCustomSource(sourceId: String) {
        saveCustomSources(customSources().filterNot { it.id == sourceId })
        disabledSourceIds = disabledSourceIds - sourceId
        clearSourceData(sourceId)
    }

    private fun saveCustomSources(sources: List<NoticeSource>) {
        preferences.edit()
            .putStringSet(KEY_CUSTOM_SOURCES, sources.map(::encodeSource).toSet())
            .apply()
    }

    private fun clearSourceData(sourceId: String) {
        preferences.edit()
            .remove(initializedKey(sourceId))
            .remove(seenKey(sourceId))
            .remove(cacheKey(sourceId))
            .apply()
    }

    var lastCheckedMillis: Long?
        get() = preferences.getLong(KEY_LAST_CHECKED_MILLIS, 0L).takeIf { it > 0L }
        set(value) {
            val editor = preferences.edit()
            if (value == null) editor.remove(KEY_LAST_CHECKED_MILLIS)
            else editor.putLong(KEY_LAST_CHECKED_MILLIS, value)
            editor.apply()
        }

    fun isInitialized(sourceId: String): Boolean =
        preferences.getBoolean(initializedKey(sourceId), false)

    fun setInitialized(sourceId: String) {
        preferences.edit().putBoolean(initializedKey(sourceId), true).apply()
    }

    fun seenArticleIds(sourceId: String): Set<String> =
        preferences.getStringSet(seenKey(sourceId), emptySet()).orEmpty()

    fun saveSeenArticleIds(sourceId: String, ids: Set<String>) {
        preferences.edit()
            .putStringSet(seenKey(sourceId), ids.take(MAX_SEEN_IDS).toSet())
            .apply()
    }

    fun cachedArticles(sourceId: String): List<NoticeArticle> =
        preferences.getStringSet(cacheKey(sourceId), emptySet())
            .orEmpty()
            .mapNotNull { decodeArticle(sourceId, it) }
            .sortedWith(articleComparator)

    fun saveCachedArticles(sourceId: String, articles: List<NoticeArticle>) {
        preferences.edit()
            .putStringSet(
                cacheKey(sourceId),
                articles.take(MAX_CACHED_ARTICLES).map(::encodeArticle).toSet(),
            )
            .apply()
    }

    companion object {
        private const val FILE_NAME = "notice_monitor_settings"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_DISABLED_SOURCE_IDS = "disabled_source_ids"
        private const val KEY_CUSTOM_SOURCES = "custom_sources"
        private const val KEY_BUILT_IN_SOURCE_OVERRIDES = "built_in_source_overrides"
        private const val KEY_UNLOCKED_BUILT_IN_SOURCE_IDS = "unlocked_built_in_source_ids"
        private const val KEY_LAST_CHECKED_MILLIS = "last_checked_millis"
        private const val MAX_SEEN_IDS = 200
        private const val MAX_CACHED_ARTICLES = 20
        private const val MAX_CUSTOM_SOURCES = 20
    }
}

private fun initializedKey(sourceId: String) = "initialized_$sourceId"
private fun seenKey(sourceId: String) = "seen_$sourceId"
private fun cacheKey(sourceId: String) = "cache_$sourceId"

private val encoder = Base64.getUrlEncoder().withoutPadding()
private val decoder = Base64.getUrlDecoder()

internal fun encodeSource(source: NoticeSource): String = listOf(
    source.id,
    source.name,
    source.organization,
    source.url,
).joinToString("|") { value ->
    encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}

internal fun decodeSource(encoded: String): NoticeSource? = runCatching {
    val parts = encoded.split('|')
    if (parts.size != 4) return@runCatching null
    val values = parts.map { part ->
        String(decoder.decode(part), StandardCharsets.UTF_8)
    }
    NoticeSource(
        id = values[0],
        name = values[1],
        organization = values[2],
        url = values[3],
        isBuiltIn = false,
    ).takeIf {
        it.id.startsWith("custom:") && it.name.isNotBlank() && it.url.isNotBlank()
    }
}.getOrNull()

internal fun decodeBuiltInOverride(encoded: String): NoticeSource? = runCatching {
    val parts = encoded.split('|')
    if (parts.size != 4) return@runCatching null
    val values = parts.map { part ->
        String(decoder.decode(part), StandardCharsets.UTF_8)
    }
    val original = NoticeSources.builtIns.firstOrNull { it.id == values[0] }
        ?: return@runCatching null
    original.copy(
        name = values[1].takeIf(String::isNotBlank) ?: original.name,
        url = values[3].takeIf(String::isNotBlank) ?: original.url,
    )
}.getOrNull()

private fun encodeArticle(article: NoticeArticle): String = listOf(
    article.articleId,
    article.title,
    article.postedDate.orEmpty(),
    article.url,
).joinToString("|") { value ->
    encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}

private fun decodeArticle(sourceId: String, encoded: String): NoticeArticle? = runCatching {
    val parts = encoded.split('|')
    if (parts.size != 4) return@runCatching null
    val values = parts.map { part ->
        String(decoder.decode(part), StandardCharsets.UTF_8)
    }
    NoticeArticle(
        sourceId = sourceId,
        articleId = values[0],
        title = values[1],
        postedDate = values[2].takeIf(String::isNotBlank),
        url = values[3],
    )
}.getOrNull()

private val articleComparator = compareByDescending<NoticeArticle> {
    it.postedDate.orEmpty()
}.thenByDescending { article ->
    article.articleId.filter(Char::isDigit).toLongOrNull() ?: 0L
}
