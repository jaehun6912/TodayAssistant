package com.example.galaxycalendarprobe.todo

import android.content.Context
import android.util.Base64
import com.example.galaxycalendarprobe.notifications.SummarySchedule

class TodoPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_ENABLED, value).apply()

    var microsoftAccount: String?
        get() = preferences.getString(KEY_ACCOUNT, null)
        set(value) = preferences.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_ACCOUNT) else putString(KEY_ACCOUNT, value)
        }.apply()

    var lists: List<TodoListInfo>
        get() = preferences.getString(KEY_LISTS, "").orEmpty()
            .lineSequence().mapNotNull(::decodeList).toList()
        set(value) = preferences.edit()
            .putString(KEY_LISTS, value.joinToString("\n", transform = ::encodeList)).apply()

    var tasks: List<TodoTaskItem>
        get() = preferences.getString(KEY_TASKS, "").orEmpty()
            .lineSequence().mapNotNull(::decodeTask).toList()
        set(value) = preferences.edit()
            .putString(KEY_TASKS, value.take(MAX_TASKS).joinToString("\n", transform = ::encodeTask))
            .apply()

    var disabledListIds: Set<String>
        get() = preferences.getStringSet(KEY_DISABLED_LIST_IDS, emptySet()).orEmpty()
        set(value) = preferences.edit().putStringSet(KEY_DISABLED_LIST_IDS, value).apply()

    var lastSyncMillis: Long
        get() = preferences.getLong(KEY_LAST_SYNC, 0L)
        set(value) = preferences.edit().putLong(KEY_LAST_SYNC, value).apply()

    var lastError: String?
        get() = preferences.getString(KEY_LAST_ERROR, null)
        set(value) = preferences.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_LAST_ERROR) else putString(KEY_LAST_ERROR, value.take(500))
        }.apply()

    var schedules: List<SummarySchedule>
        get() {
            initializeSchedulesIfNeeded()
            return preferences.getStringSet(KEY_SCHEDULES, emptySet()).orEmpty()
                .mapNotNull(::decodeSchedule)
                .sortedWith(compareBy<SummarySchedule> { it.hour }.thenBy { it.minute })
        }
        set(value) {
            val normalized = value.filter { it.id > 0 && it.hour in 0..23 && it.minute in 0..59 }
                .distinctBy(SummarySchedule::id)
            preferences.edit()
                .putStringSet(KEY_SCHEDULES, normalized.map(::encodeSchedule).toSet())
                .putInt(KEY_NEXT_SCHEDULE_ID, (normalized.maxOfOrNull { it.id } ?: 0) + 1)
                .putBoolean(KEY_SCHEDULES_INITIALIZED, true)
                .apply()
        }

    fun newSchedule(hour: Int, minute: Int): SummarySchedule {
        initializeSchedulesIfNeeded()
        val id = preferences.getInt(KEY_NEXT_SCHEDULE_ID, 1).coerceAtLeast(1)
        preferences.edit().putInt(KEY_NEXT_SCHEDULE_ID, id + 1).apply()
        return SummarySchedule(id, hour, minute)
    }

    var scheduledRequestCodes: Set<Int>
        get() = preferences.getStringSet(KEY_REQUEST_CODES, emptySet()).orEmpty()
            .mapNotNull(String::toIntOrNull).toSet()
        set(value) = preferences.edit().putStringSet(KEY_REQUEST_CODES, value.map(Int::toString).toSet()).apply()

    fun clearDisconnectedAccount() {
        preferences.edit()
            .putBoolean(KEY_ENABLED, false)
            .remove(KEY_ACCOUNT)
            .remove(KEY_LISTS)
            .remove(KEY_TASKS)
            .putLong(KEY_LAST_SYNC, 0L)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    private fun initializeSchedulesIfNeeded() {
        if (preferences.getBoolean(KEY_SCHEDULES_INITIALIZED, false)) return
        val defaults = listOf(SummarySchedule(1, 8, 0), SummarySchedule(2, 19, 0))
        preferences.edit()
            .putStringSet(KEY_SCHEDULES, defaults.map(::encodeSchedule).toSet())
            .putInt(KEY_NEXT_SCHEDULE_ID, 3)
            .putBoolean(KEY_SCHEDULES_INITIALIZED, true)
            .apply()
    }

    companion object {
        private const val FILE_NAME = "todo_summary_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_LISTS = "lists"
        private const val KEY_TASKS = "tasks"
        private const val KEY_DISABLED_LIST_IDS = "disabled_list_ids"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_SCHEDULES = "schedules"
        private const val KEY_SCHEDULES_INITIALIZED = "schedules_initialized"
        private const val KEY_NEXT_SCHEDULE_ID = "next_schedule_id"
        private const val KEY_REQUEST_CODES = "request_codes"
        private const val MAX_TASKS = 1_000
    }
}

private fun encodeSchedule(value: SummarySchedule) = "${value.id}|${value.hour}|${value.minute}"
private fun decodeSchedule(value: String): SummarySchedule? {
    val p = value.split('|')
    if (p.size != 3) return null
    return SummarySchedule(p[0].toIntOrNull() ?: return null, p[1].toIntOrNull() ?: return null, p[2].toIntOrNull() ?: return null)
        .takeIf { it.id > 0 && it.hour in 0..23 && it.minute in 0..59 }
}

private fun enc(value: String): String = Base64.encodeToString(
    value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE,
)
private fun dec(value: String): String = String(
    Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8,
)
private fun encodeList(value: TodoListInfo) = listOf(value.id, value.displayName).joinToString("|") { enc(it) }
private fun decodeList(value: String): TodoListInfo? = runCatching {
    val p = value.split('|').map(::dec)
    TodoListInfo(p[0], p[1])
}.getOrNull()
private fun encodeTask(value: TodoTaskItem) = listOf(
    value.id, value.listId, value.title, value.importance,
    value.dueAtMillis?.toString().orEmpty(), value.reminderAtMillis?.toString().orEmpty(),
).joinToString("|") { enc(it) }
private fun decodeTask(value: String): TodoTaskItem? = runCatching {
    val p = value.split('|').map(::dec)
    TodoTaskItem(p[0], p[1], p[2], p[3], p[4].toLongOrNull(), p[5].toLongOrNull())
}.getOrNull()
