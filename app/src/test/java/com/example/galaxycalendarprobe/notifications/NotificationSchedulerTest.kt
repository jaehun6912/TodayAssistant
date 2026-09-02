package com.example.galaxycalendarprobe.notifications

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSchedulerTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `오늘 설정 시각이 남았으면 오늘로 예약한다`() {
        val now = millis(2026, 8, 29, 7, 30)

        val result = nextSummaryTriggerAtMillis(now, 8, 0, zone)

        assertEquals(millis(2026, 8, 29, 8, 0), result)
    }

    @Test
    fun `설정 시각이 지났거나 같으면 다음 날로 예약한다`() {
        val now = millis(2026, 8, 29, 18, 0)

        val result = nextSummaryTriggerAtMillis(now, 18, 0, zone)

        assertEquals(millis(2026, 8, 30, 18, 0), result)
    }

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}
