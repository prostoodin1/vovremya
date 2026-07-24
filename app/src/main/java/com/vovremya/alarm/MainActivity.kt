package com.vovremya.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vovremya.alarm.ui.MainApp
import com.vovremya.alarm.ui.MainViewModel
import com.vovremya.alarm.ui.PermissionState
import com.vovremya.alarm.ui.theme.VovremyaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val calendarPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.READ_CALENDAR] == true || readPermissions().calendar) {
            viewModel.onCalendarPermissionAvailable()
            if (grants[Manifest.permission.WRITE_CALENDAR] == true || readPermissions().calendarWrite) {
                viewModel.syncNow()
            }
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val alarmSoundPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        @Suppress("DEPRECATION")
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        viewModel.setAlarmSoundUri(uri?.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            VovremyaTheme(
                themeMode = state.settings.themeMode,
                accentTheme = state.settings.accentTheme,
                customAccentColor = state.settings.customAccentColor,
                backgroundStyle = state.settings.backgroundStyle,
            ) {
                var permissions by remember { mutableStateOf(readPermissions()) }
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            permissions = readPermissions()
                            if (permissions.calendar) {
                                viewModel.onCalendarPermissionAvailable()
                            }
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                MainApp(
                    state = state,
                    permissions = permissions,
                    onRequestCalendar = ::requestCalendarPermissions,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestExactAlarms = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                        }
                    },
                    onRequestFullScreen = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                        }
                    },
                    onSync = {
                        if (permissions.calendarWrite) viewModel.syncNow()
                        else requestCalendarPermissions()
                    },
                    onLeadMinutes = viewModel::setLeadMinutes,
                    onLatestEventMinutes = viewModel::setLatestEventMinutes,
                    onLatestEventEnabled = viewModel::setLatestEventEnabled,
                    onDailySyncMinutes = viewModel::setDailySyncMinutes,
                    onLookAheadDays = viewModel::setLookAheadDays,
                    onAllEventsPerDay = viewModel::setAllEventsPerDay,
                    onIncludeAllDayEvents = viewModel::setIncludeAllDayEvents,
                    onAllDayEventMinutes = viewModel::setAllDayEventMinutes,
                    onAlarmSoundEnabled = viewModel::setAlarmSoundEnabled,
                    onAlarmVibrationEnabled = viewModel::setAlarmVibrationEnabled,
                    onReminderVibrationEnabled = viewModel::setReminderVibrationEnabled,
                    onAlarmEffects = viewModel::setAlarmEffects,
                    onReminderEffects = viewModel::setReminderEffects,
                    onQuickDismiss = viewModel::setQuickDismiss,
                    onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onPickAlarmSound = { openAlarmSoundPicker(state.settings.alarmSoundUri) },
                    onSnoozeMinutes = viewModel::setSnoozeMinutes,
                    onAutoSilenceMinutes = viewModel::setAutoSilenceMinutes,
                    onToggleDay = viewModel::toggleDay,
                    onToggleCalendar = viewModel::toggleCalendar,
                    onSelectAllCalendars = viewModel::selectAllCalendars,
                    onAutomaticUpdates = viewModel::setAutomaticUpdates,
                    onUpdateChannel = viewModel::setUpdateChannel,
                    onSkipAlarm = viewModel::skipAlarm,
                    onRestoreAlarm = viewModel::restoreAlarm,
                    onSkipAllToday = viewModel::skipAllToday,
                    onRestoreAllToday = viewModel::restoreAllToday,
                    onThemeMode = viewModel::setThemeMode,
                    onAccentTheme = viewModel::setAccentTheme,
                    onCustomAccentColor = viewModel::setCustomAccentColor,
                    onBackgroundStyle = viewModel::setBackgroundStyle,
                    onCheckUpdates = viewModel::checkForUpdates,
                    onLoadReleaseCatalog = viewModel::loadReleaseCatalog,
                    onDownloadRelease = viewModel::downloadRelease,
                    onInstallRelease = viewModel::installRelease,
                    onMessageShown = viewModel::clearMessage,
                )
            }
        }
    }

    private fun openAlarmSoundPicker(currentUri: String) {
        val existing = currentUri.takeIf(String::isNotBlank)?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        alarmSoundPicker.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            },
        )
    }

    private fun requestCalendarPermissions() {
        calendarPermissions.launch(
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
            ),
        )
    }

    private fun readPermissions(): PermissionState {
        val calendar = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        val calendarWrite = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        val fullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        val camera = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        return PermissionState(calendar, calendarWrite, notifications, exact, fullScreen, camera)
    }
}
