package com.example.galaxycalendarprobe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.galaxycalendarprobe.mail.MailScheduler
import com.example.galaxycalendarprobe.mail.MailViewModel
import com.example.galaxycalendarprobe.notice.NoticeViewModel
import com.example.galaxycalendarprobe.todo.TodoViewModel
import com.example.galaxycalendarprobe.ui.CalendarProbeApp
import com.example.galaxycalendarprobe.ui.theme.GalaxyCalendarProbeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CalendarViewModel by viewModels()
    private val noticeViewModel: NoticeViewModel by viewModels()
    private val mailViewModel: MailViewModel by viewModels()
    private val todoViewModel: TodoViewModel by viewModels()
    private var calendarPermissionGranted by mutableStateOf(false)
    private var notificationPermissionGranted by mutableStateOf(false)
    private var requestedSectionName by mutableStateOf(SECTION_TODAY)
    private var sectionRequestVersion by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        calendarPermissionGranted = hasReadCalendarPermission()
        notificationPermissionGranted = hasNotificationPermission()
        requestedSectionName = intent.requestedSection()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val noticeUiState by noticeViewModel.uiState.collectAsStateWithLifecycle()
            val mailUiState by mailViewModel.uiState.collectAsStateWithLifecycle()
            val todoUiState by todoViewModel.uiState.collectAsStateWithLifecycle()
            var permissionRequested by rememberSaveable { mutableStateOf(false) }
            var notificationPermissionRequested by rememberSaveable {
                mutableStateOf(false)
            }
            var notificationTargetName by rememberSaveable { mutableStateOf<String?>(null) }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                permissionRequested = true
                calendarPermissionGranted = granted
                if (granted) viewModel.loadCalendars(force = true)
                else viewModel.onPermissionRevoked()
            }

            val shouldOpenSettings = remember(
                calendarPermissionGranted,
                permissionRequested,
            ) {
                permissionRequested &&
                    !calendarPermissionGranted &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.READ_CALENDAR,
                    )
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                notificationPermissionRequested = true
                notificationPermissionGranted = granted
                when (notificationTargetName) {
                    NotificationPermissionTarget.CALENDAR.name -> {
                        if (granted) viewModel.setNotificationsEnabled(true)
                        else viewModel.onNotificationPermissionDenied()
                    }
                    NotificationPermissionTarget.NOTICES.name -> {
                        if (granted) noticeViewModel.setMonitoringEnabled(true)
                        else noticeViewModel.onNotificationPermissionDenied()
                    }
                    NotificationPermissionTarget.MAIL.name -> {
                        if (granted) {
                            mailViewModel.setEnabled(true)
                            val directOutlookSelected =
                                mailUiState.outlookAccount != null &&
                                    "outlook" !in mailUiState.disabledSourceIds
                            val directGmailSelected =
                                mailUiState.gmailAccount != null &&
                                    "gmail" !in mailUiState.disabledSourceIds
                            if (
                                !directOutlookSelected &&
                                !directGmailSelected &&
                                !hasMailNotificationAccess()
                            ) {
                                openNotificationAccessSettings()
                            }
                        } else {
                            mailViewModel.onNotificationPermissionDenied()
                        }
                    }
                    NotificationPermissionTarget.TODO.name -> {
                        todoViewModel.setEnabled(granted)
                    }
                }
                notificationTargetName = null
            }

            val gmailAuthorizationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                mailViewModel.completeGmailAuthorization(
                    result.data.takeIf { result.resultCode == RESULT_OK },
                )
            }
            val launchGmailAuthorization: (android.app.PendingIntent) -> Unit = { pendingIntent ->
                gmailAuthorizationLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                )
            }

            val shouldOpenNotificationSettings = remember(
                notificationPermissionGranted,
                notificationPermissionRequested,
            ) {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    notificationPermissionRequested &&
                    !notificationPermissionGranted &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
            }

            GalaxyCalendarProbeTheme {
                CalendarProbeApp(
                    permissionGranted = calendarPermissionGranted,
                    permissionRequested = permissionRequested,
                    shouldOpenSettings = shouldOpenSettings,
                    notificationPermissionGranted = notificationPermissionGranted,
                    notificationPermissionRequested = notificationPermissionRequested,
                    shouldOpenNotificationSettings = shouldOpenNotificationSettings,
                    uiState = uiState,
                    noticeUiState = noticeUiState,
                    mailUiState = mailUiState,
                    todoUiState = todoUiState,
                    initialSectionName = requestedSectionName,
                    sectionRequestVersion = sectionRequestVersion,
                    onRequestPermission = {
                        permissionRequested = true
                        permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    },
                    onOpenSettings = ::openAppSettings,
                    onEnableNotifications = {
                        if (hasNotificationPermission()) {
                            notificationPermissionGranted = true
                            viewModel.setNotificationsEnabled(true)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationTargetName =
                                NotificationPermissionTarget.CALENDAR.name
                            notificationPermissionRequested = true
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                        }
                    },
                    onDisableNotifications = {
                        viewModel.setNotificationsEnabled(false)
                    },
                    onAddSummarySchedule = viewModel::addSummarySchedule,
                    onUpdateSummarySchedule = viewModel::updateSummarySchedule,
                    onDeleteSummarySchedule = viewModel::deleteSummarySchedule,
                    onNotificationCalendarEnabledChanged =
                        viewModel::setNotificationCalendarEnabled,
                    onSelectAllNotificationCalendars =
                        viewModel::selectAllNotificationCalendars,
                    onClearAllNotificationCalendars =
                        viewModel::clearAllNotificationCalendars,
                    onRescheduleNotifications = viewModel::rescheduleNotifications,
                    onEnableNoticeMonitoring = {
                        if (hasNotificationPermission()) {
                            notificationPermissionGranted = true
                            noticeViewModel.setMonitoringEnabled(true)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationTargetName =
                                NotificationPermissionTarget.NOTICES.name
                            notificationPermissionRequested = true
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                        }
                    },
                    onDisableNoticeMonitoring = {
                        noticeViewModel.setMonitoringEnabled(false)
                    },
                    onNoticeSourceEnabledChanged = noticeViewModel::setSourceEnabled,
                    onAddNoticeSource = noticeViewModel::addCustomSource,
                    onUpdateNoticeSource = noticeViewModel::updateSource,
                    onDeleteNoticeSource = noticeViewModel::deleteCustomSource,
                    onNoticeSourceLockedChanged = noticeViewModel::setBuiltInSourceLocked,
                    onRestoreNoticeSource = noticeViewModel::restoreBuiltInSource,
                    onRefreshNotices = noticeViewModel::refresh,
                    onOpenNotice = ::openWebPage,
                    onEnableMailSummaries = {
                        if (hasNotificationPermission()) {
                            notificationPermissionGranted = true
                            mailViewModel.setEnabled(true)
                            val directOutlookSelected =
                                mailUiState.outlookAccount != null &&
                                    "outlook" !in mailUiState.disabledSourceIds
                            val directGmailSelected =
                                mailUiState.gmailAccount != null &&
                                    "gmail" !in mailUiState.disabledSourceIds
                            if (
                                !directOutlookSelected &&
                                !directGmailSelected &&
                                !hasMailNotificationAccess()
                            ) {
                                openNotificationAccessSettings()
                            }
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationTargetName = NotificationPermissionTarget.MAIL.name
                            notificationPermissionRequested = true
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                        }
                    },
                    onDisableMailSummaries = {
                        mailViewModel.setEnabled(false)
                    },
                    onOpenMailNotificationAccess = ::openNotificationAccessSettings,
                    onConnectOutlook = { mailViewModel.connectOutlook(this) },
                    onDisconnectOutlook = {
                        mailViewModel.disconnectOutlook(
                            todoViewModel::onMicrosoftAccountDisconnected,
                        )
                    },
                    onSyncOutlook = mailViewModel::syncOutlookNow,
                    onConnectGmail = {
                        mailViewModel.connectGmail(launchGmailAuthorization)
                    },
                    onDisconnectGmail = mailViewModel::disconnectGmail,
                    onSyncGmail = {
                        mailViewModel.syncGmailNow(launchGmailAuthorization)
                    },
                    onAddMailSchedule = mailViewModel::addSchedule,
                    onUpdateMailSchedule = mailViewModel::updateSchedule,
                    onDeleteMailSchedule = mailViewModel::deleteSchedule,
                    onMailSourceEnabledChanged = mailViewModel::setSourceEnabled,
                    onDeliverPendingMail = mailViewModel::deliverPendingNow,
                    onRefreshMail = {
                        mailViewModel.refresh()
                        mailViewModel.syncGmailNow(launchGmailAuthorization)
                    },
                    onEnableTodo = {
                        if (hasNotificationPermission()) {
                            notificationPermissionGranted = true
                            todoViewModel.setEnabled(true)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationTargetName = NotificationPermissionTarget.TODO.name
                            notificationPermissionRequested = true
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onDisableTodo = { todoViewModel.setEnabled(false) },
                    onAuthorizeTodo = { todoViewModel.authorizeAndSync(this) },
                    onSyncTodo = todoViewModel::syncNow,
                    onAddTodoSchedule = todoViewModel::addSchedule,
                    onUpdateTodoSchedule = todoViewModel::updateSchedule,
                    onDeleteTodoSchedule = todoViewModel::deleteSchedule,
                    onTodoListEnabledChanged = todoViewModel::setListEnabled,
                    onNotifyTodoNow = todoViewModel::notifyNow,
                    onCalendarSelected = viewModel::selectCalendar,
                    onBack = viewModel::clearSelection,
                    onRefresh = viewModel::refreshCurrentScreen,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedSectionName = intent.requestedSection()
        sectionRequestVersion += 1
    }

    override fun onResume() {
        super.onResume()
        val granted = hasReadCalendarPermission()
        notificationPermissionGranted = hasNotificationPermission()
        calendarPermissionGranted = granted
        mailViewModel.onNotificationAccessChanged(hasMailNotificationAccess())
        if (!notificationPermissionGranted) {
            viewModel.onNotificationPermissionDenied()
            noticeViewModel.onNotificationPermissionDenied()
            mailViewModel.onNotificationPermissionDenied()
            todoViewModel.setEnabled(false)
        }
        if (granted) viewModel.loadCalendars()
        else viewModel.onPermissionRevoked()
    }

    private fun hasReadCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }

    private fun openWebPage(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun hasMailNotificationAccess(): Boolean =
        MailScheduler(applicationContext).hasNotificationAccess()

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    companion object {
        const val EXTRA_OPEN_SECTION = "open_section"
        const val SECTION_TODAY = "TODAY"
        const val SECTION_MAIL = "MAIL"
        const val SECTION_NOTICES = "NOTICES"
        const val SECTION_TODOS = "TODOS"
    }
}

private fun Intent?.requestedSection(): String = this
    ?.getStringExtra(MainActivity.EXTRA_OPEN_SECTION)
    ?.takeIf {
        it in setOf(
            MainActivity.SECTION_TODAY,
            MainActivity.SECTION_MAIL,
            MainActivity.SECTION_NOTICES,
            MainActivity.SECTION_TODOS,
        )
    }
    ?: MainActivity.SECTION_TODAY

private enum class NotificationPermissionTarget {
    CALENDAR,
    NOTICES,
    MAIL,
    TODO,
}
