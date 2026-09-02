package com.example.galaxycalendarprobe.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.galaxycalendarprobe.CalendarUiState
import com.example.galaxycalendarprobe.R
import com.example.galaxycalendarprobe.data.CalendarEventInstance
import com.example.galaxycalendarprobe.data.CalendarSource
import com.example.galaxycalendarprobe.data.DeviceCalendar
import com.example.galaxycalendarprobe.mail.CapturedMail
import com.example.galaxycalendarprobe.mail.MailCollectionMethod
import com.example.galaxycalendarprobe.mail.MailSources
import com.example.galaxycalendarprobe.mail.MailUiState
import com.example.galaxycalendarprobe.notifications.NotificationPreferences
import com.example.galaxycalendarprobe.notifications.SummarySchedule
import com.example.galaxycalendarprobe.notice.NoticeArticle
import com.example.galaxycalendarprobe.notice.NoticeSource
import com.example.galaxycalendarprobe.notice.NoticeSourceState
import com.example.galaxycalendarprobe.notice.NoticeUiState
import com.example.galaxycalendarprobe.notice.key
import com.example.galaxycalendarprobe.todo.TodoTaskItem
import com.example.galaxycalendarprobe.todo.TodoUiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class AppSection {
    TODAY,
    MAIL,
    NOTICES,
    TODOS,
    DIAGNOSTICS,
    SETTINGS,
    CALENDAR_SETTINGS,
    MAIL_SETTINGS,
    NOTICE_SETTINGS,
    TODO_SETTINGS,
}

