package com.vovremya.alarm

import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vovremya.alarm.data.CalendarRepository
import com.vovremya.alarm.data.LauncherIcon
import com.vovremya.alarm.launcher.LauncherIconManager
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V17DeviceIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun sharedCalendarEventIsReturnedByTheRealAndroidProvider() = runBlocking {
        val resolver = context.contentResolver
        val account = "viewer-v17@example.com"
        val calendarUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val insertedCalendar = resolver.insert(
            calendarUri,
            ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, account)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, "v17-shared")
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "V17 Shared Calendar")
                put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF3478D4.toInt())
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_EDITOR)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, "owner-v17@example.com")
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.VISIBLE, 1)
            },
        )
        assertNotNull(insertedCalendar)
        val calendarId = ContentUris.parseId(insertedCalendar!!)
        try {
            val start = ZonedDateTime.now(ZoneId.systemDefault()).plusDays(2).withHour(9).withMinute(0)
                .withSecond(0).withNano(0)
            val eventUri = resolver.insert(
                CalendarContract.Events.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, "V17 Device Event")
                    put(CalendarContract.Events.DTSTART, start.toInstant().toEpochMilli())
                    put(CalendarContract.Events.DTEND, start.plusHours(1).toInstant().toEpochMilli())
                    put(CalendarContract.Events.EVENT_TIMEZONE, start.zone.id)
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    put(CalendarContract.Events.ALL_DAY, 0)
                },
            )
            assertNotNull(eventUri)

            val repository = CalendarRepository(context)
            val calendar = repository.getCalendars().single { it.id == calendarId }
            val scan = repository.scanUpcomingEvents(horizonDays = 7)

            assertTrue(calendar.isShared)
            assertTrue(calendar.syncEvents)
            assertTrue(calendar.visible)
            assertTrue(scan.events.any { it.title == "V17 Device Event" && it.calendarId == calendarId })
        } finally {
            resolver.delete(calendarUri, "${CalendarContract.Calendars._ID}=?", arrayOf(calendarId.toString()))
        }
    }

    @Test
    fun launcherIconSwitchesUsingRealManifestAliases() {
        val manager = LauncherIconManager(context)
        try {
            manager.apply(LauncherIcon.BELL)
            LauncherIcon.entries.forEach { icon ->
                val suffix = icon.name.lowercase().replaceFirstChar(Char::uppercase)
                val component = ComponentName(context, "com.vovremya.alarm.Launcher$suffix")
                assertEquals(
                    if (icon == LauncherIcon.BELL) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    context.packageManager.getComponentEnabledSetting(component),
                )
            }
        } finally {
            manager.apply(LauncherIcon.CLASSIC)
        }
    }
}
