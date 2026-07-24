package com.vovremya.alarm.data

import androidx.annotation.ColorInt
import java.time.DayOfWeek
import java.time.LocalDate

data class AppSettings(
    val advancedMode: Boolean = false,
    val leadMinutes: Int = 90,
    val latestEventMinutes: Int = 23 * 60 + 59,
    val latestEventEnabled: Boolean = false,
    val dailySyncMinutes: Int = 19 * 60,
    val lookAheadDays: Int = 21,
    val allEventsPerDay: Boolean = true,
    val includeAllDayEvents: Boolean = false,
    val allDayEventMinutes: Int = 9 * 60,
    val alarmSoundEnabled: Boolean = true,
    val alarmVibrationEnabled: Boolean = true,
    val reminderVibrationEnabled: Boolean = false,
    val alarmEffects: SignalEffects = SignalEffects(),
    val reminderEffects: SignalEffects = SignalEffects(vibrationIntensity = 60),
    val quickDismiss: QuickDismissSettings = QuickDismissSettings(),
    val alarmSoundUri: String = "",
    val snoozeMinutes: Int = 10,
    val autoSilenceMinutes: Int = 10,
    val enabledDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    /** An empty set means every calendar available through Android. */
    val selectedCalendarIds: Set<Long> = emptySet(),
    val automaticUpdates: Boolean = true,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val skippedEventKeys: Set<String> = emptySet(),
    val skippedDates: Set<LocalDate> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentTheme: AccentTheme = AccentTheme.VIOLET,
    @param:ColorInt val customAccentColor: Int = 0xFF6558D3.toInt(),
    val backgroundStyle: BackgroundStyle = BackgroundStyle.STANDARD,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class UpdateChannel { STABLE, BETA }

enum class TorchMode { STEADY, BLINK }

enum class QuickDismissMode { BUTTON, TAP_ANYWHERE }

data class SignalEffects(
    val highBrightnessEnabled: Boolean = false,
    val torchEnabled: Boolean = false,
    val torchMode: TorchMode = TorchMode.BLINK,
    val torchBlinkMillis: Int = 500,
    val torchRepeatCount: Int = 10,
    val vibrationIntensity: Int = 100,
)

data class QuickDismissSettings(
    val enabled: Boolean = true,
    val afterMinutes: Int = 10 * 60,
    val mode: QuickDismissMode = QuickDismissMode.BUTTON,
)

enum class AccentTheme {
    VIOLET,
    BLUE,
    GREEN,
    ORANGE,
    ROSE,
    TEAL,
    RED,
    AMBER,
    LIME,
    CYAN,
    INDIGO,
    GRAPHITE,
    CUSTOM,
}

enum class BackgroundStyle { STANDARD, TINTED, AMOLED }

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    @param:ColorInt val color: Int,
    val accountType: String = "",
    val syncEvents: Boolean = true,
    val visible: Boolean = true,
    val ownerAccount: String = "",
    val accessLevel: Int = 0,
    val isPrimary: Boolean = false,
) {
    val isShared: Boolean
        get() = ownerAccount.isNotBlank() &&
            accountName.isNotBlank() &&
            !ownerAccount.equals(accountName, ignoreCase = true)
}

enum class EventSource { INSTANCES, EVENTS }

enum class EventDecision {
    ALARM_CREATED,
    REMINDER_CREATED,
    ALL_DAY,
    CANCELED,
    DECLINED,
    CALENDAR_DISABLED,
    DAY_DISABLED,
    AFTER_CUTOFF,
    ALARM_PASSED,
    USER_SKIPPED,
    EXTRA_SAME_DAY,
}

enum class AlarmDelivery { ALARM, SILENT_REMINDER }

data class CalendarEvent(
    val eventId: Long,
    val instanceStartMillis: Long,
    val title: String,
    val location: String?,
    val calendarId: Long,
    val calendarName: String,
    @param:ColorInt val calendarColor: Int,
    val allDay: Boolean = false,
    val source: EventSource = EventSource.INSTANCES,
)

data class EventDiagnostic(
    val eventId: Long,
    val startMillis: Long,
    val title: String,
    val calendarName: String,
    val allDay: Boolean = false,
    val source: EventSource,
    val decision: EventDecision,
)

data class ScheduledAlarm(
    val key: String,
    val eventId: Long,
    val eventStartMillis: Long,
    val alarmAtMillis: Long,
    val title: String,
    val location: String?,
    val calendarId: Long,
    val calendarName: String,
    @param:ColorInt val calendarColor: Int,
    val allDay: Boolean = false,
    val delivery: AlarmDelivery = AlarmDelivery.ALARM,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val effects: SignalEffects = SignalEffects(),
    val quickDismiss: QuickDismissSettings = QuickDismissSettings(),
    val soundUri: String = "",
    val snoozeMinutes: Int = 10,
    val autoSilenceMinutes: Int = 10,
)

data class SyncResult(
    val alarms: List<ScheduledAlarm>,
    val exact: Boolean,
    val diagnostics: SyncDiagnostics = SyncDiagnostics(),
)

data class CalendarScanResult(
    val events: List<CalendarEvent> = emptyList(),
    val totalInstances: Int = 0,
    val excludedAllDay: Int = 0,
    val excludedCanceled: Int = 0,
    val excludedDeclined: Int = 0,
    val instanceRows: Int = 0,
    val directEventRows: Int = 0,
    val eventDiagnostics: List<EventDiagnostic> = emptyList(),
    val readErrors: List<String> = emptyList(),
)

data class CalendarSyncRepairResult(
    val targetedCalendars: Int = 0,
    val updatedCalendars: Int = 0,
    val failedCalendars: Int = 0,
    val requestedAccounts: Int = 0,
    val writePermissionMissing: Boolean = false,
)

data class SyncDiagnostics(
    val lookAheadDays: Int = 21,
    val totalInstances: Int = 0,
    val usableTimedEvents: Int = 0,
    val excludedAllDay: Int = 0,
    val excludedCanceled: Int = 0,
    val excludedDeclined: Int = 0,
    val excludedCalendar: Int = 0,
    val excludedDay: Int = 0,
    val excludedCutoff: Int = 0,
    val excludedPastAlarm: Int = 0,
    val excludedUserSkipped: Int = 0,
    val extraSameDay: Int = 0,
    val unsyncedCalendars: Int = 0,
    val hiddenCalendars: Int = 0,
    val instanceRows: Int = 0,
    val directEventRows: Int = 0,
    val calendarEventCounts: Map<Long, Int> = emptyMap(),
    val events: List<EventDiagnostic> = emptyList(),
    val readErrors: List<String> = emptyList(),
)
