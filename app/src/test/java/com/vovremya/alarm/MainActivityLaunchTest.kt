package com.vovremya.alarm

import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = VovremyaApplication::class)
class MainActivityLaunchTest {
    @Test
    fun applicationAndMainActivityStartWithoutCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }
}
