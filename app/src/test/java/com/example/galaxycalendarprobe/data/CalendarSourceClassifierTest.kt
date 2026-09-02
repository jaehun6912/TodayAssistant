package com.example.galaxycalendarprobe.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSourceClassifierTest {
    @Test
    fun samsungAccountTypeIsRecognized() {
        assertEquals(
            CalendarSource.SAMSUNG_CANDIDATE,
            CalendarSourceClassifier.classify(
                accountType = "com.osp.app.signin",
                displayName = "내 캘린더",
                ownerAccount = null,
            ),
        )
    }

    @Test
    fun samsungCloudVariantIsRecognized() {
        assertEquals(
            CalendarSource.SAMSUNG_CANDIDATE,
            CalendarSourceClassifier.classify(
                accountType = "com.samsung.android.scloud",
                displayName = null,
                ownerAccount = null,
            ),
        )
    }

    @Test
    fun commonProvidersAreLabeled() {
        assertEquals(
            CalendarSource.GOOGLE,
            CalendarSourceClassifier.classify("com.google", null, null),
        )
        assertEquals(
            CalendarSource.MICROSOFT,
            CalendarSourceClassifier.classify("com.android.exchange", null, null),
        )
        assertEquals(
            CalendarSource.LOCAL,
            CalendarSourceClassifier.classify("LOCAL", null, null),
        )
    }
}
