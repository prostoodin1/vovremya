package com.vovremya.alarm.data

import android.Manifest
import android.accounts.Account
import android.content.ContentUris
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

class CalendarRepository(private val context: Context) {
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun getCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.SYNC_EVENTS,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val calendars = mutableListOf<CalendarInfo>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                calendars += CalendarInfo(
                    id = cursor.getLong(0),
                    displayName = cursor.getString(1).orEmpty().ifBlank { "Календарь" },
                    accountName = cursor.getString(2).orEmpty(),
                    color = cursor.getInt(3).takeIf { it != 0 } ?: 0xFF6558D3.toInt(),
                    accountType = cursor.getString(4).orEmpty(),
                    syncEvents = cursor.getInt(5) == 1,
                    visible = cursor.getInt(6) == 1,
                    ownerAccount = cursor.getString(7).orEmpty(),
                    accessLevel = cursor.getInt(8),
                    isPrimary = cursor.getInt(9) == 1,
                )
            }
        }
        calendars.distinctBy(CalendarInfo::id)
    }

    suspend fun getUpcomingEvents(
        nowMillis: Long = System.currentTimeMillis(),
        horizonDays: Long = 21,
    ): List<CalendarEvent> = scanUpcomingEvents(nowMillis, horizonDays).events

    suspend fun scanUpcomingEvents(
        nowMillis: Long = System.currentTimeMillis(),
        horizonDays: Long = 21,
    ): CalendarScanResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext CalendarScanResult()
        val zone = ZoneId.systemDefault()
        val rangeStart = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone)
        val rangeEnd = rangeStart.plusDays(horizonDays)
        val instanceResult = runCatching { queryInstances(rangeStart, rangeEnd) }
        val directResult = runCatching { queryEvents(rangeStart, rangeEnd) }
        val instances = instanceResult.getOrDefault(emptyList())
        val direct = directResult.getOrDefault(emptyList())
        val instanceKeys = instances.mapTo(mutableSetOf()) { it.eventId to it.startMillis }
        val directOnly = direct.filter { (it.eventId to it.startMillis) !in instanceKeys }
        val errors = buildList {
            instanceResult.exceptionOrNull()?.let { add("Instances: ${it.logMessage()}") }
            directResult.exceptionOrNull()?.let { add("Events: ${it.logMessage()}") }
        }
        buildScanResult(instances + directOnly, instances.size, directOnly.size, errors)
    }

    fun requestCalendarSync(calendars: List<CalendarInfo>): Int {
        val accounts = calendars.asSequence()
            .filter { it.accountName.isNotBlank() && it.accountType.isNotBlank() }
            .filterNot { it.accountType.equals("LOCAL", ignoreCase = true) }
            .map { it.accountName to it.accountType }
            .distinct()
            .toList()
        return accounts.count { (name, type) ->
            runCatching {
                ContentResolver.requestSync(
                    Account(name, type),
                    CalendarContract.AUTHORITY,
                    Bundle().apply {
                        putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                        putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                        putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, true)
                    },
                )
            }.isSuccess
        }
    }

    fun calendarChanges(): Flow<Unit> = callbackFlow {
        if (!hasPermission()) {
            close(SecurityException("Calendar permission is not granted"))
            return@callbackFlow
        }
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        try {
            context.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
            context.contentResolver.registerContentObserver(CalendarContract.Calendars.CONTENT_URI, true, observer)
        } catch (error: SecurityException) {
            close(error)
            return@callbackFlow
        }
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    private fun queryInstances(start: ZonedDateTime, end: ZonedDateTime): List<RawEvent> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start.toInstant().toEpochMilli())
            ContentUris.appendId(it, end.toInstant().toEpochMilli())
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )
        val events = mutableListOf<RawEvent>()
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                events += RawEvent(
                    eventId = cursor.getLong(0),
                    startMillis = cursor.getLong(1),
                    title = cursor.getString(2).orEmpty().ifBlank { "Событие" },
                    location = cursor.getString(3)?.takeIf(String::isNotBlank),
                    calendarId = cursor.getLong(4),
                    calendarName = cursor.getString(5).orEmpty().ifBlank { "Календарь" },
                    calendarColor = cursor.getInt(6).takeIf { it != 0 } ?: 0xFF6558D3.toInt(),
                    allDay = cursor.getInt(7) == 1,
                    canceled = cursor.getInt(8) == CalendarContract.Events.STATUS_CANCELED,
                    declined = cursor.getInt(9) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
                    source = EventSource.INSTANCES,
                )
            }
        }
        return events
    }

    private fun queryEvents(start: ZonedDateTime, end: ZonedDateTime): List<RawEvent> {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.CALENDAR_COLOR,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.SELF_ATTENDEE_STATUS,
            CalendarContract.Events.DELETED,
        )
        val events = mutableListOf<RawEvent>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.DTSTART}>=? AND ${CalendarContract.Events.DTSTART}<?",
            arrayOf(start.toInstant().toEpochMilli().toString(), end.toInstant().toEpochMilli().toString()),
            "${CalendarContract.Events.DTSTART} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                // Some local/OEM calendars store DELETED as NULL. A SQL `deleted=0`
                // predicate silently hides those otherwise valid events, so filter here.
                if (!cursor.isNull(10) && cursor.getInt(10) != 0) continue
                events += RawEvent(
                    eventId = cursor.getLong(0),
                    startMillis = cursor.getLong(1),
                    title = cursor.getString(2).orEmpty().ifBlank { "Событие" },
                    location = cursor.getString(3)?.takeIf(String::isNotBlank),
                    calendarId = cursor.getLong(4),
                    calendarName = cursor.getString(5).orEmpty().ifBlank { "Календарь" },
                    calendarColor = cursor.getInt(6).takeIf { it != 0 } ?: 0xFF6558D3.toInt(),
                    allDay = cursor.getInt(7) == 1,
                    canceled = cursor.getInt(8) == CalendarContract.Events.STATUS_CANCELED,
                    declined = cursor.getInt(9) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
                    source = EventSource.EVENTS,
                )
            }
        }
        return events
    }

    private fun buildScanResult(
        rawEvents: List<RawEvent>,
        instanceRows: Int,
        directEventRows: Int,
        readErrors: List<String>,
    ): CalendarScanResult {
        val usable = mutableListOf<CalendarEvent>()
        val diagnostics = mutableListOf<EventDiagnostic>()
        var excludedCanceled = 0
        var excludedDeclined = 0
        rawEvents.forEach { event ->
            val decision = when {
                event.canceled -> EventDecision.CANCELED.also { excludedCanceled++ }
                event.declined -> EventDecision.DECLINED.also { excludedDeclined++ }
                else -> null
            }
            if (decision != null) {
                diagnostics += event.toDiagnostic(decision)
            } else {
                usable += CalendarEvent(
                    eventId = event.eventId,
                    instanceStartMillis = event.startMillis,
                    title = event.title,
                    location = event.location,
                    calendarId = event.calendarId,
                    calendarName = event.calendarName,
                    calendarColor = event.calendarColor,
                    allDay = event.allDay,
                    source = event.source,
                )
            }
        }
        return CalendarScanResult(
            events = usable,
            totalInstances = rawEvents.size,
            excludedCanceled = excludedCanceled,
            excludedDeclined = excludedDeclined,
            instanceRows = instanceRows,
            directEventRows = directEventRows,
            eventDiagnostics = diagnostics,
            readErrors = readErrors,
        )
    }

    private data class RawEvent(
        val eventId: Long,
        val startMillis: Long,
        val title: String,
        val location: String?,
        val calendarId: Long,
        val calendarName: String,
        val calendarColor: Int,
        val allDay: Boolean,
        val canceled: Boolean,
        val declined: Boolean,
        val source: EventSource,
    ) {
        fun toDiagnostic(decision: EventDecision) = EventDiagnostic(
            eventId = eventId,
            startMillis = startMillis,
            title = title,
            calendarName = calendarName,
            allDay = allDay,
            source = source,
            decision = decision,
        )
    }

    private fun Throwable.logMessage(): String = buildString {
        append(this@logMessage::class.java.simpleName)
        this@logMessage.message?.takeIf(String::isNotBlank)?.let { append(": ").append(it.take(180)) }
    }
}
