@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.vovremya.alarm.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vovremya.alarm.BuildConfig
import com.vovremya.alarm.data.AlarmDelivery
import com.vovremya.alarm.data.CalendarInfo
import com.vovremya.alarm.data.AccentTheme
import com.vovremya.alarm.data.BackgroundStyle
import com.vovremya.alarm.data.EventDecision
import com.vovremya.alarm.data.EventDiagnostic
import com.vovremya.alarm.data.EventSource
import com.vovremya.alarm.data.ScheduledAlarm
import com.vovremya.alarm.data.SignalEffects
import com.vovremya.alarm.data.SyncDiagnostics
import com.vovremya.alarm.data.ThemeMode
import com.vovremya.alarm.data.TorchMode
import com.vovremya.alarm.data.UpdateChannel
import com.vovremya.alarm.data.QuickDismissMode
import com.vovremya.alarm.data.QuickDismissSettings
import com.vovremya.alarm.localization.appLocale
import com.vovremya.alarm.localization.tr
import com.vovremya.alarm.ui.theme.Mint
import com.vovremya.alarm.ui.theme.previewColor
import com.vovremya.alarm.update.AvailableRelease
import com.vovremya.alarm.update.ReleaseRelation
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class PermissionState(
    val calendar: Boolean,
    val calendarWrite: Boolean,
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
    val camera: Boolean,
) {
    val allGranted: Boolean get() = calendar && calendarWrite && notifications && exactAlarms && fullScreen
}

private enum class Screen { Home, Settings }

@Composable
fun MainApp(
    state: MainUiState,
    permissions: PermissionState,
    onRequestCalendar: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarms: () -> Unit,
    onRequestFullScreen: () -> Unit,
    onSync: () -> Unit,
    onLeadMinutes: (Int) -> Unit,
    onLatestEventMinutes: (Int) -> Unit,
    onLatestEventEnabled: (Boolean) -> Unit,
    onDailySyncMinutes: (Int) -> Unit,
    onLookAheadDays: (Int) -> Unit,
    onAllEventsPerDay: (Boolean) -> Unit,
    onIncludeAllDayEvents: (Boolean) -> Unit,
    onAllDayEventMinutes: (Int) -> Unit,
    onAlarmSoundEnabled: (Boolean) -> Unit,
    onAlarmVibrationEnabled: (Boolean) -> Unit,
    onReminderVibrationEnabled: (Boolean) -> Unit,
    onAlarmEffects: (SignalEffects) -> Unit,
    onReminderEffects: (SignalEffects) -> Unit,
    onQuickDismiss: (QuickDismissSettings) -> Unit,
    onRequestCamera: () -> Unit,
    onPickAlarmSound: () -> Unit,
    onSnoozeMinutes: (Int) -> Unit,
    onAutoSilenceMinutes: (Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onToggleCalendar: (Long) -> Unit,
    onSelectAllCalendars: () -> Unit,
    onAutomaticUpdates: (Boolean) -> Unit,
    onUpdateChannel: (UpdateChannel) -> Unit,
    onAdvancedMode: (Boolean) -> Unit,
    onSkipAlarm: (ScheduledAlarm) -> Unit,
    onRestoreAlarm: (ScheduledAlarm) -> Unit,
    onSkipAllToday: () -> Unit,
    onRestoreAllToday: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAccentTheme: (AccentTheme) -> Unit,
    onCustomAccentColor: (Int) -> Unit,
    onBackgroundStyle: (BackgroundStyle) -> Unit,
    onCheckUpdates: () -> Unit,
    onLoadReleaseCatalog: () -> Unit,
    onDownloadRelease: (AvailableRelease) -> Unit,
    onInstallRelease: (AvailableRelease) -> Unit,
    onMessageShown: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    BackHandler(screen == Screen.Settings) { screen = Screen.Home }
    LaunchedEffect(state.message) {
        state.message?.let {
            scope.launch { snackbar.showSnackbar(it) }
            onMessageShown()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState == Screen.Settings) {
                    (slideInHorizontally(spring(dampingRatio = .88f, stiffness = 330f)) { it / 3 } +
                        fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(spring(dampingRatio = .92f, stiffness = 430f)) { -it / 5 } +
                            fadeOut(tween(210)))
                } else {
                    (slideInHorizontally(spring(dampingRatio = .88f, stiffness = 330f)) { -it / 3 } +
                        fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(spring(dampingRatio = .92f, stiffness = 430f)) { it / 5 } +
                            fadeOut(tween(210)))
                }.using(
                    SizeTransform(clip = false) { _, _ ->
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)
                    },
                )
            },
            label = "screen",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { target ->
            when (target) {
                Screen.Home -> HomeScreen(
                    state = state,
                    permissions = permissions,
                    onSettings = { screen = Screen.Settings },
                    onRequestCalendar = onRequestCalendar,
                    onRequestNotifications = onRequestNotifications,
                    onRequestExactAlarms = onRequestExactAlarms,
                    onRequestFullScreen = onRequestFullScreen,
                    onSync = onSync,
                    onSkipAlarm = onSkipAlarm,
                    onRestoreAlarm = onRestoreAlarm,
                    onSkipAllToday = onSkipAllToday,
                    onRestoreAllToday = onRestoreAllToday,
                )
                Screen.Settings -> SettingsScreen(
                    state = state,
                    permissions = permissions,
                    onBack = { screen = Screen.Home },
                    onLeadMinutes = onLeadMinutes,
                    onLatestEventMinutes = onLatestEventMinutes,
                    onLatestEventEnabled = onLatestEventEnabled,
                    onDailySyncMinutes = onDailySyncMinutes,
                    onLookAheadDays = onLookAheadDays,
                    onAllEventsPerDay = onAllEventsPerDay,
                    onIncludeAllDayEvents = onIncludeAllDayEvents,
                    onAllDayEventMinutes = onAllDayEventMinutes,
                    onAlarmSoundEnabled = onAlarmSoundEnabled,
                    onAlarmVibrationEnabled = onAlarmVibrationEnabled,
                    onReminderVibrationEnabled = onReminderVibrationEnabled,
                    onAlarmEffects = onAlarmEffects,
                    onReminderEffects = onReminderEffects,
                    onQuickDismiss = onQuickDismiss,
                    onRequestCamera = onRequestCamera,
                    onPickAlarmSound = onPickAlarmSound,
                    onSnoozeMinutes = onSnoozeMinutes,
                    onAutoSilenceMinutes = onAutoSilenceMinutes,
                    onToggleDay = onToggleDay,
                    onToggleCalendar = onToggleCalendar,
                    onSelectAllCalendars = onSelectAllCalendars,
                    onAutomaticUpdates = onAutomaticUpdates,
                    onUpdateChannel = onUpdateChannel,
                    onAdvancedMode = onAdvancedMode,
                    onThemeMode = onThemeMode,
                    onAccentTheme = onAccentTheme,
                    onCustomAccentColor = onCustomAccentColor,
                    onBackgroundStyle = onBackgroundStyle,
                    onCheckUpdates = onCheckUpdates,
                    onLoadReleaseCatalog = onLoadReleaseCatalog,
                    onDownloadRelease = onDownloadRelease,
                    onInstallRelease = onInstallRelease,
                    onSync = onSync,
                    onRequestCalendar = onRequestCalendar,
                    onRequestExactAlarms = onRequestExactAlarms,
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: MainUiState,
    permissions: PermissionState,
    onSettings: () -> Unit,
    onRequestCalendar: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarms: () -> Unit,
    onRequestFullScreen: () -> Unit,
    onSync: () -> Unit,
    onSkipAlarm: (ScheduledAlarm) -> Unit,
    onRestoreAlarm: (ScheduledAlarm) -> Unit,
    onSkipAllToday: () -> Unit,
    onRestoreAllToday: () -> Unit,
) {
    var revealedAlarm by rememberSaveable { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val todayAlarms = state.alarms.filter { it.eventDate() == today }
    val todaySkipped = state.skippedAlarms.filter { it.eventDate() == today }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HomeHeader(onSettings) }
        item {
            AnimatedVisibility(
                visible = !permissions.allGranted,
                enter = fadeIn(tween(320)) + expandVertically(
                    animationSpec = spring(dampingRatio = .88f, stiffness = 360f),
                ),
                exit = fadeOut(tween(200)) + shrinkVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 440f),
                ),
            ) {
                PermissionCard(
                    permissions,
                    onRequestCalendar,
                    onRequestNotifications,
                    onRequestExactAlarms,
                    onRequestFullScreen,
                )
            }
        }
        item {
            val next = state.alarms.firstOrNull { it.delivery == AlarmDelivery.ALARM }
            NextAlarmCard(
                alarm = next,
                revealed = next != null && revealedAlarm == "next:${next.key}",
                onRevealedChange = { open ->
                    revealedAlarm = if (open && next != null) "next:${next.key}" else null
                },
                onSkip = { next?.let(onSkipAlarm) },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Ближайшие"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            tr(if (state.settings.allEventsPerDay) "Будильники и тихие напоминания" else "По одному событию на день"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    IconButton(onClick = onSync, enabled = permissions.calendar && !state.syncing) {
                        if (state.syncing) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, tr("Обновить"))
                        }
                    }
                }
                if (todayAlarms.isNotEmpty() || todaySkipped.isNotEmpty() || today in state.settings.skippedDates) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (todayAlarms.isNotEmpty()) {
                            OutlinedButton(onClick = onSkipAllToday) {
                                Icon(Icons.Rounded.SkipNext, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Пропустить все сегодня"))
                            }
                        }
                        if (todaySkipped.isNotEmpty() || today in state.settings.skippedDates) {
                            OutlinedButton(onClick = onRestoreAllToday) {
                                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Вернуть все сегодня"))
                            }
                        }
                    }
                }
            }
        }
        if (state.alarms.isEmpty()) {
            item { EmptyAlarms(permissions.calendar, state.diagnostics) }
        } else {
            items(state.alarms, key = ScheduledAlarm::key) { alarm ->
                AlarmRow(
                    alarm = alarm,
                    cancelled = false,
                    revealed = revealedAlarm == "active:${alarm.key}",
                    onRevealedChange = { open ->
                        revealedAlarm = if (open) "active:${alarm.key}" else null
                    },
                    onSkip = { onSkipAlarm(alarm) },
                    onRestore = {},
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(360),
                        placementSpec = spring(dampingRatio = .86f, stiffness = 360f),
                        fadeOutSpec = tween(220),
                    ),
                )
            }
        }
        if (state.skippedAlarms.isNotEmpty()) {
            item {
                Text(
                    tr("Отменённые события"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.skippedAlarms, key = { "skipped:${it.key}" }) { alarm ->
                AlarmRow(
                    alarm = alarm,
                    cancelled = true,
                    revealed = revealedAlarm == "skipped:${alarm.key}",
                    onRevealedChange = { open ->
                        revealedAlarm = if (open) "skipped:${alarm.key}" else null
                    },
                    onSkip = {},
                    onRestore = { onRestoreAlarm(alarm) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(360),
                        placementSpec = spring(dampingRatio = .86f, stiffness = 360f),
                        fadeOutSpec = tween(220),
                    ),
                )
            }
        }
        item {
            Text(
                tr(
                    "Календарь проверяется каждый день в %s. Приложение не работает постоянно в фоне.",
                    formatClockMinutes(state.settings.dailySyncMinutes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Вовремя"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", APP_LOCALE)).replaceFirstChar { it.uppercase() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (BuildConfig.VERSION_NAME.removeSuffix("-debug").contains('-')) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "BETA",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, tr("Настройки")) }
        }
    }
}

