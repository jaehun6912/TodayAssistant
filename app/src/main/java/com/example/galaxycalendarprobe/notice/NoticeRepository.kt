package com.example.galaxycalendarprobe.notice

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class NoticeRepository {
    fun fetch(source: NoticeSource): List<NoticeArticle> {
        val separator = if ('?' in source.url) '&' else '?'
        var requestUrl = "${source.url}${separator}_=${System.currentTimeMillis()}"
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            validateNoticeSourceUrl(requestUrl)?.let { message -> error(message) }
            val connection = URL(requestUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android) TodayAssistant/0.7",
            )
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.setRequestProperty("Cache-Control", "no-cache")

            try {
                val status = connection.responseCode
                if (status in REDIRECT_STATUS_CODES) {
                    check(redirectCount < MAX_REDIRECTS) { "사이트 이동 횟수가 너무 많습니다." }
                    val location = connection.getHeaderField("Location")
                        ?.takeIf(String::isNotBlank)
                        ?: error("사이트 이동 주소가 없습니다.")
                    requestUrl = URI(requestUrl).resolve(location).toString()
                    return@repeat
                }
                check(status in 200..299) { "서버 응답 오류($status)" }
                val bytes = connection.inputStream.use { it.readBytes() }
                val charset = connection.contentType.detectCharset()
                val html = String(bytes, charset)
                return parseNoticeHtml(source, html).also { articles ->
                    check(articles.isNotEmpty()) {
                        "게시물 목록을 해석하지 못했습니다. 사이트 구조가 변경됐을 수 있습니다."
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        error("사이트에 연결하지 못했습니다.")
    }

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 12_000
        private const val READ_TIMEOUT_MILLIS = 15_000
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    }
}

internal fun validateCustomSource(name: String, url: String): String? {
    if (name.trim().length !in 2..60) return "사이트 이름을 2~60자로 입력해 주세요."
    return validateNoticeSourceUrl(url)
}

internal fun validateNoticeSourceUrl(value: String): String? {
    val uri = runCatching { URI(value.trim()) }.getOrNull()
        ?: return "올바른 사이트 주소를 입력해 주세요."
    if (!uri.scheme.equals("https", ignoreCase = true)) {
        return "보안을 위해 https:// 주소만 추가할 수 있습니다."
    }
    if (uri.rawUserInfo != null) return "계정 정보가 포함된 주소는 추가할 수 없습니다."
    val host = uri.host?.trim('[', ']')?.lowercase()
        ?: return "사이트 주소의 도메인을 확인해 주세요."
    if (host.isUnsafeNoticeHost()) return "내부 네트워크 주소는 추가할 수 없습니다."
    return null
}

private fun String.isUnsafeNoticeHost(): Boolean {
    if (this == "localhost" || endsWith(".localhost") || endsWith(".local")) return true
    val octets = split('.').mapNotNull(String::toIntOrNull)
    if (octets.size == 4 && octets.all { it in 0..255 }) {
        return octets[0] == 0 || octets[0] == 10 || octets[0] == 127 ||
            (octets[0] == 169 && octets[1] == 254) ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168) || octets[0] >= 224
    }
    val normalized = lowercase()
    return ':' in normalized && (
        normalized == "::" || normalized == "::1" ||
            normalized.startsWith("fc") || normalized.startsWith("fd") ||
            normalized.startsWith("fe8") || normalized.startsWith("fe9") ||
            normalized.startsWith("fea") || normalized.startsWith("feb")
        )
}

internal fun parseNoticeHtml(
    source: NoticeSource,
    html: String,
): List<NoticeArticle> {
    val candidates = buildList {
        addAll(tableRowRegex.findAll(html).map { it.groupValues[1] })
        addAll(listItemRegex.findAll(html).map { it.groupValues[1] })
    }.ifEmpty { listOf(html) }

    return candidates.mapNotNull { row -> parseNoticeRow(source, row) }
        .distinctBy { it.articleId }
        .sortedWith(
            compareByDescending<NoticeArticle> { it.postedDate.orEmpty() }
                .thenByDescending {
                    it.articleId.filter(Char::isDigit).toLongOrNull() ?: 0L
                },
        )
        .take(20)
}

