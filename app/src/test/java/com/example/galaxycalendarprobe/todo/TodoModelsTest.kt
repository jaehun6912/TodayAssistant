package com.example.galaxycalendarprobe.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoModelsTest {
    @Test
    fun effectiveTimeUsesEarlierOfDueAndReminder() {
        val task = TodoTaskItem(
            id = "task",
            listId = "list",
            title = "시험",
            importance = "normal",
            dueAtMillis = 2_000L,
            reminderAtMillis = 1_000L,
        )

        assertEquals(1_000L, task.effectiveAtMillis)
    }

    @Test
    fun effectiveTimeSupportsSingleOrMissingDate() {
        val dueOnly = TodoTaskItem("1", "list", "기한", "normal", 3_000L, null)
        val noDate = TodoTaskItem("2", "list", "날짜 없음", "normal", null, null)

        assertEquals(3_000L, dueOnly.effectiveAtMillis)
        assertNull(noDate.effectiveAtMillis)
    }
}
