package com.example.galaxycalendarprobe.notifications

import android.content.Context

data class SummarySchedule(
    val id: Int,
    val hour: Int,
    val minute: Int,
)

class NotificationPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_ENABLED, value).apply()

    var schedules: List<SummarySchedule>
        get() {
            migrateLegacySchedulesIfNeeded()
            return preferences.getStringSet(KEY_SCHEDULES, emptySet())
                .orEmpty()
                .mapNotNull(::decodeSchedule)
                .sortedWith(compareBy<SummarySchedule> { it.hour }.thenBy { it.minute })
        }
        set(value) {
            val normalized = value
                .filter { it.id > 0 && it.hour in 0..23 && it.minute in 0..59 }
                .distinctBy { it.id }
            val nextId = maxOf(
                preferences.getInt(KEY_NEXT_SCHEDULE_ID, 1),
                (normalized.maxOfOrNull { it.id } ?: 0) + 1,
            )
            preferences.edit()
                .putStringSet(KEY_SCHEDULES, normalized.map(::encodeSchedule).toSet())
                .putInt(KEY_NEXT_SCHEDULE_ID, nextId)
                .putBoolean(KEY_SCHEDULES_INITIALIZED, true)
                .apply()
        }

    fun newSchedule(hour: Int, minute: Int): SummarySchedule {
        require(hour in 0..23 && minute in 0..59)
        migrateLegacySchedulesIfNeeded()
        val id = preferences.getInt(KEY_NEXT_SCHEDULE_ID, 1).coerceAtLeast(1)
        preferences.edit().putInt(KEY_NEXT_SCHEDULE_ID, id + 1).apply()
        return SummarySchedule(id = id, hour = hour, minute = minute)
    }

    var excludedCalendarIds: Set<Long>
        get() = preferences.getStringSet(KEY_EXCLUDED_CALENDAR_IDS, emptySet())
            .orEmpty()
            .mapNotNull(String::toLongOrNull)
            .toSet()
        set(value) = preferences.edit()
            .putStringSet(KEY_EXCLUDED_CALENDAR_IDS, value.map(Long::toString).toSet())
            .apply()

    var scheduledRequestCodes: Set<Int>
        get() = preferences.getStringSet(KEY_ACTIVE_REQUEST_CODES, emptySet())
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .toSet()
        set(value) = preferences.edit()
            .putStringSet(KEY_ACTIVE_REQUEST_CODES, value.map(Int::toString).toSet())
            .apply()

    val legacyEventRequestCodes: Set<Int>
        get() = preferences.getStringSet(KEY_LEGACY_EVENT_REQUEST_CODES, emptySet())
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .toSet()

    fun clearLegacyEventRequestCodes() {
        preferences.edit().remove(KEY_LEGACY_EVENT_REQUEST_CODES).apply()
    }

    private fun migrateLegacySchedulesIfNeeded() {
        if (preferences.getBoolean(KEY_SCHEDULES_INITIALIZED, false)) return

        val hasLegacySettings = listOf(
            KEY_LEGACY_FIRST_HOUR,
            KEY_LEGACY_FIRST_MINUTE,
            KEY_LEGACY_SECOND_ENABLED,
            KEY_LEGACY_SECOND_HOUR,
            KEY_LEGACY_SECOND_MINUTE,
        ).any(preferences::contains)

        val migrated = if (!hasLegacySettings) {
            DEFAULT_FRESH_SCHEDULES
        } else buildList {
            add(
                SummarySchedule(
                    id = 1,
                    hour = preferences.getInt(KEY_LEGACY_FIRST_HOUR, DEFAULT_FIRST_HOUR)
                        .coerceIn(0, 23),
                    minute = preferences.getInt(
                        KEY_LEGACY_FIRST_MINUTE,
                        DEFAULT_FIRST_MINUTE,
                    ).coerceIn(0, 59),
                ),
            )
            if (preferences.getBoolean(KEY_LEGACY_SECOND_ENABLED, false)) {
                add(
                    SummarySchedule(
                        id = 2,
                        hour = preferences.getInt(
                            KEY_LEGACY_SECOND_HOUR,
                            DEFAULT_SECOND_HOUR,
                        ).coerceIn(0, 23),
                        minute = preferences.getInt(
                            KEY_LEGACY_SECOND_MINUTE,
                            DEFAULT_SECOND_MINUTE,
                        ).coerceIn(0, 59),
                    ),
                )
            }
        }

        preferences.edit()
            .putStringSet(KEY_SCHEDULES, migrated.map(::encodeSchedule).toSet())
            .putInt(KEY_NEXT_SCHEDULE_ID, (migrated.maxOfOrNull { it.id } ?: 0) + 1)
            .putBoolean(KEY_SCHEDULES_INITIALIZED, true)
            .apply()
    }

    companion object {
        const val DEFAULT_FIRST_HOUR = 8
        const val DEFAULT_FIRST_MINUTE = 0
        const val DEFAULT_SECOND_HOUR = 18
        const val DEFAULT_SECOND_MINUTE = 0

        val DEFAULT_FRESH_SCHEDULES = listOf(
            SummarySchedule(1, 8, 0),
            SummarySchedule(2, 13, 0),
            SummarySchedule(3, 19, 0),
        )

        private const val FILE_NAME = "calendar_notification_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SCHEDULES = "summary_schedules_v2"
        private const val KEY_SCHEDULES_INITIALIZED = "summary_schedules_v2_initialized"
        private const val KEY_NEXT_SCHEDULE_ID = "next_summary_schedule_id"
        private const val KEY_EXCLUDED_CALENDAR_IDS = "excluded_calendar_ids"
        private const val KEY_ACTIVE_REQUEST_CODES = "active_summary_request_codes"

        private const val KEY_LEGACY_FIRST_HOUR = "first_hour"
        private const val KEY_LEGACY_FIRST_MINUTE = "first_minute"
        private const val KEY_LEGACY_SECOND_ENABLED = "second_enabled"
        private const val KEY_LEGACY_SECOND_HOUR = "second_hour"
        private const val KEY_LEGACY_SECOND_MINUTE = "second_minute"
        private const val KEY_LEGACY_EVENT_REQUEST_CODES = "scheduled_request_codes"
    }
}

private fun encodeSchedule(schedule: SummarySchedule): String =
    "${schedule.id}|${schedule.hour}|${schedule.minute}"

private fun decodeSchedule(value: String): SummarySchedule? {
    val parts = value.split('|')
    if (parts.size != 3) return null
    val id = parts[0].toIntOrNull() ?: return null
    val hour = parts[1].toIntOrNull() ?: return null
    val minute = parts[2].toIntOrNull() ?: return null
    return SummarySchedule(id, hour, minute)
        .takeIf { id > 0 && hour in 0..23 && minute in 0..59 }
}