private val settingsSections = setOf(
    AppSection.SETTINGS,
    AppSection.CALENDAR_SETTINGS,
    AppSection.MAIL_SETTINGS,
    AppSection.NOTICE_SETTINGS,
    AppSection.TODO_SETTINGS,
    AppSection.DIAGNOSTICS,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarProbeApp(
    permissionGranted: Boolean,
    permissionRequested: Boolean,
    shouldOpenSettings: Boolean,
    notificationPermissionGranted: Boolean,
    notificationPermissionRequested: Boolean,
    shouldOpenNotificationSettings: Boolean,
    uiState: CalendarUiState,
    noticeUiState: NoticeUiState,
    mailUiState: MailUiState,
    todoUiState: TodoUiState,
    initialSectionName: String,
    sectionRequestVersion: Int,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnableNotifications: () -> Unit,
    onDisableNotifications: () -> Unit,
    onAddSummarySchedule: (Int, Int) -> Unit,
    onUpdateSummarySchedule: (Int, Int, Int) -> Unit,
    onDeleteSummarySchedule: (Int) -> Unit,
    onNotificationCalendarEnabledChanged: (Long, Boolean) -> Unit,
    onSelectAllNotificationCalendars: () -> Unit,
    onClearAllNotificationCalendars: () -> Unit,
    onRescheduleNotifications: () -> Unit,
    onEnableNoticeMonitoring: () -> Unit,
    onDisableNoticeMonitoring: () -> Unit,
    onNoticeSourceEnabledChanged: (String, Boolean) -> Unit,
    onAddNoticeSource: (String, String) -> String?,
    onUpdateNoticeSource: (String, String, String) -> String?,
    onDeleteNoticeSource: (String) -> Unit,
    onNoticeSourceLockedChanged: (String, Boolean) -> Unit,
    onRestoreNoticeSource: (String) -> Unit,
    onRefreshNotices: () -> Unit,
    onOpenNotice: (String) -> Unit,
    onEnableMailSummaries: () -> Unit,
    onDisableMailSummaries: () -> Unit,
    onOpenMailNotificationAccess: () -> Unit,
    onConnectOutlook: () -> Unit,
    onDisconnectOutlook: () -> Unit,
    onSyncOutlook: () -> Unit,
    onConnectGmail: () -> Unit,
    onDisconnectGmail: () -> Unit,
    onSyncGmail: () -> Unit,
    onAddMailSchedule: (Int, Int) -> Unit,
    onUpdateMailSchedule: (Int, Int, Int) -> Unit,
    onDeleteMailSchedule: (Int) -> Unit,
    onMailSourceEnabledChanged: (String, Boolean) -> Unit,
    onDeliverPendingMail: () -> Unit,
    onRefreshMail: () -> Unit,
    onEnableTodo: () -> Unit,
    onDisableTodo: () -> Unit,
    onAuthorizeTodo: () -> Unit,
    onSyncTodo: () -> Unit,
    onAddTodoSchedule: (Int, Int) -> Unit,
    onUpdateTodoSchedule: (Int, Int, Int) -> Unit,
    onDeleteTodoSchedule: (Int) -> Unit,
    onTodoListEnabledChanged: (String, Boolean) -> Unit,
    onNotifyTodoNow: () -> Unit,
    onCalendarSelected: (DeviceCalendar) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val selectedCalendar = uiState.selectedCalendar
    var sectionName by rememberSaveable {
        mutableStateOf(initialSectionName.toAppSection().name)
    }
    val section = sectionName.toAppSection()

    LaunchedEffect(initialSectionName, sectionRequestVersion) {
        val requested = initialSectionName.toAppSection()
        if (selectedCalendar != null) onBack()
        sectionName = requested.name
    }

    BackHandler(
        enabled = selectedCalendar != null || section != AppSection.TODAY,
    ) {
        if (selectedCalendar != null) onBack()
        else if (section in settingsSections && section != AppSection.SETTINGS) {
            sectionName = AppSection.SETTINGS.name
        }
        else sectionName = AppSection.TODAY.name
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            selectedCalendar != null ->
                                selectedCalendar.displayName ?: "이름 없는 캘린더"
                            section == AppSection.MAIL -> "메일 브리핑"
                            section == AppSection.NOTICES -> "공지 모니터링"
                            section == AppSection.TODOS -> "할 일 브리핑"
                            section == AppSection.DIAGNOSTICS -> "캘린더 진단"
                            section == AppSection.SETTINGS -> "설정"
                            section == AppSection.CALENDAR_SETTINGS -> "일정 설정"
                            section == AppSection.MAIL_SETTINGS -> "메일 설정"
                            section == AppSection.NOTICE_SETTINGS -> "공지 설정"
                            section == AppSection.TODO_SETTINGS -> "할 일 설정"
                            else -> "오늘의 비서"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (selectedCalendar != null) {
                        TextButton(onClick = onBack) { Text("‹ 진단") }
                    } else if (section in settingsSections && section != AppSection.SETTINGS) {
                        TextButton(
                            onClick = { sectionName = AppSection.SETTINGS.name },
                        ) { Text("‹ 설정") }
                    } else if (section != AppSection.TODAY) {
                        TextButton(
                            onClick = { sectionName = AppSection.TODAY.name },
                        ) { Text("‹ 오늘") }
                    }
                },
                actions = {
                    if (permissionGranted && section != AppSection.SETTINGS) {
                        TextButton(
                            onClick = when (section) {
                                AppSection.MAIL, AppSection.MAIL_SETTINGS -> onRefreshMail
                                AppSection.NOTICES, AppSection.NOTICE_SETTINGS -> onRefreshNotices
                                AppSection.TODOS, AppSection.TODO_SETTINGS -> onSyncTodo
                                AppSection.CALENDAR_SETTINGS,
                                AppSection.DIAGNOSTICS,
                                AppSection.TODAY -> onRefresh
                            },
                        ) { Text("새로고침") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (permissionGranted && selectedCalendar == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = section == AppSection.TODAY,
                        onClick = { sectionName = AppSection.TODAY.name },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_nav_today),
                                contentDescription = null,
                            )
                        },
                        label = { Text("오늘") },
                    )
                    NavigationBarItem(
                        selected = section == AppSection.MAIL,
                        onClick = { sectionName = AppSection.MAIL.name },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_nav_mail),
                                contentDescription = null,
                            )
                        },
                        label = { Text("메일") },
                    )
                    NavigationBarItem(
                        selected = section == AppSection.NOTICES,
                        onClick = { sectionName = AppSection.NOTICES.name },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_nav_notice),
                                contentDescription = null,
                            )
                        },
                        label = { Text("공지") },
                    )
                    NavigationBarItem(
                        selected = section == AppSection.TODOS,
                        onClick = { sectionName = AppSection.TODOS.name },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_nav_todo),
                                contentDescription = null,
                            )
                        },
                        label = { Text("할 일") },
                    )
                    NavigationBarItem(
                        selected = section in settingsSections,
                        onClick = { sectionName = AppSection.SETTINGS.name },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_nav_settings),
                                contentDescription = null,
                            )
                        },
                        label = { Text("설정") },
                    )
                }
            }
        },
    ) { innerPadding ->
        when {
            !permissionGranted -> PermissionScreen(
                modifier = Modifier.padding(innerPadding),
                permissionRequested = permissionRequested,
                shouldOpenSettings = shouldOpenSettings,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
            )

            selectedCalendar != null -> EventListScreen(
                modifier = Modifier.padding(innerPadding),
                calendar = selectedCalendar,
                uiState = uiState,
            )

            section == AppSection.MAIL -> MailScreen(
                modifier = Modifier.padding(innerPadding),
                uiState = mailUiState,
                permissionGranted = notificationPermissionGranted,
                permissionRequested = notificationPermissionRequested,
                shouldOpenSettings = shouldOpenNotificationSettings,
                onEnable = onEnableMailSummaries,
                onDisable = onDisableMailSummaries,
                onOpenAppSettings = onOpenSettings,
                onOpenNotificationAccess = onOpenMailNotificationAccess,
                onConnectOutlook = onConnectOutlook,
                onDisconnectOutlook = onDisconnectOutlook,
                onSyncOutlook = onSyncOutlook,
                onConnectGmail = onConnectGmail,
                onDisconnectGmail = onDisconnectGmail,
                onSyncGmail = onSyncGmail,
                onAddSchedule = onAddMailSchedule,
                onUpdateSchedule = onUpdateMailSchedule,
                onDeleteSchedule = onDeleteMailSchedule,
                onSourceEnabledChanged = onMailSourceEnabledChanged,
                onDeliverPending = onDeliverPendingMail,
                onOpenFeatureSettings = { sectionName = AppSection.MAIL_SETTINGS.name },
                settingsOnly = false,
            )

            section == AppSection.NOTICES -> NoticeScreen(
                modifier = Modifier.padding(innerPadding),
                uiState = noticeUiState,
                permissionGranted = notificationPermissionGranted,
                permissionRequested = notificationPermissionRequested,
                shouldOpenSettings = shouldOpenNotificationSettings,
                onEnableMonitoring = onEnableNoticeMonitoring,
                onDisableMonitoring = onDisableNoticeMonitoring,
                onOpenSettings = onOpenSettings,
                onSourceEnabledChanged = onNoticeSourceEnabledChanged,
                onAddSource = onAddNoticeSource,
                onUpdateSource = onUpdateNoticeSource,
                onDeleteSource = onDeleteNoticeSource,
                onSourceLockedChanged = onNoticeSourceLockedChanged,
                onRestoreSource = onRestoreNoticeSource,
                onRefresh = onRefreshNotices,
                onOpenArticle = onOpenNotice,
                onOpenFeatureSettings = { sectionName = AppSection.NOTICE_SETTINGS.name },
                settingsOnly = false,
            )

            section == AppSection.DIAGNOSTICS -> CalendarListScreen(
                modifier = Modifier.padding(innerPadding),
                uiState = uiState,
                onCalendarSelected = onCalendarSelected,
                onRefresh = onRefresh,
            )

            section == AppSection.TODOS -> TodoScreen(
                modifier = Modifier.padding(innerPadding),
                uiState = todoUiState,
                permissionGranted = notificationPermissionGranted,
                onEnable = onEnableTodo,
                onDisable = onDisableTodo,
                onOpenAppSettings = onOpenSettings,
                onAuthorize = onAuthorizeTodo,
                onSync = onSyncTodo,
                onAddSchedule = onAddTodoSchedule,
                onUpdateSchedule = onUpdateTodoSchedule,
                onDeleteSchedule = onDeleteTodoSchedule,
                onListEnabledChanged = onTodoListEnabledChanged,
                onNotifyNow = onNotifyTodoNow,
                onOpenFeatureSettings = { sectionName = AppSection.TODO_SETTINGS.name },
                settingsOnly = false,
            )

            section == AppSection.CALENDAR_SETTINGS -> NotificationSettingsScreen(
                modifier = Modifier.padding(innerPadding),
                uiState = uiState,
                permissionGranted = notificationPermissionGranted,
                permissionRequested = notificationPermissionRequested,
                shouldOpenSettings = shouldOpenNotificationSettings,
                onEnableNotifications = onEnableNotifications,
                onDisableNotifications = onDisableNotifications,
                onOpenSettings = onOpenSettings,
                onAddSchedule = onAddSummarySchedule,
                onUpdateSchedule = onUpdateSummarySchedule,
                onDeleteSchedule = onDeleteSummarySchedule,
                onCalendarEnabledChanged = onNotificationCalendarEnabledChanged,
                onSelectAllCalendars = onSelectAllNotificationCalendars,
                onClearAllCalendars = onClearAllNotificationCalendars,
                onReschedule = onRescheduleNotifications,
            )

            section == AppSection.MAIL_SETTINGS -> MailScreen(
                modifier = Modifier.padding(innerPadding),
                uiState = mailUiState,
                permissionGranted = notificationPermissionGranted,
                permissionRequested = notificationPermissionRequested,
                shouldOpenSettings = shouldOpenNotificationSettings,
                onEnable = onEnableMailSummaries,
                onDisable = onDisableMailSummaries,
                onOpenAppSettings = onOpenSettings,
                onOpenNotificationAccess = onOpenMailNotificationAccess,
                onConnectOutlook = onConnectOutlook,
                onDisconnectOutlook = onDisconnectOutlook,
                onSyncOutlook = onSyncOutlook,
                onConnectGmail = onConnectGmail,
                onDisconnectGmail = onDisconnectGmail,
                onSyncGmail = onSyncGmail,
                onAddSchedule = onAddMailSchedule,
                onUpdateSchedule = onUpdateMailSchedule,
                onDeleteSchedule = onDeleteMailSchedule,
                onSourceEnabledChanged = onMailSourceEnabledChanged,
                onDeliverPending = onDeliverPendingMail,
                onOpenFeatureSettings = {},
                settingsOnly = true,
            )

            section == AppSection.NOTICE_SETTINGS -> NoticeScreen(
                modifier = Modifier.padding(innerPadding),
                uiState = noticeUiState,
                permissionGranted = notificationPermissionGranted,
                permissionRequested = notificationPermissionRequested,
                shouldOpenSettings = shouldOpenNotificationSettings,
                onEnableMonitoring = onEnableNoticeMonitoring,
                onDisableMonitoring = onDisableNoticeMonitoring,
                onOpenSettings = onOpenSettings,
                onSourceEnabledChanged = onNoticeSourceEnabledChanged,
                onAddSource = onAddNoticeSource,
                onUpdateSource = onUpdateNoticeSource,
                onDeleteSource = onDeleteNoticeSource,
                onSourceLockedChanged = onNoticeSourceLockedChanged,
                onRestoreSource = onRestoreNoticeSource,
                onRefresh = onRefreshNotices,
                onOpenArticle = onOpenNotice,
                onOpenFeatureSettings = {},
                settingsOnly = true,
            )

            section == AppSection.TODO_SETTINGS -> TodoScreen(
                modifier = Modifier.padding(innerPadding),
                uiState = todoUiState,
                permissionGranted = notificationPermissionGranted,
                onEnable = onEnableTodo,
                onDisable = onDisableTodo,
                onOpenAppSettings = onOpenSettings,
                onAuthorize = onAuthorizeTodo,
                onSync = onSyncTodo,
                onAddSchedule = onAddTodoSchedule,
                onUpdateSchedule = onUpdateTodoSchedule,
                onDeleteSchedule = onDeleteTodoSchedule,
                onListEnabledChanged = onTodoListEnabledChanged,
                onNotifyNow = onNotifyTodoNow,
                onOpenFeatureSettings = {},
                settingsOnly = true,
            )

            section == AppSection.SETTINGS -> SettingsHomeScreen(
                modifier = Modifier.padding(innerPadding),
                uiState = uiState,
                mailUiState = mailUiState,
                noticeUiState = noticeUiState,
                todoUiState = todoUiState,
                onOpenCalendarSettings = { sectionName = AppSection.CALENDAR_SETTINGS.name },
                onOpenMailSettings = { sectionName = AppSection.MAIL_SETTINGS.name },
                onOpenNoticeSettings = { sectionName = AppSection.NOTICE_SETTINGS.name },
                onOpenTodoSettings = { sectionName = AppSection.TODO_SETTINGS.name },
                onOpenDiagnostics = { sectionName = AppSection.DIAGNOSTICS.name },
            )

            else -> TodayScreen(
                modifier = Modifier.padding(innerPadding),
                uiState = uiState,
                mailUiState = mailUiState,
                noticeUiState = noticeUiState,
                todoUiState = todoUiState,
                onOpenDiagnostics = { sectionName = AppSection.DIAGNOSTICS.name },
                onOpenCalendarTargets = {
                    sectionName = AppSection.CALENDAR_SETTINGS.name
                },
                onOpenMail = { sectionName = AppSection.MAIL.name },
                onOpenNotices = { sectionName = AppSection.NOTICES.name },
                onOpenTodos = { sectionName = AppSection.TODOS.name },
                onRefresh = onRefresh,
            )
        }
    }
}

