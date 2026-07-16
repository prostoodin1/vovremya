package com.vovremya.alarm.data

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.vovremya.alarm.localization.tr
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

    fun hasWritePermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun getCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        queryCalendars()
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
        val calendarResult = runCatching { queryCalendars().associateBy(CalendarInfo::id) }
        val calendars = calendarResult.getOrDefault(emptyMap())
        val instanceResult = runCatching { queryInstances(rangeStart, rangeEnd, calendars) }
        val directResult = runCatching { queryEvents(rangeStart, rangeEnd, calendars) }
        val instances = instanceResult.getOrNull()?.events.orEmpty()
        val direct = directResult.getOrNull()?.events.orEmpty()
        val instanceKeys = instances.mapTo(mutableSetOf()) { it.eventId to it.startMillis }
        val directOnly = direct.filter { (it.eventId to it.startMillis) !in instanceKeys }
        val errors = buildList {
            calendarResult.exceptionOrNull()?.let { add("Calendars: ${it.logMessage()}") }
            instanceResult.getOrNull()?.warnings?.let(::addAll)
            directResult.getOrNull()?.warnings?.let(::addAll)
            instanceResult.exceptionOrNull()?.let { add("Instances: ${it.logMessage()}") }
            directResult.exceptionOrNull()?.let { add("Events: ${it.logMessage()}") }
        }
        buildScanResult(instances + directOnly, instances.size, directOnly.size, errors)
    }

    fun repairAndRequestCalendarSync(
        calendars: List<CalendarInfo>,
        selectedCalendarIds: Set<Long> = emptySet(),
    ): CalendarSyncRepairResult {
        val targeted = calendars.filter { calendar ->
            selectedCalendarIds.isEmpty() || calendar.id in selectedCalendarIds
        }
        val needsRepair = targeted.filter { !it.syncEvents }
        val canWrite = hasWritePermission()
        var updated = 0
        var failed = 0
        if (canWrite) {
            needsRepair.forEach { calendar ->
                if (repairCalendar(calendar)) updated++ else failed++
            }
        } else {
            failed = needsRepair.size
        }

        val accounts = targeted.asSequence()
            .filter { it.accountName.isNotBlank() && it.accountType.isNotBlank() }
            .filterNot { it.accountType.equals("LOCAL", ignoreCase = true) }
            .map { it.accountName to it.accountType }
            .distinct()
            .toList()
        val requested = accounts.count { (name, type) ->
            val account = Account(name, type)
            runCatching {
                if (!ContentResolver.getSyncAutomatically(account, CalendarContract.AUTHORITY)) {
                    ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)
                }
            }
            runCatching {
                ContentResolver.requestSync(account, CalendarContract.AUTHORITY, manualSyncExtras())
            }.isSuccess
        }
        return CalendarSyncRepairResult(
            targetedCalendars = targeted.size,
            updatedCalendars = updated,
            failedCalendars = failed,
            requestedAccounts = requested,
            writePermissionMissing = needsRepair.isNotEmpty() && !canWrite,
        )
    }

    private fun repairCalendar(calendar: CalendarInfo): Boolean {
        val values = ContentValues().apply {
            if (!calendar.syncEvents) put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        if (values.size() == 0) return true
        val itemUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendar.id)
        val regularUpdate = runCatching {
            context.contentResolver.update(itemUri, values, null, null)
        }.getOrDefault(0)
        if (regularUpdate > 0) return true

        // Some Calendar Provider implementations only accept calendar-level
        // sync flags when the account is supplied on a sync-adapter URI.
        if (calendar.accountName.isBlank() || calendar.accountType.isBlank()) return false
        val syncAdapterUri = itemUri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, calendar.accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, calendar.accountType)
            .build()
        return runCatching {
            context.contentResolver.update(syncAdapterUri, values, null, null) > 0
        }.getOrDefault(false)
    }

    private fun manualSyncExtras() = Bundle().apply {
        putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, true)
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

    private fun queryCalendars(): List<CalendarInfo> {
        val richProjection = arrayOf(
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
        return runCatching { queryCalendarRows(richProjection, rich = true) }
            .getOrElse {
                queryCalendarRows(
                    arrayOf(
                        CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        CalendarContract.Calendars.ACCOUNT_NAME,
                        CalendarContract.Calendars.CALENDAR_COLOR,
                    ),
                    rich = false,
                )
            }
            .distinctBy(CalendarInfo::id)
    }

    private fun queryCalendarRows(projection: Array<String>, rich: Boolean): List<CalendarInfo> {
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
                    displayName = cursor.getString(1).orEmpty().ifBlank { tr("Календарь") },
                    accountName = cursor.getString(2).orEmpty(),
                    color = cursor.getInt(3).takeIf { it != 0 } ?: DEFAULT_CALENDAR_COLOR,
                    accountType = if (rich) cursor.getString(4).orEmpty() else "",
                    syncEvents = !rich || cursor.getInt(5) == 1,
                    visible = !rich || cursor.getInt(6) == 1,
                    ownerAccount = if (rich) cursor.getString(7).orEmpty() else "",
                    accessLevel = if (rich) cursor.getInt(8) else 0,
                    isPrimary = rich && cursor.getInt(9) == 1,
                )
            }
        }
        return calendars
    }

    private fun queryInstances(
        start: ZonedDateTime,
        end: ZonedDateTime,
        calendars: Map<Long, CalendarInfo>,
    ): QueryOutcome {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start.toInstant().toEpochMilli())
            ContentUris.appendId(it, end.toInstant().toEpochMilli())
        }.build()
        val richProjection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.STATUS,
        )
        val richResult = runCatching { queryInstanceRows(uri, richProjection, calendars, rich = true) }
        richResult.getOrNull()?.let { return QueryOutcome(it) }
        val minimalProjection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.STATUS,
        )
        return QueryOutcome(
            events = queryInstanceRows(uri, minimalProjection, calendars, rich = false),
            warnings = listOf(
                "Instances rich projection: ${richResult.exceptionOrNull()!!.logMessage()}; minimal fallback used",
            ),
        )
    }

    private fun queryInstanceRows(
        uri: Uri,
        projection: Array<String>,
        calendars: Map<Long, CalendarInfo>,
        rich: Boolean,
    ): List<RawEvent> {
        val events = mutableListOf<RawEvent>()
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val calendarId = cursor.getLong(if (rich) 4 else 3)
                val calendar = calendars[calendarId]
                events += RawEvent(
                    eventId = cursor.getLong(0),
                    startMillis = cursor.getLong(1),
                    title = cursor.getString(2).orEmpty().ifBlank { tr("Событие") },
                    location = if (rich) cursor.getString(3)?.takeIf(String::isNotBlank) else null,
                    calendarId = calendarId,
                    calendarName = if (rich) cursor.getString(5).orEmpty().ifBlank {
                        calendar?.displayName ?: tr("Календарь")
                    } else calendar?.displayName ?: tr("Календарь"),
                    calendarColor = if (rich) {
                        cursor.getInt(6).takeIf { it != 0 } ?: calendar?.color ?: DEFAULT_CALENDAR_COLOR
                    } else calendar?.color ?: DEFAULT_CALENDAR_COLOR,
                    allDay = cursor.getInt(if (rich) 7 else 4) == 1,
                    canceled = cursor.getInt(if (rich) 8 else 5) == CalendarContract.Events.STATUS_CANCELED,
                    source = EventSource.INSTANCES,
                )
            }
        }
        return events
    }

    private fun queryEvents(
        start: ZonedDateTime,
        end: ZonedDateTime,
        calendars: Map<Long, CalendarInfo>,
    ): QueryOutcome {
        val richProjection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.CALENDAR_COLOR,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.DELETED,
        )
        val richResult = runCatching { queryEventRows(start, end, richProjection, calendars, rich = true) }
        richResult.getOrNull()?.let { return QueryOutcome(it) }
        val minimalProjection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.STATUS,
        )
        return QueryOutcome(
            events = queryEventRows(start, end, minimalProjection, calendars, rich = false),
            warnings = listOf(
                "Events rich projection: ${richResult.exceptionOrNull()!!.logMessage()}; minimal fallback used",
            ),
        )
    }

    private fun queryEventRows(
        start: ZonedDateTime,
        end: ZonedDateTime,
        projection: Array<String>,
        calendars: Map<Long, CalendarInfo>,
        rich: Boolean,
    ): List<RawEvent> {
        val events = mutableListOf<RawEvent>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.DTSTART}>=? AND ${CalendarContract.Events.DTSTART}<?",
            arrayOf(start.toInstant().toEpochMilli().toString(), end.toInstant().toEpochMilli().toString()),
            "${CalendarContract.Events.DTSTART} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                // Some OEM providers store DELETED as NULL and some reject that
                // column entirely. The minimal fallback intentionally omits it.
                if (rich && !cursor.isNull(9) && cursor.getInt(9) != 0) continue
                val calendarId = cursor.getLong(if (rich) 4 else 3)
                val calendar = calendars[calendarId]
                events += RawEvent(
                    eventId = cursor.getLong(0),
                    startMillis = cursor.getLong(1),
                    title = cursor.getString(2).orEmpty().ifBlank { tr("Событие") },
                    location = if (rich) cursor.getString(3)?.takeIf(String::isNotBlank) else null,
                    calendarId = calendarId,
                    calendarName = if (rich) cursor.getString(5).orEmpty().ifBlank {
                        calendar?.displayName ?: tr("Календарь")
                    } else calendar?.displayName ?: tr("Календарь"),
                    calendarColor = if (rich) {
                        cursor.getInt(6).takeIf { it != 0 } ?: calendar?.color ?: DEFAULT_CALENDAR_COLOR
                    } else calendar?.color ?: DEFAULT_CALENDAR_COLOR,
                    allDay = cursor.getInt(if (rich) 7 else 4) == 1,
                    canceled = cursor.getInt(if (rich) 8 else 5) == CalendarContract.Events.STATUS_CANCELED,
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
        rawEvents.forEach { event ->
            if (event.canceled) {
                excludedCanceled++
                diagnostics += event.toDiagnostic(EventDecision.CANCELED)
            } else {
                // Do not filter SELF_ATTENDEE_STATUS. Several local and OEM
                // providers incorrectly mark ordinary events as declined.
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
            excludedDeclined = 0,
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

    private data class QueryOutcome(
        val events: List<RawEvent>,
        val warnings: List<String> = emptyList(),
    )

    private fun Throwable.logMessage(): String = buildString {
        append(this@logMessage::class.java.simpleName)
        this@logMessage.message?.takeIf(String::isNotBlank)?.let { append(": ").append(it.take(180)) }
    }

    private companion object {
        const val DEFAULT_CALENDAR_COLOR = 0xFF6558D3.toInt()
    }
}
