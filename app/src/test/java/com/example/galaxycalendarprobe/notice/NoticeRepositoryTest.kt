package com.example.galaxycalendarprobe.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Test

class NoticeRepositoryTest {
    @Test
    fun `기본 사이트 상태는 잠김이고 저장된 수정값은 기본 사이트로 복원된다`() {
        val original = NoticeSources.builtIns.first()
        val modified = original.copy(
            name = "수정한 학과 공지",
            url = "https://www.hanbat.ac.kr/custom/list.do",
        )

        val restored = decodeBuiltInOverride(encodeSource(modified))

        assertNotNull(restored)
        assertEquals(modified.name, restored?.name)
        assertEquals(modified.url, restored?.url)
        assertTrue(restored?.isBuiltIn == true)
        assertTrue(NoticeSourceState(original).locked)
        assertFalse(
            NoticeSourceState(
                NoticeSource("custom:test", "직접 추가", "example.com", "https://example.com"),
            ).locked,
        )
    }

    @Test
    fun `직접 추가 사이트 정보를 안전하게 저장하고 복원한다`() {
        val source = NoticeSource(
            id = "custom:test-id",
            name = "나의 공지 게시판",
            organization = "example.com",
            url = "https://example.com/notices?category=학교|취업",
        )

        assertEquals(source, decodeSource(encodeSource(source)))
    }

    @Test
    fun `외부 HTTPS 게시판 주소만 허용한다`() {
        assertNull(validateCustomSource("학교 공지", "https://example.com/notice"))
        assertNotNull(validateCustomSource("학교 공지", "http://example.com/notice"))
        assertNotNull(validateCustomSource("학교 공지", "https://localhost/notice"))
        assertNotNull(validateCustomSource("학교 공지", "https://192.168.0.10/notice"))
        assertNotNull(validateCustomSource(" ", "https://example.com/notice"))
    }

    @Test
    fun `한밭대 게시물 ID 제목 날짜 링크를 읽는다`() {
        val source = NoticeSources.all.first { it.id == "hanbat_department" }
        val html = """
            <table><tbody><tr>
              <td>2275</td>
              <td><a href="#view" onclick="javascript: fn_search_detail('B000123AbC'); return false;">
                2026학년도 &amp; 상담 안내
              </a></td>
              <td>정보통신공학과</td><td>2026-08-30</td>
            </tr></tbody></table>
        """.trimIndent()

        val result = parseNoticeHtml(source, html)

        assertEquals(1, result.size)
        assertEquals("B000123AbC", result.single().articleId)
        assertEquals("2026학년도 & 상담 안내", result.single().title)
        assertEquals("2026-08-30", result.single().postedDate)
        assertTrue(result.single().url.contains("nttId=B000123AbC"))
    }

    @Test
    fun `해군 게시판의 일반적인 순번 링크를 읽는다`() {
        val source = NoticeSources.all.first { it.id == "navy_civilian_recruitment" }
        val html = """
            <ul><li class="dataLi noThumb">
              <div class="dataTitle"><a href="/user/boardList.do?command=view&amp;boardId=216&amp;boardSeq=9876">
                일반군무원 채용시험 공고
              </a></div>
              <dl class="date"><dt>작성일</dt><dd>2026.08.30</dd></dl>
            </li></ul>
        """.trimIndent()

        val result = parseNoticeHtml(source, html)

        assertEquals("9876", result.single().articleId)
        assertEquals("2026-08-30", result.single().postedDate)
        assertTrue(result.single().url.startsWith("https://www.navy.mil.kr/"))
    }
}
