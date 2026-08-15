package com.vovremya.alarm.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SettingsStoreTest {
    @Test
    fun `advanced mode is persisted without changing detailed settings`() = runBlocking {
        val store = SettingsStore(RuntimeEnvironment.getApplication())

        store.setAdvancedMode(true)
        val enabled = store.settings.first()
        assertTrue(enabled.advancedMode)
        assertTrue(enabled.allEventsPerDay)
        assertFalse(enabled.includeAllDayEvents)

        store.setAdvancedMode(false)
        assertFalse(store.settings.first().advancedMode)
    }

    @Test
    fun `application language is persisted`() = runBlocking {
        val store = SettingsStore(RuntimeEnvironment.getApplication())

        store.setAppLanguageTag("it")

        assertEquals("it", store.settings.first().appLanguageTag)
    }

    @Test
    fun `navigation importance and calendar visibility settings are persisted`() = runBlocking {
        val store = SettingsStore(RuntimeEnvironment.getApplication())

        store.setNavigationStyle(NavigationStyle.BOTTOM_BAR)
        store.setBottomBarHideSeconds(10)
        store.setReduceAnimations(true)
        store.setImportantEventTitles(setOf("  Dentist  ", ""))
        store.setShowImportantTab(true)
        store.setIncludeUnselectedCalendarsAsSilent(true)
        store.setShowAllEventsTab(true)

        val settings = store.settings.first()
        assertEquals(NavigationStyle.BOTTOM_BAR, settings.navigationStyle)
        assertEquals(10, settings.bottomBarHideSeconds)
        assertTrue(settings.reduceAnimations)
        assertEquals(setOf("Dentist"), settings.importantEventTitles)
        assertTrue(settings.showImportantTab)
        assertTrue(settings.includeUnselectedCalendarsAsSilent)
        assertTrue(settings.showAllEventsTab)
    }

    @Test
    fun `event calendar swipe and launcher customization is persisted`() = runBlocking {
        val store = SettingsStore(RuntimeEnvironment.getApplication())
        val rule = EventAlarmRule(
            match = "42:123456",
            title = "Dentist",
            scope = EventRuleScope.THIS_EVENT,
            leadMinutes = 135,
            delivery = AlarmDelivery.SILENT_REMINDER,
            soundEnabled = false,
            vibrationEnabled = true,
            effects = SignalEffects(highBrightnessEnabled = true, vibrationIntensity = 72),
        )

        store.setEventAlarmRule(rule)
        store.setCalendarLeadMinutes(99, 75)
        store.setDefaultEventRuleScope(EventRuleScope.SAME_TITLE)
        store.setFullSwipeEnabled(true)
        store.setSwipeDirection(SwipeDirection.LEFT)
        store.setLeftSwipeAction(SwipeAction.SKIP)
        store.setRightSwipeAction(SwipeAction.SILENT)
        store.setLauncherIcon(LauncherIcon.HOURGLASS)

        val settings = store.settings.first()
        assertEquals(listOf(rule), settings.eventAlarmRules)
        assertEquals(mapOf(99L to 75), settings.calendarLeadMinutes)
        assertEquals(EventRuleScope.SAME_TITLE, settings.defaultEventRuleScope)
        assertTrue(settings.fullSwipeEnabled)
        assertEquals(SwipeDirection.LEFT, settings.swipeDirection)
        assertEquals(SwipeAction.SKIP, settings.leftSwipeAction)
        assertEquals(SwipeAction.SILENT, settings.rightSwipeAction)
        assertEquals(LauncherIcon.HOURGLASS, settings.launcherIcon)

        store.setCalendarLeadMinutes(99, null)
        store.removeEventAlarmRule(rule.scope, rule.match)
        assertTrue(store.settings.first().calendarLeadMinutes.isEmpty())
        assertTrue(store.settings.first().eventAlarmRules.isEmpty())
    }
}
