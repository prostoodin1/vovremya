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
}
