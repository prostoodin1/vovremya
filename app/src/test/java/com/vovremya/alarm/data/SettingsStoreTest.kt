package com.vovremya.alarm.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
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
}
