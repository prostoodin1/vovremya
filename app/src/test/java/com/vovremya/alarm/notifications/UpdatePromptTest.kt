package com.vovremya.alarm.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import com.vovremya.alarm.update.UpdateInstallerActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class UpdatePromptTest {
    @Test
    fun `update notification is high priority and opens a full screen confirmation`() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val helper = NotificationHelper(application)
        helper.createChannels()
        val apk = application.getExternalFilesDir("updates")!!.resolve("vovremya-9.9.9.apk")
        apk.parentFile?.mkdirs()
        apk.writeBytes(byteArrayOf(1, 2, 3))

        helper.showUpdate("9.9.9", apk)

        val manager = application.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).allNotifications.single()
        val launchIntent = shadowOf(notification.fullScreenIntent).savedIntent
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(NotificationHelper.UPDATE_CHANNEL).importance)
        assertEquals(NotificationHelper.UPDATE_CHANNEL, notification.channelId)
        assertNotNull(notification.fullScreenIntent)
        assertEquals(UpdateInstallerActivity::class.java.name, launchIntent.component?.className)
        assertEquals("9.9.9", launchIntent.getStringExtra(UpdateInstallerActivity.EXTRA_VERSION))
        assertEquals(1, notification.actions.size)
    }

    @Test
    fun `opening update prompt does not launch installer before confirmation`() {
        val application = RuntimeEnvironment.getApplication()
        val apk = application.getExternalFilesDir("updates")!!.resolve("vovremya-9.9.9.apk")
        apk.parentFile?.mkdirs()
        apk.writeBytes(byteArrayOf(1, 2, 3))
        val intent = Intent(application, UpdateInstallerActivity::class.java).apply {
            putExtra(UpdateInstallerActivity.EXTRA_APK_PATH, apk.absolutePath)
            putExtra(UpdateInstallerActivity.EXTRA_VERSION, "9.9.9")
        }

        val controller = Robolectric.buildActivity(UpdateInstallerActivity::class.java, intent).setup()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(controller.get().isFinishing)
        assertNull(shadowOf(controller.get()).nextStartedActivity)
        controller.pause().stop().destroy()
    }
}
