package com.example.galaxycalendarprobe.notice

data class NoticeSource(
    val id: String,
    val name: String,
    val organization: String,
    val url: String,
    val isBuiltIn: Boolean = false,
)

data class NoticeArticle(
    val sourceId: String,
    val articleId: String,
    val title: String,
    val postedDate: String?,
    val url: String,
)

data class NoticeSourceState(
    val source: NoticeSource,
    val enabled: Boolean = true,
    val locked: Boolean = source.isBuiltIn,
    val modified: Boolean = false,
    val articles: List<NoticeArticle> = emptyList(),
    val error: String? = null,
)

data class NoticeUiState(
    val monitoringEnabled: Boolean = false,
    val sources: List<NoticeSourceState> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastCheckedMillis: Long? = null,
    val newArticleKeys: Set<String> = emptySet(),
    val newArticleCount: Int = 0,
    val notificationError: String? = null,
)

data class NoticeCheckResult(
    val sources: List<NoticeSourceState>,
    val newArticles: List<NoticeArticle>,
    val checkedAtMillis: Long,
)

object NoticeSources {
    val builtIns = listOf(
        NoticeSource(
            id = "hanbat_department",
            name = "정보통신공학과 공지사항",
            organization = "국립한밭대학교",
            url = "https://www.hanbat.ac.kr/prog/bbsArticle/" +
                "BBSMSTR_000000000323/list.do",
            isBuiltIn = true,
        ),
        NoticeSource(
            id = "hanbat_university",
            name = "학교 공지사항",
            organization = "국립한밭대학교",
            url = "https://www.hanbat.ac.kr/prog/bbsArticle/" +
                "BBSMSTR_000000000050/list.do",
            isBuiltIn = true,
        ),
        NoticeSource(
            id = "navy_notice",
            name = "해군 공지사항",
            organization = "대한민국 해군",
            url = "https://www.navy.mil.kr/user/boardList.do?" +
                "handle=13010&siteId=navy&id=navy_020100000000",
            isBuiltIn = true,
        ),
        NoticeSource(
            id = "navy_civilian_recruitment",
            name = "군무원 채용공지",
            organization = "대한민국 해군",
            url = "https://www.navy.mil.kr/user/boardList.do?" +
                "handle=216&siteId=navy&id=navy_050701020000",
            isBuiltIn = true,
        ),
        NoticeSource(
            id = "navy_temporary_recruitment",
            name = "한시임기제 군무원채용",
            organization = "대한민국 해군",
            url = "https://www.navy.mil.kr/user/boardList.do?" +
                "handle=244941&siteId=navy&id=navy_050707000000",
            isBuiltIn = true,
        ),
    )

    // Kept for parser tests and callers that only need the bundled definitions.
    val all: List<NoticeSource> = builtIns
}

fun NoticeArticle.key(): String = "$sourceId::$articleId"
