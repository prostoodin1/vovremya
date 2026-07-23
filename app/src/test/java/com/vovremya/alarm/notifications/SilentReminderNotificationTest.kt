package com.vovremya.alarm.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import com.vovremya.alarm.data.AlarmDelivery
import com.vovremya.alarm.domain.AlarmPayload
import com.vovremya.alarm.ui.AlarmActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SilentReminderNotificationTest {
    @Test
    fun `later event opens full screen through a silent channel`() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val helper = NotificationHelper(application)
        helper.createChannels()
        val intent = Intent().apply {
            putExtra(AlarmPayload.EXTRA_KEY, "later-event")
            putExtra(AlarmPayload.EXTRA_TITLE, "Later event")
            putExtra(AlarmPayload.EXTRA_EVENT_START, 123_456L)
            putExtra(AlarmPayload.EXTRA_DELIVERY, AlarmDelivery.SILENT_REMINDER.name)
            putExtra(AlarmPayload.EXTRA_SOUND_ENABLED, true)
            putExtra(AlarmPayload.EXTRA_VIBRATION_ENABLED, false)
        }

        helper.showAlarm(intent)

        val manager = application.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(NotificationHelper.REMINDER_CHANNEL)
        val notification = shadowOf(manager).allNotifications.single()
        val launchIntent = shadowOf(notification.fullScreenIntent).savedIntent
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(Uri.EMPTY, channel.sound)
        assertFalse(channel.shouldVibrate())
        assertEquals(NotificationHelper.REMINDER_CHANNEL, notification.channelId)
        assertNull(notification.sound)
        assertNull(notification.vibrate)
        assertNotNull(notification.fullScreenIntent)
        assertEquals(AlarmActivity::class.java.name, launchIntent.component?.className)
        assertEquals(AlarmDelivery.SILENT_REMINDER.name, launchIntent.getStringExtra(AlarmPayload.EXTRA_DELIVERY))
    }

    @Test
    fun `later event can use vibration only channel`() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val helper = NotificationHelper(application)
        helper.createChannels()
        val intent = Intent().apply {
            putExtra(AlarmPayload.EXTRA_KEY, "vibrating-later-event")
            putExtra(AlarmPayload.EXTRA_TITLE, "Later event")
            putExtra(AlarmPayload.EXTRA_EVENT_START, 123_456L)
            putExtra(AlarmPayload.EXTRA_DELIVERY, AlarmDelivery.SILENT_REMINDER.name)
            putExtra(AlarmPayload.EXTRA_SOUND_ENABLED, true)
            putExtra(AlarmPayload.EXTRA_VIBRATION_ENABLED, true)
        }

        helper.showAlarm(intent)

        val manager = application.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(NotificationHelper.REMINDER_VIBRATION_CHANNEL)
        val notification = shadowOf(manager).allNotifications.single()
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(Uri.EMPTY, channel.sound)
        assertEquals(true, channel.shouldVibrate())
        assertEquals(NotificationHelper.REMINDER_VIBRATION_CHANNEL, notification.channelId)
        assertNull(notification.sound)
        assertNotNull(notification.fullScreenIntent)
    }
}
