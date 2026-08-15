package com.vovremya.alarm.launcher

import android.content.ComponentName
import android.content.pm.PackageManager
import com.vovremya.alarm.data.LauncherIcon
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LauncherIconManagerTest {
    @Test
    fun `enables selected launcher alias and disables every other alias`() {
        val context = RuntimeEnvironment.getApplication()
        val packageManager = context.packageManager

        LauncherIconManager(context).apply(LauncherIcon.BELL)

        LauncherIcon.entries.forEach { icon ->
            val suffix = icon.name.lowercase().replaceFirstChar(Char::uppercase)
            val component = ComponentName(context, "com.vovremya.alarm.Launcher$suffix")
            assertEquals(
                if (icon == LauncherIcon.BELL) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                packageManager.getComponentEnabledSetting(component),
            )
        }
    }
}
