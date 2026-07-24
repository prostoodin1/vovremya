package com.vovremya.alarm.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vovremya.alarm.MainActivity
import com.vovremya.alarm.R
import com.vovremya.alarm.data.AlarmDelivery
import com.vovremya.alarm.data.SyncResult
import com.vovremya.alarm.domain.AlarmPayload
import com.vovremya.alarm.localization.appLocale
import com.vovremya.alarm.localization.tr
import com.vovremya.alarm.ui.AlarmActivity
import com.vovremya.alarm.update.UpdateInstallerActivity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NotificationHelper(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val systemManager = context.getSystemService(NotificationManager::class.java)
        val alarmSound = Settings.System.DEFAULT_ALARM_ALERT_URI
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarmChannels = listOf(
            Triple(ALARM_CHANNEL_SOUND_VIBRATION, true, true),
            Triple(ALARM_CHANNEL_SOUND, true, false),
            Triple(ALARM_CHANNEL_VIBRATION, false, true),
            Triple(ALARM_CHANNEL_SILENT, false, false),
        ).map { (id, sound, vibration) ->
            NotificationChannel(
                id,
                "${context.getString(R.string.alarm_channel)} · ${alarmModeName(sound, vibration)}",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = tr("Сигнал перед событием")
                enableVibration(vibration)
                vibrationPattern = if (vibration) longArrayOf(0, 600, 300, 600) else null
                if (sound) setSound(alarmSound, audio) else setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        }
        val reminderChannels = listOf(
            REMINDER_CHANNEL to false,
            REMINDER_VIBRATION_CHANNEL to true,
        ).map { (id, vibration) -> NotificationChannel(
            id,
            tr(if (vibration) "Напоминания с вибрацией" else "Тихие напоминания"),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = tr(
                if (vibration) "Экран для последующих событий: только вибрация, без звука"
                else "Экран для последующих событий без звука и вибрации",
            )
            val silentAudio = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            // A non-null, empty URI keeps Android's full-screen alert path enabled
            // while producing no audible media or audio file playback.
            setSound(Uri.EMPTY, silentAudio)
            enableVibration(vibration)
            vibrationPattern = if (vibration) longArrayOf(0, 700, 350, 700) else null
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        } }
        val planning = NotificationChannel(
            PLANNING_CHANNEL,
            context.getString(R.string.sync_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = tr("Итог ежедневной проверки календаря") }
        val updates = NotificationChannel(
            UPDATE_CHANNEL,
            context.getString(R.string.update_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = tr("Новые версии из GitHub Releases")
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        systemManager.createNotificationChannels(alarmChannels + reminderChannels + listOf(planning, updates))
    }

    fun showAlarm(intent: Intent) {
        if (!canNotify()) return
        val key = intent.getStringExtra(AlarmPayload.EXTRA_KEY).orEmpty()
        val eventStart = intent.getLongExtra(AlarmPayload.EXTRA_EVENT_START, 0L)
        val location = intent.getStringExtra(AlarmPayload.EXTRA_LOCATION)
        val delivery = intent.getStringExtra(AlarmPayload.EXTRA_DELIVERY)
            ?.let { stored -> AlarmDelivery.entries.firstOrNull { it.name == stored } }
            ?: AlarmDelivery.ALARM
        val isReminder = delivery == AlarmDelivery.SILENT_REMINDER
        val title = intent.getStringExtra(AlarmPayload.EXTRA_TITLE).orEmpty().ifBlank {
            tr(if (isReminder) "Напоминание о событии" else "Пора собираться")
        }
        val soundEnabled = !isReminder && intent.getBooleanExtra(AlarmPayload.EXTRA_SOUND_ENABLED, true)
        val vibrationEnabled = intent.getBooleanExtra(AlarmPayload.EXTRA_VIBRATION_ENABLED, true)
        val fullScreen = Intent(context, AlarmActivity::class.java).apply {
            putExtras(intent)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            key.hashCode() and Int.MAX_VALUE,
            fullScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val content = buildString {
            append(tr("Событие в %s", formatTime(eventStart)))
            if (!location.isNullOrBlank()) append(" · $location")
        }
        val notification = NotificationCompat.Builder(
            context,
            if (isReminder) reminderChannelId(vibrationEnabled) else alarmChannelId(soundEnabled, vibrationEnabled),
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()
        notifySafely(alarmNotificationId(key), notification)
    }

    fun cancelAlarm(key: String) = manager.cancel(alarmNotificationId(key))

    fun showPlanningSummary(result: SyncResult) {
        if (!canNotify()) return
        val alarms = result.alarms
        val next = alarms.firstOrNull { it.delivery == AlarmDelivery.ALARM }
        val title = tr(if (next == null) "На ближайшие дни будильников нет" else "Будильник готов")
        val body = if (next == null) {
            when {
                result.diagnostics.totalInstances == 0 && result.diagnostics.unsyncedCalendars > 0 ->
                    tr("События не прочитаны: проверьте синхронизацию календарей")
                result.diagnostics.totalInstances == 0 ->
                    tr("Календарь не вернул событий на ближайшие %d дней", result.diagnostics.lookAheadDays)
                else -> tr("Календарь проверен — события не прошли выбранные фильтры")
            }
        } else {
            "${formatDay(next.alarmAtMillis)}, ${formatTime(next.alarmAtMillis)} · ${next.title}"
        }
        val openApp = PendingIntent.getActivity(
            context,
            19,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notifySafely(
            PLANNING_NOTIFICATION_ID,
            NotificationCompat.Builder(context, PLANNING_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun showUpdate(version: String, apk: File, isDowngrade: Boolean = false) {
        if (!canNotify()) return
        val install = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            updatePromptIntent(version, apk, isDowngrade),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = tr(
            if (isDowngrade) {
                "Старая версия загружена; Android не установит её поверх новой"
            } else {
                "Нажмите, чтобы подтвердить установку обновления"
            },
        )
        notifySafely(
            UPDATE_NOTIFICATION_ID,
            NotificationCompat.Builder(context, UPDATE_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(tr("%s %s готово", context.getString(R.string.app_name), version))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(install, true)
                .setContentIntent(install)
                .addAction(R.drawable.ic_notification, tr("Установить"), install)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun openUpdatePrompt(version: String, apk: File, isDowngrade: Boolean = false) {
        context.startActivity(updatePromptIntent(version, apk, isDowngrade))
    }

    private fun updatePromptIntent(version: String, apk: File, isDowngrade: Boolean): Intent =
        Intent(context, UpdateInstallerActivity::class.java).apply {
            putExtra(UpdateInstallerActivity.EXTRA_APK_PATH, apk.absolutePath)
            putExtra(UpdateInstallerActivity.EXTRA_VERSION, version)
            putExtra(UpdateInstallerActivity.EXTRA_IS_DOWNGRADE, isDowngrade)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    fun cancelUpdate() = manager.cancel(UPDATE_NOTIFICATION_ID)

    private fun canNotify(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Permission can be revoked between [canNotify] and the framework call. Keeping
     * that race local prevents a background sync or alarm receiver from crashing.
     */
    private fun notifySafely(id: Int, notification: android.app.Notification) {
        if (!canNotify()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // The next foreground launch will ask for notification permission again.
        }
    }

    private fun alarmNotificationId(key: String): Int = (key.hashCode() and 0x00FFFFFF) + 1000

    private fun alarmChannelId(sound: Boolean, vibration: Boolean): String = when {
        sound && vibration -> ALARM_CHANNEL_SOUND_VIBRATION
        sound -> ALARM_CHANNEL_SOUND
        vibration -> ALARM_CHANNEL_VIBRATION
        else -> ALARM_CHANNEL_SILENT
    }

    private fun reminderChannelId(vibration: Boolean): String =
        if (vibration) REMINDER_VIBRATION_CHANNEL else REMINDER_CHANNEL

    private fun alarmModeName(sound: Boolean, vibration: Boolean): String = when {
        sound && vibration -> tr("звук и вибрация")
        sound -> tr("только звук")
        vibration -> tr("только вибрация")
        else -> tr("без звука")
    }

    private fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", appLocale()))

    private fun formatDay(millis: Long): String = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE, d MMM", appLocale()))

    companion object {
        const val ALARM_CHANNEL_SOUND_VIBRATION = "event_alarms_sound_vibration_v2"
        const val ALARM_CHANNEL_SOUND = "event_alarms_sound_v2"
        const val ALARM_CHANNEL_VIBRATION = "event_alarms_vibration_v2"
        const val ALARM_CHANNEL_SILENT = "event_alarms_silent_v2"
        const val REMINDER_CHANNEL = "event_reminders_silent_full_screen_v2"
        const val REMINDER_VIBRATION_CHANNEL = "event_reminders_vibration_full_screen_v1"
        const val PLANNING_CHANNEL = "daily_planning"
        const val UPDATE_CHANNEL = "github_updates_full_screen_v2"
        private const val PLANNING_NOTIFICATION_ID = 1900
        private const val UPDATE_NOTIFICATION_ID = 2300
    }
}