private fun String.toAppSection(): AppSection =
    runCatching { AppSection.valueOf(this) }.getOrDefault(AppSection.TODAY)

@Composable
private fun SettingsHomeScreen(
    modifier: Modifier,
    uiState: CalendarUiState,
    mailUiState: MailUiState,
    noticeUiState: NoticeUiState,
    todoUiState: TodoUiState,
    onOpenCalendarSettings: () -> Unit,
    onOpenMailSettings: () -> Unit,
    onOpenNoticeSettings: () -> Unit,
    onOpenTodoSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "비서 기능 설정",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "기능별 연결 상태, 알림 시각과 대상을 한곳에서 관리하세요.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            SettingsEntryCard(
                iconRes = R.drawable.ic_nav_today,
                title = "일정",
                detail = "요약 ${uiState.summarySchedules.size}회 · " +
                    "캘린더 ${uiState.calendars.count { it.id !in uiState.excludedNotificationCalendarIds }}개 선택",
                status = if (uiState.notificationsEnabled) "켜짐" else "꺼짐",
                active = uiState.notificationsEnabled,
                onClick = onOpenCalendarSettings,
            )
        }
        item {
            SettingsEntryCard(
                iconRes = R.drawable.ic_nav_mail,
                title = "메일",
                detail = "요약 시각 ${mailUiState.schedules.size}개",
                status = if (mailUiState.enabled) "켜짐" else "꺼짐",
                active = mailUiState.enabled,
                onClick = onOpenMailSettings,
            )
        }
        item {
            SettingsEntryCard(
                iconRes = R.drawable.ic_nav_notice,
                title = "공지",
                detail = "사이트 ${noticeUiState.sources.count { it.enabled }}개 선택",
                status = if (noticeUiState.monitoringEnabled) "켜짐" else "꺼짐",
                active = noticeUiState.monitoringEnabled,
                onClick = onOpenNoticeSettings,
            )
        }
        item {
            SettingsEntryCard(
                iconRes = R.drawable.ic_nav_todo,
                title = "할 일",
                detail = "요약 시각 ${todoUiState.schedules.size}개",
                status = if (todoUiState.enabled) "켜짐" else "꺼짐",
                active = todoUiState.enabled,
                onClick = onOpenTodoSettings,
            )
        }
        item {
            SettingsEntryCard(
                iconRes = R.drawable.ic_calendar_probe,
                title = "캘린더 연결 진단",
                detail = "기기에 공개된 캘린더 원본 정보와 30일 일정을 확인합니다.",
                status = null,
                active = false,
                onClick = onOpenDiagnostics,
            )
        }
        item {
            Text(
                "이 앱은 일정·메일·할 일을 읽기 전용으로 사용합니다. 설정 변경은 " +
                    "요약 대상과 알림 예약에만 적용됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp, 4.dp, 4.dp, 20.dp),
            )
        }
    }
}

@Composable
private fun SettingsEntryCard(
    iconRes: Int,
    title: String,
    detail: String,
    status: String?,
    active: Boolean,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    status?.let { StatusPill(text = it, active = active) }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PermissionScreen(
    modifier: Modifier,
    permissionRequested: Boolean,
    shouldOpenSettings: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_nav_today),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "캘린더 읽기 권한이 필요합니다",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "기기에 동기화된 Samsung·Google·Outlook·로컬 캘린더 일정을 " +
                        "한 화면에 모읍니다. 이 앱은 일정 내용을 읽기만 하며 수정하거나 삭제하지 않습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (permissionRequested) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (shouldOpenSettings) {
                            "권한 요청 창이 열리지 않으면 앱 설정에서 ‘캘린더’를 허용해 주세요."
                        } else {
                            "권한이 거부되었습니다. 테스트하려면 다시 허용해 주세요."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(20.dp))
                if (shouldOpenSettings) {
                    Button(onClick = onOpenSettings) { Text("앱 설정 열기") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRequestPermission) { Text("권한 다시 요청") }
                } else {
                    Button(onClick = onRequestPermission) {
                        Text(if (permissionRequested) "권한 다시 요청" else "캘린더 권한 허용")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "요청 권한: READ_CALENDAR · 쓰기 권한 없음",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TodayScreen(
    modifier: Modifier,
    uiState: CalendarUiState,
    mailUiState: MailUiState,
    noticeUiState: NoticeUiState,
    todoUiState: TodoUiState,
    onOpenDiagnostics: () -> Unit,
    onOpenCalendarTargets: () -> Unit,
    onOpenMail: () -> Unit,
    onOpenNotices: () -> Unit,
    onOpenTodos: () -> Unit,
    onRefresh: () -> Unit,
) {
    val today = LocalDate.now()
    val nowMillis = System.currentTimeMillis()
    val calendarsById = uiState.calendars.associateBy { it.id }
    val selectedCalendarIds = uiState.calendars
        .filterNot { it.id in uiState.excludedNotificationCalendarIds }
        .mapTo(mutableSetOf()) { it.id }
    val filteredEvents = uiState.overviewEvents.filter {
        it.calendarId in selectedCalendarIds
    }
    val todayEvents = filteredEvents.filter { it.occursOn(today) }
    val futureGroups = filteredEvents
        .filterNot { it.occursOn(today) }
        .groupBy { it.startDate() }
        .toSortedMap()
    val nextEvent = filteredEvents.firstOrNull { it.endMillis >= nowMillis }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TodayHeroCard(
                today = today,
                todayCount = todayEvents.size,
                selectedCalendarCount = selectedCalendarIds.size,
                totalCalendarCount = uiState.calendars.size,
                nextEvent = nextEvent,
                nextCalendar = nextEvent?.let { calendarsById[it.calendarId] },
                onOpenDiagnostics = onOpenDiagnostics,
                onOpenCalendarTargets = onOpenCalendarTargets,
            )
        }

        item {
            AssistantStatusCard(
                mailUiState = mailUiState,
                noticeUiState = noticeUiState,
                todoUiState = todoUiState,
                onOpenMail = onOpenMail,
                onOpenNotices = onOpenNotices,
                onOpenTodos = onOpenTodos,
            )
        }

        uiState.calendarError?.let { error ->
            item { ErrorCard(message = error, onRetry = onRefresh) }
        }
        uiState.overviewError?.let { error ->
            item { ErrorCard(message = error, onRetry = onRefresh) }
        }

        if (uiState.isLoadingCalendars || uiState.isLoadingOverview) {
            item { LoadingRow("오늘과 향후 일정을 모으는 중…") }
        } else if (
            filteredEvents.isEmpty() &&
            uiState.calendarError == null &&
            uiState.overviewError == null
        ) {
            item {
                EmptyCard(
                    if (selectedCalendarIds.isEmpty()) {
                        "메인 화면과 알림에 사용할 캘린더가 선택되지 않았습니다. " +
                            "설정의 일정 항목에서 한 개 이상 선택해 주세요."
                    } else {
                        "선택한 캘린더에는 오늘부터 30일 안에 표시할 일정이 없습니다. " +
                            "설정의 캘린더 연결 진단에서 상태를 확인할 수 있습니다."
                    },
                )
            }
        }

        if (todayEvents.isNotEmpty()) {
            item { ScheduleSectionTitle("오늘 일정", todayEvents.size) }
            items(
                items = todayEvents,
                key = { "today-${it.calendarId}-${it.eventId}-${it.beginMillis}" },
            ) { event ->
                OverviewEventCard(
                    event = event,
                    calendar = calendarsById[event.calendarId],
                )
            }
        }

        if (futureGroups.isNotEmpty()) {
            item { ScheduleSectionTitle("다가오는 일정", futureGroups.values.sumOf { it.size }) }
            futureGroups.forEach { (date, eventsOnDate) ->
                item(key = "date-$date") {
                    Text(
                        date.format(dayHeaderFormatter),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
                items(
                    items = eventsOnDate,
                    key = { "future-${it.calendarId}-${it.eventId}-${it.beginMillis}" },
                ) { event ->
                    OverviewEventCard(
                        event = event,
                        calendar = calendarsById[event.calendarId],
                    )
                }
            }
        }

        item {
            Text(
                "설정에서 선택한 캘린더의 일정만 표시합니다. 연결 진단은 확인을 위해 " +
                    "전체 캘린더를 유지합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp, 8.dp, 4.dp, 20.dp),
            )
        }
    }
}

@Composable
private fun AssistantStatusCard(
    mailUiState: MailUiState,
    noticeUiState: NoticeUiState,
    todoUiState: TodoUiState,
    onOpenMail: () -> Unit,
    onOpenNotices: () -> Unit,
    onOpenTodos: () -> Unit,
) {
    val endOfToday = LocalDate.now().plusDays(1)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val dueTodoCount = todoUiState.tasks.count { task ->
        task.listId !in todoUiState.disabledListIds &&
            task.effectiveAtMillis?.let { it < endOfToday } == true
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "비서 상태",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "각 항목을 눌러 최신 내역을 확인하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistantStatusButton(
                iconRes = R.drawable.ic_nav_mail,
                title = "메일",
                status = if (mailUiState.enabled) {
                    "요약 대기 ${mailUiState.pendingMails.size}개"
                } else "요약 꺼짐",
                active = mailUiState.enabled,
                onClick = onOpenMail,
            )
            AssistantStatusButton(
                iconRes = R.drawable.ic_nav_notice,
                title = "공지",
                status = if (noticeUiState.monitoringEnabled) {
                    if (noticeUiState.newArticleCount > 0) {
                        "신규 ${noticeUiState.newArticleCount}개"
                    } else "자동 확인 중"
                } else "모니터링 꺼짐",
                active = noticeUiState.monitoringEnabled,
                onClick = onOpenNotices,
            )
            AssistantStatusButton(
                iconRes = R.drawable.ic_nav_todo,
                title = "할 일",
                status = if (todoUiState.enabled) "오늘까지 ${dueTodoCount}개" else "요약 꺼짐",
                active = todoUiState.enabled,
                onClick = onOpenTodos,
            )
        }
    }
}

