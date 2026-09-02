package com.example.galaxycalendarprobe.data

data class DeviceCalendar(
    val id: Long,
    val displayName: String?,
    val accountName: String?,
    val accountType: String?,
    val ownerAccount: String?,
    val visible: Boolean?,
    val syncEvents: Boolean?,
) {
    val source: CalendarSource = CalendarSourceClassifier.classify(
        accountType = accountType,
        displayName = displayName,
        ownerAccount = ownerAccount,
    )
}

data class CalendarEventInstance(
    val eventId: Long,
    val calendarId: Long,
    val title: String?,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String?,
)

data class EventWindow(
    val startMillis: Long,
    val endMillis: Long,
    val events: List<CalendarEventInstance>,
)

enum class CalendarSource(val label: String) {
    SAMSUNG_CANDIDATE("Samsung 계정 후보"),
    GOOGLE("Google"),
    MICROSOFT("Microsoft / Exchange"),
    LOCAL("기기 로컬"),
    OTHER("기타 계정"),
}

object CalendarSourceClassifier {
    fun classify(
        accountType: String?,
        displayName: String?,
        ownerAccount: String?,
    ): CalendarSource {
        val searchable = listOf(accountType, displayName, ownerAccount)
            .filterNotNull()
            .joinToString(" ")
            .lowercase()

        return when {
            accountType.equals("com.osp.app.signin", ignoreCase = true) ||
                "samsung" in searchable ||
                "scloud" in searchable -> CalendarSource.SAMSUNG_CANDIDATE

            accountType.equals("com.google", ignoreCase = true) ||
                "google" in searchable -> CalendarSource.GOOGLE

            "exchange" in searchable ||
                "outlook" in searchable ||
                accountType.equals("com.microsoft.office.outlook", ignoreCase = true) ||
                accountType.equals("com.android.exchange", ignoreCase = true) ->
                CalendarSource.MICROSOFT

            accountType.equals("LOCAL", ignoreCase = true) ||
                accountType.isNullOrBlank() -> CalendarSource.LOCAL

            else -> CalendarSource.OTHER
        }
    }
}
