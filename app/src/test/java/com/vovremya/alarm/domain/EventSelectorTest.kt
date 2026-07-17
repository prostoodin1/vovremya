package com.vovremya.alarm.domain

import com.vovremya.alarm.data.AppSettings
import com.vovremya.alarm.data.CalendarEvent
import com.vovremya.alarm.data.EventDecision
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
    fun `uses later event when first alarm time has already passed`() {
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
            listOf(EventDecision.ALARM_PASSED, EventDecision.ALARM_CREATED),
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

        assertEquals(listOf(5L), result.alarms.map { it.eventId })
        assertEquals(1, result.excludedCalendar)
        assertEquals(1, result.excludedDay)
        assertEquals(1, result.excludedCutoff)
        assertEquals(1, result.excludedPastAlarm)
        assertEquals(1, result.extraSameDay)
    }

    @Test
    fun `creates alarms for every event when all events mode is enabled`() {
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
                allEventsPerDay = false,
                skippedEventKeys = setOf("1:$firstStart"),
            ),
            now,
            zone,
        )

        assertEquals(listOf(2L), result.alarms.map { it.eventId })
        assertEquals(1, result.excludedUserSkipped)
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