@Composable
private fun AssistantStatusButton(
    iconRes: Int,
    title: String,
    status: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusPill(text = status, active = active)
            Spacer(Modifier.width(8.dp))
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TodayHeroCard(
    today: LocalDate,
    todayCount: Int,
    selectedCalendarCount: Int,
    totalCalendarCount: Int,
    nextEvent: CalendarEventInstance?,
    nextCalendar: DeviceCalendar?,
    onOpenDiagnostics: () -> Unit,
    onOpenCalendarTargets: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                today.format(todayTitleFormatter),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (selectedCalendarCount == 0) "표시 대상 캘린더가 없습니다"
                else if (todayCount == 0) "오늘은 등록된 일정이 없습니다"
                else "오늘 일정 ${todayCount}개",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "표시 대상 ${selectedCalendarCount}/${totalCalendarCount}개 캘린더",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            nextEvent?.let { event ->
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Text(
                    "다음 일정",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    event.title ?: "(제목 없음)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "${formatEventTime(event)} · ${nextCalendar?.displayName ?: "출처 미확인"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpenCalendarTargets) {
                    Text("표시 대상 선택")
                }
                TextButton(onClick = onOpenDiagnostics) {
                    Text("연결 상태")
                }
            }
        }
    }
}

@Composable
private fun ScheduleSectionTitle(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${count}개",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverviewEventCard(
    event: CalendarEventInstance,
    calendar: DeviceCalendar?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    event.title ?: "(제목 없음)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                calendar?.let {
                    Spacer(Modifier.width(8.dp))
                    SourceBadge(it.source)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(formatEventTime(event), style = MaterialTheme.typography.bodyMedium)
            event.location?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text("장소: $it", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                calendar?.displayName ?: "캘린더 ID ${event.calendarId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MailScreen(
    modifier: Modifier,
    uiState: MailUiState,
    permissionGranted: Boolean,
    permissionRequested: Boolean,
    shouldOpenSettings: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onConnectOutlook: () -> Unit,
    onDisconnectOutlook: () -> Unit,
    onSyncOutlook: () -> Unit,
    onConnectGmail: () -> Unit,
    onDisconnectGmail: () -> Unit,
    onSyncGmail: () -> Unit,
    onAddSchedule: (Int, Int) -> Unit,
    onUpdateSchedule: (Int, Int, Int) -> Unit,
    onDeleteSchedule: (Int) -> Unit,
    onSourceEnabledChanged: (String, Boolean) -> Unit,
    onDeliverPending: () -> Unit,
    onOpenFeatureSettings: () -> Unit,
    settingsOnly: Boolean,
) {
    val outlookEnabled = "outlook" !in uiState.disabledSourceIds
    val gmailEnabled = "gmail" !in uiState.disabledSourceIds
    val outlookDirectActive = outlookEnabled && uiState.outlookAccount != null
    val gmailDirectActive = gmailEnabled && uiState.gmailAccount != null
    val notificationCollectionNeeded =
        (gmailEnabled && !gmailDirectActive) || (outlookEnabled && !outlookDirectActive)
    val collectionAvailable = outlookDirectActive || gmailDirectActive ||
        (notificationCollectionNeeded && uiState.notificationAccessGranted)
    val active = uiState.enabled && permissionGranted && collectionAvailable
    val enabledSources = MailSources.all.count { it.id !in uiState.disabledSourceIds }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "새 메일 모아보기",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                when {
                                    active -> "켜짐 · 요약 대기 ${uiState.pendingMails.size}개"
                                    uiState.enabled && !collectionAvailable -> "메일 연결 필요"
                                    else -> "꺼짐"
                                },
                            )
                        }
                        if (settingsOnly) {
                            Switch(
                                checked = uiState.enabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) onEnable() else onDisable()
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Gmail·Outlook 계정 또는 앱 알림에서 새 메일을 확인해 " +
                            "설정한 시각에 한 번에 알려 줍니다.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (uiState.lastSummaryCheckMillis > 0L) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "마지막 요약 확인: ${formatMailTime(uiState.lastSummaryCheckMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!settingsOnly) {
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = onOpenFeatureSettings) {
                            Text(if (collectionAvailable) "메일 설정" else "연결 및 설정")
                        }
                    }
                }
            }
        }

        if (settingsOnly && !permissionGranted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "알림 권한 필요",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (permissionRequested) {
                                "메일 요약을 받으려면 Android 설정에서 알림을 허용해 주세요."
                            } else {
                                "메일 요약을 켜면 Android가 알림 권한을 요청합니다."
                            },
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = if (shouldOpenSettings) onOpenAppSettings else onEnable,
                        ) {
                            Text(if (shouldOpenSettings) "앱 설정 열기" else "알림 권한 허용")
                        }
                    }
                }
            }
        }

        if (settingsOnly) {
            item {
                OutlookDirectAccountCard(
                    uiState = uiState,
                    onConnect = onConnectOutlook,
                    onDisconnect = onDisconnectOutlook,
                    onSync = onSyncOutlook,
                )
            }

            item {
                GmailDirectAccountCard(
                    uiState = uiState,
                    onConnect = onConnectGmail,
                    onDisconnect = onDisconnectGmail,
                    onSync = onSyncGmail,
                )
            }
        }

        if (settingsOnly && !uiState.notificationAccessGranted && notificationCollectionNeeded) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "Gmail·Outlook 보조 수집",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "직접 연결하지 않은 Gmail·Outlook의 앱 알림을 수집하려면 " +
                                "대비하려면 Android 알림 접근에서 ‘오늘의 비서 메일 " +
                                "수집’을 허용해 주세요.",
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onOpenNotificationAccess) {
                            Text("알림 접근 설정 열기")
                        }
                    }
                }
            }
        }

        uiState.error?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
        }

        if (settingsOnly) {
            item {
                MailScheduleSettingsCard(
                    schedules = uiState.schedules,
                    onAddSchedule = onAddSchedule,
                    onUpdateSchedule = onUpdateSchedule,
                    onDeleteSchedule = onDeleteSchedule,
                )
            }

            item {
                MailSourceSettingsCard(
                    disabledSourceIds = uiState.disabledSourceIds,
                    outlookDirectConnected = uiState.outlookAccount != null,
                    gmailDirectConnected = uiState.gmailAccount != null,
                    onSourceEnabledChanged = onSourceEnabledChanged,
                )
            }
        }

        if (!settingsOnly) item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "요약 대기 메일 ${uiState.pendingMails.size}개",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "대상 앱 ${enabledSources}개 · 예약 ${uiState.scheduledCount}회",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onDeliverPending,
                    enabled = active && uiState.pendingMails.isNotEmpty(),
                ) { Text("지금 알림") }
            }
        }

        if (!settingsOnly && uiState.pendingMails.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (active) {
                            "아직 새 메일이 없습니다. 직접 연결 계정은 새로고침 또는 " +
                                "설정한 요약 시각에 확인합니다."
                        } else {
                            "기능을 켜고 Outlook 계정을 연결하거나 알림 접근을 허용해 주세요."
                        },
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (!settingsOnly) {
            items(
                items = uiState.pendingMails,
                key = CapturedMail::notificationKey,
            ) { mail ->
                CapturedMailCard(mail)
            }
        }

        if (!settingsOnly) item {
            Text(
                "Outlook은 Mail.ReadBasic, Gmail은 gmail.metadata 권한으로 발신자·제목·" +
                    "수신 시각만 읽습니다. 본문과 첨부파일은 읽지 않으며 표시된 메일 " +
                    "정보는 이 기기에만 저장됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp, 4.dp, 4.dp, 20.dp),
            )
        }
    }
}

