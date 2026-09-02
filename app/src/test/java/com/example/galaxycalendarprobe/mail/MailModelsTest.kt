package com.example.galaxycalendarprobe.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MailModelsTest {
    @Test
    fun parsesSenderSubjectAndPreview() {
        val result = parseMailNotificationText(
            title = "홍길동",
            text = "서류 제출 요청",
            bigText = "서류 제출 요청  내일까지 보내 주세요.",
            textLines = emptyList(),
        )

        assertEquals("홍길동", result?.sender)
        assertEquals("서류 제출 요청", result?.subject)
        assertEquals("서류 제출 요청 내일까지 보내 주세요.", result?.preview)
    }

    @Test
    fun ignoresNotificationWithoutUsefulContent() {
        assertNull(
            parseMailNotificationText(
                title = "Gmail",
                text = null,
                bigText = null,
                textLines = emptyList(),
            ),
        )
    }
}