private fun parseNoticeRow(source: NoticeSource, row: String): NoticeArticle? {
    val plainRow = htmlToText(row)
    val date = dateRegex.find(plainRow)?.value?.replace('.', '-')?.replace('/', '-')
        ?: return null
    val anchors = anchorRegex.findAll(row).mapNotNull { match ->
        val href = decodeHtmlEntities(match.groupValues[1]).trim()
        val title = htmlToText(match.groupValues[2])
        AnchorCandidate(href = href, title = title)
            .takeIf {
                title.length >= 3 &&
                    !title.contains("첨부파일") &&
                    !title.contains("페이지")
            }
    }.toList()
    val anchor = anchors.firstOrNull { candidate ->
        val searchable = "${candidate.href} $row"
        findArticleId(searchable) != null ||
            candidate.href.contains("view", ignoreCase = true) ||
            candidate.href.startsWith("javascript:", ignoreCase = true)
    } ?: anchors.firstOrNull() ?: return null

    val articleId = findArticleId("${anchor.href} $row")
        ?: javascriptIdRegex.find("${anchor.href} $row")?.groupValues?.get(1)
        ?: firstNumberCellRegex.find(row)?.groupValues?.get(1)
        ?: "${date}_${anchor.title.hashCode().toUInt()}"
    val url = resolveArticleUrl(source, anchor.href, articleId)

    return NoticeArticle(
        sourceId = source.id,
        articleId = articleId,
        title = anchor.title,
        postedDate = date,
        url = url,
    )
}

private fun resolveArticleUrl(
    source: NoticeSource,
    href: String,
    articleId: String,
): String {
    if (
        href.isNotBlank() &&
        !href.startsWith("javascript:", ignoreCase = true) &&
        !href.startsWith('#')
    ) {
        return runCatching { URI(source.url).resolve(href).toString() }
            .getOrDefault(source.url)
    }
    if (source.id.startsWith("hanbat_")) {
        return source.url.replace("list.do", "view.do") + "?nttId=$articleId"
    }
    return source.url
}

private fun String?.detectCharset(): Charset {
    val charsetName = this?.let { charsetRegex.find(it)?.groupValues?.get(1) }
    return charsetName?.let { runCatching { Charset.forName(it) }.getOrNull() }
        ?: StandardCharsets.UTF_8
}

private fun htmlToText(html: String): String = decodeHtmlEntities(
    html
        .replace(brRegex, " ")
        .replace(tagRegex, " ")
        .replace(whitespaceRegex, " ")
        .trim(),
)

private fun decodeHtmlEntities(value: String): String {
    var decoded = value
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
    decoded = numericEntityRegex.replace(decoded) { match ->
        val valueText = match.groupValues[1]
        val codePoint = if (valueText.startsWith("x", ignoreCase = true)) {
            valueText.drop(1).toIntOrNull(16)
        } else {
            valueText.toIntOrNull()
        }
        codePoint?.let { String(Character.toChars(it)) } ?: match.value
    }
    return decoded.replace(whitespaceRegex, " ").trim()
}

private data class AnchorCandidate(val href: String, val title: String)

private val tableRowRegex = Regex(
    "<tr\\b[^>]*>(.*?)</tr>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val listItemRegex = Regex(
    "<li\\b[^>]*class\\s*=\\s*[\"'][^\"']*dataLi[^\"']*[\"'][^>]*>" +
        "(.*?)</li>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val anchorRegex = Regex(
    "<a\\b[^>]*href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>(.*?)</a>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val articleIdRegexes = listOf(
    "nttId",
    "boardSeq",
    "articleId",
    "contentId",
    "seq",
    "idx",
    "boardId",
).map { parameter ->
    Regex(
        "$parameter(?:=|%3D|['\",\\s(]+)([A-Za-z0-9_-]+)",
        RegexOption.IGNORE_CASE,
    )
}
private val firstNumberCellRegex = Regex(
    "<td\\b[^>]*>\\s*(?:<[^>]+>\\s*)*(\\d{1,12})\\s*(?:</[^>]+>\\s*)*</td>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val javascriptIdRegex = Regex(
    "(?:select|view|detail|read)[^(]*\\([^A-Za-z0-9]*([A-Za-z0-9_-]{4,})",
    RegexOption.IGNORE_CASE,
)
private val dateRegex = Regex("20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2}")
private val charsetRegex = Regex("charset=([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)
private val brRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val tagRegex = Regex("<[^>]+>")
private val whitespaceRegex = Regex("\\s+")
private val numericEntityRegex = Regex("&#(x?[0-9A-Fa-f]+);")

private fun findArticleId(value: String): String? =
    articleIdRegexes.firstNotNullOfOrNull { regex ->
        regex.find(value)?.groupValues?.get(1)?.takeIf(String::isNotBlank)
    }