@Composable
private fun GmailDirectAccountCard(
    uiState: MailUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSync: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Gmail 계정 직접 연결",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            if (uiState.gmailAccount == null) {
                Text(
                    "Google 로그인으로 받은편지함의 발신자·제목·수신 시각만 직접 확인합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onConnect, enabled = !uiState.isGmailSyncing) {
                    if (uiState.isGmailSyncing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Google 계정 연결")
                }
            } else {
                Text(uiState.gmailAccount.username, fontWeight = FontWeight.SemiBold)
                Text(
                    if (uiState.gmailBaselineInitialized) {
                        "연결됨 · 기존 메일 기준 설정 완료"
                    } else {
                        "연결됨 · 기존 메일 기준 설정 필요"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (uiState.lastGmailSyncMillis > 0L) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "마지막 직접 확인: ${formatMailTime(uiState.lastGmailSyncMillis)} " +
                            "· 새 메일 ${uiState.lastGmailNewMailCount}개",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = onSync, enabled = !uiState.isGmailSyncing) {
                        if (uiState.isGmailSyncing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (uiState.isGmailSyncing) "확인 중" else "지금 확인")
                    }
                    TextButton(onClick = onDisconnect, enabled = !uiState.isGmailSyncing) {
                        Text("연결 해제")
                    }
                }
            }
            uiState.gmailError?.let { error ->
                Spacer(Modifier.height(10.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun OutlookDirectAccountCard(
    uiState: MailUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSync: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Outlook 계정 직접 연결",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))

            when {
                uiState.isOutlookInitializing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("저장된 Microsoft 계정을 확인하는 중…")
                    }
                }

                uiState.outlookAccount == null -> {
                    Text(
                        "Microsoft 로그인으로 받은편지함의 기본 정보만 직접 확인합니다. " +
                            "비밀번호와 메일 본문은 이 앱에 저장하지 않습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onConnect, enabled = !uiState.isOutlookSyncing) {
                        if (uiState.isOutlookSyncing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Microsoft 계정 연결")
                    }
                }

                else -> {
                    Text(
                        uiState.outlookAccount.username,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (uiState.outlookBaselineInitialized) {
                            "연결됨 · 기존 메일 기준 설정 완료"
                        } else {
                            "연결됨 · 기존 메일 기준 설정 필요"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (uiState.lastOutlookSyncMillis > 0L) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "마지막 직접 확인: ${formatMailTime(uiState.lastOutlookSyncMillis)} " +
                                "· 새 메일 ${uiState.lastOutlookNewMailCount}개",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Microsoft 계정은 Outlook 메일과 To Do 할 일이 함께 사용합니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = onSync,
                            enabled = !uiState.isOutlookSyncing,
                        ) {
                            if (uiState.isOutlookSyncing) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (uiState.isOutlookSyncing) "확인 중" else "지금 확인")
                        }
                        TextButton(
                            onClick = onDisconnect,
                            enabled = !uiState.isOutlookSyncing,
                        ) { Text("Microsoft 연결 해제") }
                    }
                }
            }

            uiState.outlookError?.let { error ->
                Spacer(Modifier.height(10.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MailScheduleSettingsCard(
    schedules: List<SummarySchedule>,
    onAddSchedule: (Int, Int) -> Unit,
    onUpdateSchedule: (Int, Int, Int) -> Unit,
    onDeleteSchedule: (Int) -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "메일 요약 시각",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "기본값은 오전 8시, 오후 1시, 오후 7시입니다. 차수는 자유롭게 바꿀 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            if (schedules.isEmpty()) {
                Text(
                    "등록된 메일 요약 시각이 없습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            } else {
                schedules.forEachIndexed { index, schedule ->
                    if (index > 0) {
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}차 요약", fontWeight = FontWeight.SemiBold)
                            Text(
                                formatClockTime(schedule.hour, schedule.minute),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        TextButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        onUpdateSchedule(schedule.id, hour, minute)
                                    },
                                    schedule.hour,
                                    schedule.minute,
                                    false,
                                ).show()
                            },
                        ) { Text("변경") }
                        TextButton(onClick = { onDeleteSchedule(schedule.id) }) {
                            Text("삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            FilledTonalButton(
                onClick = {
                    val latest = schedules.lastOrNull()
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onAddSchedule(hour, minute) },
                        latest?.let { (it.hour + 1) % 24 } ?: 8,
                        latest?.minute ?: 0,
                        false,
                    ).show()
                },
            ) { Text("＋ 메일 알림 차수 추가") }
        }
    }
}

@Composable
private fun MailSourceSettingsCard(
    disabledSourceIds: Set<String>,
    outlookDirectConnected: Boolean,
    gmailDirectConnected: Boolean,
    onSourceEnabledChanged: (String, Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "메일 알림 대상",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "메일 요약에 포함할 계정을 선택하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            MailSources.all.forEachIndexed { index, source ->
                if (index > 0) HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(source.displayName, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                source.id == "outlook" && outlookDirectConnected ->
                                    "계정 직접 연결 · 앱 알림 중복 수집 안 함"
                                source.id == "gmail" && gmailDirectConnected ->
                                    "계정 직접 연결 · 앱 알림 중복 수집 안 함"
                                source.id == "outlook" -> "앱 알림으로 수집"
                                else -> "앱 알림으로 수집"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = source.id !in disabledSourceIds,
                        onCheckedChange = { enabled ->
                            onSourceEnabledChanged(source.id, enabled)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CapturedMailCard(mail: CapturedMail) {
    val source = MailSources.all.firstOrNull { it.id == mail.sourceId }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                    buildString {
                        append(source?.displayName ?: "메일")
                        if (mail.collectionMethod == MailCollectionMethod.DIRECT_ACCOUNT) {
                            append(" · 직접 연결")
                        }
                    },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    formatMailTime(mail.receivedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(mail.sender, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(mail.subject)
        }
    }
}

@Composable
private fun TodoScreen(
    modifier: Modifier,
    uiState: TodoUiState,
    permissionGranted: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onAuthorize: () -> Unit,
    onSync: () -> Unit,
    onAddSchedule: (Int, Int) -> Unit,
    onUpdateSchedule: (Int, Int, Int) -> Unit,
    onDeleteSchedule: (Int) -> Unit,
    onListEnabledChanged: (String, Boolean) -> Unit,
    onNotifyNow: () -> Unit,
    onOpenFeatureSettings: () -> Unit,
    settingsOnly: Boolean,
) {
    val context = LocalContext.current
    val listsById = uiState.lists.associateBy { it.id }
    val selectedTasks = uiState.tasks.filter { it.listId !in uiState.disabledListIds }
    val endOfToday = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
        .toInstant().toEpochMilli()
    val dueNow = selectedTasks.filter { it.effectiveAtMillis?.let { time -> time < endOfToday } == true }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "오늘의 할 일 요약",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (uiState.enabled && permissionGranted) {
                                    "켜짐 · 오늘까지 ${dueNow.size}개"
                                } else "꺼짐",
                            )
                        }
                        if (settingsOnly) {
                            Switch(
                                checked = uiState.enabled,
                                onCheckedChange = { if (it) onEnable() else onDisable() },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Samsung Reminder에서 Microsoft To Do로 동기화된 항목을 읽어 " +
                            "설정한 시각에 오늘까지 처리할 일을 모아 알려 줍니다.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!settingsOnly) {
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = onOpenFeatureSettings) {
                            Text(if (uiState.microsoftAccount == null) "연결 및 설정" else "할 일 설정")
                        }
                    }
                }
            }
        }

        if (settingsOnly && !permissionGranted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("알림 권한 필요", fontWeight = FontWeight.Bold)
                        Text("할 일 요약을 받으려면 Android 알림 권한을 허용해 주세요.")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = onEnable) { Text("권한 요청") }
                            TextButton(onClick = onOpenAppSettings) { Text("앱 설정") }
                        }
                    }
                }
            }
        }

        if (settingsOnly) item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Microsoft To Do 연결",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    when {
                        uiState.isInitializing -> Text("Microsoft 계정을 확인하는 중…")
                        uiState.microsoftAccount == null -> {
                            Text(
                                "Tasks.Read 읽기 전용 권한을 승인하면 할 일 목록을 가져옵니다.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onAuthorize, enabled = !uiState.isSyncing) {
                                Text("Microsoft 권한 승인")
                            }
                        }
                        else -> {
                            Text(uiState.microsoftAccount, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (uiState.lastSyncMillis > 0L) {
                                    "마지막 동기화: ${formatMailTime(uiState.lastSyncMillis)}"
                                } else "계정 확인됨 · Tasks.Read 승인 필요 여부를 확인하세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(onClick = onSync, enabled = !uiState.isSyncing) {
                                    if (uiState.isSyncing) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(if (uiState.isSyncing) "동기화 중" else "지금 동기화")
                                }
                                TextButton(onClick = onAuthorize, enabled = !uiState.isSyncing) {
                                    Text("권한 다시 승인")
                                }
                            }
                        }
                    }
                    uiState.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (settingsOnly) item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("할 일 요약 시각", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "기본값은 오전 8시와 오후 7시이며 차수를 자유롭게 추가·삭제할 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    uiState.schedules.forEachIndexed { index, schedule ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${index + 1}차 요약", fontWeight = FontWeight.SemiBold)
                                Text(formatClockTime(schedule.hour, schedule.minute))
                            }
                            TextButton(onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute -> onUpdateSchedule(schedule.id, hour, minute) },
                                    schedule.hour,
                                    schedule.minute,
                                    false,
                                ).show()
                            }) { Text("변경") }
                            TextButton(onClick = { onDeleteSchedule(schedule.id) }) {
                                Text("삭제", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    FilledTonalButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> onAddSchedule(hour, minute) },
                            8,
                            0,
                            false,
                        ).show()
                    }) { Text("＋ 할 일 알림 차수 추가") }
                }
            }
        }

        if (settingsOnly) item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("할 일 알림 대상", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Microsoft To Do 목록별로 요약 포함 여부를 선택합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.lists.isEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("동기화 후 목록이 표시됩니다.")
                    } else {
                        uiState.lists.forEachIndexed { index, list ->
                            if (index > 0) HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(list.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "미완료 ${uiState.tasks.count { it.listId == list.id }}개",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                Switch(
                                    checked = list.id !in uiState.disabledListIds,
                                    onCheckedChange = { onListEnabledChanged(list.id, it) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!settingsOnly) item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("미완료 할 일 ${selectedTasks.size}개", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("오늘까지 ${dueNow.size}개 · 예약 ${uiState.scheduledCount}회", style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(onClick = onNotifyNow, enabled = uiState.enabled && dueNow.isNotEmpty()) {
                    Text("지금 알림")
                }
            }
        }

        if (!settingsOnly && selectedTasks.isEmpty()) {
            item { Card { Text("표시할 미완료 할 일이 없습니다.", modifier = Modifier.padding(18.dp)) } }
        } else if (!settingsOnly) {
            items(selectedTasks, key = TodoTaskItem::id) { task ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            task.effectiveAtMillis?.let(::formatTodoTime) ?: "날짜 없음",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            listsById[task.listId]?.displayName ?: "Microsoft To Do",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        if (!settingsOnly) item {
            Text(
                "읽기 전용: 이 앱에서는 할 일을 완료·수정·삭제하지 않습니다. 개별 리마인더 " +
                    "알림은 Samsung Reminder가 계속 담당합니다.",
                modifier = Modifier.padding(4.dp, 4.dp, 4.dp, 20.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTodoTime(value: Long): String = Instant.ofEpochMilli(value)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm", Locale.KOREAN))

@Composable
private fun NoticeScreen(
    modifier: Modifier,
    uiState: NoticeUiState,
    permissionGranted: Boolean,
    permissionRequested: Boolean,
    shouldOpenSettings: Boolean,
    onEnableMonitoring: () -> Unit,
    onDisableMonitoring: () -> Unit,
    onOpenSettings: () -> Unit,
    onSourceEnabledChanged: (String, Boolean) -> Unit,
    onAddSource: (String, String) -> String?,
    onUpdateSource: (String, String, String) -> String?,
    onDeleteSource: (String) -> Unit,
    onSourceLockedChanged: (String, Boolean) -> Unit,
    onRestoreSource: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenFeatureSettings: () -> Unit,
    settingsOnly: Boolean,
) {
    val monitoringActive = uiState.monitoringEnabled && permissionGranted
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorName by rememberSaveable { mutableStateOf("") }
    var editorUrl by rememberSaveable { mutableStateOf("") }
    var editorError by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreSourceId by rememberSaveable { mutableStateOf<String?>(null) }

    fun openEditor(source: NoticeSource? = null) {
        editorSourceId = source?.id
        editorName = source?.name.orEmpty()
        editorUrl = source?.url.orEmpty()
        editorError = null
        editorOpen = true
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "신규 공지 자동 확인",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (monitoringActive) "1시간 간격 · 신규 글만 알림"
                                else "자동 확인 꺼짐",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (settingsOnly) {
                            Switch(
                                checked = monitoringActive,
                                onCheckedChange = { enabled ->
                                    if (enabled) onEnableMonitoring()
                                    else onDisableMonitoring()
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            uiState.isRefreshing -> "${uiState.sources.count { it.enabled }}개 사이트를 확인하는 중…"
                            uiState.lastCheckedMillis == null ->
                                "아직 확인하지 않았습니다. 첫 조회는 현재 글을 기준선으로 저장합니다."
                            else -> "마지막 확인: ${formatNoticeCheckTime(uiState.lastCheckedMillis)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (uiState.newArticleCount > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "이번 확인에서 신규 공지 ${uiState.newArticleCount}개 발견",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = onRefresh,
                            enabled = !uiState.isRefreshing,
                        ) { Text("지금 확인") }
                        if (!settingsOnly) {
                            TextButton(onClick = onOpenFeatureSettings) { Text("공지 설정") }
                        }
                    }
                }
            }
        }

        if (settingsOnly && (!permissionGranted || uiState.notificationError != null)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("자동 알림 권한", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            uiState.notificationError ?: if (permissionRequested) {
                                "Android 설정에서 이 앱의 알림을 허용해 주세요."
                            } else {
                                "자동 확인을 켜면 Android가 알림 권한을 요청합니다."
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = if (shouldOpenSettings) onOpenSettings
                            else onEnableMonitoring,
                        ) {
                            Text(if (shouldOpenSettings) "앱 설정 열기" else "알림 권한 허용")
                        }
                    }
                }
            }
        }

        if (uiState.isRefreshing) {
            item { LoadingRow("선택한 사이트를 확인하는 중…") }
        }

        if (settingsOnly) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("모니터링 사이트", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "기본 ${uiState.sources.count { it.source.isBuiltIn }}개 · " +
                                "직접 추가 ${uiState.sources.count { !it.source.isBuiltIn }}개",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { openEditor() }) { Text("＋ 추가") }
                }
            }
            items(items = uiState.sources, key = { it.source.id }) { sourceState ->
                NoticeSourceManagementCard(
                    state = sourceState,
                    onEnabledChanged = { enabled ->
                        onSourceEnabledChanged(sourceState.source.id, enabled)
                    },
                    onEdit = { openEditor(sourceState.source) },
                    onDelete = { deleteSourceId = sourceState.source.id },
                    onLockedChanged = { locked ->
                        onSourceLockedChanged(sourceState.source.id, locked)
                    },
                    onRestore = { restoreSourceId = sourceState.source.id },
                )
            }
        } else {
            items(items = uiState.sources, key = { it.source.id }) { sourceState ->
                NoticeSourceCard(
                    state = sourceState,
                    newArticleKeys = uiState.newArticleKeys,
                    onOpenArticle = onOpenArticle,
                )
            }
        }

        item {
            Text(
                if (settingsOnly) {
                    "HTTPS 게시판 목록 주소를 추가할 수 있습니다. 저장하면 즉시 구조를 " +
                        "확인하며, 첫 성공 조회는 기존 글을 기준선으로만 저장합니다. 로그인이나 " +
                        "자바스크립트가 필요한 사이트는 자동 분석이 되지 않을 수 있습니다."
                } else {
                    "첫 성공 조회에서는 기존 글을 신규로 알리지 않습니다. 이후 게시물 고유 ID가 " +
                        "추가됐을 때만 신규로 판정하며, 한 사이트가 실패해도 나머지는 계속 확인합니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp, 4.dp, 4.dp, 20.dp),
            )
        }
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = { editorOpen = false },
            title = { Text(if (editorSourceId == null) "사이트 추가" else "사이트 수정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editorName,
                        onValueChange = { editorName = it; editorError = null },
                        label = { Text("사이트 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editorUrl,
                        onValueChange = { editorUrl = it; editorError = null },
                        label = { Text("게시판 목록 주소 (https://)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    editorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val error = editorSourceId?.let { id ->
                            onUpdateSource(id, editorName, editorUrl)
                        } ?: onAddSource(editorName, editorUrl)
                        if (error == null) editorOpen = false else editorError = error
                    },
                ) { Text("저장 및 확인") }
            },
            dismissButton = {
                TextButton(onClick = { editorOpen = false }) { Text("취소") }
            },
        )
    }

    deleteSourceId?.let { sourceId ->
        val sourceName = uiState.sources.firstOrNull { it.source.id == sourceId }?.source?.name
            ?: "이 사이트"
        AlertDialog(
            onDismissRequest = { deleteSourceId = null },
            title = { Text("사이트 삭제") },
            text = { Text("‘$sourceName’을 삭제할까요? 저장된 기준 목록도 함께 지워집니다.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSource(sourceId)
                    deleteSourceId = null
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteSourceId = null }) { Text("취소") }
            },
        )
    }

    restoreSourceId?.let { sourceId ->
        val sourceName = uiState.sources.firstOrNull { it.source.id == sourceId }?.source?.name
            ?: "이 사이트"
        AlertDialog(
            onDismissRequest = { restoreSourceId = null },
            title = { Text("기본값 복원") },
            text = { Text("‘$sourceName’의 이름과 주소를 앱의 기본값으로 되돌릴까요?") },
            confirmButton = {
                TextButton(onClick = {
                    onRestoreSource(sourceId)
                    restoreSourceId = null
                }) { Text("복원") }
            },
            dismissButton = {
                TextButton(onClick = { restoreSourceId = null }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun NoticeSourceCard(
    state: NoticeSourceState,
    newArticleKeys: Set<String>,
    onOpenArticle: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.source.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        state.source.organization,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (state.enabled) "알림 대상" else "알림 제외",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!state.enabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "이 게시판은 자동 확인 대상에서 제외됐습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            state.error?.let { error ->
                Spacer(Modifier.height(10.dp))
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.articles.isNotEmpty()) {
                    Text(
                        "아래에는 마지막 성공 조회 결과를 표시합니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.articles.isEmpty() && state.error == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "지금 확인을 누르면 최신 게시물을 불러옵니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.articles.take(5).forEach { article ->
                    Spacer(Modifier.height(10.dp))
                    NoticeArticleCard(
                        article = article,
                        isNew = article.key() in newArticleKeys,
                        onClick = { onOpenArticle(article.url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeSourceManagementCard(
    state: NoticeSourceState,
    onEnabledChanged: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLockedChanged: (Boolean) -> Unit,
    onRestore: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.source.isBuiltIn) {
                            buildString {
                                append("기본 사이트 · ${state.source.organization} · ")
                                append(if (state.locked) "잠김" else "잠금 해제됨")
                                if (state.modified) append(" · 수정됨")
                            }
                        } else "직접 추가 · ${state.source.organization}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.enabled, onCheckedChange = onEnabledChanged)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                state.source.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            state.error?.let { error ->
                Spacer(Modifier.height(6.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (state.source.isBuiltIn) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onLockedChanged(!state.locked) }) {
                        Text(if (state.locked) "잠금 해제" else "잠그기")
                    }
                    if (!state.locked) {
                        TextButton(onClick = onEdit) { Text("수정") }
                    }
                }
                if (state.modified) {
                    TextButton(onClick = onRestore) { Text("기본값 복원") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onEdit) { Text("수정") }
                    TextButton(onClick = onDelete) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeArticleCard(
    article: NoticeArticle,
    isNew: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isNew) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    article.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (isNew) {
                    Spacer(Modifier.width(8.dp))
                    StatusPill(text = "신규", active = true)
                }
            }
            article.postedDate?.let { date ->
                Spacer(Modifier.height(4.dp))
                Text(
                    date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotificationSettingsScreen(
    modifier: Modifier,
    uiState: CalendarUiState,
    permissionGranted: Boolean,
    permissionRequested: Boolean,
    shouldOpenSettings: Boolean,
    onEnableNotifications: () -> Unit,
    onDisableNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddSchedule: (Int, Int) -> Unit,
    onUpdateSchedule: (Int, Int, Int) -> Unit,
    onDeleteSchedule: (Int) -> Unit,
    onCalendarEnabledChanged: (Long, Boolean) -> Unit,
    onSelectAllCalendars: () -> Unit,
    onClearAllCalendars: () -> Unit,
    onReschedule: () -> Unit,
) {
    val active = uiState.notificationsEnabled && permissionGranted

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "오늘 일정 요약 알림",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (active) "켜짐" else "꺼짐",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = active,
                            onCheckedChange = { enabled ->
                                if (enabled) onEnableNotifications()
                                else onDisableNotifications()
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "개별 일정 시작 전 알림이 아닙니다. 선택한 시각에 오늘의 전체 " +
                            "일정을 한 번에 정리해 알려 줍니다.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (!permissionGranted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "알림 권한 필요",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (permissionRequested) {
                                "일정 알림을 받으려면 Android 설정에서 알림을 허용해 주세요."
                            } else {
                                "알림을 켜면 Android가 알림 권한을 요청합니다."
                            },
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (shouldOpenSettings) {
                            Button(onClick = onOpenSettings) { Text("앱 설정 열기") }
                        } else {
                            Button(onClick = onEnableNotifications) { Text("알림 권한 허용") }
                        }
                    }
                }
            }
        }

        uiState.notificationError?.let { error ->
            item { ErrorCard(message = error, onRetry = onReschedule) }
        }

        item {
            ScheduleSettingsCard(
                schedules = uiState.summarySchedules,
                onAddSchedule = onAddSchedule,
                onUpdateSchedule = onUpdateSchedule,
                onDeleteSchedule = onDeleteSchedule,
            )
        }

        item {
            NotificationCalendarSettingsCard(
                calendars = uiState.calendars,
                excludedCalendarIds = uiState.excludedNotificationCalendarIds,
                onCalendarEnabledChanged = onCalendarEnabledChanged,
                onSelectAll = onSelectAllCalendars,
                onClearAll = onClearAllCalendars,
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "예약 상태",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            uiState.isSchedulingNotifications -> "알림을 다시 예약하는 중…"
                            !active -> "알림이 꺼져 있습니다."
                            else -> "매일 요약 알림 ${uiState.scheduledNotificationCount}회 예약됨"
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onReschedule,
                        enabled = active && !uiState.isSchedulingNotifications,
                    ) {
                        Text("지금 다시 예약")
                    }
                }
            }
        }

        item {
            Text(
                "알림 시각이 되면 선택한 캘린더의 당일 일정을 다시 읽어 한 알림으로 " +
                    "보여 줍니다. 공휴일·법정기념일·절기 캘린더도 각각 제외할 수 있습니다. " +
                    "삼성 캘린더의 개별 일정 알림과는 별개입니다. 배터리 절약 " +
                    "정책에 따라 예정 시각보다 늦게 도착할 수 있으며, 정확한 알람 특별권한은 " +
                    "요청하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp, 4.dp, 4.dp, 20.dp),
            )
        }
    }
}

@Composable
private fun ScheduleSettingsCard(
    schedules: List<SummarySchedule>,
    onAddSchedule: (Int, Int) -> Unit,
    onUpdateSchedule: (Int, Int, Int) -> Unit,
    onDeleteSchedule: (Int) -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "알림 차수와 시각",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "필요한 횟수만큼 추가하고, 각 시각을 변경하거나 삭제할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            if (schedules.isEmpty()) {
                Text(
                    "등록된 알림 시각이 없습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            } else {
                schedules.forEachIndexed { index, schedule ->
                    if (index > 0) {
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${index + 1}차 알림",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                formatClockTime(schedule.hour, schedule.minute),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        TextButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        onUpdateSchedule(schedule.id, hour, minute)
                                    },
                                    schedule.hour,
                                    schedule.minute,
                                    false,
                                ).show()
                            },
                        ) { Text("변경") }
                        TextButton(onClick = { onDeleteSchedule(schedule.id) }) {
                            Text("삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            FilledTonalButton(
                onClick = {
                    val latest = schedules.lastOrNull()
                    val suggestedHour = latest?.let { (it.hour + 1) % 24 }
                        ?: NotificationPreferences.DEFAULT_FIRST_HOUR
                    val suggestedMinute = latest?.minute
                        ?: NotificationPreferences.DEFAULT_FIRST_MINUTE
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onAddSchedule(hour, minute) },
                        suggestedHour,
                        suggestedMinute,
                        false,
                    ).show()
                },
            ) { Text("＋ 알림 차수 추가") }
        }
    }
}

@Composable
private fun NotificationCalendarSettingsCard(
    calendars: List<DeviceCalendar>,
    excludedCalendarIds: Set<Long>,
    onCalendarEnabledChanged: (Long, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
) {
    val selectedCount = calendars.count { it.id !in excludedCalendarIds }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "메인 화면 및 알림 대상",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${calendars.size}개 중 ${selectedCount}개 선택 · 오늘·다가오는 일정과 " +
                    "요약 알림에 함께 적용됩니다. 진단 화면은 전체를 표시합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSelectAll) { Text("전체 선택") }
                TextButton(onClick = onClearAll) { Text("전체 해제") }
            }

            if (calendars.isEmpty()) {
                Text(
                    "조회된 캘린더가 없습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                calendars.forEachIndexed { index, calendar ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                calendar.displayName ?: "이름 없는 캘린더",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${calendar.source.label} · " +
                                    calendar.accountType.displayValue(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = calendar.id !in excludedCalendarIds,
                            onCheckedChange = { enabled ->
                                onCalendarEnabledChanged(calendar.id, enabled)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarListScreen(
    modifier: Modifier,
    uiState: CalendarUiState,
    onCalendarSelected: (DeviceCalendar) -> Unit,
    onRefresh: () -> Unit,
) {
    val samsungCandidates = uiState.calendars.count {
        it.source == CalendarSource.SAMSUNG_CANDIDATE
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IntroCard(
                calendarCount = uiState.calendars.size,
                samsungCandidateCount = samsungCandidates,
            )
        }

        uiState.calendarError?.let { error ->
            item { ErrorCard(message = error, onRetry = onRefresh) }
        }

        if (uiState.isLoadingCalendars) {
            item { LoadingRow("기기 캘린더를 읽는 중…") }
        } else if (uiState.calendars.isEmpty() && uiState.calendarError == null) {
            item {
                EmptyCard(
                    "Calendar Provider가 반환한 캘린더가 없습니다. " +
                        "Samsung 캘린더 앱의 동기화·표시 설정을 확인해 주세요.",
                )
            }
        }

        items(uiState.calendars, key = { it.id }) { calendar ->
            CalendarCard(calendar = calendar, onClick = { onCalendarSelected(calendar) })
        }

        if (uiState.calendars.isNotEmpty()) {
            item {
                Text(
                    "캘린더를 누르면 지금부터 30일 동안의 일정 인스턴스를 조회합니다. " +
                        "VISIBLE이 꺼진 캘린더는 Android가 Instances 행을 만들지 않을 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp, 4.dp, 4.dp, 20.dp),
                )
            }
        }
    }
}

@Composable
private fun IntroCard(calendarCount: Int, samsungCandidateCount: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "읽기 전용 진단",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    calendarCount == 0 -> "기기 Calendar Provider를 확인하고 있습니다."
                    samsungCandidateCount > 0 ->
                        "전체 ${calendarCount}개 중 Samsung 계정 후보 ${samsungCandidateCount}개를 찾았습니다."
                    else ->
                        "캘린더 ${calendarCount}개를 찾았지만 명확한 Samsung 계정 유형은 없습니다. " +
                            "아래 원본 ACCOUNT_TYPE도 확인해 주세요."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CalendarCard(calendar: DeviceCalendar, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        calendar.displayName ?: "(표시 이름 없음)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    SourceBadge(calendar.source)
                }
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            CalendarMetadata(calendar)
        }
    }
}

@Composable
private fun CalendarMetadata(calendar: DeviceCalendar) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        MetadataRow("_ID", calendar.id.toString())
        MetadataRow("CALENDAR_DISPLAY_NAME", calendar.displayName.displayValue())
        MetadataRow("ACCOUNT_NAME", calendar.accountName.displayValue())
        MetadataRow("ACCOUNT_TYPE", calendar.accountType.displayValue())
        MetadataRow("OWNER_ACCOUNT", calendar.ownerAccount.displayValue())
        MetadataRow("VISIBLE", calendar.visible.displayValue())
        MetadataRow("SYNC_EVENTS", calendar.syncEvents.displayValue())
    }
}

@Composable
private fun EventListScreen(
    modifier: Modifier,
    calendar: DeviceCalendar,
    uiState: CalendarUiState,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    SourceBadge(calendar.source)
                    Spacer(Modifier.height(10.dp))
                    CalendarMetadata(calendar)
                    uiState.eventWindowStartMillis?.let { start ->
                        uiState.eventWindowEndMillis?.let { end ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "조회 범위: ${formatWindow(start, end)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        uiState.eventError?.let { error ->
            item { ErrorCard(message = error, onRetry = null) }
        }

        if (uiState.isLoadingEvents) {
            item { LoadingRow("30일 일정 인스턴스를 읽는 중…") }
        } else if (uiState.events.isEmpty() && uiState.eventError == null) {
            item {
                EmptyCard(
                    "앞으로 30일 안에 조회된 일정이 없습니다. " +
                        "VISIBLE이 꺼져 있거나 동기화되지 않은 캘린더일 수도 있습니다.",
                )
            }
        } else {
            item {
                Text(
                    "다가오는 일정 ${uiState.events.size}개",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            items(
                items = uiState.events,
                key = { "${it.eventId}-${it.beginMillis}" },
            ) { event ->
                EventCard(event = event, calendar = calendar)
            }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEventInstance, calendar: DeviceCalendar) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                event.title ?: "(제목 없음)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(formatEventTime(event), style = MaterialTheme.typography.bodyMedium)
            event.location?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text("장소: $it", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "출처: ${calendar.source.label} · ${calendar.accountType.displayValue()} " +
                    "· eventId ${event.eventId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SourceBadge(source: CalendarSource) {
    val containerColor = if (source == CalendarSource.SAMSUNG_CANDIDATE) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (source == CalendarSource.SAMSUNG_CANDIDATE) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            source.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(150.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("표시할 내용이 없습니다", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "확인이 필요합니다",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            onRetry?.let {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = it) { Text("다시 시도") }
            }
        }
    }
}

private fun String?.displayValue(): String = this?.takeIf { it.isNotBlank() } ?: "미제공"

private fun Boolean?.displayValue(): String = when (this) {
    true -> "1 (켜짐)"
    false -> "0 (꺼짐)"
    null -> "미제공"
}

private val dateTimeFormatter = DateTimeFormatter.ofPattern(
    "M월 d일 (E) a h:mm",
    Locale.KOREAN,
)
private val dateFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val todayTitleFormatter = DateTimeFormatter.ofPattern(
    "yyyy년 M월 d일 EEEE",
    Locale.KOREAN,
)
private val dayHeaderFormatter = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
private val clockTimeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
private val noticeCheckFormatter = DateTimeFormatter.ofPattern(
    "M월 d일 (E) a h:mm",
    Locale.KOREAN,
)

private fun formatClockTime(hour: Int, minute: Int): String =
    LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        .format(clockTimeFormatter)

private fun formatNoticeCheckTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(noticeCheckFormatter)

private fun formatMailTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(noticeCheckFormatter)

private fun CalendarEventInstance.startDate(): LocalDate {
    val zone = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
    return Instant.ofEpochMilli(beginMillis).atZone(zone).toLocalDate()
}

private fun CalendarEventInstance.inclusiveEndDate(): LocalDate {
    val zone = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
    val inclusiveEndMillis = maxOf(beginMillis, endMillis - 1)
    return Instant.ofEpochMilli(inclusiveEndMillis).atZone(zone).toLocalDate()
}

private fun CalendarEventInstance.occursOn(date: LocalDate): Boolean =
    !date.isBefore(startDate()) && !date.isAfter(inclusiveEndDate())

private fun formatWindow(startMillis: Long, endMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startMillis).atZone(zone).format(dateTimeFormatter)
    val end = Instant.ofEpochMilli(endMillis).atZone(zone).format(dateTimeFormatter)
    return "$start ~ $end"
}

private fun formatEventTime(event: CalendarEventInstance): String {
    if (event.allDay) {
        val start = Instant.ofEpochMilli(event.beginMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        val inclusiveEndMillis = maxOf(event.beginMillis, event.endMillis - 1)
        val end = Instant.ofEpochMilli(inclusiveEndMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        val startText = start.format(dateFormatter)
        return if (start == end) "$startText · 종일"
        else "$startText ~ ${end.format(dateFormatter)} · 종일"
    }

    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(event.beginMillis).atZone(zone).format(dateTimeFormatter)
    val end = Instant.ofEpochMilli(event.endMillis).atZone(zone).format(dateTimeFormatter)
    return "$start ~ $end"
}
