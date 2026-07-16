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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vovremya.alarm.BuildConfig
import com.vovremya.alarm.data.CalendarInfo
import com.vovremya.alarm.data.AccentTheme
import com.vovremya.alarm.data.EventDecision
import com.vovremya.alarm.data.EventDiagnostic
import com.vovremya.alarm.data.EventSource
import com.vovremya.alarm.data.ScheduledAlarm
import com.vovremya.alarm.data.SyncDiagnostics
import com.vovremya.alarm.data.ThemeMode
import com.vovremya.alarm.localization.appLocale
import com.vovremya.alarm.localization.tr
import com.vovremya.alarm.ui.theme.Mint
import com.vovremya.alarm.ui.theme.Violet
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.launch

data class PermissionState(
    val calendar: Boolean,
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
) {
    val allGranted: Boolean get() = calendar && notifications && exactAlarms && fullScreen
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
    onPickAlarmSound: () -> Unit,
    onSnoozeMinutes: (Int) -> Unit,
    onAutoSilenceMinutes: (Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onToggleCalendar: (Long) -> Unit,
    onSelectAllCalendars: () -> Unit,
    onAutomaticUpdates: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAccentTheme: (AccentTheme) -> Unit,
    onCheckUpdates: () -> Unit,
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
                    (slideInHorizontally(tween(340)) { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally(tween(340)) { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally(tween(280)) { it / 4 } + fadeOut())
                }.using(SizeTransform(clip = false))
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
                    onPickAlarmSound = onPickAlarmSound,
                    onSnoozeMinutes = onSnoozeMinutes,
                    onAutoSilenceMinutes = onAutoSilenceMinutes,
                    onToggleDay = onToggleDay,
                    onToggleCalendar = onToggleCalendar,
                    onSelectAllCalendars = onSelectAllCalendars,
                    onAutomaticUpdates = onAutomaticUpdates,
                    onThemeMode = onThemeMode,
                    onAccentTheme = onAccentTheme,
                    onCheckUpdates = onCheckUpdates,
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
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HomeHeader(onSettings) }
        item {
            AnimatedVisibility(!permissions.allGranted) {
                PermissionCard(
                    permissions,
                    onRequestCalendar,
                    onRequestNotifications,
                    onRequestExactAlarms,
                    onRequestFullScreen,
                )
            }
        }
        item { NextAlarmCard(state.alarms.firstOrNull()) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Ближайшие"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        tr(if (state.settings.allEventsPerDay) "Все подходящие события" else "По одному событию на день"),
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
        }
        if (state.alarms.isEmpty()) {
            item { EmptyAlarms(permissions.calendar, state.diagnostics) }
        } else {
            items(state.alarms, key = ScheduledAlarm::key) { alarm -> AlarmRow(alarm) }
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
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", APP_LOCALE)).replaceFirstChar { it.uppercase() },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            PermissionRow(tr("Доступ к календарю"), permissions.calendar, onCalendar)
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
private fun NextAlarmCard(alarm: ScheduledAlarm?) {
    val primary = MaterialTheme.colorScheme.primary
    val start = listOf(primary, lerp(primary, Color.Black, .38f))
    val cardText = MaterialTheme.colorScheme.onPrimary
    Card(
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(tween(350)),
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(start)).padding(24.dp)) {
            if (alarm == null) {
                Column(Modifier.padding(vertical = 18.dp)) {
                    Icon(Icons.Rounded.EventAvailable, null, tint = Color(0xFFD8D3FF), modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(18.dp))
                    Text(tr("Всё спокойно"), color = cardText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(tr("Следующий будильник появится после проверки календаря"), color = cardText.copy(alpha = .82f))
                }
            } else {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Alarm, null, tint = Color(0xFFD8D3FF))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("СЛЕДУЮЩИЙ БУДИЛЬНИК"), color = cardText.copy(alpha = .82f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(formatTime(alarm.alarmAtMillis), color = cardText, fontSize = 58.sp, lineHeight = 62.sp, fontWeight = FontWeight.Light)
                    Text(formatDayLong(alarm.alarmAtMillis), color = cardText.copy(alpha = .9f), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(22.dp))
                    Surface(color = Color.White.copy(alpha = .13f), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(alarm.title, color = cardText, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(tr("Событие в %s · %s", formatTime(alarm.eventStartMillis), timeUntil(alarm.alarmAtMillis)), color = cardText.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmRow(alarm: ScheduledAlarm) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
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
    onPickAlarmSound: () -> Unit,
    onSnoozeMinutes: (Int) -> Unit,
    onAutoSilenceMinutes: (Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onToggleCalendar: (Long) -> Unit,
    onSelectAllCalendars: () -> Unit,
    onAutomaticUpdates: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAccentTheme: (AccentTheme) -> Unit,
    onCheckUpdates: () -> Unit,
    onSync: () -> Unit,
    onRequestCalendar: () -> Unit,
    onRequestExactAlarms: () -> Unit,
) {
    var showLeadDialog by rememberSaveable { mutableStateOf(false) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, tr("Назад")) }
                Spacer(Modifier.width(4.dp))
                Text(tr("Настройки"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            SettingsCard(Icons.Rounded.Palette, tr("Оформление"), tr("Цвет и режим интерфейса")) {
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
                Text(tr("Основной цвет"), fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentTheme.entries.forEach { accent ->
                        FilterChip(
                            selected = state.settings.accentTheme == accent,
                            onClick = { onAccentTheme(accent) },
                            label = {
                                Text(
                                    when (accent) {
                                        AccentTheme.VIOLET -> tr("Фиолетовый")
                                        AccentTheme.BLUE -> tr("Синий")
                                        AccentTheme.GREEN -> tr("Зелёный")
                                        AccentTheme.ORANGE -> tr("Оранжевый")
                                        AccentTheme.ROSE -> tr("Розовый")
                                        AccentTheme.TEAL -> tr("Бирюзовый")
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
        item {
            SettingsCard(Icons.Rounded.Timer, tr("Время"), tr("Точная настройка планирования")) {
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
                HorizontalDivider()
                ToggleRow(
                    title = tr("Ограничить поздние события"),
                    subtitle = if (state.settings.latestEventEnabled) tr("Не позже %s", formatClockMinutes(state.settings.latestEventMinutes)) else tr("Ограничение выключено"),
                    checked = state.settings.latestEventEnabled,
                    onChecked = onLatestEventEnabled,
                )
                AnimatedVisibility(state.settings.latestEventEnabled) {
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
        item {
            SettingsCard(Icons.Rounded.NotificationsActive, tr("Будильники"), tr("События, звук и повтор сигнала")) {
                ToggleRow(
                    title = tr("Все события за день"),
                    subtitle = tr(if (state.settings.allEventsPerDay) "Будильник для каждого подходящего события" else "Только самое раннее событие дня"),
                    checked = state.settings.allEventsPerDay,
                    onChecked = onAllEventsPerDay,
                )
                ToggleRow(
                    title = tr("События на весь день"),
                    subtitle = tr("Использовать для них выбранное условное время"),
                    checked = state.settings.includeAllDayEvents,
                    onChecked = onIncludeAllDayEvents,
                )
                AnimatedVisibility(state.settings.includeAllDayEvents) {
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
                HorizontalDivider()
                ToggleRow(
                    title = tr("Звук"),
                    subtitle = tr("Проигрывать выбранную мелодию"),
                    checked = state.settings.alarmSoundEnabled,
                    onChecked = onAlarmSoundEnabled,
                )
                AnimatedVisibility(state.settings.alarmSoundEnabled) {
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
                HorizontalDivider()
                Text(tr("Отложить сигнал"), fontWeight = FontWeight.Medium)
                MinuteChoiceChips(state.settings.snoozeMinutes, listOf(5, 10, 15, 30, 60), onSnoozeMinutes)
                Text(tr("Автоматически выключить звук"), fontWeight = FontWeight.Medium)
                MinuteChoiceChips(state.settings.autoSilenceMinutes, listOf(1, 5, 10, 15, 30), onAutoSilenceMinutes)
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
                    if (state.calendars.any { !it.syncEvents }) {
                        Text(
                            tr("У календарей без синхронизации Android может не хранить события. Включите их синхронизацию в Google Calendar; уже загруженные записи приложение проверит напрямую."),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.calendars.any { !it.visible }) {
                        Text(
                            tr("Скрытые календари тоже проверяются напрямую, но повторяющиеся события надёжнее читать после включения показа календаря."),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
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
                        state.lastError != null -> "Есть ошибка · нажмите, чтобы раскрыть"
                        state.diagnostics?.readErrors?.isNotEmpty() == true -> "Есть ошибки чтения · нажмите, чтобы раскрыть"
                        diagnosticsExpanded -> tr("Нажмите, чтобы скрыть")
                        else -> tr("Скрыто · нажмите, чтобы раскрыть")
                    },
                    onClick = { diagnosticsExpanded = !diagnosticsExpanded },
                )
                AnimatedVisibility(diagnosticsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.lastError?.let {
                            Text("Ошибка приложения: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                            Text("Календари Android", fontWeight = FontWeight.Bold)
                            state.calendars.forEach { calendar ->
                                val selected = state.settings.selectedCalendarIds.isEmpty() || calendar.id in state.settings.selectedCalendarIds
                                Text(
                                    "${calendar.displayName}: ${if (selected) "выбран" else "выключен"}, sync=${calendar.syncEvents}, visible=${calendar.visible}, shared=${calendar.isShared}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (calendar.syncEvents) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                )
                            }
                            HorizontalDivider()
                            Text("Что вернул Android", fontWeight = FontWeight.Bold)
                            if (diagnostics.events.isEmpty()) {
                                Text(
                                    "Ни одной записи о событиях на ближайшие ${state.settings.lookAheadDays} дней.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                diagnostics.events.take(30).forEach { event -> DiagnosticEventRow(event) }
                            }
                        } ?: Text("Сначала нажмите «Проверить сейчас»", style = MaterialTheme.typography.bodySmall)
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
        item {
            SettingsCard(Icons.Rounded.SystemUpdate, tr("Обновления"), tr("Через GitHub Releases")) {
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
private fun SettingsCard(icon: ImageVector, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
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
                color = if (calendar.syncEvents && calendar.visible) {
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
            color = if (event.decision == EventDecision.ALARM_CREATED) Mint else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SettingsActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
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

private fun diagnosticsText(diagnostics: SyncDiagnostics?): String = when {
    diagnostics == null -> tr("Нажмите обновить, чтобы проверить события")
    diagnostics.totalInstances == 0 && diagnostics.unsyncedCalendars > 0 ->
        tr("Android не вернул событий. Проверьте календари с выключенной синхронизацией в настройках")
    diagnostics.totalInstances == 0 && diagnostics.hiddenCalendars > 0 ->
        tr("Android не вернул событий. Проверьте скрытые календари в настройках")
    diagnostics.totalInstances == 0 -> tr("Android не вернул событий на ближайшие %d дней", diagnostics.lookAheadDays)
    diagnostics.excludedAllDay == diagnostics.totalInstances -> tr("Найдены только события на весь день")
    diagnostics.excludedCalendar > 0 -> tr("События есть, но их календари отключены в настройках")
    diagnostics.excludedDay > 0 -> tr("События есть, но нужные дни недели отключены")
    diagnostics.excludedCutoff > 0 -> tr("События начинаются позже заданной метки")
    diagnostics.excludedPastAlarm > 0 -> tr("Время этих будильников уже прошло")
    else -> tr("Проверьте фильтры календарей, дней и времени в настройках")
}

private fun scanSummary(diagnostics: SyncDiagnostics): String = buildString {
    append("Последняя проверка: найдено ${diagnostics.totalInstances}, подходящих по типу ${diagnostics.usableTimedEvents}")
    append(". Instances: ${diagnostics.instanceRows}")
    if (diagnostics.directEventRows > 0) append(", прямой запрос Events: ${diagnostics.directEventRows}")
    if (diagnostics.unsyncedCalendars > 0) {
        append(". Без синхронизации: ${diagnostics.unsyncedCalendars}")
    }
    if (diagnostics.hiddenCalendars > 0) append(". Скрыто: ${diagnostics.hiddenCalendars}")
}

private fun diagnosticDecision(event: EventDiagnostic): String {
    val decision = when (event.decision) {
        EventDecision.ALARM_CREATED -> tr("Будильник создан")
        EventDecision.ALL_DAY -> tr("Пропущено: событие на весь день")
        EventDecision.CANCELED -> tr("Пропущено: событие отменено")
        EventDecision.DECLINED -> tr("Пропущено: приглашение отклонено")
        EventDecision.CALENDAR_DISABLED -> tr("Пропущено: календарь отключён в настройках")
        EventDecision.DAY_DISABLED -> tr("Пропущено: день недели отключён")
        EventDecision.AFTER_CUTOFF -> tr("Пропущено: позже временной метки")
        EventDecision.ALARM_PASSED -> tr("Пропущено: время будильника уже прошло")
        EventDecision.EXTRA_SAME_DAY -> tr("Пропущено: на этот день уже выбран более ранний будильник")
    }
    return if (event.source == EventSource.EVENTS) "$decision · ${tr("найдено резервным запросом")}" else decision
}

private fun formatClockMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

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
