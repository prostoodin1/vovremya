package com.vovremya.alarm.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vovremya.alarm.MainActivity
import com.vovremya.alarm.data.CalendarInfo
import com.vovremya.alarm.data.AlarmDelivery
import com.vovremya.alarm.data.CalendarEvent
import com.vovremya.alarm.data.CalendarRepository
import com.vovremya.alarm.data.ScheduledAlarm
import com.vovremya.alarm.data.SettingsStore
import com.vovremya.alarm.data.SyncDiagnostics
import com.vovremya.alarm.data.SyncResult
import com.vovremya.alarm.receivers.AlarmReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AlarmScheduler(
    private val context: Context,
    private val calendarRepository: CalendarRepository,
    private val settingsStore: SettingsStore,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val syncMutex = Mutex()

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    suspend fun syncFromCalendar(nowMillis: Long = System.currentTimeMillis()): SyncResult =
        syncMutex.withLock {
            val oldAlarms = settingsStore.scheduledAlarms.first()
            val calendars = calendarRepository.getCalendars()
            val storedSettings = settingsStore.settings.first()
            val scan = calendarRepository.scanUpcomingEvents(nowMillis, storedSettings.lookAheadDays.toLong())
            val currentCalendarIds = calendars.map(CalendarInfo::id).toSet()
            val normalizedCalendarIds = if (currentCalendarIds.isEmpty()) {
                storedSettings.selectedCalendarIds
            } else {
                storedSettings.selectedCalendarIds.intersect(currentCalendarIds)
            }
            if (storedSettings.selectedCalendarIds.isNotEmpty() && normalizedCalendarIds != storedSettings.selectedCalendarIds) {
                settingsStore.setSelectedCalendarIds(normalizedCalendarIds)
            }
            val settings = storedSettings.copy(selectedCalendarIds = normalizedCalendarIds)
            val selectedCalendars = calendars.filter { calendar ->
                normalizedCalendarIds.isEmpty() || calendar.id in normalizedCalendarIds
            }
            val selection = EventSelector.select(scan.events, settings, nowMillis)
            val newAlarms = selection.alarms

            oldAlarms.filter { old -> newAlarms.none { it.key == old.key } }.forEach(::cancel)
            newAlarms.forEach(::schedule)
            settingsStore.saveScheduledAlarms(newAlarms)
            SyncResult(
                alarms = newAlarms,
                exact = canScheduleExactAlarms(),
                diagnostics = SyncDiagnostics(
                    lookAheadDays = storedSettings.lookAheadDays,
                    totalInstances = scan.totalInstances,
                    usableTimedEvents = scan.events.size,
                    excludedAllDay = selection.excludedAllDay,
                    excludedCanceled = scan.excludedCanceled,
                    excludedDeclined = scan.excludedDeclined,
                    excludedCalendar = selection.excludedCalendar,
                    excludedDay = selection.excludedDay,
                    excludedCutoff = selection.excludedCutoff,
                    excludedPastAlarm = selection.excludedPastAlarm,
                    excludedUserSkipped = selection.excludedUserSkipped,
                    extraSameDay = selection.extraSameDay,
                    unsyncedCalendars = selectedCalendars.count { !it.syncEvents },
                    hiddenCalendars = selectedCalendars.count { !it.visible },
                    instanceRows = scan.instanceRows,
                    directEventRows = scan.directEventRows,
                    calendarEventCounts = scan.events.groupingBy(CalendarEvent::calendarId).eachCount(),
                    events = (scan.eventDiagnostics + selection.eventDiagnostics)
                        .sortedBy { it.startMillis }
                        .take(MAX_DIAGNOSTIC_EVENTS),
                    readErrors = scan.readErrors,
                ),
            )
        }

    suspend fun rescheduleSaved(nowMillis: Long = System.currentTimeMillis()) {
        settingsStore.scheduledAlarms.first()
            .filter { it.alarmAtMillis > nowMillis }
            .forEach(::schedule)
    }

    fun schedule(alarm: ScheduledAlarm) {
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            requestCode(alarm.key),
            Intent(context, AlarmReceiver::class.java).apply {
                putExtra(AlarmPayload.EXTRA_KEY, alarm.key)
                putExtra(AlarmPayload.EXTRA_TITLE, alarm.title)
                putExtra(AlarmPayload.EXTRA_EVENT_START, alarm.eventStartMillis)
                putExtra(AlarmPayload.EXTRA_ALARM_AT, alarm.alarmAtMillis)
                putExtra(AlarmPayload.EXTRA_LOCATION, alarm.location)
                putExtra(AlarmPayload.EXTRA_ALL_DAY, alarm.allDay)
                putExtra(AlarmPayload.EXTRA_DELIVERY, alarm.delivery.name)
                putExtra(AlarmPayload.EXTRA_SOUND_ENABLED, alarm.soundEnabled)
                putExtra(AlarmPayload.EXTRA_VIBRATION_ENABLED, alarm.vibrationEnabled)
                putExtra(AlarmPayload.EXTRA_SOUND_URI, alarm.soundUri)
                putExtra(AlarmPayload.EXTRA_SNOOZE_MINUTES, alarm.snoozeMinutes)
                putExtra(AlarmPayload.EXTRA_AUTO_SILENCE_MINUTES, alarm.autoSilenceMinutes)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (alarm.delivery == AlarmDelivery.SILENT_REMINDER) {
            scheduleReminder(alarm, alarmIntent)
        } else if (canScheduleExactAlarms()) {
            val showAppIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            try {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(alarm.alarmAtMillis, showAppIntent),
                    alarmIntent,
                )
            } catch (_: SecurityException) {
                scheduleInexact(alarm, alarmIntent)
            }
        } else {
            scheduleInexact(alarm, alarmIntent)
        }
    }

    private fun scheduleReminder(alarm: ScheduledAlarm, reminderIntent: PendingIntent) {
        if (!canScheduleExactAlarms()) {
            scheduleInexact(alarm, reminderIntent)
            return
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarm.alarmAtMillis,
                reminderIntent,
            )
        } catch (_: SecurityException) {
            scheduleInexact(alarm, reminderIntent)
        }
    }

    fun cancel(alarm: ScheduledAlarm) {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(alarm.key),
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun requestCode(key: String): Int = key.hashCode() and Int.MAX_VALUE

    private fun scheduleInexact(alarm: ScheduledAlarm, alarmIntent: PendingIntent) {
        // Graceful fallback until the user grants the special exact-alarm access.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            alarm.alarmAtMillis,
            alarmIntent,
        )
    }

    private companion object {
        const val MAX_DIAGNOSTIC_EVENTS = 20
    }
}
