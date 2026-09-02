package com.example.galaxycalendarprobe.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class CalendarRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    fun getCalendars(): List<DeviceCalendar> {
        requireReadPermission()

        val requestedProjection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS,
        )

        val calendars = try {
            queryCalendars(requestedProjection)
        } catch (_: IllegalArgumentException) {
            // Some vendor providers reject columns they do not expose. A null
            // projection asks that provider for every column it actually offers.
            queryCalendars(null)
        }

        return calendars.sortedWith(
            compareBy<DeviceCalendar> {
                it.displayName?.lowercase(Locale.ROOT).orEmpty()
            }.thenBy { it.id },
        )
    }

    fun getInstances(
        calendarId: Long? = null,
        startAtBeginningOfToday: Boolean = false,
    ): EventWindow {
        requireReadPermission()

        val nowMillis = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val startMillis = if (startAtBeginningOfToday) {
            Instant.ofEpochMilli(nowMillis)
                .atZone(zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        } else {
            nowMillis
        }
        val endMillis = if (startAtBeginningOfToday) {
            Instant.ofEpochMilli(startMillis)
                .atZone(zone)
                .toLocalDate()
                .plusDays(30)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        } else {
            startMillis + Duration.ofDays(30).toMillis()
        }
        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, startMillis)
        ContentUris.appendId(uriBuilder, endMillis)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION,
        )

        val selection = calendarId?.let { "${CalendarContract.Events.CALENDAR_ID} = ?" }
        val selectionArgs = calendarId?.let { arrayOf(it.toString()) }

        val events = resolver.query(
            uriBuilder.build(),
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        CalendarEventInstance(
                            eventId = cursor.requiredLong(CalendarContract.Instances.EVENT_ID),
                            calendarId = cursor.requiredLong(
                                CalendarContract.Events.CALENDAR_ID,
                            ),
                            title = cursor.nullableString(CalendarContract.Events.TITLE),
                            beginMillis = cursor.requiredLong(CalendarContract.Instances.BEGIN),
                            endMillis = cursor.requiredLong(CalendarContract.Instances.END),
                            allDay = cursor.nullableInt(CalendarContract.Events.ALL_DAY) == 1,
                            location = cursor.nullableString(CalendarContract.Events.EVENT_LOCATION),
                        ),
                    )
                }
            }
        }.orEmpty()

        return EventWindow(
            startMillis = startMillis,
            endMillis = endMillis,
            events = events,
        )
    }

    private fun queryCalendars(projection: Array<String>?): List<DeviceCalendar> {
        return resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DeviceCalendar(
                            id = cursor.requiredLong(CalendarContract.Calendars._ID),
                            displayName = cursor.nullableString(
                                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                            ),
                            accountName = cursor.nullableString(
                                CalendarContract.Calendars.ACCOUNT_NAME,
                            ),
                            accountType = cursor.nullableString(
                                CalendarContract.Calendars.ACCOUNT_TYPE,
                            ),
                            ownerAccount = cursor.nullableString(
                                CalendarContract.Calendars.OWNER_ACCOUNT,
                            ),
                            visible = cursor.nullableInt(
                                CalendarContract.Calendars.VISIBLE,
                            )?.let { it != 0 },
                            syncEvents = cursor.nullableInt(
                                CalendarContract.Calendars.SYNC_EVENTS,
                            )?.let { it != 0 },
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private fun requireReadPermission() {
        check(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED,
        ) { "캘린더 읽기 권한이 필요합니다." }
    }
}

private fun Cursor.requiredLong(columnName: String): Long {
    val index = getColumnIndex(columnName)
    check(index >= 0) { "필수 열을 찾을 수 없습니다: $columnName" }
    return getLong(index)
}

private fun Cursor.nullableString(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index < 0 || isNull(index)) null else getString(index)
}

private fun Cursor.nullableInt(columnName: String): Int? {
    val index = getColumnIndex(columnName)
    return if (index < 0 || isNull(index)) null else getInt(index)
}
