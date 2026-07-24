package com.vovremya.alarm.domain

import com.vovremya.alarm.data.AppSettings
import com.vovremya.alarm.data.AlarmDelivery
import com.vovremya.alarm.data.CalendarEvent
import com.vovremya.alarm.data.EventDecision
import com.vovremya.alarm.data.QuickDismissMode
import com.vovremya.alarm.data.QuickDismissSettings
import com.vovremya.alarm.data.SignalEffects
import com.vovremya.alarm.data.TorchMode
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class EventSelectorTest {
    private val zone = ZoneId.of("Europe/Paris")

    @Test
    fun `chooses first allowed event and subtracts lead time`() {
        val now = time(2026, 7, 14, 18, 0)
        val events = listOf(
            event(2, time(2026, 7, 15, 11, 0)),
            event(1, time(2026, 7, 15, 9, 0)),
        )

        val alarms = EventSelector.oneAlarmPerDay(events, AppSettings(leadMinutes = 90), now, zone)

        assertEquals(1, alarms.size)
        assertEquals(1, alarms.single().eventId)
        assertEquals(time(2026, 7, 15, 7, 30), alarms.single().alarmAtMillis)
    }

    @Test
    fun `respects selected days calendars and latest time`() {
        val now = time(2026, 7, 14, 10, 0)
        val settings = AppSettings(
            latestEventMinutes = 17 * 60,
            latestEventEnabled = true,
            enabledDays = setOf(DayOfWeek.WEDNESDAY),
            selectedCalendarIds = setOf(10),
        )
        val events = listOf(
            event(1, time(2026, 7, 15, 16, 0), calendarId = 10),
            event(2, time(2026, 7, 15, 18, 0), calendarId = 10),
            event(3, time(2026, 7, 16, 9, 0), calendarId = 10),
            event(4, time(2026, 7, 15, 8, 0), calendarId = 99),
        )

        val alarms = EventSelector.oneAlarmPerDay(events, settings, now, zone)

        assertEquals(listOf(1L), alarms.map { it.eventId })
    }

    @Test
    fun `keeps later event as reminder when first alarm time has already passed`() {
        val now = time(2026, 7, 15, 8, 0)
        val events = listOf(
            event(1, time(2026, 7, 15, 9, 0)),
            event(2, time(2026, 7, 15, 11, 0)),
        )

        val result = EventSelector.select(events, AppSettings(leadMinutes = 90), now, zone)

        assertEquals(listOf(2L), result.alarms.map { it.eventId })
        assertEquals(time(2026, 7, 15, 9, 30), result.alarms.single().alarmAtMillis)
        assertEquals(1, result.excludedPastAlarm)
        assertEquals(
            listOf(EventDecision.ALARM_PASSED, EventDecision.REMINDER_CREATED),
            result.eventDiagnostics.map { it.decision },
        )
    }

    @Test
    fun `reports why events were excluded`() {
        val now = time(2026, 7, 15, 10, 0)
        val settings = AppSettings(
            leadMinutes = 90,
            latestEventMinutes = 17 * 60,
            latestEventEnabled = true,
            allEventsPerDay = false,
            enabledDays = setOf(DayOfWeek.WEDNESDAY),
            selectedCalendarIds = setOf(10),
        )
        val events = listOf(
            event(1, time(2026, 7, 15, 9, 0), calendarId = 99),
            event(2, time(2026, 7, 16, 9, 0), calendarId = 10),
            event(3, time(2026, 7, 15, 18, 0), calendarId = 10),
            event(4, time(2026, 7, 15, 11, 0), calendarId = 10),
            event(5, time(2026, 7, 15, 12, 0), calendarId = 10),
            event(6, time(2026, 7, 15, 13, 0), calendarId = 10),
        )

        val result = EventSelector.select(events, settings, now, zone)

        assertEquals(emptyList<Long>(), result.alarms.map { it.eventId })
        assertEquals(1, result.excludedCalendar)
        assertEquals(1, result.excludedDay)
        assertEquals(1, result.excludedCutoff)
        assertEquals(1, result.excludedPastAlarm)
        assertEquals(2, result.extraSameDay)
    }

    @Test
    fun `creates one alarm and silent reminders for later events`() {
        val now = time(2026, 7, 14, 10, 0)
        val events = listOf(
            event(1, time(2026, 7, 15, 9, 0)),
            event(2, time(2026, 7, 15, 11, 0)),
        )

        val result = EventSelector.select(
            events,
            AppSettings(leadMinutes = 30, allEventsPerDay = true),
            now,
            zone,
        )

        assertEquals(listOf(1L, 2L), result.alarms.map { it.eventId })
        assertEquals(
            listOf(AlarmDelivery.ALARM, AlarmDelivery.SILENT_REMINDER),
            result.alarms.map { it.delivery },
        )
        assertEquals(listOf(true, false), result.alarms.map { it.soundEnabled })
        assertEquals(listOf(true, false), result.alarms.map { it.vibrationEnabled })
        assertEquals(0, result.extraSameDay)
    }

    @Test
    fun `later events use optional vibration without becoming alarms`() {
        val now = time(2026, 7, 14, 10, 0)
        val events = listOf(
            event(3, time(2026, 7, 15, 14, 0)),
            event(1, time(2026, 7, 15, 9, 0)),
            event(2, time(2026, 7, 15, 11, 0)),
        )

        val result = EventSelector.select(
            events,
            AppSettings(
                leadMinutes = 30,
                allEventsPerDay = true,
                reminderVibrationEnabled = true,
            ),
            now,
            zone,
        )

        assertEquals(1, result.alarms.count { it.delivery == AlarmDelivery.ALARM })
        assertEquals(2, result.alarms.count { it.delivery == AlarmDelivery.SILENT_REMINDER })
        assertEquals(listOf(true, true, true), result.alarms.map { it.vibrationEnabled })
        assertEquals(listOf(true, false, false), result.alarms.map { it.soundEnabled })
    }

    @Test
    fun `first and later events receive their own signal profiles`() {
        val now = time(2026, 7, 14, 10, 0)
        val alarmEffects = SignalEffects(
            highBrightnessEnabled = true,
            torchEnabled = true,
            torchMode = TorchMode.STEADY,
            vibrationIntensity = 90,
        )
        val reminderEffects = SignalEffects(
            torchEnabled = true,
            torchMode = TorchMode.BLINK,
            torchBlinkMillis = 200,
            torchRepeatCount = 20,
            vibrationIntensity = 35,
        )
        val quickDismiss = QuickDismissSettings(
            enabled = true,
            afterMinutes = 11 * 60,
            mode = QuickDismissMode.TAP_ANYWHERE,
        )

        val result = EventSelector.select(
            listOf(
                event(1, time(2026, 7, 15, 9, 0)),
                event(2, time(2026, 7, 15, 11, 0)),
            ),
            AppSettings(
                leadMinutes = 30,
                allEventsPerDay = true,
                alarmEffects = alarmEffects,
                reminderEffects = reminderEffects,
                quickDismiss = quickDismiss,
            ),
            now,
            zone,
        )

        assertEquals(alarmEffects, result.alarms[0].effects)
        assertEquals(reminderEffects, result.alarms[1].effects)
        assertEquals(listOf(quickDismiss, quickDismiss), result.alarms.map { it.quickDismiss })
    }

    @Test
    fun `past first alarm never promotes a later event to full alarm`() {
        val now = time(2026, 7, 15, 10, 0)
        val events = listOf(
            event(1, time(2026, 7, 15, 10, 15)),
            event(2, time(2026, 7, 15, 11, 0)),
        )

        val result = EventSelector.select(
            events,
            AppSettings(
                leadMinutes = 30,
                allEventsPerDay = true,
                reminderVibrationEnabled = true,
            ),
            now,
            zone,
        )

        assertEquals(listOf(2L), result.alarms.map { it.eventId })
        assertEquals(AlarmDelivery.SILENT_REMINDER, result.alarms.single().delivery)
        assertEquals(false, result.alarms.single().soundEnabled)
        assertEquals(true, result.alarms.single().vibrationEnabled)
        assertEquals(1, result.excludedPastAlarm)
        assertEquals(0, result.extraSameDay)
    }

    @Test
    fun `skipping the first event selects the next event of that day`() {
        val now = time(2026, 7, 14, 10, 0)
        val firstStart = time(2026, 7, 15, 9, 0)
        val events = listOf(
            event(1, firstStart),
            event(2, time(2026, 7, 15, 11, 0)),
        )

        val result = EventSelector.select(
            events,
            AppSettings(
                leadMinutes = 30,
                allEventsPerDay = true,
                skippedEventKeys = setOf("1:$firstStart"),
            ),
            now,
            zone,
        )

        assertEquals(listOf(2L), result.alarms.map { it.eventId })
        assertEquals(AlarmDelivery.ALARM, result.alarms.single().delivery)
        assertEquals(1, result.excludedUserSkipped)
    }

    @Test
    fun `each day starts with a full alarm`() {
        val now = time(2026, 7, 14, 10, 0)
        val events = listOf(
            event(1, time(2026, 7, 15, 9, 0)),
            event(2, time(2026, 7, 15, 11, 0)),
            event(3, time(2026, 7, 16, 8, 0)),
            event(4, time(2026, 7, 16, 10, 0)),
        )

        val result = EventSelector.select(
            events,
            AppSettings(leadMinutes = 30, allEventsPerDay = true),
            now,
            zone,
        )

        assertEquals(
            listOf(
                AlarmDelivery.ALARM,
                AlarmDelivery.SILENT_REMINDER,
                AlarmDelivery.ALARM,
                AlarmDelivery.SILENT_REMINDER,
            ),
            result.alarms.map { it.delivery },
        )
        assertEquals(
            listOf(
                EventDecision.ALARM_CREATED,
                EventDecision.REMINDER_CREATED,
                EventDecision.ALARM_CREATED,
                EventDecision.REMINDER_CREATED,
            ),
            result.eventDiagnostics.map { it.decision },
        )
    }

    @Test
    fun `skipping a date excludes every event on that date`() {
        val now = time(2026, 7, 14, 10, 0)
        val events = listOf(
            event(1, time(2026, 7, 15, 9, 0)),
            event(2, time(2026, 7, 15, 11, 0)),
            event(3, time(2026, 7, 16, 11, 0)),
        )

        val result = EventSelector.select(
            events,
            AppSettings(
                leadMinutes = 30,
                skippedDates = setOf(java.time.LocalDate.of(2026, 7, 15)),
            ),
            now,
            zone,
        )

        assertEquals(listOf(3L), result.alarms.map { it.eventId })
        assertEquals(2, result.excludedUserSkipped)
    }

    @Test
    fun `can schedule an all day event at its configured time`() {
        val now = time(2026, 7, 14, 10, 0)
        val allDayStart = ZonedDateTime.of(2026, 7, 15, 0, 0, 0, 0, java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val allDayEvent = event(1, allDayStart).copy(allDay = true)

        val result = EventSelector.select(
            listOf(allDayEvent),
            AppSettings(
                leadMinutes = 60,
                includeAllDayEvents = true,
                allDayEventMinutes = 9 * 60,
            ),
            now,
            zone,
        )

        assertEquals(time(2026, 7, 15, 8, 0), result.alarms.single().alarmAtMillis)
        assertEquals(true, result.alarms.single().allDay)
    }

    private fun event(id: Long, start: Long, calendarId: Long = 10) = CalendarEvent(
        eventId = id,
        instanceStartMillis = start,
        title = "Event $id",
        location = null,
        calendarId = calendarId,
        calendarName = "Main",
        calendarColor = 0,
    )

    private fun time(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
}
