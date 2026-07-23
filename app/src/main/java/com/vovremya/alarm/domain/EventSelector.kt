package com.vovremya.alarm.domain

import com.vovremya.alarm.data.AppSettings
import com.vovremya.alarm.data.AlarmDelivery
import com.vovremya.alarm.data.CalendarEvent
import com.vovremya.alarm.data.EventDecision
import com.vovremya.alarm.data.EventDiagnostic
import com.vovremya.alarm.data.ScheduledAlarm
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

object EventSelector {
    data class Result(
        val alarms: List<ScheduledAlarm>,
        val excludedAllDay: Int,
        val excludedCalendar: Int,
        val excludedDay: Int,
        val excludedCutoff: Int,
        val excludedPastAlarm: Int,
        val excludedUserSkipped: Int,
        val extraSameDay: Int,
        val eventDiagnostics: List<EventDiagnostic>,
    )

    fun oneAlarmPerDay(
        events: List<CalendarEvent>,
        settings: AppSettings,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<ScheduledAlarm> = select(
        events,
        settings.copy(allEventsPerDay = false),
        nowMillis,
        zoneId,
    ).alarms

    fun select(
        events: List<CalendarEvent>,
        settings: AppSettings,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Result {
        var excludedAllDay = 0
        var excludedCalendar = 0
        var excludedDay = 0
        var excludedCutoff = 0
        var excludedPastAlarm = 0
        var excludedUserSkipped = 0
        val diagnostics = mutableListOf<EventDiagnostic>()
        val eligible = buildList {
            events.forEach { event ->
                if (settings.selectedCalendarIds.isNotEmpty() && event.calendarId !in settings.selectedCalendarIds) {
                    excludedCalendar++
                    diagnostics += event.toDiagnostic(EventDecision.CALENDAR_DISABLED)
                    return@forEach
                }
                if (event.allDay && !settings.includeAllDayEvents) {
                    excludedAllDay++
                    diagnostics += event.toDiagnostic(EventDecision.ALL_DAY)
                    return@forEach
                }
                val start = if (event.allDay) {
                    val eventDate = Instant.ofEpochMilli(event.instanceStartMillis)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                    eventDate.atStartOfDay(zoneId).plusMinutes(settings.allDayEventMinutes.toLong())
                } else {
                    Instant.ofEpochMilli(event.instanceStartMillis).atZone(zoneId)
                }
                if (start.dayOfWeek !in settings.enabledDays) {
                    excludedDay++
                    diagnostics += event.toDiagnostic(EventDecision.DAY_DISABLED)
                    return@forEach
                }
                if (
                    !event.allDay &&
                    settings.latestEventEnabled &&
                    start.hour * 60 + start.minute > settings.latestEventMinutes
                ) {
                    excludedCutoff++
                    diagnostics += event.toDiagnostic(EventDecision.AFTER_CUTOFF)
                    return@forEach
                }
                val effectiveStartMillis = start.toInstant().toEpochMilli()
                val eventKey = "${event.eventId}:${event.instanceStartMillis}"
                if (eventKey in settings.skippedEventKeys || start.toLocalDate() in settings.skippedDates) {
                    excludedUserSkipped++
                    diagnostics += event.toDiagnostic(EventDecision.USER_SKIPPED)
                    return@forEach
                }
                val alarmAt = effectiveStartMillis - settings.leadMinutes * 60_000L
                add(EligibleEvent(event, start.toLocalDate(), effectiveStartMillis, alarmAt))
            }
        }
        val selected = eligible
            .groupBy(EligibleEvent::date)
            .values
            .flatMap { dayEvents ->
                val sorted = dayEvents.sortedBy(EligibleEvent::effectiveStartMillis)
                val included = if (settings.allEventsPerDay) sorted else sorted.take(1)
                included.mapIndexed { index, event ->
                    SelectedEvent(
                        eligible = event,
                        delivery = if (index == 0) AlarmDelivery.ALARM else AlarmDelivery.SILENT_REMINDER,
                    )
                }
            }
        val alarms = selected.filter { it.eligible.alarmAtMillis > nowMillis }.map { selection ->
            val selectedEvent = selection.eligible
            val event = selectedEvent.event
            val isAlarm = selection.delivery == AlarmDelivery.ALARM
            ScheduledAlarm(
                key = "${event.eventId}:${event.instanceStartMillis}",
                eventId = event.eventId,
                eventStartMillis = selectedEvent.effectiveStartMillis,
                alarmAtMillis = selectedEvent.alarmAtMillis,
                title = event.title,
                location = event.location,
                calendarId = event.calendarId,
                calendarName = event.calendarName,
                calendarColor = event.calendarColor,
                allDay = event.allDay,
                delivery = selection.delivery,
                soundEnabled = isAlarm && settings.alarmSoundEnabled,
                vibrationEnabled = if (isAlarm) {
                    settings.alarmVibrationEnabled
                } else {
                    settings.reminderVibrationEnabled
                },
                soundUri = settings.alarmSoundUri,
                snoozeMinutes = settings.snoozeMinutes,
                autoSilenceMinutes = settings.autoSilenceMinutes,
            )
        }.sortedBy(ScheduledAlarm::alarmAtMillis)
        val deliveryByKey = selected.associate { selection ->
            val event = selection.eligible.event
            (event.eventId to event.instanceStartMillis) to selection.delivery
        }
        eligible.forEach { eligibleEvent ->
            val event = eligibleEvent.event
            if (eligibleEvent.alarmAtMillis <= nowMillis) {
                excludedPastAlarm++
                diagnostics += event.toDiagnostic(EventDecision.ALARM_PASSED)
            } else {
                diagnostics += event.toDiagnostic(
                    when (deliveryByKey[event.eventId to event.instanceStartMillis]) {
                        AlarmDelivery.ALARM -> EventDecision.ALARM_CREATED
                        AlarmDelivery.SILENT_REMINDER -> EventDecision.REMINDER_CREATED
                        null -> EventDecision.EXTRA_SAME_DAY
                    },
                )
            }
        }
        return Result(
            alarms = alarms,
            excludedAllDay = excludedAllDay,
            excludedCalendar = excludedCalendar,
            excludedDay = excludedDay,
            excludedCutoff = excludedCutoff,
            excludedPastAlarm = excludedPastAlarm,
            excludedUserSkipped = excludedUserSkipped,
            extraSameDay = eligible.size - selected.size,
            eventDiagnostics = diagnostics,
        )
    }

    private data class EligibleEvent(
        val event: CalendarEvent,
        val date: java.time.LocalDate,
        val effectiveStartMillis: Long,
        val alarmAtMillis: Long,
    )

    private data class SelectedEvent(
        val eligible: EligibleEvent,
        val delivery: AlarmDelivery,
    )

    private fun CalendarEvent.toDiagnostic(decision: EventDecision) = EventDiagnostic(
        eventId = eventId,
        startMillis = instanceStartMillis,
        title = title,
        calendarName = calendarName,
        allDay = allDay,
        source = source,
        decision = decision,
    )
}
