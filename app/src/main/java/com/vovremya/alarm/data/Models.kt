package com.vovremya.alarm.data

import androidx.annotation.ColorInt
import java.time.DayOfWeek

data class AppSettings(
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
    val alarmSoundUri: String = "",
    val snoozeMinutes: Int = 10,
    val autoSilenceMinutes: Int = 10,
    val enabledDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    /** An empty set means every calendar available through Android. */
    val selectedCalendarIds: Set<Long> = emptySet(),
    val automaticUpdates: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentTheme: AccentTheme = AccentTheme.VIOLET,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AccentTheme { VIOLET, BLUE, GREEN, ORANGE, ROSE, TEAL }

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    @ColorInt val color: Int,
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
    ALL_DAY,
    CANCELED,
    DECLINED,
    CALENDAR_DISABLED,
    DAY_DISABLED,
    AFTER_CUTOFF,
    ALARM_PASSED,
    EXTRA_SAME_DAY,
}

data class CalendarEvent(
    val eventId: Long,
    val instanceStartMillis: Long,
    val title: String,
    val location: String?,
    val calendarId: Long,
    val calendarName: String,
    @ColorInt val calendarColor: Int,
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
    @ColorInt val calendarColor: Int,
    val allDay: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
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
    val extraSameDay: Int = 0,
    val unsyncedCalendars: Int = 0,
    val hiddenCalendars: Int = 0,
    val instanceRows: Int = 0,
    val directEventRows: Int = 0,
    val events: List<EventDiagnostic> = emptyList(),
    val readErrors: List<String> = emptyList(),
)