@Composable
private fun PermissionCard(
    permissions: PermissionState,
    onCalendar: () -> Unit,
    onNotifications: () -> Unit,
    onExact: () -> Unit,
    onFullScreen: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Завершим настройку"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                tr("Android просит разрешения отдельно — данные календаря остаются только на телефоне."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f),
            )
            Spacer(Modifier.height(4.dp))
            PermissionRow(
                tr("Доступ и синхронизация календаря"),
                permissions.calendar && permissions.calendarWrite,
                onCalendar,
            )
            PermissionRow(tr("Уведомления"), permissions.notifications, onNotifications)
            PermissionRow(tr("Точное время сигнала"), permissions.exactAlarms, onExact)
            PermissionRow(tr("Экран будильника"), permissions.fullScreen, onFullScreen)
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = !granted, onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.ChevronRight,
            null,
            tint = if (granted) Mint else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(tr(if (granted) "Готово" else "Разрешить"), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun NextAlarmCard(
    alarm: ScheduledAlarm?,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onSkip: () -> Unit,
) {
    if (alarm == null) {
        NextAlarmCardSurface(alarm = null, modifier = Modifier.fillMaxWidth())
        return
    }
    RevealableAlarmContainer(
        itemKey = "next:${alarm.key}",
        revealed = revealed,
        cancelled = false,
        onRevealedChange = onRevealedChange,
        onSkip = onSkip,
        onRestore = {},
        shape = RoundedCornerShape(30.dp),
    ) { foreground ->
        NextAlarmCardSurface(alarm = alarm, modifier = foreground)
    }
}

@Composable
private fun NextAlarmCardSurface(alarm: ScheduledAlarm?, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val start = listOf(primary, lerp(primary, Color.Black, .38f))
    val cardText = MaterialTheme.colorScheme.onPrimary
    Card(
        shape = RoundedCornerShape(30.dp),
        modifier = modifier.fillMaxWidth().animateContentSize(
            spring(dampingRatio = .86f, stiffness = 360f),
        ),
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(start)).padding(24.dp)) {
            AnimatedContent(
                targetState = alarm,
                contentKey = { it?.key },
                transitionSpec = {
                    (fadeIn(tween(360)) + scaleIn(tween(420), initialScale = .975f)) togetherWith
                        (fadeOut(tween(210)) + scaleOut(tween(240), targetScale = .985f))
                },
                label = "nextAlarm",
            ) { displayedAlarm ->
                if (displayedAlarm == null) {
                    Column(Modifier.padding(vertical = 18.dp)) {
                        Icon(Icons.Rounded.EventAvailable, null, tint = cardText.copy(alpha = .82f), modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(18.dp))
                        Text(tr("Всё спокойно"), color = cardText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(tr("Следующий будильник появится после проверки календаря"), color = cardText.copy(alpha = .82f))
                    }
                } else {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Alarm, null, tint = cardText.copy(alpha = .82f))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("СЛЕДУЮЩИЙ БУДИЛЬНИК"), color = cardText.copy(alpha = .82f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(formatTime(displayedAlarm.alarmAtMillis), color = cardText, fontSize = 58.sp, lineHeight = 62.sp, fontWeight = FontWeight.Light)
                        Text(formatDayLong(displayedAlarm.alarmAtMillis), color = cardText.copy(alpha = .9f), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(22.dp))
                        Surface(color = cardText.copy(alpha = .13f), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(displayedAlarm.title, color = cardText, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(tr("Событие в %s · %s", formatTime(displayedAlarm.eventStartMillis), timeUntil(displayedAlarm.alarmAtMillis)), color = cardText.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: ScheduledAlarm,
    cancelled: Boolean,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RevealableAlarmContainer(
        itemKey = "${if (cancelled) "skipped" else "active"}:${alarm.key}",
        revealed = revealed,
        cancelled = cancelled,
        onRevealedChange = onRevealedChange,
        onSkip = onSkip,
        onRestore = onRestore,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
    ) { foreground ->
        Card(
            modifier = foreground.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (cancelled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (cancelled) 0.dp else 1.dp),
        ) {
            Column {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(54.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(17.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(formatTime(alarm.alarmAtMillis), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(formatDayShort(alarm.alarmAtMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(alarm.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(Color(alarm.calendarColor), CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("%s · событие %s", alarm.calendarName, formatTime(alarm.eventStartMillis)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (!alarm.location.isNullOrBlank()) Icon(Icons.Rounded.LocationOn, null, tint = MaterialTheme.colorScheme.outline)
                }
                if (!cancelled) {
                    val reminder = alarm.delivery == AlarmDelivery.SILENT_REMINDER
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (reminder) Icons.Rounded.NotificationsActive else Icons.Rounded.Alarm,
                            null,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            tr(
                                when {
                                    !reminder -> "Будильник · звук по настройкам"
                                    alarm.vibrationEnabled -> "Напоминание · только вибрация"
                                    else -> "Тихое напоминание · без звука и вибрации"
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (cancelled) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Block, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            tr("Отменено пользователем"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RevealableAlarmContainer(
    itemKey: String,
    revealed: Boolean,
    cancelled: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onRestore: () -> Unit,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxOffsetPx = with(density) { 180.dp.toPx() }
    val offset = remember(itemKey) { Animatable(0f) }
    val animation = spring<Float>(dampingRatio = .84f, stiffness = 420f)
    LaunchedEffect(revealed, maxOffsetPx) {
        offset.animateTo(if (revealed) -maxOffsetPx else 0f, animation)
    }
    val dragState = rememberDraggableState { delta ->
        scope.launch {
            offset.snapTo((offset.value + delta).coerceIn(-maxOffsetPx, 0f))
        }
    }
    Box(modifier = modifier.fillMaxWidth().clip(shape)) {
        Row(
            modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cancelled) {
                AlarmActionButton(
                    label = tr("Вернуть"),
                    icon = Icons.Rounded.Refresh,
                    color = Mint,
                    onClick = {
                        onRevealedChange(false)
                        onRestore()
                    },
                )
                AlarmActionButton(
                    label = tr("Закрыть"),
                    icon = Icons.Rounded.ChevronRight,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onRevealedChange(false) },
                )
            } else {
                AlarmActionButton(
                    label = tr("Пропустить"),
                    icon = Icons.Rounded.SkipNext,
                    color = MaterialTheme.colorScheme.error,
                    onClick = {
                        onRevealedChange(false)
                        onSkip()
                    },
                )
                AlarmActionButton(
                    label = tr("Вернуть"),
                    icon = Icons.Rounded.Refresh,
                    color = Mint,
                    onClick = { onRevealedChange(false) },
                )
            }
        }
        content(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        val shouldReveal = offset.value < -maxOffsetPx * .28f
                        onRevealedChange(shouldReveal)
                        scope.launch {
                            offset.animateTo(if (shouldReveal) -maxOffsetPx else 0f, animation)
                        }
                    },
                )
                .clickable { onRevealedChange(!revealed) },
        )
    }
}

@Composable
private fun AlarmActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(84.dp).height(64.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        color = color.copy(alpha = .16f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, Modifier.size(21.dp), tint = color)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyAlarms(hasCalendarPermission: Boolean, diagnostics: SyncDiagnostics?) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(tr(if (hasCalendarPermission) "Подходящих событий нет" else "Нужен доступ к календарю"), fontWeight = FontWeight.Bold)
            Text(
                if (hasCalendarPermission) diagnosticsText(diagnostics) else tr("Разрешите доступ в карточке выше"),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    state: MainUiState,
    permissions: PermissionState,
    onBack: () -> Unit,
    onLeadMinutes: (Int) -> Unit,
    onLatestEventMinutes: (Int) -> Unit,
    onLatestEventEnabled: (Boolean) -> Unit,
    onDailySyncMinutes: (Int) -> Unit,
    onLookAheadDays: (Int) -> Unit,
    onAllEventsPerDay: (Boolean) -> Unit,
    onIncludeAllDayEvents: (Boolean) -> Unit,
    onAllDayEventMinutes: (Int) -> Unit,
    onAlarmSoundEnabled: (Boolean) -> Unit,
    onAlarmVibrationEnabled: (Boolean) -> Unit,
    onReminderVibrationEnabled: (Boolean) -> Unit,
    onAlarmEffects: (SignalEffects) -> Unit,
    onReminderEffects: (SignalEffects) -> Unit,
    onQuickDismiss: (QuickDismissSettings) -> Unit,
    onRequestCamera: () -> Unit,
    onPickAlarmSound: () -> Unit,
    onSnoozeMinutes: (Int) -> Unit,
    onAutoSilenceMinutes: (Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onToggleCalendar: (Long) -> Unit,
    onSelectAllCalendars: () -> Unit,
    onAutomaticUpdates: (Boolean) -> Unit,
    onUpdateChannel: (UpdateChannel) -> Unit,
    onAdvancedMode: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAccentTheme: (AccentTheme) -> Unit,
    onCustomAccentColor: (Int) -> Unit,
    onBackgroundStyle: (BackgroundStyle) -> Unit,
    onCheckUpdates: () -> Unit,
    onLoadReleaseCatalog: () -> Unit,
    onDownloadRelease: (AvailableRelease) -> Unit,
    onInstallRelease: (AvailableRelease) -> Unit,
    onSync: () -> Unit,
    onRequestCalendar: () -> Unit,
    onRequestExactAlarms: () -> Unit,
) {
    var showLeadDialog by rememberSaveable { mutableStateOf(false) }
    var showColorDialog by rememberSaveable { mutableStateOf(false) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    var releasesExpanded by rememberSaveable { mutableStateOf(false) }
    var alarmEffectsExpanded by rememberSaveable { mutableStateOf(false) }
    var reminderEffectsExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val selectedCalendars = state.calendars.filter { calendar ->
        state.settings.selectedCalendarIds.isEmpty() || calendar.id in state.settings.selectedCalendarIds
    }
    val repairCandidates = selectedCalendars.filter { !it.syncEvents }
    if (showLeadDialog) {
        LeadTimeDialog(
            initialMinutes = state.settings.leadMinutes,
            onDismiss = { showLeadDialog = false },
            onConfirm = {
                onLeadMinutes(it)
                showLeadDialog = false
            },
        )
    }
    if (showColorDialog) {
        ColorPickerDialog(
            initialColor = state.settings.customAccentColor,
            onDismiss = { showColorDialog = false },
            onConfirm = {
                onCustomAccentColor(it)
                showColorDialog = false
            },
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Назад")) }
                Spacer(Modifier.width(4.dp))
                Text(tr("Настройки"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            SettingsCard(
                Icons.Rounded.Palette,
                tr("Оформление"),
                tr(if (state.settings.advancedMode) "Цвет и режим интерфейса" else "Тема приложения"),
            ) {
                Text(tr("Режим"), fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.settings.themeMode == mode,
                            onClick = { onThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> tr("Как на телефоне")
                                        ThemeMode.LIGHT -> tr("Светлый")
                                        ThemeMode.DARK -> tr("Тёмный")
                                    },
                                )
                            },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = state.settings.advancedMode,
                    enter = fadeIn(tween(260)) + expandVertically(
                        animationSpec = spring(dampingRatio = .88f, stiffness = 360f),
                    ),
                    exit = fadeOut(tween(160)) + shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(tr("Фон"), fontWeight = FontWeight.Medium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BackgroundStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = state.settings.backgroundStyle == style,
                                    onClick = { onBackgroundStyle(style) },
                                    label = { Text(backgroundStyleName(style)) },
                                )
                            }
                        }
                        HorizontalDivider()
                        Text(tr("Основной цвет"), fontWeight = FontWeight.Medium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccentTheme.entries.filterNot { it == AccentTheme.CUSTOM }.forEach { accent ->
                                FilterChip(
                                    selected = state.settings.accentTheme == accent,
                                    onClick = { onAccentTheme(accent) },
                                    leadingIcon = {
                                        Box(
                                            Modifier
                                                .size(15.dp)
                                                .background(accent.previewColor(), CircleShape),
                                        )
                                    },
                                    label = { Text(accentName(accent)) },
                                )
                            }
                        }
                        OutlinedButton(onClick = { showColorDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .background(Color(state.settings.customAccentColor), CircleShape),
                            )
                            Spacer(Modifier.width(9.dp))
                            Text("${tr("Свой цвет")} · ${formatColorHex(state.settings.customAccentColor)}")
                        }
                    }
                }
            }
        }
        item {
            SettingsCard(
                Icons.Rounded.Timer,
                tr("Время"),
                tr(if (state.settings.advancedMode) "Точная настройка планирования" else "За сколько поставить будильник"),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatLead(state.settings.leadMinutes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(tr(if (state.settings.leadMinutes == 0) "без опережения" else "до начала"), modifier = Modifier.padding(bottom = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 30, 60, 90, 120, 180, 24 * 60).forEach { minutes ->
                        FilterChip(
                            selected = state.settings.leadMinutes == minutes,
                            onClick = { onLeadMinutes(minutes) },
                            label = { Text(formatLead(minutes)) },
                        )
                    }
                }
                OutlinedButton(onClick = { showLeadDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Задать дни, часы и минуты"))
                }
                AnimatedVisibility(
                    visible = state.settings.advancedMode,
                    enter = fadeIn(tween(260)) + expandVertically(
                        animationSpec = spring(dampingRatio = .88f, stiffness = 360f),
                    ),
                    exit = fadeOut(tween(160)) + shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        HorizontalDivider()
                        ToggleRow(
                            title = tr("Ограничить поздние события"),
                            subtitle = if (state.settings.latestEventEnabled) tr("Не позже %s", formatClockMinutes(state.settings.latestEventMinutes)) else tr("Ограничение выключено"),
                            checked = state.settings.latestEventEnabled,
                            onChecked = onLatestEventEnabled,
                        )
                        AnimatedVisibility(
                            visible = state.settings.latestEventEnabled,
                            enter = fadeIn(tween(260)) + expandVertically(),
                            exit = fadeOut(tween(180)) + shrinkVertically(),
                        ) {
                            SettingsActionRow(
                                title = tr("Последнее допустимое время"),
                                subtitle = formatClockMinutes(state.settings.latestEventMinutes),
                                onClick = {
                                    val hour = state.settings.latestEventMinutes / 60
                                    val minute = state.settings.latestEventMinutes % 60
                                    TimePickerDialog(context, { _, h, m -> onLatestEventMinutes(h * 60 + m) }, hour, minute, true).show()
                                },
                            )
                        }
                        HorizontalDivider()
                        Text(tr("Проверять события вперёд"), fontWeight = FontWeight.Medium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 14, 21, 30, 60, 90).forEach { days ->
                                FilterChip(
                                    selected = state.settings.lookAheadDays == days,
                                    onClick = { onLookAheadDays(days) },
                                    label = { Text(tr("%d дн", days)) },
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            SettingsCard(
                Icons.Rounded.NotificationsActive,
                tr("Будильники"),
                tr(if (state.settings.advancedMode) "События, звук и повтор сигнала" else "События, звук и вибрация"),
            ) {
                ToggleRow(
                    title = tr("Напоминать об остальных событиях"),
                    subtitle = tr(if (state.settings.allEventsPerDay) "Первое событие — будильник, остальные — экран без звука" else "Только первое событие дня с будильником"),
                    checked = state.settings.allEventsPerDay,
                    onChecked = onAllEventsPerDay,
                )
                AnimatedVisibility(
                    visible = state.settings.allEventsPerDay,
                    enter = fadeIn(tween(260)) + expandVertically(
                        animationSpec = spring(dampingRatio = .88f, stiffness = 390f),
                    ),
                    exit = fadeOut(tween(180)) + shrinkVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 460f),
                    ),
                ) {
                    ToggleRow(
                        title = tr("Вибрация для остальных событий"),
                        subtitle = tr("Только вибрация, без звука"),
                        checked = state.settings.reminderVibrationEnabled,
                        onChecked = onReminderVibrationEnabled,
                    )
                }
                AnimatedVisibility(
                    visible = state.settings.advancedMode,
                    enter = fadeIn(tween(260)) + expandVertically(
                        animationSpec = spring(dampingRatio = .88f, stiffness = 360f),
                    ),
                    exit = fadeOut(tween(160)) + shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ToggleRow(
                            title = tr("События на весь день"),
                            subtitle = tr("Использовать для них выбранное условное время"),
                            checked = state.settings.includeAllDayEvents,
                            onChecked = onIncludeAllDayEvents,
                        )
                        AnimatedVisibility(
                            visible = state.settings.includeAllDayEvents,
                            enter = fadeIn(tween(260)) + expandVertically(),
                            exit = fadeOut(tween(180)) + shrinkVertically(),
                        ) {
                            SettingsActionRow(
                                title = tr("Условное время события"),
                                subtitle = formatClockMinutes(state.settings.allDayEventMinutes),
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m -> onAllDayEventMinutes(h * 60 + m) },
                                        state.settings.allDayEventMinutes / 60,
                                        state.settings.allDayEventMinutes % 60,
                                        true,
                                    ).show()
                                },
                            )
                        }
                    }
                }
                HorizontalDivider()
                ToggleRow(
                    title = tr("Звук"),
                    subtitle = tr("Проигрывать выбранную мелодию"),
                    checked = state.settings.alarmSoundEnabled,
                    onChecked = onAlarmSoundEnabled,
                )
                AnimatedVisibility(
                    visible = state.settings.alarmSoundEnabled,
                    enter = fadeIn(tween(260)) + expandVertically(
                        animationSpec = spring(dampingRatio = .88f, stiffness = 390f),
                    ),
                    exit = fadeOut(tween(180)) + shrinkVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 460f),
                    ),
                ) {
                    SettingsActionRow(
                        title = tr("Мелодия будильника"),
                        subtitle = tr(if (state.settings.alarmSoundUri.isBlank()) "Системная по умолчанию" else "Выбрана пользователем"),
                        onClick = onPickAlarmSound,
                    )
                }
                ToggleRow(
                    title = tr("Вибрация"),
                    subtitle = tr("Повторяющийся вибросигнал"),
                    checked = state.settings.alarmVibrationEnabled,
                    onChecked = onAlarmVibrationEnabled,
                )
                AnimatedVisibility(
                    visible = state.settings.advancedMode,
                    enter = fadeIn(tween(260)) + expandVertically(
                        animationSpec = spring(dampingRatio = .88f, stiffness = 360f),
                    ),
                    exit = fadeOut(tween(160)) + shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        HorizontalDivider()
                        ToggleRow(
                            title = tr("Упрощённое отключение после времени"),
                            subtitle = if (state.settings.quickDismiss.enabled) {
                                tr("После %s без кнопки «Позже»", formatClockMinutes(state.settings.quickDismiss.afterMinutes))
                            } else {
                                tr("Всегда показывать «Я встал» и «Позже»")
                            },
                            checked = state.settings.quickDismiss.enabled,
                            onChecked = { enabled ->
                                onQuickDismiss(state.settings.quickDismiss.copy(enabled = enabled))
                            },
                        )
                        AnimatedVisibility(
                            visible = state.settings.quickDismiss.enabled,
                            enter = fadeIn(tween(260)) + expandVertically(),
                            exit = fadeOut(tween(180)) + shrinkVertically(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SettingsActionRow(
                                    title = tr("Включать упрощённый экран после"),
                                    subtitle = formatClockMinutes(state.settings.quickDismiss.afterMinutes),
                                    onClick = {
                                        TimePickerDialog(
                                            context,
                                            { _, h, m ->
                                                onQuickDismiss(state.settings.quickDismiss.copy(afterMinutes = h * 60 + m))
                                            },
                                            state.settings.quickDismiss.afterMinutes / 60,
                                            state.settings.quickDismiss.afterMinutes % 60,
                                            true,
                                        ).show()
                                    },
                                )
                                Text(tr("Как выключать"), fontWeight = FontWeight.Medium)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    QuickDismissMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = state.settings.quickDismiss.mode == mode,
                                            onClick = { onQuickDismiss(state.settings.quickDismiss.copy(mode = mode)) },
                                            label = {
                                                Text(
                                                    tr(
                                                        if (mode == QuickDismissMode.BUTTON) {
                                                            "Кнопка «Готово»"
                                                        } else {
                                                            "Касание экрана"
                                                        },
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                        Text(tr("Отложить сигнал"), fontWeight = FontWeight.Medium)
                        MinuteChoiceChips(state.settings.snoozeMinutes, listOf(5, 10, 15, 30, 60), onSnoozeMinutes)
                        Text(tr("Автоматически выключить звук"), fontWeight = FontWeight.Medium)
                        MinuteChoiceChips(state.settings.autoSilenceMinutes, listOf(1, 5, 10, 15, 30), onAutoSilenceMinutes)
                    }
                }
            }
        }
        item {
            AnimatedVisibility(
                visible = state.settings.advancedMode,
                enter = fadeIn(tween(280)) + expandVertically(
                    animationSpec = spring(dampingRatio = .88f, stiffness = 360f),
                ),
                exit = fadeOut(tween(160)) + shrinkVertically(),
            ) {
                SettingsCard(Icons.Rounded.FlashOn, tr("Сигналы"), tr("Яркость, фонарик и сила вибрации")) {
                SettingsActionRow(
                    title = tr("Первое событие дня"),
                    subtitle = signalEffectsSummary(
                        state.settings.alarmEffects,
                        state.settings.alarmVibrationEnabled,
                    ),
                    onClick = { alarmEffectsExpanded = !alarmEffectsExpanded },
                    expanded = alarmEffectsExpanded,
                )
                AnimatedVisibility(
                    visible = alarmEffectsExpanded,
                    enter = fadeIn(tween(260)) + expandVertically(
                        animationSpec = spring(dampingRatio = .88f, stiffness = 390f),
                    ),
                    exit = fadeOut(tween(180)) + shrinkVertically(),
                ) {
                    SignalEffectsEditor(
                        effects = state.settings.alarmEffects,
                        vibrationEnabled = state.settings.alarmVibrationEnabled,
                        cameraGranted = permissions.camera,
                        onEffectsChange = onAlarmEffects,
                        onVibrationEnabled = onAlarmVibrationEnabled,
                        onRequestCamera = onRequestCamera,
                    )
                }
                HorizontalDivider()
                SettingsActionRow(
                    title = tr("Остальные события"),
                    subtitle = signalEffectsSummary(
                        state.settings.reminderEffects,
                        state.settings.reminderVibrationEnabled,
                    ),
                    onClick = { reminderEffectsExpanded = !reminderEffectsExpanded },
                    expanded = reminderEffectsExpanded,
                )
                AnimatedVisibility(
                    visible = reminderEffectsExpanded,
                    enter = fadeIn(tween(260)) + expandVertically(
                        animationSpec = spring(dampingRatio = .88f, stiffness = 390f),
                    ),
                    exit = fadeOut(tween(180)) + shrinkVertically(),
                ) {
                    SignalEffectsEditor(
                        effects = state.settings.reminderEffects,
                        vibrationEnabled = state.settings.reminderVibrationEnabled,
                        cameraGranted = permissions.camera,
                        onEffectsChange = onReminderEffects,
                        onVibrationEnabled = onReminderVibrationEnabled,
                        onRequestCamera = onRequestCamera,
                    )
                }
                }
            }
        }
        item {
            SettingsCard(Icons.Rounded.CalendarMonth, tr("Дни недели"), tr("В какие дни создавать будильник")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in state.settings.enabledDays,
                            onClick = { onToggleDay(day) },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, APP_LOCALE).replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                if (state.settings.enabledDays.isEmpty()) {
                    Text(tr("Ни один день не выбран — будильники не создаются"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SettingsCard(Icons.Rounded.EventAvailable, tr("Календари"), tr("Источники событий на этом телефоне")) {
                if (!permissions.calendar) {
                    FilledTonalButton(onClick = onRequestCalendar, modifier = Modifier.fillMaxWidth()) { Text(tr("Разрешить доступ к календарю")) }
                } else if (state.calendars.isEmpty()) {
                    Text(tr("На устройстве не найдено календарей"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    if (state.calendars.any(CalendarInfo::isShared)) {
                        Text(
                            tr("Найдено общих календарей: %d", state.calendars.count(CalendarInfo::isShared)),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (repairCandidates.isNotEmpty() || !permissions.calendarWrite) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    tr("Приложение может включить выбранные календари и запросить их синхронизацию у Android."),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                FilledTonalButton(
                                    onClick = if (permissions.calendarWrite) onSync else onRequestCalendar,
                                    enabled = !state.syncing,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (state.syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Rounded.Refresh, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        tr(
                                            if (permissions.calendarWrite) "Исправить и синхронизировать"
                                            else "Разрешить управление календарями",
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    FilledTonalButton(onClick = onSelectAllCalendars, modifier = Modifier.fillMaxWidth()) {
                        Text(tr(if (state.settings.selectedCalendarIds.isEmpty()) "Все календари уже выбраны" else "Выбрать все календари"))
                    }
                    state.calendars.forEachIndexed { index, calendar ->
                        CalendarToggle(
                            calendar = calendar,
                            checked = state.settings.selectedCalendarIds.isEmpty() || calendar.id in state.settings.selectedCalendarIds,
                            enabled = true,
                            onClick = { onToggleCalendar(calendar.id) },
                        )
                        if (index != state.calendars.lastIndex) HorizontalDivider(Modifier.padding(start = 42.dp))
                    }
                }
            }
        }
        item {
            AnimatedVisibility(
                visible = state.settings.advancedMode,
                enter = fadeIn(tween(280)) + expandVertically(
                    animationSpec = spring(dampingRatio = .88f, stiffness = 360f),
                ),
                exit = fadeOut(tween(160)) + shrinkVertically(),
            ) {
                SettingsCard(
                    Icons.Rounded.NotificationsActive,
                    tr("Планирование"),
                    tr("Ежедневно в %s", formatClockMinutes(state.settings.dailySyncMinutes)),
                ) {
                    SettingsActionRow(
                    title = tr("Время проверки календарей"),
                    subtitle = formatClockMinutes(state.settings.dailySyncMinutes),
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> onDailySyncMinutes(h * 60 + m) },
                            state.settings.dailySyncMinutes / 60,
                            state.settings.dailySyncMinutes % 60,
                            true,
                        ).show()
                    },
                )
                SettingsActionRow(
                    title = tr(if (permissions.exactAlarms) "Точное планирование включено" else "Разрешить точное время"),
                    subtitle = state.lastSyncMillis?.let { tr("Последняя проверка %s", formatLastSync(it)) } ?: tr("Ещё не проверялось"),
                    onClick = if (permissions.exactAlarms) onSync else onRequestExactAlarms,
                )
                HorizontalDivider()
                SettingsActionRow(
                    title = tr("Ошибки и журнал"),
                    subtitle = when {
                        state.lastError != null -> tr("Есть ошибка · нажмите, чтобы раскрыть")
                        state.diagnostics?.readErrors?.isNotEmpty() == true -> tr("Есть ошибки чтения · нажмите, чтобы раскрыть")
                        diagnosticsExpanded -> tr("Нажмите, чтобы скрыть")
                        else -> tr("Скрыто · нажмите, чтобы раскрыть")
                    },
                    onClick = { diagnosticsExpanded = !diagnosticsExpanded },
                    expanded = diagnosticsExpanded,
                )
                AnimatedVisibility(
                    visible = diagnosticsExpanded,
                    enter = fadeIn(tween(300)) + expandVertically(
                        animationSpec = spring(dampingRatio = .9f, stiffness = 330f),
                    ),
                    exit = fadeOut(tween(180)) + shrinkVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f),
                    ),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.lastError?.let {
                            Text(tr("Ошибка приложения: %s", it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        state.diagnostics?.let { diagnostics ->
                            diagnostics.readErrors.forEach { error ->
                                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                scanSummary(diagnostics),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (repairCandidates.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            tr("Если у календаря «событий: 0», Android не сохранил его записи. Нажмите кнопку ниже — приложение включит календарь и запросит синхронизацию."),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        FilledTonalButton(
                                            onClick = if (permissions.calendarWrite) onSync else onRequestCalendar,
                                            enabled = !state.syncing,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                tr(
                                                    if (permissions.calendarWrite) "Исправить и синхронизировать"
                                                    else "Разрешить управление календарями",
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                            Text(tr("Календари Android"), fontWeight = FontWeight.Bold)
                            state.calendars
                                .groupBy { calendar -> calendar.displayName.trim() to calendar.accountName.trim() }
                                .values
                                .forEach { calendars ->
                                    CalendarDiagnosticGroup(
                                        calendars = calendars,
                                        selectedCalendarIds = state.settings.selectedCalendarIds,
                                        eventCounts = diagnostics.calendarEventCounts,
                                    )
                                }
                            HorizontalDivider()
                            Text(tr("Android передал события"), fontWeight = FontWeight.Bold)
                            if (diagnostics.events.isEmpty()) {
                                Text(
                                    tr("Ни одной записи о событиях на ближайшие %d дней.", state.settings.lookAheadDays),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                diagnostics.events.take(30).forEach { event -> DiagnosticEventRow(event) }
                            }
                        } ?: Text(tr("Сначала нажмите «Проверить сейчас»"), style = MaterialTheme.typography.bodySmall)
                    }
                }
                    Button(onClick = onSync, enabled = permissions.calendar && !state.syncing, modifier = Modifier.fillMaxWidth()) {
                        if (state.syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Проверить сейчас"))
                    }
                }
            }
        }
        item {
            SettingsCard(Icons.Rounded.SystemUpdate, tr("Обновления"), tr("Через GitHub Releases")) {
                ToggleRow(
                    title = tr("Расширенный режим"),
                    subtitle = tr(
                        if (state.settings.advancedMode) {
                            "Показаны все тонкие настройки"
                        } else {
                            "Скрывает сложные настройки и оставляет основные"
                        },
                    ),
                    checked = state.settings.advancedMode,
                    onChecked = onAdvancedMode,
                )
                HorizontalDivider()
                Text(tr("Канал обновлений"), fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.settings.updateChannel == UpdateChannel.STABLE,
                        onClick = { onUpdateChannel(UpdateChannel.STABLE) },
                        label = { Text(tr("Стабильная версия")) },
                    )
                    FilterChip(
                        selected = state.settings.updateChannel == UpdateChannel.BETA,
                        onClick = { onUpdateChannel(UpdateChannel.BETA) },
                        label = { Text(tr("Бета-версии")) },
                    )
                }
                Text(
                    tr(
                        if (state.settings.updateChannel == UpdateChannel.BETA) {
                            "Новые функции раньше, но возможны ошибки"
                        } else {
                            "Только проверенные полные версии"
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToggleRow(
                    title = tr("Проверять автоматически"),
                    subtitle = tr("APK загрузится сам; установку подтверждает Android"),
                    checked = state.settings.automaticUpdates,
                    onChecked = onAutomaticUpdates,
                )
                FilledTonalButton(onClick = onCheckUpdates, enabled = state.githubConfigured, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.SystemUpdate, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr(if (state.githubConfigured) "Проверить обновления" else "Репозиторий не настроен"))
                }
                AnimatedVisibility(
                    visible = state.settings.advancedMode,
                    enter = fadeIn(tween(260)) + expandVertically(
                        animationSpec = spring(dampingRatio = .88f, stiffness = 360f),
                    ),
                    exit = fadeOut(tween(160)) + shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        HorizontalDivider()
                        SettingsActionRow(
                            title = tr("Все версии"),
                            subtitle = tr("Скачать стабильную или бета-версию из архива"),
                            onClick = {
                                releasesExpanded = !releasesExpanded
                                if (releasesExpanded && state.availableReleases.isEmpty()) onLoadReleaseCatalog()
                            },
                            expanded = releasesExpanded,
                        )
                        AnimatedVisibility(
                            visible = releasesExpanded,
                            enter = fadeIn(tween(260)) + expandVertically(
                                animationSpec = spring(dampingRatio = .88f, stiffness = 360f),
                            ),
                            exit = fadeOut(tween(160)) + shrinkVertically(),
                        ) {
                            val beta = state.settings.updateChannel == UpdateChannel.BETA
                            val releases = state.availableReleases.filter { it.prerelease == beta }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    tr(if (beta) "Архив бета-версий" else "Архив стабильных версий"),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    tr("Старую версию можно скачать, но Android не установит её поверх более новой."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                when {
                                    state.releaseCatalogLoading -> CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp),
                                        strokeWidth = 3.dp,
                                    )
                                    releases.isEmpty() -> {
                                        Text(
                                            tr("В выбранном канале пока нет версий с APK"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        OutlinedButton(
                                            onClick = onLoadReleaseCatalog,
                                            enabled = state.githubConfigured,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) { Text(tr("Обновить список")) }
                                    }
                                    else -> releases.take(20).forEach { release ->
                                        ReleaseDownloadRow(
                                            release = release,
                                            downloading = state.downloadingReleaseTag == release.tag,
                                            downloaded = release.tag in state.downloadedReleaseTags,
                                            downloadEnabled = state.downloadingReleaseTag == null,
                                            onDownload = { onDownloadRelease(release) },
                                            onInstall = { onInstallRelease(release) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tr("Экономно для батареи"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(tr("Одна плановая проверка в сутки и только нужные системные сигналы — без постоянного сервиса."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .8f))
                    }
                }
            }
        }
        item {
            Text(
                tr("Версия %s", BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var hex by rememberSaveable(initialColor) { mutableStateOf(formatColorHex(initialColor)) }
    val parsed = parseHexColor(hex)
    val preview by animateColorAsState(
        targetValue = Color(parsed ?: initialColor),
        animationSpec = tween(420),
        label = "customColorPreview",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Собственный цвет")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(tr("Введите HEX-код цвета"), style = MaterialTheme.typography.bodyMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .background(preview, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        parsed?.let(::formatColorHex) ?: tr("Например: #6558D3"),
                        color = if (preview.luminance() > .5f) Color(0xFF17151D) else Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }.take(6)
                        hex = "#${digits.uppercase()}"
                    },
                    singleLine = true,
                    isError = parsed == null,
                    label = { Text("HEX") },
                    supportingText = { if (parsed == null) Text(tr("Например: #6558D3")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text(tr("Применить"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } },
    )
}

@Composable
private fun LeadTimeDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var days by remember(initialMinutes) { mutableStateOf((initialMinutes / (24 * 60)).toString()) }
    var hours by remember(initialMinutes) { mutableStateOf(((initialMinutes / 60) % 24).toString()) }
    var minutes by remember(initialMinutes) { mutableStateOf((initialMinutes % 60).toString()) }
    val total = ((days.toIntOrNull() ?: 0).coerceIn(0, 14) * 24 * 60) +
        ((hours.toIntOrNull() ?: 0).coerceIn(0, 23) * 60) +
        (minutes.toIntOrNull() ?: 0).coerceIn(0, 59)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Опережение будильника")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Можно выбрать от момента начала до 14 суток заранее."), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DurationField(days, { days = it.filter(Char::isDigit).take(2) }, tr("Дни"), Modifier.weight(1f))
                    DurationField(hours, { hours = it.filter(Char::isDigit).take(2) }, tr("Часы"), Modifier.weight(1f))
                    DurationField(minutes, { minutes = it.filter(Char::isDigit).take(2) }, tr("Мин"), Modifier.weight(1f))
                }
                Text(tr("Итого: %s", formatLead(total)), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(total) }) { Text(tr("Сохранить")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } },
    )
}

@Composable
private fun DurationField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun MinuteChoiceChips(current: Int, choices: List<Int>, onChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { minutes ->
            FilterChip(
                selected = current == minutes,
                onClick = { onChange(minutes) },
                label = { Text(tr("%d мин", minutes)) },
            )
        }
    }
}

@Composable
private fun ReleaseDownloadRow(
    release: AvailableRelease,
    downloading: Boolean,
    downloaded: Boolean,
    downloadEnabled: Boolean,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(release.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    tr(
                        when (release.relation) {
                            ReleaseRelation.NEWER -> "Версия %s · новее установленной"
                            ReleaseRelation.CURRENT -> "Версия %s · установлена"
                            ReleaseRelation.OLDER -> "Версия %s · старая"
                        },
                        release.version,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (release.relation == ReleaseRelation.NEWER) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(
                onClick = if (downloaded) onInstall else onDownload,
                enabled = downloadEnabled,
            ) {
                if (downloading) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                } else {
                    Text(tr(if (downloaded) "Установить" else "Скачать"))
                }
            }
        }
    }
}

@Composable
private fun SignalEffectsEditor(
    effects: SignalEffects,
    vibrationEnabled: Boolean,
    cameraGranted: Boolean,
    onEffectsChange: (SignalEffects) -> Unit,
    onVibrationEnabled: (Boolean) -> Unit,
    onRequestCamera: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToggleRow(
            title = tr("Высокая яркость экрана"),
            subtitle = tr("На время сигнала экран будет максимально ярким"),
            checked = effects.highBrightnessEnabled,
            onChecked = { onEffectsChange(effects.copy(highBrightnessEnabled = it)) },
        )
        ToggleRow(
            title = tr("Фонарик"),
            subtitle = tr("Использовать заднюю вспышку телефона"),
            checked = effects.torchEnabled,
            onChecked = { enabled ->
                onEffectsChange(effects.copy(torchEnabled = enabled))
                if (enabled && !cameraGranted) onRequestCamera()
            },
        )
        AnimatedVisibility(
            visible = effects.torchEnabled,
            enter = fadeIn(tween(240)) + expandVertically(),
            exit = fadeOut(tween(160)) + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!cameraGranted) {
                    FilledTonalButton(onClick = onRequestCamera, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Разрешить доступ к фонарику"))
                    }
                }
                Text(tr("Режим фонарика"), fontWeight = FontWeight.Medium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TorchMode.entries.forEach { mode ->
                        FilterChip(
                            selected = effects.torchMode == mode,
                            onClick = { onEffectsChange(effects.copy(torchMode = mode)) },
                            label = { Text(tr(if (mode == TorchMode.STEADY) "Светить постоянно" else "Моргать")) },
                        )
                    }
                }
                if (effects.torchMode == TorchMode.BLINK) {
                    Text(tr("Скорость мигания"), fontWeight = FontWeight.Medium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(200, 350, 500, 1_000).forEach { millis ->
                            FilterChip(
                                selected = effects.torchBlinkMillis == millis,
                                onClick = { onEffectsChange(effects.copy(torchBlinkMillis = millis)) },
                                label = { Text(tr("%d мс", millis)) },
                            )
                        }
                    }
                    Text(tr("Количество миганий"), fontWeight = FontWeight.Medium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 10, 20, 50).forEach { count ->
                            FilterChip(
                                selected = effects.torchRepeatCount == count,
                                onClick = { onEffectsChange(effects.copy(torchRepeatCount = count)) },
                                label = { Text(count.toString()) },
                            )
                        }
                    }
                }
            }
        }
        ToggleRow(
            title = tr("Вибрация"),
            subtitle = tr("Настраиваемая сила вибросигнала"),
            checked = vibrationEnabled,
            onChecked = onVibrationEnabled,
        )
        AnimatedVisibility(
            visible = vibrationEnabled,
            enter = fadeIn(tween(240)) + expandVertically(),
            exit = fadeOut(tween(160)) + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Сила вибрации"), fontWeight = FontWeight.Medium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(25, 50, 75, 100).forEach { intensity ->
                        FilterChip(
                            selected = effects.vibrationIntensity == intensity,
                            onClick = { onEffectsChange(effects.copy(vibrationIntensity = intensity)) },
                            label = { Text("$intensity%") },
                        )
                    }
                }
            }
        }
    }
}

private fun signalEffectsSummary(effects: SignalEffects, vibrationEnabled: Boolean): String {
    val enabled = buildList {
        if (effects.highBrightnessEnabled) add(tr("яркий экран"))
        if (effects.torchEnabled) add(tr(if (effects.torchMode == TorchMode.BLINK) "мигающий фонарик" else "фонарик"))
        if (vibrationEnabled) add(tr("вибрация %d%%", effects.vibrationIntensity))
    }
    return enabled.joinToString(" · ").ifBlank { tr("Без дополнительных эффектов") }
}

@Composable
private fun SettingsCard(icon: ImageVector, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = .82f, stiffness = 430f),
        label = "settingsSectionArrow",
    )
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = tr(if (expanded) "Свернуть" else "Развернуть"),
                    modifier = Modifier.rotate(arrowRotation),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(260)) + expandVertically(
                    animationSpec = spring(dampingRatio = .88f, stiffness = 360f),
                ),
                exit = fadeOut(tween(150)) + shrinkVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 460f),
                ),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun CalendarToggle(calendar: CalendarInfo, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(Color(calendar.color), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(calendar.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    !calendar.syncEvents -> "${if (calendar.isShared) "${tr("Общий")} · " else ""}${calendar.accountName} · ${tr("синхронизация выключена")}"
                    !calendar.visible -> "${if (calendar.isShared) "${tr("Общий")} · " else ""}${calendar.accountName} · ${tr("календарь скрыт")}"
                    calendar.isShared -> "${tr("Общий")} · ${tr("владелец %s", calendar.ownerAccount)}"
                    else -> calendar.accountName
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (calendar.syncEvents) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = { onClick() })
    }
}

@Composable
private fun CalendarDiagnosticGroup(
    calendars: List<CalendarInfo>,
    selectedCalendarIds: Set<Long>,
    eventCounts: Map<Long, Int>,
) {
    val first = calendars.firstOrNull() ?: return
    val selected = calendars.count { selectedCalendarIds.isEmpty() || it.id in selectedCalendarIds }
    val synced = calendars.count {
        (selectedCalendarIds.isEmpty() || it.id in selectedCalendarIds) && it.syncEvents
    }
    val events = calendars.sumOf { eventCounts[it.id] ?: 0 }
    val warning = events == 0 && calendars.any {
        (selectedCalendarIds.isEmpty() || it.id in selectedCalendarIds) && !it.syncEvents
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            first.displayName,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (first.accountName.isNotBlank() && !first.displayName.equals(first.accountName, ignoreCase = true)) {
            Text(
                first.accountName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            tr("Копий: %d · выбрано: %d · синхр.: %d · событий: %d", calendars.size, selected, synced, events),
            style = MaterialTheme.typography.bodySmall,
            color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (warning) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DiagnosticEventRow(event: EventDiagnostic) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(event.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${formatDayShort(event.startMillis)}, ${if (event.allDay) "весь день" else formatTime(event.startMillis)} · ${event.calendarName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            diagnosticDecision(event),
            style = MaterialTheme.typography.labelMedium,
            color = if (event.decision in setOf(EventDecision.ALARM_CREATED, EventDecision.REMINDER_CREATED)) {
                Mint
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    expanded: Boolean? = null,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded == true) 90f else 0f,
        animationSpec = spring(dampingRatio = .82f, stiffness = 390f),
        label = "settingsArrow",
    )
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Rounded.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.rotate(arrowRotation),
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun formatLead(minutes: Int): String {
    if (minutes == 0) return tr("В момент начала")
    val days = minutes / (24 * 60)
    val hours = (minutes / 60) % 24
    val restMinutes = minutes % 60
    return buildList {
        if (days > 0) add(tr("%d дн", days))
        if (hours > 0) add(tr("%d ч", hours))
        if (restMinutes > 0) add(tr("%d мин", restMinutes))
    }.joinToString(" ")
}

private fun accentName(accent: AccentTheme): String = tr(
    when (accent) {
        AccentTheme.VIOLET -> "Фиолетовый"
        AccentTheme.BLUE -> "Синий"
        AccentTheme.GREEN -> "Зелёный"
        AccentTheme.ORANGE -> "Оранжевый"
        AccentTheme.ROSE -> "Розовый"
        AccentTheme.TEAL -> "Бирюзовый"
        AccentTheme.RED -> "Красный"
        AccentTheme.AMBER -> "Янтарный"
        AccentTheme.LIME -> "Лаймовый"
        AccentTheme.CYAN -> "Голубой"
        AccentTheme.INDIGO -> "Индиго"
        AccentTheme.GRAPHITE -> "Графитовый"
        AccentTheme.CUSTOM -> "Свой цвет"
    },
)

private fun backgroundStyleName(style: BackgroundStyle): String = tr(
    when (style) {
        BackgroundStyle.STANDARD -> "Обычный"
        BackgroundStyle.TINTED -> "С оттенком"
        BackgroundStyle.AMOLED -> "Чёрный OLED"
    },
)

private fun formatColorHex(color: Int): String = "#%06X".format(color and 0x00FFFFFF)

private fun parseHexColor(value: String): Int? {
    val digits = value.removePrefix("#")
    if (digits.length != 6 || digits.any { !it.isDigit() && it.uppercaseChar() !in 'A'..'F' }) return null
    return digits.toLongOrNull(16)?.toInt()?.or(0xFF000000.toInt())
}

private fun diagnosticsText(diagnostics: SyncDiagnostics?): String = when {
    diagnostics == null -> tr("Нажмите обновить, чтобы проверить события")
    diagnostics.totalInstances == 0 && diagnostics.unsyncedCalendars > 0 ->
        tr("Android не вернул событий. Проверьте календари с выключенной синхронизацией в настройках")
    diagnostics.totalInstances == 0 -> tr("Android не вернул событий на ближайшие %d дней", diagnostics.lookAheadDays)
    diagnostics.excludedAllDay == diagnostics.totalInstances -> tr("Найдены только события на весь день")
    diagnostics.excludedCalendar > 0 -> tr("События есть, но их календари отключены в настройках")
    diagnostics.excludedDay > 0 -> tr("События есть, но нужные дни недели отключены")
    diagnostics.excludedCutoff > 0 -> tr("События начинаются позже заданной метки")
    diagnostics.excludedPastAlarm > 0 -> tr("Время этих будильников уже прошло")
    diagnostics.excludedUserSkipped > 0 -> tr("События пропущены пользователем")
    else -> tr("Проверьте фильтры календарей, дней и времени в настройках")
}

private fun scanSummary(diagnostics: SyncDiagnostics): String = tr(
    "Найдено: %d · подходящих: %d · Instances: %d · Events: %d · без синхронизации: %d · скрыто: %d",
    diagnostics.totalInstances,
    diagnostics.usableTimedEvents,
    diagnostics.instanceRows,
    diagnostics.directEventRows,
    diagnostics.unsyncedCalendars,
    diagnostics.hiddenCalendars,
)

private fun diagnosticDecision(event: EventDiagnostic): String {
    val decision = when (event.decision) {
        EventDecision.ALARM_CREATED -> tr("Будильник создан")
        EventDecision.REMINDER_CREATED -> tr("Тихое напоминание создано")
        EventDecision.ALL_DAY -> tr("Пропущено: событие на весь день")
        EventDecision.CANCELED -> tr("Пропущено: событие отменено")
        EventDecision.DECLINED -> tr("Пропущено: приглашение отклонено")
        EventDecision.CALENDAR_DISABLED -> tr("Пропущено: календарь отключён в настройках")
        EventDecision.DAY_DISABLED -> tr("Пропущено: день недели отключён")
        EventDecision.AFTER_CUTOFF -> tr("Пропущено: позже временной метки")
        EventDecision.ALARM_PASSED -> tr("Пропущено: время будильника уже прошло")
        EventDecision.USER_SKIPPED -> tr("Пропущено пользователем")
        EventDecision.EXTRA_SAME_DAY -> tr("Пропущено: на этот день уже выбран более ранний будильник")
    }
    return if (event.source == EventSource.EVENTS) "$decision · ${tr("найдено резервным запросом")}" else decision
}

private fun formatClockMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

private fun ScheduledAlarm.eventDate(): LocalDate = Instant.ofEpochMilli(eventStartMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()

private fun formatDayLong(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DAY_LONG_FORMAT)
    .replaceFirstChar { it.uppercase() }

private fun formatDayShort(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).let { dateTime ->
    when (dateTime.toLocalDate()) {
        LocalDate.now() -> tr("Сегодня")
        LocalDate.now().plusDays(1) -> tr("Завтра")
        else -> dateTime.format(DAY_SHORT_FORMAT).replaceFirstChar { it.uppercase() }
    }
}

private fun formatLastSync(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(LAST_SYNC_FORMAT)

private fun timeUntil(millis: Long): String {
    val duration = Duration.between(Instant.now(), Instant.ofEpochMilli(millis))
    val hours = duration.toHours().coerceAtLeast(0)
    return when {
        hours >= 24 -> tr("через %d дн.", hours / 24)
        hours > 0 -> tr("через %d ч", hours)
        else -> tr("скоро")
    }
}

private val APP_LOCALE = appLocale()
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", APP_LOCALE)
private val DAY_LONG_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM", APP_LOCALE)
private val DAY_SHORT_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM", APP_LOCALE)
private val LAST_SYNC_FORMAT = DateTimeFormatter.ofPattern("d MMM, HH:mm", APP_LOCALE)
