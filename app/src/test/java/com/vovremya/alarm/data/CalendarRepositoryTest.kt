package com.vovremya.alarm.data

import android.Manifest
import android.accounts.Account
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import com.vovremya.alarm.localization.tr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CalendarRepositoryTest {
    private val eventStart = ZonedDateTime.of(2026, 7, 16, 9, 0, 0, 0, ZoneId.of("Europe/Paris"))
        .toInstant()
        .toEpochMilli()

    @Before
    fun setUp() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_SYNC_SETTINGS,
            Manifest.permission.WRITE_SYNC_SETTINGS,
        )
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, FakeCalendarProvider(eventStart))
    }

    @Test
    fun `falls back to Events and names an untitled event when Instances is empty`() = runBlocking {
        val now = eventStart - 24 * 60 * 60_000L
        val result = CalendarRepository(RuntimeEnvironment.getApplication()).scanUpcomingEvents(now)

        assertEquals(0, result.instanceRows)
        assertEquals(1, result.directEventRows)
        assertEquals(1, result.totalInstances)
        assertEquals(tr("Событие"), result.events.single().title)
        assertEquals(EventSource.EVENTS, result.events.single().source)
    }

    @Test
    fun `keeps an untitled direct event when Instances contains another event`() = runBlocking {
        ShadowContentResolver.registerProviderInternal(
            CalendarContract.AUTHORITY,
            FakeCalendarProvider(eventStart, includeInstance = true),
        )
        val now = eventStart - 24 * 60 * 60_000L
        val result = CalendarRepository(RuntimeEnvironment.getApplication()).scanUpcomingEvents(now)

        assertEquals(1, result.instanceRows)
        assertEquals(1, result.directEventRows)
        assertEquals(2, result.totalInstances)
        assertEquals(2, result.events.size)
        val untitled = result.events.single { it.eventId == 42L }
        assertEquals(tr("Событие"), untitled.title)
        assertEquals(EventSource.EVENTS, untitled.source)
    }

    @Test
    fun `reads a local event with minimal projection and never filters attendee status`() = runBlocking {
        val provider = FakeCalendarProvider(eventStart, rejectRichEventProjection = true)
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)

        val result = CalendarRepository(RuntimeEnvironment.getApplication())
            .scanUpcomingEvents(eventStart - 24 * 60 * 60_000L)

        assertEquals(1, result.events.size)
        assertEquals(EventSource.EVENTS, result.events.single().source)
        assertTrue(result.readErrors.any { it.contains("minimal fallback used") })
        assertFalse(provider.attendeeColumnRequested)
    }

    @Test
    fun `repairs sync without changing calendar visibility`() {
        val provider = FakeCalendarProvider(eventStart)
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        val calendar = CalendarInfo(
            id = 2L,
            displayName = "My calendar",
            accountName = "local",
            color = 0xFF6558D3.toInt(),
            accountType = "LOCAL",
            syncEvents = false,
            visible = false,
        )

        val result = CalendarRepository(RuntimeEnvironment.getApplication())
            .repairAndRequestCalendarSync(listOf(calendar))

        assertEquals(1, result.targetedCalendars)
        assertEquals(1, result.updatedCalendars)
        assertEquals(0, result.failedCalendars)
        assertEquals(0, result.requestedAccounts)
        assertEquals(1, provider.calendarUpdates.size)
        assertEquals(1, provider.calendarUpdates.single().getAsInteger(CalendarContract.Calendars.SYNC_EVENTS))
        assertFalse(provider.calendarUpdates.single().containsKey(CalendarContract.Calendars.VISIBLE))
    }

    @Test
    fun `repairs only explicitly selected calendars`() {
        val provider = FakeCalendarProvider(eventStart)
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        val calendars = listOf(2L, 3L).map { id ->
            CalendarInfo(
                id = id,
                displayName = "Calendar $id",
                accountName = "local",
                color = 0xFF6558D3.toInt(),
                accountType = "LOCAL",
                syncEvents = false,
                visible = false,
            )
        }

        val result = CalendarRepository(RuntimeEnvironment.getApplication())
            .repairAndRequestCalendarSync(calendars, selectedCalendarIds = setOf(3L))

        assertEquals(1, result.targetedCalendars)
        assertEquals(1, result.updatedCalendars)
        assertEquals(1, provider.calendarUpdates.size)
    }

    @Test
    fun `reads metadata and events from a calendar shared by another owner`() = runBlocking {
        val provider = FakeCalendarProvider(eventStart, includeSharedCalendarRow = true)
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        val repository = CalendarRepository(RuntimeEnvironment.getApplication())

        val calendar = repository.getCalendars().single()
        val result = repository.scanUpcomingEvents(eventStart - 24 * 60 * 60_000L)

        assertTrue(calendar.isShared)
        assertEquals("owner@example.com", calendar.ownerAccount)
        assertEquals(7L, result.events.single().calendarId)
        assertEquals(1, result.directEventRows)
    }

    @Test
    fun `repairs a disabled shared duplicate and enables account syncability`() {
        val provider = FakeCalendarProvider(eventStart)
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        val accountName = "shared-test-user@gmail.com"
        val accountType = "com.google"
        val calendars = listOf(
            CalendarInfo(
                id = 7L,
                displayName = "Shared team",
                accountName = accountName,
                color = 0xFF6558D3.toInt(),
                accountType = accountType,
                syncEvents = true,
                visible = true,
                ownerAccount = "owner@example.com",
            ),
            CalendarInfo(
                id = 8L,
                displayName = "Shared team",
                accountName = accountName,
                color = 0xFF6558D3.toInt(),
                accountType = accountType,
                syncEvents = false,
                visible = false,
                ownerAccount = "owner@example.com",
            ),
        )

        val result = CalendarRepository(RuntimeEnvironment.getApplication())
            .repairAndRequestCalendarSync(calendars)
        val account = Account(accountName, accountType)

        assertEquals(1, result.updatedCalendars)
        assertEquals(1, result.requestedAccounts)
        assertEquals(1, provider.calendarUpdates.size)
        assertEquals(1, ContentResolver.getIsSyncable(account, CalendarContract.AUTHORITY))
        assertTrue(ContentResolver.getSyncAutomatically(account, CalendarContract.AUTHORITY))
    }

    private class FakeCalendarProvider(
        private val eventStart: Long,
        private val includeInstance: Boolean = false,
        private val rejectRichEventProjection: Boolean = false,
        private val includeSharedCalendarRow: Boolean = false,
    ) : ContentProvider() {
        var attendeeColumnRequested: Boolean = false
            private set
        val calendarUpdates = mutableListOf<ContentValues>()

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            if (projection?.any { it == CalendarContract.Events.SELF_ATTENDEE_STATUS } == true) {
                attendeeColumnRequested = true
            }
            if (
                uri.pathSegments.firstOrNull() == "events" &&
                rejectRichEventProjection &&
                projection?.any { it == CalendarContract.Events.CALENDAR_DISPLAY_NAME } == true
            ) {
                throw IllegalArgumentException("OEM provider rejects joined event columns")
            }
            if (
                uri.pathSegments.firstOrNull() == "events" &&
                selection?.contains(CalendarContract.Events.DELETED) == true
            ) {
                throw IllegalArgumentException("OEM provider rejects a deleted predicate")
            }
            val columns: Array<String> = projection?.let { source ->
                Array(source.size) { index -> source[index] }
            } ?: emptyArray()
            val cursor = MatrixCursor(columns)
            if (uri.pathSegments.firstOrNull() == "calendars" && includeSharedCalendarRow) {
                val values = mapOf<String, Any?>(
                    CalendarContract.Calendars._ID to 7L,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME to "Shared team",
                    CalendarContract.Calendars.ACCOUNT_NAME to "shared-test-user@gmail.com",
                    CalendarContract.Calendars.CALENDAR_COLOR to 0xFF6558D3.toInt(),
                    CalendarContract.Calendars.ACCOUNT_TYPE to "com.google",
                    CalendarContract.Calendars.SYNC_EVENTS to 1,
                    CalendarContract.Calendars.VISIBLE to 1,
                    CalendarContract.Calendars.OWNER_ACCOUNT to "owner@example.com",
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL to CalendarContract.Calendars.CAL_ACCESS_EDITOR,
                    CalendarContract.Calendars.IS_PRIMARY to 0,
                )
                cursor.addRow(columns.map { column -> values[column] })
            }
            if (uri.pathSegments.firstOrNull() == "instances" && includeInstance) {
                val values = mapOf<String, Any?>(
                    CalendarContract.Instances.EVENT_ID to 99L,
                    CalendarContract.Instances.BEGIN to eventStart + 60 * 60_000L,
                    CalendarContract.Instances.TITLE to "Another event",
                    CalendarContract.Instances.EVENT_LOCATION to null,
                    CalendarContract.Instances.CALENDAR_ID to 7L,
                    CalendarContract.Instances.CALENDAR_DISPLAY_NAME to "Google",
                    CalendarContract.Instances.CALENDAR_COLOR to 0xFF6558D3.toInt(),
                    CalendarContract.Instances.ALL_DAY to 0,
                    CalendarContract.Instances.STATUS to CalendarContract.Events.STATUS_CONFIRMED,
                    CalendarContract.Instances.SELF_ATTENDEE_STATUS to CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
                )
                cursor.addRow(columns.map { column -> values[column] })
            }
            if (uri.pathSegments.firstOrNull() == "events") {
                val values = mapOf<String, Any?>(
                    CalendarContract.Events._ID to 42L,
                    CalendarContract.Events.DTSTART to eventStart,
                    CalendarContract.Events.TITLE to null,
                    CalendarContract.Events.EVENT_LOCATION to null,
                    CalendarContract.Events.CALENDAR_ID to 7L,
                    CalendarContract.Events.CALENDAR_DISPLAY_NAME to "Google",
                    CalendarContract.Events.CALENDAR_COLOR to 0xFF6558D3.toInt(),
                    CalendarContract.Events.ALL_DAY to 0,
                    CalendarContract.Events.STATUS to CalendarContract.Events.STATUS_CONFIRMED,
                    CalendarContract.Events.SELF_ATTENDEE_STATUS to CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
                    CalendarContract.Events.DELETED to null,
                )
                cursor.addRow(columns.map { column -> values[column] })
            }
            return cursor
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            if (uri.pathSegments.firstOrNull() != "calendars" || values == null) return 0
            calendarUpdates += ContentValues(values)
            return 1
        }
    }
}
